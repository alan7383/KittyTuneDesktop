package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual loopback TCP/WebSocket traffic through the production listener; no audio engine. */
class ConnectLanServerTest {
    private val prefs = NamedPrefs("sync_state")
    private var previousListener = false
    private var previousEnabled = true
    private var previousPeers = emptyList<KnownDevice>()
    private val peer = KnownDevice("test-remote-${UUID.randomUUID()}", "Test phone",
        secret = "test-remote-device-pairing-secret", platform = "android")
    private val calls = AtomicInteger()
    private val renderer = AtomicReference<PlaybackSnapshot>()
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS).build()
    private val sockets = mutableListOf<WebSocket>()

    @Before fun startListener() = runBlocking {
        previousListener = SyncService.isListenerEnabled
        previousEnabled = SyncPlayback.enabled
        previousPeers = SyncPeers.all()
        SyncPeers.forgetAll()
        // Enable the preference without starting the unrelated fixed-port HTTP listener.
        prefs.putBoolean("listener_enabled", true)
        SyncPlayback.enabled = true
        renderer.set(PlaybackSnapshot(SyncLog.deviceId, System.currentTimeMillis(),
            listOf(Track(id = 1, title = "Test track", artworkUrl = null, durationMs = 180_000, user = null)),
            0, 0, false, false, "NONE", volume = 0.7f))
        ConnectManager.configure({ renderer.get() }) { command ->
            calls.incrementAndGet()
            require(command.action == "play")
            renderer.set(renderer.get().copy(isPlaying = true))
        }
        SyncPeers.remember(peer)
        ConnectLanServer.start(InetSocketAddress("127.0.0.1", 0))
        withTimeout(5_000) { while (ConnectLanServer.boundPort == 0) delay(10) }
    }

    @After fun stopListener() {
        sockets.forEach { it.cancel() }
        ConnectLanServer.stop()
        ConnectManager.setEnabled(false)
        SyncPeers.forgetAll()
        previousPeers.forEach(SyncPeers::remember)
        prefs.putBoolean("listener_enabled", previousListener)
        ConnectManager.configure({ null }) {}
        SyncPlayback.enabled = previousEnabled
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private class Receiver(val credentials: ConnectCredentials, val token: String, val peerId: String) : WebSocketListener() {
        val ready = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Int>()
        val frames = LinkedBlockingQueue<ConnectMessage>()
        val received = CopyOnWriteArrayList<ConnectMessage>()
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(Gson().toJson(mapOf("type" to "join", "device" to peerId,
                "room" to credentials.room, "token" to token)))
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (text == "{\"type\":\"ready\"}") ready.complete(Unit)
            else ConnectWire.open(credentials, text)?.let { received.add(it); frames.offer(it) }
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            closed.complete(code)
            webSocket.close(code, "")
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            ready.completeExceptionally(t)
            closed.complete(-1)
        }
        fun awaitFrame(kind: String, id: String? = null): ConnectMessage {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline) {
                val frame = frames.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (frame.kind == kind && (id == null || frame.id == id)) return frame
            }
            error("No $kind frame received")
        }
    }

    private fun connect(token: String): Pair<WebSocket, Receiver> {
        val credentials = ConnectManager.credentials(peer)
        val receiver = Receiver(credentials, token, peer.deviceId)
        val socket = client.newWebSocket(Request.Builder()
            .url("ws://127.0.0.1:${ConnectLanServer.boundPort}/v1/connect").build(), receiver)
        sockets.add(socket)
        return socket to receiver
    }

    @Test(timeout = 30_000) fun authenticatedCommandsPublishStateBeforeAckAndRejectStaleTracks() = runBlocking {
        val credentials = ConnectManager.credentials(peer)
        val (socket, receiver) = connect(credentials.token)
        withTimeout(5_000) { receiver.ready.await() }
        val hello = receiver.awaitFrame("hello")
        val session = UUID.randomUUID().toString()
        socket.send(ConnectWire.seal(credentials, ConnectMessage(kind = "hello", sender = peer.deviceId,
            session = session, sequence = 1, challenge = UUID.randomUUID().toString(), compression = "gzip")))
        receiver.awaitFrame("welcome")
        val commandId = UUID.randomUUID().toString()
        socket.send(ConnectWire.seal(credentials, ConnectMessage(kind = "command", sender = peer.deviceId,
            session = session, sequence = 2, replyTo = hello.challenge, id = commandId,
            action = "play", trackId = 1)))
        val ack = receiver.awaitFrame("ack", commandId)
        assertTrue(ack.error, ack.ok)
        assertEquals(1, calls.get())
        assertTrue(receiver.received.any { it.kind == "state" && it.state?.isPlaying == true && it.sequence < ack.sequence })

        val staleId = UUID.randomUUID().toString()
        socket.send(ConnectWire.seal(credentials, ConnectMessage(kind = "command", sender = peer.deviceId,
            session = session, sequence = 3, replyTo = hello.challenge, id = staleId,
            action = "play", trackId = 999)))
        val rejected = receiver.awaitFrame("ack", staleId)
        assertFalse(rejected.ok)
        assertTrue(rejected.error.contains("Track changed"))
        assertEquals("A stale command must not reach the renderer", 1, calls.get())
    }

    @Test(timeout = 15_000) fun invalidPairingTokenIsClosedBeforeStateIsExposed() = runBlocking {
        val (_, receiver) = connect("invalid-token")
        assertEquals(1008, withTimeout(5_000) { receiver.closed.await() })
        assertFalse(receiver.ready.isCompleted)
        assertTrue(receiver.received.isEmpty())
        assertEquals(0, calls.get())
    }
}

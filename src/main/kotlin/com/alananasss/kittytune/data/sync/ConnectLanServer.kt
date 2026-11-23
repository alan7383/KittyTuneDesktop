package com.alananasss.kittytune.data.sync

import com.google.gson.JsonParser
import org.java_websocket.WebSocket
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.extensions.DefaultExtension
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Only paired clients can join. Payload is encrypted even on LAN; port is never exposed by the tunnel. */
object ConnectLanServer {
    private var server: WebSocketServer? = null
    private val links = ConcurrentHashMap<WebSocket, ConnectManager.Link>()
    internal val boundPort: Int get() = server?.port ?: 0
    @Synchronized fun start(bindAddress: InetSocketAddress = InetSocketAddress(ConnectLanPort)) {
        if (server != null) return
        server = object : WebSocketServer(bindAddress, 2,
            listOf(Draft_6455(listOf(DefaultExtension()), ConnectWire.MAX_BYTES))) {
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                if (handshake.resourceDescriptor != "/v1/connect" || connections.size > 16) conn.close(1008, "Unavailable")
            }
            override fun onMessage(conn: WebSocket, message: String) {
                val link = links[conn]
                if (link != null) { link.receive(message); return }
                val peer = runCatching {
                    require(message.length < 1024)
                    val join = JsonParser.parseString(message).asJsonObject
                    require(join["type"].asString == "join")
                    val device = requireNotNull(SyncPeers.find(join["device"].asString))
                    val credentials = ConnectManager.credentials(device)
                    require(join["room"].asString == credentials.room)
                    require(MessageDigest.isEqual(join["token"].asString.toByteArray(), credentials.token.toByteArray()))
                    device
                }.getOrNull()
                if (peer == null || !SyncPlayback.enabled || !SyncService.isListenerEnabled) {
                    NetLog.w("connect", "Refused a LAN join from ${conn.remoteSocketAddress?.address?.hostAddress}")
                    conn.close(1008, "Unauthorized"); return
                }
                conn.send("{\"type\":\"ready\"}")
                val attached = ConnectManager.attach(peer, "LAN", { conn.send(it); true }, dialer = peer.deviceId) { conn.close(1000, "idle") }
                if (attached == null) conn.close(1000, "duplicate") else links[conn] = attached
            }
            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) { links.remove(conn)?.detach() }
            override fun onError(conn: WebSocket?, ex: Exception) {
                if (conn != null) links.remove(conn)?.detach()
                // A null connection is the server itself: most often the port could not be bound.
                else { NetLog.w("connect", "LAN server on port ${bindAddress.port} failed", ex); Thread { stop() }.start() }
            }
            override fun onStart() { NetLog.i("connect", "LAN server listening on port ${bindAddress.port}") }
        }.apply { isReuseAddr = true; connectionLostTimeout = 60; start() }
    }
    @Synchronized fun stop() {
        val running = server ?: return
        server = null
        runCatching { running.stop(500) }
        links.values.forEach { it.detach() }; links.clear()
    }
}

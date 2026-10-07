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
    @Synchronized fun start() {
        if (server != null) return
        server = object : WebSocketServer(InetSocketAddress(ConnectLanPort), 2,
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
                if (peer == null || !SyncPlayback.enabled || !SyncService.isListenerEnabled) { conn.close(1008, "Unauthorized"); return }
                conn.send("{\"type\":\"ready\"}")
                links[conn] = ConnectManager.attach(peer, "LAN", { conn.send(it); true }) { conn.close(1000, "idle") }
            }
            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) { links.remove(conn)?.detach() }
            override fun onError(conn: WebSocket?, ex: Exception) { if (conn != null) links.remove(conn)?.detach() }
            override fun onStart() {}
        }.apply { isReuseAddr = true; connectionLostTimeout = 60; start() }
    }
    @Synchronized fun stop() {
        runCatching { server?.stop(500) }; server = null
        links.values.forEach { it.detach() }; links.clear()
    }
}

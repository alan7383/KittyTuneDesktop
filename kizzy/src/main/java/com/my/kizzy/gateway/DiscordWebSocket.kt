    package com.my.kizzy.gateway
    
    import com.my.kizzy.gateway.entities.Heartbeat
    import com.my.kizzy.gateway.entities.Identify.Companion.toIdentifyPayload
    import com.my.kizzy.gateway.entities.Payload
    import com.my.kizzy.gateway.entities.Ready
    import com.my.kizzy.gateway.entities.Resume
    import com.my.kizzy.gateway.entities.op.OpCode
    import com.my.kizzy.gateway.entities.op.OpCode.DISPATCH
    import com.my.kizzy.gateway.entities.op.OpCode.HEARTBEAT
    import com.my.kizzy.gateway.entities.op.OpCode.HELLO
    import com.my.kizzy.gateway.entities.op.OpCode.IDENTIFY
    import com.my.kizzy.gateway.entities.op.OpCode.INVALID_SESSION
    import com.my.kizzy.gateway.entities.op.OpCode.PRESENCE_UPDATE
    import com.my.kizzy.gateway.entities.op.OpCode.RECONNECT
    import com.my.kizzy.gateway.entities.op.OpCode.RESUME
    import com.my.kizzy.gateway.entities.presence.Presence
    import io.ktor.client.HttpClient
    import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
    import io.ktor.client.plugins.websocket.WebSockets
    import io.ktor.client.plugins.websocket.webSocketSession
    import io.ktor.websocket.CloseReason
    import io.ktor.websocket.Frame
    import io.ktor.websocket.close
    import io.ktor.websocket.readText
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.DelicateCoroutinesApi
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.SupervisorJob
    import kotlinx.coroutines.cancel
    import kotlinx.coroutines.cancelChildren
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.flow.receiveAsFlow
    import kotlinx.coroutines.isActive
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.runBlocking
    import kotlinx.coroutines.withTimeoutOrNull
    import kotlinx.serialization.encodeToString
    import kotlinx.serialization.json.Json
    import kotlinx.serialization.json.decodeFromJsonElement
    import kotlinx.serialization.json.encodeToJsonElement
    import java.util.logging.Level
    import java.util.logging.Level.INFO
    import java.util.logging.Logger
    import kotlin.coroutines.CoroutineContext
    import kotlin.time.Duration.Companion.milliseconds
    
    /**
     * Modified by Zion Huang
     */
    open class DiscordWebSocket(
        private val token: String,
    ) : CoroutineScope {
        private val gatewayUrl = "wss://gateway.discord.gg/?v=10&encoding=json"
        private var websocket: DefaultClientWebSocketSession? = null
        private var sequence = 0
        private var sessionId: String? = null
        private var heartbeatInterval = 0L
        private var resumeGatewayUrl: String? = null
        private var heartbeatJob: Job? = null
        private var connectJob: Job? = null
        private var connected = false
        private var client: HttpClient? = null

        private fun getClient(): HttpClient {
            var c = client
            if (c == null) {
                c = HttpClient {
                    install(WebSockets)
                }
                client = c
            }
            return c
        }

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        // A single stable SupervisorJob: every launch()/heartbeat launched from this scope is a child
        // of THIS job. The previous `get() = SupervisorJob() + Dispatchers.Default` constructed a new
        // job on every access, so `close()`'s `this.cancel()` cancelled a throwaway job — none of the
        // actually-launched coroutines (heartbeat, receive loop) were ever cancelled, leaking the
        // WebSocket and the ktor HttpClient on every closeRPC().
        private val supervisorJob = SupervisorJob()
        override val coroutineContext: CoroutineContext
            get() = supervisorJob + Dispatchers.Default
    
        suspend fun connect() {
            if (isSocketConnectedToAccount()) return
            if (connectJob?.isActive == true) return
            connectJob = launch {
                try {
                    Logger.getLogger("Kizzy").log(INFO, "Gateway: Connect called")
                    val url = resumeGatewayUrl ?: gatewayUrl
                    websocket = getClient().webSocketSession(url)
    
                    // start receiving messages
                    websocket!!.incoming.receiveAsFlow()
                        .collect {
                            when (it) {
                                is Frame.Text -> {
                                    val jsonString = it.readText()
                                    onMessage(json.decodeFromString(jsonString))
                                }
    
                                else -> {}
                            }
                        }
                    handleClose()
                } catch (e: Exception) {
                    Logger.getLogger("Kizzy").log(INFO, "Gateway: ${e.message}")
                    close()
                }
            }
        }
    
        private suspend fun handleClose() {
            heartbeatJob?.cancel()
            connected = false
            val close = websocket?.closeReason?.await()
            val code = close?.code?.toInt()
            Logger.getLogger("Kizzy").log(INFO, "Gateway: Closed with code: $code, reason: ${close?.message}")
            // Codes that cannot reconnect (authentication failure, invalid shards, etc.)
            val isFatal = code in listOf(4004, 4010, 4011, 4012, 4013, 4014)
            if (!isFatal) {
                delay(1000.milliseconds)
                connect()
            } else {
                close()
            }
        }
    
        private suspend fun onMessage(payload: Payload) {
            Logger.getLogger("Kizzy").log(INFO, "Gateway: Received op:${payload.op}, seq:${payload.s}, event :${payload.t}")
    
            payload.s?.let {
                sequence = it
            }
            when (payload.op) {
                DISPATCH -> payload.handleDispatch()
                HEARTBEAT -> sendHeartBeat()
                RECONNECT -> reconnectWebSocket()
                INVALID_SESSION -> handleInvalidSession()
                HELLO -> payload.handleHello()
                else -> {}
            }
        }
    
        open fun Payload.handleDispatch() {
            when (this.t.toString()) {
                "READY" -> {
                    val ready = json.decodeFromJsonElement<Ready>(this.d!!)
                    sessionId = ready.sessionId
                    resumeGatewayUrl = ready.resumeGatewayUrl + "/?v=10&encoding=json"
                    Logger.getLogger("Kizzy").log(INFO, "Gateway: resume_gateway_url updated to $resumeGatewayUrl")
                    Logger.getLogger("Kizzy").log(INFO, "Gateway: session_id updated to $sessionId")
                    connected = true
                    return
                }
    
                "RESUMED" -> {
                    Logger.getLogger("Kizzy").log(INFO, "Gateway: Session Resumed")
                }
    
                else -> {}
            }
        }
    
        private suspend inline fun handleInvalidSession() {
            Logger.getLogger("Kizzy").log(INFO, "Gateway: Handling Invalid Session")
            Logger.getLogger("Kizzy").log(INFO, "Gateway: Sending Identify after 150ms")
            delay(150)
            sendIdentify()
        }
    
        private suspend inline fun Payload.handleHello() {
            if (sequence > 0 && !sessionId.isNullOrBlank()) {
                sendResume()
            } else {
                sendIdentify()
            }
            heartbeatInterval = json.decodeFromJsonElement<Heartbeat>(this.d!!).heartbeatInterval
            Logger.getLogger("Kizzy").log(INFO, "Gateway: Setting heartbeatInterval= $heartbeatInterval")
            startHeartbeatJob(heartbeatInterval)
        }
    
        private suspend fun sendHeartBeat() {
            Logger.getLogger("Kizzy").log(INFO, "Gateway: Sending $HEARTBEAT with seq: $sequence")
            send(
                op = HEARTBEAT,
                d = if (sequence == 0) "null" else sequence.toString(),
            )
        }
    
        private suspend inline fun reconnectWebSocket() {
            websocket?.close(
                CloseReason(
                    code = 4000,
                    message = "Attempting to reconnect"
                )
            )
        }
    
        private suspend fun sendIdentify() {
            Logger.getLogger("Kizzy").log(INFO, "Gateway: Sending $IDENTIFY")
            send(
                op = IDENTIFY,
                d = token.toIdentifyPayload()
            )
        }
    
        private suspend fun sendResume() {
            Logger.getLogger("Kizzy").log(INFO, "Gateway: Sending $RESUME")
            send(
                op = RESUME,
                d = Resume(
                    seq = sequence,
                    sessionId = sessionId,
                    token = token
                )
            )
        }
    
        private fun startHeartbeatJob(interval: Long) {
            heartbeatJob?.cancel()
            heartbeatJob = launch {
                while (isActive) {
                    sendHeartBeat()
                    delay(interval)
                }
            }
        }
    
        fun isSocketConnectedToAccount(): Boolean {
            return connected && websocket?.isActive == true
        }
    
        @OptIn(DelicateCoroutinesApi::class)
        fun isWebSocketConnected(): Boolean {
            return websocket?.incoming != null && websocket?.outgoing?.isClosedForSend == false
        }
    
        private suspend inline fun <reified T> send(op: OpCode, d: T?) {
            if (websocket?.isActive == true) {
                val payload = json.encodeToString(
                    Payload(
                        op = op,
                        d = json.encodeToJsonElement(d),
                    )
                )
                websocket?.send(Frame.Text(payload))
            }
        }
    
        fun close() {
            heartbeatJob?.cancel()
            heartbeatJob = null
            connectJob?.cancel()
            connectJob = null
            // Cancel only the launched children (connect receive loop, heartbeat), not the scope
            // itself. Cancelling the scope via this.cancel() would make the scope unusable for a
            // future reconnect, and with the previous buggy getter it didn't even do that.
            supervisorJob.cancelChildren()
            resumeGatewayUrl = null
            sessionId = null
            connected = false
            runCatching {
                runBlocking {
                    websocket?.close()
                    Logger.getLogger("Kizzy").log(Level.SEVERE, "Gateway: Connection to gateway closed")
                }
            }
            // Release the ktor HttpClient (connection pool + threads). Previously leaked forever —
            // every closeRPC() left one HttpClient open.
            runCatching { client?.close() }
            client = null
            websocket = null
        }

        suspend fun sendActivity(presence: Presence) {
            if (!isSocketConnectedToAccount()) {
                connect()
            }
            // Wait for the gateway to reach READY, but with a timeout: the previous unbounded
            // `while (!isSocketConnectedToAccount()) delay(10ms)` could spin forever if connect()
            // silently failed to dispatch READY, suspending the caller indefinitely.
            val ready = withTimeoutOrNull(ACTIVITY_READY_TIMEOUT_MS) {
                while (!isSocketConnectedToAccount()) delay(50.milliseconds)
                true
            }
            if (ready == null) {
                Logger.getLogger("Kizzy").log(Level.WARNING, "Gateway: sendActivity timed out waiting for READY, dropping presence update")
                return
            }
            Logger.getLogger("Kizzy").log(INFO, "Gateway: Sending $PRESENCE_UPDATE")
            send(
                op = PRESENCE_UPDATE,
                d = presence
            )
        }

        companion object {
            /** Maximum time sendActivity waits for the gateway to reach READY before giving up. */
            private const val ACTIVITY_READY_TIMEOUT_MS = 10_000L
        }

    }



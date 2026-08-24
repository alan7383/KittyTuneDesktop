package com.alananasss.kittytune.data.discord

import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.MGF1ParameterSpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Discord's remote-auth flow — the "log in by scanning a QR code" the official mobile and
 * desktop clients use. No password, no captcha, no embedded browser.
 *
 * We hold an RSA-2048 keypair for the session and hand Discord the public key; everything it
 * sends back that matters (the nonce challenge, the account preview, the final token) comes
 * encrypted to that key. The crypto is exactly what Discord's own Android client does in its
 * RemoteAuthCryptoModule: RSA/ECB/OAEPPadding with SHA-256 and MGF1-SHA-256, keys and
 * ciphertext base64, the nonce proof base64url without padding.
 *
 * Flow: connect -> hello -> init(public key) -> nonce_proof challenge -> pending_remote_init
 * gives us a fingerprint, which becomes the QR at discord.com/ra/<fingerprint> -> the phone
 * scans it and we get pending_ticket (a preview of the account, to show who is about to be
 * linked) -> the user confirms on the phone and we get pending_login with a ticket -> that
 * ticket buys the encrypted token over HTTP.
 *
 * The private key never leaves this object and is dropped when the flow ends.
 */
object DiscordRemoteAuth {

    private const val TAG = "DiscordRemoteAuth"
    private const val GATEWAY_URL = "wss://remote-auth-gateway.discord.gg/?v=2"
    private const val LOGIN_URL = "https://discord.com/api/v9/users/@me/remote-auth/login"
    private const val ORIGIN = "https://discord.com"
    private const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    /** What the login screen renders. */
    sealed interface State {
        data object Idle : State
        data object Connecting : State
        /** Show this as a QR code: the phone scans it to start the handshake. */
        data class AwaitingScan(val qrContent: String) : State
        /** The phone scanned; the account it will link, pending the user's confirmation. */
        data class AwaitingConfirmation(
            val userId: String,
            val username: String,
            val discriminator: String?,
            val avatarUrl: String?
        ) : State
        data class Success(val token: String) : State
        /** The handshake window closed (Discord gives us a few minutes) — offer a new QR. */
        data object Expired : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()

    private var scope: CoroutineScope? = null
    private var socket: WebSocket? = null
    private var keyPair: KeyPair? = null
    private var heartbeatJob: Job? = null
    private var timeoutJob: Job? = null

    /**
     * Bumped every time a handshake ends. Each socket's listener remembers the generation it
     * belongs to and ignores anything that arrives afterwards, so a late message from an
     * abandoned socket can neither expire nor fail the handshake that replaced it.
     */
    @Volatile
    private var generation = 0

    /** Starts (or restarts) a handshake. Safe to call again to refresh an expired QR. */
    @Synchronized
    fun start() {
        stop(State.Connecting)

        val currentGeneration = generation
        val freshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = freshScope

        freshScope.launch {
            val pair = runCatching {
                KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            }.getOrElse {
                Logger.e(TAG, "RSA keygen failed: ${it.message}")
                _state.value = State.Failed("keygen")
                return@launch
            }
            keyPair = pair

            val request = Request.Builder()
                .url(GATEWAY_URL)
                .header("Origin", ORIGIN)
                .header("User-Agent", USER_AGENT)
                .build()
            socket = client.newWebSocket(request, Listener(currentGeneration))
        }
    }

    /** Abandons the handshake and forgets the key. */
    @Synchronized
    fun cancel() {
        stop(State.Idle)
    }

    private fun stop(next: State) {
        _state.value = next
        cleanup()
    }

    /**
     * Drops the socket, the timers and the private key without touching [state] — the caller
     * decides what the flow ended as, and a success must not be overwritten on the way out.
     * Safe to call from inside [scope]: nothing after it needs to run.
     */
    private fun cleanup() {
        generation++
        heartbeatJob?.cancel()
        heartbeatJob = null
        timeoutJob?.cancel()
        timeoutJob = null
        runCatching { socket?.close(1000, null) }
        socket = null
        keyPair = null
        scope?.cancel()
        scope = null
    }

    private class Listener(private val ownGeneration: Int) : WebSocketListener() {

        private fun isStale() = ownGeneration != generation

        override fun onOpen(webSocket: WebSocket, response: Response) {
            Logger.d(TAG, "Remote auth gateway connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isStale()) return
            val payload = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (payload.optString("op")) {
                "hello" -> onHello(webSocket, payload)
                "nonce_proof" -> onNonceProof(webSocket, payload)
                "pending_remote_init" -> onPendingRemoteInit(payload)
                "pending_ticket" -> onPendingTicket(payload)
                "pending_login" -> onPendingLogin(payload)
                "cancel" -> stop(State.Failed("cancelled_on_phone"))
                "heartbeat_ack" -> Unit
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isStale()) return
            Logger.w(TAG, "Remote auth socket failed: ${t.message}")
            stop(State.Failed("network"))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // Discord dropping the socket on its own means the handshake window is over.
            if (isStale()) return
            stop(State.Expired)
        }
    }

    private fun onHello(webSocket: WebSocket, payload: JSONObject) {
        val publicKey = keyPair?.public?.encoded ?: return
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(publicKey)
        webSocket.send(JSONObject().put("op", "init").put("encoded_public_key", encoded).toString())

        val interval = payload.optLong("heartbeat_interval", 41_250L)
        heartbeatJob?.cancel()
        heartbeatJob = scope?.launch {
            while (true) {
                delay(interval)
                runCatching { webSocket.send("""{"op":"heartbeat"}""") }
            }
        }

        // Discord gives the handshake a fixed window; surface the end of it as Expired so the
        // screen can offer a fresh QR instead of showing one that no longer works.
        val timeoutMs = payload.optLong("timeout_ms", 0L)
        timeoutJob?.cancel()
        if (timeoutMs > 0) {
            timeoutJob = scope?.launch {
                delay(timeoutMs)
                if (_state.value !is State.Success) stop(State.Expired)
            }
        }
    }

    private fun onNonceProof(webSocket: WebSocket, payload: JSONObject) {
        val encryptedNonce = payload.optString("encrypted_nonce").takeIf { it.isNotBlank() } ?: return
        val nonce = decrypt(encryptedNonce) ?: run {
            stop(State.Failed("nonce_decrypt"))
            return
        }
        val proof = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(nonce))
        webSocket.send(JSONObject().put("op", "nonce_proof").put("proof", proof).toString())
    }

    private fun onPendingRemoteInit(payload: JSONObject) {
        val fingerprint = payload.optString("fingerprint").takeIf { it.isNotBlank() } ?: return
        _state.value = State.AwaitingScan("https://discord.com/ra/$fingerprint")
    }

    private fun onPendingTicket(payload: JSONObject) {
        val encrypted = payload.optString("encrypted_user_payload").takeIf { it.isNotBlank() } ?: return
        val decoded = decrypt(encrypted)?.toString(Charsets.UTF_8) ?: return
        // "<id>:<discriminator>:<avatar hash>:<username>" — the avatar hash can be empty.
        val parts = decoded.split(':', limit = 4)
        val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return
        val avatarHash = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
        _state.value = State.AwaitingConfirmation(
            userId = id,
            username = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: id,
            discriminator = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it != "0" },
            avatarUrl = avatarHash?.let { "https://cdn.discordapp.com/avatars/$id/$it.png?size=128" }
        )
    }

    private fun onPendingLogin(payload: JSONObject) {
        val ticket = payload.optString("ticket").takeIf { it.isNotBlank() } ?: return
        val currentScope = scope ?: return
        currentScope.launch {
            val token = exchangeTicket(ticket)
            if (token == null) {
                stop(State.Failed("ticket_exchange"))
            } else {
                stop(State.Success(token))
            }
        }
    }

    /** Trades the confirmed ticket for the account token, which arrives encrypted to our key. */
    private fun exchangeTicket(ticket: String): String? {
        val body = JSONObject().put("ticket", ticket).toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(LOGIN_URL)
            .post(body)
            .header("Origin", ORIGIN)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string()
                if (!response.isSuccessful || text.isNullOrBlank()) {
                    Logger.w(TAG, "Ticket exchange failed: HTTP ${response.code}")
                    return null
                }
                val encryptedToken = JSONObject(text).optString("encrypted_token")
                    .takeIf { it.isNotBlank() } ?: return null
                decrypt(encryptedToken)?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Ticket exchange error: ${e.message}")
            null
        }
    }

    /** RSA-OAEP(SHA-256) with our session key — the one operation everything here depends on. */
    private fun decrypt(base64Ciphertext: String): ByteArray? {
        val privateKey = keyPair?.private ?: return null
        return try {
            val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                privateKey,
                OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)
            )
            cipher.doFinal(Base64.getDecoder().decode(base64Ciphertext))
        } catch (e: Exception) {
            Logger.w(TAG, "Decrypt failed: ${e.message}")
            null
        }
    }
}

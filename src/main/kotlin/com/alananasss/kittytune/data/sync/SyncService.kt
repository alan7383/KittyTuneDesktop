package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.core.NamedPrefs
import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.Base64

/**
 * Device-to-device sync over the local network (issue #33).
 *
 * There is no server and there is not going to be one: the two devices talk to each other directly
 * while they are on the same network, and nothing leaves it. One of them listens, the other calls in,
 * and a single request carries both directions — each side sends what it knows the other lacks and
 * says how far it has got, so one round trip converges them.
 *
 * ## Access control
 *
 * The listener is bound to every interface, because the point is to be reachable from a phone. That
 * makes it reachable from everything else on the network too, so **every request must carry the
 * pairing secret** — [PAIRING_HEADER] with the value from [pairingSecret] — and one that does not is
 * refused before its body is read. The secret is 160 random bits, generated once per install, and it
 * is the only thing standing between a stranger on the same café Wi-Fi and a copy of the listening
 * history. It can be replaced with [regeneratePairingSecret], which un-pairs every device.
 *
 * The listener is off unless it is turned on. A sync feature that quietly opened a port on every
 * launch would be a worse default than one that has to be asked for.
 */
object SyncService {

    private val gson = Gson()
    private val prefs by lazy { NamedPrefs("sync_state") }

    /** Fixed so a paired device can find its way back without being told the port again. */
    const val DEFAULT_PORT = 47653

    /** Where the secret goes. Bearer-style, because that is what it is. */
    const val PAIRING_HEADER = "Authorization"

    private const val KEY_SECRET = "pairing_secret"
    private const val KEY_ENABLED = "listener_enabled"
    private const val KEY_PORT = "listener_port"

    private var server: HttpServer? = null

    /** What a device has to know to reach this one. Never logged, never sent anywhere. */
    val pairingSecret: String
        get() = prefs.getString(KEY_SECRET, null)?.takeIf { it.isNotBlank() } ?: newSecret()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.putInt(KEY_PORT, value.coerceIn(1024, 65535))

    /** Whether the listener should come up on launch. Off until asked for. */
    var isListenerEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.putBoolean(KEY_ENABLED, value)
            if (value) start() else stop()
        }

    val isRunning: Boolean get() = server != null

    /** Replaces the secret. Every previously paired device stops being able to connect. */
    fun regeneratePairingSecret(): String = newSecret()

    private fun newSecret(): String {
        val bytes = ByteArray(20)
        SecureRandom().nextBytes(bytes)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        prefs.putString(KEY_SECRET, secret)
        return secret
    }

    /**
     * What another device needs to reach this one.
     *
     * Also what is handed to a peer mid-exchange so it can call *us* back, which is what turned sync
     * from one-directional into something either side can start (issue #33).
     */
    fun selfPairing(): PairingPayload = PairingPayload(
        host = localAddress(),
        port = port,
        secret = pairingSecret,
        deviceId = SyncLog.deviceId,
        deviceName = SyncLog.deviceName,
        platform = PLATFORM,
    )

    /**
     * The one string a phone needs: where to call and what to say. Base64 so it survives being
     * pasted, scanned, or read out, and so it is obvious it is not meant to be edited by hand.
     */
    fun pairingCode(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(gson.toJson(selfPairing()).toByteArray())

    const val PLATFORM = "desktop"

    /** @return the pairing details in [code], or null when it is not one of ours. */
    fun parsePairingCode(code: String): PairingPayload? = runCatching {
        val json = String(Base64.getUrlDecoder().decode(code.trim()))
        gson.fromJson(json, PairingPayload::class.java)
            ?.takeIf { it.host.isNotBlank() && it.secret.isNotBlank() && it.port in 1..65535 }
    }.getOrNull()

    @Synchronized
    fun start() {
        if (server != null) return
        runCatching {
            val created = HttpServer.create(InetSocketAddress(port), 0)
            created.createContext("/sync") { exchange -> handleSync(exchange) }
            created.executor = null
            created.start()
            server = created
            // Findable for exactly as long as it is reachable. Announcing an address that nothing
            // answers on is the failure mode this whole beacon exists to remove (issue #33).
            SyncDiscovery.startResponder()
        }.onFailure { System.err.println("Sync listener failed to start: ${it.message}") }
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
        SyncDiscovery.stopResponder()
    }

    /**
     * Answers a peer: takes what it sent, hands back what it lacks.
     *
     * Refused before the body is read when the secret is missing or wrong, so an unauthenticated
     * caller cannot make us parse anything it chose.
     */
    private fun handleSync(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "POST") {
                exchange.sendResponseHeaders(405, -1)
                return
            }
            val presented = exchange.requestHeaders.getFirst(PAIRING_HEADER)
                ?.removePrefix("Bearer ")?.trim()
            if (!secretMatches(presented)) {
                // Fingerprints, never the secrets themselves. A refused pairing is almost always a
                // caller holding a code that was replaced since it was shown, and without this the
                // only evidence was a 401 on the phone with nothing to compare it against (issue #33).
                System.err.println(
                    "Sync: refused a caller presenting ${fingerprint(presented)}, " +
                        "this device expects ${fingerprint(pairingSecret)}"
                )
                exchange.sendResponseHeaders(401, -1)
                return
            }

            val body = exchange.requestBody.readBytes().decodeToString()
            val request = runCatching { gson.fromJson(body, SyncExchange::class.java) }.getOrNull()
            if (request == null) {
                exchange.sendResponseHeaders(400, -1)
                return
            }

            val response = kotlinx.coroutines.runBlocking {
                respondTo(request, exchange.remoteAddress?.address?.hostAddress)
            }
            val bytes = gson.toJson(response).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (t: Throwable) {
            runCatching { exchange.sendResponseHeaders(500, -1) }
        } finally {
            exchange.close()
        }
    }

    /**
     * The exchange itself, either side of the wire.
     *
     * Merging first and answering second is deliberate: the marks we report already account for what
     * the peer just told us, so it does not send the same batch again on the next round.
     *
     * @param observedHost the address the request actually arrived from, preferred over the one the
     *   caller claims. A device reporting a stale interface — a phone that has just changed networks
     *   does exactly this — would otherwise have us saving an address that answers nothing.
     */
    suspend fun respondTo(request: SyncExchange, observedHost: String? = null): SyncExchange {
        val applied = SyncLog.merge(request.events)
        // Awaited, so the marks and the count we report describe work that has actually happened.
        SyncApply.applyNow(applied)
        SyncLog.setPeerMarks(request.deviceId, request.marks)

        // The caller hands over how to call it back, which is what makes the pairing mutual: after one
        // exchange in either direction, both devices can start the next one (issue #33).
        val callback = request.callback
        SyncPeers.remember(
            KnownDevice(
                deviceId = request.deviceId,
                deviceName = request.deviceName,
                host = observedHost?.takeIf { it.isNotBlank() } ?: callback?.host.orEmpty(),
                port = callback?.port?.takeIf { it in 1..65535 } ?: DEFAULT_PORT,
                secret = callback?.secret.orEmpty(),
                platform = callback?.platform.orEmpty(),
                lastSyncedAtMs = System.currentTimeMillis(),
            )
        )

        return SyncExchange(
            deviceId = SyncLog.deviceId,
            deviceName = SyncLog.deviceName,
            marks = SyncLog.marks(),
            events = SyncMerge.eventsToSend(SyncLog.all(), request.marks, request.deviceId),
            callback = selfPairing().takeIf { isRunning },
        )
    }

    /** First bytes of a SHA-256, enough to tell two secrets apart in a log without revealing either. */
    private fun fingerprint(secret: String?): String {
        if (secret.isNullOrEmpty()) return "no secret"
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    /** Constant-time, so a wrong secret cannot be found one character at a time. */
    private fun secretMatches(presented: String?): Boolean {
        val expected = pairingSecret
        if (presented == null || presented.length != expected.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (presented[i].code xor expected[i].code)
        return diff == 0
    }

    /**
     * This machine's address on the local network, for the pairing code.
     *
     * The loopback address is useless to a phone, so an interface with a real address is preferred
     * and loopback is only the last resort — which at least fails visibly rather than silently
     * handing out an address that cannot work.
     */
    fun localAddress(): String = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
            ?: "127.0.0.1"
    }.getOrDefault("127.0.0.1")
}

/**
 * What a device needs to reach another one.
 *
 * Doubles as the QR payload and as the connect-back details a caller hands over mid-exchange, so a
 * pairing is symmetric after one round in either direction.
 */
data class PairingPayload(
    val host: String,
    val port: Int,
    val secret: String,
    val deviceId: String,
    val deviceName: String,
    /** "desktop" or "android", for a screen that wants to draw the right icon. Absent in old codes. */
    val platform: String = "",
)

/**
 * One side of an exchange: who I am, how far I have got, and what I think you are missing.
 *
 * The same shape both ways, so one type describes the request and the response and neither side has
 * a role the other does not.
 */
data class SyncExchange(
    val deviceId: String,
    val deviceName: String,
    val marks: Map<String, Long>,
    val events: List<SyncEvent>,
    /**
     * How to call the sender back, when it can be called.
     *
     * Null from a device with no listener, and absent entirely from a version that predates mutual
     * pairing — both of which leave the pairing one-directional rather than breaking it.
     */
    val callback: PairingPayload? = null,
)

package com.alananasss.kittytune.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean

/** Shared by native URL events and the Windows/Linux protocol wrappers. Never logs credentials. */
internal object SoundCloudOAuthCallback {
    const val MAX_BYTES = 16 * 1024

    data class Result(val code: String? = null, val error: String? = null)

    fun parse(value: String, expectedState: String): Result? = runCatching {
        if (value.length > MAX_BYTES) return null
        val uri = URI(value)
        // GET callbacks use a relative loopback path; POST callbacks contain the full sc:// URL.
        if (uri.rawFragment != null || uri.rawUserInfo != null || uri.port != -1) return null
        if (uri.scheme == null) {
            if (uri.rawAuthority != null || uri.rawPath != "/callback") return null
        } else if (uri.scheme != "sc" || uri.rawAuthority != "auth" || (uri.rawPath != null && uri.rawPath != "/" && uri.rawPath.isNotEmpty())) {
            return null
        }
        val params = linkedMapOf<String, String>()
        for (part in (uri.rawQuery ?: return null).split('&')) {
            val pair = part.split('=', limit = 2)
            if (pair.size != 2) return null
            val key = URLDecoder.decode(pair[0], Charsets.UTF_8)
            val decoded = URLDecoder.decode(pair[1], Charsets.UTF_8)
            if (params.put(key, decoded) != null) return null
        }
        if (params["state"] != expectedState) return null
        val code = params["code"]?.takeIf { it.isNotBlank() }
        val error = params["error"]?.takeIf { it.isNotBlank() }
        if ((code == null) == (error == null)) return null
        Result(code, error)
    }.getOrNull()
}

/** One attempt, one accepted result, bound explicitly to IPv4 loopback (never a LAN interface). */
internal class SoundCloudOAuthServer(
    private val state: String,
    private val onResult: (SoundCloudOAuthCallback.Result) -> Unit
) : AutoCloseable {
    private val accepted = AtomicBoolean(false)
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val port: Int get() = server.address.port

    init {
        server.createContext("/callback") { exchange ->
            exchange.use {
                val value = when {
                    exchange.requestURI.rawPath != "/callback" -> null
                    exchange.requestMethod == "GET" -> exchange.requestURI.toString()
                    exchange.requestMethod == "POST" -> {
                        val bytes = exchange.requestBody.readNBytes(SoundCloudOAuthCallback.MAX_BYTES + 1)
                        if (bytes.size > SoundCloudOAuthCallback.MAX_BYTES) null else bytes.toString(Charsets.UTF_8)
                    }
                    else -> null
                }
                val result = value?.let { SoundCloudOAuthCallback.parse(it, state) }
                val status = when {
                    result == null -> 400
                    !accepted.compareAndSet(false, true) -> 409
                    else -> 200
                }
                val message = when {
                    status != 200 -> "Invalid or expired SoundCloud callback. Return to KittyTune."
                    result?.error != null -> "SoundCloud sign-in was declined. Return to KittyTune to retry."
                    else -> "SoundCloud callback received. Return to KittyTune to finish signing in."
                }.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                exchange.responseHeaders.set("Cache-Control", "no-store")
                exchange.sendResponseHeaders(status, message.size.toLong())
                exchange.responseBody.write(message)
                if (status == 200) onResult(requireNotNull(result))
            }
        }
        server.start()
    }

    override fun close() {
        accepted.set(true)
        server.stop(0)
    }
}

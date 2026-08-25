package com.alananasss.kittytune.utils

import java.net.URLDecoder
import java.util.Base64

/**
 * Reads the deadline out of a signed CDN URL.
 *
 * SoundCloud hands back CloudFront links that only live for about three minutes, either with
 * a canned policy (`Expires=<epoch>`) or a custom one (`Policy=` — base64url JSON carrying
 * `DateLessThan."AWS:EpochTime"`); the YouTube fallback uses `expire=<epoch>`. Past the
 * deadline the CDN answers 403 forever, so anything that caches or prefetches a resolved URL
 * has to know when it dies rather than trusting a wall-clock TTL of its own.
 */
object SignedUrl {

    /**
     * How much life a URL needs left before we still consider it worth using. Covers the
     * decoder's own open/probe round-trip plus clock skew against the CDN.
     */
    const val DEFAULT_MARGIN_MS = 20_000L

    private val EPOCH_TIME = Regex("\"AWS:EpochTime\"\\s*:\\s*(\\d+)")

    /** Epoch millis this URL stops being served, or null when it carries no readable deadline. */
    fun expiryEpochMs(url: String): Long? {
        if (!isNetworkUrl(url)) return null
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null

        var earliest: Long? = null
        for (pair in query.split('&')) {
            val separator = pair.indexOf('=')
            if (separator <= 0) continue
            val value = pair.substring(separator + 1)
            val candidate = when (pair.substring(0, separator)) {
                "Policy" -> policyExpiry(value)
                // CloudFront canned policies, YouTube/googlevideo.
                "Expires", "expires", "expire" -> value.toLongOrNull()?.let(::toEpochMs)
                else -> null
            } ?: continue
            if (earliest == null || candidate < earliest) earliest = candidate
        }
        return earliest
    }

    /**
     * True when [url] is signed and its deadline is already past (or within [marginMs]).
     * A URL with no readable deadline is never called expired — callers fall back to their
     * own TTL for those.
     */
    fun isExpired(url: String, marginMs: Long = DEFAULT_MARGIN_MS): Boolean {
        val expiry = expiryEpochMs(url) ?: return false
        return System.currentTimeMillis() + marginMs >= expiry
    }

    /** Millis until [url] should be treated as dead, or null when it carries no deadline. */
    fun remainingMs(url: String, marginMs: Long = DEFAULT_MARGIN_MS): Long? {
        val expiry = expiryEpochMs(url) ?: return null
        return expiry - marginMs - System.currentTimeMillis()
    }

    fun isNetworkUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private fun policyExpiry(rawValue: String): Long? {
        val policy = decodePolicy(rawValue) ?: return null
        val epochTime = EPOCH_TIME.find(policy)?.groupValues?.get(1) ?: return null
        return epochTime.toLongOrNull()?.let(::toEpochMs)
    }

    private fun decodePolicy(rawValue: String): String? {
        val percentDecoded = if (rawValue.contains('%')) {
            try {
                URLDecoder.decode(rawValue, "UTF-8")
            } catch (_: Exception) {
                rawValue
            }
        } else {
            rawValue
        }

        // CloudFront ships policies through a substituted base64 alphabet so they survive a
        // query string: '+' -> '-', '=' -> '_', '/' -> '~'.
        val base64 = percentDecoded.replace('-', '+').replace('_', '=').replace('~', '/')
        val padded = base64 + "=".repeat((4 - base64.length % 4) % 4)
        return try {
            String(Base64.getMimeDecoder().decode(padded))
        } catch (_: Exception) {
            null
        }
    }

    /** Signed URLs quote epoch seconds; accept millis too rather than reading them as 1970. */
    private fun toEpochMs(value: Long): Long = if (value < 100_000_000_000L) value * 1000L else value
}

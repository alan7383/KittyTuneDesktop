package com.alananasss.kittytune.data.search

import com.alananasss.kittytune.data.network.ProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * A small difference hash for cover art, used by the verified-track filter.
 *
 * Two rows that claim to be the same song should show the same artwork. Comparing URLs does not
 * work — every platform re-hosts and resizes its own copy — so the image itself is reduced to a
 * 9x8 grayscale bitmap and every pixel is compared with the one to its right. That gives a 64-bit
 * fingerprint where the same cover at different sizes or compression levels stays within a few
 * bits, while a different cover lands much farther away.
 *
 * Decoded fingerprints are cached by URL: a search asks for the same handful of covers again
 * when it is repeated or when another result on the page shares the album art.
 */
object CoverFingerprint {

    private const val MAX_BYTES = 6_000_000
    private const val FETCH_TIMEOUT_MS = 5_000L
    private const val HASH_WIDTH = 9
    private const val HASH_HEIGHT = 8
    private const val CACHE_LIMIT = 512

    private val cache = ConcurrentHashMap<String, Long>()

    /** URLs that will not be tried again during this process (404, not an image, local file…). */
    private val unavailable: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** The fingerprint for [url], or null when the image cannot be fetched or decoded. */
    suspend fun fingerprint(url: String): Long? {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return null
        cache[url]?.let { return it }
        if (url in unavailable) return null

        val hash = try {
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { download(url) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        if (hash == null) {
            unavailable.add(url)
        } else {
            if (cache.size >= CACHE_LIMIT) cache.clear()
            cache[url] = hash
        }
        return hash
    }

    /**
     * Hashes [image] into the same 64-bit layout used by [fingerprint]. Exposed so other artwork
     * comparisons (and tests) can reuse the exact layout.
     */
    fun fromImage(image: BufferedImage): Long {
        val scaled = BufferedImage(HASH_WIDTH, HASH_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR,
            )
            graphics.drawImage(image, 0, 0, HASH_WIDTH, HASH_HEIGHT, null)
        } finally {
            graphics.dispose()
        }

        val luminance = IntArray(HASH_WIDTH * HASH_HEIGHT)
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH) {
                val rgb = scaled.getRGB(x, y)
                val red = (rgb shr 16) and 0xFF
                val green = (rgb shr 8) and 0xFF
                val blue = rgb and 0xFF
                luminance[y * HASH_WIDTH + x] = (red * 299 + green * 587 + blue * 114) / 1000
            }
        }

        var hash = 0L
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            val row = y * HASH_WIDTH
            for (x in 0 until HASH_WIDTH - 1) {
                if (luminance[row + x] > luminance[row + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    private suspend fun download(url: String): Long? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "image/*")
            .build()

        ProxyManager.getOkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@use null
            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return@use null
            fromImage(image)
        }
    }
}

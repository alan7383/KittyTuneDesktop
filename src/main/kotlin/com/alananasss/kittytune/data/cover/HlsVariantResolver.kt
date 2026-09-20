package com.alananasss.kittytune.data.cover

import com.alananasss.kittytune.utils.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves a master HLS (.m3u8) playlist into a single optimal sub-variant playlist.
 *
 * This prevents FFmpeg from probing and demuxing all 20+ video streams in parallel,
 * which causes massive network congestion, NAL unit size errors and multi-second delays.
 */
object HlsVariantResolver {

    private const val TAG = "HlsVariantResolver"

    private val cache = ConcurrentHashMap<String, String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun resolveOptimalVariant(url: String): String {
        if (!url.contains(".m3u8", ignoreCase = true)) {
            return url
        }

        cache[url]?.let { return it }

        val resolved = runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .apply {
                    if (url.contains("apple.com") || url.contains("itunes.apple.com")) {
                        header("Origin", "https://music.apple.com")
                        header("Referer", "https://music.apple.com/")
                    }
                }
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@use url
                val body = response.body.string()
                if (!body.contains("#EXT-X-STREAM-INF")) {
                    // Not a master playlist, already a single media playlist
                    return@use url
                }

                val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val variants = mutableListOf<VariantEntry>()

                for (i in lines.indices) {
                    val line = lines[i]
                    if (line.startsWith("#EXT-X-STREAM-INF:")) {
                        val nextLine = lines.getOrNull(i + 1) ?: continue
                        if (nextLine.startsWith("#")) continue

                        val resolvedUri = try {
                            val baseUri = URI(url)
                            baseUri.resolve(nextLine).toString()
                        } catch (_: Exception) {
                            nextLine
                        }

                        val isAvc = line.contains("avc1", ignoreCase = true)
                        val isHevc = line.contains("hvc1", ignoreCase = true)
                        val resMatch = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
                        val width = resMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        val height = resMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0

                        variants.add(
                            VariantEntry(
                                url = resolvedUri,
                                isAvc = isAvc,
                                isHevc = isHevc,
                                width = width,
                                height = height
                            )
                        )
                    }
                }

                // Choose optimal variant:
                // 1. Prefer H.264 (AVC) around 540p - 720p (super light to decode and instant)
                // 2. Fallback to AVC any height
                // 3. Fallback to HEVC
                // 4. Fallback to first
                val chosen = variants.firstOrNull { it.isAvc && (it.height in 540..720) }
                    ?: variants.firstOrNull { it.isAvc && (it.height in 360..1080) }
                    ?: variants.firstOrNull { it.isAvc }
                    ?: variants.firstOrNull { it.isHevc && (it.height in 540..720) }
                    ?: variants.firstOrNull()

                chosen?.url ?: url
            }
        }.getOrElse { e ->
            Logger.w(TAG, "Failed to resolve HLS variant for $url: ${e.message}")
            url
        }

        cache[url] = resolved
        return resolved
    }

    private data class VariantEntry(
        val url: String,
        val isAvc: Boolean,
        val isHevc: Boolean,
        val width: Int,
        val height: Int
    )
}

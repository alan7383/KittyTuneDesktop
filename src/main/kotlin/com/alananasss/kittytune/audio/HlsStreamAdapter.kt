package com.alananasss.kittytune.audio

import com.alananasss.kittytune.utils.Logger
import com.alananasss.kittytune.utils.SignedUrl
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.net.URL

/**
 * Feeds an HLS playlist to FFmpeg as one continuous stream, concatenating its fragments.
 *
 * SoundCloud signs both the playlist and every fragment inside it for a few minutes, so a track
 * longer than that window runs out of signature partway through: the fragment list parsed at
 * construction goes 403 while the decoder is still reading it. [refreshPlaylistUrl] lets the
 * adapter fetch a re-signed playlist for the same audio and carry on from the same fragment,
 * instead of letting the decoder see EOF and rebuilding the whole stream around a gap.
 *
 * @param playlistUrl the playlist this adapter was built for. Stays fixed as the adapter's
 *   identity even after a refresh swaps in re-signed URLs, so callers can still tell which
 *   stream it belongs to.
 */
class HlsStreamAdapter(
    val playlistUrl: String,
    val headers: Map<String, String>,
    private val refreshPlaylistUrl: ((failedUrl: String) -> String?)? = null
) {

    class Segment(val url: String, val durationMs: Long, val startTimeMs: Long)

    private class Playlist(
        val initSegmentUrl: String?,
        val segments: List<Segment>,
        val totalDurationMs: Long
    )

    private var initSegmentUrl: String? = null
    private var initSegmentData: ByteArray? = null
    private var segments: List<Segment> = emptyList()

    var totalDurationMs = 0L
        private set

    /** What we actually fetch from; [playlistUrl] is only the identity we were built with. */
    private var livePlaylistUrl = playlistUrl
    private var lastFailedRefreshAtMs = 0L

    init {
        val playlist = loadPlaylist(playlistUrl)
        initSegmentUrl = playlist.initSegmentUrl
        segments = playlist.segments
        totalDurationMs = playlist.totalDurationMs
        initSegmentData = playlist.initSegmentUrl?.let { fetchInitSegment(it) }
    }

    companion object {
        private val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** How long to leave a refresh that did not help alone, so a dead track cannot spin. */
        private const val REFRESH_RETRY_BACKOFF_MS = 5_000L
    }

    private fun loadPlaylist(url: String): Playlist {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (k, v) -> requestBuilder.header(k, v) }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Failed to load playlist: ${response.code}")
        }

        val body = response.body?.string() ?: throw IOException("Empty playlist")

        var initUrl: String? = null
        val parsed = mutableListOf<Segment>()
        var currentStartTime = 0L
        var nextDurationMs = 0L

        body.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-MAP:URI=")) {
                var uri = trimmed.substringAfter("URI=\"").substringBefore("\"")
                if (!uri.startsWith("http")) uri = resolveUrl(url, uri)
                initUrl = uri
            } else if (trimmed.startsWith("#EXTINF:")) {
                val durStr = trimmed.substringAfter("#EXTINF:").substringBefore(",")
                val durSec = durStr.toDoubleOrNull() ?: 0.0
                nextDurationMs = (durSec * 1000).toLong()
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                var segUrl = trimmed
                if (!segUrl.startsWith("http")) segUrl = resolveUrl(url, segUrl)
                parsed.add(Segment(segUrl, nextDurationMs, currentStartTime))
                currentStartTime += nextDurationMs
                nextDurationMs = 0L
            }
        }

        return Playlist(initUrl, parsed, currentStartTime)
    }

    /** The ISOBMFF header is the same audio whatever the signature, so it is cached once. */
    private fun fetchInitSegment(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: Exception) {
            Logger.e("HlsStreamAdapter", "Failed to cache init segment", e)
            null
        }
    }

    /**
     * Swaps in a re-signed fragment list for the same audio, and reports whether it worked.
     *
     * Only a playlist that segments the track identically is adopted: the decoder is partway
     * through a fragment index, and that index has to keep meaning the same moment. Anything else
     * is left to the engine's recovery, which rebuilds the stream from scratch and cannot get the
     * timeline wrong.
     */
    @Synchronized
    private fun refreshSegments(): Boolean {
        val refresher = refreshPlaylistUrl ?: return false
        if (System.currentTimeMillis() - lastFailedRefreshAtMs < REFRESH_RETRY_BACKOFF_MS) return false

        val freshUrl = refresher(livePlaylistUrl)
        if (freshUrl.isNullOrEmpty() || !freshUrl.contains(".m3u8")) {
            // No playlist to be had — the track may have re-resolved to progressive, which only
            // the engine can act on.
            lastFailedRefreshAtMs = System.currentTimeMillis()
            return false
        }
        if (freshUrl == livePlaylistUrl) {
            lastFailedRefreshAtMs = System.currentTimeMillis()
            return false
        }

        return try {
            val playlist = loadPlaylist(freshUrl)
            if (playlist.segments.size != segments.size) {
                Logger.e(
                    "HlsStreamAdapter",
                    "Re-signed playlist has ${playlist.segments.size} fragments, expected ${segments.size}; ignoring"
                )
                lastFailedRefreshAtMs = System.currentTimeMillis()
                return false
            }
            segments = playlist.segments
            initSegmentUrl = playlist.initSegmentUrl
            livePlaylistUrl = freshUrl
            Logger.e("HlsStreamAdapter", "Re-signed ${segments.size} fragments")
            true
        } catch (e: Exception) {
            Logger.e("HlsStreamAdapter", "Playlist refresh failed: ${e.message}")
            lastFailedRefreshAtMs = System.currentTimeMillis()
            false
        }
    }

    /**
     * The playlist currently in use — [playlistUrl] until a refresh replaces it. Callers holding
     * the original URL can ask this before deciding it has expired.
     */
    val liveUrl: String
        @Synchronized get() = livePlaylistUrl

    @Synchronized
    private fun segmentCount(): Int = segments.size

    @Synchronized
    private fun segmentUrlAt(index: Int): String? = segments.getOrNull(index)?.url

    @Synchronized
    private fun initUrl(): String? = initSegmentUrl

    @Synchronized
    /**
     * The fragment a seek to [positionMs] should start reading from.
     *
     * ## The restart this had
     *
     * It was `indexOfFirst { ... }.coerceAtLeast(0)`, and `indexOfFirst` answers -1 when nothing
     * matches. Coercing that to 0 turned "past the end of the track" into "the first fragment", so a
     * seek beyond the last fragment silently started the song again from the beginning (issue #33).
     *
     * That is reachable from the lyrics: clicking a line seeks to its timestamp, and a lyric sheet
     * matched from a longer song carries timestamps past this track's end. Which is what the report
     * described, "when you click on the text, playback starts from the very beginning".
     *
     * Past the end now means what it says: no fragment, so the decoder sees EOF and the track ends,
     * exactly as if it had played out. A position inside the track that matches nothing can only be
     * rounding at the boundary, and takes the last fragment.
     */
    private fun startIndexFor(positionMs: Long): Int {
        if (segments.isEmpty()) return 0
        if (totalDurationMs > 0 && positionMs >= totalDurationMs) return segments.size
        val match = segments.indexOfFirst { it.startTimeMs + it.durationMs > positionMs }
        return if (match >= 0) match else segments.lastIndex
    }

    private fun resolveUrl(base: String, rel: String): String {
        return URL(URL(base), rel).toString()
    }

    fun getInputStream(startPositionMs: Long): InputStream {
        return HlsInputStream(startPositionMs)
    }

    inner class HlsInputStream(startPositionMs: Long) : InputStream() {
        private var currentSegmentIndex = startIndexFor(startPositionMs)
        private var currentStream: InputStream? = null
        private var isClosed = false
        private var emittedInit = false

        override fun read(): Int {
            val b = ByteArray(1)
            val n = read(b, 0, 1)
            return if (n == -1) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (isClosed) return -1

            while (true) {
                if (currentStream == null) {
                    if (!emittedInit && initUrl() != null) {
                        val cached = initSegmentData
                        currentStream = if (cached != null) {
                            java.io.ByteArrayInputStream(cached)
                        } else {
                            openSigned { initUrl() }
                        }
                        emittedInit = true
                    } else if (currentSegmentIndex < segmentCount()) {
                        currentStream = openSigned { segmentUrlAt(currentSegmentIndex) }
                        currentSegmentIndex++
                    } else {
                        return -1 // EOF
                    }
                }

                if (currentStream != null) {
                    val read = currentStream!!.read(b, off, len)
                    if (read == -1) {
                        currentStream?.close()
                        currentStream = null
                    } else {
                        return read
                    }
                }
            }
        }

        /**
         * Opens whatever [url] resolves to, re-signing the playlist first when that URL has run
         * out of time and once more if the CDN turns it down anyway. [url] is re-read after a
         * refresh so it picks up the new signature.
         */
        private fun openSigned(url: () -> String?): InputStream {
            val current = url() ?: throw IOException("No fragment to read")
            if (SignedUrl.isExpired(current)) {
                Logger.e("HlsStreamAdapter", "Fragment signature lapsed; re-signing the playlist")
                refreshSegments()
            }
            return try {
                openHttpStream(url() ?: current)
            } catch (e: IOException) {
                if (!refreshSegments()) throw e
                openHttpStream(url() ?: throw e)
            }
        }

        private fun openHttpStream(url: String): InputStream {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            val t0 = System.currentTimeMillis()
            Logger.e("HlsStreamAdapter", "Fetching segment: $url")
            val response = client.newCall(req).execute()
            Logger.e("HlsStreamAdapter", "Response received in ${System.currentTimeMillis() - t0}ms")
            if (!response.isSuccessful) {
                response.close()
                throw IOException("HTTP ${response.code} for segment")
            }
            return response.body?.byteStream() ?: throw IOException("Empty segment body")
        }

        override fun close() {
            isClosed = true
            currentStream?.close()
            currentStream = null
        }
    }
}

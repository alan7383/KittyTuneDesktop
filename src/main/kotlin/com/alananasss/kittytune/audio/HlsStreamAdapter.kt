package com.alananasss.kittytune.audio

import com.alananasss.kittytune.data.drm.CencDecrypter
import com.alananasss.kittytune.data.drm.HlsEncryptionInfo
import com.alananasss.kittytune.data.drm.SoundCloudDrm
import com.alananasss.kittytune.data.drm.WidevineLicenseSession
import com.alananasss.kittytune.data.drm.isExpired
import com.alananasss.kittytune.utils.Logger
import com.alananasss.kittytune.utils.SignedUrl
import okhttp3.Request
import java.io.ByteArrayInputStream
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
 * ## Protected streams
 *
 * `ctr-encrypted-hls` playlists are CENC-encrypted: an `#EXT-X-KEY` line carrying a Widevine PSSH,
 * an init segment describing the encryption in `tenc`, and fragments whose samples are AES-CTR'd
 * with per-sample IVs from `senc`. With a [licenseToken] and a registered
 * [WidevineCdm][com.alananasss.kittytune.data.drm.WidevineCdm] the adapter acquires the content key
 * once, rewrites the init segment to read as clear, and decrypts each fragment as it is fetched, so
 * the decoder downstream sees an ordinary `mp4a` stream and nothing about it has to change.
 *
 * @param playlistUrl the playlist this adapter was built for. Stays fixed as the adapter's
 *   identity even after a refresh swaps in re-signed URLs, so callers can still tell which
 *   stream it belongs to.
 * @param licenseToken the `licenseAuthToken` resolved alongside a protected stream URL; ignored for
 *   clear playlists, required for protected ones.
 */
class HlsStreamAdapter(
    val playlistUrl: String,
    val headers: Map<String, String>,
    /**
     * Settable so an adapter built ahead of time can be handed to the engine that ends up playing it.
     */
    internal var refreshPlaylistUrl: ((failedUrl: String) -> String?)? = null,
) {

    /**
     * How a licensed session is obtained.
     *
     * Playback uses the shared one; a download sets [SoundCloudDrm.sessionForDownload] so it works on
     * its own CDM instance and cannot reset the session of the track currently playing.
     */
    internal var sessionProvider: (HlsEncryptionInfo, String) -> WidevineLicenseSession = SoundCloudDrm::session

    /**
     * Reports `(fragments read, fragments total)` as the stream is consumed.
     *
     * Only downloads need it: playback never knows how long a track will take to read, but a download
     * writes every fragment to disk and can show a percentage from that.
     */
    @Volatile
    var onFragmentProgress: ((Int, Int) -> Unit)? = null

    /**
     * The `licenseAuthToken` resolved alongside a protected stream URL.
     *
     * Set by the engine that owns the adapter, because the token belongs to the track rather than to
     * the playlist and the adapter is built before the track is known in the prefetch path. Ignored
     * for clear playlists, required before the first protected fragment is read.
     */
    @Volatile
    var licenseToken: String? = null

    class Segment(val url: String, val durationMs: Long, val startTimeMs: Long)

    private class Playlist(
        val initSegmentUrl: String?,
        val segments: List<Segment>,
        val totalDurationMs: Long,
        val encryption: HlsEncryptionInfo?
    )

    private var initSegmentUrl: String? = null
    private var initSegmentData: ByteArray? = null

    /** Set when the playlist declares a Widevine key; null for the ordinary clear stream. */
    private var encryption: HlsEncryptionInfo? = null

    /** The licensed CDM session for this stream; the key it holds never leaves the CDM. */
    @Volatile
    private var drmSession: WidevineLicenseSession? = null

    /**
     * How a session this stream no longer needs is handed back.
     *
     * Playback's session is closed, which lets a CDM that costs something to keep alive (the WSL
     * sidecar) stop. A session that is not playback's current one is left alone: a download releases
     * its own when it is done.
     */
    internal var sessionRelease: (WidevineLicenseSession) -> Unit = SoundCloudDrm::releaseSession

    /**
     * Fragments of a protected stream, already decrypted, by index.
     *
     * Every reader of the stream (the decoder, the upfront audio analysis, a seek) would otherwise
     * decrypt each fragment again, and keep the CDM busy for it. Once all of them are here the track
     * never needs the CDM again, so the session is released at once ([releaseDrm]). Bounded by
     * [CLEAR_CACHE_LIMIT]: past it (an hour-long mix) nothing is kept and the CDM simply stops
     * when it goes idle.
     */
    private val clearFragments = java.util.concurrent.ConcurrentHashMap<Int, ByteArray>()
    private val clearBytes = java.util.concurrent.atomic.AtomicLong()

    @Volatile
    private var clearCacheFull = false

    /** How the fragment samples are encrypted, read from the init segment's `tenc` box. */
    @Volatile
    private var trackEncryption: CencDecrypter.TrackEncryption? = null

    /** The first fragment, fetched ahead of time for a track that is up next; see [prefetchOpening]. */
    @Volatile
    private var openingSegmentData: ByteArray? = null
    private var segments: List<Segment> = emptyList()

    var totalDurationMs = 0L
        private set

    /** True when this stream needs its fragments decrypted before they reach the decoder. */
    val isEncrypted: Boolean get() = encryption != null

    /** What we actually fetch from; [playlistUrl] is only the identity we were built with. */
    private var livePlaylistUrl = playlistUrl
    private var lastFailedRefreshAtMs = 0L

    init {
        val playlist = loadPlaylist(playlistUrl)
        initSegmentUrl = playlist.initSegmentUrl
        segments = playlist.segments
        totalDurationMs = playlist.totalDurationMs
        encryption = playlist.encryption
        initSegmentData = playlist.initSegmentUrl?.let { fetchInitSegment(it) }
    }

    companion object {
        // Derived from ProxyManager's shared client: same connection pool and dispatcher as every
        // other caller, with this module's own short timeouts (RAM, issue #33).
        private val client = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient().newBuilder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** How long to leave a refresh that did not help alone, so a dead track cannot spin. */
        private const val REFRESH_RETRY_BACKOFF_MS = 5_000L

        /** About fifty minutes of AAC 160: enough for nearly every track, bounded for mixes. */
        private const val CLEAR_CACHE_LIMIT = 64L * 1024 * 1024
    }

    /** Keeps a decrypted fragment; once every fragment is kept, the CDM is no longer needed. */
    private fun rememberClear(index: Int, bytes: ByteArray) {
        if (!isEncrypted || clearCacheFull) return
        if (clearFragments.putIfAbsent(index, bytes) == null &&
            clearBytes.addAndGet(bytes.size.toLong()) > CLEAR_CACHE_LIMIT
        ) {
            clearCacheFull = true
            clearFragments.clear()
            Logger.d("HlsStreamAdapter", "Track too long to keep decrypted; the CDM stops when idle instead")
            return
        }
        val total = segmentCount()
        if (total > 0 && clearFragments.size >= total) {
            Logger.d("HlsStreamAdapter", "All $total fragments decrypted; releasing the DRM session")
            releaseDrm()
        }
    }

    /**
     * Hands the licensed session back, because nothing of this stream needs decrypting any more: it is
     * fully decrypted, or the engine dropped it. Safe to call more than once; a later read that still
     * needs the CDM simply opens a new session.
     */
    fun releaseDrm() {
        val session = synchronized(this) { drmSession.also { drmSession = null } } ?: return
        runCatching { sessionRelease(session) }
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

        return Playlist(initUrl, parsed, currentStartTime, HlsEncryptionInfo.parse(body))
    }

    /**
     * Opens (or reuses) the licensed CDM session for a protected playlist, once.
     *
     * Deferred to the first fragment rather than done in the constructor: the license is only worth
     * asking for if the track actually plays, and the engine primes the opening fragment while the
     * previous track is still running, so the exchange happens off the playback path.
     *
     * Failures become an [IOException] so the engine's existing "this URL did not work" recovery
     * handles them, rather than a DRM error escaping a background thread.
     */
    private fun acquireSession(): WidevineLicenseSession {
        // An expired session lost its keys with its CDM process (the WSL sidecar stops when idle).
        drmSession?.takeIf { !it.isExpired }?.let { return it }
        synchronized(this) {
            drmSession?.takeIf { !it.isExpired }?.let { return it }
            val info = encryption
                ?: throw IOException("Stream is not DRM-protected but a license was requested")
            if (!info.isSampleAes) {
                throw IOException("Unsupported HLS encryption METHOD=${info.method} on this stream")
            }
            val token = licenseToken
            if (token.isNullOrBlank()) {
                throw IOException("This stream is DRM-protected but no license token was resolved for it")
            }
            val session = try {
                sessionProvider(info, token)
            } catch (e: Exception) {
                Logger.e("HlsStreamAdapter", "Could not open a Widevine session: ${e.message}")
                throw IOException(e.message ?: "Widevine license failed", e)
            }
            drmSession = session
            return session
        }
    }

    /**
     * Decrypts one fetched fragment, or hands it back untouched for a clear stream.
     *
     * A protected fragment is rewritten to look clear: its samples are decrypted and its `senc` /
     * `saiz` / `saio` boxes are dropped.
     */
    private fun decryptSegment(bytes: ByteArray): ByteArray {
        if (!isEncrypted) return bytes
        val track = trackEncryption
            ?: throw IOException("Protected stream whose init segment carried no tenc box")
        val session = acquireSession()
        return try {
            try {
                SoundCloudDrm.decryptSegment(bytes, track, session)
            } catch (e: Exception) {
                // The daemon stopped between the check and the call: once more, on a fresh session.
                if (!session.isExpired) throw e
                SoundCloudDrm.decryptSegment(bytes, track, acquireSession())
            }
        } catch (e: Exception) {
            Logger.e("HlsStreamAdapter", "Fragment decryption failed: ${e.message}")
            throw IOException(e.message ?: "DRM fragment could not be decrypted", e)
        }
    }

    /** The ISOBMFF header is the same audio whatever the signature, so it is cached once. */
    private fun fetchInitSegment(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val raw = response.body?.bytes() ?: return null
                if (!isEncrypted) return raw
                // A protected track is described by its init segment: `tenc` says how the samples are
                // encrypted, and the entry has to be renamed back so the demuxer sees a clear track.
                trackEncryption = CencDecrypter.parseInit(raw)
                if (trackEncryption == null) {
                    Logger.e("HlsStreamAdapter", "Encrypted playlist but the init segment has no tenc box")
                }
                SoundCloudDrm.clearInit(raw)
            }
        } catch (e: Exception) {
            Logger.e("HlsStreamAdapter", "Failed to cache init segment", e)
            null
        }
    }

    /**
     * Downloads the first fragment now, so a stream opened from the start has its first seconds in memory and
     * plays at once. Used for the track up next, which is often skipped to within seconds (issue #66).
     */
    fun prefetchOpening() {
        if (openingSegmentData != null) return
        val url = segmentUrlAt(0) ?: return
        openingSegmentData = try {
            val request = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes()?.let { decryptSegment(it) } else null
            }?.also { rememberClear(0, it) }
        } catch (e: Exception) {
            Logger.e("HlsStreamAdapter", "Could not prefetch the first fragment: ${e.message}")
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

    val hasInitSegment: Boolean
        @Synchronized get() = initSegmentUrl != null

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
                            openSigned(null) { initUrl() }
                        }
                        emittedInit = true
                    } else if (currentSegmentIndex < segmentCount()) {
                        val index = currentSegmentIndex
                        val prefetched = openingSegmentData.takeIf { index == 0 }
                        val clear = clearFragments[index]
                        currentStream = when {
                            clear != null -> java.io.ByteArrayInputStream(clear)
                            prefetched != null -> java.io.ByteArrayInputStream(prefetched)
                            else -> openSigned(index) { segmentUrlAt(index) }
                        }
                        currentSegmentIndex++
                        onFragmentProgress?.invoke(currentSegmentIndex, segmentCount())
                    } else {
                        return -1 // EOF
                    }
                }

                if (currentStream != null) {
                    val read = currentStream!!.read(b, off, len)
                    if (read == -1) {
                        currentStream?.close()
                        currentStream = null
                        // Once we've moved past segment 0, the prefetched opening segment data is
                        // dead weight (0.5-2 MB). Null it so GC can collect it. A re-open from
                        // position 0 will re-fetch — one HTTP request, not a user-visible delay
                        // (audit Phase 2 RAM gain).
                        if (currentSegmentIndex > 0) {
                            openingSegmentData = null
                        }
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
        private fun openSigned(index: Int?, url: () -> String?): InputStream {
            val current = url() ?: throw IOException("No fragment to read")
            if (SignedUrl.isExpired(current)) {
                Logger.e("HlsStreamAdapter", "Fragment signature lapsed; re-signing the playlist")
                refreshSegments()
            }
            return try {
                openHttpStream(url() ?: current, index)
            } catch (e: IOException) {
                if (!refreshSegments()) throw e
                openHttpStream(url() ?: throw e, index)
            }
        }

        /** [index] is the fragment's place in the playlist, or null for the init segment. */
        private fun openHttpStream(url: String, index: Int?): InputStream {
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
            if (!isEncrypted) {
                return response.body?.byteStream() ?: throw IOException("Empty segment body")
            }
            // A protected fragment has to be held whole: its samples are decrypted per `senc` entry
            // and the encryption boxes are stripped, so the demuxer never sees the original bytes.
            val decrypted = response.use { it.body?.bytes()?.let { bytes -> decryptSegment(bytes) } }
                ?: throw IOException("Empty segment body")
            if (index != null) rememberClear(index, decrypted)
            return ByteArrayInputStream(decrypted)
        }

        override fun close() {
            isClosed = true
            currentStream?.close()
            currentStream = null
        }
    }
}

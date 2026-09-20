package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
import com.alananasss.kittytune.audio.providers.ProviderIsrc
import com.alananasss.kittytune.audio.providers.IsrcResolver
import com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider
import com.alananasss.kittytune.audio.providers.tidal.TidalAudioProvider
import com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProvider
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.CookieStore
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.Config
import com.alananasss.kittytune.utils.Logger
import com.alananasss.kittytune.utils.SignedUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private object ExtractorDownloader : Downloader() {
    private val baseClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .cookieJar(object : CookieJar {
                private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    val existing = cookieStore.getOrPut(host) { mutableListOf() }
                    val updated = existing.associateBy { it.name }.toMutableMap()
                    cookies.forEach { updated[it.name] = it }
                    cookieStore[host] = updated.values.toMutableList()
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val host = url.host
                    val validCookies = mutableListOf<Cookie>()
                    cookieStore.forEach { (domain, domainCookies) ->
                        if (host == domain || host.endsWith(".$domain")) {
                            validCookies.addAll(domainCookies)
                        }
                    }
                    return validCookies
                }
            })
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                if (host.contains("youtube.com") || host.contains("youtu.be")) {
                    val existingCookies = request.headers("Cookie").joinToString("; ")
                    if (!existingCookies.contains("CONSENT=")) {
                        val newCookie = if (existingCookies.isNotEmpty()) "$existingCookies; CONSENT=YES+cb" else "CONSENT=YES+cb"
                        val newRequest = request.newBuilder().header("Cookie", newCookie).build()
                        return@addInterceptor chain.proceed(newRequest)
                    }
                }
                chain.proceed(request)
            }
            .build()
    }

    private val client: OkHttpClient
        get() = com.alananasss.kittytune.data.network.ProxyManager.configureOkHttpClient(baseClient.newBuilder()).build()

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val okHttpRequest = okhttp3.Request.Builder().url(request.url())
        request.headers().forEach { (key, values) ->
            values.forEach { value -> okHttpRequest.addHeader(key, value) }
        }

        when (request.httpMethod()) {
            "GET" -> okHttpRequest.get()
            "HEAD" -> okHttpRequest.head()
            "POST" -> {
                val body = request.dataToSend()?.toRequestBody() ?: byteArrayOf().toRequestBody()
                okHttpRequest.post(body)
            }
            else -> throw IOException("unsupported http method: ${request.httpMethod()}")
        }

        val response = client.newCall(okHttpRequest.build()).execute()
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            response.body?.string(),
            response.request.url.toString()
        )
    }
}

/**
 * Desktop port of the Android StreamResolver.
 * - Context removed; PlayerPreferences()/RetrofitClient.create() take no args.
 * - WebView CookieManager -> CookieStore.
 * - Widevine DRM (ctr/cbc-encrypted-hls) is NOT decryptable on desktop, so those
 *   candidates are dropped: playback relies on progressive/plain-HLS + YouTube fallback.
 *   FFmpeg reads plain HLS (m3u8) natively.
 */
object StreamResolver {

    private val baseClient by lazy {
        OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .cookieJar(CookieStore)
            .build()
    }

    private val client: OkHttpClient
        get() = com.alananasss.kittytune.data.network.ProxyManager.configureOkHttpClient(baseClient.newBuilder()).build()
    /**
     * Resolved stream URLs, bounded (issue #33). Entries go stale within minutes — see
     * [OPAQUE_STREAM_TTL_MS] — but an expired one was only dropped when that same track came round
     * again, so the map still grew by one per track played or previewed for the whole session.
     */
    private val streamCache =
        com.alananasss.kittytune.core.BoundedCache<Long, CachedStream>(MAX_CACHED_STREAMS)

    /** Comfortably more than a queue, since a stale entry costs one re-resolve and nothing else. */
    private const val MAX_CACHED_STREAMS = 200

    /** Local files never go stale; only the bookkeeping does. */
    private const val LOCAL_CACHE_TTL_MS = 30 * 60 * 1000L

    /**
     * How long to trust a network URL that carries no readable deadline. SoundCloud's signed
     * links last about three minutes, so nothing opaque is worth keeping past that either.
     */
    private const val OPAQUE_STREAM_TTL_MS = 3 * 60 * 1000L

    /**
     * A resolved stream plus the point it stops being playable.
     *
     * The cache used to hand out anything younger than 30 minutes, but a SoundCloud CDN
     * signature dies after ~3, so sitting on one track long enough to outlive the queue's
     * prefetch meant the next skip (or a seek on the current track) fed FFmpeg a URL the CDN
     * answers 403 to, and playback only came back after its reconnect backoff gave up.
     */
    private class CachedStream(val stream: ResolvedStream) {
        private val cachedAtMs = System.currentTimeMillis()
        private val isNetwork = SignedUrl.isNetworkUrl(stream.url)
        private val expiresAtMs = SignedUrl.expiryEpochMs(stream.url)

        fun isUsable(marginMs: Long = SignedUrl.DEFAULT_MARGIN_MS): Boolean {
            val now = System.currentTimeMillis()
            if (!isNetwork) return now - cachedAtMs < LOCAL_CACHE_TTL_MS
            val deadline = expiresAtMs ?: return now - cachedAtMs < OPAQUE_STREAM_TTL_MS
            return now + marginMs < deadline
        }
    }

    fun evictStream(trackId: Long) {
        streamCache.remove(trackId)
    }

    /** The cached URL for [trackId] while it is still playable, else null. */
    fun cachedStreamUrl(trackId: Long): String? =
        streamCache[trackId]?.takeIf { it.isUsable() }?.stream?.url

    fun init() {}

    init {
        try {
            NewPipe.init(ExtractorDownloader)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Whether SoundCloud will refuse to stream this track to us, so the YouTube fallback is the only
     * way to hear it.
     *
     * Judged on the policy fields alone. An absent media block used to count too, and that was wrong in
     * a way that broke ordinary playback: a `Track` with no transcodings is one nobody has asked the API
     * about yet, not one the API refuses. Liked tracks are deliberately slimmed for memory — the signed
     * stream URLs in them expire within minutes of launch and are dead weight — so once that landed,
     * *every liked track* looked restricted and went to YouTube. Reported as a three-minute song playing
     * for twenty seconds (issue #33).
     *
     * Nothing is lost by dropping the clause: [resolveFromSoundCloudWithDrm] re-fetches a track whose
     * transcodings are missing and decides from the fresh data, which is the right place for that
     * decision. And if the fresh data really has no usable candidate, the fallback still runs from there.
     */
    fun isRestricted(track: Track): Boolean {
        return track.policy == "SNIP" ||
            track.policy == "BLOCK" ||
            track.monetizationModel == "SUB_HIGH_TIER"
    }

    suspend fun resolveStream(track: Track, forDownload: Boolean = false): String? {
        return resolveStreamWithDrm(track, forDownload)?.url
    }

    suspend fun resolveStreamWithDrm(track: Track, forDownload: Boolean = false): ResolvedStream? {
        if (!forDownload) {
            val cached = streamCache[track.id]
            if (cached != null) {
                if (cached.isUsable()) return cached.stream
                streamCache.remove(track.id)
                // One expiry is a good moment to drop the others: they all age on the same clock.
                streamCache.retainWhere { it.isUsable() }
            }
        }
        val result = withContext(Dispatchers.IO) {
            try {
                val localTrack = DownloadManager.getLocalTrack(track.id)
                if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                    val fileExists = java.io.File(localTrack.localAudioPath).exists()
                    if (fileExists) {
                        return@withContext ResolvedStream(localTrack.localAudioPath)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (track.source == "youtube") {
                val url = resolveFromYoutubeDirect(track)
                return@withContext url?.let { ResolvedStream(it) }
            }

            if (track.source == "spotify") {
                Logger.d("StreamResolver", "Resolving Spotify track via configured audio providers: ${track.title} by ${track.displayArtist}")
                val providerStream = resolveViaProviders(track, forDownload)
                if (providerStream != null) {
                    return@withContext providerStream
                }
                val streamUrl = resolveViaNewPipe(track)
                if (streamUrl != null) {
                    return@withContext ResolvedStream(streamUrl)
                }
                try {
                    val query = "${track.displayArtist} ${track.title}".trim()
                    val scResults = RetrofitClient.create().searchTracks(query, limit = 5)
                    val bestScTrack = scResults.collection.firstOrNull()
                    if (bestScTrack != null) {
                        val scStream = resolveFromSoundCloudWithDrm(bestScTrack, forDownload)
                        if (scStream != null) return@withContext scStream
                    }
                } catch (e: Exception) {
                    Logger.w("StreamResolver", "Failed SoundCloud fallback for Spotify track: ${e.message}")
                }
                return@withContext null
            }

            if (track.source in listOf("deezer", "tidal", "qobuz") ||
                track.permalink?.startsWith("deezer:") == true ||
                track.permalink?.startsWith("tidal:") == true ||
                track.permalink?.startsWith("qobuz:") == true) {
                Logger.d("StreamResolver", "Resolving direct provider track (${track.source}): ${track.title} by ${track.displayArtist}")
                val providerStream = resolveViaProviders(track, forDownload)
                if (providerStream != null) {
                    return@withContext providerStream
                }
                val allowYoutubeFallback = PlayerPreferences().getYouTubeFallbackEnabled()
                if (allowYoutubeFallback) {
                    Logger.w("StreamResolver", "Provider track (${track.source}) resolution failed, trying YouTube fallback for: ${track.title}")
                    val ytFallback = resolveViaNewPipe(track)
                    if (ytFallback != null) {
                        return@withContext ResolvedStream(ytFallback)
                    }
                }
                Logger.w("StreamResolver", "Could not resolve provider track (${track.source}): ${track.title}")
                return@withContext null
            }

            val prefs = PlayerPreferences()
            val allowYoutube = prefs.getYouTubeFallbackEnabled()

            if (isRestricted(track)) {
                val providerStream = resolveViaProviders(track, forDownload)
                if (providerStream != null) {
                    return@withContext providerStream
                }
                if (allowYoutube) {
                    val streamUrl = resolveViaNewPipe(track)
                    if (streamUrl != null) {
                        return@withContext ResolvedStream(streamUrl)
                    }
                }
            }

            val scStream = resolveFromSoundCloudWithDrm(track, forDownload)
            if (scStream != null) {
                scStream
            } else {
                val providerFallback = resolveViaProviders(track, forDownload)
                if (providerFallback != null) {
                    providerFallback
                } else if (allowYoutube) {
                    Logger.w("StreamResolver", "All sources failed for '${track.title}', trying final YouTube fallback")
                    val ytUrl = resolveViaNewPipe(track)
                    ytUrl?.let { ResolvedStream(it) }
                } else {
                    null
                }
            }
        }
        if (result != null && !forDownload) {
            streamCache[track.id] = CachedStream(result)
        }
        return result
    }

    suspend fun resolveViaProviders(
        track: Track,
        forDownload: Boolean = false
    ): ResolvedStream? {
        val prefs = PlayerPreferences()
        val configuredOrder = prefs.getAudioProviderOrder()
        val order = when {
            track.source == "deezer" || track.permalink?.startsWith("deezer:") == true -> {
                listOf(AudioProviderOrderItem.DEEZER) + (configuredOrder - AudioProviderOrderItem.DEEZER)
            }
            track.source == "tidal" || track.permalink?.startsWith("tidal:") == true -> {
                listOf(AudioProviderOrderItem.TIDAL) + (configuredOrder - AudioProviderOrderItem.TIDAL)
            }
            track.source == "qobuz" || track.permalink?.startsWith("qobuz:") == true -> {
                listOf(AudioProviderOrderItem.QOBUZ) + (configuredOrder - AudioProviderOrderItem.QOBUZ)
            }
            else -> configuredOrder
        }
        val mediaId = track.permalink?.takeIf { it.isNotBlank() } ?: track.id.toString()
        val title = track.title?.trim().orEmpty()
        val artist = (track.displayArtist.ifBlank { track.user?.username.orEmpty() }).trim()
        val album = (track.publisherMetadata?.albumTitle ?: track.publisherMetadata?.releaseTitle).orEmpty()
        val durationMs = track.durationMs ?: 0L

        var isrc = ProviderIsrc.normalize(track.publisherMetadata?.isrc)
        if (isrc == null && title.isNotBlank() && artist.isNotBlank()) {
            isrc = IsrcResolver.resolveAndValidate(
                candidateIsrc = null,
                song = title,
                artist = artist,
                durationSeconds = (durationMs / 1000).toInt()
            )
        }
        val explicitArtists = track.artists?.map { it.name.trim() }?.filter { it.isNotBlank() }.orEmpty()
        val splitArtists = artist.split(Regex(""",\s*|&\s*|\s+feat\.?\s+|\s+ft\.?\s+""", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val artists = (explicitArtists + listOf(artist) + splitArtists).filter { it.isNotBlank() }.distinct()

        val attempted = mutableSetOf<AudioProviderOrderItem>()
        for (provider in order) {
            if (!attempted.add(provider)) continue
            try {
                when (provider) {
                    AudioProviderOrderItem.QOBUZ -> {
                        val country = prefs.getQobuzCountry()
                        val customInstances = prefs.getQobuzCustomInstances()
                        val quality = prefs.getQobuzQuality()
                        val query = QobuzAudioProvider.Query(
                            mediaId = mediaId,
                            title = title,
                            artists = artists,
                            album = album.ifBlank { null },
                            isrc = isrc,
                            durationMs = durationMs,
                            countryCode = country,
                            qualityCode = quality,
                            customInstances = customInstances
                        )
                        val resolved = QobuzAudioProvider.resolve(query)
                        if (resolved != null && resolved.mediaUri.isNotBlank()) {
                            Logger.i("StreamResolver", "Using Qobuz stream for '${track.title}': ${resolved.label}")
                            return ResolvedStream(resolved.mediaUri, mimeType = "audio/mp4")
                        }
                    }
                    AudioProviderOrderItem.TIDAL -> {
                        val quality = prefs.getTidalAudioQuality()
                        val endpoints = prefs.getTidalResolverEndpoints()
                        val query = TidalAudioProvider.Query(
                            mediaId = mediaId,
                            title = title,
                            artists = artists,
                            album = album.ifBlank { null },
                            isrc = isrc,
                            durationMs = durationMs
                        )
                        val resolved = TidalAudioProvider.resolve(
                            query = query,
                            cacheDir = AppDirs.cacheDir,
                            preferAtmos = false,
                            preferLiveDash = false,
                            audioQuality = quality,
                            resolverEndpoints = endpoints
                        )
                        if (resolved != null && resolved.mediaUri.isNotBlank()) {
                            Logger.i("StreamResolver", "Using Tidal stream for '${track.title}': ${resolved.label}")
                            return ResolvedStream(resolved.mediaUri, mimeType = resolved.mimeType)
                        }
                    }
                    AudioProviderOrderItem.DEEZER -> {
                        val resolverUrl = prefs.getDeezerResolverUrl()
                        val quality = prefs.getDeezerAudioQuality()
                        val fastMode = prefs.getDeezerFastMode()
                        val configuredProxyUrl = prefs.getDeezerProxyUrl()
                        val proxyMode = prefs.getDeezerProxyMode()
                        val globalProxyEnabled = prefs.getProxyEnabled()
                        val effectiveProxyUrl = DeezerAudioProvider.effectiveProxyUrl(
                            configuredProxyMode = proxyMode,
                            configuredProxyUrl = configuredProxyUrl,
                            globalProxyEnabled = globalProxyEnabled
                        )
                        val cookie = prefs.getDeezerCookie()
                        val useAccount = prefs.getDeezerUseAccount()
                        val query = DeezerAudioProvider.Query(
                            mediaId = mediaId,
                            title = title,
                            artists = artists,
                            album = album.ifBlank { null },
                            isrc = isrc,
                            durationMs = durationMs,
                            resolverUrl = resolverUrl,
                            quality = quality,
                            fastMode = fastMode,
                            proxyUrl = effectiveProxyUrl,
                            cookie = cookie,
                            useAccount = useAccount
                        )
                        val resolved = DeezerAudioProvider.resolve(query)
                        if (resolved != null && resolved.mediaUri.isNotBlank()) {
                            Logger.i("StreamResolver", "Using Deezer stream for '${track.title}': ${resolved.label}")
                            val mimeType = if (resolved.mediaUri.contains(".flac", ignoreCase = true) || resolved.label.contains("FLAC", ignoreCase = true)) "audio/flac" else "audio/mpeg"
                            return ResolvedStream(resolved.mediaUri, mimeType = mimeType)
                        }
                    }
                    AudioProviderOrderItem.YOUTUBE_MUSIC -> {
                        val ytUrl = resolveViaNewPipe(track)
                        if (ytUrl != null) {
                            Logger.i("StreamResolver", "Using YouTube stream for '${track.title}'")
                            return ResolvedStream(ytUrl)
                        }
                    }
                    AudioProviderOrderItem.SOUNDCLOUD -> {
                        if (track.source == "soundcloud" || track.source.isNullOrEmpty()) {
                            val scStream = resolveFromSoundCloudWithDrm(track, forDownload)
                            if (scStream != null) return scStream
                        } else if (track.source in listOf("deezer", "tidal", "qobuz")) {
                            Logger.d("StreamResolver", "Skipping SoundCloud text-search fallback for ${track.source} track: ${track.title}")
                        } else {
                            try {
                                val q = "$artist $title".trim()
                                val scResults = RetrofitClient.create().searchTracks(q, limit = 5)
                                val bestScTrack = scResults.collection.firstOrNull()
                                if (bestScTrack != null) {
                                    val scStream = resolveFromSoundCloudWithDrm(bestScTrack, forDownload)
                                    if (scStream != null) return scStream
                                }
                            } catch (e: Exception) {
                                Logger.w("StreamResolver", "SoundCloud search fallback failed: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.w("StreamResolver", "Provider $provider failed for '${track.title}': ${e.message}")
            }
        }
        return null
    }

    /**
     * How far a YouTube candidate's length may be from the track's before it is rejected.
     *
     * Wide enough for the usual differences — a fade, a bit of silence, an intro on the upload — and far
     * too narrow for a snippet or a teaser to slip through.
     */
    private const val DURATION_TOLERANCE_SEC = 12L

    private fun resolveViaNewPipe(track: Track): String? {
        return try {
            val cleanTitle = track.title?.replace(Regex("(?i)(\\[.*?\\]|\\(.*?\\))"), "")?.trim() ?: ""
            val artistName = track.user?.username ?: ""
            val query = "$cleanTitle $artistName audio"

            val youtubeService = ServiceList.YouTube
            val searchInfo = SearchInfo.getInfo(youtubeService, youtubeService.searchQHFactory.fromQuery(query, listOf("videos"), ""))
            val videoResults = searchInfo.relatedItems.filterIsInstance<StreamInfoItem>()

            if (videoResults.isEmpty()) return null

            // The first hit was taken blindly, and that is how a twenty-second clip came to stand in for a
            // three-minute song: YouTube's top result for a track name is frequently a snippet, a teaser or
            // a "sped up" edit. A substitute has to be the same length as the thing it replaces, so results
            // whose duration is nowhere near the track's are dropped, and among what is left the closest
            // wins (issue #33).
            val wantedSec = (track.durationMs ?: 0L) / 1000
            val firstResultUrl = if (wantedSec <= 0) {
                // No duration to check against — nothing better than the top hit, but nothing worse either.
                videoResults.first().url
            } else {
                val matched = videoResults
                    .filter { it.duration > 0 && kotlin.math.abs(it.duration - wantedSec) <= DURATION_TOLERANCE_SEC }
                    .minByOrNull { kotlin.math.abs(it.duration - wantedSec) }
                if (matched == null) {
                    println(
                        "StreamResolver: no YouTube result within ${DURATION_TOLERANCE_SEC}s of " +
                            "${wantedSec}s for '${track.title}' — refusing to substitute a different song"
                    )
                    return null
                }
                matched.url
            }
            val extractor = youtubeService.getStreamExtractor(firstResultUrl)
            extractor.fetchPage()

            val audioStreams = try {
                extractor.audioStreams
            } catch (e: Exception) {
                emptyList()
            }

            val bestAudioStream = audioStreams
                .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP && it.format == org.schabi.newpipe.extractor.MediaFormat.M4A && it.url != null }
                .maxByOrNull { it.averageBitrate }
                ?: audioStreams
                    .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP && it.url != null }
                    .maxByOrNull { it.averageBitrate }
                ?: audioStreams
                    .filter { it.url != null }
                    .maxByOrNull { it.averageBitrate }

            if (bestAudioStream != null) return bestAudioStream.url

            val videoStreams = try {
                extractor.videoStreams
            } catch (e: Exception) {
                emptyList()
            }

            val bestVideoStream = videoStreams
                .filter { it.url != null }
                .minByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: Int.MAX_VALUE }

            bestVideoStream?.url
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun resolveFromYoutubeDirect(track: Track): String? {
        val url = track.permalinkUrl ?: return null
        return try {
            val service = ServiceList.YouTube
            val extractor = service.getStreamExtractor(url)
            extractor.fetchPage()
            val best = extractor.audioStreams
                .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP && it.url != null }
                .maxByOrNull { it.averageBitrate }
                ?: extractor.audioStreams
                    .filter { it.url != null }
                    .maxByOrNull { it.averageBitrate }
            if (best != null) return best.url

            val muxed = extractor.videoStreams
                .filter { it.url != null }
                .minByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: Int.MAX_VALUE }
            muxed?.url
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun resolveFromSoundCloudWithDrm(track: Track, forDownload: Boolean): ResolvedStream? {
        val prefs = PlayerPreferences()
        val api = RetrofitClient.create()
        var trackToUse = track

        if (track.media == null || track.media.transcodings.isNullOrEmpty()) {
            try {
                val fetched = api.getTracksByIds(track.id.toString())
                if (fetched.isNotEmpty()) trackToUse = fetched[0] else return null
            } catch (e: Exception) {
                return null
            }
        }

        val transcodings = trackToUse.media?.transcodings ?: return null
        val qualityPref = prefs.getAudioQuality()

        // Desktop cannot decrypt Widevine, so DRM candidates are never allowed.
        val candidates = buildTranscodingCandidates(transcodings, qualityPref, forDownload, allowDrm = false)

        if (candidates.isEmpty()) {
            if (prefs.getYouTubeFallbackEnabled()) {
                val url = resolveViaNewPipe(track)
                return url?.let { ResolvedStream(it) }
            }
            return null
        }

        val token = SessionManager.awaitFreshAccessToken(
            force = tokenManagerShouldRefresh()
        ) ?: TokenManager.getAccessToken()
        var mutableToken = token

        for (candidate in candidates) {
            val protocol = candidate.format?.protocol ?: continue
            val apiUrl = candidate.url ?: continue

            val urlWithParams = if (apiUrl.contains("?")) "$apiUrl&client_id=${Config.CLIENT_ID}" else "$apiUrl?client_id=${Config.CLIENT_ID}"

            try {
                var response = client.newCall(buildStreamInfoRequest(urlWithParams, mutableToken)).execute()

                if (!response.isSuccessful && isAuthFailure(response.code)) {
                    val refreshedToken = SessionManager.awaitFreshAccessToken(staleToken = mutableToken, force = true)
                    if (!refreshedToken.isNullOrEmpty() && refreshedToken != mutableToken) {
                        response.close()
                        mutableToken = refreshedToken
                        response = client.newCall(buildStreamInfoRequest(urlWithParams, mutableToken)).execute()
                    } else if (!mutableToken.isNullOrEmpty()) {
                        response.close()
                        mutableToken = null
                        response = client.newCall(buildStreamInfoRequest(urlWithParams, mutableToken)).execute()
                    }
                }

                if (!response.isSuccessful) {
                    response.close()
                    continue
                }

                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                val streamInfoUrl = json.getString("url")
                val licenseAuthToken = json.optString("licenseAuthToken", null)

                val isHlsLike = protocol == "hls" || protocol.contains("encrypted-hls")
                if (isHlsLike) {
                    return ResolvedStream(streamInfoUrl, licenseAuthToken)
                }

                val finalRequest = okhttp3.Request.Builder().url(streamInfoUrl).build()
                val finalResponse = client.newCall(finalRequest).execute()
                finalResponse.body?.close()

                if (!finalResponse.isSuccessful) continue

                val finalUrl = finalResponse.request.url.toString()
                return ResolvedStream(finalUrl, licenseAuthToken)
            } catch (e: Exception) {
                e.printStackTrace()
                continue
            }
        }

        if (prefs.getYouTubeFallbackEnabled()) {
            val url = resolveViaNewPipe(track)
            return url?.let { ResolvedStream(it) }
        }
        return null
    }

    private fun tokenManagerShouldRefresh(): Boolean = TokenManager.shouldRefreshAccessToken()

    private fun buildTranscodingCandidates(
        transcodings: List<com.alananasss.kittytune.domain.Transcoding>,
        qualityPref: String,
        forDownload: Boolean,
        allowDrm: Boolean
    ): List<com.alananasss.kittytune.domain.Transcoding> {
        val candidates = mutableListOf<com.alananasss.kittytune.domain.Transcoding>()

        transcodings.find { it.format?.protocol == "progressive" }?.let { candidates.add(it) }

        if (qualityPref != "HIGH") {
            transcodings.find { it.format?.protocol == "hls" && it.format.mimeType?.contains("mpeg") == true }?.let { candidates.add(it) }
        }
        transcodings.find { it.format?.protocol == "hls" }?.let {
            if (!candidates.contains(it)) candidates.add(it)
        }

        if (allowDrm) {
            val cencPresets = listOf("aac_160k", "aac_96k", "abr_sq")
            for (preset in cencPresets) {
                transcodings.find { it.preset == preset && it.format?.protocol == "ctr-encrypted-hls" }?.let { candidates.add(it) }
                transcodings.find { it.preset == preset && it.format?.protocol == "cbc-encrypted-hls" }?.let { candidates.add(it) }
            }
            transcodings.filter { it.format?.protocol?.contains("encrypted") == true }.forEach {
                if (!candidates.contains(it)) candidates.add(it)
            }
        }

        return candidates
    }

    private fun buildStreamInfoRequest(url: String, token: String?): okhttp3.Request {
        val builder = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", Config.USER_AGENT)
            .header("Accept", "application/json")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")

        if (!token.isNullOrEmpty() && token != "null") {
            builder.header("Authorization", "OAuth $token")
        }

        CookieStore.cookieHeader("https://soundcloud.com")?.let {
            builder.header("Cookie", it)
        }

        return builder.build()
    }

    private fun isAuthFailure(code: Int): Boolean = code == 401 || code == 403

    suspend fun resolveStreamForBeatAnalysis(track: Track): ResolvedStream? {
        return withContext(Dispatchers.IO) {
            val cached = cachedStreamUrl(track.id)
            if (!cached.isNullOrBlank()) {
                Logger.d("StreamResolver", "[BeatAnalysis] Using cached stream for '${track.title}'")
                return@withContext ResolvedStream(cached)
            }

            val scStream = try {
                resolveFromSoundCloudWithDrm(track, forDownload = false)
            } catch (e: Exception) {
                Logger.w("StreamResolver", "[BeatAnalysis] Failed resolving SoundCloud stream: ${e.message}")
                null
            }
            if (scStream != null) {
                Logger.d("StreamResolver", "[BeatAnalysis] Using SoundCloud stream for '${track.title}' (drm=${scStream.isDrmProtected})")
                return@withContext scStream
            }

            val prefs = PlayerPreferences()
            if (prefs.getYouTubeFallbackEnabled()) {
                val ytUrl = try { resolveViaNewPipe(track) } catch (_: Exception) { null }
                if (ytUrl != null) {
                    Logger.d("StreamResolver", "[BeatAnalysis] Got YouTube stream for '${track.title}'")
                    return@withContext ResolvedStream(ytUrl)
                }
            }

            val providerStream = try {
                resolveViaProviders(track, forDownload = true)
            } catch (_: Exception) { null }
            if (providerStream != null) {
                Logger.d("StreamResolver", "[BeatAnalysis] Using provider stream for '${track.title}'")
                return@withContext providerStream
            }

            Logger.d("StreamResolver", "[BeatAnalysis] No stream found for '${track.title}'")
            null
        }
    }
}


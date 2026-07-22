package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.CookieStore
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.Config
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
    private val client = OkHttpClient.Builder()
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

    private val client = OkHttpClient.Builder().cookieJar(CookieStore).build()
    private val streamCache = ConcurrentHashMap<Long, Pair<Long, ResolvedStream>>()
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

    fun evictStream(trackId: Long) {
        streamCache.remove(trackId)
    }

    init {
        try {
            NewPipe.init(ExtractorDownloader)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isRestricted(track: Track): Boolean {
        return track.policy == "SNIP" ||
            track.policy == "BLOCK" ||
            track.monetizationModel == "SUB_HIGH_TIER" ||
            track.media?.transcodings.isNullOrEmpty()
    }

    suspend fun resolveStream(track: Track, forDownload: Boolean = false): String? {
        return resolveStreamWithDrm(track, forDownload)?.url
    }

    suspend fun resolveStreamWithDrm(track: Track, forDownload: Boolean = false): ResolvedStream? {
        if (!forDownload) {
            val cached = streamCache[track.id]
            if (cached != null && (System.currentTimeMillis() - cached.first) < CACHE_TTL_MS) {
                return cached.second
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

            val prefs = PlayerPreferences()
            val allowYoutube = prefs.getYouTubeFallbackEnabled()

            if (isRestricted(track) && allowYoutube) {
                val streamUrl = resolveViaNewPipe(track)
                if (streamUrl != null) {
                    return@withContext ResolvedStream(streamUrl)
                }
            }

            return@withContext resolveFromSoundCloudWithDrm(track, forDownload)
        }
        if (result != null && !forDownload) {
            streamCache[track.id] = System.currentTimeMillis() to result
        }
        return result
    }

    private fun resolveViaNewPipe(track: Track): String? {
        return try {
            val cleanTitle = track.title?.replace(Regex("(?i)(\\[.*?\\]|\\(.*?\\))"), "")?.trim() ?: ""
            val artistName = track.user?.username ?: ""
            val query = "$cleanTitle $artistName audio"

            val youtubeService = ServiceList.YouTube
            val searchInfo = SearchInfo.getInfo(youtubeService, youtubeService.searchQHFactory.fromQuery(query, listOf("videos"), ""))
            val videoResults = searchInfo.relatedItems.filterIsInstance<StreamInfoItem>()

            if (videoResults.isEmpty()) return null

            val firstResultUrl = videoResults.first().url
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
}


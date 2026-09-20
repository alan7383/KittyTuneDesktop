package com.alananasss.kittytune.data.cover

import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Fetches Apple Music artist motion artwork (HLS / MP4 canvas) for the artist profile screen.
 *
 * 1. Searches for the artist by name on Apple Music catalog.
 * 2. Fetches the artist details with `extend=editorialVideo,editorialArtwork`.
 * 3. Extracts the motion video URL (e.g. motionArtistFullscreen16x9, motionArtistSquare1x1, motionDetailRaw).
 *
 * Results are cached in memory for 24 hours.
 */
object AppleMusicArtistBackgroundProvider {

    private const val TAG = "AppleArtistMotion"
    private const val AMP_BASE = "https://amp-api.music.apple.com"
    private const val DEFAULT_STOREFRONT = "us"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private data class CacheEntry(
        val videoUrl: String?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val NEGATIVE_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getByArtistName(
        artistName: String,
        storefront: String = DEFAULT_STOREFRONT,
    ): String? = withContext(Dispatchers.IO) {
        if (artistName.isBlank()) return@withContext null

        val localSf = Locale.getDefault().country.lowercase().takeIf { it.isNotBlank() && it.length == 2 } ?: "us"
        val sf = if (storefront != DEFAULT_STOREFRONT) storefront else localSf

        val key = cacheKey("artist", artistName, sf)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let {
            return@withContext it.videoUrl
        }

        val neg = negativeCache[key]
        if (neg != null && System.currentTimeMillis() - neg < NEGATIVE_CACHE_TTL_MS) {
            return@withContext null
        }

        var result = searchAndFetchArtistMotion(artistName.trim(), sf)
        if (result == null && sf != "us") {
            result = searchAndFetchArtistMotion(artistName.trim(), "us")
        }
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        } else {
            negativeCache[key] = System.currentTimeMillis()
        }
        return@withContext result
    }

    private suspend fun searchAndFetchArtistMotion(
        artistName: String,
        storefront: String,
    ): String? {
        return runCatching {
            var token = AppleMusicCanvasProvider.getToken()
            var matchedArtistId = searchArtistId(artistName, storefront, token)

            if (matchedArtistId == null) {
                // Retry once with a force refreshed token if search returned nothing
                token = AppleMusicCanvasProvider.getToken(forceRefresh = true)
                matchedArtistId = searchArtistId(artistName, storefront, token)
            }

            if (matchedArtistId == null) {
                Logger.d(TAG, "No matching Apple Music artist found for: $artistName")
                return@runCatching null
            }

            fetchArtistMotionById(matchedArtistId, storefront, token)
        }.getOrElse { e ->
            Logger.w(TAG, "Error fetching artist motion for '$artistName': ${e.message}")
            null
        }
    }

    private fun searchArtistId(
        artistName: String,
        storefront: String,
        token: String,
    ): String? {
        val cleanName = cleanArtistName(artistName)
        if (cleanName.isBlank()) return null

        val url = "$AMP_BASE/v1/catalog/$storefront/search"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("term", cleanName)
            ?.addQueryParameter("types", "artists")
            ?.addQueryParameter("limit", "5")
            ?.build()
            ?: return null

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body.string())
                val artists = root.optJSONObject("results")
                    ?.optJSONObject("artists")
                    ?.optJSONArray("data")
                    ?: return@use null

                val normTarget = normalize(cleanName)
                for (i in 0 until artists.length()) {
                    val item = artists.optJSONObject(i) ?: continue
                    val name = normalize(item.optJSONObject("attributes")?.optString("name").orEmpty())
                    if (name == normTarget || name.contains(normTarget) || normTarget.contains(name)) {
                        return@use item.optString("id").takeIf { it.isNotBlank() }
                    }
                }
                artists.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun fetchArtistMotionById(
        artistId: String,
        storefront: String,
        token: String,
    ): String? {
        val url = "$AMP_BASE/v1/catalog/$storefront/artists/$artistId"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("extend", "editorialVideo,editorialArtwork")
            ?.addQueryParameter("views", "full-editorial-video")
            ?.build()
            ?: return null

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JSONObject(resp.body.string())
                val data = root.optJSONArray("data") ?: return@use null
                val artistObj = data.optJSONObject(0) ?: return@use null
                extractArtistMotionUrl(artistObj)
            }
        }.getOrNull()
    }

    internal fun extractArtistMotionUrl(artistObj: JSONObject): String? {
        val attrs = artistObj.optJSONObject("attributes") ?: return null

        val editorialVideo = attrs.optJSONObject("editorialVideo")
            ?: attrs.optJSONObject("editorialArtwork")
            ?: artistObj.optJSONObject("views")
                ?.optJSONObject("full-editorial-video")
                ?.optJSONArray("data")
                ?.optJSONObject(0)
                ?.optJSONObject("attributes")
            ?: return null

        val preferredKeys = listOf(
            "motionArtistFullscreen16x9",
            "motionArtistSquare1x1",
            "motionDetailTall",
            "motionDetailSquare",
            "motionSquareVideo1x1",
            "motionDetailRaw",
        )

        for (k in preferredKeys) {
            extractVideoUrl(editorialVideo.opt(k))?.let { return it }
        }

        val it = editorialVideo.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k in preferredKeys) continue
            extractVideoUrl(editorialVideo.opt(k))?.let { return it }
        }

        return null
    }

    private val VIDEO_URL_REGEX = Regex("""\.(m3u8|mp4)(\?|$)""", RegexOption.IGNORE_CASE)

    private fun extractVideoUrl(node: Any?): String? {
        if (node == null) return null
        if (node is String && VIDEO_URL_REGEX.containsMatchIn(node)) return node
        if (node is JSONObject) {
            val directCandidates = listOf("video", "hlsUrl", "url", "streamUrl", "assetUrl", "previewUrl")
            for (c in directCandidates) {
                val v = node.optString(c).trim()
                if (v.isNotBlank() && VIDEO_URL_REGEX.containsMatchIn(v)) return v
            }
            val innerIt = node.keys()
            while (innerIt.hasNext()) {
                val k = innerIt.next()
                if (k in directCandidates) continue
                extractVideoUrl(node.opt(k))?.let { return it }
            }
        }
        return null
    }

    private fun cleanArtistName(s: String): String {
        return s.split(Regex("[,&/]|\\bfeat\\.?|\\bft\\.?|\\bwith\\b", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim() ?: s.trim()
    }

    private fun normalize(s: String): String {
        return cleanArtistName(s).lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun cacheKey(type: String, idOrName: String, storefront: String): String =
        "$type:$storefront:${idOrName.trim().lowercase(Locale.ROOT)}"
}

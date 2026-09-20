package com.alananasss.kittytune.data.cover

import com.alananasss.kittytune.audio.providers.ProviderIsrc
import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Fetches Apple Music animated canvas (motion cover) URLs via Apple Music AMP API.
 * Token is obtained from the JWT token endpoint.
 */
object AppleMusicCanvasProvider {

    private const val TAG = "AppleMusicCanvasProvider"
    private const val TOKEN_URL = "https://yesitworkssomehow-funny-deeza-api-and-yeah.hf.space/apple/token"
    private const val AMP_BASE = "https://amp-api.music.apple.com"
    private const val STOREFRONT = "us"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /**
     * Verified official Apple Music Web Player developer token.
     * Valid through late 2026 as guaranteed baseline fallback.
     */
    const val FALLBACK_TOKEN =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzg2MzYyMTUwLCJleHAiOjE3OTI0MTAxNTAsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.wmgvODbrLN8VxNt45wP6fxrI-U2PJhDD1Y1ZokU1ZqAKg_2F8rB30P_MwzPlQ0SyEGPXNg8Pfh7HUsO1cBv3cQ"

    private const val TOKEN_TTL_MS = 60 * 60 * 1000L
    private const val PREFS_NAME = "apple_music_token_prefs"
    private const val PREF_KEY_TOKEN = "cached_jwt_token"
    private const val PREF_KEY_TOKEN_TIME = "cached_token_timestamp"
    private val VIDEO_URL_REGEX = Regex("""\.(m3u8|mp4)(\?|$)""", RegexOption.IGNORE_CASE)

    enum class CanvasAspectPreference { TALL, SQUARE }

    data class AppleMusicCanvas(val animated: String?)

    private val cache = ConcurrentHashMap<String, AppleMusicCanvas>()
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private const val NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1000L

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenFetchedAt: Long = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val tokenHttp = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val tokenMutex = Mutex()
    private val prefs by lazy { NamedPrefs(PREFS_NAME) }

    private fun getStoredToken(): Pair<String?, Long> {
        return try {
            val token = prefs.getString(PREF_KEY_TOKEN, null)
            val timestamp = prefs.getLong(PREF_KEY_TOKEN_TIME, 0L)
            token to timestamp
        } catch (_: Exception) {
            null to 0L
        }
    }

    private fun persistToken(token: String, timestamp: Long) {
        try {
            prefs.putString(PREF_KEY_TOKEN, token)
            prefs.putLong(PREF_KEY_TOKEN_TIME, timestamp)
        } catch (_: Exception) {}
    }

    fun getCached(
        song: String,
        artist: String,
        isrc: String?,
        preferredAspect: CanvasAspectPreference,
    ): AppleMusicCanvas? {
        val key = cacheKey(isrc, song, artist, preferredAspect)
        cache[key]?.let { return it }
        val neg = negativeCache[key]
        if (neg != null && System.currentTimeMillis() - neg < NEGATIVE_CACHE_TTL_MS) return null
        return null
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        isrc: String? = null,
        durationSeconds: Int? = null,
        preferredAspect: CanvasAspectPreference = CanvasAspectPreference.SQUARE,
    ): AppleMusicCanvas? = withContext(Dispatchers.IO) {
        val key = cacheKey(isrc, song, artist, preferredAspect)
        cache[key]?.let { return@withContext it }
        val neg = negativeCache[key]
        if (neg != null && System.currentTimeMillis() - neg < NEGATIVE_CACHE_TTL_MS) {
            return@withContext null
        }

        runCatching {
            var token = getToken()

            val resolvedIsrc = ProviderIsrc.normalize(isrc)
            resolvedIsrc?.let { CanvasIndex.getByIsrc(it) }?.let { indexed ->
                return@withContext AppleMusicCanvas(animated = indexed.sourceUrl).also {
                    cache[key] = it
                }
            }

            fun attempt(): Triple<AppleMusicCanvas?, CanvasMatchTier?, JSONObject?> {
                if (resolvedIsrc != null) {
                    val (canvasResult, songItem) = fetchByIsrc(resolvedIsrc, token, preferredAspect)
                    if (canvasResult != null) return Triple(canvasResult, CanvasMatchTier.ISRC_EXACT, songItem)
                    if (songItem != null) {
                        val albName = songItem.optJSONObject("attributes")?.optString("albumName")
                        if (!albName.isNullOrBlank()) {
                            val (albumCanvas, albumItem) = fetchByAlbum(albName, artist, token, preferredAspect)
                            if (albumCanvas != null) return Triple(albumCanvas, CanvasMatchTier.ISRC_EXACT, albumItem)
                        }
                    }
                }
                val (canvasResult, songItem, tier) = fetchBySearch(song, artist, durationSeconds, token, preferredAspect)
                if (canvasResult != null) return Triple(canvasResult, tier, songItem)

                val candidateAlbum = album?.takeIf { it.isNotBlank() }
                    ?: songItem?.optJSONObject("attributes")?.optString("albumName")?.takeIf { it.isNotBlank() }
                if (!candidateAlbum.isNullOrBlank()) {
                    val (albumCanvas, albumItem) = fetchByAlbum(candidateAlbum, artist, token, preferredAspect)
                    if (albumCanvas != null) return Triple(albumCanvas, tier ?: CanvasMatchTier.ALBUM_ARTIST_TITLE, albumItem ?: songItem)
                }

                return Triple(canvasResult, tier, songItem)
            }

            var (canvas, tier, matchedItem) = attempt()

            if (canvas == null && tier != CanvasMatchTier.ISRC_EXACT) {
                token = getToken(forceRefresh = true)
                val retry = attempt()
                canvas = retry.first
                tier = retry.second
                matchedItem = retry.third
            }

            val matchedAttrs = matchedItem?.optJSONObject("attributes")
            val matchedTitle = matchedAttrs?.optString("name")
            val matchedCatalogId = matchedItem?.optString("id")?.takeIf { it.isNotBlank() }
            val matchedIsrcRaw = matchedAttrs?.optString("isrc")?.takeIf { it.isNotBlank() }
            val effectiveIsrc = resolvedIsrc ?: ProviderIsrc.normalize(matchedIsrcRaw)

            if (canvas != null) {
                cache[key] = canvas
                negativeCache.remove(key)
                if (tier != null) {
                    CanvasIndex.put(
                        CanvasMatchEntry(
                            isrc = effectiveIsrc,
                            appleCatalogId = matchedCatalogId,
                            title = matchedTitle ?: song,
                            artist = artist,
                            album = album,
                            durationMs = durationSeconds?.toLong()?.times(1000L),
                            sourceUrl = canvas.animated.orEmpty(),
                            matchTier = tier,
                            confidence = tier.baseConfidence,
                            lastMatchedAtMs = System.currentTimeMillis(),
                        )
                    )
                }
            } else {
                negativeCache[key] = System.currentTimeMillis()
            }
            canvas
        }.onFailure {
            Logger.e(TAG, "Canvas fetch failed for \"$song\" by $artist: ${it.message}")
        }.getOrNull()
    }

    private fun cacheKey(
        isrc: String?,
        song: String,
        artist: String,
        aspect: CanvasAspectPreference,
    ): String =
        ((isrc?.takeIf { it.isNotBlank() } ?: "$song\u001F$artist") + "\u001F$aspect").lowercase()

    private fun fetchTokenFromAppleWeb(): String? {
        return runCatching {
            val req = Request.Builder()
                .url("https://music.apple.com/us/browse")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val html = tokenHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body.string()
            }
            val jsMatch = Regex("""/assets/index-[^"']+\.js""").find(html) ?: return@runCatching null
            val jsUrl = "https://music.apple.com${jsMatch.value}"

            val jsReq = Request.Builder()
                .url(jsUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val js = tokenHttp.newCall(jsReq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body.string()
            }
            val tokenMatch = Regex("""eyJh[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""").find(js)
            tokenMatch?.value
        }.onFailure { Logger.d(TAG, "Apple web token scraping skipped: ${it.message}") }.getOrNull()
    }

    private fun fetchTokenFromEndpoint(): String? {
        return runCatching {
            val req = Request.Builder()
                .url(TOKEN_URL)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            tokenHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Logger.w(TAG, "Token endpoint returned ${resp.code}")
                    return@runCatching null
                }
                val body = resp.body.string().trim()
                if (body.startsWith("{")) {
                    val json = JSONObject(body)
                    json.optString("token")
                        .ifBlank { json.optString("developerToken") }
                        .ifBlank { json.optString("jwt") }
                        .takeIf { it.isNotBlank() }
                } else {
                    body.takeIf { it.startsWith("ey") }
                }
            }
        }.onFailure { Logger.d(TAG, "Token endpoint fetch failed: ${it.message}") }.getOrNull()
    }

    suspend fun getToken(forceRefresh: Boolean = false): String = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedToken?.takeIf { now - tokenFetchedAt < TOKEN_TTL_MS }?.let { return@withLock it }

            val (storedToken, storedTimestamp) = getStoredToken()
            if (!storedToken.isNullOrBlank() && now - storedTimestamp < TOKEN_TTL_MS) {
                cachedToken = storedToken
                tokenFetchedAt = storedTimestamp
                return@withLock storedToken
            }
        }

        val scrapedToken = fetchTokenFromAppleWeb()
        if (!scrapedToken.isNullOrBlank()) {
            cachedToken = scrapedToken
            tokenFetchedAt = now
            persistToken(scrapedToken, now)
            Logger.d(TAG, "Successfully extracted Apple Music token from web player")
            return@withLock scrapedToken
        }

        val remoteToken = fetchTokenFromEndpoint()
        if (!remoteToken.isNullOrBlank()) {
            cachedToken = remoteToken
            tokenFetchedAt = now
            persistToken(remoteToken, now)
            Logger.d(TAG, "Successfully fetched Apple Music token from endpoint")
            return@withLock remoteToken
        }

        cachedToken = FALLBACK_TOKEN
        tokenFetchedAt = now
        persistToken(FALLBACK_TOKEN, now)
        Logger.d(TAG, "Using fallback Apple Music token")
        return@withLock FALLBACK_TOKEN
    }

    private fun fetchByIsrc(
        isrc: String,
        token: String,
        preferredAspect: CanvasAspectPreference,
    ): Pair<AppleMusicCanvas?, JSONObject?> {
        val url = "$AMP_BASE/v1/catalog/$STOREFRONT/songs"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("filter[isrc]", isrc)
            ?.addQueryParameter("extend", "editorialVideo")
            ?.build()
            ?: return null to null

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null to null
                val root = JSONObject(resp.body.string())
                val data = root.optJSONArray("data") ?: return@use null to null
                val song = data.optJSONObject(0) ?: return@use null to null
                val animatedUrl = extractMotionUrl(song, preferredAspect)
                (animatedUrl?.let { AppleMusicCanvas(animated = it) }) to song
            }
        }.getOrDefault(null to null)
    }

    private fun fetchBySearch(
        song: String,
        artist: String,
        durationSeconds: Int?,
        token: String,
        preferredAspect: CanvasAspectPreference,
    ): Triple<AppleMusicCanvas?, JSONObject?, CanvasMatchTier?> {
        val cleanSong = cleanTrackTitle(song)
        val cleanArtist = cleanArtistName(artist)
        val term = listOfNotNull(cleanSong.takeIf { it.isNotBlank() }, cleanArtist.takeIf { it.isNotBlank() }).joinToString(" ")
        if (term.isBlank()) return Triple(null, null, null)

        val url = "$AMP_BASE/v1/catalog/$STOREFRONT/search"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("term", term)
            ?.addQueryParameter("types", "songs")
            ?.addQueryParameter("limit", "10")
            ?.addQueryParameter("extend", "editorialVideo")
            ?.build()
            ?: return Triple(null, null, null)

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use Triple(null, null, null)
                val root = JSONObject(resp.body.string())
                val results = root.optJSONObject("results") ?: return@use Triple(null, null, null)
                val songsObj = results.optJSONObject("songs") ?: return@use Triple(null, null, null)
                val data = songsObj.optJSONArray("data") ?: return@use Triple(null, null, null)
                val (bestItem, tier) = pickBestCandidate(data, song, artist, durationSeconds)
                if (bestItem == null) return@use Triple(null, null, null)
                val animatedUrl = extractMotionUrl(bestItem, preferredAspect)
                Triple(animatedUrl?.let { AppleMusicCanvas(animated = it) }, bestItem, tier)
            }
        }.getOrDefault(Triple(null, null, null))
    }

    private fun fetchByAlbum(
        album: String,
        artist: String,
        token: String,
        preferredAspect: CanvasAspectPreference,
    ): Pair<AppleMusicCanvas?, JSONObject?> {
        val cleanAlb = cleanTrackTitle(album)
        val cleanArt = cleanArtistName(artist)
        val term = listOfNotNull(cleanArt.takeIf { it.isNotBlank() }, cleanAlb.takeIf { it.isNotBlank() }).joinToString(" ")
        if (term.isBlank()) return null to null

        val url = "$AMP_BASE/v1/catalog/$STOREFRONT/search"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("term", term)
            ?.addQueryParameter("types", "albums")
            ?.addQueryParameter("limit", "5")
            ?.addQueryParameter("extend", "editorialVideo")
            ?.build()
            ?: return null to null

        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null to null
                val root = JSONObject(resp.body.string())
                val results = root.optJSONObject("results") ?: return@use null to null
                val albumsObj = results.optJSONObject("albums") ?: return@use null to null
                val data = albumsObj.optJSONArray("data") ?: return@use null to null

                val normTargetAlb = normalize(cleanAlb)
                val normTargetArt = normalize(cleanArt)

                var bestAlb: JSONObject? = null
                var bestScore = -1

                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val attrs = item.optJSONObject("attributes") ?: continue
                    val edVid = attrs.optJSONObject("editorialVideo")
                        ?: attrs.optJSONObject("artwork")?.optJSONObject("editorialVideo")
                    if (edVid == null) continue

                    val name = normalize(attrs.optString("name"))
                    val artName = normalize(attrs.optString("artistName"))

                    var score = 0
                    if (name == normTargetAlb) score += 100
                    else if (name.contains(normTargetAlb) || normTargetAlb.contains(name)) score += 60

                    if (artName == normTargetArt) score += 80
                    else if (artName.contains(normTargetArt) || normTargetArt.contains(artName)) score += 40

                    if (score > bestScore) {
                        bestScore = score
                        bestAlb = item
                    }
                }

                if (bestAlb != null) {
                    val animatedUrl = extractMotionUrl(bestAlb, preferredAspect)
                    (animatedUrl?.let { AppleMusicCanvas(animated = it) }) to bestAlb
                } else {
                    null to null
                }
            }
        }.getOrDefault(null to null)
    }

    private fun pickBestCandidate(
        items: JSONArray,
        targetSong: String,
        targetArtist: String,
        targetDurationSec: Int?,
    ): Pair<JSONObject?, CanvasMatchTier?> {
        val normTargetSong = normalize(targetSong)
        val normTargetArtist = normalize(targetArtist)

        data class Scored(val item: JSONObject, val score: Int, val tier: CanvasMatchTier)
        var best: Scored? = null

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val attrs = item.optJSONObject("attributes") ?: continue
            val name = normalize(attrs.optString("name"))
            val artistName = normalize(attrs.optString("artistName"))
            val durMs = attrs.optLong("durationInMillis", 0L)
            val durSec = (durMs / 1000L).toInt()

            var score = 0
            var tier: CanvasMatchTier = CanvasMatchTier.FUZZY

            val titleExact = name == normTargetSong
            val titleContains = name.contains(normTargetSong) || normTargetSong.contains(name)

            if (titleExact) {
                score += 100
            } else if (titleContains) {
                score += 60
            } else {
                continue
            }

            val artistExact = normTargetArtist.isNotBlank() && artistName == normTargetArtist
            val artistContains = normTargetArtist.isNotBlank() && (artistName.contains(normTargetArtist) || normTargetArtist.contains(artistName))

            if (artistExact) {
                score += 80
            } else if (artistContains) {
                score += 40
            }

            if (targetDurationSec != null && targetDurationSec > 0 && durSec > 0) {
                val diff = kotlin.math.abs(targetDurationSec - durSec)
                if (diff <= 2) {
                    score += 50
                } else if (diff <= 5) {
                    score += 30
                } else if (diff > 12) {
                    score -= 40
                }
            }

            if (titleExact && (artistExact || artistContains)) {
                tier = CanvasMatchTier.ALBUM_ARTIST_TITLE
            } else if (score >= 120) {
                tier = CanvasMatchTier.ALBUM_ARTIST_TITLE
            }

            if (hasMotionArtwork(item)) {
                score += 25
            }

            if (best == null || score > best.score) {
                best = Scored(item, score, tier)
            }
        }

        return best?.let { it.item to it.tier } ?: (null to null)
    }

    private fun hasMotionArtwork(item: JSONObject): Boolean {
        val attrs = item.optJSONObject("attributes") ?: return false
        if (attrs.optJSONObject("editorialVideo") != null) return true
        val artwork = attrs.optJSONObject("artwork") ?: return false
        return artwork.optJSONObject("editorialVideo") != null
    }

    internal fun extractMotionUrl(
        item: JSONObject,
        preferredAspect: CanvasAspectPreference = CanvasAspectPreference.SQUARE,
    ): String? {
        val attrs = item.optJSONObject("attributes") ?: return null

        val editorialVideo = attrs.optJSONObject("editorialVideo")
            ?: attrs.optJSONObject("artwork")?.optJSONObject("editorialVideo")
            ?: return null

        return extractFromEditorialVideoObject(editorialVideo, preferredAspect)
    }

    private fun extractFromEditorialVideoObject(
        obj: JSONObject,
        preferredAspect: CanvasAspectPreference,
    ): String? {
        val tallKeys = listOf(
            "motionDetailTall",
            "motionSquareVideo1x1",
            "motionDetailSquare",
            "motionArtistSquare1x1",
            "motionArtistFullscreen16x9",
        )
        val squareKeys = listOf(
            "motionSquareVideo1x1",
            "motionDetailSquare",
            "motionArtistSquare1x1",
            "motionDetailTall",
            "motionArtistFullscreen16x9",
        )

        val preferredKeys = if (preferredAspect == CanvasAspectPreference.TALL) tallKeys else squareKeys

        for (k in preferredKeys) {
            extractVideoUrlFromNode(obj.opt(k))?.let { return it }
        }

        val it = obj.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (k in preferredKeys) continue
            extractVideoUrlFromNode(obj.opt(k))?.let { return it }
        }

        return null
    }

    private fun extractVideoUrlFromNode(node: Any?): String? {
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
                extractVideoUrlFromNode(node.opt(k))?.let { return it }
            }
        }
        return null
    }

    private val TITLE_STRIP_REGEX = Regex(
        """\s*(\(|\[)[^)\]]*(remaster|edition|version|feat\.?|ft\.?|with|prod\.?|explicit|clean|live|deluxe|expanded|single|ep|instrumental)[^)\]]*(\)|\]).*""",
        RegexOption.IGNORE_CASE
    )

    private fun cleanTrackTitle(s: String): String {
        var r = s.trim()
        r = r.replace(TITLE_STRIP_REGEX, "")
        r = r.replace(Regex("""\s*-\s*(remaster(ed)?|live|single|ep|deluxe).*$""", RegexOption.IGNORE_CASE), "")
        return r.trim()
    }

    private fun cleanArtistName(s: String): String {
        return s.split(Regex("[,&/]|\\bfeat\\.?|\\bft\\.?|\\bwith\\b", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim() ?: s.trim()
    }

    private fun normalize(s: String): String {
        return cleanTrackTitle(s).lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

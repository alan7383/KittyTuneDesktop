package com.alananasss.kittytune.data.applemusic

import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.core.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Reads the Apple Music catalogue, and only ever reads it (issue #33).
 *
 * ## What this can and cannot do, stated once
 *
 * Metadata: search, and the fields a result carries — title, artist, album, artwork, duration, release
 * date. **Never audio.** Apple's streams are DRM-protected through MusicKit and that is not something to
 * work around; the point of having the catalogue at all is that "many artists don't upload their music to
 * soundcloud, youtube, spotify", so a match here is a name to go and find on a source that can play it.
 * The resolver that does that is [AppleMusicFallback].
 *
 * ## The credential
 *
 * Scraped from the public web player, the same way [com.alananasss.kittytune.data.ClientIdScraper] gets
 * SoundCloud's anonymous client id — see [AppleMusicTokens] for what was verified and why the Android
 * app was the wrong place to look. Two consequences shape this file:
 *
 *  - The bundle carries several tokens and does not say which is the catalogue's, so [ensureToken] probes
 *    them and remembers the one that answered.
 *  - It can stop working at any time, because it depends on the shape of somebody else's web page. So
 *    every method here answers null rather than throwing, a 401 clears the cached token and retries once,
 *    and nothing in the app may treat an Apple Music failure as anything but "no results from Apple".
 */
object AppleMusicClient {

    private val prefs by lazy { NamedPrefs("apple_music") }
    private const val TOKEN_KEY = "web_player_token"

    /** One scrape at a time: a cold start fires several searches at once and they would all scrape. */
    private val tokenLock = Mutex()

    private val baseClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val client: OkHttpClient
        get() = com.alananasss.kittytune.data.network.ProxyManager
            .configureOkHttpClient(baseClient.newBuilder()).build()

    private val json = Json { ignoreUnknownKeys = true }

    /** A desktop UA, or the web player serves a page without the asset bundle. */
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Safari/537.36"

    /**
     * A token known to work, from the cache if there is one and from the web player if not.
     *
     * The stored one is trusted until the API says otherwise rather than being probed on every call: a
     * live token's expiry was two months out, and a probe per search would double every request.
     */
    private suspend fun ensureToken(forceRefresh: Boolean = false): String? = tokenLock.withLock {
        if (!forceRefresh) {
            prefs.getString(TOKEN_KEY, null)?.takeIf { it.isNotBlank() }?.let { return@withLock it }
        }
        val scraped = scrapeWorkingToken()
        if (scraped != null) prefs.putString(TOKEN_KEY, scraped)
        scraped
    }

    private suspend fun scrapeWorkingToken(): String? = withContext(Dispatchers.IO) {
        val html = get(AppleMusicTokens.WEB_PLAYER) ?: return@withContext null
        val bundleUrl = AppleMusicTokens.bundleUrlIn(html) ?: return@withContext null
        val bundle = get(bundleUrl) ?: return@withContext null
        // Asking the API which one it is, because the bundle does not say. The first candidate answered
        // 401 and the second answered 200 when this was written.
        AppleMusicTokens.candidatesIn(bundle).firstOrNull { probe(it) }
    }

    private fun probe(token: String): Boolean = runCatching {
        callCatalog(token, "search", mapOf("term" to "a", "types" to "songs", "limit" to "1")) != null
    }.getOrDefault(false)

    /**
     * Catalogue search.
     *
     * @return the songs Apple knows about, or an empty list for every failure there is. A source that
     *   depends on scraping must never be able to empty a search that other sources answered.
     */
    suspend fun searchSongs(term: String, limit: Int = 25): List<AppleSong> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()
        val body = withToken { token ->
            callCatalog(
                token,
                "search",
                mapOf("term" to term, "types" to "songs", "limit" to limit.coerceIn(1, 25).toString()),
            )
        } ?: return@withContext emptyList()

        runCatching {
            json.parseToJsonElement(body).jsonObject["results"]?.jsonObject
                ?.get("songs")?.jsonObject?.get("data")?.jsonArray
                ?.mapNotNull { AppleSong.from(it.jsonObject) }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * Runs [block] with a token, once more with a fresh one if the first answer was unauthorised.
     *
     * The retry is the whole reason the token is cached optimistically: a stale token costs one wasted
     * request and a re-scrape, which is a better trade than probing before every search.
     */
    private suspend fun <T> withToken(block: (String) -> T?): T? {
        val first = ensureToken() ?: return null
        block(first)?.let { return it }
        val fresh = ensureToken(forceRefresh = true) ?: return null
        if (fresh == first) return null
        return block(fresh)
    }

    private fun callCatalog(token: String, path: String, query: Map<String, String>): String? {
        val storefront = AppleMusicTokens.storefrontFor(Strings.resolvedLanguage)
        val url = buildString {
            append(AppleMusicTokens.API).append("/v1/catalog/").append(storefront).append('/').append(path)
            append('?')
            append(query.entries.joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" })
        }
        return runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    // Rejected without this, whatever the bearer says.
                    .header("Origin", AppleMusicTokens.WEB_PLAYER)
                    .header("User-Agent", DESKTOP_UA)
                    .build()
            ).execute().use { if (it.code == 200) it.body?.string() else null }
        }.getOrNull()
    }

    private fun get(url: String): String? = runCatching {
        client.newCall(
            Request.Builder().url(url).header("User-Agent", DESKTOP_UA).build()
        ).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()
}

package com.alananasss.kittytune.data

import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Desktop replacement for the Android ghost-WebView client_id capture:
 * the WebView intercepted SoundCloud's own requests and read `client_id=` off them.
 * Here we fetch the soundcloud.com homepage, locate the JS asset bundles and pull
 * the anonymous `client_id:"..."` constant out of them, then persist via Config.
 */
object ClientIdScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val scriptUrlRegex = Regex("""src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""")
    private val clientIdRegex = Regex("""client_id\s*[:=]\s*"([A-Za-z0-9]{32})"""")

    /**
     * Desktop-browser UA: Config.USER_AGENT is a mobile Android UA, which makes
     * soundcloud.com serve the mobile page without the a-v2 asset bundles.
     */
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /** Returns a working anonymous client_id, scraping if the stored one is missing/stale. */
    suspend fun ensureClientId(): String? = withContext(Dispatchers.IO) {
        // Quick validation of the current id first.
        if (isValid(Config.CLIENT_ID)) {
            SessionManager.onClientIdCaptured(Config.CLIENT_ID)
            return@withContext Config.CLIENT_ID
        }

        val scraped = scrape()
        println("KittyTune: scraped SoundCloud client_id = $scraped")
        if (scraped == null) return@withContext null
        Config.updateClientId(scraped)
        SessionManager.onClientIdCaptured(scraped)
        scraped
    }

    private fun isValid(clientId: String): Boolean {
        if (clientId.isBlank() || clientId.length < 20) return false
        return try {
            val req = Request.Builder()
                .url("https://api-v2.soundcloud.com/featured_tracks/top/all-music?client_id=$clientId&limit=1")
                .header("User-Agent", Config.USER_AGENT)
                .build()
            client.newCall(req).execute().use { it.code == 200 }
        } catch (_: Exception) {
            false
        }
    }

    private fun scrape(): String? {
        return try {
            val homepage = client.newCall(
                Request.Builder()
                    .url("https://soundcloud.com/")
                    .header("User-Agent", DESKTOP_UA)
                    .build()
            ).execute().use { it.body?.string() } ?: return null

            // Search the JS bundles last-to-first — the id usually sits in the last chunk.
            val scripts = scriptUrlRegex.findAll(homepage).map { it.groupValues[1] }.toList()
            for (scriptUrl in scripts.asReversed()) {
                val js = try {
                    client.newCall(
                        Request.Builder()
                            .url(scriptUrl)
                            .header("User-Agent", DESKTOP_UA)
                            .build()
                    ).execute().use { it.body?.string() }
                } catch (_: Exception) {
                    null
                } ?: continue

                clientIdRegex.find(js)?.groupValues?.getOrNull(1)?.let { return it }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

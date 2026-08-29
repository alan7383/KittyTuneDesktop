package com.alananasss.kittytune.data.sections

import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.TokenManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fetches the two browse screens SoundCloud's mobile client uses (issue #33).
 *
 * ## The endpoint, and why it needs the listener's token
 *
 * Taken from their decompiled client — see [SectionsParser] for the request shape. It lives on
 * `api-mobile.soundcloud.com`, which answers **401 to an anonymous client id**: unlike the api-v2 endpoints this
 * app mostly uses, the sections API is only served to a signed-in account. That is not a limitation worth working
 * around, it is simply what the feature costs, and the app already holds a token for everything else.
 *
 * So this can fail for a reason that is nobody's fault — not signed in — and the caller is expected to fall back
 * to the browse screen the app already had rather than showing an error. A category grid that is occasionally the
 * local one is better than a category grid that is sometimes an apology.
 *
 * ## Why nothing here is cached
 *
 * Their own client caches the home layout to a file and re-parses it on launch. Not copied, deliberately: these
 * two screens are opened rarely, they are a few hundred milliseconds, and a stale category grid is the one thing
 * this feature exists to stop being — "mets tout exactement comme Android" means what Android has *now*.
 */
object SectionsRepository {

    /** Their layout for the browse screen: the grid of moods and genres. */
    private const val LAYOUT_BROWSE = "soundcloud:layouts:search_default"

    /** And for one category's own page. */
    private const val LAYOUT_CATEGORY = "soundcloud:layouts:category_page"

    /**
     * Their newest layout vocabulary. Their client picks between v8, v9 and v10 on feature flags and always
     * prefers the newest, so this asks for the newest — an older version answers with fewer section kinds, which
     * would mean deliberately receiving less than Android does.
     */
    private const val VERSION = "v10"

    private const val HOST = "https://api-mobile.soundcloud.com"

    private val baseClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val client: OkHttpClient
        get() = com.alananasss.kittytune.data.network.ProxyManager
            .configureOkHttpClient(baseClient.newBuilder()).build()

    /** The grid of categories, exactly the set the Android app shows. */
    suspend fun browse(): SectionsParser.Screen = query("", LAYOUT_BROWSE)

    /**
     * One category's page.
     *
     * @param query what the grid's tile carried. Passed through untouched — it is their own identifier for the
     *   category and inventing a slug from the title is how this would stop matching Android.
     */
    suspend fun category(query: String): SectionsParser.Screen = query(query, LAYOUT_CATEGORY)

    /** A `next` link from a previous screen, for a shelf's "see all". */
    suspend fun more(href: String): SectionsParser.Screen = withContext(Dispatchers.IO) {
        SectionsParser.parse(get(if (href.startsWith("http")) href else HOST + href))
    }

    private suspend fun query(q: String, layout: String): SectionsParser.Screen =
        withContext(Dispatchers.IO) {
            val url = HOST + "/search/query" +
                "?q=" + java.net.URLEncoder.encode(q, "UTF-8") +
                "&layout=" + java.net.URLEncoder.encode(layout, "UTF-8") +
                "&version=" + VERSION +
                "&limit=30" +
                "&device_time=" + java.net.URLEncoder.encode(deviceTime(), "UTF-8")
            SectionsParser.parse(get(url))
        }

    /**
     * Their client sends the handset's clock with every sections request; the API is content-negotiated on it for
     * time-of-day shelves ("late night", and the rest). Sent in the same format for the same reason.
     */
    private fun deviceTime(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())

    /**
     * @return the parsed body, or null for every failure there is — no token, expired token, a layout version
     *   they have retired, no network. A browse screen must be able to fall back rather than fail.
     */
    private fun get(url: String): JsonObject? = runCatching {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json; charset=utf-8")
            // Their own client identifier. The API varies its layout by client, and asking as something it does
            // not recognise gets the oldest one.
            .header("User-Agent", "SoundCloud-Android/2024.02.15-release")
            .header("Accept-Language", Strings.getAcceptLanguage())

        val token = TokenManager.getAccessToken()
        if (token.isNullOrBlank()) return null
        builder.header("Authorization", "OAuth $token")

        client.newCall(builder.build()).execute().use { response ->
            if (response.code != 200) return null
            val body = response.body?.string() ?: return null
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
        }
    }.getOrNull()
}

package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.core.AppDirs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent OkHttp CookieJar, the desktop replacement for the Android WebView
 * CookieManager the app harvested SoundCloud session/DataDome cookies from.
 *
 * Shared app-wide: the login flow (embedded browser) populates it, and the API
 * client sends cookies back — same behavior as the Android cookie jar.
 */
object CookieStore : CookieJar {

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    )

    private val file = File(AppDirs.dataDir, "cookies.json")
    private val json = Json { ignoreUnknownKeys = true }

    // key: "domain|name" -> cookie
    private val store = ConcurrentHashMap<String, Cookie>()

    init {
        load()
    }

    private fun keyOf(c: Cookie): String = "${c.domain}|${c.name}"

    @Synchronized
    private fun load() {
        try {
            if (!file.exists()) return
            val list = json.decodeFromString<List<StoredCookie>>(file.readText())
            val now = System.currentTimeMillis()
            list.filter { it.expiresAt > now }.forEach { s ->
                val c = Cookie.Builder()
                    .name(s.name)
                    .value(s.value)
                    .path(s.path)
                    .expiresAt(s.expiresAt)
                    .apply {
                        if (s.hostOnly) hostOnlyDomain(s.domain) else domain(s.domain)
                        if (s.secure) secure()
                        if (s.httpOnly) httpOnly()
                    }
                    .build()
                store[keyOf(c)] = c
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun persist() {
        try {
            val list = store.values.map {
                StoredCookie(
                    it.name, it.value, it.domain, it.path,
                    it.expiresAt, it.secure, it.httpOnly, it.hostOnly,
                )
            }
            file.writeText(json.encodeToString(list))
        } catch (_: Exception) {
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        var changed = false
        cookies.forEach { c ->
            store[keyOf(c)] = c
            changed = true
        }
        if (changed) persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val matches = store.values.filter { it.expiresAt > now && it.matches(url) }
        // prune expired lazily
        val expired = store.values.filter { it.expiresAt <= now }
        if (expired.isNotEmpty()) {
            expired.forEach { store.remove(keyOf(it)) }
            persist()
        }
        return matches
    }

    /** Manually set a cookie (used by the login flow + DataDome handling). */
    fun set(url: String, cookie: Cookie) {
        store[keyOf(cookie)] = cookie
        persist()
    }

    /** Raw cookie string for a URL, like WebView CookieManager.getCookie(url). */
    fun cookieHeader(url: String): String? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        return loadForRequest(httpUrl).joinToString("; ") { "${it.name}=${it.value}" }
            .takeIf { it.isNotBlank() }
    }

    /** Extract a single cookie value by name across all stored cookies for a host. */
    fun value(host: String, name: String): String? =
        store.values.firstOrNull { it.name == name && host.endsWith(it.domain) }?.value

    fun clear() {
        store.clear()
        persist()
    }
}

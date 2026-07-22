package com.alananasss.kittytune.core

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Desktop replacement for Android string resources.
 * Loads the exact same strings.xml files as the Android app (copied to resources/i18n/)
 * and resolves them with Android-style positional formatting (%1$s, %d, ...).
 *
 * Locales supported (same as the Android in-app picker): system / en / fr / hu.
 */
object Strings {

    /** "system", "en", "fr" or "hu" — mirrors the Android `app_language` pref. */
    private var _appLanguage = androidx.compose.runtime.mutableStateOf("system")
    var appLanguage: String
        get() = _appLanguage.value
        set(value) {
            _appLanguage.value = value
            cache.clear()
        }

    private val tables = ConcurrentHashMap<String, Map<String, String>>()
    private val cache = ConcurrentHashMap<String, String>()

    /** The concrete language in use after resolving "system": "en", "fr" or "hu". */
    val resolvedLanguage: String
        get() = effectiveLang()

    private fun effectiveLang(): String = when (appLanguage) {
        "fr", "en", "hu" -> appLanguage
        else -> when (Locale.getDefault().language) {
            "fr" -> "fr"
            "hu" -> "hu"
            else -> "en"
        }
    }

    private fun table(lang: String): Map<String, String> = tables.getOrPut(lang) {
        val stream = Strings::class.java.getResourceAsStream("/i18n/strings-$lang.xml")
            ?: return@getOrPut emptyMap()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
        val nodes = doc.getElementsByTagName("string")
        buildMap {
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                val name = el.getAttribute("name")
                put(name, unescape(el.textContent))
            }
        }
    }

    private fun unescape(raw: String): String = raw
        .removeSurrounding("\"")
        .replace("\\'", "'")
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\@", "@")
        .replace("\\?", "?")

    /** Equivalent of stringResource(R.string.key). Falls back to English, then to the key itself. */
    fun get(key: String): String {
        val lang = effectiveLang()
        return cache.getOrPut("$lang:$key") {
            table(lang)[key] ?: table("en")[key] ?: key
        }
    }

    /** Equivalent of stringResource(R.string.key, args...) with Android positional format support. */
    fun get(key: String, vararg args: Any?): String {
        val pattern = get(key)
        return try {
            // Android uses java.util.Formatter syntax — same on JVM.
            String.format(pattern, *args)
        } catch (_: Exception) {
            pattern
        }
    }
}

/** Terse helper mirroring `stringResource(...)` call-sites: `str("app_name")`. */
fun str(key: String): String = Strings.get(key)
fun str(key: String, vararg args: Any?): String = Strings.get(key, *args)

package com.alananasss.kittytune

import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class LocalizationParityTest {

    private val languages = listOf("en", "fr", "de", "hu", "ru", "vi")

    private fun loadKeys(lang: String): Map<String, String> {
        val file = File("src/main/resources/i18n/strings-$lang.xml")
        assertTrue("Resource file strings-$lang.xml should exist", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name")
            val text = el.textContent
            result[name] = text
        }
        return result
    }

    @Test
    fun testAllLanguagesHaveFullParityWithEnglish() {
        val enStrings = loadKeys("en")
        assertTrue("English strings should not be empty", enStrings.isNotEmpty())

        for (lang in languages) {
            if (lang == "en") continue
            val langStrings = loadKeys(lang)
            val missing = enStrings.keys - langStrings.keys
            assertTrue(
                "Language '$lang' is missing ${missing.size} keys from strings-en.xml: ${missing.take(10)}",
                missing.isEmpty()
            )
        }
    }

    @Test
    fun testVietnameseScreenshotKeysAreTranslated() {
        val viStrings = loadKeys("vi")
        val enStrings = loadKeys("en")

        val keysToCheck = listOf(
            "pref_info_half",
            "pref_info_half_remember",
            "pref_auto_update_subtitle",
            "pref_custom_font",
            "pref_custom_font_subtitle",
            "pref_sidebar_hover_expand",
            "pref_sidebar_hover_expand_sub"
        )

        for (key in keysToCheck) {
            assertTrue("strings-vi.xml should contain $key", viStrings.containsKey(key))
            assertFalse("strings-vi.xml value for $key should not be blank", viStrings[key].isNullOrBlank())
            // Ensure it's not simply the English string copy-pasted
            assertNotEquals("strings-vi.xml for $key should not equal English fallback", enStrings[key], viStrings[key])
        }
    }

    @Test
    fun testStringsResolutionInAllLanguages() {
        val previousLang = Strings.appLanguage
        try {
            for (lang in languages) {
                Strings.appLanguage = lang
                assertEquals(lang, Strings.resolvedLanguage)

                // Test volume and like
                val vol = str("volume_title")
                assertFalse("volume_title in $lang should not be empty", vol.isBlank())
                assertNotEquals("volume_title", vol)

                val like = str("player_like")
                assertFalse("player_like in $lang should not be empty", like.isBlank())
                assertNotEquals("player_like", like)
            }
        } finally {
            Strings.appLanguage = previousLang
        }
    }
}

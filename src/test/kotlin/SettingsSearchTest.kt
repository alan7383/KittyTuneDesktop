package com.alananasss.kittytune

import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates point 14 fixes:
 * - Playlist filter renamed to playlist search in all 6 localization files.
 * - Settings search strings exist across all 6 languages.
 * - SettingsScreen has search query state, text field, and results wired up.
 * - SearchDismiss clears focus immediately on Press without double-firing on Release.
 */
class SettingsSearchTest {

    private fun loadKey(lang: String, key: String): String? {
        val file = File("src/main/resources/i18n/strings-$lang.xml")
        assertTrue(file.exists(), "Resource file strings-$lang.xml should exist")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            if (el.getAttribute("name") == key) {
                return el.textContent
            }
        }
        return null
    }

    @Test
    fun testPlaylistFilterRenamedToSearchInAllLanguages() {
        assertEquals("Search playlist", loadKey("en", "search_playlist_hint"))
        assertEquals("Rechercher dans la playlist", loadKey("fr", "search_playlist_hint"))
        assertEquals("Поиск по плейлисту", loadKey("ru", "search_playlist_hint"))
        assertEquals("Playlist durchsuchen", loadKey("de", "search_playlist_hint"))
        assertEquals("Tìm kiếm trong danh sách phát", loadKey("vi", "search_playlist_hint"))
        assertEquals("Keresés a lejátszási listában", loadKey("hu", "search_playlist_hint"))
    }

    @Test
    fun testSettingsSearchStringsExistInAllLanguages() {
        val languages = listOf("en", "fr", "ru", "de", "vi", "hu")
        val requiredKeys = listOf(
            "search_settings_hint",
            "settings_search_results",
            "settings_search_no_results",
        )
        for (lang in languages) {
            for (key in requiredKeys) {
                val value = loadKey(lang, key)
                assertTrue(!value.isNullOrBlank(), "Key '$key' must exist in strings-$lang.xml")
            }
        }
    }

    @Test
    fun testSettingsScreenHasSearchWired() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/profile/SettingsScreen.kt")
        assertTrue(file.exists())
        val content = file.readText()

        assertTrue(content.contains("var searchQuery"), "SettingsScreen must hold searchQuery state")
        assertTrue(content.contains("SettingsSearchResults"), "SettingsScreen must render SettingsSearchResults")
        assertTrue(content.contains("search_settings_hint"), "SettingsScreen must use search_settings_hint placeholder")
        assertTrue(content.contains("escapeDismisses"), "Settings search field must handle escape dismissal")
    }

    @Test
    fun testSettingsSearchDirectOptionIndexing() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/profile/SettingsScreen.kt")
        assertTrue(file.exists())
        val content = file.readText()

        assertTrue(
            content.contains("pref_animated_artist_profiles"),
            "Animated artist profiles option must be directly searchable"
        )
        assertTrue(
            content.contains("hasSwitch = true"),
            "Search results must support direct switch toggling"
        )
        assertTrue(
            content.contains("animatedArtistProfiles"),
            "Animated artist profiles switch state must be wired"
        )
        assertTrue(
            content.contains("setAnimatedArtistProfilesEnabled"),
            "Animated artist profiles switch toggling must persist preference"
        )
    }

    @Test
    fun testSettingsSearchBarDimensionsAndClipping() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/profile/SettingsScreen.kt")
        assertTrue(file.exists())
        val content = file.readText()

        val searchFieldSection = content.substringAfter("OutlinedTextField(").substringBefore("com.alananasss.kittytune.ui.common.ScrollableColumn")
        assertTrue(
            !searchFieldSection.contains(".height(48.dp)"),
            "Search field must not constrain height to 48dp to prevent vertical text clipping"
        )
        assertTrue(
            searchFieldSection.contains("320.dp"),
            "Search field must have adequate width (320dp) to fit placeholder text without compression"
        )
        assertTrue(
            searchFieldSection.contains("surfaceContainerHigh"),
            "Search field should use surfaceContainerHigh for consistent pill styling"
        )
    }

    @Test
    fun testSearchDismissClearsFocusOnPress() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/common/SearchDismiss.kt")
        assertTrue(file.exists())
        val content = file.readText()

        assertTrue(
            content.contains("event.type == PointerEventType.Press"),
            "clearFocusOnEmptyClick must intercept Press events"
        )
        assertTrue(
            content.contains("focusManager.clearFocus()"),
            "clearFocusOnEmptyClick must clear focus on empty click"
        )
        // Verify it doesn't wait for Release causing double animation
        val clearFocusFunction = content.substringAfter("fun Modifier.clearFocusOnEmptyClick")
        assertTrue(
            !clearFocusFunction.contains("PointerEventType.Release"),
            "clearFocusOnEmptyClick should not delay clearing focus until Release"
        )
    }
}

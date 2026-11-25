package com.alananasss.kittytune

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.ui.common.LocalPlayerBarOverlay
import com.alananasss.kittytune.ui.common.PlayerBarOverlay
import com.alananasss.kittytune.ui.common.ScrollableColumn
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn
import com.alananasss.kittytune.ui.common.SettingsHighlight
import com.alananasss.kittytune.ui.common.SettingsHighlightScope
import com.alananasss.kittytune.ui.common.settingHighlight
import com.alananasss.kittytune.ui.profile.SettingsCategory
import com.alananasss.kittytune.ui.profile.SettingsPlace
import com.alananasss.kittytune.ui.profile.SettingsSubPage
import com.alananasss.kittytune.ui.profile.buildSettingsSearchCatalog
import com.alananasss.kittytune.ui.profile.buildSettingsSearchIndex
import com.alananasss.kittytune.ui.profile.fontSearchKey
import com.alananasss.kittytune.ui.profile.searchSettings
import com.alananasss.kittytune.ui.theme.AppFont
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
        withLanguage("en") {
            val index = buildSettingsSearchIndex("Windows", emptyList())
            val entry = searchSettings(index, "animated artist profiles").first().entry
            assertEquals("pref_animated_artist_profiles", entry.highlightKey)
            assertEquals(SettingsSubPage.THEMES.place, entry.place)
        }
    }

    @Test
    fun testFontOpensTheControlAndSeparatesLyricsTypography() {
        withLanguage("en") {
            val installed = AppFont.System("Example Installed Font")
            val imported = AppFont.UserFile("Example Custom Typeface.otf")
            val index = buildSettingsSearchIndex("Windows", listOf(AppFont.Default, AppFont.Flex, installed, imported))
            val fonts = searchSettings(index, "  FONT ")
            assertEquals("Font", fonts.first().title)
            assertEquals("pref_font", fonts.first().entry.highlightKey)
            assertEquals(SettingsSubPage.THEMES.place, fonts.first().entry.place)
            assertEquals("Interface › Themes", fonts.first().breadcrumb)
            assertEquals("pref_lyrics_font_title", searchSettings(index, "font lyrics").first().entry.highlightKey)
            assertEquals(fontSearchKey(AppFont.Flex), searchSettings(index, "kittytune-flex").first().entry.highlightKey)
            assertEquals(fontSearchKey(installed), searchSettings(index, "installed example").first().entry.highlightKey)
            assertEquals(fontSearchKey(imported), searchSettings(index, "example custom typeface.otf").first().entry.highlightKey)
            assertEquals("font_add", searchSettings(index, "add font").first().entry.highlightKey)
        }
    }

    @Test
    fun testSearchUsesLocalizedLabelsAndIgnoresAccentsAndPunctuation() {
        withLanguage("fr") {
            val index = buildSettingsSearchIndex("Windows", emptyList())
            val icon = searchSettings(index, " ICONE ").first()
            assertEquals("pref_app_icon", icon.entry.highlightKey)
            assertEquals("pref_font", searchSettings(index, "font").first().entry.highlightKey)
            assertEquals("pref_lyrics_font_title", searchSettings(index, "paroles police").first().entry.highlightKey)
            val interfaceMatches = searchSettings(index, "interface")
            assertEquals("settings_cat_interface", interfaceMatches.first().entry.titleKey)
            assertTrue(interfaceMatches.none { it.entry.titleKey == "pref_font" })
            assertTrue(searchSettings(index, "  !!! ").isEmpty())
        }
    }

    @Test
    fun testPerformanceControlsKeepTheirOldSearchTermsAndNewLabels() {
        withLanguage("en") {
            val index = buildSettingsSearchIndex("Windows", emptyList())
            val queries = mapOf(
                "performance_image_cache" to listOf("artwork memory cache", "covers kept in memory"),
                "performance_gpu_cache" to listOf("graphics resource cache", "memory for drawing"),
                "performance_disk_cache" to listOf("artwork disk cache", "covers saved on disk"),
                "performance_vsync" to listOf("vsync", "vertical synchronization", "keep motion in step with the screen"),
                "performance_gpu" to listOf("gpu", "preferred gpu", "graphics card"),
                "performance_renderer" to listOf("renderer", "drawing mode"),
            )
            for ((key, terms) in queries) for (term in terms) {
                assertEquals(key, searchSettings(index, term).first().entry.highlightKey, term)
            }
        }
    }

    @Test
    fun testSearchReachesControlsAcrossPagesAndProviderRoutes() {
        withLanguage("en") {
            val index = buildSettingsSearchIndex("Windows", emptyList())
            val renderer = searchSettings(index, "direct3d").first().entry
            assertEquals("performance_renderer", renderer.highlightKey)
            assertEquals(SettingsSubPage.PERFORMANCE_GRAPHICS.place, renderer.place)
            val preset = searchSettings(index, "smooth browsing").first().entry
            assertEquals("performance_preset_smooth", preset.highlightKey)
            assertEquals(SettingsSubPage.PERFORMANCE_PRESETS.place, preset.place)
            val cache = searchSettings(index, "artwork disk cache").first().entry
            assertEquals("performance_disk_cache", cache.highlightKey)
            assertEquals(SettingsSubPage.PERFORMANCE_CACHES.place, cache.place)
            assertEquals("storage_backup_restore", searchSettings(index, "restore backup").first().entry.highlightKey)
            assertEquals("proxy_port", searchSettings(index, "proxy port").first().entry.highlightKey)
            assertEquals("tidal_audio_quality", searchSettings(index, "tidal audio quality").first().entry.highlightKey)
            assertEquals("tidal_settings", searchSettings(index, "tidal audio quality").first().entry.route)
            val sizes = searchSettings(index, "lyrics size").filter { it.entry.highlightKey == "pref_lyrics_size" }
            assertEquals(3, sizes.map { it.entry.place }.distinct().size)
        }
    }

    @Test
    fun testCatalogDestinationsAreReachableAndUseRealLabels() {
        val brandNames = setOf("SoundCloud", "Qobuz", "TIDAL", "Deezer")
        for (os in listOf("Windows", "Linux", "Mac OS X")) {
            val catalog = buildSettingsSearchCatalog(os)
            for (entry in catalog) {
                entry.place.subPage?.let { assertEquals(it.place, entry.place) }
                val resourceKeys = listOfNotNull(entry.titleKey, entry.descriptionKey, entry.groupKey) + entry.aliasKeys
                for (key in resourceKeys - brandNames) {
                    assertTrue(Strings.english(key) != key, "Missing search resource '$key'")
                }
            }
            assertEquals(os == "Windows", catalog.any { it.titleKey == "performance_windows_gpu" })
        }
    }

    @Test
    fun testSearchHighlightCentersWithinTheScrollPane() {
        // Already visible near the bottom, below the viewport, and at the end of the page.
        val cases = listOf(Triple(176, 420, 200f), Triple(416, 420, 200f), Triple(416, 12, 280f))
        for ((before, after, expectedCenter) in cases) {
            val request = SettingsHighlight("option") {}
            var center = Float.NaN
            val scene = ImageComposeScene(width = 260, height = 420, density = Density(1f)) {
                Column {
                    Spacer(Modifier.height(80.dp))
                    SettingsHighlightScope(request) {
                        ScrollableColumn(Modifier.height(240.dp), hideScrollbar = true) {
                            Spacer(Modifier.height(before.dp))
                            Box(Modifier.fillMaxWidth().height(56.dp).settingHighlight("option")
                                .onGloballyPositioned { center = it.positionInRoot().y + it.size.height / 2f })
                            Spacer(Modifier.height(after.dp))
                        }
                    }
                }
            }
            settleHighlight(scene)
            assertEquals(expectedCenter, center, absoluteTolerance = 1f)
        }
    }

    @Test
    fun testSearchHighlightCentersAnAlreadyRevealedLazyRow() {
        val request = SettingsHighlight("option") {}
        var center = Float.NaN
        val scene = ImageComposeScene(width = 260, height = 420, density = Density(1f)) {
            Column {
                Spacer(Modifier.height(80.dp))
                SettingsHighlightScope(request) {
                    val state = rememberLazyListState(initialFirstVisibleItemIndex = 10)
                    ScrollableLazyColumn(Modifier.height(240.dp), state = state) {
                        items(24) { index ->
                            val row = Modifier.fillMaxWidth().height(56.dp)
                            if (index == 10) {
                                Box(row.settingHighlight("option")
                                    .onGloballyPositioned { center = it.positionInRoot().y + it.size.height / 2f })
                            } else {
                                Spacer(row)
                            }
                        }
                    }
                }
            }
        }
        settleHighlight(scene)
        // Lazy list layout rounds each scroll step to whole pixels.
        assertEquals(200f, center, absoluteTolerance = 3f)
    }

    @Test
    fun testSearchHighlightCentersAboveTheFloatingPlayer() {
        val request = SettingsHighlight("option") {}
        val playerBar = PlayerBarOverlay().apply { bounds = Rect(0f, 264f, 260f, 320f) }
        var center = Float.NaN
        val scene = ImageComposeScene(width = 260, height = 420, density = Density(1f)) {
            CompositionLocalProvider(LocalPlayerBarOverlay provides playerBar) {
                Column {
                    Spacer(Modifier.height(80.dp))
                    SettingsHighlightScope(request) {
                        ScrollableColumn(Modifier.height(240.dp), hideScrollbar = true) {
                            Spacer(Modifier.height(416.dp))
                            Box(Modifier.fillMaxWidth().height(56.dp).settingHighlight("option")
                                .onGloballyPositioned { center = it.positionInRoot().y + it.size.height / 2f })
                            Spacer(Modifier.height(420.dp))
                        }
                    }
                }
            }
        }
        settleHighlight(scene)
        assertEquals(172f, center, absoluteTolerance = 1f)
    }

    private fun settleHighlight(scene: ImageComposeScene) {
        try {
            for (frame in 0..90) scene.render(frame * 16_666_667L).close()
        } finally {
            scene.close()
        }
    }

    private fun withLanguage(language: String, block: () -> Unit) {
        val previous = Strings.appLanguage
        try {
            Strings.appLanguage = language
            block()
        } finally {
            Strings.appLanguage = previous
        }
    }

    @Test
    fun testSettingsSearchBarDimensionsAndClipping() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/profile/SettingsScreen.kt")
        assertTrue(file.exists())
        val content = file.readText()

        // The field is one composable shown in two places: above the categories, filling their column, and in
        // the pane's title row when the window is too narrow for that column to hold it.
        val searchFieldSection = content.substringAfter("private fun SettingsSearchField(").substringBefore("@Composable")
        assertTrue(
            !searchFieldSection.contains(".height(48.dp)"),
            "Search field must not constrain height to 48dp to prevent vertical text clipping"
        )
        assertTrue(
            content.contains("SettingsSearchField(searchQuery, onSearchQueryChange, Modifier.width(320.dp))"),
            "Search field in the pane must have adequate width (320dp) to fit placeholder text without compression"
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

import java.io.File
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicThemeTransitionTest {

    private fun uiFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/kotlin/com/alananasss/kittytune/$relativePath"),
            File("KittyTuneDesktop/src/main/kotlin/com/alananasss/kittytune/$relativePath"),
        )
        return candidates.first { it.isFile }
    }

    private fun nonImportAnimateColorCalls(source: String): List<String> =
        source.lineSequence()
            .filter { line ->
                val trimmed = line.trimStart()
                !trimmed.startsWith("import ") &&
                    !trimmed.startsWith("//") &&
                    !trimmed.startsWith("*") &&
                    trimmed.contains("animateColorAsState(")
            }
            .toList()

    @Test
    fun `chrome controls use animatePaletteColor instead of nested animateColorAsState`() {
        val controlFiles = listOf(
            "ui/main/TrackInfoTab.kt",
            "ui/main/NowPlayingPanel.kt",
            "ui/main/Sidebar.kt",
            "ui/main/MainTopBar.kt",
            "ui/main/PanelQueue.kt",
            "ui/main/HomeContent.kt",
            "ui/home/SongChart.kt",
            "ui/feed/FeedScreen.kt",
            "ui/track/TrackDetailScreen.kt",
            "ui/player/EffectsPanel.kt",
            "ui/player/ExpandedQueueScreen.kt",
            "ui/profile/SettingsScreen.kt",
            "ui/profile/AppearanceSettingsScreen.kt",
            "ui/profile/PlayerDesignScreen.kt",
            "ui/together/TogetherExpressiveComponents.kt",
            "ui/together/TogetherScreens.kt",
        )

        for (filePath in controlFiles) {
            val content = uiFile(filePath).readText()
            val nestedCalls = nonImportAnimateColorCalls(content)
            assertTrue(
                nestedCalls.isEmpty(),
                "$filePath still calls animateColorAsState($nestedCalls) which double-animates theme transitions",
            )
        }

        assertTrue(uiFile("ui/main/TrackInfoTab.kt").readText().contains("animatePaletteColor"))
        assertTrue(uiFile("ui/main/NowPlayingPanel.kt").readText().contains("animatePaletteColor"))
        assertTrue(uiFile("ui/main/Sidebar.kt").readText().contains("animatePaletteColor"))
    }

    @Test
    fun `theme routes all color scheme branches through easedInto`() {
        val themeSource = uiFile("ui/theme/Theme.kt").readText()
        assertFalse(
            themeSource.contains("return end4.scheme") || themeSource.contains("return winScheme"),
            "Theme branches should flow through easedInto rather than returning early and snapping",
        )
        assertTrue(themeSource.contains("target.easedInto("))
        assertTrue(themeSource.contains("fun animatePaletteColor("))
    }
}

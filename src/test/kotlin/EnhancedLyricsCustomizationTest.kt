import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnhancedLyricsCustomizationTest {

    @Test
    fun testLineSpacingPreferencesIndependenceAndClamping() {
        val prefs = PlayerPreferences()
        val origPanel = prefs.getLyricsLineSpacing()
        val origFullscreen = prefs.getLyricsFullScreenLineSpacing()

        try {
            // Test independent settings
            prefs.setLyricsLineSpacing(18f)
            prefs.setLyricsFullScreenLineSpacing(32f)

            assertEquals(18f, prefs.getLyricsLineSpacing())
            assertEquals(32f, prefs.getLyricsFullScreenLineSpacing())

            // Changing one must not affect the other
            prefs.setLyricsLineSpacing(10f)
            assertEquals(10f, prefs.getLyricsLineSpacing())
            assertEquals(32f, prefs.getLyricsFullScreenLineSpacing())

            // Test clamping for panel: 0f..48f
            prefs.setLyricsLineSpacing(-5f)
            assertEquals(0f, prefs.getLyricsLineSpacing())
            prefs.setLyricsLineSpacing(100f)
            assertEquals(48f, prefs.getLyricsLineSpacing())

            // Test clamping for fullscreen: 0f..64f
            prefs.setLyricsFullScreenLineSpacing(-5f)
            assertEquals(0f, prefs.getLyricsFullScreenLineSpacing())
            prefs.setLyricsFullScreenLineSpacing(100f)
            assertEquals(64f, prefs.getLyricsFullScreenLineSpacing())
        } finally {
            prefs.setLyricsLineSpacing(origPanel)
            prefs.setLyricsFullScreenLineSpacing(origFullscreen)
        }
    }

    @Test
    fun testActiveScalePreferencesAndClamping() {
        val prefs = PlayerPreferences()
        val origScale = prefs.getLyricsActiveScale()

        try {
            prefs.setLyricsActiveScale(1.15f)
            assertEquals(1.15f, prefs.getLyricsActiveScale(), 0.001f)

            // Test clamping: 1.00f..1.30f
            prefs.setLyricsActiveScale(0.80f)
            assertEquals(1.00f, prefs.getLyricsActiveScale(), 0.001f)

            prefs.setLyricsActiveScale(1.50f)
            assertEquals(1.30f, prefs.getLyricsActiveScale(), 0.001f)
        } finally {
            prefs.setLyricsActiveScale(origScale)
        }
    }

    @Test
    fun testMarginsPreferencesIndependenceAndClamping() {
        val prefs = PlayerPreferences()
        val origPanelHoriz = prefs.getLyricsHorizontalMargin()
        val origFsHoriz = prefs.getLyricsFullScreenHorizontalMargin()
        val origPanelVert = prefs.getLyricsVerticalOffset()
        val origFsVert = prefs.getLyricsFullScreenVerticalOffset()

        try {
            // Horizontal margins
            prefs.setLyricsHorizontalMargin(20f)
            prefs.setLyricsFullScreenHorizontalMargin(60f)
            assertEquals(20f, prefs.getLyricsHorizontalMargin())
            assertEquals(60f, prefs.getLyricsFullScreenHorizontalMargin())

            // Clamping for horizontal margins: panel 0..64, fullscreen 0..160
            prefs.setLyricsHorizontalMargin(-10f)
            assertEquals(0f, prefs.getLyricsHorizontalMargin())
            prefs.setLyricsHorizontalMargin(90f)
            assertEquals(64f, prefs.getLyricsHorizontalMargin())

            prefs.setLyricsFullScreenHorizontalMargin(-10f)
            assertEquals(0f, prefs.getLyricsFullScreenHorizontalMargin())
            prefs.setLyricsFullScreenHorizontalMargin(200f)
            assertEquals(160f, prefs.getLyricsFullScreenHorizontalMargin())

            // Vertical offset clamping: 0.20f..0.60f
            prefs.setLyricsVerticalOffset(0.25f)
            prefs.setLyricsFullScreenVerticalOffset(0.45f)
            assertEquals(0.25f, prefs.getLyricsVerticalOffset(), 0.001f)
            assertEquals(0.45f, prefs.getLyricsFullScreenVerticalOffset(), 0.001f)

            prefs.setLyricsVerticalOffset(0.10f)
            assertEquals(0.20f, prefs.getLyricsVerticalOffset(), 0.001f)
            prefs.setLyricsVerticalOffset(0.80f)
            assertEquals(0.60f, prefs.getLyricsVerticalOffset(), 0.001f)

            prefs.setLyricsFullScreenVerticalOffset(0.10f)
            assertEquals(0.20f, prefs.getLyricsFullScreenVerticalOffset(), 0.001f)
            prefs.setLyricsFullScreenVerticalOffset(0.80f)
            assertEquals(0.60f, prefs.getLyricsFullScreenVerticalOffset(), 0.001f)
        } finally {
            prefs.setLyricsHorizontalMargin(origPanelHoriz)
            prefs.setLyricsFullScreenHorizontalMargin(origFsHoriz)
            prefs.setLyricsVerticalOffset(origPanelVert)
            prefs.setLyricsFullScreenVerticalOffset(origFsVert)
        }
    }

    @Test
    fun testResetTypographyValues() {
        val prefs = PlayerPreferences()
        val origFontSize = prefs.getLyricsFontSize()
        val origFsFontSize = prefs.getLyricsFullScreenFontSize()
        val origLineSpacing = prefs.getLyricsLineSpacing()
        val origFsLineSpacing = prefs.getLyricsFullScreenLineSpacing()
        val origActiveScale = prefs.getLyricsActiveScale()
        val origHoriz = prefs.getLyricsHorizontalMargin()
        val origFsHoriz = prefs.getLyricsFullScreenHorizontalMargin()
        val origVert = prefs.getLyricsVerticalOffset()
        val origFsVert = prefs.getLyricsFullScreenVerticalOffset()

        try {
            // Modify all settings to non-default values
            prefs.setLyricsFontSize(20f)
            prefs.setLyricsFullScreenFontSize(20f)
            prefs.setLyricsLineSpacing(8f)
            prefs.setLyricsFullScreenLineSpacing(12f)
            prefs.setLyricsActiveScale(1.25f)
            prefs.setLyricsHorizontalMargin(30f)
            prefs.setLyricsFullScreenHorizontalMargin(80f)
            prefs.setLyricsVerticalOffset(0.50f)
            prefs.setLyricsFullScreenVerticalOffset(0.50f)

            // Panel reset to standard default sizes
            prefs.setLyricsFontSize(42f)
            prefs.setLyricsLineSpacing(0f)
            prefs.setLyricsHorizontalMargin(0f)
            prefs.setLyricsVerticalOffset(0.38f)
            prefs.setLyricsActiveScale(1.00f)

            assertEquals(42f, prefs.getLyricsFontSize())
            assertEquals(0f, prefs.getLyricsLineSpacing())
            assertEquals(0f, prefs.getLyricsHorizontalMargin())
            assertEquals(0.38f, prefs.getLyricsVerticalOffset(), 0.001f)
            assertEquals(1.00f, prefs.getLyricsActiveScale(), 0.001f)

            // Fullscreen reset to standard default sizes
            prefs.setLyricsFullScreenFontSize(42f)
            prefs.setLyricsFullScreenLineSpacing(0f)
            prefs.setLyricsFullScreenHorizontalMargin(0f)
            prefs.setLyricsFullScreenVerticalOffset(0.38f)

            assertEquals(42f, prefs.getLyricsFullScreenFontSize())
            assertEquals(0f, prefs.getLyricsFullScreenLineSpacing())
            assertEquals(0f, prefs.getLyricsFullScreenHorizontalMargin())
            assertEquals(0.38f, prefs.getLyricsFullScreenVerticalOffset(), 0.001f)
        } finally {
            prefs.setLyricsFontSize(origFontSize)
            prefs.setLyricsFullScreenFontSize(origFsFontSize)
            prefs.setLyricsLineSpacing(origLineSpacing)
            prefs.setLyricsFullScreenLineSpacing(origFsLineSpacing)
            prefs.setLyricsActiveScale(origActiveScale)
            prefs.setLyricsHorizontalMargin(origHoriz)
            prefs.setLyricsFullScreenHorizontalMargin(origFsHoriz)
            prefs.setLyricsVerticalOffset(origVert)
            prefs.setLyricsFullScreenVerticalOffset(origFsVert)
        }
    }

    @Test
    fun testIndependentLayoutModeFontSizes() {
        val prefs = PlayerPreferences()
        val origCentral = prefs.getLyricsFontSize()
        val origFullscreen = prefs.getLyricsFullScreenFontSize()
        val origSidebar = prefs.getLyricsSidebarFontSize()

        try {
            // Set the three example values from user request
            prefs.setLyricsSidebarFontSize(22f)
            prefs.setLyricsFontSize(55f)
            prefs.setLyricsFullScreenFontSize(70f)

            // Verify all three independent values
            assertEquals(22f, prefs.getLyricsSidebarFontSize())
            assertEquals(55f, prefs.getLyricsFontSize())
            assertEquals(70f, prefs.getLyricsFullScreenFontSize())

            // Changing side panel must not affect central or full-screen
            prefs.setLyricsSidebarFontSize(28f)
            assertEquals(28f, prefs.getLyricsSidebarFontSize())
            assertEquals(55f, prefs.getLyricsFontSize())
            assertEquals(70f, prefs.getLyricsFullScreenFontSize())

            // Changing central mode must not affect side panel or full-screen
            prefs.setLyricsFontSize(48f)
            assertEquals(28f, prefs.getLyricsSidebarFontSize())
            assertEquals(48f, prefs.getLyricsFontSize())
            assertEquals(70f, prefs.getLyricsFullScreenFontSize())

            // Changing full-screen mode must not affect side panel or central
            prefs.setLyricsFullScreenFontSize(80f)
            assertEquals(28f, prefs.getLyricsSidebarFontSize())
            assertEquals(48f, prefs.getLyricsFontSize())
            assertEquals(80f, prefs.getLyricsFullScreenFontSize())
        } finally {
            prefs.setLyricsFontSize(origCentral)
            prefs.setLyricsFullScreenFontSize(origFullscreen)
            prefs.setLyricsSidebarFontSize(origSidebar)
        }
    }

    @Test
    fun testIndependentLayoutModeUiStyles() {
        val prefs = PlayerPreferences()
        val origCentral = prefs.getLyricsUiStyle()
        val origFullscreen = prefs.getLyricsFullScreenUiStyle()
        val origSidebar = prefs.getLyricsSidebarUiStyle()

        try {
            // Default verification
            // Sidebar must default to CLASSIC
            // Central & Fullscreen must default to ENHANCED
            Prefs.remove("lyrics_sidebar_ui_style")
            Prefs.remove("lyrics_ui_style")
            Prefs.remove("lyrics_fullscreen_ui_style")

            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC, prefs.getLyricsSidebarUiStyle())
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED, prefs.getLyricsUiStyle())
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED, prefs.getLyricsFullScreenUiStyle())

            // Changing sidebar to ENHANCED must not change central or fullscreen
            prefs.setLyricsSidebarUiStyle(com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED)
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED, prefs.getLyricsSidebarUiStyle())
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED, prefs.getLyricsUiStyle())
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED, prefs.getLyricsFullScreenUiStyle())

            // Changing fullscreen to CLASSIC must not change sidebar or central
            prefs.setLyricsFullScreenUiStyle(com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC)
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED, prefs.getLyricsSidebarUiStyle())
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED, prefs.getLyricsUiStyle())
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC, prefs.getLyricsFullScreenUiStyle())

            // Changing central to CLASSIC must not change sidebar or fullscreen
            prefs.setLyricsUiStyle(com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC)
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED, prefs.getLyricsSidebarUiStyle())
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC, prefs.getLyricsUiStyle())
            assertEquals(com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC, prefs.getLyricsFullScreenUiStyle())
        } finally {
            prefs.setLyricsUiStyle(origCentral)
            prefs.setLyricsFullScreenUiStyle(origFullscreen)
            prefs.setLyricsSidebarUiStyle(origSidebar)
        }
    }

    @Test
    fun testIndependentLayoutModeLineBlur() {
        val prefs = PlayerPreferences()
        val origCentral = prefs.getLyricsLineBlurEnabled()
        val origFullscreen = prefs.getLyricsFullScreenLineBlurEnabled()
        val origSidebar = prefs.getLyricsSidebarLineBlurEnabled()

        try {
            // Verify default values: all true
            Prefs.remove("lyrics_line_blur_enabled")
            Prefs.remove("lyrics_fullscreen_line_blur_enabled")
            Prefs.remove("lyrics_sidebar_line_blur_enabled")

            assertEquals(true, prefs.getLyricsLineBlurEnabled())
            assertEquals(true, prefs.getLyricsFullScreenLineBlurEnabled())
            assertEquals(true, prefs.getLyricsSidebarLineBlurEnabled())

            // Disabling sidebar blur must not affect central or fullscreen
            prefs.setLyricsSidebarLineBlurEnabled(false)
            assertEquals(false, prefs.getLyricsSidebarLineBlurEnabled())
            assertEquals(true, prefs.getLyricsLineBlurEnabled())
            assertEquals(true, prefs.getLyricsFullScreenLineBlurEnabled())

            // Disabling central blur must not affect sidebar or fullscreen
            prefs.setLyricsLineBlurEnabled(false)
            assertEquals(false, prefs.getLyricsSidebarLineBlurEnabled())
            assertEquals(false, prefs.getLyricsLineBlurEnabled())
            assertEquals(true, prefs.getLyricsFullScreenLineBlurEnabled())

            // Disabling fullscreen blur must not affect sidebar or central
            prefs.setLyricsFullScreenLineBlurEnabled(false)
            assertEquals(false, prefs.getLyricsSidebarLineBlurEnabled())
            assertEquals(false, prefs.getLyricsLineBlurEnabled())
            assertEquals(false, prefs.getLyricsFullScreenLineBlurEnabled())

            // Re-enabling central blur must only affect central
            prefs.setLyricsLineBlurEnabled(true)
            assertEquals(false, prefs.getLyricsSidebarLineBlurEnabled())
            assertEquals(true, prefs.getLyricsLineBlurEnabled())
            assertEquals(false, prefs.getLyricsFullScreenLineBlurEnabled())
        } finally {
            prefs.setLyricsLineBlurEnabled(origCentral)
            prefs.setLyricsFullScreenLineBlurEnabled(origFullscreen)
            prefs.setLyricsSidebarLineBlurEnabled(origSidebar)
        }
    }

    @Test
    fun testIndependentLayoutModeAlignment() {
        val prefs = PlayerPreferences()
        val origCentral = prefs.getLyricsAlignment()
        val origFullscreen = prefs.getLyricsFullScreenAlignment()
        val origSidebar = prefs.getLyricsSidebarAlignment()

        try {
            // Test default values
            Prefs.remove("lyrics_alignment")
            Prefs.remove("lyrics_fullscreen_alignment")
            Prefs.remove("lyrics_sidebar_alignment")

            assertEquals(LyricsAlignment.LEFT, prefs.getLyricsAlignment())
            assertEquals(LyricsAlignment.LEFT, prefs.getLyricsFullScreenAlignment())
            assertEquals(LyricsAlignment.LEFT, prefs.getLyricsSidebarAlignment())

            // Setting sidebar alignment to RIGHT must not affect central or fullscreen
            prefs.setLyricsSidebarAlignment(LyricsAlignment.RIGHT)
            assertEquals(LyricsAlignment.RIGHT, prefs.getLyricsSidebarAlignment())
            assertEquals(LyricsAlignment.LEFT, prefs.getLyricsAlignment())
            assertEquals(LyricsAlignment.LEFT, prefs.getLyricsFullScreenAlignment())

            // Setting central alignment to CENTER must not affect sidebar or fullscreen
            prefs.setLyricsAlignment(LyricsAlignment.CENTER)
            assertEquals(LyricsAlignment.RIGHT, prefs.getLyricsSidebarAlignment())
            assertEquals(LyricsAlignment.CENTER, prefs.getLyricsAlignment())
            assertEquals(LyricsAlignment.LEFT, prefs.getLyricsFullScreenAlignment())

            // Setting fullscreen alignment to RIGHT must not affect sidebar or central
            prefs.setLyricsFullScreenAlignment(LyricsAlignment.RIGHT)
            assertEquals(LyricsAlignment.RIGHT, prefs.getLyricsSidebarAlignment())
            assertEquals(LyricsAlignment.CENTER, prefs.getLyricsAlignment())
            assertEquals(LyricsAlignment.RIGHT, prefs.getLyricsFullScreenAlignment())
        } finally {
            prefs.setLyricsAlignment(origCentral)
            prefs.setLyricsFullScreenAlignment(origFullscreen)
            prefs.setLyricsSidebarAlignment(origSidebar)
        }
    }
}



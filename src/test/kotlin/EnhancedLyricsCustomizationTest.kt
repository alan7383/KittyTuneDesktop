import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.InstrumentalIndicatorStyle
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.LyricsFont
import com.alananasss.kittytune.data.local.LyricsSurfaceMode
import com.alananasss.kittytune.data.local.LyricsUiStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        try {
            // Even with legacy CLASSIC values persisted, all 3 surfaces always use ENHANCED
            Prefs.putString("lyrics_ui_style", LyricsUiStyle.CLASSIC.name)
            Prefs.putString("lyrics_fullscreen_ui_style", LyricsUiStyle.CLASSIC.name)
            Prefs.putString("lyrics_sidebar_ui_style", LyricsUiStyle.CLASSIC.name)

            assertEquals(LyricsUiStyle.ENHANCED, prefs.getLyricsUiStyle())
            assertEquals(LyricsUiStyle.ENHANCED, prefs.getLyricsFullScreenUiStyle())
            assertEquals(LyricsUiStyle.ENHANCED, prefs.getLyricsSidebarUiStyle())

            prefs.setLyricsUiStyle(LyricsUiStyle.CLASSIC)
            prefs.setLyricsFullScreenUiStyle(LyricsUiStyle.CLASSIC)
            prefs.setLyricsSidebarUiStyle(LyricsUiStyle.CLASSIC)

            assertEquals(LyricsUiStyle.ENHANCED, prefs.getLyricsUiStyle())
            assertEquals(LyricsUiStyle.ENHANCED, prefs.getLyricsFullScreenUiStyle())
            assertEquals(LyricsUiStyle.ENHANCED, prefs.getLyricsSidebarUiStyle())
        } finally {
            Prefs.remove("lyrics_ui_style")
            Prefs.remove("lyrics_fullscreen_ui_style")
            Prefs.remove("lyrics_sidebar_ui_style")
        }
    }

    @Test
    fun testPerModeLyricsSettingsIsolation() {
        val prefs = PlayerPreferences()
        val modes = listOf(LyricsSurfaceMode.CENTRAL, LyricsSurfaceMode.FULLSCREEN, LyricsSurfaceMode.SIDEBAR)
        val origFonts = modes.associateWith { prefs.getLyricsFont(it) }
        val origWordSync = modes.associateWith { prefs.getLyricsWordSyncEnabled(it) }
        val origAppleEffect = modes.associateWith { prefs.getLyricsAppleEffectEnabled(it) }
        val origDuet = modes.associateWith { prefs.getLyricsDuetViewEnabled(it) }
        val origBacking = modes.associateWith { prefs.getLyricsSplitBackingVocals(it) }
        val origReveal = modes.associateWith { prefs.getLyricsRevealWords(it) }
        val origSweep = modes.associateWith { prefs.getLyricsSweepEnabled(it) }
        val origInstrumental = modes.associateWith { prefs.getInstrumentalIndicatorStyle(it) }
        val origLrcBounce = modes.associateWith { prefs.getLyricsLrcBounceEnabled(it) }
        val origBounce = modes.associateWith { prefs.getLyricsBounceFactor(it) }
        val origGlow = modes.associateWith { prefs.getLyricsGlowFactor(it) }
        val origFill = modes.associateWith { prefs.getLyricsFillTransitionWidth(it) }
        val origTrans = modes.associateWith { prefs.getLyricsTranslationEnabled(it) }
        val origRoman = modes.associateWith { prefs.getLyricsRomanizationEnabled(it) }

        try {
            // Mutate only FULLSCREEN and verify CENTRAL and SIDEBAR remain untouched
            prefs.setLyricsFont(LyricsSurfaceMode.CENTRAL, LyricsFont.APPLE)
            prefs.setLyricsFont(LyricsSurfaceMode.SIDEBAR, LyricsFont.APPLE)
            prefs.setLyricsFont(LyricsSurfaceMode.FULLSCREEN, LyricsFont.APP_DEFAULT)
            assertEquals(LyricsFont.APPLE, prefs.getLyricsFont(LyricsSurfaceMode.CENTRAL))
            assertEquals(LyricsFont.APP_DEFAULT, prefs.getLyricsFont(LyricsSurfaceMode.FULLSCREEN))
            assertEquals(LyricsFont.APPLE, prefs.getLyricsFont(LyricsSurfaceMode.SIDEBAR))

            // Mutate only SIDEBAR and verify CENTRAL and FULLSCREEN remain untouched
            prefs.setLyricsFont(LyricsSurfaceMode.SIDEBAR, LyricsFont.APP_DEFAULT)
            prefs.setLyricsFont(LyricsSurfaceMode.FULLSCREEN, LyricsFont.APPLE)
            assertEquals(LyricsFont.APPLE, prefs.getLyricsFont(LyricsSurfaceMode.CENTRAL))
            assertEquals(LyricsFont.APPLE, prefs.getLyricsFont(LyricsSurfaceMode.FULLSCREEN))
            assertEquals(LyricsFont.APP_DEFAULT, prefs.getLyricsFont(LyricsSurfaceMode.SIDEBAR))

            // Booleans & enums per mode
            prefs.setLyricsWordSyncEnabled(LyricsSurfaceMode.CENTRAL, true)
            prefs.setLyricsWordSyncEnabled(LyricsSurfaceMode.FULLSCREEN, false)
            prefs.setLyricsWordSyncEnabled(LyricsSurfaceMode.SIDEBAR, true)
            assertTrue(prefs.getLyricsWordSyncEnabled(LyricsSurfaceMode.CENTRAL))
            assertFalse(prefs.getLyricsWordSyncEnabled(LyricsSurfaceMode.FULLSCREEN))
            assertTrue(prefs.getLyricsWordSyncEnabled(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsAppleEffectEnabled(LyricsSurfaceMode.CENTRAL, false)
            prefs.setLyricsAppleEffectEnabled(LyricsSurfaceMode.FULLSCREEN, true)
            prefs.setLyricsAppleEffectEnabled(LyricsSurfaceMode.SIDEBAR, false)
            assertFalse(prefs.getLyricsAppleEffectEnabled(LyricsSurfaceMode.CENTRAL))
            assertTrue(prefs.getLyricsAppleEffectEnabled(LyricsSurfaceMode.FULLSCREEN))
            assertFalse(prefs.getLyricsAppleEffectEnabled(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsDuetViewEnabled(LyricsSurfaceMode.CENTRAL, true)
            prefs.setLyricsDuetViewEnabled(LyricsSurfaceMode.FULLSCREEN, true)
            prefs.setLyricsDuetViewEnabled(LyricsSurfaceMode.SIDEBAR, false)
            assertTrue(prefs.getLyricsDuetViewEnabled(LyricsSurfaceMode.CENTRAL))
            assertTrue(prefs.getLyricsDuetViewEnabled(LyricsSurfaceMode.FULLSCREEN))
            assertFalse(prefs.getLyricsDuetViewEnabled(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsSplitBackingVocals(LyricsSurfaceMode.CENTRAL, false)
            prefs.setLyricsSplitBackingVocals(LyricsSurfaceMode.FULLSCREEN, true)
            prefs.setLyricsSplitBackingVocals(LyricsSurfaceMode.SIDEBAR, false)
            assertFalse(prefs.getLyricsSplitBackingVocals(LyricsSurfaceMode.CENTRAL))
            assertTrue(prefs.getLyricsSplitBackingVocals(LyricsSurfaceMode.FULLSCREEN))
            assertFalse(prefs.getLyricsSplitBackingVocals(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsRevealWords(LyricsSurfaceMode.CENTRAL, true)
            prefs.setLyricsRevealWords(LyricsSurfaceMode.FULLSCREEN, false)
            prefs.setLyricsRevealWords(LyricsSurfaceMode.SIDEBAR, true)
            assertTrue(prefs.getLyricsRevealWords(LyricsSurfaceMode.CENTRAL))
            assertFalse(prefs.getLyricsRevealWords(LyricsSurfaceMode.FULLSCREEN))
            assertTrue(prefs.getLyricsRevealWords(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsSweepEnabled(LyricsSurfaceMode.CENTRAL, false)
            prefs.setLyricsSweepEnabled(LyricsSurfaceMode.FULLSCREEN, true)
            prefs.setLyricsSweepEnabled(LyricsSurfaceMode.SIDEBAR, false)
            assertFalse(prefs.getLyricsSweepEnabled(LyricsSurfaceMode.CENTRAL))
            assertTrue(prefs.getLyricsSweepEnabled(LyricsSurfaceMode.FULLSCREEN))
            assertFalse(prefs.getLyricsSweepEnabled(LyricsSurfaceMode.SIDEBAR))

            prefs.setInstrumentalIndicatorStyle(LyricsSurfaceMode.CENTRAL, InstrumentalIndicatorStyle.DOTS)
            prefs.setInstrumentalIndicatorStyle(LyricsSurfaceMode.FULLSCREEN, InstrumentalIndicatorStyle.NOTE_WAVE)
            prefs.setInstrumentalIndicatorStyle(LyricsSurfaceMode.SIDEBAR, InstrumentalIndicatorStyle.OFF)
            assertEquals(InstrumentalIndicatorStyle.DOTS, prefs.getInstrumentalIndicatorStyle(LyricsSurfaceMode.CENTRAL))
            assertEquals(InstrumentalIndicatorStyle.NOTE_WAVE, prefs.getInstrumentalIndicatorStyle(LyricsSurfaceMode.FULLSCREEN))
            assertEquals(InstrumentalIndicatorStyle.OFF, prefs.getInstrumentalIndicatorStyle(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsLrcBounceEnabled(LyricsSurfaceMode.CENTRAL, false)
            prefs.setLyricsLrcBounceEnabled(LyricsSurfaceMode.FULLSCREEN, true)
            prefs.setLyricsLrcBounceEnabled(LyricsSurfaceMode.SIDEBAR, true)
            assertFalse(prefs.getLyricsLrcBounceEnabled(LyricsSurfaceMode.CENTRAL))
            assertTrue(prefs.getLyricsLrcBounceEnabled(LyricsSurfaceMode.FULLSCREEN))
            assertTrue(prefs.getLyricsLrcBounceEnabled(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsBounceFactor(LyricsSurfaceMode.CENTRAL, 0.5f)
            prefs.setLyricsBounceFactor(LyricsSurfaceMode.FULLSCREEN, 1.5f)
            prefs.setLyricsBounceFactor(LyricsSurfaceMode.SIDEBAR, 1.8f)
            assertEquals(0.5f, prefs.getLyricsBounceFactor(LyricsSurfaceMode.CENTRAL))
            assertEquals(1.5f, prefs.getLyricsBounceFactor(LyricsSurfaceMode.FULLSCREEN))
            assertEquals(1.8f, prefs.getLyricsBounceFactor(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsGlowFactor(LyricsSurfaceMode.CENTRAL, 0.2f)
            prefs.setLyricsGlowFactor(LyricsSurfaceMode.FULLSCREEN, 1.4f)
            prefs.setLyricsGlowFactor(LyricsSurfaceMode.SIDEBAR, 0.9f)
            assertEquals(0.2f, prefs.getLyricsGlowFactor(LyricsSurfaceMode.CENTRAL))
            assertEquals(1.4f, prefs.getLyricsGlowFactor(LyricsSurfaceMode.FULLSCREEN))
            assertEquals(0.9f, prefs.getLyricsGlowFactor(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsFillTransitionWidth(LyricsSurfaceMode.CENTRAL, 4f)
            prefs.setLyricsFillTransitionWidth(LyricsSurfaceMode.FULLSCREEN, 16f)
            prefs.setLyricsFillTransitionWidth(LyricsSurfaceMode.SIDEBAR, 10f)
            assertEquals(4f, prefs.getLyricsFillTransitionWidth(LyricsSurfaceMode.CENTRAL))
            assertEquals(16f, prefs.getLyricsFillTransitionWidth(LyricsSurfaceMode.FULLSCREEN))
            assertEquals(10f, prefs.getLyricsFillTransitionWidth(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsTranslationEnabled(LyricsSurfaceMode.CENTRAL, false)
            prefs.setLyricsTranslationEnabled(LyricsSurfaceMode.FULLSCREEN, true)
            prefs.setLyricsTranslationEnabled(LyricsSurfaceMode.SIDEBAR, false)
            assertFalse(prefs.getLyricsTranslationEnabled(LyricsSurfaceMode.CENTRAL))
            assertTrue(prefs.getLyricsTranslationEnabled(LyricsSurfaceMode.FULLSCREEN))
            assertFalse(prefs.getLyricsTranslationEnabled(LyricsSurfaceMode.SIDEBAR))

            prefs.setLyricsRomanizationEnabled(LyricsSurfaceMode.CENTRAL, true)
            prefs.setLyricsRomanizationEnabled(LyricsSurfaceMode.FULLSCREEN, false)
            prefs.setLyricsRomanizationEnabled(LyricsSurfaceMode.SIDEBAR, false)
            assertTrue(prefs.getLyricsRomanizationEnabled(LyricsSurfaceMode.CENTRAL))
            assertFalse(prefs.getLyricsRomanizationEnabled(LyricsSurfaceMode.FULLSCREEN))
            assertFalse(prefs.getLyricsRomanizationEnabled(LyricsSurfaceMode.SIDEBAR))
        } finally {
            modes.forEach { m ->
                prefs.setLyricsFont(m, origFonts.getValue(m))
                prefs.setLyricsWordSyncEnabled(m, origWordSync.getValue(m))
                prefs.setLyricsAppleEffectEnabled(m, origAppleEffect.getValue(m))
                prefs.setLyricsDuetViewEnabled(m, origDuet.getValue(m))
                prefs.setLyricsSplitBackingVocals(m, origBacking.getValue(m))
                prefs.setLyricsRevealWords(m, origReveal.getValue(m))
                prefs.setLyricsSweepEnabled(m, origSweep.getValue(m))
                prefs.setInstrumentalIndicatorStyle(m, origInstrumental.getValue(m))
                prefs.setLyricsLrcBounceEnabled(m, origLrcBounce.getValue(m))
                prefs.setLyricsBounceFactor(m, origBounce.getValue(m))
                prefs.setLyricsGlowFactor(m, origGlow.getValue(m))
                prefs.setLyricsFillTransitionWidth(m, origFill.getValue(m))
                prefs.setLyricsTranslationEnabled(m, origTrans.getValue(m))
                prefs.setLyricsRomanizationEnabled(m, origRoman.getValue(m))
            }
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

    @Test
    fun testCentralLyricsPositionDefaultsLeftAndPersistsIndependently() {
        val prefs = PlayerPreferences()
        val origPos = prefs.getLyricsCentralPosition()
        val origAlign = prefs.getLyricsAlignment()
        try {
            Prefs.remove("lyrics_central_position")
            assertEquals(LyricsAlignment.LEFT, prefs.getLyricsCentralPosition())

            prefs.setLyricsAlignment(LyricsAlignment.LEFT)
            prefs.setLyricsCentralPosition(LyricsAlignment.CENTER)
            assertEquals(LyricsAlignment.CENTER, prefs.getLyricsCentralPosition())
            assertEquals(LyricsAlignment.LEFT, prefs.getLyricsAlignment())

            prefs.setLyricsCentralPosition(LyricsAlignment.RIGHT)
            assertEquals(LyricsAlignment.RIGHT, prefs.getLyricsCentralPosition())
            assertEquals(LyricsAlignment.LEFT, prefs.getLyricsAlignment())
        } finally {
            prefs.setLyricsCentralPosition(origPos)
            prefs.setLyricsAlignment(origAlign)
        }
    }
}



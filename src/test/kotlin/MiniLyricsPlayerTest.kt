import com.alananasss.kittytune.data.local.MiniPlayerStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.mini.resolveActiveChunk
import com.alananasss.kittytune.ui.player.mini.splitIntoChunks
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniLyricsPlayerTest {

    @Test
    fun short_line_stays_as_one_chunk() {
        val words = listOf(
            LyricWord("Hold ", 0L, 500L),
            LyricWord("on", 500L, 1000L),
        )
        val text = "Hold on"
        val chunks = splitIntoChunks(text, words, maxChunkChars = 36)
        assertEquals(1, chunks.size)
        assertEquals("Hold on", chunks[0].text)
        assertEquals(0L, chunks[0].startTime)
        assertEquals(1000L, chunks[0].endTime)
    }

    @Test
    fun long_line_splits_on_word_boundaries() {
        val words = listOf(
            LyricWord("Brought ", 0L, 800L),
            LyricWord("to ", 800L, 1200L),
            LyricWord("you ", 1200L, 1600L),
            LyricWord("with ", 1600L, 2000L),
            LyricWord("Apple ", 2000L, 2600L),
            LyricWord("Music ", 2600L, 3200L),
            LyricWord("and ", 3200L, 3500L),
            LyricWord("KittyTune ", 3500L, 4200L),
            LyricWord("Desktop ", 4200L, 4800L),
            LyricWord("Player", 4800L, 5500L),
        )
        val text = words.joinToString("") { it.text }
        val chunks = splitIntoChunks(text, words, maxChunkChars = 32)
        assertTrue(chunks.size >= 2, "Long line should be split into at least 2 chunks")
        for (chunk in chunks) {
            assertTrue(chunk.text.isNotBlank())
            assertTrue(chunk.words.isNotEmpty())
        }
        assertEquals(0L, chunks.first().startTime)
        assertEquals(5500L, chunks.last().endTime)
    }

    @Test
    fun active_chunk_resolves_correctly_by_playback_time() {
        val words1 = listOf(LyricWord("Part ", 0L, 1000L), LyricWord("one ", 1000L, 2000L))
        val words2 = listOf(LyricWord("Part ", 2000L, 3000L), LyricWord("two", 3000L, 4000L))
        val text = "Part one Part two"
        val chunks = splitIntoChunks(text, words1 + words2, maxChunkChars = 10)
        assertEquals(2, chunks.size)

        assertEquals(0, resolveActiveChunk(chunks, 500f))
        assertEquals(0, resolveActiveChunk(chunks, 1999f))
        assertEquals(1, resolveActiveChunk(chunks, 2500f))
        assertEquals(1, resolveActiveChunk(chunks, 3900f))
        assertEquals(1, resolveActiveChunk(chunks, 9999f))
    }

    @Test
    fun mini_player_scaling_bounds_persists_width_and_height() {
        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        val origX = prefs.getMiniPlayerX() ?: 100
        val origY = prefs.getMiniPlayerY() ?: 100
        val origW = prefs.getMiniPlayerWidth()
        val origH = prefs.getMiniPlayerHeight()

        try {
            // Save custom scaled bounds
            prefs.setMiniPlayerBounds(x = 250, y = 350, width = 780, height = 110)

            assertEquals(250, prefs.getMiniPlayerX())
            assertEquals(350, prefs.getMiniPlayerY())
            assertEquals(780, prefs.getMiniPlayerWidth())
            assertEquals(110, prefs.getMiniPlayerHeight())
        } finally {
            // Restore original values
            prefs.setMiniPlayerBounds(origX, origY, origW, origH)
        }
    }

    @Test
    fun mini_player_bounds_coerces_extreme_scale_dimensions() {
        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        val origX = prefs.getMiniPlayerX() ?: 100
        val origY = prefs.getMiniPlayerY() ?: 100
        val origW = prefs.getMiniPlayerWidth()
        val origH = prefs.getMiniPlayerHeight()

        try {
            // Test below min limits
            prefs.setMiniPlayerBounds(x = 10, y = 20, width = 100, height = 20)
            assertEquals(PlayerPreferences.MINI_PLAYER_MIN_WIDTH, prefs.getMiniPlayerWidth(), "Width should clamp to min 340")
            assertEquals(PlayerPreferences.MINI_PLAYER_MIN_HEIGHT, prefs.getMiniPlayerHeight(), "Height should clamp to min 68")

            // Test above max limits
            prefs.setMiniPlayerBounds(x = 10, y = 20, width = 5000, height = 3000)
            assertEquals(PlayerPreferences.MINI_PLAYER_MAX_WIDTH, prefs.getMiniPlayerWidth(), "Width should clamp to max 1000")
            assertEquals(PlayerPreferences.MINI_PLAYER_MAX_HEIGHT, prefs.getMiniPlayerHeight(), "Height should clamp to max 120")
        } finally {
            prefs.setMiniPlayerBounds(origX, origY, origW, origH)
        }
    }

    @Test
    fun chunking_adapts_to_wide_and_narrow_scale() {
        val words = listOf(
            LyricWord("Never ", 0L, 500L),
            LyricWord("gonna ", 500L, 1000L),
            LyricWord("give ", 1000L, 1500L),
            LyricWord("you ", 1500L, 2000L),
            LyricWord("up, ", 2000L, 2500L),
            LyricWord("never ", 2500L, 3000L),
            LyricWord("gonna ", 3000L, 3500L),
            LyricWord("let ", 3500L, 4000L),
            LyricWord("you ", 4000L, 4500L),
            LyricWord("down", 4500L, 5000L),
        )
        val text = words.joinToString("") { it.text }

        // Scaled wide (e.g. 70 chars max)
        val wideChunks = splitIntoChunks(text, words, maxChunkChars = 70)
        assertEquals(1, wideChunks.size, "Wide scaled mini player should fit full line without split")

        // Scaled narrow (e.g. 22 chars max)
        val narrowChunks = splitIntoChunks(text, words, maxChunkChars = 22)
        assertTrue(narrowChunks.size >= 2, "Narrow scaled mini player should split into multiple chunks")
    }

    @Test
    fun mini_player_display_settings_persistence() {
        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        val origCover = prefs.getMiniPlayerShowCover()
        val origPlayback = prefs.getMiniPlayerShowPlaybackControls()
        val origAdditional = prefs.getMiniPlayerShowAdditionalControls()
        val origOnHover = prefs.getMiniPlayerControlsOnHover()
        val origHoverEffect = prefs.getMiniPlayerHoverEffect()
        val origHoverIllumination = prefs.getMiniPlayerHoverIllumination()
        val origProgress = prefs.getMiniPlayerShowProgress()

        try {
            // Defaults should be true
            assertTrue(prefs.getMiniPlayerShowCover(), "Cover default should be true")
            assertTrue(prefs.getMiniPlayerShowPlaybackControls(), "Playback controls default should be true")
            assertTrue(prefs.getMiniPlayerShowAdditionalControls(), "Additional controls default should be true")
            assertTrue(prefs.getMiniPlayerControlsOnHover(), "Controls on hover default should be true")
            assertTrue(prefs.getMiniPlayerHoverEffect(), "Hover effect default should be true")
            assertTrue(prefs.getMiniPlayerHoverIllumination(), "Hover illumination default should be true")
            assertTrue(prefs.getMiniPlayerShowProgress(), "Progress bar default should be true")

            // Test toggling false
            prefs.setMiniPlayerShowCover(false)
            prefs.setMiniPlayerShowPlaybackControls(false)
            prefs.setMiniPlayerShowAdditionalControls(false)
            prefs.setMiniPlayerControlsOnHover(false)
            prefs.setMiniPlayerHoverEffect(false)
            prefs.setMiniPlayerHoverIllumination(false)
            prefs.setMiniPlayerShowProgress(false)

            assertFalse(prefs.getMiniPlayerShowCover())
            assertFalse(prefs.getMiniPlayerShowPlaybackControls())
            assertFalse(prefs.getMiniPlayerShowAdditionalControls())
            assertFalse(prefs.getMiniPlayerControlsOnHover())
            assertFalse(prefs.getMiniPlayerHoverEffect())
            assertFalse(prefs.getMiniPlayerHoverIllumination())
            assertFalse(prefs.getMiniPlayerShowProgress())

            // Test toggling back to true
            prefs.setMiniPlayerShowCover(true)
            prefs.setMiniPlayerShowPlaybackControls(true)
            prefs.setMiniPlayerShowAdditionalControls(true)
            prefs.setMiniPlayerControlsOnHover(true)
            prefs.setMiniPlayerHoverEffect(true)
            prefs.setMiniPlayerHoverIllumination(true)
            prefs.setMiniPlayerShowProgress(true)

            assertTrue(prefs.getMiniPlayerShowCover())
            assertTrue(prefs.getMiniPlayerShowPlaybackControls())
            assertTrue(prefs.getMiniPlayerShowAdditionalControls())
            assertTrue(prefs.getMiniPlayerControlsOnHover())
            assertTrue(prefs.getMiniPlayerHoverEffect())
            assertTrue(prefs.getMiniPlayerHoverIllumination())
            assertTrue(prefs.getMiniPlayerShowProgress())
        } finally {
            prefs.setMiniPlayerShowCover(origCover)
            prefs.setMiniPlayerShowPlaybackControls(origPlayback)
            prefs.setMiniPlayerShowAdditionalControls(origAdditional)
            prefs.setMiniPlayerControlsOnHover(origOnHover)
            prefs.setMiniPlayerHoverEffect(origHoverEffect)
            prefs.setMiniPlayerHoverIllumination(origHoverIllumination)
            prefs.setMiniPlayerShowProgress(origProgress)
        }
    }

    @Test
    fun mini_player_style_and_transparent_bg_preferences_persistence() {
        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        val origStyle = prefs.getMiniPlayerStyle()
        val origTransparentBg = prefs.getMiniPlayerTransparentBg()

        try {
            // Default check
            assertEquals(MiniPlayerStyle.STANDARD, MiniPlayerStyle.valueOf(PlayerPreferences.DEFAULT_MINI_PLAYER_STYLE))

            // Toggle to elongated
            prefs.setMiniPlayerStyle(MiniPlayerStyle.ELONGATED)
            assertEquals(MiniPlayerStyle.ELONGATED, prefs.getMiniPlayerStyle())

            // Toggle to standard
            prefs.setMiniPlayerStyle(MiniPlayerStyle.STANDARD)
            assertEquals(MiniPlayerStyle.STANDARD, prefs.getMiniPlayerStyle())

            // Transparent background
            prefs.setMiniPlayerTransparentBg(true)
            assertTrue(prefs.getMiniPlayerTransparentBg())
            prefs.setMiniPlayerTransparentBg(false)
            assertFalse(prefs.getMiniPlayerTransparentBg())
        } finally {
            prefs.setMiniPlayerStyle(origStyle)
            prefs.setMiniPlayerTransparentBg(origTransparentBg)
        }
    }

    @Test
    fun mini_player_elongated_bounds_persistence_and_clamping() {
        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        val origX = prefs.getMiniPlayerElongatedX() ?: 100
        val origY = prefs.getMiniPlayerElongatedY() ?: 100
        val origW = prefs.getMiniPlayerElongatedWidth()
        val origH = prefs.getMiniPlayerElongatedHeight()

        try {
            // Valid elongated bounds
            prefs.setMiniPlayerElongatedBounds(x = 150, y = 200, width = 650, height = 40)
            assertEquals(150, prefs.getMiniPlayerElongatedX())
            assertEquals(200, prefs.getMiniPlayerElongatedY())
            assertEquals(650, prefs.getMiniPlayerElongatedWidth())
            assertEquals(40, prefs.getMiniPlayerElongatedHeight())

            // Clamp below minimums (240 min width, 30 min height)
            prefs.setMiniPlayerElongatedBounds(x = 50, y = 60, width = 100, height = 15)
            assertEquals(PlayerPreferences.MINI_PLAYER_ELONGATED_MIN_WIDTH, prefs.getMiniPlayerElongatedWidth())
            assertEquals(PlayerPreferences.MINI_PLAYER_ELONGATED_MIN_HEIGHT, prefs.getMiniPlayerElongatedHeight())

            // Clamp above maximums (1400 max width, 56 max height)
            prefs.setMiniPlayerElongatedBounds(x = 50, y = 60, width = 2500, height = 120)
            assertEquals(PlayerPreferences.MINI_PLAYER_ELONGATED_MAX_WIDTH, prefs.getMiniPlayerElongatedWidth())
            assertEquals(PlayerPreferences.MINI_PLAYER_ELONGATED_MAX_HEIGHT, prefs.getMiniPlayerElongatedHeight())
        } finally {
            prefs.setMiniPlayerElongatedBounds(origX, origY, origW, origH)
        }
    }
}


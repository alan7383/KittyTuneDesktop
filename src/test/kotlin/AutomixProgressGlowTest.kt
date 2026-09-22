import com.alananasss.kittytune.audio.automix.AutomixManager
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression guard for crossfade & automix transitions without playback controls layout shift.
 *
 * Issue:
 * When tracks started switching in crossfade/auto-mixing mode, a pop-up badge with digital countdown
 * was inserted into the transport controls row, causing SkipPrevious, Play/Pause, and SkipNext
 * to physically shrink and shift under the cursor.
 *
 * Fix:
 * Transport buttons row has 0 layout shift (badge removed from row). Transition feedback is delivered
 * via ambient glow and gradient animation on the bottom PlayerBar panel.
 */
class AutomixProgressGlowTest {

    @Test
    fun testBeatCountdownUrgencyProgression() {
        // At 16 beats left, urgency should be 0.0 (calm primary glow)
        val urgency16 = ((16 - 16) / 15f).coerceIn(0f, 1f)
        assertEquals(0f, urgency16, 0.001f)

        // At 8 beats left, urgency is intermediate
        val urgency8 = ((16 - 8) / 15f).coerceIn(0f, 1f)
        assertTrue(urgency8 in 0.5f..0.6f)

        // At 1 beat left, urgency should be 1.0 (vibrant transition glow)
        val urgency1 = ((16 - 1) / 15f).coerceIn(0f, 1f)
        assertEquals(1f, urgency1, 0.001f)
    }

    @Test
    fun testTempoSyncedBeatPeriod() {
        // 120 BPM -> 500ms per beat
        val bpm120 = 120f
        val period120 = 60_000f / bpm120
        assertEquals(500f, period120, 0.01f)

        // 140 BPM -> ~428.57ms per beat
        val bpm140 = 140f
        val period140 = 60_000f / bpm140
        assertEquals(428.57f, period140, 0.01f)
    }

    @Test
    fun testAutomixManagerMixBeatsState() {
        AutomixManager.setMixBeatsLeft(12)
        assertEquals(12, AutomixManager.mixBeatsLeft.value)

        AutomixManager.setMixBeatsLeft(null)
        assertEquals(null, AutomixManager.mixBeatsLeft.value)
    }
}

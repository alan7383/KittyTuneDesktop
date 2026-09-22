import com.alananasss.kittytune.audio.automix.AutomixManager
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests the automix subtitle transition ("Mix vers [titre suivant]").
 *
 * Replaces the disruptive badge and blurry glow with a clean, native metadata transition
 * under the current track title.
 */
class AutomixSubtitleTransitionTest {

    @Test
    fun testMixStateActivatesOnCountdown() {
        AutomixManager.setMixBeatsLeft(8)
        assertEquals(8, AutomixManager.mixBeatsLeft.value)

        AutomixManager.setMixBeatsLeft(null)
        assertNull(AutomixManager.mixBeatsLeft.value)
    }

    @Test
    fun testAutomixActiveState() {
        AutomixManager.setIsAutomixing(true)
        assertEquals(true, AutomixManager.isAutomixing.value)

        AutomixManager.setIsAutomixing(false)
        assertEquals(false, AutomixManager.isAutomixing.value)
    }
}

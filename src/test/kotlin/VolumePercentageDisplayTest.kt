import com.alananasss.kittytune.ui.main.volumePercentLabel
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The level shown beside the volume control.
 *
 * Replaces checks that searched PlayerBar.kt's source text for particular constant names, which
 * failed on any refactor while proving nothing about what the user sees.
 */
class VolumePercentageDisplayTest {

    @Test
    fun `shows whole percentages`() {
        assertEquals("0%", volumePercentLabel(0f))
        assertEquals("50%", volumePercentLabel(0.5f))
        assertEquals("100%", volumePercentLabel(1f))
    }

    @Test
    fun `rounds to the nearest percent`() {
        assertEquals("35%", volumePercentLabel(0.346f))
        assertEquals("34%", volumePercentLabel(0.344f))
    }

    @Test
    fun `shows 0 percent only when silent and at least 1 percent when audible`() {
        assertEquals("0%", volumePercentLabel(0f))
        assertEquals("0%", volumePercentLabel(0.0005f))
        assertEquals("0%", volumePercentLabel(0.001f))
        assertEquals("1%", volumePercentLabel(0.002f))
        assertEquals("1%", volumePercentLabel(0.004f))
        assertEquals("1%", volumePercentLabel(0.01f))
    }

    @Test
    fun `never displays minus zero percent`() {
        assertEquals("0%", volumePercentLabel(-0.0f))
        assertEquals("0%", volumePercentLabel(-0.001f))
    }
}

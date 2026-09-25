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
}

import java.io.File
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Validates volume percentage display on both horizontal and vertical volume control sliders.
 */
class VolumePercentageDisplayTest {

    @Test
    fun `PlayerBar has volume percentage in horizontal volume slider`() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/main/PlayerBar.kt")
        assertTrue(file.exists(), "PlayerBar.kt must exist")
        val content = file.readText()

        // Check horizontal slider row has volume percentage text
        assertTrue(content.contains("VOLUME_TEXT_WIDTH"), "Must have VOLUME_TEXT_WIDTH constant")
        assertTrue(content.contains("VOLUME_TEXT_GAP"), "Must have VOLUME_TEXT_GAP constant")
        assertTrue(content.contains("(volume * 100).roundToInt()"), "Must compute rounded percentage for horizontal slider")
    }

    @Test
    fun `VolumeHoverControl has volume percentage in vertical volume slider`() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/main/PlayerBar.kt")
        assertTrue(file.exists(), "PlayerBar.kt must exist")
        val content = file.readText()

        // Check vertical slider popup has percentage based on state.value with roundToInt
        assertTrue(content.contains("(state.value * 100).roundToInt()"), "Vertical slider popup must display live percentage from state.value")
    }

    @Test
    fun `FullPlayer has live volume percentage display`() {
        val file = File("src/main/kotlin/com/alananasss/kittytune/ui/player/FullPlayer.kt")
        assertTrue(file.exists(), "FullPlayer.kt must exist")
        val content = file.readText()

        assertTrue(content.contains("(activeFraction * 100).roundToInt()"), "FullPlayer must compute rounded percentage from activeFraction")
    }
}

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.ui.player.slider.PlayerSlider
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerSliderTest {

    @Test
    fun `rapid clicks use the clicked position and finish once in each style`() {
        for (style in PlayerSliderStyle.entries) {
            var duration by mutableFloatStateOf(1f)
            var position by mutableFloatStateOf(0f)
            val committed = mutableListOf<Float>()
            val scene = ImageComposeScene(width = 400, height = 48, density = Density(1f)) {
                PlayerSlider(
                    value = position,
                    onValueChange = { position = it },
                    onValueChangeFinished = { committed += position },
                    valueRange = 0f..duration,
                    sliderStyle = style,
                    isPlaying = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            try {
                scene.render(0L).close()
                duration = 200_000f
                scene.render(16_000_000L).close()

                fun click(x: Float) {
                    val point = Offset(x, 24f)
                    scene.sendPointerEvent(PointerEventType.Press, point)
                    scene.sendPointerEvent(PointerEventType.Release, point)
                }

                click(80f)
                scene.render(32_000_000L).close()
                // Return to the last drawn value before there is another frame.
                click(320f)
                click(80f)
                click(200f)
                assertEquals(4, committed.size, "$style must finish each click once")
                assertTrue(committed[0] in 35_000f..45_000f, "$style used the wrong duration: $committed")
                assertTrue(committed[1] in 155_000f..165_000f, "$style missed the second click: $committed")
                assertEquals(committed[0], committed[2], "$style ignored the click back to the drawn value")
                assertEquals(100_000f, committed[3], 1f, "$style missed the center")

                click(1f)
                click(399f)
                assertTrue(committed[4] in 0f..1_000f, "$style missed the start of the track")
                assertTrue(committed[5] in 199_000f..200_000f, "$style missed the end of the track")

                scene.render(48_000_000L).close()
                scene.sendPointerEvent(PointerEventType.Press, Offset(399f, 24f))
                scene.sendPointerEvent(PointerEventType.Move, Offset(250f, 24f))
                scene.sendPointerEvent(PointerEventType.Move, Offset(120f, 24f))
                assertEquals(6, committed.size, "$style committed before the drag ended")
                assertTrue(position in 55_000f..65_000f, "$style drag did not follow the pointer: $position")
                scene.sendPointerEvent(PointerEventType.Release, Offset(120f, 24f))
                assertEquals(7, committed.size, "$style must finish the drag once")
                assertEquals(position, committed.last())
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun testPlayerSliderStyleEnum() {
        val styles = PlayerSliderStyle.entries
        assertEquals(4, styles.size)
        assertTrue(styles.contains(PlayerSliderStyle.BAR))
        assertTrue(styles.contains(PlayerSliderStyle.WAVY))
        assertTrue(styles.contains(PlayerSliderStyle.SLIM))
        assertTrue(styles.contains(PlayerSliderStyle.SQUIGGLY))
    }

    @Test
    fun testPlayerSliderStylePreferences() {
        val prefs = PlayerPreferences()
        val originalStyle = prefs.getPlayerSliderStyle()

        try {
            prefs.setPlayerSliderStyle(PlayerSliderStyle.WAVY)
            assertEquals(PlayerSliderStyle.WAVY, prefs.getPlayerSliderStyle())

            prefs.setPlayerSliderStyle(PlayerSliderStyle.SLIM)
            assertEquals(PlayerSliderStyle.SLIM, prefs.getPlayerSliderStyle())

            prefs.setPlayerSliderStyle(PlayerSliderStyle.SQUIGGLY)
            assertEquals(PlayerSliderStyle.SQUIGGLY, prefs.getPlayerSliderStyle())

            prefs.setPlayerSliderStyle(PlayerSliderStyle.BAR)
            assertEquals(PlayerSliderStyle.BAR, prefs.getPlayerSliderStyle())
        } finally {
            prefs.setPlayerSliderStyle(originalStyle)
        }
    }

    @Test
    fun testPlayerSliderStyleFallback() {
        val prefs = PlayerPreferences()
        val originalStyle = prefs.getPlayerSliderStyle()

        try {
            Prefs.putString(PlayerPreferences.KEY_PLAYER_SLIDER_STYLE, "UNKNOWN_INVALID_STYLE")
            assertEquals(PlayerSliderStyle.WAVY, prefs.getPlayerSliderStyle())
        } finally {
            prefs.setPlayerSliderStyle(originalStyle)
        }
    }
}

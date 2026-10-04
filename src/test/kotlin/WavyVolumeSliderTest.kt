import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.ui.main.VolumeTrackSpec
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WavyVolumeSliderTest {

    @Test
    fun `wavy volume track spec defines circle thumb and wave amplitude`() {
        val spec = VolumeTrackSpec.of(PlayerSliderStyle.WAVY)
        assertEquals(7.dp, spec.thumbRadius, "Wavy volume slider must have circle thumb radius")
        assertEquals(20.dp, spec.thumbLength, "Wavy volume slider must define morph thumb height")
        assertEquals(2.5.dp, spec.amplitude, "Wavy volume slider must have wave amplitude")
        assertTrue(spec.amplitude > 0.dp)
    }

    @Test
    fun `other styles maintain their expected specs`() {
        val barSpec = VolumeTrackSpec.of(PlayerSliderStyle.BAR)
        assertEquals(0.dp, barSpec.amplitude)
        assertEquals(0.dp, barSpec.thumbRadius)
        assertTrue(barSpec.gapAroundThumb)

        val slimSpec = VolumeTrackSpec.of(PlayerSliderStyle.SLIM)
        assertEquals(0.dp, slimSpec.amplitude)
        assertEquals(0.dp, slimSpec.thumbRadius)
        assertEquals(0.dp, slimSpec.thumbLength)
    }

    @Test
    fun `full player seekBar uses PlayerSlider with styled slider`() {
        val fullPlayerSource = java.io.File("src/main/kotlin/com/alananasss/kittytune/ui/player/FullPlayer.kt").readText()
        assertTrue(
            fullPlayerSource.contains("PlayerSlider("),
            "FullPlayerSeekBar must use PlayerSlider instead of hardcoded hairline bar",
        )
        assertTrue(
            fullPlayerSource.contains("sliderStyle = sliderStyle"),
            "FullPlayerSeekBar must bind user's chosen sliderStyle",
        )
        assertTrue(
            fullPlayerSource.contains("rememberPlayerSliderStyle()"),
            "FullPlayerSeekBar must read reactive player slider style preference",
        )
    }

    @Test
    fun `volume icon and labels reflect mute down and up states`() {
        assertEquals(Icons.AutoMirrored.Filled.VolumeOff, com.alananasss.kittytune.ui.main.volumeIcon(0f))
        assertEquals(Icons.AutoMirrored.Filled.VolumeOff, com.alananasss.kittytune.ui.main.volumeIcon(0.001f))
        assertEquals(Icons.AutoMirrored.Filled.VolumeDown, com.alananasss.kittytune.ui.main.volumeIcon(0.25f))
        assertEquals(Icons.AutoMirrored.Filled.VolumeUp, com.alananasss.kittytune.ui.main.volumeIcon(0.5f))
        assertEquals(Icons.AutoMirrored.Filled.VolumeUp, com.alananasss.kittytune.ui.main.volumeIcon(1.0f))

        assertEquals("0%", com.alananasss.kittytune.ui.main.volumePercentLabel(0f))
        assertEquals("50%", com.alananasss.kittytune.ui.main.volumePercentLabel(0.5f))
    }

    @Test
    fun `volume hover control speaker icon does not use primary accent tint`() {
        val volumeControlSource = java.io.File("src/main/kotlin/com/alananasss/kittytune/ui/main/VolumeControl.kt").readText()
        assertTrue(
            !volumeControlSource.contains("tint = MaterialTheme.colorScheme.primary"),
            "VolumeControl must not tint the speaker or mute icon with primary accent color",
        )
    }

    @Test
    fun `volume controls use IconButton with expressive shapes`() {
        val volumeControlSource = java.io.File("src/main/kotlin/com/alananasss/kittytune/ui/main/VolumeControl.kt").readText()
        assertTrue(
            volumeControlSource.contains("shapes: IconButtonShapes = IconButtonDefaults.shapes()"),
            "VolumeControl must support expressive shapes defaulting to IconButtonDefaults.shapes()",
        )
        val playerBarSource = java.io.File("src/main/kotlin/com/alananasss/kittytune/ui/main/PlayerBar.kt").readText()
        assertTrue(
            playerBarSource.contains("shapes = iconShapes"),
            "PlayerBar must pass iconShapes to VolumeControl",
        )
    }

    @Test
    fun `wavy slider connects track to thumb without idle gap`() {
        val wavyExpressiveSource = java.io.File("src/main/kotlin/com/alananasss/kittytune/ui/player/slider/WavySliderExpressive.kt").readText()
        assertTrue(
            wavyExpressiveSource.contains("val idleGap = 6.dp"),
            "WavySliderExpressive must maintain idleGap at 6.dp so track connects to 8.dp thumb",
        )
        val volumeSource = java.io.File("src/main/kotlin/com/alananasss/kittytune/ui/main/VolumeControl.kt").readText()
        assertTrue(
            volumeSource.contains("val idleGap = 5.dp.toPx()"),
            "VolumeControl must maintain idleGap at 5.dp so track connects to 7.dp thumb",
        )
    }
}


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.wave.WaveMode
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.main.MyWaveCardContent
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test

/** Renders the My Wave card idle and playing, in Russian (the longest labels), to build/previews. */
class MyWaveCardRenderTest {
    @Test
    fun rendersIdleAndPlaying() {
        val previous = Strings.appLanguage
        Strings.appLanguage = "ru"
        val track = Track(1, "Midnight City", null, 240_000, User(1, "M83", null))
        try {
            for (dark in listOf(true, false)) {
                val scene = ImageComposeScene(width = 1000, height = 1000) {
                    SoundTuneTheme(
                        themeMode = if (dark) AppThemeMode.DARK else AppThemeMode.LIGHT, dynamicColor = false, pureBlack = false,
                        keyColor = 0xFF829ADF.toInt(), colorStyle = "Expressive",
                        typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f),
                    ) {
                        Column(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            MyWaveCardContent(WaveMode.FAVORITES, false, false, false, null, {}, {})
                            MyWaveCardContent(WaveMode.DISCOVER, true, true, false, track, {}, {})
                            androidx.compose.foundation.layout.Box(Modifier.width(560.dp)) { MyWaveCardContent(WaveMode.BALANCED, false, false, false, null, {}, {}) }
                        }
                    }
                }
                try {
                    scene.render(0)
                    val out = File("build/previews/my-wave-${if (dark) "dark" else "light"}.png").apply { parentFile.mkdirs() }
                    scene.render(2_000_000_000L).use { img -> img.encodeToData(EncodedImageFormat.PNG)!!.use { out.writeBytes(it.bytes) } }
                } finally {
                    scene.close()
                }
            }
        } finally {
            Strings.appLanguage = previous
        }
    }
}

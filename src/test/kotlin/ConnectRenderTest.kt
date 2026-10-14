import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.profile.ConnectPanel
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.*
import org.junit.Test

class ConnectRenderTest {
    @Test fun devicePickerRendersWithoutAPrimingPlaybackQueue() {
        val previousLanguage = Strings.appLanguage
        Strings.appLanguage = "ru"
        try {
            val scene = ImageComposeScene(width = 600, height = 700) {
                SoundTuneTheme(themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                    keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                    typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f)) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().padding(24.dp)) { ConnectPanel() }
                    }
                }
            }
            try {
                val output = File("build/previews/connect-devices-ru.png").apply { parentFile.mkdirs() }
                scene.render().use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { output.writeBytes(it.bytes) }
                }
                val bitmap = ImageIO.read(output)
                assertEquals(600, bitmap.width)
                assertEquals(700, bitmap.height)
                val background = bitmap.getRGB(10, 690)
                val drawn = (24 until 675 step 4).sumOf { y ->
                    (24 until 575 step 4).count { x -> bitmap.getRGB(x, y) != background }
                }
                assertTrue("Device choices and labels must be drawn without a playing track", drawn > 500)
            } finally { scene.close() }
        } finally { Strings.appLanguage = previousLanguage }
    }
}

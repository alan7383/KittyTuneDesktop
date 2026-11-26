import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.alananasss.kittytune.core.*
import com.alananasss.kittytune.ui.profile.PerformanceGraphicsPage
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceGraphicsRenderTest {
    @Test fun graphicsPageRendersAtDesktopScalesWithNamedDevices() {
        val previousLanguage = Strings.appLanguage
        val previousPrefs = Prefs.snapshot()
        val intel = GraphicsDevice("intel", "Intel(R) HD Graphics 530")
        val nvidia = GraphicsDevice("nvidia", "NVIDIA GeForce GTX 960M")
        val devices = GraphicsDevices(listOf(intel, nvidia), intel.id, intel.id, nvidia.id)
        try {
            Strings.appLanguage = "en"
            PerformanceSettings.selectGpu(nvidia, devices)
            for (scale in listOf(1f, 1.25f, 1.5f)) {
                val scene = ImageComposeScene(width = (760 * scale).toInt(), height = (820 * scale).toInt(), density = Density(scale)) {
                    MaterialTheme {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            PerformanceGraphicsPage(loadDevices = { devices }, loadGraphics = { GraphicsInUse(intel.name, false) })
                        }
                    }
                }
                try {
                    scene.render().close()
                    Thread.sleep(100)
                    scene.render().use { image ->
                        image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                            assertTrue(data.bytes.size > 1000)
                            File("build/previews/performance-graphics-${(scale * 100).toInt()}.png")
                                .apply { parentFile.mkdirs() }.writeBytes(data.bytes)
                        }
                    }
                } finally { scene.close() }
            }
        } finally { Prefs.restore(previousPrefs); Prefs.flush(); Strings.appLanguage = previousLanguage }
    }
}

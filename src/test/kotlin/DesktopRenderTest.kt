import androidx.compose.ui.ImageComposeScene
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.login.WelcomeScreen
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import com.alananasss.kittytune.ui.theme.withUiScale
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Render the real first-launch screen offscreen, with the same font and theme as Android. */
class DesktopRenderTest {
    @Test
    fun nativeWelcomeScreenRendersWithRussianTextAndMobileTheme() {
        renderWelcome(1280, 800, 1f, "desktop-welcome")
    }

    @Test
    fun qhdAt125PercentRendersInNativePhysicalPixels() {
        renderWelcome(2560, 1440, 1.25f, "desktop-welcome-qhd-125")
    }

    @Test
    fun qhdAt150PercentRendersInNativePhysicalPixels() {
        renderWelcome(2560, 1440, 1.5f, "desktop-welcome-qhd-150")
    }

    private fun renderWelcome(width: Int, height: Int, monitorScale: Float, name: String) {
        val previousLanguage = Strings.appLanguage
        Strings.appLanguage = "ru"
        try {
            val scene = ImageComposeScene(width = width, height = height, density = Density(monitorScale)) {
                CompositionLocalProvider(LocalDensity provides LocalDensity.current.withUiScale(1f)) {
                    SoundTuneTheme(
                        themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                        keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                        typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f)
                    ) {
                        WelcomeScreen(onLoginClick = {}, onGuestClick = {}, isGuestLoading = false)
                    }
                }
            }
            try {
                val output = File("build/previews/$name.png").apply { parentFile.mkdirs() }
                scene.render().use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
                }
                val bitmap = ImageIO.read(output)
                assertEquals(width, bitmap.width)
                assertEquals(height, bitmap.height)
                assertNotEquals("The central welcome card must be visible",
                    bitmap.getRGB(20, height / 2),
                    bitmap.getRGB(width / 2 - (210 * monitorScale).toInt(), height / 2))
            } finally { scene.close() }
        } finally { Strings.appLanguage = previousLanguage }
    }
}

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.pet.PetInAppContent
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import com.alananasss.kittytune.ui.theme.withUiScale
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The cat kept inside the app's window is drawn over the panels, never using the window's edges:
 * this renders it offscreen and checks it is both there and held away from the borders — the
 * whole point of the placement, as opposed to the floating window that can sit over another app.
 */
class PetInAppRenderTest {

    @Test
    fun theCatIsDrawnInsideTheWindowAwayFromItsEdges() {
        val previousLanguage = Strings.appLanguage
        Strings.appLanguage = "en"
        val width = 900
        val height = 560
        try {
            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                CompositionLocalProvider(LocalDensity provides LocalDensity.current.withUiScale(1f)) {
                    SoundTuneTheme(
                        themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                        keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                        typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f)
                    ) {
                        // What the app's panels look like under it.
                        Box(Modifier.fillMaxSize().background(Color(0xFF15100F))) {
                            PetInAppContent(
                                onOpenSettings = {},
                                onPlayPause = {},
                                initialFraction = 1f to 0.72f,
                            )
                        }
                    }
                }
            }
            try {
                val output = File("build/previews/pet-in-window.png").apply { parentFile.mkdirs() }
                scene.render().use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
                }
                val bitmap = ImageIO.read(output)
                assertEquals(width, bitmap.width)
                assertEquals(height, bitmap.height)

                val background = bitmap.getRGB(4, 4)
                // The card is 190x175 dp, held 12 dp from the window's edges, three quarters down.
                val cardLeft = width - 12 - 190
                val cardTop = 12 + ((height - 24 - 175) * 0.72f).toInt()

                assertNotEquals(
                    "The cat's card must be drawn inside the window",
                    background,
                    bitmap.getRGB(cardLeft + 95, cardTop + 80),
                )
                // Its right edge is a hard edge with a 12 dp gutter before the window's border.
                val onCard = bitmap.getRGB(width - 13, cardTop + 30)
                assertNotEquals("The gutter is not the card", onCard, background)
                assertNotEquals(
                    "The cat must stop before the window's border",
                    onCard,
                    bitmap.getRGB(width - 4, cardTop + 30),
                )
            } finally { scene.close() }
        } finally { Strings.appLanguage = previousLanguage }
    }
}

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.ui.pet.PetPlacement
import com.alananasss.kittytune.ui.pet.PetPlacementChoices
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import com.alananasss.kittytune.ui.theme.getDynamicTypography
import com.alananasss.kittytune.ui.theme.withUiScale
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The popup opened from the pet's settings must offer the two homes — the floating window and this
 * window — each with its own explanation, so the choice is made in the dialog rather than guessed
 * from a switch.
 */
class PetPlacementChoicesRenderTest {

    @Test
    fun bothHomesAreDrawnInTheDialog() {
        val previousLanguage = Strings.appLanguage
        Strings.appLanguage = "fr"
        val width = 420
        val height = 200
        try {
            val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
                CompositionLocalProvider(LocalDensity provides LocalDensity.current.withUiScale(1f)) {
                    SoundTuneTheme(
                        themeMode = AppThemeMode.DARK, dynamicColor = false, pureBlack = false,
                        keyColor = 0xFFFF7A1A.toInt(), colorStyle = "Expressive",
                        typography = getDynamicTypography(true, 400, 100f, 0f, 0f, 0f, 14f)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            PetPlacementChoices(selected = PetPlacement.DESKTOP, onSelect = {})
                        }
                    }
                }
            }
            try {
                val output = File("build/previews/pet-placement-dialog.png").apply { parentFile.mkdirs() }
                scene.render().use { image ->
                    image.encodeToData(EncodedImageFormat.PNG)!!.use { data -> output.writeBytes(data.bytes) }
                }
                val bitmap = ImageIO.read(output)
                assertEquals(width, bitmap.width)
                assertEquals(height, bitmap.height)

                // The surface, sampled in a corner no row reaches.
                val surface = bitmap.getRGB(width - 14, 14)
                // The two rows sit under each other: each band must carry its own radio, icon and text.
                val firstRow = ink(bitmap, surface, 20, 96, 16, 104)
                val secondRow = ink(bitmap, surface, 20, 96, 108, 196)
                assertTrue("The floating window option must be drawn (found $firstRow ink pixels)", firstRow > 200)
                assertTrue("The in-the-window option must be drawn (found $secondRow ink pixels)", secondRow > 200)
            } finally { scene.close() }
        } finally { Strings.appLanguage = previousLanguage }
    }

    /** How many pixels of a band differ from the surface: text, icons and radios. */
    private fun ink(bitmap: java.awt.image.BufferedImage, surface: Int, x0: Int, x1: Int, y0: Int, y1: Int): Int {
        val sr = surface shr 16 and 0xFF
        val sg = surface shr 8 and 0xFF
        val sb = surface and 0xFF
        var count = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val c = bitmap.getRGB(x, y)
                val d = abs((c shr 16 and 0xFF) - sr) + abs((c shr 8 and 0xFF) - sg) + abs((c and 0xFF) - sb)
                if (d > 60) count++
            }
        }
        return count
    }
}

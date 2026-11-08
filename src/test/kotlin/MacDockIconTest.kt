import com.alananasss.kittytune.core.AppIconRuntime
import com.alananasss.kittytune.core.AppIconVariants
import java.awt.Color
import java.awt.Image
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertTrue

/** Exercises the real Cocoa Dock API; offscreen rendering cannot catch a missing Taskbar update. */
class MacDockIconTest {
    @Test
    fun applyingDefaultIconReplacesGenericDockImage() {
        assumeTrue(System.getProperty("os.name").lowercase().contains("mac"))
        assumeTrue(Taskbar.isTaskbarSupported())
        val taskbar = Taskbar.getTaskbar()
        assumeTrue(taskbar.isSupported(Taskbar.Feature.ICON_IMAGE))
        SwingUtilities.invokeAndWait {
            val previous = taskbar.iconImage
            try {
                val placeholder = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
                placeholder.createGraphics().apply {
                    color = Color.MAGENTA
                    fillRect(0, 0, 128, 128)
                    dispose()
                }
                taskbar.iconImage = placeholder
                AppIconRuntime.apply(AppIconVariants.DEFAULT_KEY)
                val expected = ImageIO.read(javaClass.getResource("/icons/kittytune.png"))
                val actual = thumbnail(taskbar.iconImage)
                val source = thumbnail(expected)
                // Inspect the interior to allow the OS to decorate the icon's outer edge.
                var difference = 0L
                var samples = 0
                for (y in 32 until 96) for (x in 32 until 96) {
                    val a = actual.getRGB(x, y)
                    val b = source.getRGB(x, y)
                    for (shift in listOf(0, 8, 16)) {
                        difference += abs(((a ushr shift) and 255) - ((b ushr shift) and 255))
                        samples++
                    }
                }
                assertTrue(difference.toDouble() / samples < 20, "Dock must display the KittyTune artwork")
            } finally { taskbar.iconImage = previous }
        }
    }

    private fun thumbnail(image: Image): BufferedImage =
        BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().apply { drawImage(image, 0, 0, 128, 128, null); dispose() }
        }
}

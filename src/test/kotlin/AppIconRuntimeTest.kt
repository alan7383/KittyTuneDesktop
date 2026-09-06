import com.alananasss.kittytune.core.AppIconRuntime
import com.alananasss.kittytune.core.AppIconVariants
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppIconRuntimeTest {

    @Test
    fun fillTransparentCorners_makesCornersFullyOpaqueWithMatchingColor() {
        // Create a 64x64 test image with an orange squircle (rounded rectangle) and transparent corners
        val size = 64
        val source = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = source.createGraphics()
        g.color = Color(255, 85, 0, 255) // SoundCloud / OG orange
        g.fillRoundRect(8, 8, size - 16, size - 16, 16, 16)
        g.dispose()

        // Before fill: corners must be transparent
        val cornerAlphaBefore = (source.getRGB(0, 0) ushr 24) and 0xff
        assertEquals(0, cornerAlphaBefore, "Corner should be transparent initially")

        // Fill corners
        val filled = AppIconRuntime.fillTransparentCorners(source)

        // After fill: all 4 corners must be fully opaque
        val corners = listOf(
            filled.getRGB(0, 0),
            filled.getRGB(size - 1, 0),
            filled.getRGB(0, size - 1),
            filled.getRGB(size - 1, size - 1)
        )
        for (c in corners) {
            val alpha = (c ushr 24) and 0xff
            val red = (c ushr 16) and 0xff
            val green = (c ushr 8) and 0xff
            val blue = c and 0xff
            assertEquals(255, alpha, "Corner alpha must be 255 (fully opaque)")
            assertEquals(255, red, "Corner red should match orange edge")
            assertEquals(85, green, "Corner green should match orange edge")
            assertEquals(0, blue, "Corner blue should match orange edge")
        }
    }

    @Test
    fun fillTransparentCorners_leavesAlreadyOpaqueImageUntouched() {
        val size = 32
        val source = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = source.createGraphics()
        g.color = Color.BLACK
        g.fillRect(0, 0, size, size)
        g.dispose()

        val result = AppIconRuntime.fillTransparentCorners(source)
        // Should return the exact same instance because all corners are already opaque
        assertTrue(result === source, "Should not allocate a new image when already opaque")
    }

    @Test
    fun loadTrayPainter_successfullyLoadsDefaultAndVariants() {
        // Default icon
        val defaultPainter = AppIconRuntime.loadTrayPainter(AppIconVariants.DEFAULT_KEY)
        assertNotNull(defaultPainter, "Default tray icon should load successfully")

        // OG variant (orange squircle)
        val ogPainter = AppIconRuntime.loadTrayPainter("og")
        assertNotNull(ogPainter, "OG variant tray icon should load successfully")

        // Black variant
        val blackPainter = AppIconRuntime.loadTrayPainter("black")
        assertNotNull(blackPainter, "Black variant tray icon should load successfully")
    }
}

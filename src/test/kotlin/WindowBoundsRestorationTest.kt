import com.alananasss.kittytune.ScreenMetricsDp
import com.alananasss.kittytune.clampFloatingBounds
import com.alananasss.kittytune.isFullOrMaximizedDimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Rectangle

class WindowBoundsRestorationTest {

    @Test
    fun testFloatingSizeNotDetectedAsFullScreenOnStandardDisplay() {
        // 1080p at 100% scale: screen 1920x1080, usable 1920x1040 (40px taskbar)
        val metrics = ScreenMetricsDp(
            screenWidthDp = 1920,
            screenHeightDp = 1080,
            usableBoundsDp = Rectangle(0, 0, 1920, 1040),
            scaleX = 1.0f,
            scaleY = 1.0f
        )

        // Normal floating window: 1200x800
        assertFalse("1200x800 should not be full or maximized", isFullOrMaximizedDimension(1200, 800, metrics))
        assertFalse("1000x700 should not be full or maximized", isFullOrMaximizedDimension(1000, 700, metrics))

        // Full screen window: 1920x1080
        assertTrue("1920x1080 is full screen", isFullOrMaximizedDimension(1920, 1080, metrics))

        // Maximized window: 1920x1040
        assertTrue("1920x1040 is maximized", isFullOrMaximizedDimension(1920, 1040, metrics))

        // Near-maximized with border margin: 1910x1030
        assertTrue("1910x1030 is within 24dp of maximized", isFullOrMaximizedDimension(1910, 1030, metrics))
    }

    @Test
    fun testFloatingSizeNotDetectedAsFullScreenOnHiDpi150Display() {
        // 1080p at 150% scale:
        // Physical pixels: 1920x1080, usable 1920x1038
        // Dp: 1280x720, usable 1280x692
        val metrics = ScreenMetricsDp(
            screenWidthDp = 1280,
            screenHeightDp = 720,
            usableBoundsDp = Rectangle(0, 0, 1280, 692),
            scaleX = 1.5f,
            scaleY = 1.5f
        )

        // Normal floating window: 960x600 Dp
        assertFalse("960x600 should not be full or maximized", isFullOrMaximizedDimension(960, 600, metrics))

        // Full screen in Dp: 1280x720
        assertTrue("1280x720 is full screen in Dp", isFullOrMaximizedDimension(1280, 720, metrics))

        // Maximized in Dp: 1280x692
        assertTrue("1280x692 is maximized in Dp", isFullOrMaximizedDimension(1280, 692, metrics))
    }

    @Test
    fun testFloatingSizeNotDetectedAsFullScreenOn4K200Display() {
        // 4K at 200% scale:
        // Physical pixels: 3840x2160, usable 3840x2080
        // Dp: 1920x1080, usable 1920x1040
        val metrics = ScreenMetricsDp(
            screenWidthDp = 1920,
            screenHeightDp = 1080,
            usableBoundsDp = Rectangle(0, 0, 1920, 1040),
            scaleX = 2.0f,
            scaleY = 2.0f
        )

        // Normal floating window: 1440x900 Dp
        assertFalse("1440x900 should not be full or maximized on 4K", isFullOrMaximizedDimension(1440, 900, metrics))

        // Full screen in Dp: 1920x1080
        assertTrue("1920x1080 is full screen on 4K Dp", isFullOrMaximizedDimension(1920, 1080, metrics))

        // Maximized in Dp: 1920x1040
        assertTrue("1920x1040 is maximized on 4K Dp", isFullOrMaximizedDimension(1920, 1040, metrics))
    }

    @Test
    fun testClampingPreservesExactFloatingBoundsWhenFitting() {
        val usable = Rectangle(0, 40, 1920, 1040)
        val clamped = clampFloatingBounds(1200, 800, 300, 150, usable)

        assertEquals("Width must be preserved", 1200, clamped.width)
        assertEquals("Height must be preserved", 800, clamped.height)
        assertEquals("X must be preserved", 300, clamped.x)
        assertEquals("Y must be preserved", 150, clamped.y)
    }

    @Test
    fun testClampingKeepsWindowOnScreenWhenPositionOutOfBounds() {
        val usable = Rectangle(0, 40, 1920, 1040)

        // Window placed too far right: x = 1800 with width = 1200 (overflows past 1920)
        val clampedRight = clampFloatingBounds(1200, 800, 1800, 100, usable)
        assertEquals("Window x must be clamped within right edge", 1920 - 1200, clampedRight.x)
        assertEquals("Window width must be preserved", 1200, clampedRight.width)

        // Window placed too far down: y = 900 with height = 800 (overflows past 1080)
        val clampedBottom = clampFloatingBounds(1200, 800, 200, 900, usable)
        assertEquals("Window y must be clamped within bottom edge", 40 + 1040 - 800, clampedBottom.y)
        assertEquals("Window height must be preserved", 800, clampedBottom.height)
    }
}

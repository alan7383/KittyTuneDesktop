import androidx.compose.ui.window.WindowPlacement
import com.alananasss.kittytune.data.theme.WindowsFullScreen
import org.junit.Assert.assertFalse
import org.junit.Test

class WindowsFullScreenTest {

    @Test
    fun testNullWindowHandling() {
        assertFalse("Entering fullscreen with null window should return false safely", WindowsFullScreen.enter(null))
        assertFalse("Exiting fullscreen with null window should return false safely", WindowsFullScreen.exit(null, WindowPlacement.Floating, null))
        assertFalse("isFullScreen should initially be false", WindowsFullScreen.isFullScreen)
    }

    @Test
    fun testNonDisplayableWindowHandling() {
        val dummyFrame = java.awt.Frame("Dummy")
        assertFalse("Non-displayable window should return null HWND", WindowsFullScreen.handleOf(dummyFrame) != null)
        assertFalse("Non-displayable window should return false on enter", WindowsFullScreen.enter(dummyFrame))
        assertFalse("Non-displayable window should return false on exit", WindowsFullScreen.exit(dummyFrame, WindowPlacement.Floating, null))
        dummyFrame.dispose()
    }

    @Test
    fun testClampFloatingBoundsMultiMonitor() {
        // Monitor 1: Primary [0, 0, 1920, 1080]
        val mon1Usable = java.awt.Rectangle(0, 40, 1920, 1040)
        val clamped1 = com.alananasss.kittytune.clampFloatingBounds(1200, 800, 200, 100, mon1Usable)
        org.junit.Assert.assertEquals(200, clamped1.x)
        org.junit.Assert.assertEquals(100, clamped1.y)
        org.junit.Assert.assertEquals(1200, clamped1.width)
        org.junit.Assert.assertEquals(800, clamped1.height)

        // Monitor 2: Right side [1920, 0, 1920, 1080]
        val mon2Usable = java.awt.Rectangle(1920, 40, 1920, 1040)
        val clamped2 = com.alananasss.kittytune.clampFloatingBounds(1200, 800, 2100, 150, mon2Usable)
        org.junit.Assert.assertEquals(2100, clamped2.x)
        org.junit.Assert.assertEquals(150, clamped2.y)
        org.junit.Assert.assertEquals(1200, clamped2.width)
        org.junit.Assert.assertEquals(800, clamped2.height)

        // Monitor 3: Left side (negative coordinates) [-1920, 0, 1920, 1080]
        val mon3Usable = java.awt.Rectangle(-1920, 0, 1920, 1080)
        val clamped3 = com.alananasss.kittytune.clampFloatingBounds(1200, 800, -1500, 100, mon3Usable)
        org.junit.Assert.assertEquals(-1500, clamped3.x)
        org.junit.Assert.assertEquals(100, clamped3.y)
        org.junit.Assert.assertEquals(1200, clamped3.width)
        org.junit.Assert.assertEquals(800, clamped3.height)

        // Overflow clamping on negative monitor
        val clampedOverflow = com.alananasss.kittytune.clampFloatingBounds(1200, 800, -500, 100, mon3Usable)
        // maxX is 0, so x should be clamped to 0 - 1200 = -1200
        org.junit.Assert.assertEquals(-1200, clampedOverflow.x)
    }
}


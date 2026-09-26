import androidx.compose.ui.window.WindowPlacement
import com.alananasss.kittytune.data.theme.WindowsFullScreen
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.JFrame
import javax.swing.SwingUtilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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

    @Test
    fun testHwndTopmostConstants() {
        org.junit.Assert.assertNotNull(WindowsFullScreen.HWND_TOPMOST)
        org.junit.Assert.assertNotNull(WindowsFullScreen.HWND_NOTOPMOST)
        org.junit.Assert.assertEquals(
            com.sun.jna.Pointer.createConstant(-1),
            WindowsFullScreen.HWND_TOPMOST.pointer
        )
        org.junit.Assert.assertEquals(
            com.sun.jna.Pointer.createConstant(-2),
            WindowsFullScreen.HWND_NOTOPMOST.pointer
        )
    }

    @Test
    fun testFullScreenWindowStyleTransform() {
        val originalStyle = com.sun.jna.platform.win32.WinUser.WS_OVERLAPPEDWINDOW or com.sun.jna.platform.win32.WinUser.WS_VISIBLE
        val fsStyle = (originalStyle and (
            com.sun.jna.platform.win32.WinUser.WS_CAPTION or
            com.sun.jna.platform.win32.WinUser.WS_THICKFRAME or
            com.sun.jna.platform.win32.WinUser.WS_OVERLAPPEDWINDOW
        ).inv()) or com.sun.jna.platform.win32.WinUser.WS_POPUP

        // WS_POPUP must be set
        org.junit.Assert.assertTrue((fsStyle and com.sun.jna.platform.win32.WinUser.WS_POPUP) != 0)
        // WS_CAPTION must be stripped
        org.junit.Assert.assertEquals(0, fsStyle and com.sun.jna.platform.win32.WinUser.WS_CAPTION)
        // WS_THICKFRAME must be stripped
        org.junit.Assert.assertEquals(0, fsStyle and com.sun.jna.platform.win32.WinUser.WS_THICKFRAME)
        // WS_OVERLAPPEDWINDOW must be stripped
        org.junit.Assert.assertEquals(0, fsStyle and com.sun.jna.platform.win32.WinUser.WS_OVERLAPPEDWINDOW)
        // WS_VISIBLE must be preserved
        org.junit.Assert.assertTrue((fsStyle and com.sun.jna.platform.win32.WinUser.WS_VISIBLE) != 0)
    }

    /**
     * The reported bug: with the full player open, clicking another monitor minimised the app. That is
     * what AWT's exclusive full screen does on purpose when its window loses focus, which is why Windows
     * uses the borderless mode instead. This drives a real window through it.
     */
    @Test
    fun borderlessFullScreenSurvivesFocusLossAndRestoresBounds() {
        assumeTrue(WindowsFullScreen.isWindows && !GraphicsEnvironment.isHeadless())
        val start = Rectangle(120, 120, 900, 600)
        val app = onEdt { JFrame("fs-test").apply { bounds = start; isVisible = true } }
        val other = onEdt { JFrame("other").apply { setBounds(40, 40, 200, 150); isVisible = true } }
        try {
            val hwnd = WindowsFullScreen.handleOf(app)!!
            val framedStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)

            assertTrue(onEdt { WindowsFullScreen.enter(app) })
            val monitor = onEdt { app.graphicsConfiguration.bounds }
            assertEquals("covers the whole monitor", monitor, onEdt { app.bounds })

            onEdt { other.toFront(); other.requestFocus() }
            Thread.sleep(300)
            assertFalse("focus loss must not minimise", onEdt { app.extendedState and java.awt.Frame.ICONIFIED != 0 })

            assertTrue(onEdt { WindowsFullScreen.exit(app, WindowPlacement.Floating, start) })
            assertEquals(start, onEdt { app.bounds })
            assertEquals("frame restored", framedStyle, User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE))
            assertFalse(WindowsFullScreen.isFullScreen)
        } finally {
            onEdt { other.dispose(); app.dispose() }
        }
    }

    private fun <T> onEdt(block: () -> T): T {
        var result: Result<T>? = null
        SwingUtilities.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}

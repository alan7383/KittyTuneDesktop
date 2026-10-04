import com.alananasss.kittytune.data.theme.WindowsFullScreen
import androidx.compose.ui.window.WindowPlacement
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.SwingUtilities

/**
 * Fullscreen must cover exactly one monitor, whatever the display scaling (issue #66: "in full-screen
 * mode, the content spills over to the second monitor").
 *
 * Win32 rectangles are physical pixels and AWT bounds are scaled units. Feeding one to the other made the
 * window `scale` times too large at 125% / 150%, so it covered neighbouring monitors. These assertions read
 * the *native* rectangle, which is what the desktop actually shows, so they hold at any scale. Run with
 * `-PuiScale=1.5` to exercise the scaled case on a machine that is at 100%.
 */
class WindowsFullScreenDpiTest {

    @Before
    @After
    fun resetState() {
        WindowsFullScreen.resetForTesting()
    }

    private fun nativeRect(hwnd: WinDef.HWND): Rectangle {
        val r = WinDef.RECT()
        User32.INSTANCE.GetWindowRect(hwnd, r)
        return Rectangle(r.left, r.top, r.right - r.left, r.bottom - r.top)
    }

    private fun monitorRect(hwnd: WinDef.HWND): Rectangle {
        val mon = User32.INSTANCE.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST)
        val mi = WinUser.MONITORINFO()
        User32.INSTANCE.GetMonitorInfo(mon, mi)
        val m = mi.rcMonitor
        return Rectangle(m.left, m.top, m.right - m.left, m.bottom - m.top)
    }

    @Test
    fun fullscreenCoversExactlyTheMonitorAndRestoresTheNativeRectangle() {
        assumeTrue("Only runs on Windows", WindowsFullScreen.isWindows)
        assumeFalse("Requires graphical environment", GraphicsEnvironment.isHeadless())

        var frame: Frame? = null
        try {
            SwingUtilities.invokeAndWait {
                val f = Frame("DpiFullscreenFrame")
                frame = f
                f.isUndecorated = true
                f.setBounds(100, 100, 500, 400)
                f.isVisible = true
            }
            val window = frame!!
            val hwnd = WindowsFullScreen.handleOf(window)!!
            val before = nativeRect(hwnd)
            println("uiScale=${System.getProperty("sun.java2d.uiScale")} before=$before monitor=${monitorRect(hwnd)}")

            SwingUtilities.invokeAndWait { WindowsFullScreen.enter(window) }
            // AWT reacts to the native resize asynchronously; give it a moment before reading anything.
            Thread.sleep(500)
            SwingUtilities.invokeAndWait { }

            val monitor = monitorRect(hwnd)
            val actual = nativeRect(hwnd)
            // Allow at most 1 pixel rounding difference on displays with non-integer scaling fractions (e.g. 1.25x on 768p)
            assertTrue(
                "Fullscreen must cover the monitor (expected: $monitor, actual: $actual)",
                Math.abs(monitor.x - actual.x) <= 1 &&
                Math.abs(monitor.y - actual.y) <= 1 &&
                Math.abs(monitor.width - actual.width) <= 1 &&
                Math.abs(monitor.height - actual.height) <= 1
            )

            SwingUtilities.invokeAndWait {
                WindowsFullScreen.exit(window, WindowPlacement.Floating, Rectangle(100, 100, 500, 400))
            }
            Thread.sleep(500)
            SwingUtilities.invokeAndWait { }

            val afterExit = nativeRect(hwnd)
            assertTrue(
                "Leaving fullscreen must restore the native window rectangle (expected: $before, actual: $afterExit)",
                Math.abs(before.x - afterExit.x) <= 1 &&
                Math.abs(before.y - afterExit.y) <= 1 &&
                Math.abs(before.width - afterExit.width) <= 1 &&
                Math.abs(before.height - afterExit.height) <= 1
            )
        } finally {
            frame?.let { f -> SwingUtilities.invokeAndWait { f.dispose() } }
        }
    }
}

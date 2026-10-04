import com.alananasss.kittytune.data.theme.WindowsFullScreen
import androidx.compose.ui.window.WindowPlacement
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
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
            assertEquals("Fullscreen must cover exactly the monitor", monitor, nativeRect(hwnd))

            SwingUtilities.invokeAndWait {
                WindowsFullScreen.exit(window, WindowPlacement.Floating, Rectangle(100, 100, 500, 400))
            }
            Thread.sleep(500)
            SwingUtilities.invokeAndWait { }

            assertEquals("Leaving fullscreen must restore the native window rectangle", before, nativeRect(hwnd))
        } finally {
            frame?.let { f -> SwingUtilities.invokeAndWait { f.dispose() } }
        }
    }
}

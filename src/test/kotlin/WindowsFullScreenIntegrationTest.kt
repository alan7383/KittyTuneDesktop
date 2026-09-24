import androidx.compose.ui.window.WindowPlacement
import com.alananasss.kittytune.data.theme.WindowsFullScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import javax.swing.SwingUtilities

class WindowsFullScreenIntegrationTest {

    @Test
    fun testPlatformDetection() {
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("win")) {
            assertTrue("WindowsFullScreen.isWindows should be true on Windows/Wine", WindowsFullScreen.isWindows)
        } else {
            assertFalse("WindowsFullScreen.isWindows should be false on non-Windows", WindowsFullScreen.isWindows)
        }
    }

    @Test
    fun testNonWindowsGracefulHandling() {
        assumeFalse("Only runs on non-Windows", WindowsFullScreen.isWindows)
        // Calling enter/exit on Linux should safely return false without exceptions
        assertFalse(WindowsFullScreen.enter(null))
        assertFalse(WindowsFullScreen.exit(null, WindowPlacement.Floating, null))
    }

    @Test
    fun testWindowsFullscreenEnterAndExitRestoresBounds() {
        assumeTrue("Only runs on Windows/Wine", WindowsFullScreen.isWindows)
        assumeFalse("Requires graphical environment", GraphicsEnvironment.isHeadless())

        var frame: Frame? = null
        try {
            SwingUtilities.invokeAndWait {
                val f = Frame("TestFullscreenFrame")
                frame = f
                f.isUndecorated = true
                f.setBounds(150, 120, 800, 600)
                f.isVisible = true
            }

            val currentFrame = frame!!
            assertNotNull("Native window pointer must be resolved on Windows/Wine", WindowsFullScreen.handleOf(currentFrame))

            val initialBounds = currentFrame.bounds
            assertEquals(150, initialBounds.x)
            assertEquals(120, initialBounds.y)
            assertEquals(800, initialBounds.width)
            assertEquals(600, initialBounds.height)

            // 1. Enter fullscreen
            SwingUtilities.invokeAndWait {
                val entered = WindowsFullScreen.enter(currentFrame)
                assertTrue("Entering fullscreen should succeed", entered)
            }
            assertTrue("WindowsFullScreen.isFullScreen should be true", WindowsFullScreen.isFullScreen)

            // 2. Exit fullscreen to floating
            val fallback = Rectangle(150, 120, 800, 600)
            SwingUtilities.invokeAndWait {
                val exited = WindowsFullScreen.exit(currentFrame, WindowPlacement.Floating, fallback)
                assertTrue("Exiting fullscreen should succeed", exited)
            }
            assertFalse("WindowsFullScreen.isFullScreen should be false after exit", WindowsFullScreen.isFullScreen)

            // Bounds must match original pre-fullscreen dimensions
            assertEquals("Restored width must equal pre-fullscreen width", 800, currentFrame.bounds.width)
            assertEquals("Restored height must equal pre-fullscreen height", 600, currentFrame.bounds.height)
            assertEquals("Restored X must equal pre-fullscreen X", 150, currentFrame.bounds.x)
            assertEquals("Restored Y must equal pre-fullscreen Y", 120, currentFrame.bounds.y)

            // 3. Repeated fullscreen enter/exit cycle (reproducing the reported issue)
            for (i in 1..5) {
                SwingUtilities.invokeAndWait {
                    WindowsFullScreen.enter(currentFrame)
                }
                assertTrue("Must be fullscreen on iteration $i", WindowsFullScreen.isFullScreen)

                SwingUtilities.invokeAndWait {
                    WindowsFullScreen.exit(currentFrame, WindowPlacement.Floating, fallback)
                }
                assertFalse("Must not be fullscreen on iteration $i", WindowsFullScreen.isFullScreen)

                assertEquals("Iteration $i: Width should remain preserved", 800, currentFrame.bounds.width)
                assertEquals("Iteration $i: Height should remain preserved", 600, currentFrame.bounds.height)
                assertEquals("Iteration $i: X should remain preserved", 150, currentFrame.bounds.x)
                assertEquals("Iteration $i: Y should remain preserved", 120, currentFrame.bounds.y)
            }
        } finally {
            frame?.let { f ->
                SwingUtilities.invokeAndWait {
                    f.dispose()
                }
            }
        }
    }
}

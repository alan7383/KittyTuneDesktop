import com.alananasss.kittytune.core.ToolWindowStyle
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.Frame
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities
import kotlin.test.assertTrue

/**
 * `ToolWindowStyle.raiseToFront` is what makes the mini player's settings dialog take clicks on
 * Windows (issue #76): `toFront()`/`requestFocus()` through AWT don't reliably raise a regular
 * topmost window above a topmost *tool* window, so the dialog opened behind the mini player and
 * appeared frozen. `raiseToFront` calls `SetForegroundWindow` + `BringWindowToTop` +
 * `SetWindowPos(HWND_TOPMOST)` — the Win32 calls that actually reorder a window above other
 * topmost windows in the same process.
 *
 * On non-Windows the method is a no-op; on Windows it needs a displayable window with a native
 * peer. These tests guard both paths so the fix for issue #76 can't silently regress.
 */
class ToolWindowStyleRaiseToFrontTest {

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    @Test
    fun raiseToFrontDoesNotThrowOnNonDisplayableWindow() {
        assumeFalse("Requires a display to create AWT windows", GraphicsEnvironment.isHeadless())
        // A non-displayable window has no native peer (HWND), so Native.getWindowPointer would
        // throw on Windows. raiseToFront must guard on isDisplayable and return before touching JNA.
        // On non-Windows the method returns even earlier (isWindows check).
        val frame = Frame("RaiseToFrontProbe")
        try {
            assertTrue(!frame.isDisplayable, "Test precondition: frame must not be displayable yet")
            // Must not throw — the isDisplayable guard returns before touching JNA.
            ToolWindowStyle.raiseToFront(frame)
        } finally {
            SwingUtilities.invokeAndWait { runCatching { frame.dispose() } }
        }
    }

    @Test
    fun raiseToFrontHandlesDisplayableWindowWithoutCrashing() {
        assumeTrue("Only Windows has the native Win32 path", isWindows)
        assumeFalse("Requires graphical environment", GraphicsEnvironment.isHeadless())
        val frame = Frame("RaiseToFrontLive")
        try {
            SwingUtilities.invokeAndWait {
                frame.isUndecorated = true
                frame.setBounds(100, 100, 300, 200)
                frame.isVisible = true
            }
            // Now displayable and showing — the Win32 calls run. We only assert it doesn't crash;
            // SetForegroundWindow's return value depends on whether the test process holds the
            // foreground, which a CI run may not.
            ToolWindowStyle.raiseToFront(frame)
        } finally {
            SwingUtilities.invokeAndWait { runCatching { frame.dispose() } }
        }
    }

    @Test
    fun raiseToFrontMethodExistsAndIsCallable() {
        // Pure sanity: the method that fixes issue #76 is present on the object. A rename or
        // signature change that breaks the settings window's call site fails here.
        val method = ToolWindowStyle::class.java.getDeclaredMethod(
            "raiseToFront",
            java.awt.Window::class.java,
        )
        assertTrue(method.returnType == Void.TYPE, "raiseToFront must return Unit/Void")
    }
}

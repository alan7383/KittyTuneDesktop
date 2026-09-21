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
}

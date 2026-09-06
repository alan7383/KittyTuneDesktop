import com.alananasss.kittytune.core.LinuxTrayMenuHelper
import org.junit.Test
import java.awt.PopupMenu
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.awt.event.MouseEvent
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinuxTrayMenuHelperTest {

    @Test
    fun attach_clearsNativePopupMenuAndRegistersListener() {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val icon = TrayIcon(img)
        icon.popupMenu = PopupMenu("Test")

        var showWindowCalled = false
        var toggleMiniCalled = false
        var exitCalled = false

        LinuxTrayMenuHelper.attach(
            icon = icon,
            isMiniPlayerVisible = { false },
            onShowWindow = { showWindowCalled = true },
            onToggleMiniPlayer = { toggleMiniCalled = true },
            onExit = { exitCalled = true }
        )

        // Native PopupMenu must be cleared on Linux so AWT doesn't try to open off-screen
        assertNull(icon.popupMenu, "icon.popupMenu should be cleared on Linux")

        // MouseListener must be registered
        assertTrue(icon.mouseListeners.isNotEmpty(), "Mouse listener must be registered on TrayIcon")
    }
}

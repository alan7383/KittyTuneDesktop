import com.alananasss.kittytune.core.DBusMenuInterface
import com.alananasss.kittytune.core.LinuxStatusNotifierService
import com.alananasss.kittytune.core.LinuxTrayMenuHelper
import com.alananasss.kittytune.core.StatusNotifierItemInterface
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LinuxStatusNotifierLiveTest {

    @Test
    fun testLiveSNIContextMenuAndActivateFlow() {
        // Talks to a real session bus, which only a Linux desktop has.
        assumeTrue(System.getProperty("os.name").lowercase().contains("linux"))
        val windowShownLatch = CountDownLatch(1)
        val miniPlayerToggledLatch = CountDownLatch(1)
        val contextMenuTriggeredLatch = CountDownLatch(1)

        var isMiniPlayerVisible = false

        val suffix = "Live-${System.currentTimeMillis()}"
        val service = LinuxStatusNotifierService(
            serviceSuffix = suffix,
            iconName = "kittytune",
            isMiniPlayerVisible = { isMiniPlayerVisible },
            onActivate = {
                windowShownLatch.countDown()
            },
            onToggleMiniPlayer = {
                isMiniPlayerVisible = !isMiniPlayerVisible
                miniPlayerToggledLatch.countDown()
            },
            onExit = {},
            onContextMenu = { x, y ->
                contextMenuTriggeredLatch.countDown()
                LinuxTrayMenuHelper.showMenu(
                    preferredX = x,
                    preferredY = y,
                    isMiniPlayerVisible = { isMiniPlayerVisible },
                    onShowWindow = { windowShownLatch.countDown() },
                    onToggleMiniPlayer = {
                        isMiniPlayerVisible = !isMiniPlayerVisible
                        miniPlayerToggledLatch.countDown()
                    },
                    onExit = {}
                )
            }
        )

        try {
            val started = service.start()
            assertTrue(started, "LinuxStatusNotifierService must start successfully on this system")

            // 1. Verify watcher has registered our item
            val clientConn = DBusConnectionBuilder.forSessionBus().build()
            val remoteItem = clientConn.getRemoteObject(
                "org.kde.StatusNotifierItem-$suffix",
                "/StatusNotifierItem",
                StatusNotifierItemInterface::class.java
            )

            // 2. Simulate Left-Click (Activate) from KDE Plasma
            remoteItem.Activate(100, 100)
            assertTrue(windowShownLatch.await(3, TimeUnit.SECONDS), "Window show callback must be invoked on Activate")

            // 3. Simulate Native DBusMenu (Right-Click) as rendered by KDE Plasma
            val remoteMenu = clientConn.getRemoteObject(
                "org.kde.StatusNotifierItem-$suffix",
                "/MenuBar",
                DBusMenuInterface::class.java
            )

            val layout = remoteMenu.GetLayout(0, 1, emptyList())
            assertNotNull(layout, "KDE Plasma native DBusMenu layout must be present")
            assertEquals(0, layout.root.id, "Root id must be 0")

            // Click "Toggle Mini-Player" item in KDE native menu
            remoteMenu.Event(2, "clicked", Variant(0), UInt32(0))
            assertTrue(miniPlayerToggledLatch.await(3, TimeUnit.SECONDS), "Mini player toggle must be invoked from DBusMenu")
            assertTrue(isMiniPlayerVisible, "Mini-Player must be visible after click")

            // 4. Simulate Right-Click fallback (ContextMenu)
            remoteItem.ContextMenu(500, 500)
            assertTrue(contextMenuTriggeredLatch.await(3, TimeUnit.SECONDS), "ContextMenu callback must be invoked on fallback right click")

            clientConn.disconnect()
        } finally {
            service.close()
        }
    }
}

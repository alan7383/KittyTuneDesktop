import com.alananasss.kittytune.core.DBusMenuInterface
import com.alananasss.kittytune.core.LinuxStatusNotifierService
import com.alananasss.kittytune.core.StatusNotifierItemInterface
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LinuxStatusNotifierServiceTest {

    @Test
    fun testServiceRegistrationAndDirectCallbacks() {
        val activateLatch = CountDownLatch(1)
        val contextMenuLatch = CountDownLatch(1)
        var menuX = 0
        var menuY = 0

        val service = LinuxStatusNotifierService(
            serviceSuffix = "TestDirect-${System.currentTimeMillis()}",
            iconName = "kittytune",
            onActivate = {
                activateLatch.countDown()
            },
            onContextMenu = { x, y ->
                menuX = x
                menuY = y
                contextMenuLatch.countDown()
            }
        )

        try {
            val registered = service.start()
            if (!registered) {
                println("StatusNotifierWatcher not available in test environment, skipping assertion")
                return
            }

            assertTrue(service.isRegistered, "Service should be registered")

            service.triggerActivate(100, 100)
            service.triggerContextMenu(250, 350)

            assertTrue(activateLatch.await(2, TimeUnit.SECONDS), "Activate callback should have been invoked")
            assertTrue(contextMenuLatch.await(2, TimeUnit.SECONDS), "ContextMenu callback should have been invoked")
            assertTrue(menuX == 250 && menuY == 350, "Coordinates should match")
        } finally {
            service.close()
            assertFalse(service.isRegistered, "Service should be unregistered after close")
        }
    }

    @Test
    fun testRealDBusMethodInvocationAndDBusMenu() {
        val activateLatch = CountDownLatch(1)
        val contextMenuLatch = CountDownLatch(1)
        val miniPlayerLatch = CountDownLatch(1)
        var isMiniPlayerVisible = false
        var menuX = 0
        var menuY = 0
        val suffix = "TestBus-${System.currentTimeMillis()}"

        val service = LinuxStatusNotifierService(
            serviceSuffix = suffix,
            iconName = "kittytune",
            isMiniPlayerVisible = { isMiniPlayerVisible },
            onActivate = {
                activateLatch.countDown()
            },
            onToggleMiniPlayer = {
                isMiniPlayerVisible = !isMiniPlayerVisible
                miniPlayerLatch.countDown()
            },
            onExit = {},
            onContextMenu = { x, y ->
                menuX = x
                menuY = y
                contextMenuLatch.countDown()
            }
        )

        try {
            val started = service.start()
            if (!started) return

            // Connect a separate client to call ContextMenu & Activate over D-Bus
            val clientConn = DBusConnectionBuilder.forSessionBus().build()
            val busName = "org.kde.StatusNotifierItem-$suffix"

            val remoteItem = clientConn.getRemoteObject(
                busName,
                "/StatusNotifierItem",
                StatusNotifierItemInterface::class.java
            )

            remoteItem.Activate(123, 456)
            remoteItem.ContextMenu(789, 999)

            assertTrue(activateLatch.await(3, TimeUnit.SECONDS), "Activate should be received over D-Bus")
            assertTrue(contextMenuLatch.await(3, TimeUnit.SECONDS), "ContextMenu should be received over D-Bus")
            assertTrue(menuX == 789 && menuY == 999, "Coordinates over D-Bus must match: got ($menuX, $menuY)")

            // Test native DBusMenu (com.canonical.dbusmenu) as used by KDE Plasma
            val remoteMenu = clientConn.getRemoteObject(
                busName,
                "/MenuBar",
                DBusMenuInterface::class.java
            )

            val layout = remoteMenu.GetLayout(0, 1, emptyList())
            assertNotNull(layout, "DBusMenu layout must not be null")
            assertEquals(0, layout.root.id, "Root id must be 0")
            assertTrue(layout.root.children.isNotEmpty(), "Menu must have items")

            // Simulate clicking Item 2 (Toggle Mini-Player) in KDE native menu
            remoteMenu.Event(2, "clicked", Variant(0), UInt32(0))
            assertTrue(miniPlayerLatch.await(3, TimeUnit.SECONDS), "Mini player toggle must be invoked from DBusMenu")
            assertTrue(isMiniPlayerVisible, "Mini player state should have toggled to true")

            clientConn.disconnect()
        } finally {
            service.close()
        }
    }
}

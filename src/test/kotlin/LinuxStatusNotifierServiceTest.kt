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
    companion object {
        private lateinit var watcher: org.freedesktop.dbus.connections.impl.DBusConnection

        @JvmStatic @org.junit.BeforeClass
        fun startWatcher() {
            // A private dbus-run-session makes registration deterministic, without the user's shell.
            watcher = DBusConnectionBuilder.forSessionBus().withShared(false).build()
            watcher.exportObject("/StatusNotifierWatcher", object : com.alananasss.kittytune.core.StatusNotifierWatcherInterface {
                override fun getObjectPath() = "/StatusNotifierWatcher"
                override fun RegisterStatusNotifierItem(service: String) = Unit
            })
            watcher.requestBusName("org.kde.StatusNotifierWatcher")
        }

        @JvmStatic @org.junit.AfterClass
        fun stopWatcher() { watcher.disconnect() }
    }


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
            assertTrue(registered, "Service must register with the test watcher")

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
        val playPauseLatch = CountDownLatch(1)
        val nextLatch = CountDownLatch(1)
        val prevLatch = CountDownLatch(1)
        var isMiniPlayerVisible = false
        var menuX = 0
        var menuY = 0
        val suffix = "TestBus-${System.currentTimeMillis()}"

        val service = LinuxStatusNotifierService(
            serviceSuffix = suffix,
            iconName = "kittytune",
            isMiniPlayerVisible = { isMiniPlayerVisible },
            nowPlaying = {
                com.alananasss.kittytune.ui.tray.TrayNowPlaying(
                    title = "Test Song",
                    artist = "Test Artist",
                    artworkUrl = null,
                    isPlaying = true
                )
            },
            onPlayPause = {
                playPauseLatch.countDown()
            },
            onNext = {
                nextLatch.countDown()
            },
            onPrevious = {
                prevLatch.countDown()
            },
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
            assertTrue(started, "Service must register with the test watcher")

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

            // Test Menu property on StatusNotifierItem object
            val remoteProps = clientConn.getRemoteObject(
                busName,
                "/StatusNotifierItem",
                org.freedesktop.dbus.interfaces.Properties::class.java
            )
            val menuPath = remoteProps.Get<org.freedesktop.dbus.DBusPath>("org.kde.StatusNotifierItem", "Menu")
            assertEquals("/MenuBar", menuPath.path, "Menu property must point to /MenuBar")

            // Test native DBusMenu (com.canonical.dbusmenu) as used by KDE Plasma / Waybar
            val remoteMenu = clientConn.getRemoteObject(
                busName,
                "/MenuBar",
                DBusMenuInterface::class.java
            )

            val layout = remoteMenu.GetLayout(0, 1, emptyList())
            assertNotNull(layout, "DBusMenu layout must not be null")
            assertEquals(0, layout.root.id, "Root id must be 0")
            assertTrue(layout.root.children.isNotEmpty(), "Menu must have items")

            // Test GetGroupProperties & GetProperty on remoteMenu
            val groupProps = remoteMenu.GetGroupProperties(listOf(1, 2), listOf("label"))
            assertTrue(groupProps.isNotEmpty(), "Group properties should not be empty")

            val prop = remoteMenu.GetProperty(1, "label")
            assertNotNull(prop, "Property should be retrieved")

            // Simulate clicking Item 2 (Toggle Mini-Player) in native menu
            remoteMenu.Event(2, "clicked", Variant(0), UInt32(0))
            assertTrue(miniPlayerLatch.await(3, TimeUnit.SECONDS), "Mini player toggle must be invoked from DBusMenu")
            assertTrue(isMiniPlayerVisible, "Mini player state should have toggled to true")

            // Simulate clicking playback items
            remoteMenu.Event(11, "clicked", Variant(0), UInt32(0))
            remoteMenu.Event(12, "clicked", Variant(0), UInt32(0))
            remoteMenu.Event(13, "clicked", Variant(0), UInt32(0))
            assertTrue(playPauseLatch.await(3, TimeUnit.SECONDS), "PlayPause should be invoked from DBusMenu")
            assertTrue(nextLatch.await(3, TimeUnit.SECONDS), "Next should be invoked from DBusMenu")
            assertTrue(prevLatch.await(3, TimeUnit.SECONDS), "Previous should be invoked from DBusMenu")

            clientConn.disconnect()
        } finally {
            service.close()
        }
    }
    @Test
    fun tooltipRoundTripsAndAnnouncesTrackArtistIconAndClear() {
        val current = java.util.concurrent.atomic.AtomicReference<com.alananasss.kittytune.ui.tray.TrayNowPlaying?>(
            com.alananasss.kittytune.ui.tray.TrayNowPlaying("Song", "Artist", null, true))
        val suffix = "Tooltip-${System.nanoTime()}"
        val bus = "org.kde.StatusNotifierItem-$suffix"
        val service = LinuxStatusNotifierService(serviceSuffix = suffix, nowPlaying = { current.get() }, onActivate = {})
        val connection = DBusConnectionBuilder.forSessionBus().withShared(false).build()
        val signals = java.util.concurrent.LinkedBlockingQueue<Unit>()
        try {
            assertTrue(service.start())
            connection.addSigHandler(
                org.freedesktop.dbus.matchrules.DBusMatchRuleBuilder.create().withType("signal")
                    .withPath("/StatusNotifierItem").withInterface("org.kde.StatusNotifierItem")
                    .withMember("NewToolTip").build(),
                org.freedesktop.dbus.interfaces.DBusSigHandler<StatusNotifierItemInterface.NewToolTip> { signals.offer(Unit) })
            val properties = connection.getRemoteObject(bus, "/StatusNotifierItem",
                org.freedesktop.dbus.interfaces.Properties::class.java, false)
            assertEquals("(sa(iiay)ss)", properties.GetAll("org.kde.StatusNotifierItem").getValue("ToolTip").getSig())
            fun readTooltip(): List<String> {
                val process = ProcessBuilder("busctl", "--user", "--json=short", "get-property", bus,
                    "/StatusNotifierItem", "org.kde.StatusNotifierItem", "ToolTip").redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(0, process.waitFor(), output)
                val data = com.google.gson.JsonParser.parseString(output).asJsonObject.getAsJsonArray("data")
                return listOf(data[0].asString, data[2].asString, data[3].asString)
            }
            assertEquals(listOf("kittytune", "Song", "Artist"), readTooltip())
            current.set(com.alananasss.kittytune.ui.tray.TrayNowPlaying("Another Song", "Another Artist", null, false))
            service.notifyMenuUpdated()
            assertNotNull(signals.poll(2, TimeUnit.SECONDS))
            assertEquals(listOf("kittytune", "Another Song", "Another Artist"), readTooltip())
            service.notifyMenuUpdated()
            assertEquals(null, signals.poll(150, TimeUnit.MILLISECONDS), "No redundant tooltip signal")
            service.updateIcon("kittytune-blue")
            assertNotNull(signals.poll(2, TimeUnit.SECONDS))
            assertEquals("kittytune-blue", readTooltip()[0])
            current.set(null)
            service.notifyMenuUpdated()
            assertNotNull(signals.poll(2, TimeUnit.SECONDS))
            assertEquals(listOf("kittytune-blue", "KittyTune", ""), readTooltip())
        } finally { connection.disconnect(); service.close() }
    }

}

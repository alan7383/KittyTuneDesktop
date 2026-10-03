package com.alananasss.kittytune.core

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.Tuple
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.EventQueue
import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class DBusMenuItem(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<*>>,
    @field:Position(2) val children: List<Variant<*>>
) : Struct()

class DBusMenuLayout<A, B>(
    @field:Position(0) val revision: A,
    @field:Position(1) val root: B
) : Tuple()

class DBusMenuItemProperties(
    @field:Position(0) val id: Int,
    @field:Position(1) val properties: Map<String, Variant<*>>
) : Struct()

@DBusInterfaceName("com.canonical.dbusmenu")
interface DBusMenuInterface : DBusInterface {
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): DBusMenuLayout<UInt32, DBusMenuItem>
    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<DBusMenuItemProperties>
    fun GetProperty(id: Int, name: String): Variant<*>
    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)
    fun AboutToShow(id: Int): Boolean

    class LayoutUpdated(path: String, revision: UInt32, parent: Int) :
        DBusSignal(path, "com.canonical.dbusmenu", "LayoutUpdated", revision, parent)
}

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcherInterface : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItemInterface : DBusInterface {
    fun ContextMenu(x: Int, y: Int)
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)
    fun ProvideXdgActivationToken(token: String)

    class NewIcon(path: String) : DBusSignal(path, "org.kde.StatusNotifierItem", "NewIcon")
    class NewToolTip(path: String) : DBusSignal(path, "org.kde.StatusNotifierItem", "NewToolTip")
    class NewStatus(path: String, status: String) : DBusSignal(path, "org.kde.StatusNotifierItem", "NewStatus", status)
    class NewMenu(path: String) : DBusSignal(path, "org.kde.StatusNotifierItem", "NewMenu")
}

/**
 * Native FreeDesktop / KDE StatusNotifierItem (SNI) and DBusMenu implementation.
 *
 * KDE Plasma 6 (Wayland) and modern Linux desktop environments render tray menus natively
 * through the com.canonical.dbusmenu protocol on D-Bus.
 *
 * This provides:
 * - 100% native Qt/Breeze context menu rendered by KDE Plasma's panel (blur, shadows, system theme).
 * - Direct Activate(x, y) callback on left-click to restore/focus the window.
 * - Zero X11/XEmbed reliance or synthetic click issues on Wayland.
 * - Direct ContextMenu(x, y) fallback for lightweight bars that do not support DBusMenu.
 */
class LinuxStatusNotifierService(
    private val serviceSuffix: String = ProcessHandle.current().pid().toString(),
    private var iconName: String = "kittytune",
    private val isMiniPlayerVisible: () -> Boolean = { false },
    private val nowPlaying: () -> com.alananasss.kittytune.ui.tray.TrayNowPlaying? = { null },
    private val onPlayPause: () -> Unit = {},
    private val onNext: () -> Unit = {},
    private val onPrevious: () -> Unit = {},
    private val onActivate: () -> Unit,
    private val onToggleMiniPlayer: () -> Unit = {},
    private val onExit: () -> Unit = {},
    private val onContextMenu: (x: Int, y: Int) -> Unit = { _, _ -> }
) : Closeable {

    private var connection: DBusConnection? = null
    var isRegistered: Boolean = false
        private set

    private val iconThemePath: String by lazy {
        File(System.getProperty("user.home"), ".local/share/icons/hicolor").absolutePath
    }

    private val itemObject = StatusNotifierItemObject()
    private val menuObject = DBusMenuObject()

    fun start(): Boolean {
        return try {
            val os = System.getProperty("os.name").lowercase()
            if (!os.contains("linux") && !os.contains("nix")) return false

            val conn = DBusConnectionBuilder.forSessionBus().build()
            connection = conn

            val busName = "org.kde.StatusNotifierItem-$serviceSuffix"
            conn.requestBusName(busName)
            conn.exportObject("/StatusNotifierItem", itemObject)
            conn.exportObject("/MenuBar", menuObject)

            // Register with StatusNotifierWatcher if present
            try {
                val watcher = conn.getRemoteObject(
                    "org.kde.StatusNotifierWatcher",
                    "/StatusNotifierWatcher",
                    StatusNotifierWatcherInterface::class.java
                )
                watcher.RegisterStatusNotifierItem(busName)
                isRegistered = true
                println("SNI [init] Registered successfully as $busName with DBusMenu")
                true
            } catch (e: Exception) {
                println("SNI [init] StatusNotifierWatcher not responding: ${e.message}")
                conn.disconnect()
                connection = null
                false
            }
        } catch (e: Exception) {
            println("SNI [init] Failed to start D-Bus service: ${e.message}")
            connection = null
            false
        }
    }

    fun updateIcon(newIconName: String) {
        this.iconName = newIconName
        try {
            connection?.sendMessage(StatusNotifierItemInterface.NewIcon("/StatusNotifierItem"))
        } catch (_: Exception) {}
    }

    fun notifyMenuUpdated() {
        menuObject.notifyUpdated()
    }

    fun triggerActivate(x: Int, y: Int) {
        itemObject.Activate(x, y)
    }

    fun triggerContextMenu(x: Int, y: Int) {
        itemObject.ContextMenu(x, y)
    }

    override fun close() {
        try {
            connection?.unExportObject("/StatusNotifierItem")
            connection?.unExportObject("/MenuBar")
            connection?.disconnect()
        } catch (_: Exception) {}
        connection = null
        isRegistered = false
    }

    private fun dispatchToMain(action: () -> Unit) {
        if (EventQueue.isDispatchThread()) {
            action()
        } else {
            EventQueue.invokeLater { action() }
        }
    }

    inner class StatusNotifierItemObject : StatusNotifierItemInterface, Properties {

        override fun ContextMenu(x: Int, y: Int) {
            dispatchToMain { onContextMenu(x, y) }
        }

        override fun Activate(x: Int, y: Int) {
            dispatchToMain { onActivate() }
        }

        override fun SecondaryActivate(x: Int, y: Int) {
            Activate(x, y)
        }

        override fun Scroll(delta: Int, orientation: String) {}

        override fun ProvideXdgActivationToken(token: String) {
            runCatching { System.setProperty("XDG_ACTIVATION_TOKEN", token) }
        }

        override fun isRemote(): Boolean = false
        override fun getObjectPath(): String = "/StatusNotifierItem"

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(interface_name: String, property_name: String): A {
            val all = GetAll(interface_name)
            val prop = all[property_name] ?: GetAll("org.kde.StatusNotifierItem")[property_name]
            return prop?.value as A
        }

        override fun <A> Set(interface_name: String, property_name: String, value: A) {}

        override fun GetAll(interface_name: String): Map<String, Variant<*>> {
            val map = HashMap<String, Variant<*>>()
            if (interface_name == "org.kde.StatusNotifierItem" || interface_name.isEmpty()) {
                map["Category"] = Variant("ApplicationStatus", "s")
                map["Id"] = Variant("KittyTune", "s")
                map["Title"] = Variant("KittyTune", "s")
                map["Status"] = Variant("Active", "s")
                map["WindowId"] = Variant(0, "i")
                map["IconName"] = Variant(iconName, "s")
                map["IconThemePath"] = Variant(iconThemePath, "s")
                map["Menu"] = Variant(DBusPath("/MenuBar"), "o")
                map["ItemIsMenu"] = Variant(false, "b")
            }
            return map
        }
    }

    inner class DBusMenuObject : DBusMenuInterface, Properties {
        private val revision = AtomicInteger(1)

        fun notifyUpdated() {
            val rev = revision.incrementAndGet()
            try {
                connection?.sendMessage(DBusMenuInterface.LayoutUpdated("/MenuBar", UInt32(rev.toLong()), 0))
            } catch (_: Exception) {}
        }

        override fun GetLayout(
            parentId: Int,
            recursionDepth: Int,
            propertyNames: List<String>
        ): DBusMenuLayout<UInt32, DBusMenuItem> {
            val rootProps = mapOf("children-display" to Variant("submenu", "s"))
            val children = mutableListOf<Variant<*>>()

            val currentNowPlaying = nowPlaying()
            if (currentNowPlaying != null) {
                val title = currentNowPlaying.title.orEmpty().trim()
                val artist = currentNowPlaying.artist.orEmpty().trim()
                val headerText = listOfNotNull(title.takeIf { it.isNotBlank() }, artist.takeIf { it.isNotBlank() }).joinToString(" — ")
                if (headerText.isNotBlank()) {
                    children.add(
                        Variant(
                            DBusMenuItem(
                                10,
                                mapOf(
                                    "label" to Variant(headerText, "s"),
                                    "enabled" to Variant(false, "b")
                                ),
                                emptyList()
                            ),
                            "(ia{sv}av)"
                        )
                    )
                }

                val playPauseText = if (currentNowPlaying.isPlaying) str("action_pause") else str("action_play")
                children.add(
                    Variant(
                        DBusMenuItem(
                            11,
                            mapOf(
                                "label" to Variant(playPauseText, "s"),
                                "enabled" to Variant(true, "b")
                            ),
                            emptyList()
                        ),
                        "(ia{sv}av)"
                    )
                )

                children.add(
                    Variant(
                        DBusMenuItem(
                            12,
                            mapOf(
                                "label" to Variant(str("player_next"), "s"),
                                "enabled" to Variant(true, "b")
                            ),
                            emptyList()
                        ),
                        "(ia{sv}av)"
                    )
                )

                children.add(
                    Variant(
                        DBusMenuItem(
                            13,
                            mapOf(
                                "label" to Variant(str("player_previous"), "s"),
                                "enabled" to Variant(true, "b")
                            ),
                            emptyList()
                        ),
                        "(ia{sv}av)"
                    )
                )

                children.add(
                    Variant(
                        DBusMenuItem(
                            14,
                            mapOf(
                                "type" to Variant("separator", "s")
                            ),
                            emptyList()
                        ),
                        "(ia{sv}av)"
                    )
                )
            }

            val itemShowWindow = DBusMenuItem(
                1,
                mapOf(
                    "label" to Variant(str("menu_show_window"), "s"),
                    "enabled" to Variant(true, "b")
                ),
                emptyList()
            )

            val miniText = if (isMiniPlayerVisible()) str("menu_mini_player_hide") else str("menu_mini_player_show")
            val itemMiniPlayer = DBusMenuItem(
                2,
                mapOf(
                    "label" to Variant(miniText, "s"),
                    "enabled" to Variant(true, "b")
                ),
                emptyList()
            )

            val itemSeparator = DBusMenuItem(
                3,
                mapOf(
                    "type" to Variant("separator", "s")
                ),
                emptyList()
            )

            val itemExit = DBusMenuItem(
                4,
                mapOf(
                    "label" to Variant(str("menu_exit"), "s"),
                    "enabled" to Variant(true, "b")
                ),
                emptyList()
            )

            children.add(Variant(itemShowWindow, "(ia{sv}av)"))
            children.add(Variant(itemMiniPlayer, "(ia{sv}av)"))
            children.add(Variant(itemSeparator, "(ia{sv}av)"))
            children.add(Variant(itemExit, "(ia{sv}av)"))

            val root = DBusMenuItem(0, rootProps, children)
            return DBusMenuLayout(UInt32(revision.get().toLong()), root)
        }

        override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<DBusMenuItemProperties> {
            val layout = GetLayout(0, -1, propertyNames)
            val result = mutableListOf<DBusMenuItemProperties>()
            fun collect(item: DBusMenuItem) {
                if (ids.isEmpty() || ids.contains(item.id)) {
                    val filteredProps = if (propertyNames.isEmpty()) {
                        item.properties
                    } else {
                        item.properties.filterKeys { propertyNames.contains(it) }
                    }
                    result.add(DBusMenuItemProperties(item.id, filteredProps))
                }
                for (v in item.children) {
                    val child = v.value as? DBusMenuItem
                    if (child != null) {
                        collect(child)
                    }
                }
            }
            collect(layout.root)
            return result
        }

        override fun GetProperty(id: Int, name: String): Variant<*> {
            val group = GetGroupProperties(listOf(id), listOf(name))
            return group.firstOrNull()?.properties?.get(name) ?: Variant("", "s")
        }

        override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
            if (eventId == "clicked") {
                when (id) {
                    1 -> dispatchToMain { onActivate() }
                    2 -> dispatchToMain {
                        onToggleMiniPlayer()
                        notifyUpdated()
                    }
                    4 -> dispatchToMain { onExit() }
                    11 -> dispatchToMain {
                        onPlayPause()
                        notifyUpdated()
                    }
                    12 -> dispatchToMain {
                        onNext()
                        notifyUpdated()
                    }
                    13 -> dispatchToMain {
                        onPrevious()
                        notifyUpdated()
                    }
                }
            }
        }

        override fun AboutToShow(id: Int): Boolean = false

        override fun isRemote(): Boolean = false
        override fun getObjectPath(): String = "/MenuBar"

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(interface_name: String, property_name: String): A {
            val all = GetAll(interface_name)
            val prop = all[property_name] ?: GetAll("com.canonical.dbusmenu")[property_name]
            return prop?.value as A
        }

        override fun <A> Set(interface_name: String, property_name: String, value: A) {}

        override fun GetAll(interface_name: String): Map<String, Variant<*>> {
            val map = HashMap<String, Variant<*>>()
            if (interface_name == "com.canonical.dbusmenu" || interface_name.isEmpty()) {
                map["Version"] = Variant(UInt32(3), "u")
                map["Status"] = Variant("normal", "s")
                map["TextDirection"] = Variant("ltr", "s")
                map["IconThemePath"] = Variant(emptyArray<String>(), "as")
            }
            return map
        }
    }

    companion object {
        fun isSupported(): Boolean {
            val os = System.getProperty("os.name").lowercase()
            if (!os.contains("linux") && !os.contains("nix")) return false
            return runCatching {
                val conn = DBusConnectionBuilder.forSessionBus().build()
                val hasWatcher = runCatching {
                    val watcher = conn.getRemoteObject(
                        "org.kde.StatusNotifierWatcher",
                        "/StatusNotifierWatcher",
                        StatusNotifierWatcherInterface::class.java
                    )
                    watcher != null
                }.getOrDefault(false)
                conn.disconnect()
                hasWatcher
            }.getOrDefault(false)
        }
    }
}

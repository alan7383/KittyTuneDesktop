package com.alananasss.kittytune.core

import kotlinx.coroutines.delay
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.MouseInfo
import java.awt.Point
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.SwingUtilities

/**
 * Provides a reliable system tray context menu on Linux (KDE Plasma, GNOME, Waybar, etc.).
 *
 * On Linux, Compose Desktop attaches a heavyweight java.awt.PopupMenu to the TrayIcon.
 * Under KDE Plasma and modern desktop environments using StatusNotifierItem (SNI) via xembed-sni-proxy,
 * the XEmbed container is placed off-screen or unmapped, causing AWT's native XPopupMenuPeer
 * to fail or open off-screen when right-clicked.
 *
 * This helper listens directly for right-click / popup trigger mouse events and displays a
 * high-quality, lightweight Swing JPopupMenu positioned accurately at the cursor location.
 */
object LinuxTrayMenuHelper {

    private var activeListener: MouseAdapter? = null
    private var lastPopupTime = 0L

    suspend fun install(
        tooltip: String = "KittyTune",
        isMiniPlayerVisible: () -> Boolean,
        onShowWindow: () -> Unit,
        onToggleMiniPlayer: () -> Unit,
        onExit: () -> Unit
    ) {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("linux") && !os.contains("nix")) return

        for (attempt in 0 until 50) {
            val tray = runCatching { SystemTray.getSystemTray() }.getOrNull()
            val icon = tray?.trayIcons?.firstOrNull { it.toolTip == tooltip } ?: tray?.trayIcons?.firstOrNull()
            if (icon != null) {
                attach(icon, isMiniPlayerVisible, onShowWindow, onToggleMiniPlayer, onExit)
                return
            }
            delay(100)
        }
    }

    fun attach(
        icon: TrayIcon,
        isMiniPlayerVisible: () -> Boolean,
        onShowWindow: () -> Unit,
        onToggleMiniPlayer: () -> Unit,
        onExit: () -> Unit
    ) {
        // Clear AWT's native PopupMenu on Linux to avoid off-screen rendering or focus-grabbing conflicts
        icon.popupMenu = null

        activeListener?.let { icon.removeMouseListener(it) }

        val listener = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                handleEvent(e)
            }

            override fun mouseReleased(e: MouseEvent) {
                handleEvent(e)
            }

            private fun handleEvent(e: MouseEvent) {
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) {
                    val now = System.currentTimeMillis()
                    if (now - lastPopupTime < 350) return
                    lastPopupTime = now
                    showMenu(
                        preferredX = null,
                        preferredY = null,
                        isMiniPlayerVisible = isMiniPlayerVisible,
                        onShowWindow = onShowWindow,
                        onToggleMiniPlayer = onToggleMiniPlayer,
                        onExit = onExit
                    )
                }
            }
        }

        activeListener = listener
        icon.addMouseListener(listener)
    }

    fun showMenu(
        preferredX: Int? = null,
        preferredY: Int? = null,
        isMiniPlayerVisible: () -> Boolean,
        onShowWindow: () -> Unit,
        onToggleMiniPlayer: () -> Unit,
        onExit: () -> Unit
    ) {
        SwingUtilities.invokeLater {
            val pointer = if (preferredX != null && preferredY != null && preferredX > 0 && preferredY > 0) {
                Point(preferredX, preferredY)
            } else {
                runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: Point(100, 100)
            }

            val menu = JPopupMenu().apply {
                background = Color(24, 24, 28)
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color(50, 50, 60), 1),
                    BorderFactory.createEmptyBorder(6, 6, 6, 6)
                )
            }

            fun createItem(text: String, onClick: () -> Unit): JMenuItem {
                return JMenuItem(text).apply {
                    background = Color(24, 24, 28)
                    foreground = Color(240, 240, 245)
                    font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
                    border = BorderFactory.createEmptyBorder(7, 12, 7, 12)
                    isOpaque = true
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    addActionListener { onClick() }
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseEntered(e: MouseEvent) {
                            background = Color(44, 44, 56)
                        }
                        override fun mouseExited(e: MouseEvent) {
                            background = Color(24, 24, 28)
                        }
                    })
                }
            }

            menu.add(createItem(str("menu_show_window"), onShowWindow))
            menu.add(createItem(
                if (isMiniPlayerVisible()) str("menu_mini_player_hide") else str("menu_mini_player_show"),
                onToggleMiniPlayer
            ))
            menu.add(JSeparator().apply {
                foreground = Color(50, 50, 60)
                background = Color(50, 50, 60)
            })
            menu.add(createItem(str("menu_exit"), onExit))

            menu.show(null, pointer.x, pointer.y)
        }
    }
}

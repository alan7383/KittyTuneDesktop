package com.alananasss.kittytune.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JWindow

internal data class MacStatusMenuItem(val label: String, val action: () -> Unit)
internal const val MAC_STATUS_MENU_ANCHOR = "KittyTune status menu"

@Composable
internal fun MacStatusMenu(image: BufferedImage, items: List<MacStatusMenuItem?>) {
    if (!SystemTray.isSupported()) return
    val statusMenu = remember { StatusMenu(image) }
    SideEffect { statusMenu.update(image, items) }
    DisposableEffect(statusMenu) {
        val tray = SystemTray.getSystemTray()
        val added = runCatching { tray.add(statusMenu.icon) }.isSuccess
        onDispose {
            if (added) tray.remove(statusMenu.icon)
            statusMenu.dispose()
        }
    }
}

private class StatusMenu(image: BufferedImage) {
    val icon = TrayIcon(image, "KittyTune").apply { isImageAutoSize = true }
    private var items = emptyList<MacStatusMenuItem?>()
    private var contextMenu: PopupMenu? = null
    private var anchor: JWindow? = null

    init {
        icon.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON3 ||
                    (event.button == MouseEvent.BUTTON1 && event.isControlDown)
                ) {
                    showContextMenu(event)
                }
            }
        })
    }

    fun update(image: BufferedImage, items: List<MacStatusMenuItem?>) {
        if (icon.image !== image) icon.image = image
        val labelsChanged = this.items.map { it?.label } != items.map { it?.label }
        this.items = items
        if (labelsChanged) icon.popupMenu = createMenu()
    }

    private fun createMenu() = PopupMenu().apply {
        items.forEachIndexed { index, item ->
            if (item == null) addSeparator()
            else add(MenuItem(item.label).apply { addActionListener { items[index]?.action?.invoke() } })
        }
    }

    private fun showContextMenu(event: MouseEvent) {
        // AWT requires a visible component to open a native PopupMenu at screen coordinates.
        val window = anchor ?: JWindow().apply {
            name = MAC_STATUS_MENU_ANCHOR
            focusableWindowState = false
            isAutoRequestFocus = false
            setSize(1, 1)
            opacity = 0f
            anchor = this
        }
        contextMenu?.let(window::remove)
        val menu = createMenu()
        contextMenu = menu
        window.add(menu)
        window.setLocation(event.xOnScreen, event.yOnScreen)
        window.isVisible = true
        val origin = window.locationOnScreen
        menu.show(window, event.xOnScreen - origin.x, event.yOnScreen - origin.y)
    }

    fun dispose() {
        anchor?.dispose()
    }
}

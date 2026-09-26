package com.alananasss.kittytune.ui.tray

import java.awt.MouseInfo
import java.awt.Point
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Hooks the AWT [TrayIcon] that Compose's `Tray` composable creates so right-click opens
 * [TrayMenuState] instead of the native `PopupMenu`.
 *
 * Compose does not expose the TrayIcon, and its `menu = { … }` DSL can only build an AWT menu
 * (system chrome, square corners, no theme). Clearing `popupMenu` and listening for the popup
 * trigger is the same trick [com.alananasss.kittytune.core.LinuxTrayMenuHelper] already uses —
 * applied on every OS so Windows/macOS get the modern menu too.
 */
object ModernTrayMenuHook {

    private const val TOOLTIP = "KittyTune"
    private const val POLL_MS = 400L
    private const val DEBOUNCE_MS = 300L

    private var job: Job? = null
    private var listener: MouseAdapter? = null
    private var attachedTo: TrayIcon? = null
    private var lastPopupAt = 0L

    /** Where a right-click goes: the pointer in AWT screen coordinates. */
    private var onPopup: (Int, Int) -> Unit = { x, y -> TrayMenuState.toggle(x, y) }

    fun install(scope: CoroutineScope, onPopup: (Int, Int) -> Unit = { x, y -> TrayMenuState.toggle(x, y) }) {
        uninstall()
        this.onPopup = onPopup
        job = scope.launch {
            while (isActive) {
                val icon = findOurIcon()
                if (icon != null) {
                    if (icon !== attachedTo) attach(icon)
                    // Compose can re-install a PopupMenu on recomposition; keep it cleared.
                    runCatching { if (icon.popupMenu != null) icon.popupMenu = null }
                }
                delay(POLL_MS)
            }
        }
    }

    fun uninstall() {
        job?.cancel()
        job = null
        detach()
    }

    private fun findOurIcon(): TrayIcon? {
        val tray = runCatching { SystemTray.getSystemTray() }.getOrNull() ?: return null
        val icons = runCatching { tray.trayIcons.toList() }.getOrNull() ?: return null
        return icons.firstOrNull { it.toolTip == TOOLTIP }
            ?: icons.firstOrNull { it.toolTip?.contains(TOOLTIP, ignoreCase = true) == true }
            ?: icons.firstOrNull()
    }

    private fun attach(icon: TrayIcon) {
        detach()
        runCatching { icon.popupMenu = null }

        val mouse = object : MouseAdapter() {
            // Exactly one toggle per right-click. Both the press and the release used to toggle, with only
            // a 300 ms debounce between them, so a click held a little longer opened the menu on the press
            // and closed it again on the release. The platform says which of the two is the popup trigger
            // (the release on Windows, the press on macOS); a plain button-3 release is the fallback.
            private var openedOnPress = false

            override fun mousePressed(e: MouseEvent) {
                openedOnPress = e.isPopupTrigger
                if (openedOnPress) open()
            }

            override fun mouseReleased(e: MouseEvent) {
                if (openedOnPress) return
                if (e.isPopupTrigger || e.button == MouseEvent.BUTTON3) open()
            }

            private fun open() {
                val now = System.currentTimeMillis()
                if (now - lastPopupAt < DEBOUNCE_MS) return
                lastPopupAt = now
                val at = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return
                onPopup(at.x, at.y)
            }
        }

        listener = mouse
        attachedTo = icon
        runCatching { icon.addMouseListener(mouse) }
    }

    private fun detach() {
        attachedTo?.let { icon ->
            listener?.let { runCatching { icon.removeMouseListener(it) } }
        }
        attachedTo = null
        listener = null
    }
}

package com.alananasss.kittytune.core

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Window

/**
 * Makes a window a tool window: it floats with the app but gets no taskbar button and no Alt+Tab entry.
 *
 * The mini player was a full top-level window, so the taskbar showed a second button for it — with the
 * generic Java icon, since it is not the launcher's window. On Windows the extended style is changed and the
 * window re-shown once, which is what makes the shell drop the button. Elsewhere this does nothing.
 */
object ToolWindowStyle {
    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val WS_EX_APPWINDOW = 0x00040000

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    fun apply(window: Window) {
        if (!isWindows || !window.isDisplayable) return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            val user32 = User32.INSTANCE
            val style = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
            val toolStyle = (style or WS_EX_TOOLWINDOW) and WS_EX_APPWINDOW.inv()
            if (style == toolStyle) return
            if (!window.isShowing) {
                // Not on screen yet: the style simply lands before the first show, nothing to hide.
                user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, toolStyle)
                return
            }
            // The shell only re-reads the style when the window is shown, so hide, restyle, show. Through AWT:
            // hiding it with a bare ShowWindow left the toolkit believing the window was still up, and the window
            // came back deaf to the mouse (the mini player could not be dragged, its menu not clicked).
            window.isVisible = false
            user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, toolStyle)
            window.isVisible = true
        }
    }

    /**
     * Brings a tray menu to the foreground the way native tray menus are, so the Windows 11 hidden-icons
     * flyout it was opened from stays open while it is used.
     *
     * Native tray menus call `SetForegroundWindow` on their own window before showing: the flyout then stops
     * treating the pointer leaving it as a dismissal. A menu that stays in the background is, to the flyout,
     * the pointer wandering off — so it closed the moment you moved towards the menu. The process that just
     * received the tray click is allowed to take the foreground, so this is not refused.
     */
    fun activateLikeNativeMenu(window: Window) {
        if (!isWindows || !window.isDisplayable) return
        runCatching {
            User32.INSTANCE.SetForegroundWindow(WinDef.HWND(Native.getWindowPointer(window)))
        }
    }

    /**
     * Raises a window to the foreground above other topmost windows in the same process.
     *
     * `Window.toFront()` / `Window.requestFocus()` through AWT don't reliably raise a regular topmost
     * window above a topmost *tool* window (the mini player is one), so the settings dialog opened on
     * screen but took no clicks — it sat behind the mini player's topmost tool window even though both
     * carried `alwaysOnTop = true` (issue #76). `SetForegroundWindow` is the Win32 call that actually
     * activates a window and reorders it above other topmost windows in the Z-order.
     *
     * `SetForegroundWindow` can be refused when the calling thread doesn't own the foreground. Since the
     * user just clicked a context-menu item in our process, we hold the foreground privilege, so the call
     * is allowed. As belt-and-braces, `BringWindowToTop` and a `SetWindowPos(HWND_TOPMOST)` re-assert the
     * Z-order even if `SetForegroundWindow` is denied.
     */
    fun raiseToFront(window: Window) {
        if (!isWindows || !window.isDisplayable) return
        val hwnd = runCatching { WinDef.HWND(Native.getWindowPointer(window)) }.getOrNull() ?: return
        val user32 = User32.INSTANCE
        val HWND_TOPMOST = WinDef.HWND(Pointer.createConstant(-1))
        // The primary call: activates the window and brings it above other topmost windows.
        // SetForegroundWindow can be refused when the calling thread doesn't own the foreground,
        // but since the user just clicked in our process we hold the privilege.
        runCatching { user32.SetForegroundWindow(hwnd) }
        // Fallbacks that reorder the Z-order without needing the foreground privilege.
        runCatching { user32.BringWindowToTop(hwnd) }
        // Re-assert WS_EX_TOPMOST so the shell recomputes the topmost group's Z-order; the window
        // is already topmost from `alwaysOnTop = true`, but the explicit SetWindowPos forces the
        // recalculation that SetForegroundWindow alone sometimes doesn't trigger.
        runCatching {
            user32.SetWindowPos(
                hwnd,
                HWND_TOPMOST,
                0, 0, 0, 0,
                WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOACTIVATE,
            )
        }
    }

}

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
            // The shell only re-reads the style when the window is shown, so hide, restyle, show.
            user32.ShowWindow(hwnd, WinUser.SW_HIDE)
            user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, toolStyle)
            user32.ShowWindow(hwnd, WinUser.SW_SHOWNOACTIVATE)
            user32.SetWindowPos(
                hwnd, WinDef.HWND(Pointer.NULL), 0, 0, 0, 0,
                WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOZORDER or WinUser.SWP_FRAMECHANGED or 0x0010, // SWP_NOACTIVATE
            )
        }
    }
}

package com.alananasss.kittytune.core

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent

/**
 * Ensures floating utility windows (such as the lyrics mini-player or custom tray menu)
 * do not appear as a separate application or icon in the taskbar, dock, or Alt-Tab
 * task switcher across Linux (KDE Plasma, GNOME Shell, etc.) and Windows (Win32 / DWM).
 *
 * Background:
 * - On Windows: OpenJDK sets `WS_EX_TOOLWINDOW` for `Window.Type.UTILITY`. To guarantee
 *   that unowned Compose frames never show up in the Windows Taskbar or Alt-Tab switcher,
 *   this helper additionally enforces `WS_EX_TOOLWINDOW` and strips `WS_EX_APPWINDOW` via Win32 API.
 * - On Linux: OpenJDK's `Window.Type.UTILITY` only sets `_NET_WM_WINDOW_TYPE_UTILITY`.
 *   Window managers like KWin (KDE) and Mutter (GNOME) still treat top-level frames as switchable
 *   applications in Alt-Tab unless:
 *    1. `_NET_WM_STATE_SKIP_TASKBAR` is applied.
 *    2. `_NET_WM_STATE_SKIP_PAGER` is applied.
 *    3. `_KDE_NET_WM_STATE_SKIP_SWITCHER` and `_NET_WM_STATE_SKIP_SWITCHER` are applied.
 *
 * For already-mapped windows, EWMH mandates that `_NET_WM_STATE` changes must be sent via client
 * messages to the root window with `_NET_WM_STATE_ADD`. When a window is unmapped and remapped
 * (such as when hiding during full-screen playback and restoring), listeners re-assert them automatically.
 */
object LinuxWindowHelper {

    val isLinux: Boolean by lazy {
        val os = System.getProperty("os.name").lowercase()
        os.contains("linux") || os.contains("nix")
    }

    val isWindows: Boolean by lazy {
        System.getProperty("os.name").lowercase().contains("win")
    }

    /**
     * Configures [window] to act as an unlisted utility window that does not appear in Alt-Tab
     * or taskbars. Safe to call on all operating systems.
     */
    fun configureUtilityWindow(window: Window) {
        if (window is javax.swing.RootPaneContainer) {
            val root = window.rootPane
            if (root?.getClientProperty("utility_configured") == true) {
                if (isWindows) applyWindowsToolWindow(window)
                else if (isLinux) applyLinuxSkipHints(window)
                return
            }
            root?.putClientProperty("utility_configured", true)
        }

        runCatching {
            window.type = Window.Type.UTILITY
        }

        if (isWindows) {
            applyWindowsToolWindow(window)
        } else if (isLinux) {
            applyLinuxSkipHints(window)
        }

        // Install listeners to ensure hints are reapplied whenever the window is shown,
        // remapped, or gains its native peer.
        val listener = object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent?) {
                if (isWindows) applyWindowsToolWindow(window)
                else if (isLinux) applyLinuxSkipHints(window)
            }
        }
        window.addComponentListener(listener)

        window.addHierarchyListener { event ->
            if ((event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) != 0L && window.isShowing) {
                if (isWindows) applyWindowsToolWindow(window)
                else if (isLinux) applyLinuxSkipHints(window)
            }
        }
    }

    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val WS_EX_APPWINDOW = 0x00040000

    fun applyWindowsToolWindow(window: Window) {
        if (!isWindows) return
        runCatching {
            if (!window.isDisplayable) return
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            if (hwnd.pointer == null || hwnd.pointer == Pointer.NULL) return

            val exStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
            val newExStyle = (exStyle or WS_EX_TOOLWINDOW) and WS_EX_APPWINDOW.inv()
            if (exStyle != newExStyle) {
                User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, newExStyle)
                User32.INSTANCE.SetWindowPos(
                    hwnd,
                    null,
                    0, 0, 0, 0,
                    WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOZORDER or WinUser.SWP_FRAMECHANGED
                )
            }
        }
    }

    fun applyLinuxSkipHints(window: Window) {
        if (!isLinux) return
        runCatching {
            if (!window.isDisplayable) return

            val windowId = Native.getWindowID(window)
            if (windowId == 0L) return

            val x11 = X11.INSTANCE
            val display = x11.XOpenDisplay(null) ?: return
            try {
                val win = X11.Window(windowId)
                val root = x11.XDefaultRootWindow(display)

                val netWmState = x11.XInternAtom(display, "_NET_WM_STATE", false)
                val skipTaskbar = x11.XInternAtom(display, "_NET_WM_STATE_SKIP_TASKBAR", false)
                val skipPager = x11.XInternAtom(display, "_NET_WM_STATE_SKIP_PAGER", false)
                val kdeSkipSwitcher = x11.XInternAtom(display, "_KDE_NET_WM_STATE_SKIP_SWITCHER", false)
                val netSkipSwitcher = x11.XInternAtom(display, "_NET_WM_STATE_SKIP_SWITCHER", false)

                val netWmWindowType = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE", false)
                val typeUtility = x11.XInternAtom(display, "_NET_WM_WINDOW_TYPE_UTILITY", false)

                // 1. Direct property writes
                val atomType = X11.Atom(4) // XA_ATOM = 4

                val typeMem = Memory(Native.LONG_SIZE.toLong())
                typeMem.setLong(0, typeUtility.toLong())
                x11.XChangeProperty(display, win, netWmWindowType, atomType, 32, X11.PropModeReplace, typeMem, 1)

                val stateMem = Memory(4L * Native.LONG_SIZE)
                stateMem.setLong(0L * Native.LONG_SIZE, skipTaskbar.toLong())
                stateMem.setLong(1L * Native.LONG_SIZE, skipPager.toLong())
                stateMem.setLong(2L * Native.LONG_SIZE, kdeSkipSwitcher.toLong())
                stateMem.setLong(3L * Native.LONG_SIZE, netSkipSwitcher.toLong())
                x11.XChangeProperty(display, win, netWmState, atomType, 32, X11.PropModeReplace, stateMem, 4)

                // 2. EWMH Client messages to root window (essential for active window managers like KWin/Mutter)
                val atoms = arrayOf(skipTaskbar, skipPager, kdeSkipSwitcher, netSkipSwitcher)
                val mask = NativeLong((X11.SubstructureRedirectMask or X11.SubstructureNotifyMask).toLong())

                for (atom in atoms) {
                    val event = X11.XEvent()
                    event.setType(X11.XClientMessageEvent::class.java)
                    event.xclient.type = X11.ClientMessage
                    event.xclient.serial = NativeLong(0)
                    event.xclient.send_event = 1
                    event.xclient.display = display
                    event.xclient.window = win
                    event.xclient.message_type = netWmState
                    event.xclient.format = 32
                    event.xclient.data.setType(Array<NativeLong>::class.java)
                    val data = arrayOf(
                        NativeLong(1), // 1 = _NET_WM_STATE_ADD
                        NativeLong(atom.toLong()),
                        NativeLong(0),
                        NativeLong(1), // 1 = normal client application source
                        NativeLong(0)
                    )
                    event.xclient.data.l = data
                    event.write()

                    x11.XSendEvent(display, root, 0, mask, event)
                }

                x11.XFlush(display)
            } finally {
                x11.XCloseDisplay(display)
            }
        }
    }

    interface ExtendedX11 : com.sun.jna.Library {
        fun XUngrabPointer(display: X11.Display, time: NativeLong): Int
    }

    private val extendedX11: ExtendedX11? by lazy {
        if (isLinux) {
            runCatching { Native.load("X11", ExtendedX11::class.java) }.getOrNull()
        } else null
    }

    /**
     * Attempts to initiate a native, hardware-accelerated window drag through the host OS window manager
     * (EWMH _NET_WM_MOVERESIZE on Linux/KDE/GNOME, WM_SYSCOMMAND on Windows).
     *
     * In native drag:
     * - The host compositor translates the window surface directly at the monitor's native refresh rate (e.g. 144Hz/240Hz).
     * - The JVM Event Dispatch Thread is NOT flooded with mouse motion events.
     * - Skiko/Compose does not repeatedly stall on swapBuffers/vsync during drag.
     *
     * Returns true if native move was initiated, false if software drag should be used as fallback.
     */
    fun startNativeMove(window: Window, xOnScreen: Int, yOnScreen: Int): Boolean {
        if (isLinux) {
            return startNativeMoveLinux(window, xOnScreen, yOnScreen)
        } else if (isWindows) {
            return startNativeMoveWindows(window)
        }
        return false
    }

    fun startNativeMoveLinux(window: Window, xOnScreen: Int, yOnScreen: Int): Boolean {
        if (!isLinux) return false
        return runCatching {
            if (!window.isDisplayable) return false
            val windowId = Native.getWindowID(window)
            if (windowId == 0L) return false

            val x11 = X11.INSTANCE
            val display = x11.XOpenDisplay(null) ?: return false
            try {
                val win = X11.Window(windowId)
                val root = x11.XDefaultRootWindow(display)
                val netWmMoveResize = x11.XInternAtom(display, "_NET_WM_MOVERESIZE", false)

                // EWMH requires releasing any active client pointer grab before the WM can take over
                extendedX11?.XUngrabPointer(display, NativeLong(0))

                val event = X11.XEvent()
                event.setType(X11.XClientMessageEvent::class.java)
                event.xclient.type = X11.ClientMessage
                event.xclient.serial = NativeLong(0)
                event.xclient.send_event = 1
                event.xclient.display = display
                event.xclient.window = win
                event.xclient.message_type = netWmMoveResize
                event.xclient.format = 32
                event.xclient.data.setType(Array<NativeLong>::class.java)
                val data = arrayOf(
                    NativeLong(xOnScreen.toLong()),
                    NativeLong(yOnScreen.toLong()),
                    NativeLong(8), // 8 = _NET_WM_MOVERESIZE_MOVE
                    NativeLong(1), // 1 = Button1 (Left click)
                    NativeLong(1)  // 1 = normal application source
                )
                event.xclient.data.l = data
                event.write()

                val mask = NativeLong((X11.SubstructureRedirectMask or X11.SubstructureNotifyMask).toLong())
                x11.XSendEvent(display, root, 0, mask, event)
                x11.XFlush(display)
                true
            } finally {
                x11.XCloseDisplay(display)
            }
        }.getOrDefault(false)
    }

    interface ExtendedUser32 : com.sun.jna.Library {
        fun ReleaseCapture(): Boolean
        fun PostMessage(hWnd: WinDef.HWND, msg: Int, wParam: WinDef.WPARAM, lParam: WinDef.LPARAM): Boolean
    }

    private val extendedUser32: ExtendedUser32? by lazy {
        if (isWindows) {
            runCatching { Native.load("user32", ExtendedUser32::class.java) }.getOrNull()
        } else null
    }

    fun startNativeMoveWindows(window: Window): Boolean {
        if (!isWindows) return false
        return runCatching {
            if (!window.isDisplayable) return false
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            if (hwnd.pointer == null || hwnd.pointer == Pointer.NULL) return false
            extendedUser32?.ReleaseCapture()
            val WM_SYSCOMMAND = 0x0112
            val SC_MOVE = 0xF010
            val HTCAPTION = 0x0002
            extendedUser32?.PostMessage(hwnd, WM_SYSCOMMAND, WinDef.WPARAM((SC_MOVE or HTCAPTION).toLong()), WinDef.LPARAM(0))
            true
        }.getOrDefault(false)
    }
}


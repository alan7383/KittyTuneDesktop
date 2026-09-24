package com.alananasss.kittytune.data.theme

import androidx.compose.ui.window.WindowPlacement
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Window

/**
 * Manages borderless fullscreen mode on Windows via Win32 API (JNA).
 *
 * Rather than delegating fullscreen to Compose's [WindowPlacement.Fullscreen] or AWT's
 * `GraphicsDevice.setFullScreenWindow` (which activates exclusive fullscreen mode on Windows,
 * causing black screens and freezes whenever a screenshot or overlay interrupts the DirectX 12 surface),
 * this provides true borderless fullscreen (windowed fullscreen) by:
 * 1. Removing window caption and resizing borders (WS_CAPTION and WS_THICKFRAME).
 * 2. Sizing the window to exactly cover the current monitor's full dimensions (rcMonitor, including taskbar).
 * 3. Keeping the window managed by Windows DWM (Flip model DXGI swapchain), ensuring screenshots (Win+Shift+S,
 *    PrintScreen, Snipping Tool, Game Bar) and multi-monitor setups work completely without device lost errors or freezing.
 */
object WindowsFullScreen {

    val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

    private var originalStyle: Int = 0
    private var savedBounds: Rectangle? = null
    private var wasMaximized: Boolean = false

    @Volatile
    var isFullScreen: Boolean = false
        private set

    /** Native handle of an AWT window, or null before it has been realised. */
    fun handleOf(window: Window?): WinDef.HWND? {
        if (window == null || !window.isDisplayable) return null
        return runCatching { WinDef.HWND(Native.getWindowPointer(window)) }.getOrNull()
    }

    /**
     * Enters borderless fullscreen mode on Windows.
     * Must be called on the Swing Event Dispatch Thread (EDT).
     */
    fun enter(window: Window?): Boolean {
        if (!isWindows || window == null || !window.isDisplayable) return false
        val hwnd = handleOf(window) ?: return false
        if (isFullScreen) return true

        return runCatching {
            // Release any exclusive fullscreen handle AWT might have acquired
            val device = window.graphicsConfiguration?.device
            if (device?.fullScreenWindow === window) {
                device.fullScreenWindow = null
            }

            originalStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
            if (window is Frame) {
                wasMaximized = (window.extendedState and Frame.MAXIMIZED_BOTH) != 0
                if (!wasMaximized) {
                    savedBounds = window.bounds
                }
                // Normalize window state before applying monitor bounds so Win32 does not constrain it to work area
                if (wasMaximized) {
                    window.extendedState = Frame.NORMAL
                }
            } else {
                savedBounds = window.bounds
            }

            // Find monitor where the window currently is (supports multi-monitor setups seamlessly)
            val hMon = User32.INSTANCE.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST)
            val mi = WinUser.MONITORINFO()
            User32.INSTANCE.GetMonitorInfo(hMon, mi)

            val monX = mi.rcMonitor.left
            val monY = mi.rcMonitor.top
            val monW = mi.rcMonitor.right - mi.rcMonitor.left
            val monH = mi.rcMonitor.bottom - mi.rcMonitor.top

            val fsStyle = originalStyle and (WinUser.WS_CAPTION or WinUser.WS_THICKFRAME).inv()
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, fsStyle)
            User32.INSTANCE.SetWindowPos(
                hwnd,
                WinDef.HWND(Pointer.NULL), // HWND_TOP
                monX, monY, monW, monH,
                WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW
            )

            isFullScreen = true
            true
        }.getOrElse { false }
    }

    /**
     * Exits borderless fullscreen mode on Windows and restores the previous placement and bounds.
     * Must be called on the Swing Event Dispatch Thread (EDT).
     */
    fun exit(window: Window?, restorePlacement: WindowPlacement, fallbackBounds: Rectangle?): Boolean {
        if (!isWindows || window == null || !window.isDisplayable) return false
        val hwnd = handleOf(window) ?: return false
        if (!isFullScreen) return true

        return runCatching {
            // Restore original Win32 window style
            if (originalStyle != 0) {
                User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, originalStyle)
            }

            val shouldMaximize = restorePlacement == WindowPlacement.Maximized || wasMaximized
            if (shouldMaximize && window is Frame) {
                window.extendedState = Frame.MAXIMIZED_BOTH
                User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_MAXIMIZE)
                User32.INSTANCE.SetWindowPos(
                    hwnd,
                    WinDef.HWND(Pointer.NULL),
                    0, 0, 0, 0,
                    WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW
                )
            } else {
                if (window is Frame) {
                    window.extendedState = Frame.NORMAL
                }
                val targetBounds = savedBounds ?: fallbackBounds ?: window.bounds
                User32.INSTANCE.SetWindowPos(
                    hwnd,
                    WinDef.HWND(Pointer.NULL),
                    targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height,
                    WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW
                )
                window.setBounds(targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height)
            }

            // Ensure AWT device full screen is definitely null
            val device = window.graphicsConfiguration?.device
            if (device?.fullScreenWindow === window) {
                device.fullScreenWindow = null
            }

            window.revalidate()
            window.repaint()
            isFullScreen = false
            savedBounds = null
            wasMaximized = false
            true
        }.getOrElse { false }
    }
}

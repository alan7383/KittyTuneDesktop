package com.alananasss.kittytune.data.theme

import androidx.compose.ui.window.WindowPlacement
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.PointerByReference
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
 * 1. Removing window caption and resizing borders (WS_CAPTION, WS_THICKFRAME, and WS_OVERLAPPEDWINDOW)
 *    and setting WS_POPUP style.
 * 2. Elevating the window to HWND_TOPMOST and notifying Windows Shell Explorer via
 *    ITaskbarList2::MarkFullscreenWindow(hwnd, TRUE). This explicitly informs Explorer to keep the
 *    taskbar behind the window even when the window loses focus (e.g. user clicks on another monitor).
 * 3. Sizing the window to exactly cover the current monitor's full dimensions (rcMonitor, including taskbar).
 * 4. Keeping the window managed by Windows DWM (Flip model DXGI swapchain), ensuring screenshots (Win+Shift+S,
 *    PrintScreen, Snipping Tool, Game Bar) and multi-monitor setups work completely without device lost errors or freezing.
 */
object WindowsFullScreen {

    val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

    val HWND_TOPMOST: WinDef.HWND = WinDef.HWND(Pointer.createConstant(-1))
    val HWND_NOTOPMOST: WinDef.HWND = WinDef.HWND(Pointer.createConstant(-2))

    private val CLSID_TaskbarList = Guid.GUID("{56FDF344-FD6D-11d0-958A-006097C9A090}")
    private val IID_ITaskbarList2 = Guid.GUID("{602D4995-B13A-429B-A66E-1935E44F4317}")

    private var originalStyle: Int = 0
    private var savedBounds: Rectangle? = null
    private var savedWindowPlacement: WinUser.WINDOWPLACEMENT? = null
    private var wasMaximized: Boolean = false

    @Volatile
    var isFullScreen: Boolean = false
        private set

    fun resetForTesting() {
        isFullScreen = false
        savedBounds = null
        savedWindowPlacement = null
        wasMaximized = false
        originalStyle = 0
    }

    /** Native handle of an AWT window, or null before it has been realised. */
    fun handleOf(window: Window?): WinDef.HWND? {
        if (window == null || !window.isDisplayable) return null
        return runCatching { WinDef.HWND(Native.getWindowPointer(window)) }.getOrNull()
    }

    /**
     * Win32 ITaskbarList2 COM wrapper via JNA.
     * Method 3: HrInit()
     * Method 8: MarkFullscreenWindow(HWND hwnd, BOOL fFullscreen)
     */
    internal class TaskbarList2(pvInstance: Pointer) : Unknown(pvInstance) {
        fun hrInit(): Int {
            return _invokeNativeInt(3, arrayOf(pointer))
        }

        fun markFullscreenWindow(hwnd: WinDef.HWND, fullscreen: Boolean): Int {
            return _invokeNativeInt(8, arrayOf(pointer, hwnd, if (fullscreen) 1 else 0))
        }
    }

    /**
     * Informs Windows Explorer Shell that a window is fullscreen or windowed,
     * so that the taskbar remains behind the fullscreen window even on multi-monitor focus loss.
     */
    private fun markFullscreenWindow(hwnd: WinDef.HWND, fullscreen: Boolean) {
        runCatching {
            val coInitHr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED)
            val shouldUninit = coInitHr.toInt() == 0 || coInitHr.toInt() == 1
            try {
                val ppv = PointerByReference()
                val hr = Ole32.INSTANCE.CoCreateInstance(
                    CLSID_TaskbarList,
                    Pointer.NULL,
                    WTypes.CLSCTX_ALL,
                    IID_ITaskbarList2,
                    ppv
                )
                if (hr.toInt() == 0 && ppv.value != null && ppv.value != Pointer.NULL) {
                    val taskbarList = TaskbarList2(ppv.value)
                    try {
                        taskbarList.hrInit()
                        taskbarList.markFullscreenWindow(hwnd, fullscreen)
                    } finally {
                        taskbarList.Release()
                    }
                }
            } finally {
                if (shouldUninit) {
                    Ole32.INSTANCE.CoUninitialize()
                }
            }
        }
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

            // Save Win32 window placement (captures accurate restore placement even when maximized)
            val wp = WinUser.WINDOWPLACEMENT()
            wp.length = wp.size()
            if (User32.INSTANCE.GetWindowPlacement(hwnd, wp).booleanValue()) {
                savedWindowPlacement = wp
                wasMaximized = (wp.showCmd == WinUser.SW_SHOWMAXIMIZED) ||
                    (window is Frame && (window.extendedState and Frame.MAXIMIZED_BOTH) != 0)
            } else {
                wasMaximized = (window is Frame && (window.extendedState and Frame.MAXIMIZED_BOTH) != 0)
            }

            if (!wasMaximized || savedBounds == null) {
                savedBounds = window.bounds
            }

            if (window is Frame && wasMaximized) {
                window.extendedState = Frame.NORMAL
            }

            // Find monitor where the window currently is (supports multi-monitor setups seamlessly)
            val hMon = User32.INSTANCE.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST)
            val mi = WinUser.MONITORINFO()
            User32.INSTANCE.GetMonitorInfo(hMon, mi)

            val monX = mi.rcMonitor.left
            val monY = mi.rcMonitor.top
            val monW = mi.rcMonitor.right - mi.rcMonitor.left
            val monH = mi.rcMonitor.bottom - mi.rcMonitor.top

            // Strip caption, sizing borders, and any residual maximize flags before making it a popup
            val fsStyle = (originalStyle and (
                WinUser.WS_CAPTION or
                WinUser.WS_THICKFRAME or
                WinUser.WS_OVERLAPPEDWINDOW or
                WinUser.WS_MAXIMIZE or
                WinUser.WS_MINIMIZE
            ).inv()) or WinUser.WS_POPUP

            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, fsStyle)

            // Set AWT bounds using user-space coordinates from GraphicsConfiguration.
            // Win32 GetMonitorInfo returns physical pixels; passing physical pixels directly to
            // AWT's setBounds causes AWT to multiply by scale (e.g. 1.5x), enlarging the window
            // beyond the monitor and spilling onto the second screen (issue #66).
            val gc = window.graphicsConfiguration
            val userBounds = gc?.bounds
            val targetX = userBounds?.x ?: monX
            val targetY = userBounds?.y ?: monY
            val targetW = userBounds?.width ?: monW
            val targetH = userBounds?.height ?: monH

            window.setBounds(targetX, targetY, targetW, targetH)
            window.revalidate()
            window.repaint()

            // Ensure native window pos exactly matches physical monitor rectangle (avoids 1px rounding discrepancies)
            User32.INSTANCE.SetWindowPos(
                hwnd,
                HWND_TOPMOST,
                monX, monY, monW, monH,
                WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW
            )

            // Explicitly notify Explorer shell that this window is fullscreen
            markFullscreenWindow(hwnd, true)

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
            // Notify Explorer shell that window is no longer fullscreen
            markFullscreenWindow(hwnd, false)

            // Release topmost immediately
            User32.INSTANCE.SetWindowPos(
                hwnd,
                HWND_NOTOPMOST,
                0, 0, 0, 0,
                WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOACTIVATE
            )

            // Restore original Win32 window style
            val restoredStyle = if ((originalStyle and WinUser.WS_CAPTION) != 0) {
                (originalStyle and WinUser.WS_POPUP.inv() and WinUser.WS_MAXIMIZE.inv()) or
                    (WinUser.WS_CAPTION or WinUser.WS_THICKFRAME or WinUser.WS_OVERLAPPEDWINDOW)
            } else if (originalStyle != 0) {
                originalStyle
            } else {
                WinUser.WS_OVERLAPPEDWINDOW or WinUser.WS_CLIPCHILDREN or WinUser.WS_CLIPSIBLINGS
            }
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, restoredStyle)

            val hMon = User32.INSTANCE.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST)
            val mi = WinUser.MONITORINFO()
            User32.INSTANCE.GetMonitorInfo(hMon, mi)

            val shouldMaximize = restorePlacement == WindowPlacement.Maximized || wasMaximized
            if (shouldMaximize && window is Frame) {
                val wp = savedWindowPlacement
                if (wp != null) {
                    wp.showCmd = WinUser.SW_SHOWMAXIMIZED
                    User32.INSTANCE.SetWindowPlacement(hwnd, wp)
                } else {
                    val normal = savedBounds ?: fallbackBounds ?: window.bounds
                    User32.INSTANCE.SetWindowPos(
                        hwnd,
                        HWND_NOTOPMOST,
                        normal.x, normal.y, normal.width, normal.height,
                        WinUser.SWP_FRAMECHANGED or WinUser.SWP_NOACTIVATE
                    )
                    User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_RESTORE)
                    User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_MAXIMIZE)
                }
                User32.INSTANCE.SetWindowPos(
                    hwnd,
                    HWND_NOTOPMOST,
                    0, 0, 0, 0,
                    WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOZORDER or WinUser.SWP_FRAMECHANGED
                )
                window.extendedState = Frame.MAXIMIZED_BOTH
                if (window is androidx.compose.ui.awt.ComposeWindow) {
                    window.placement = WindowPlacement.Maximized
                }
            } else {
                if (window is Frame) {
                    window.extendedState = Frame.NORMAL
                }
                val targetBounds = savedBounds ?: fallbackBounds ?: window.bounds
                User32.INSTANCE.SetWindowPos(
                    hwnd,
                    HWND_NOTOPMOST,
                    0, 0, 0, 0,
                    WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOZORDER or WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW
                )
                window.setBounds(targetBounds.x, targetBounds.y, targetBounds.width, targetBounds.height)
                if (window is androidx.compose.ui.awt.ComposeWindow) {
                    window.placement = WindowPlacement.Floating
                }
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
            savedWindowPlacement = null
            wasMaximized = false
            true
        }.getOrElse { false }
    }
}

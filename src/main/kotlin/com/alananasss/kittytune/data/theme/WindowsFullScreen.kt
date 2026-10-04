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

            // Save Win32 window placement (captures accurate restore rectangle even when maximized)
            val wp = WinUser.WINDOWPLACEMENT()
            wp.length = wp.size()
            if (User32.INSTANCE.GetWindowPlacement(hwnd, wp).booleanValue()) {
                savedWindowPlacement = wp
                wasMaximized = (wp.showCmd == WinUser.SW_SHOWMAXIMIZED) ||
                    (window is Frame && (window.extendedState and Frame.MAXIMIZED_BOTH) != 0)
                val r = wp.rcNormalPosition
                savedBounds = Rectangle(r.left, r.top, r.right - r.left, r.bottom - r.top)
            } else {
                wasMaximized = (window is Frame && (window.extendedState and Frame.MAXIMIZED_BOTH) != 0)
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
            User32.INSTANCE.SetWindowPos(
                hwnd,
                HWND_TOPMOST,
                monX, monY, monW, monH,
                WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW
            )

            // Explicitly notify Explorer shell that this window is fullscreen
            markFullscreenWindow(hwnd, true)

            // No window.setBounds here: monX/monY/monW/monH are physical pixels from Win32, while AWT bounds
            // are DPI-scaled user units. Passing them through made the window `scale` times too large at
            // >100% display scaling, so it spilled onto neighbouring monitors. SetWindowPos above already
            // sized the native window and AWT picks the new bounds up from the resulting WM_WINDOWPOSCHANGED.
            window.revalidate()
            window.repaint()

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

            // Restore original Win32 window style, ensuring WS_POPUP and WS_MAXIMIZE are stripped first
            val baseStyle = if (originalStyle != 0) originalStyle else (WinUser.WS_OVERLAPPEDWINDOW or WinUser.WS_CLIPCHILDREN or WinUser.WS_CLIPSIBLINGS)
            val restoredStyle = (baseStyle and WinUser.WS_POPUP.inv() and WinUser.WS_MAXIMIZE.inv()) or
                (WinUser.WS_CAPTION or WinUser.WS_THICKFRAME or WinUser.WS_OVERLAPPEDWINDOW)
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
                val rawBounds = savedBounds ?: fallbackBounds ?: window.bounds
                val workW = mi.rcWork.right - mi.rcWork.left
                val workH = mi.rcWork.bottom - mi.rcWork.top
                var targetW = rawBounds.width.coerceIn(minOf(600, workW), workW)
                var targetH = rawBounds.height.coerceIn(minOf(400, workH), workH)
                var targetX = rawBounds.x
                var targetY = rawBounds.y

                // Ensure the window never spills outside the usable desktop area (above the taskbar)
                if (targetX + targetW > mi.rcWork.right) targetX = mi.rcWork.right - targetW
                if (targetX < mi.rcWork.left) targetX = mi.rcWork.left
                if (targetY + targetH > mi.rcWork.bottom) targetY = mi.rcWork.bottom - targetH
                if (targetY < mi.rcWork.top) targetY = mi.rcWork.top

                val wp = savedWindowPlacement
                if (wp != null) {
                    wp.showCmd = WinUser.SW_SHOWNORMAL
                    wp.rcNormalPosition.left = targetX
                    wp.rcNormalPosition.top = targetY
                    wp.rcNormalPosition.right = targetX + targetW
                    wp.rcNormalPosition.bottom = targetY + targetH
                    User32.INSTANCE.SetWindowPlacement(hwnd, wp)
                }
                User32.INSTANCE.SetWindowPos(
                    hwnd,
                    HWND_NOTOPMOST,
                    targetX, targetY, targetW, targetH,
                    WinUser.SWP_FRAMECHANGED or WinUser.SWP_SHOWWINDOW
                )
                // Native physical-pixel bounds were applied by SetWindowPos; see enter() for why AWT's
                // DPI-scaled setBounds must not be fed them.
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

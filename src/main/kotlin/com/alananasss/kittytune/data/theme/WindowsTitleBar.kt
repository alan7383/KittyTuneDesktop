package com.alananasss.kittytune.data.theme

import androidx.compose.ui.graphics.Color
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/**
 * Paints the Windows title bar in the app's own colours (issue #33).
 *
 * The window is a real AWT/Compose window, not a web view with its own chrome, so the caption is
 * drawn by the desktop window manager and not by us. On Windows that manager exposes exactly what
 * is needed: `DwmSetWindowAttribute` takes a caption colour, a caption text colour and a "use the
 * dark caption" flag, which together are enough to make the bar match without giving up native
 * dragging, snapping, or the system window buttons — everything an undecorated window would have
 * had to reimplement by hand.
 *
 * Elsewhere this is a no-op. On Linux the caption belongs to the window manager and there is no
 * equivalent to ask; on macOS the bar already follows the system appearance.
 *
 * The attributes are version-gated by Windows itself, which simply returns a failure HRESULT for
 * one it does not know: dark captions arrived in Windows 10 1809 and explicit caption colours in
 * Windows 11. Failures are ignored on purpose — an older Windows keeps its default bar.
 */
object WindowsTitleBar {

    private val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

    /** Use the dark caption treatment. 20 since Windows 10 2004; 19 on the first builds that had it. */
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY = 19

    /** Caption background, caption text, window border. All Windows 11 and later. */
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    /** Hands the attribute back to the system default rather than a colour of ours. */
    private const val DWMWA_COLOR_DEFAULT = -1 // 0xFFFFFFFF

    private interface Dwmapi : Library {
        fun DwmSetWindowAttribute(
            hwnd: WinDef.HWND,
            dwAttribute: Int,
            pvAttribute: IntByReference,
            cbAttribute: Int,
        ): Int
    }

    private val dwmapi: Dwmapi? by lazy {
        if (!isWindows) null else runCatching { Native.load("dwmapi", Dwmapi::class.java) }.getOrNull()
    }

    /**
     * Colours the caption of [window] to match the app.
     *
     * @param caption the colour the title bar should take.
     * @param captionText the colour of the window title text on top of it.
     * @param dark whether the app is currently on a dark palette, which is what the window buttons
     *   follow — they are drawn by Windows and would otherwise stay dark on a dark bar.
     */
    fun apply(window: Window?, caption: Color, captionText: Color, dark: Boolean) {
        val api = dwmapi ?: return
        val hwnd = handleOf(window) ?: return
        set(api, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
        set(api, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY, if (dark) 1 else 0)
        set(api, hwnd, DWMWA_CAPTION_COLOR, colorRef(caption))
        set(api, hwnd, DWMWA_TEXT_COLOR, colorRef(captionText))
        set(api, hwnd, DWMWA_BORDER_COLOR, colorRef(caption))
    }

    /**
     * Gives the caption back to Windows, for when the user would rather have the stock bar. The
     * dark flag follows the system app theme rather than ours, because that is what "default" means
     * here — a Windows in dark mode has a dark title bar of its own.
     */
    fun reset(window: Window?) {
        val api = dwmapi ?: return
        val hwnd = handleOf(window) ?: return
        val systemDark = systemUsesDarkMode()
        set(api, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, if (systemDark) 1 else 0)
        set(api, hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY, if (systemDark) 1 else 0)
        set(api, hwnd, DWMWA_CAPTION_COLOR, DWMWA_COLOR_DEFAULT)
        set(api, hwnd, DWMWA_TEXT_COLOR, DWMWA_COLOR_DEFAULT)
        set(api, hwnd, DWMWA_BORDER_COLOR, DWMWA_COLOR_DEFAULT)
    }

    private fun set(api: Dwmapi, hwnd: WinDef.HWND, attribute: Int, value: Int) {
        runCatching { api.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), 4) }
    }

    /** Native handle of an AWT window, or null before it has been realised. */
    private fun handleOf(window: Window?): WinDef.HWND? {
        if (window == null || !window.isDisplayable) return null
        return runCatching { WinDef.HWND(Native.getWindowPointer(window)) }.getOrNull()
    }

    /** COLORREF is `0x00BBGGRR`, the reverse of the usual RGB packing. */
    private fun colorRef(color: Color): Int {
        val r = (color.red * 255f).toInt().coerceIn(0, 255)
        val g = (color.green * 255f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255f).toInt().coerceIn(0, 255)
        return (b shl 16) or (g shl 8) or r
    }

    private fun systemUsesDarkMode(): Boolean = runCatching {
        Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "AppsUseLightTheme",
        ) == 0
    }.getOrDefault(false)
}

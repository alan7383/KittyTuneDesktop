package com.alananasss.kittytune.core

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The tray icon's context menu on Windows, as a real Win32 menu.
 *
 * Why not a window of ours: Windows 11's hidden-icons flyout only stays open around a native menu — a
 * Compose window, whatever its styling, focus or owner, counted as the pointer leaving, and the flyout
 * closed as soon as you moved towards the menu. Why not AWT's `PopupMenu`: Java draws those items itself,
 * so they stayed light and square whatever Windows' theme said.
 *
 * A plain `CreatePopupMenu` / `TrackPopupMenuEx` menu is drawn by Windows: dark when the app is (via
 * uxtheme's preferred app mode), rounded and animated on Windows 11. It runs on a thread of its own with a
 * hidden window and message loop, which is what `TrackPopupMenuEx` needs; the chosen item's action is
 * handed back to the UI thread.
 */
object Win32TrayMenu {

    /** One row of the menu. A null [onClick] is a greyed-out label; [isSeparator] draws a line. */
    data class Entry(
        val text: String = "",
        val onClick: (() -> Unit)? = null,
        val checked: Boolean = false,
        val isSeparator: Boolean = false,
    ) {
        companion object {
            val Separator = Entry(isSeparator = true)
        }
    }

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val isSupported: Boolean get() = isWindows

    private val pending = AtomicReference<List<Entry>?>(null)
    private val hwndRef = AtomicReference<WinDef.HWND?>(null)
    // Held so the callback is never collected while Windows can still call it.
    private var windowProc: WinUser.WindowProc? = null
    @Volatile private var dark = true

    /** Whether the menu is drawn dark; follows the app's palette. */
    fun setDark(value: Boolean) {
        dark = value
    }

    /** Opens the menu at the pointer. Safe from any thread. */
    fun show(entries: List<Entry>) {
        if (!isWindows) return
        val hwnd = ensureThread() ?: return
        pending.set(entries)
        User32.INSTANCE.PostMessage(hwnd, WM_SHOW_MENU, null, null)
    }

    @Synchronized
    private fun ensureThread(): WinDef.HWND? {
        hwndRef.get()?.let { return it }
        val ready = CountDownLatch(1)
        thread(isDaemon = true, name = "Win32TrayMenu") {
            runCatching { runMessageLoop(ready) }
            ready.countDown()
        }
        ready.await()
        return hwndRef.get()
    }

    private fun runMessageLoop(ready: CountDownLatch) {
        val user32 = User32.INSTANCE
        val instance = Kernel32.INSTANCE.GetModuleHandle(null)
        val proc = WinUser.WindowProc { hwnd, msg, wParam, lParam ->
            if (msg == WM_SHOW_MENU) {
                pending.getAndSet(null)?.let { track(hwnd, it) }
                WinDef.LRESULT(0)
            } else {
                user32.DefWindowProc(hwnd, msg, wParam, lParam)
            }
        }
        windowProc = proc
        val cls = WinUser.WNDCLASSEX().apply {
            hInstance = instance
            lpfnWndProc = proc
            lpszClassName = CLASS_NAME
        }
        user32.RegisterClassEx(cls)
        // A hidden top-level window, not message-only: the menu's owner has to be able to take the
        // foreground, which is what makes a click elsewhere dismiss the menu.
        val hwnd = user32.CreateWindowEx(0, CLASS_NAME, "KittyTune tray menu", WinUser.WS_POPUP, 0, 0, 0, 0, null, null, instance, null)
        hwndRef.set(hwnd)
        allowDarkMode(hwnd)
        ready.countDown()

        val msg = WinUser.MSG()
        while (user32.GetMessage(msg, null, 0, 0) > 0) {
            user32.TranslateMessage(msg)
            user32.DispatchMessage(msg)
        }
    }

    private fun track(hwnd: WinDef.HWND, entries: List<Entry>) {
        val menus = MenuApi.INSTANCE
        applyTheme()
        val menu = menus.CreatePopupMenu() ?: return
        try {
            entries.forEachIndexed { index, entry ->
                when {
                    entry.isSeparator -> menus.AppendMenuW(menu, MF_SEPARATOR, 0, null)
                    else -> {
                        var flags = MF_STRING
                        if (entry.onClick == null) flags = flags or MF_GRAYED
                        if (entry.checked) flags = flags or MF_CHECKED
                        menus.AppendMenuW(menu, flags, index + 1, WString(entry.text))
                    }
                }
            }
            val cursor = WinDef.POINT()
            User32.INSTANCE.GetCursorPos(cursor)
            // Foreground first, then a WM_NULL after: the documented pair that makes a tray menu close
            // when you click elsewhere instead of lingering.
            User32.INSTANCE.SetForegroundWindow(hwnd)
            val chosen = menus.TrackPopupMenuEx(
                menu, TPM_RETURNCMD or TPM_RIGHTBUTTON or TPM_BOTTOMALIGN, cursor.x, cursor.y, hwnd, null,
            )
            User32.INSTANCE.PostMessage(hwnd, WM_NULL, null, null)
            entries.getOrNull(chosen - 1)?.onClick?.let { action -> java.awt.EventQueue.invokeLater(action) }
        } finally {
            menus.DestroyMenu(menu)
        }
    }

    /** uxtheme's undocumented but universally used dark-menu switch (ordinals 135 and 136). */
    private fun applyTheme() {
        runCatching {
            val uxtheme = Kernel32.INSTANCE.LoadLibraryEx("uxtheme.dll", null, 0) ?: return
            Kernel32.INSTANCE.GetProcAddress(uxtheme, ORDINAL_SET_PREFERRED_APP_MODE)?.let {
                Function.getFunction(it).invokeInt(arrayOf(if (dark) MODE_FORCE_DARK else MODE_FORCE_LIGHT))
            }
            Kernel32.INSTANCE.GetProcAddress(uxtheme, ORDINAL_FLUSH_MENU_THEMES)?.let {
                Function.getFunction(it).invokeVoid(emptyArray())
            }
        }
    }

    /** Lets the owner window's menus follow the dark app mode (uxtheme ordinal 133). */
    private fun allowDarkMode(hwnd: WinDef.HWND) {
        runCatching {
            val uxtheme = Kernel32.INSTANCE.LoadLibraryEx("uxtheme.dll", null, 0) ?: return
            Kernel32.INSTANCE.GetProcAddress(uxtheme, ORDINAL_ALLOW_DARK_MODE_FOR_WINDOW)?.let {
                Function.getFunction(it).invokeInt(arrayOf(hwnd, true))
            }
        }
    }

    private interface MenuApi : StdCallLibrary {
        fun CreatePopupMenu(): WinDef.HMENU?
        fun AppendMenuW(menu: WinDef.HMENU, flags: Int, id: Int, text: WString?): Boolean
        fun TrackPopupMenuEx(menu: WinDef.HMENU, flags: Int, x: Int, y: Int, hwnd: WinDef.HWND, params: Pointer?): Int
        fun DestroyMenu(menu: WinDef.HMENU): Boolean

        companion object {
            val INSTANCE: MenuApi = Native.load("user32", MenuApi::class.java)
        }
    }

    private const val CLASS_NAME = "KittyTuneTrayMenuOwner"
    private const val WM_SHOW_MENU = WinUser.WM_USER + 1
    private const val WM_NULL = 0x0000

    private const val MF_STRING = 0x0000
    private const val MF_GRAYED = 0x0001
    private const val MF_CHECKED = 0x0008
    private const val MF_SEPARATOR = 0x0800
    private const val TPM_RIGHTBUTTON = 0x0002
    private const val TPM_BOTTOMALIGN = 0x0020
    private const val TPM_RETURNCMD = 0x0100

    private const val ORDINAL_ALLOW_DARK_MODE_FOR_WINDOW = 133
    private const val ORDINAL_SET_PREFERRED_APP_MODE = 135
    private const val ORDINAL_FLUSH_MENU_THEMES = 136
    private const val MODE_FORCE_DARK = 2
    private const val MODE_FORCE_LIGHT = 3
}

package com.alananasss.kittytune.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Frame
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlinx.coroutines.delay

/**
 * Whether anyone can currently see the window this composition is in.
 *
 * Decorative animations that run for as long as music plays (the wavy seek bar) read this to stop when
 * nobody can see them: every frame they request repaints the whole window, so a hidden player was
 * still rendering at 60 fps. Defaults to true, so a window that does not provide it animates as before.
 */
val LocalWindowSeen = compositionLocalOf { true }

private const val RECHECK_INTERVAL_MS = 1_000L

/**
 * Tracks [window]'s visibility to the user, deliberately erring towards "seen".
 *
 * Losing focus is *not* enough to count as hidden: with two monitors the player commonly sits on one
 * while the user works on the other. Hidden means minimised, closed to the tray, or — on Windows — the
 * foreground window sits above this one in z-order and covers it completely.
 */
@Composable
fun rememberWindowSeen(window: Window): Boolean {
    var seen by remember(window) { mutableStateOf(true) }

    DisposableEffect(window) {
        fun recheck() {
            seen = isSeen(window)
        }
        val windowListener = object : WindowAdapter() {
            override fun windowIconified(e: WindowEvent) = recheck()
            override fun windowDeiconified(e: WindowEvent) = recheck()
            override fun windowActivated(e: WindowEvent) = recheck()
            override fun windowDeactivated(e: WindowEvent) = recheck()
            override fun windowStateChanged(e: WindowEvent) = recheck()
        }
        val componentListener = object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent) = recheck()
            override fun componentHidden(e: ComponentEvent) = recheck()
        }
        window.addWindowListener(windowListener)
        window.addWindowStateListener(windowListener)
        window.addComponentListener(componentListener)
        onDispose {
            window.removeWindowListener(windowListener)
            window.removeWindowStateListener(windowListener)
            window.removeComponentListener(componentListener)
        }
    }

    // Another app being moved over us or maximised raises no event on our side, so while we are in the
    // background the answer is refreshed once a second. A handful of Win32 calls, nothing more.
    LaunchedEffect(window) {
        while (true) {
            delay(RECHECK_INTERVAL_MS)
            seen = isSeen(window)
        }
    }

    return seen
}

private fun isSeen(window: Window): Boolean {
    if (!window.isShowing) return false
    if (window is Frame && (window.extendedState and Frame.ICONIFIED) != 0) return false
    if (window.isActive) return true
    return !isCoveredByForegroundWindow(window)
}

private val isWindows = System.getProperty("os.name").lowercase().contains("win")

/**
 * True when the foreground window is above [window] in z-order and its bounds contain [window]'s —
 * a maximised browser on the same monitor, say. A foreground window on another monitor, or one that
 * only overlaps, leaves the player visible. The desktop is never "above": it sits at the bottom of
 * the z-order even while it has focus, which the walk below accounts for.
 */
private fun isCoveredByForegroundWindow(window: Window): Boolean {
    if (!isWindows) return false
    return runCatching {
        val user32 = User32.INSTANCE
        val ours = WinDef.HWND(com.sun.jna.Native.getWindowPointer(window))
        val foreground = user32.GetForegroundWindow() ?: return false
        if (foreground == ours || !user32.IsWindowVisible(foreground)) return false
        if (!isAbove(foreground, ours)) return false

        val fgRect = WinDef.RECT().also { user32.GetWindowRect(foreground, it) }
        val ourRect = WinDef.RECT().also { user32.GetWindowRect(ours, it) }
        // Windows adds invisible resize borders to GetWindowRect; a few pixels of slack absorbs them.
        val slack = 16
        fgRect.left <= ourRect.left + slack && fgRect.top <= ourRect.top + slack &&
            fgRect.right >= ourRect.right - slack && fgRect.bottom >= ourRect.bottom - slack
    }.getOrDefault(false)
}

private fun isAbove(upper: WinDef.HWND, lower: WinDef.HWND): Boolean {
    val user32 = User32.INSTANCE
    var next = user32.GetWindow(upper, WinDef.DWORD(WinUser.GW_HWNDNEXT.toLong()))
    // Bounded: a desktop has a few hundred top-level windows at most.
    repeat(4_096) {
        if (next == null) return false
        if (next == lower) return true
        next = user32.GetWindow(next, WinDef.DWORD(WinUser.GW_HWNDNEXT.toLong()))
    }
    return false
}

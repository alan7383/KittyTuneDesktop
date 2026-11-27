package com.alananasss.kittytune.core

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.event.KeyEvent
import kotlin.concurrent.thread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class PlaybackShortcut(val virtualKey: Int, val keyLabel: String, val actionLabel: String) {
    PLAY_PAUSE(KeyEvent.VK_SPACE, "shortcut_key_space", "shortcut_play_pause"),
    PREVIOUS(KeyEvent.VK_LEFT, "←", "shortcut_prev_track"),
    NEXT(KeyEvent.VK_RIGHT, "→", "shortcut_next_track"),
}

internal object BackgroundPlaybackShortcuts {
    val isSupported: Boolean = System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true)

    private val _unavailable = MutableStateFlow<Set<PlaybackShortcut>>(emptySet())
    val unavailable = _unavailable.asStateFlow()

    suspend fun listen(enabled: Flow<Boolean>, onAction: (PlaybackShortcut) -> Unit) = coroutineScope {
        if (!isSupported) return@coroutineScope

        val actions = Channel<PlaybackShortcut>(Channel.BUFFERED)
        WindowsPlaybackShortcuts(
            onAction = { actions.trySend(it) },
            onUnavailable = { _unavailable.value = it },
        ).use { shortcuts ->
            launch { for (action in actions) onAction(action) }
            try {
                enabled.collect { shortcuts.setEnabled(it) }
            } finally {
                actions.close()
            }
        }
    }
}

/** The registrations belong to a native thread so they survive disposal of the tray-hidden window. */
internal class WindowsPlaybackShortcuts(
    private val onAction: (PlaybackShortcut) -> Unit,
    private val onUnavailable: (Set<PlaybackShortcut>) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var threadId = 0
    private var enabled = false
    private var closed = false
    private var messageLoopFailed = false

    init {
        thread(name = "KittyTune Playback Shortcuts", isDaemon = true) {
            runCatching { runMessageLoop() }.onFailure { error ->
                synchronized(lock) {
                    messageLoopFailed = true
                    if (enabled && !closed) onUnavailable(PlaybackShortcut.entries.toSet())
                }
                println("Windows playback shortcuts: ${error.message}")
            }
        }
    }

    fun setEnabled(value: Boolean): Unit = synchronized(lock) {
        if (closed || enabled == value) return
        enabled = value
        if (messageLoopFailed) {
            onUnavailable(if (enabled) PlaybackShortcut.entries.toSet() else emptySet())
        } else if (threadId != 0) {
            postMessage(UPDATE_REGISTRATIONS)
        }
    }

    override fun close(): Unit = synchronized(lock) {
        if (closed) return
        closed = true
        if (threadId != 0) postMessage(WinUser.WM_QUIT)
    }

    private fun runMessageLoop() {
        val user32 = User32.INSTANCE
        val message = WinUser.MSG()
        // Create the message queue before making the thread visible to callers of PostThreadMessage.
        user32.PeekMessage(message, null, 0, 0, 0)
        synchronized(lock) {
            if (closed) return
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
        }

        val registered = mutableSetOf<PlaybackShortcut>()
        val modifiers = WinUser.MOD_CONTROL or WinUser.MOD_ALT or WinUser.MOD_SHIFT or WinUser.MOD_NOREPEAT
        fun updateRegistrations() = synchronized(lock) {
            if (enabled && !closed) {
                for (shortcut in PlaybackShortcut.entries) {
                    if (shortcut !in registered && user32.RegisterHotKey(null, shortcut.ordinal, modifiers, shortcut.virtualKey)) {
                        registered.add(shortcut)
                    }
                }
                onUnavailable(PlaybackShortcut.entries.toSet() - registered)
            } else {
                registered.forEach { user32.UnregisterHotKey(null, it.ordinal) }
                registered.clear()
                onUnavailable(emptySet())
            }
        }

        try {
            updateRegistrations()
            while (true) {
                val result = user32.GetMessage(message, null, 0, 0)
                check(result >= 0) { "GetMessage failed with Windows error ${Kernel32.INSTANCE.GetLastError()}" }
                if (result == 0) break
                when (message.message) {
                    UPDATE_REGISTRATIONS -> updateRegistrations()
                    WinUser.WM_HOTKEY -> synchronized(lock) {
                        val shortcut = PlaybackShortcut.entries.getOrNull(message.wParam.toInt())
                        if (enabled && !closed && shortcut != null && shortcut in registered) onAction(shortcut)
                    }
                }
            }
        } finally {
            registered.forEach { user32.UnregisterHotKey(null, it.ordinal) }
            synchronized(lock) { threadId = 0 }
        }
    }

    private fun postMessage(message: Int) {
        User32.INSTANCE.PostThreadMessage(threadId, message, WinDef.WPARAM(0), WinDef.LPARAM(0))
    }

    private companion object {
        const val UPDATE_REGISTRATIONS = 0x8000 + 1 // WM_APP + 1
    }
}

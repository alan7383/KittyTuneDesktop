package com.alananasss.kittytune.core

import androidx.compose.ui.input.key.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GlobalShortcutDispatcher {
    private val _keyEvents = MutableSharedFlow<KeyEvent>(extraBufferCapacity = 64)
    val keyEvents = _keyEvents.asSharedFlow()

    /**
     * Gets a shortcut key before the shortcuts do, and keeps it when it returns true. The full player's sleep
     * screen uses it to make Space wake the screen instead of pausing the music.
     */
    @Volatile
    var interceptor: ((KeyEvent) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean {
        if (interceptor?.invoke(event) == true) return true
        return _keyEvents.tryEmit(event)
    }
}

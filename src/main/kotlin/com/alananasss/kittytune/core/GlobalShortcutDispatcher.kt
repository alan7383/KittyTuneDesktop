package com.alananasss.kittytune.core

import androidx.compose.ui.input.key.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GlobalShortcutDispatcher {
    private val _keyEvents = MutableSharedFlow<KeyEvent>(extraBufferCapacity = 64)
    val keyEvents = _keyEvents.asSharedFlow()

    fun dispatch(event: KeyEvent): Boolean {
        return _keyEvents.tryEmit(event)
    }
}

package com.alananasss.kittytune.core

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Desktop replacement for android.widget.Toast: emits messages that MainScreen
 * shows through its pill snackbar (same visual as the Android app's snackbar).
 */
object Toaster {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun show(message: String) {
        messages.tryEmit(message)
    }
}

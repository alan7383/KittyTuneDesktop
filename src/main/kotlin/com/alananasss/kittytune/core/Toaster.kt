package com.alananasss.kittytune.core

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Desktop replacement for android.widget.Toast: emits messages that MainScreen
 * shows through ToastNoticeHost, above the player bar.
 */
object Toaster {
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun show(message: String) {
        messages.tryEmit(message)
    }

    /** Short "it worked" notices such as "Link copied", shown as the same pill as the app's undo bars. */
    val confirmations = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun confirm(message: String) {
        confirmations.tryEmit(message)
    }
}

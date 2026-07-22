package com.alananasss.kittytune.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Desktop replacement for androidx.activity.compose.BackHandler.
 *
 * Handlers are kept in a LIFO stack; the window dispatches Esc (and mouse "back"
 * button 4) to [DesktopBackDispatcher.onBack], which invokes the most recently
 * registered enabled handler — same semantics as Android's back dispatcher.
 */
object DesktopBackDispatcher {
    private class Entry(var enabled: Boolean, var callback: () -> Unit)

    private val stack = ArrayDeque<Any>()
    private val entries = HashMap<Any, Entry>()

    internal fun register(key: Any, enabled: Boolean, callback: () -> Unit) {
        val e = entries.getOrPut(key) { Entry(enabled, callback).also { stack.addLast(key) } }
        e.enabled = enabled
        e.callback = callback
    }

    internal fun unregister(key: Any) {
        entries.remove(key)
        stack.remove(key)
    }

    /** Returns true if a handler consumed the back event. */
    fun onBack(): Boolean {
        for (key in stack.reversed()) {
            val e = entries[key] ?: continue
            if (e.enabled) {
                e.callback()
                return true
            }
        }
        return false
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val key = remember { Any() }
    DesktopBackDispatcher.register(key, enabled) { currentOnBack() }
    DisposableEffect(key) {
        onDispose { DesktopBackDispatcher.unregister(key) }
    }
}

package com.alananasss.kittytune.core

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.dp

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
        val e = entries[key]
        if (e != null) {
            val wasEnabled = e.enabled
            e.enabled = enabled
            e.callback = callback
            if (enabled && !wasEnabled) {
                stack.remove(key)
                stack.addLast(key)
            }
        } else {
            entries[key] = Entry(enabled, callback).also { stack.addLast(key) }
        }
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

@Composable
fun EscapableAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    BackHandler(onBack = onDismissRequest)
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        icon = icon,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = shape,
        containerColor = containerColor,
    )
}

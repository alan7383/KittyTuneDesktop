package com.alananasss.kittytune.core

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

object TextInputTracker {
    @Volatile
    var isTextInputFocused: Boolean = false

    fun isFocused(): Boolean {
        if (isTextInputFocused) return true
        try {
            val owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (owner != null) {
                val name = owner.javaClass.name.lowercase()
                if (owner is javax.swing.text.JTextComponent || owner is java.awt.TextComponent) return true
                if (name.contains("text") || name.contains("input") || name.contains("editor") || name.contains("webview")) return true
            }
        } catch (_: Exception) {}
        return false
    }
}

fun Modifier.trackTextInput(): Modifier = this.onFocusChanged {
    if (it.isFocused) {
        TextInputTracker.isTextInputFocused = true
    } else {
        TextInputTracker.isTextInputFocused = false
    }
}

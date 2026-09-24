package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.hypot

/**
 * Two ways out of a search field, for the report that there was none (issue #33).
 *
 * "When you click on any search, you need to add that if you press lmb from scratch or esc, then the
 * search stops and shuts down — because now, if you click in many places, for example in the track
 * search, you will be able to close it only after returning from the tabs."
 *
 * He is describing a field that traps you: it opens, it takes the focus, and nothing about clicking
 * elsewhere or pressing the key everybody presses gets you back out. The only exit was to leave the
 * screen and come back, which is not an exit, it is a workaround the user had to find.
 *
 * These are deliberately two modifiers rather than one, because the two gestures do not always mean the
 * same thing. A field that only exists while it is being used — the library's, which is an icon until
 * you press it — should close on both. A field that is always on the bar cannot "close", so clicking
 * away from it means only "stop typing", while Escape means "and take the search with you". One
 * modifier with a flag would hide that difference behind a parameter; two make each call site say which
 * it wants.
 */
@Composable
fun Modifier.escapeDismisses(onDismiss: () -> Unit): Modifier = this.onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
        onDismiss()
        true
    } else {
        false
    }
}

/**
 * Closes a search field once it loses the focus — a click anywhere else, in other words.
 *
 * Guarded on having held the focus first. Without that guard the very first composition closes the
 * field: it is composed unfocused, the effect that focuses it has not run yet, and an unguarded
 * handler reads that one frame as the user clicking away.
 *
 * @see escapeDismisses
 */
@Composable
fun Modifier.focusLossDismisses(onDismiss: () -> Unit): Modifier {
    var hadFocus by remember { mutableStateOf(false) }
    return this.onFocusChanged { state ->
        if (state.isFocused) {
            hadFocus = true
        } else if (hadFocus) {
            hadFocus = false
            onDismiss()
        }
    }
}

/**
 * Detects an empty click (primary mouse button press and release in an unconsumed, non-interactive area)
 * anywhere in the layout subtree, and clears focus via [focusManager].
 *
 * In Compose Desktop, clicking outside of focusable components on a Box, Column, Row, Surface, or empty
 * list space does not clear focus by default. This modifier intercepts unconsumed pointer releases on
 * [PointerEventPass.Final], after all interactive children (buttons, text fields, clickable items) have
 * had a chance to consume their clicks.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.clearFocusOnEmptyClick(focusManager: FocusManager): Modifier = this.pointerInput(focusManager) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            if (event.type == PointerEventType.Press) {
                if (event.button != null && event.button != PointerButton.Primary) continue
                if (event.changes.any { it.isConsumed }) continue

                val downPos = event.changes.firstOrNull()?.position ?: continue
                var dragged = false

                while (true) {
                    val nextEvent = awaitPointerEvent(PointerEventPass.Final)
                    if (nextEvent.type == PointerEventType.Move) {
                        val currentPos = nextEvent.changes.firstOrNull()?.position
                        if (currentPos != null && hypot(currentPos.x - downPos.x, currentPos.y - downPos.y) > 12f) {
                            dragged = true
                        }
                    } else if (nextEvent.type == PointerEventType.Release) {
                        val change = nextEvent.changes.firstOrNull()
                        val unconsumed = nextEvent.changes.all { !it.isConsumed }
                        if (!dragged && change != null && unconsumed && (nextEvent.button == null || nextEvent.button == PointerButton.Primary)) {
                            focusManager.clearFocus()
                        }
                        break
                    } else if (nextEvent.changes.none { it.pressed }) {
                        break
                    }
                }
            }
        }
    }
}

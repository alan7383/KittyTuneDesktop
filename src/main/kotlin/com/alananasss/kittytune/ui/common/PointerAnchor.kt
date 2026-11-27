package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Where the pointer last pressed in the main window, for menus that open where they were asked for rather than in
 * the middle of the window (issue #66). Watched on the way down, so no press is taken from anything.
 */
object PointerAnchor {
    /** In the window's own pixels. */
    val lastPress = mutableStateOf(Offset.Zero)
}

/** Notes every press in this layout into [PointerAnchor]; put on the window's root. */
fun Modifier.notePointerPresses(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) {
                event.changes.firstOrNull()?.let { PointerAnchor.lastPress.value = it.position }
            }
        }
    }
}

/**
 * Opens a popup with its corner at [anchor] (pixels from the popup's parent), going up or left instead when the popup would
 * leave the window there.
 */
class AtPointPositionProvider(
    private val anchor: IntOffset,
    private val onSideResolved: ((opensLeft: Boolean) -> Unit)? = null,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val ax = anchorBounds.left + anchor.x
        val ay = anchorBounds.top + anchor.y
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val spaceRight = (windowSize.width - ax).coerceAtLeast(0)
        // Prefer opening rightward (clamped within the right window edge) whenever reasonable
        // room exists on the right; only flip entirely to the left when clicked near the far
        // right edge so middle-right controls like FullPlayer's "..." in Text-Left don't jump
        // over the lyrics pane.
        val opensLeft = ax + popupContentSize.width > windowSize.width &&
            spaceRight < (popupContentSize.width * 0.45f) &&
            ax >= popupContentSize.width / 2
        val rawX = if (opensLeft) ax - popupContentSize.width else ax
        val x = rawX.coerceIn(0, maxX)
        onSideResolved?.invoke(x + popupContentSize.width / 2 < ax)
        val y = if (ay + popupContentSize.height <= windowSize.height) ay else ay - popupContentSize.height
        return IntOffset(
            x,
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

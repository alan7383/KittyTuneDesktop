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
class AtPointPositionProvider(private val anchor: IntOffset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val ax = anchorBounds.left + anchor.x
        val ay = anchorBounds.top + anchor.y
        val x = if (ax + popupContentSize.width <= windowSize.width) ax else ax - popupContentSize.width
        val y = if (ay + popupContentSize.height <= windowSize.height) ay else ay - popupContentSize.height
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

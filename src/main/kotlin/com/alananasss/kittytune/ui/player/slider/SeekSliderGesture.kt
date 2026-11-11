package com.alananasss.kittytune.ui.player.slider

import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Maps a press and its drag to the same track coordinates, with one completion on release. */
@Composable
internal fun Modifier.seekSliderGesture(
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    edgePaddingPx: Float = 0f,
    onInteracting: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    onValueChangeCancelled: (() -> Unit)?,
): Modifier {
    val updateInteraction by rememberUpdatedState(onInteracting)
    val updateValue by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val cancel by rememberUpdatedState(onValueChangeCancelled)
    val start = valueRange.start
    val end = valueRange.endInclusive

    return pointerInput(enabled, start, end, edgePaddingPx) {
        if (!enabled || end <= start) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            val padding = edgePaddingPx.coerceIn(0f, size.width / 2f)
            val width = size.width - 2f * padding
            if (width <= 0f) return@awaitEachGesture
            fun valueAt(x: Float): Float = start + ((x - padding) / width).coerceIn(0f, 1f) * (end - start)

            var value = valueAt(down.position.x)
            var released = false
            down.consume()
            try {
                updateInteraction(true)
                updateValue(value)
                var change = down
                while (change.pressed) {
                    change = awaitDragOrCancellation(change.id) ?: return@awaitEachGesture
                    change.consume()
                    val next = valueAt(change.position.x)
                    if (next != value) {
                        value = next
                        updateValue(value)
                    }
                }
                released = true
                finish?.invoke()
            } finally {
                updateInteraction(false)
                if (!released) cancel?.invoke()
            }
        }
    }
}

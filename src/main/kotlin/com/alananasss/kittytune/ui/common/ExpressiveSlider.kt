package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Slider as Material3Slider

/**
 * A [androidx.compose.material3.Slider] that behaves at its extremities the way
 * [androidx.compose.material3.VerticalSlider] already does.
 *
 * Material 3 ships two track treatments. The one `Slider` picks by default drops the leftover
 * track stub the moment it gets shorter than its own corner radius, so the last few pixels of
 * travel make the pill pop out of existence instead of closing. The expressive overload —
 * the one `VerticalSlider` defaults to — keeps drawing it and lets the corner radius shrink with
 * it, so the stub squeezes shut and reopens smoothly. Only the second one reads as motion.
 *
 * The default track uses the expressive overload to shrink its corners near the ends. A custom
 * thumb or track can use the same controlled slider state and input handling.
 *
 * Note that a stepped slider (`steps > 0`) opts itself back out inside the library, since ticks
 * need the track to end on a full corner. That is upstream behaviour, not a limitation here.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = {
        SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled)
    },
    track: @Composable (SliderState) -> Unit = { state ->
        SliderDefaults.Track(
            sliderState = state,
            trackCornerSize = Dp.Unspecified,
            drawStopIndicator = null,
            enabled = enabled,
            colors = colors,
        )
    },
) {
    val state = remember(steps, valueRange.start, valueRange.endInclusive) {
        SliderState(valueRange.start, steps, valueRange)
    }
    if (state.value != value) state.value = value

    Material3Slider(
        state = state,
        modifier = modifier,
        enabled = enabled,
        onValueChange = {
            // Material compares each input against state.value. Keep it current even when two
            // clicks land before the owner has recomposed with the first click's value.
            state.value = it
            onValueChange(it)
        },
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        thumb = thumb,
        track = track,
    )
}

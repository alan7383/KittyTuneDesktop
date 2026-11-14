package com.alananasss.kittytune.ui.player.slider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import com.alananasss.kittytune.core.LocalWindowSeen
import com.alananasss.kittytune.ui.common.LocalAmbientClock
import com.alananasss.kittytune.ui.common.withAmbientFrameNanos

/** Keeps the last drawn phase when the slider stops receiving frames. */
@Composable
internal fun rememberSliderWavePhase(moving: Boolean, speedPx: Float, wavelengthPx: Float): FloatState {
    val phase = remember { mutableFloatStateOf(0f) }
    val isSeen = LocalWindowSeen.current
    val clock = LocalAmbientClock.current
    LaunchedEffect(moving, isSeen, speedPx, wavelengthPx, clock) {
        if (!moving || !isSeen) return@LaunchedEffect
        var previous = withAmbientFrameNanos(clock) { it }
        while (true) {
            withAmbientFrameNanos(clock) { now ->
                // A stalled renderer can miss the visibility change. Skip the gap in that case too.
                val elapsed = now - previous
                if (elapsed in 1L..100_000_000L) {
                    phase.floatValue = (phase.floatValue + elapsed / 1_000_000_000f * speedPx) % wavelengthPx
                }
                previous = now
            }
        }
    }
    return phase
}

/**
 * The phase of every moving wave in the app, from one clock.
 *
 * Each wavy track used to advance its own phase from its own start, so the seek bar and the volume track
 * drifted apart. Reading the phase from the time instead keeps any two waves with the same speed in step.
 */
internal fun wavePhasePx(speedPx: Float, wavelengthPx: Float): Float {
    if (wavelengthPx <= 0f) return 0f
    val seconds = System.nanoTime() / 1_000_000_000.0
    return ((seconds * speedPx) % wavelengthPx).toFloat()
}

package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.alananasss.kittytune.core.LocalWindowSeen
import kotlin.math.floor

/**
 * An endless value on the shared ambient pulse, for a decoration that moves all the time.
 *
 * This is what `rememberInfiniteTransition` should have been here. That one holds the window's own frame clock for
 * as long as it is composed, read or not, visible or not: one pulsing badge in a screen nobody is looking at still
 * had Compose re-recording and redrawing the whole window at the display's rate. With this the value is sampled on
 * the [AmbientClock] — about thirty frames a second, shared with every other decoration — and while the window is
 * hidden the effect is dropped altogether, so the value holds still and no frame is asked for.
 *
 * The value restarts from [from] each time the window comes back, which is what a decoration wants: nobody saw the
 * time that passed.
 *
 * @param periodMs one run from [from] to [to].
 * @param repeatMode [RepeatMode.Restart] jumps back to [from] each run, [RepeatMode.Reverse] goes back down the next.
 * @param slow half the ambient rate, for drifts where a frame every thirtieth of a second is indistinguishable.
 */
@Composable
fun rememberAmbientLoop(
    periodMs: Int,
    from: Float = 0f,
    to: Float = 1f,
    repeatMode: RepeatMode = RepeatMode.Restart,
    easing: Easing = LinearEasing,
    slow: Boolean = false,
): State<Float> {
    val value = remember { mutableFloatStateOf(from) }
    val clock = LocalAmbientClock.current
    val seen = LocalWindowSeen.current
    val reverse = repeatMode == RepeatMode.Reverse
    // Not a restart key: an easing built inline would otherwise restart the loop on every recomposition.
    val currentEasing = rememberUpdatedState(easing)

    LaunchedEffect(periodMs, from, to, reverse, slow, clock, seen) {
        if (!seen || periodMs <= 0) {
            value.floatValue = if (periodMs <= 0) to else from
            return@LaunchedEffect
        }
        val startedAt = if (slow) withSlowAmbientFrameNanos(clock) { it } else withAmbientFrameNanos(clock) { it }
        value.floatValue = from
        while (true) {
            val now = if (slow) withSlowAmbientFrameNanos(clock) { it } else withAmbientFrameNanos(clock) { it }
            value.floatValue = ambientLoopValue(
                elapsedMs = (now - startedAt) / 1_000_000.0,
                periodMs = periodMs,
                from = from,
                to = to,
                reverse = reverse,
                easing = currentEasing.value,
            )
        }
    }
    return value
}

/**
 * Where an endless run that started [elapsedMs] ago is, as a value between [from] and [to].
 *
 * Pure, so that what the decorations draw can be pinned by a test: a [reverse] run mirrors every other period, the
 * way `infiniteRepeatable` does, and the easing is applied to the progress within a period, so the value starts
 * exactly at [from] and reaches exactly [to] at the end of one.
 */
internal fun ambientLoopValue(
    elapsedMs: Double,
    periodMs: Int,
    from: Float,
    to: Float,
    reverse: Boolean,
    easing: Easing,
): Float {
    if (periodMs <= 0) return to
    val cycle = elapsedMs / periodMs
    val iteration = floor(cycle).toLong()
    val fraction = (cycle - iteration).toFloat()
    val progress = if (reverse && iteration % 2L != 0L) 1f - fraction else fraction
    return from + (to - from) * easing.transform(progress)
}

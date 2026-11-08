package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.LocalWindowSeen

/**
 * Three bars bouncing out of step, the usual "this one is playing" mark; held still at different heights when it
 * is paused. [color] is the bars' colour, since they sit on covers, buttons and plain surfaces alike.
 *
 * The bounce only runs while the window is seen and music is playing. It used to be an infinite transition composed
 * whatever the state: a transition asks for frames for as long as it holds animations, read or not, so a paused
 * track's bars kept the window redrawing at the display's rate for as long as they were on screen. Now nothing is
 * driven while paused, and while playing the bounce follows the shared ambient pulse (see AmbientClock).
 */
@Composable
fun PlayingBars(isPlaying: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    val shouldAnimate = isPlaying && LocalWindowSeen.current
    val heights = remember { List(BAR_PERIODS_MS.size) { i -> mutableFloatStateOf(PAUSED_BAR_HEIGHTS[i]) } }
    val ambientClock = LocalAmbientClock.current
    LaunchedEffect(shouldAnimate, ambientClock) {
        if (!shouldAnimate) {
            heights.forEachIndexed { i, h -> h.floatValue = PAUSED_BAR_HEIGHTS[i] }
            return@LaunchedEffect
        }
        val start = withAmbientFrameNanos(ambientClock) { it }
        while (true) {
            withAmbientFrameNanos(ambientClock) { now ->
                val elapsedMs = (now - start) / 1_000_000.0
                BAR_PERIODS_MS.forEachIndexed { i, period -> heights[i].floatValue = bounceAt(elapsedMs, period) }
            }
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { height ->
            Box(
                Modifier
                    .weight(1f)
                    // Read in layout through the lambda, so a bounce re-measures the bar rather than recomposing the row.
                    .layout { measurable, constraints ->
                        if (!constraints.hasBoundedHeight) {
                            val p = measurable.measure(constraints)
                            return@layout layout(p.width, p.height) { p.place(0, 0) }
                        }
                        val h = (constraints.maxHeight * height.floatValue).toInt().coerceIn(0, constraints.maxHeight)
                        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
                        layout(placeable.width, constraints.maxHeight) { placeable.place(0, constraints.maxHeight - h) }
                    }
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

/**
 * The height the old `infiniteRepeatable(tween(period), RepeatMode.Reverse)` from 0.25 to 1 had [elapsedMs] in: up over
 * one period, down over the next, with tween's default FastOutSlowIn easing each way.
 */
internal fun bounceAt(elapsedMs: Double, periodMs: Int): Float {
    val cycle = elapsedMs / periodMs
    val iteration = kotlin.math.floor(cycle).toLong()
    val fraction = (cycle - iteration).toFloat()
    val forward = if (iteration % 2L == 0L) fraction else 1f - fraction
    return 0.25f + 0.75f * FastOutSlowInEasing.transform(forward)
}

private val BAR_PERIODS_MS = listOf(420, 560, 360)
private val PAUSED_BAR_HEIGHTS = listOf(0.4f, 0.7f, 0.5f)

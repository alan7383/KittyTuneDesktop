package com.alananasss.kittytune.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The wheel over a progress bar, moving the playhead the way the wheel over the volume moves the volume.
 *
 * Each notch used to seek the player at once, and the player answers a seek late and in steps. The bar was
 * then drawn from those answers: it slid towards the new spot, was dragged back by a report that had not caught
 * up, and slid again, so a quick spin back and forth looked braked and jerky while the volume, which simply takes
 * the value, was smooth (issue #66).
 *
 * Now the bar belongs to the wheel for as long as the spin lasts. Every notch moves a target, the bar follows
 * it on a spring that keeps its speed from one notch to the next, and the player is asked to seek once, a
 * moment after the last notch. The bar stays on the target until the player's own position has arrived there,
 * so there is no hand-back to see.
 */
internal class WheelSeek(
    private val scope: CoroutineScope,
    /** What is drawn now: where the first notch of a spin starts from, so the bar does not jump. */
    private val drawnMs: () -> Long,
    /** What the player reports, to know when it has arrived. */
    private val reportedMs: () -> Long,
    private val durationMs: () -> Long,
    private val stepSeconds: () -> Float,
    /** Called once per spin, with where it ended. */
    private val commit: (Long) -> Unit,
) {
    private var target by mutableStateOf<Long?>(null)
    private var shown by mutableFloatStateOf(0f)
    private val glide = Animatable(0f)
    private var glideJob: Job? = null
    private var commitJob: Job? = null

    /** Where to draw the playhead while the wheel owns it; null when it does not. */
    val shownMs: Long? get() = target?.let { shown.toLong() }

    /** True from the first notch until the player has caught up with the last one. */
    val isActive: Boolean get() = target != null

    /** One wheel event: [notches] is the scroll delta, positive for down, which goes back. */
    fun onNotch(notches: Float) {
        val total = durationMs()
        if (total <= 0L || notches == 0f) return
        val wasIdle = target == null
        val base = target ?: drawnMs()
        val next = (base - (notches * stepSeconds() * 1000f).toLong()).coerceIn(0L, total)
        if (wasIdle) shown = base.toFloat()
        target = next

        glideJob?.cancel()
        glideJob = scope.launch {
            if (wasIdle) glide.snapTo(base.toFloat())
            glide.animateTo(next.toFloat(), WHEEL_SPRING) { shown = value }
        }

        commitJob?.cancel()
        commitJob = scope.launch {
            delay(SETTLE_MS)
            commit(next)
            val deadline = System.nanoTime() + CATCH_UP_LIMIT_NANOS
            while (abs(reportedMs() - next) > CAUGHT_UP_MS && System.nanoTime() < deadline) delay(CATCH_UP_POLL_MS)
            target = null
        }
    }

    /** A drag or click takes the bar over. */
    fun cancel() {
        glideJob?.cancel()
        commitJob?.cancel()
        target = null
    }

    private companion object {
        /** Quiet time after the last notch before the player is asked to seek. */
        const val SETTLE_MS = 180L

        /** The playhead is this close to the target: handed back. */
        const val CAUGHT_UP_MS = 90L
        const val CATCH_UP_LIMIT_NANOS = 1_500_000_000L
        const val CATCH_UP_POLL_MS = 40L

        val WHEEL_SPRING = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f)
    }
}

@Composable
internal fun rememberWheelSeek(
    drawnMs: () -> Long,
    reportedMs: () -> Long,
    durationMs: () -> Long,
    stepSeconds: () -> Float,
    commit: (Long) -> Unit,
): WheelSeek {
    val scope = rememberCoroutineScope()
    val drawn by rememberUpdatedState(drawnMs)
    val reported by rememberUpdatedState(reportedMs)
    val duration by rememberUpdatedState(durationMs)
    val step by rememberUpdatedState(stepSeconds)
    val onCommit by rememberUpdatedState(commit)
    return remember(scope) {
        WheelSeek(scope, { drawn() }, { reported() }, { duration() }, { step() }, { onCommit(it) })
    }
}

/**
 * Feeds the wheel over the bar to [wheel].
 *
 * Consumed, so the wheel does not also scroll whatever the player bar happens to be sitting on. Up goes
 * forward, matching the volume control right next to it, where up is louder. The step is a setting because five
 * seconds is right for checking a lyric and useless for finding your way around a two-hour set.
 */
internal fun Modifier.seekWheel(wheel: WheelSeek): Modifier = pointerInput(wheel) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val notches = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
            if (notches == 0f) continue
            wheel.onNotch(notches)
            event.changes.forEach { it.consume() }
        }
    }
}

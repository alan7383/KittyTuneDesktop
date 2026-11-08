package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * One shared, slower pulse for the decorative things that move all the time: the waves under the progress bar and on
 * the "My Wave" card, the playhead between seeks, the "playing" bars.
 *
 * ## Why
 *
 * Each of those used to await every frame, so the window was redrawn at the display's rate for as long as music
 * played — the player bar is always on screen. On a 240 Hz monitor that is 240 full redraws a second, and every one
 * of them builds fresh Skia paths and shaders (native objects only freed when a collection gets round to their Java
 * wrappers), pushes its geometry through Direct3D's upload heaps, and gives G1 an allocation rate it sizes the heap
 * for. Measured on the home screen with only the "My Wave" card moving: 715 MB, against 483 MB with it still — a
 * quarter of a gigabyte for a slow drift (issue #33).
 *
 * Things that drift this slowly look the same at 60 frames a second as at 240, so they are sampled here at about
 * that rate, and all of them on the *same* frames: two animations each limited to 60 but out of step would still ask
 * for 120. Anything that answers the user — scrolling, hovering, a seek's glide — keeps awaiting the window's own
 * frames, and on a 60 Hz display nothing changes at all.
 *
 * ## How
 *
 * A [BroadcastFrameClock] the decorative animations await instead of the window's clock. While any of them is
 * waiting, a ticker sleeps [TICK_DELAY_MS], waits for the window's next real frame and delivers that frame's time to
 * all of them inside it, so their state changes land in the frame being drawn rather than asking for another one.
 * With nobody waiting the ticker parks, and asks for nothing.
 */
class AmbientClock internal constructor() : MonotonicFrameClock {
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private val broadcast = BroadcastFrameClock(onNewAwaiters = { wakeUp.trySend(Unit) })

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        broadcast.withFrameNanos(onFrame)

    /** Runs for the life of the window; see the class comment. */
    internal suspend fun drive() {
        while (true) {
            if (!broadcast.hasAwaiters) wakeUp.receive()
            delay(TICK_DELAY_MS)
            // The window's own frame clock, from the composition this runs in.
            androidx.compose.runtime.withFrameNanos { now -> broadcast.sendFrame(now) }
        }
    }

    internal companion object {
        /**
         * Sleep before waiting for the next real frame. Fourteen milliseconds plus the wait for vsync comes to about
         * sixty frames a second at 144 or 240 Hz (twelve measured 77 at 240 Hz), and to every frame at 60 Hz.
         */
        const val TICK_DELAY_MS = 14L
    }
}

/** The window's [AmbientClock], or null outside a [ProvideAmbientClock] (then callers use the window's frames). */
val LocalAmbientClock: ProvidableCompositionLocal<AmbientClock?> = staticCompositionLocalOf { null }

/** Gives the window under [content] its [AmbientClock]. One per window: the ticker follows that window's frames. */
@Composable
fun ProvideAmbientClock(content: @Composable () -> Unit) {
    val clock = remember { AmbientClock() }
    LaunchedEffect(clock) { clock.drive() }
    androidx.compose.runtime.CompositionLocalProvider(LocalAmbientClock provides clock, content = content)
}

/**
 * Awaits the next ambient frame from [clock], or the window's next frame when there is none, and runs [onFrame] in it.
 */
suspend fun <R> withAmbientFrameNanos(clock: AmbientClock?, onFrame: (frameTimeNanos: Long) -> R): R =
    clock?.withFrameNanos(onFrame) ?: withFrameNanos(onFrame)

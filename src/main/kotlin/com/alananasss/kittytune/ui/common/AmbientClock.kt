package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import com.alananasss.kittytune.core.LocalWindowSeen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * One shared, slower pulse for the decorative things that move all the time: the waves under the progress bar and on
 * the "My Wave" card, the playhead between seeks, the "playing" bars, the drifting colours of the liked songs.
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
 * Things that drift this slowly look the same at 30 frames a second as at 240, so they are sampled here at about
 * that rate, and all of them on the *same* frames: two animations each limited to 30 but out of step would still ask
 * for 60. Anything that answers the user — scrolling, hovering, a seek's glide — keeps awaiting the window's own
 * frames.
 *
 * ## Hidden windows
 *
 * A minimised or covered window cannot be seen, and ticking for it is pure waste: Compose re-records and redraws the
 * whole window for every frame an animation asks for. [setWindowSeen] parks the ticker, so a hidden window asks the
 * window's clock for nothing at all and its CPU/GPU falls to that of the audio decoder. The park is handled here
 * rather than by each caller so that a decoration added later cannot forget it.
 *
 * ## How
 *
 * A [BroadcastFrameClock] the decorative animations await instead of the window's clock. While any of them is
 * waiting, a ticker sleeps [TICK_DELAY_MS] and hands all of them one timestamp, so their state changes land in the
 * next frame Compose draws rather than each asking for a frame of its own. With nobody waiting, or with the window
 * hidden, the ticker parks, and asks for nothing. A second clock, fed every other tick, serves decorations so slow
 * that half the frames are invisible ([withSlowFrameNanos]).
 */
class AmbientClock internal constructor() : MonotonicFrameClock {
    private val wakeUp = Channel<Unit>(Channel.CONFLATED)
    private val broadcast = BroadcastFrameClock(onNewAwaiters = { wakeUp.trySend(Unit) })
    private val slowBroadcast = BroadcastFrameClock(onNewAwaiters = { wakeUp.trySend(Unit) })

    @Volatile
    private var windowSeen = true
    private var slowTick = false

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        broadcast.withFrameNanos(onFrame)

    /** Like [withFrameNanos], but at half the ambient rate, for drifts that gain nothing from the other half. */
    internal suspend fun <R> withSlowFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        slowBroadcast.withFrameNanos(onFrame)

    /** Parks the ticker while nobody can see the window; see the class comment. Cheap and idempotent. */
    fun setWindowSeen(seen: Boolean) {
        if (windowSeen == seen) return
        windowSeen = seen
        wakeUp.trySend(Unit)
    }

    /** Runs for the life of the window; see the class comment. */
    internal suspend fun drive() {
        while (true) {
            while (!windowSeen || (!broadcast.hasAwaiters && !slowBroadcast.hasAwaiters)) {
                wakeUp.receive()
            }
            delay(TICK_DELAY_MS)
            // Visibility can change during the sleep: no frame is asked for a window nobody is looking at.
            if (!windowSeen) continue
            slowTick = !slowTick
            val deliverSlow = slowTick
            // Delivered from this coroutine, not from inside the window's next frame. Awaiting a frame here put
            // the state change after that frame had been recorded, so Compose drew a second frame a couple of
            // milliseconds later: PresentMon showed two presents per tick (2 ms then 28 ms), and the window kept
            // being presented at nearly the old rate however long the tick was. A plain delay lets Compose
            // schedule the single frame the change actually needs.
            val now = System.nanoTime()
            broadcast.sendFrame(now)
            if (deliverSlow) slowBroadcast.sendFrame(now)
        }
    }

    internal companion object {
        /**
         * Sleep between two ambient frames. Measured through PresentMon on a 240 Hz display, 30 ms comes to 31 ms
         * per presented frame, about thirty-two frames a second: half the sixty the old fourteen-millisecond tick
         * produced at this refresh rate, for half the GPU time and half the redraws.
         */
        const val TICK_DELAY_MS = 30L
    }
}

/** The window's [AmbientClock], or null outside a [ProvideAmbientClock] (then callers use the window's frames). */
val LocalAmbientClock: ProvidableCompositionLocal<AmbientClock?> = staticCompositionLocalOf { null }

/** Gives the window under [content] its [AmbientClock]. One per window: the ticker follows that window's frames. */
@Composable
fun ProvideAmbientClock(content: @Composable () -> Unit) {
    val clock = remember { AmbientClock() }
    val seen = LocalWindowSeen.current
    // Before the ticker's first frame where possible; the ticker re-reads the flag on every lap anyway.
    SideEffect { clock.setWindowSeen(seen) }
    LaunchedEffect(clock) { clock.drive() }
    androidx.compose.runtime.CompositionLocalProvider(LocalAmbientClock provides clock, content = content)
}

/**
 * Awaits the next ambient frame from [clock], or the window's next frame when there is none, and runs [onFrame] in it.
 */
suspend fun <R> withAmbientFrameNanos(clock: AmbientClock?, onFrame: (frameTimeNanos: Long) -> R): R =
    clock?.withFrameNanos(onFrame) ?: withFrameNanos(onFrame)

/** [withAmbientFrameNanos] at half the rate, for decorations too slow to show the difference. */
suspend fun <R> withSlowAmbientFrameNanos(clock: AmbientClock?, onFrame: (frameTimeNanos: Long) -> R): R =
    clock?.withSlowFrameNanos(onFrame) ?: withFrameNanos(onFrame)

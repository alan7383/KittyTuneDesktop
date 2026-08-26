package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * How the lyrics views scroll, shared by the full screen and the right-hand panel (issue #33).
 *
 * The two used to be unrelated: the full screen followed the song and the panel did not move at
 * all, so switching between them changed how reading worked. One set of numbers and one follow
 * loop means a change to either shows up in both.
 */
internal object LyricsScrolling {

    /**
     * Auto-scroll rate for lyrics with no timings, at speed 1×, in **lines** per second.
     *
     * Lines, not dp. It was 18 dp per second, and dp is the wrong unit for reading: the full screen draws
     * plain text at the size the reader chose — 42 sp is not unusual, so a line is about 59 dp — while the
     * side panel draws it at `bodyMedium`, about 20 dp. The same 18 dp/s therefore moved a third of a line
     * per second in one view and nearly a whole line in the other, so "1.5×" meant two different speeds
     * depending on where you were reading. Reported as the panel's text moving too fast (issue #33).
     *
     * Expressed per line, a line takes the same time to pass in both, whatever either one's typography.
     * The value is what the full screen already did at a 42 sp setting, since that is the pace being
     * compared against.
     */
    const val PLAIN_BASE_LINES_PER_SEC = 0.3f

    /**
     * @param lineHeightDp the height of one line in the view asking, which is what makes the pace
     *   comparable between views rather than merely equal in dp.
     * @return how far to scroll, in dp, for [elapsedSec] at [speed].
     */
    fun plainScrollStepDp(lineHeightDp: Float, speed: Float, elapsedSec: Float): Float =
        lineHeightDp * PLAIN_BASE_LINES_PER_SEC * speed * elapsedSec

    /** How long a manual scroll holds the automatic one off. */
    const val PLAIN_PAUSE_MS = 2_500L

    /**
     * How long a synced view leaves the reader alone after they scroll by hand before it goes back
     * to following the track. A gesture ends in well under a second, so resuming on that alone made
     * reading ahead impossible (issue #33).
     */
    const val MANUAL_GRACE_MS = 5_000L
}

/**
 * Keeps [listState] parked on [activeIndex] as the song moves through the lines.
 *
 * ## The bug this had
 *
 * Following was driven off [LazyListState.isScrollInProgress], read as "the reader took over". But that flag
 * is set by *any* scroll, including the automatic one this function performs — and the timestamp it wrote
 * was a key of the effect doing the scrolling. So each automatic scroll flipped the flag, the flag moved the
 * timestamp, the timestamp re-keyed the effect, and re-keying it cancelled the very animation that had just
 * set the flag. One frame of movement, then a five-second lockout, then the same again: the lyrics crawled
 * instead of following, in the panel and on the full screen alike (issue #33).
 *
 * The fix is to stop inferring intent from a flag that cannot distinguish who caused it. A drag arrives
 * through [LazyListState.interactionSource], which programmatic scrolls never touch, and a mouse wheel is
 * caught by the flag guarded against our own animation. Neither signal is a key of the scrolling effect any
 * more, so nothing can cancel itself.
 *
 * A manual scroll still buys [LyricsScrolling.MANUAL_GRACE_MS] of being left alone, and the wait is served
 * rather than skipped — a reader who scrolled away and stopped is brought back to where the song is, instead
 * of being left behind until the next line happens to start.
 *
 * @param activeIndex the line to keep in view, or a negative value before the first line starts.
 * @param anchorPx how far below the top of the viewport the active line should settle. Zero puts it at the
 *   top of the content area, which is what a view with a large top inset already wants; a short view passes
 *   a real anchor instead of padding a third of itself away.
 */
@Composable
internal fun FollowActiveLine(listState: LazyListState, activeIndex: Int, anchorPx: Int = 0) {
    var lastManualScrollMs by remember { mutableStateOf(0L) }

    /** True for exactly as long as the scroll below is ours, so the flag cannot be misattributed. */
    var autoScrolling by remember { mutableStateOf(false) }

    // Drags and presses only — a programmatic scroll emits nothing here, which is the whole point.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start || interaction is PressInteraction.Press) {
                lastManualScrollMs = System.currentTimeMillis()
            }
        }
    }

    // The mouse wheel is not a drag and does not reach the interaction source, so it is caught here —
    // guarded, because this is the flag our own animation also sets.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !autoScrolling) {
            lastManualScrollMs = System.currentTimeMillis()
        }
    }

    // Keyed on the line alone. Keying it on the manual timestamp is what made it cancel itself.
    LaunchedEffect(activeIndex, anchorPx) {
        if (activeIndex < 0) return@LaunchedEffect

        // Re-read each time round: a second scroll during the wait extends it rather than being ignored.
        while (true) {
            val remaining =
                LyricsScrolling.MANUAL_GRACE_MS - (System.currentTimeMillis() - lastManualScrollMs)
            if (remaining <= 0) break
            delay(remaining)
        }

        autoScrolling = true
        try {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = -anchorPx)
        } finally {
            autoScrolling = false
        }
    }
}

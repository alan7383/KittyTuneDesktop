package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

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
     * Where the untimed text belongs for a given playback position.
     *
     * ## Why this is a function of the position and not a running total
     *
     * It used to be an accumulator: a loop added a frame's worth of movement on every frame the view
     * was composed. That works only while somebody is looking at it, which is what produced all four
     * of the complaints in issue #33 at once — "make it so that scrolling starts when the track is
     * turned on and remembers where it left off, because if you restart, it starts from the
     * beginning. I think it should keep going even when the text isn't open, and it should be visible
     * right away when you turn it on."
     *
     * An accumulator can be made to answer those, but only by adding state: an offset persisted per
     * track, and a ticker that keeps running with nothing on screen. Expressed as a function of the
     * position instead, all four stop being features. The position is already restored at startup, so
     * the text resumes where it was; nothing runs while the panel is shut, so nothing can drift; and
     * the first frame after it opens is already in the right place, so there is nothing to catch up.
     *
     * @return the line to put at the top of the view, and how far into that line, as a fraction of
     *   its height.
     */
    fun plainScrollTarget(positionMs: Float, speed: Float, lineCount: Int): PlainScrollTarget {
        if (lineCount <= 0) return PlainScrollTarget(0, 0f)
        val lines = PLAIN_BASE_LINES_PER_SEC * speed * (positionMs / 1000f)
        val whole = floor(lines)
        val index = whole.toInt().coerceIn(0, lineCount - 1)
        // Zero once the last line is reached, so the view settles instead of straining past the end.
        val fraction = if (index == lineCount - 1) 0f else (lines - whole).coerceIn(0f, 1f)
        return PlainScrollTarget(index, fraction)
    }

    /** @see plainScrollTarget */
    data class PlainScrollTarget(val index: Int, val fraction: Float)

    /**
     * How far one notch of the wheel moves, in pixels, given the height of a line.
     *
     * The wheel delta a desktop mouse reports is a notch count, not a distance, so the distance is
     * ours to choose — which is what makes it adjustable at all (issue #33).
     */
    fun wheelStepPx(notches: Float, lines: Float, lineHeightPx: Float): Float =
        notches * lines * lineHeightPx

    /** How long a manual scroll holds the automatic one off. */
    const val PLAIN_PAUSE_MS = 5_000L

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


/**
 * Drives a list of untimed lyrics from the playback position, and steps aside when the reader scrolls.
 *
 * Shared by the full screen and the side panel so the two cannot drift apart, which is how they came
 * to disagree about what "1.5×" meant in the first place (issue #33).
 *
 * The loop does no accumulating of its own: every frame it asks
 * [LyricsScrolling.plainScrollTarget] where the text belongs *now* and puts it there. That is what
 * makes it resume correctly after a restart and be in the right place the instant the panel opens —
 * and it also means a paused track costs nothing, since an unchanged target skips the scroll
 * entirely.
 *
 * @param positionMs reads the player's reported position. A lambda rather than a value so the loop
 *   sees the current one without the caller recomposing every frame to hand it over.
 */
@Composable
internal fun FollowPlainLyrics(
    listState: LazyListState,
    enabled: Boolean,
    speed: Float,
    lineCount: Int,
    positionMs: () -> Long,
    isPlaying: () -> Boolean,
    playbackSpeed: () -> Float,
    lastManualScrollMs: () -> Long,
) {
    val position by rememberUpdatedState(positionMs)
    val playing by rememberUpdatedState(isPlaying)
    val rate by rememberUpdatedState(playbackSpeed)
    val lastManual by rememberUpdatedState(lastManualScrollMs)

    /** True for exactly as long as the scroll below is ours, so the flag cannot be misattributed. */
    var autoScrolling by remember { mutableStateOf(false) }

    /**
     * A scroll nobody told us about — which on the desktop means the scrollbar beside the text, since
     * dragging it drives the list state directly and reaches neither the wheel handler nor the
     * interaction source. Guarded against our own movement, or every frame would look like the reader
     * taking over.
     */
    var lastForeignScrollMs by remember { mutableStateOf(0L) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !autoScrolling) {
            lastForeignScrollMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(enabled, speed, lineCount) {
        if (!enabled || lineCount <= 0) return@LaunchedEffect

        // The player reports about four times a second. Interpolating between those reports here
        // rather than through a Compose state keeps the estimate off the recomposition path: a value
        // that changed every frame would redraw the whole list to move it by a pixel.
        var reported = position()
        var reportedAtMs = System.currentTimeMillis()

        var appliedIndex = -1
        var appliedOffset = Int.MIN_VALUE
        /** Set while a manual scroll holds us off, so the way back is animated rather than a snap. */
        var returningFromManual = false

        while (true) {
            withFrameNanos { }

            val fresh = position()
            if (fresh != reported) {
                reported = fresh
                reportedAtMs = System.currentTimeMillis()
            }

            val lastTouched = maxOf(lastManual(), lastForeignScrollMs)
            if (System.currentTimeMillis() - lastTouched < LyricsScrolling.PLAIN_PAUSE_MS) {
                returningFromManual = true
                continue
            }

            val elapsed = if (playing()) {
                (System.currentTimeMillis() - reportedAtMs).coerceAtMost(MAX_EXTRAPOLATION_MS)
            } else {
                0L
            }
            val estimated = reported + elapsed * rate()

            val target = LyricsScrolling.plainScrollTarget(estimated, speed, lineCount)
            val offset = (target.fraction * lineHeightPx(listState, target.index)).toInt()

            // Nothing to do while the track is paused, or once the last line is reached.
            if (target.index == appliedIndex && offset == appliedOffset && !returningFromManual) continue
            appliedIndex = target.index
            appliedOffset = offset

            autoScrolling = true
            try {
                if (returningFromManual) {
                    returningFromManual = false
                    // "Give it more time — 5 seconds after the last scroll — and then it will
                    // smoothly return you, not abruptly as it does now."
                    listState.animateScrollToItem(target.index, offset)
                } else {
                    listState.scrollToItem(target.index, offset)
                }
            } finally {
                autoScrolling = false
            }
        }
    }
}

/**
 * The measured height of one line, for turning a fraction of a line into a scroll offset.
 *
 * The line asked about is normally on screen. When it is not — the first frame after opening, or
 * straight after a jump — any visible line is a good enough stand-in, since they are all set in the
 * same style.
 */
private fun lineHeightPx(listState: LazyListState, index: Int): Float {
    val visible = listState.layoutInfo.visibleItemsInfo
    val item = visible.firstOrNull { it.index == index } ?: visible.firstOrNull()
    return item?.size?.toFloat() ?: 0f
}

/** One report interval plus slack. Past this the estimate is guessing, not interpolating. */
private const val MAX_EXTRAPOLATION_MS = 400L

/**
 * The mouse wheel over a lyrics view, at the reader's chosen pace (issue #33).
 *
 * The wheel delta a desktop mouse reports is a notch count, not a distance, so how far a notch goes
 * was always ours to decide — it was simply never exposed. This intercepts the event before the list
 * sees it, so the list's own step is replaced rather than added to, and notes the scroll as manual so
 * the automatic one stands down.
 */
internal fun Modifier.lyricsWheel(
    listState: LazyListState,
    scope: CoroutineScope,
    lines: () -> Float,
    onManualScroll: () -> Unit,
): Modifier = this.pointerInput(listState) {
    awaitPointerEventScope {
        while (true) {
            // Initial, so the decision is made before the list's own wheel handling on Main.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            when (event.type) {
                PointerEventType.Scroll -> {
                    val notches = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (notches == 0f) continue
                    onManualScroll()
                    event.changes.forEach { it.consume() }
                    val step = LyricsScrolling.wheelStepPx(
                        notches = notches,
                        lines = lines(),
                        lineHeightPx = lineHeightPx(listState, listState.firstVisibleItemIndex),
                    )
                    if (step != 0f) scope.launch { listState.scrollBy(step) }
                }
                // A drag is the list's to handle; this only notes that the reader took over.
                PointerEventType.Press -> onManualScroll()
                else -> Unit
            }
        }
    }
}

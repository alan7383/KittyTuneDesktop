package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

    /** How long a manual scroll holds the automatic one off, counted from when the scrolling stopped. */
    const val PLAIN_PAUSE_MS = 3_000L

    /**
     * How long a synced view leaves the reader alone after they stop scrolling by hand, before it goes
     * back to following the track: three seconds of the reader not touching it (issue #66). Counted from
     * the end of the scrolling, not its start, so a long scroll is never interrupted.
     */
    const val MANUAL_GRACE_MS = 3_000L
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
 * The very first placement is a jump rather than an animation, which is what makes opening the lyrics
 * mid-song show the line being sung instead of scrolling to it from the top of the file. See the scroll
 * itself for the report that asked for it.
 *
 * @param activeIndex the line to keep in view, or a negative value before the first line starts.
 * @param anchorPx how far below the top of the viewport the active line should settle. Zero puts it at the
 *   top of the content area, which is what a view with a large top inset already wants; a short view passes
 *   a real anchor instead of padding a third of itself away.
 * @param centred put the middle of the active line on the middle of the viewport instead, whatever its
 *   height. A line anchored by its top edge sits lower the more it wraps, so a long line opened below the
 *   centre (issue #33, round 5). Measured from the laid-out line, so it falls back to [anchorPx] for a line
 *   not on screen yet.
 * @return whether the reader is scrolling by hand — true from their scroll until following resumes.
 */
@Composable
internal fun FollowActiveLine(
    listState: LazyListState,
    activeIndex: Int,
    anchorPx: Int = 0,
    centred: Boolean = false,
    contentKey: Any? = null,
): Boolean {
    var lastManualScrollMs by remember { mutableStateOf(0L) }
    var readingByHand by remember { mutableStateOf(false) }

    /** True for exactly as long as the scroll below is ours, so the flag cannot be misattributed. */
    var autoScrolling by remember { mutableStateOf(false) }

    /** The reader's own scroll is under way: a drag held, or the wheel still turning. */
    var scrollingByHand by remember { mutableStateOf(false) }

    // Its own clock, not the follow loop's: that one only runs when the line changes, and a reader who
    // scrolls during a long line would otherwise stay "by hand" until the next one. It starts counting when
    // the scrolling stops; it used to count from when it started, so a long scroll was taken back mid-way.
    LaunchedEffect(lastManualScrollMs, scrollingByHand) {
        if (lastManualScrollMs == 0L) return@LaunchedEffect
        readingByHand = true
        if (scrollingByHand) return@LaunchedEffect
        delay(LyricsScrolling.MANUAL_GRACE_MS)
        readingByHand = false
    }

    /**
     * Whether this list has ever been put where the song is.
     *
     * False means the view has just appeared and is sitting at line one because that is where a list
     * starts, not because anybody scrolled it there. The scroll below reads it to decide between
     * putting the list where the song is and animating it there.
     */
    // Keyed on the lyrics too: a list kept across tracks would otherwise glide the next song's words in from
    // line one instead of placing them (issue #66).
    var placed by remember(listState, contentKey) { mutableStateOf(false) }

    // Drags and presses only — a programmatic scroll emits nothing here, which is the whole point.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start || interaction is PressInteraction.Press) {
                lastManualScrollMs = System.currentTimeMillis()
            }
        }
    }

    // The mouse wheel is not a drag and does not reach the interaction source, so it is caught here —
    // guarded, because this is the flag our own animation also sets. Noted again when it stops, which is
    // where the wait before following again starts.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !autoScrolling) {
            scrollingByHand = true
            lastManualScrollMs = System.currentTimeMillis()
        } else if (!listState.isScrollInProgress && scrollingByHand) {
            scrollingByHand = false
            lastManualScrollMs = System.currentTimeMillis()
        }
    }

    // Keyed on the line alone. Keying it on the manual timestamp is what made it cancel itself.
    LaunchedEffect(activeIndex, anchorPx, contentKey) {
        if (activeIndex < 0) return@LaunchedEffect

        // Re-read each time round: a second scroll during the wait extends it rather than being ignored. The
        // way back once the wait is over is the effect below, so a line that changed meanwhile has nothing to do.
        var waited = false
        while (true) {
            if (scrollingByHand) {
                waited = true
                delay(MANUAL_POLL_MS)
                continue
            }
            val remaining =
                LyricsScrolling.MANUAL_GRACE_MS - (System.currentTimeMillis() - lastManualScrollMs)
            if (remaining <= 0) break
            waited = true
            delay(remaining)
        }
        if (waited && placed) return@LaunchedEffect

        autoScrolling = true
        try {
            // Snapped the first time, animated afterwards (issue #33).
            //
            // "When you click on the text, playback does not start from the very beginning, but
            // continues from the line where the song stopped or is currently playing — both in
            // full-screen mode and in the sidebar."
            //
            // Opening the lyrics on a song already two minutes in composed the list at line one and
            // then *animated* to line thirty, so the words visibly ran from the top of the song down
            // to where it actually was, every time the view was opened. The panel's tab and the full
            // screen both build their list state fresh, so both did it, which is the "both" in the
            // report. There is nothing to animate on a first placement: no reader is following a
            // line yet, and the position the list happens to start at means nothing. Once the view
            // *is* placed, the animation is the point — that is the song moving from one line to the
            // next, and it should glide.
            if (placed) {
                listState.glideToLine(activeIndex) { followOffset(listState, activeIndex, anchorPx, centred) }
            } else {
                listState.scrollToItem(activeIndex, -anchorPx)
                // A snap remeasures at once, so the line is laid out now and can be centred before the
                // frame is drawn.
                if (centred) listState.scrollToItem(activeIndex, followOffset(listState, activeIndex, anchorPx, true))
            }
            placed = true
        } finally {
            autoScrolling = false
        }
    }

    // Back to the line being sung once the reader has left it alone. Following used to wait for the next line
    // to start, so on a pause, or in a long line, the view stayed wherever it had been scrolled to while the
    // blur came back over it (issue #66). The whole list slides back as one; see [returnToLine].
    val currentActive by rememberUpdatedState(activeIndex)
    LaunchedEffect(readingByHand) {
        if (readingByHand || !placed || currentActive < 0) return@LaunchedEffect
        autoScrolling = true
        try {
            listState.returnToLine(currentActive) { followOffset(listState, currentActive, anchorPx, centred) }
        } finally {
            autoScrolling = false
        }
    }
    return readingByHand
}

/** How often a wait for the reader to stop scrolling looks again. */
private const val MANUAL_POLL_MS = 100L

/** The longest a way back scrolls through, in viewport heights; anything further is first brought this close. */
private const val RETURN_MAX_VIEWPORTS = 2f

/**
 * Slides the list back to line [index] after the reader scrolled away: one eased movement of the whole list,
 * over a time that grows a little with the distance.
 *
 * [glideToLine] jumped to a few lines short of a far line first and then sprang the rest, and in the karaoke
 * view every line followed with its own spring, so the way back was a jump and lines flying into place
 * (issue #66). The distance to a line that is not laid out is estimated from the lines that are, and the
 * last few pixels are corrected once it is on screen.
 *
 * @param offset the scroll offset to settle the line at, as for [LazyListState.scrollToItem].
 */
internal suspend fun LazyListState.returnToLine(index: Int, offset: () -> Int) {
    val info = layoutInfo
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) {
        scrollToItem(index, offset())
        return
    }
    val target = visible.firstOrNull { it.index == index }
    var distance = if (target != null) {
        (target.offset + offset()).toFloat()
    } else {
        val first = visible.first()
        val averageStep = (visible.last().offset + visible.last().size - first.offset).toFloat() / visible.size
        first.offset + (index - first.index) * averageStep + offset()
    }
    val maxRun = viewport * RETURN_MAX_VIEWPORTS
    if (kotlin.math.abs(distance) > maxRun) {
        // Too far to scroll through in one movement: start the movement from closer.
        scrollBy(distance - kotlin.math.sign(distance) * maxRun)
        distance = kotlin.math.sign(distance) * maxRun
    }
    val durationMs = (RETURN_BASE_MS + RETURN_PER_VIEWPORT_MS * kotlin.math.abs(distance) / viewport).toInt()
    animateScrollBy(distance, tween(durationMs, easing = FastOutSlowInEasing))
    val landed = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (landed != null) {
        val miss = landed.offset + offset()
        if (kotlin.math.abs(miss) > 1) animateScrollBy(miss.toFloat(), tween(RETURN_CORRECTION_MS, easing = FastOutSlowInEasing))
    } else {
        animateScrollToItem(index, offset())
    }
}

private const val RETURN_BASE_MS = 520f
private const val RETURN_PER_VIEWPORT_MS = 180f
private const val RETURN_CORRECTION_MS = 160

/** How many lines short of a far-away line a glide starts, so it reads as movement rather than a jump. */
private const val GLIDE_RUN_UP_LINES = 3

/**
 * Scrolls to [index] with a glide however far away it is.
 *
 * [LazyListState.animateScrollToItem] only animates to a line it can measure; anything further away it
 * reaches with a jump at the end, which is what returning from a long scroll looked like. A line that is not
 * on screen is first brought within a few lines, then the rest is animated.
 */
internal suspend fun LazyListState.glideToLine(index: Int, offset: () -> Int) {
    val visible = layoutInfo.visibleItemsInfo.any { it.index == index }
    if (!visible) {
        val runUp = if (firstVisibleItemIndex < index) index - GLIDE_RUN_UP_LINES else index + GLIDE_RUN_UP_LINES
        scrollToItem(runUp.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }
    animateScrollToItem(index, offset())
}

/**
 * The line the view is about: the one being sung, or — while the reader scrolls by hand — the one nearest
 * the middle of the viewport.
 *
 * Only the treatment (scale, dimming, blur) follows it; the highlight stays on the sung line. Measuring
 * blur from the sung line alone blurred everything a reader had scrolled to, which is the opposite of
 * reading (issue #33, round 5).
 */
@Composable
internal fun rememberFocusLine(listState: LazyListState, activeIndex: Int, readingByHand: Boolean): Int {
    val centreLine by remember(listState) { derivedStateOf { listState.lineNearestViewportCentre() } }
    return if (readingByHand && centreLine >= 0) centreLine else activeIndex
}

private fun LazyListState.lineNearestViewportCentre(): Int {
    val info = layoutInfo
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - middle) }?.index ?: -1
}

/**
 * The scroll offset that puts line [index] where [FollowActiveLine] wants it.
 *
 * Item offsets count from the start of the content area and the viewport starts before it by the top
 * padding, so the viewport's middle in the same terms is the mean of its start and end offsets.
 */
private fun followOffset(listState: LazyListState, index: Int, anchorPx: Int, centred: Boolean): Int {
    if (!centred) return -anchorPx
    val info = listState.layoutInfo
    val line = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return -anchorPx
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return line.size / 2 - middle
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
    var foreignScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !autoScrolling) {
            foreignScrolling = true
            lastForeignScrollMs = System.currentTimeMillis()
        } else if (!listState.isScrollInProgress && foreignScrolling) {
            // The wait starts when the scrolling stops, not when it started.
            foreignScrolling = false
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
            if (foreignScrolling || System.currentTimeMillis() - lastTouched < LyricsScrolling.PLAIN_PAUSE_MS) {
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
                    listState.returnToLine(target.index) { offset }
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
 *
 * Each notch slides rather than jumps: a jump of several lines at once is how a reader lost their place
 * and skipped the line they were looking for (issue #66). Notches that come while a slide is under way
 * add to what is left of it, so a fast spin is one continuous movement.
 */
internal fun Modifier.lyricsWheel(
    listState: LazyListState,
    scope: CoroutineScope,
    lines: () -> Float,
    onManualScroll: () -> Unit,
): Modifier = this.pointerInput(listState) {
    // Main-thread state: the wheel events and the slide's frames all run on the UI thread, one at a time.
    var pendingPx = 0f
    var slidPx = 0f
    var slide: Job? = null
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
                    if (step == 0f) continue
                    pendingPx = pendingPx - slidPx + step
                    slidPx = 0f
                    slide?.cancel()
                    val distance = pendingPx
                    slide = scope.launch {
                        listState.scroll {
                            var previous = 0f
                            animate(0f, distance, animationSpec = tween(WHEEL_SLIDE_MS, easing = LinearOutSlowInEasing)) { value, _ ->
                                val consumed = scrollBy(value - previous)
                                previous = value
                                slidPx += consumed
                            }
                        }
                        pendingPx = 0f
                        slidPx = 0f
                    }
                }
                // A drag is the list's to handle; this only notes that the reader took over.
                PointerEventType.Press -> onManualScroll()
                else -> Unit
            }
        }
    }
}

/** How long one wheel notch takes to slide the lyrics. */
private const val WHEEL_SLIDE_MS = 240

/** What has been revealed already, so a second showing of the same lyrics does not fade. Kept short. */
private val revealedContent: MutableSet<Any> = java.util.Collections.newSetFromMap(
    object : java.util.LinkedHashMap<Any, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Boolean>?) = size > 24
    },
)

/** How long the whole list takes to fade in once it is in place. */
private const val REVEAL_MS = 420

/** The most a list waits to be placed before it is shown regardless, so a slow layout never hides the words. */
private const val REVEAL_PLACEMENT_TIMEOUT_MS = 700L

/**
 * Keeps a lyrics list invisible until it shows the line being sung, then fades it in as one block.
 *
 * A list is born at line one and only afterwards moved to where the song is. That move, and the lines
 * settling around it, used to be on screen: a line at the top first, then three more appearing under it,
 * "like mush", every time the lyrics were opened (issue #66). Nothing of that is worth seeing, so the list
 * stays transparent until [activeIndex] is laid out and no scroll is running, and then rises in gently.
 *
 * @param activeIndex the line the list is being placed on, or a negative value when there is none
 *   (plain text, or before the first line), in which case the list fades in as soon as it has content.
 */
@Composable
internal fun Modifier.revealWhenPlaced(listState: LazyListState, activeIndex: Int, contentKey: Any? = null): Modifier {
    val alpha = remember(listState) { Animatable(0f) }
    val currentActiveIndex by rememberUpdatedState(activeIndex)
    val risePx = with(LocalDensity.current) { 14.dp.toPx() }
    // Again for every new set of lyrics, not just when the view opens: a list kept across tracks showed the
    // next song's lines arriving piece by piece (issue #66).
    LaunchedEffect(listState, contentKey) {
        alpha.snapTo(0f)
        withTimeoutOrNull(REVEAL_PLACEMENT_TIMEOUT_MS) {
            snapshotFlow {
                val layout = listState.layoutInfo
                val target = currentActiveIndex
                layout.totalItemsCount > 0 &&
                    !listState.isScrollInProgress &&
                    (target < 0 || layout.visibleItemsInfo.any { it.index == target })
            }.first { it }
            // One frame more, for the lines that move with the list to land where it put them.
            withFrameNanos { }
        }
        // Lyrics that were already shown (the panel opened again, a tab came back) appear at once, placed: fading
        // them in again read as the text loading a second time (issue #66).
        if (contentKey != null && !revealedContent.add(contentKey)) {
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        this.alpha = alpha.value
        translationY = (1f - alpha.value) * risePx
    }
}

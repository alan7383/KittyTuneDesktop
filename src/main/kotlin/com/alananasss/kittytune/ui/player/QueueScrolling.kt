package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.abs

/**
 * Keeps the track being played in sight in a queue list, without ever moving it out from under the
 * pointer (issue #33).
 *
 * ## The jump this had
 *
 * The effect used to scroll on every change of current track, unconditionally. Starting a track
 * *from this list* is such a change — so the moment you clicked a row, the list animated itself to
 * put that row third from the top, sliding the whole queue under the cursor that had just clicked
 * it. Reported as "when you turn on a song from the queue, your interface jumps strangely".
 *
 * The rule below needs no notion of who caused the change, which is what makes it hold: scroll only
 * when the target is not already on screen. A row you just clicked is visible by definition, so
 * nothing moves. A track that arrived because the previous one ended is usually below the fold, so
 * the list follows it.
 *
 * @param currentIndex the position of the track being played, or negative when there is none.
 * @param currentTrackId also a key, so replacing the queue with a different track at the same
 *   position still re-anchors.
 */
@Composable
internal fun AnchorCurrentQueueItem(
    listState: LazyListState,
    currentIndex: Int,
    currentTrackId: Long?,
) {
    LaunchedEffect(currentIndex, currentTrackId) {
        if (currentIndex < 0) return@LaunchedEffect

        val visible = listState.layoutInfo.visibleItemsInfo
        // Empty before the first measure — which is the panel opening. Nothing is on screen yet, so
        // this falls through and anchors, exactly as it should.
        if (visible.any { it.index == currentIndex }) return@LaunchedEffect

        // One row above the current track, so the one just played stays visible: it is the single
        // piece of the past worth seeing at rest, and everything after it is still to come.
        val target = (currentIndex - 1).coerceAtLeast(0)
        val distance = visible.firstOrNull()?.index?.let { abs(target - it) }

        // Animating across a long queue crawls through every row in between. Past a screenful there
        // is nothing for the eye to follow anyway, so it jumps.
        if (distance == null || distance > FAR_JUMP_ITEMS) {
            listState.scrollToItem(target)
        } else {
            listState.animateScrollToItem(target)
        }
    }
}

/** Beyond this many rows, anchoring stops being a movement worth watching and becomes a wait. */
private const val FAR_JUMP_ITEMS = 12

/**
 * Stable per-row keys for [tracks], in one pass.
 *
 * The same track can sit in a queue more than once, so a key has to carry which occurrence it is.
 * That used to be answered by walking the list from the start for every row that asked, twice per
 * visible row — quadratic in the length of a queue that can hold a whole library (issue #33).
 */
internal fun queueItemKeys(tracks: List<com.alananasss.kittytune.domain.Track>): List<String> {
    val seen = HashMap<Long, Int>(tracks.size)
    return tracks.map { track ->
        val occurrence = seen.getOrDefault(track.id, 0)
        seen[track.id] = occurrence + 1
        "${track.id}_dup$occurrence"
    }
}

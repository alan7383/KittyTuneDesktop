package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.domain.Track

/** Queue entries are occurrences: upcoming entries move, played entries are queued again. */
internal sealed interface QueueNextAction {
    data class Move(val from: Int, val to: Int) : QueueNextAction
    data class Replay(val index: Int) : QueueNextAction
    data object None : QueueNextAction
}

internal fun queueNextAction(size: Int, current: Int, selected: Int): QueueNextAction = when {
    current !in 0 until size || selected !in 0 until size || selected == current -> QueueNextAction.None
    selected < current -> QueueNextAction.Replay(selected)
    else -> QueueNextAction.Move(selected, current + 1)
}

/** Match an entry across the live and original order without deleting every copy of a song. */
internal fun matchingQueueIndex(source: List<Track>, index: Int, target: List<Track>): Int {
    val track = source.getOrNull(index) ?: return -1
    val referenceMatches = target.indices.filter { target[it] === track }
    if (referenceMatches.size == 1) return referenceMatches.single()
    val occurrence = source.take(index).count { it.id == track.id }
    return target.indices.filter { target[it].id == track.id }.getOrNull(occurrence) ?: -1
}

/** In skip-to-position mode, skipped entries join the past section just like in YTM. */
internal fun queuePastIndices(
    queue: List<Track>,
    current: Int,
    played: Set<Long>,
    preserveUpcoming: Boolean
): List<Int> = queue.indices.filter { index ->
    if (preserveUpcoming) index < current - 1 && queue[index].id in played
    else index < current
}

/** Keep the active occurrence when saving a queue that includes replays of a song. */
internal fun resolveQueueIndex(queue: List<Track>, trackId: Long?, preferredIndex: Int): Int {
    if (trackId == null) return -1
    if (queue.getOrNull(preferredIndex)?.id == trackId) return preferredIndex
    return queue.indexOfFirst { it.id == trackId }
}

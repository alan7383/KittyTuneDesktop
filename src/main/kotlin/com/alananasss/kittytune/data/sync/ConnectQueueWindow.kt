package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.domain.Track

/** Captures an immutable queue once per queue change/window shift, not per playhead tick. */
internal class ConnectQueueWindow {
    data class Window(val queue: List<Track>, val index: Int, val offset: Int)
    private var source: List<Track>? = null
    private var offset = -1
    private var captured: List<Track> = emptyList()

    fun capture(queue: List<Track>, index: Int): Window? {
        if (index !in queue.indices) return null
        val start = start(queue.size, index)
        if (source !== queue || offset != start) {
            captured = queue.subList(start, minOf(queue.size, start + MAX_QUEUE)).toList()
            source = queue
            offset = start
        }
        return Window(captured, index - start, start)
    }

    companion object {
        const val MAX_QUEUE = 500
        fun start(size: Int, index: Int): Int = if (size > MAX_QUEUE)
            (index - MAX_QUEUE / 2).coerceIn(0, size - MAX_QUEUE) else 0

        fun sameTracks(first: List<Track>, second: List<Track>): Boolean =
            first === second || first.size == second.size && first.indices.all {
                first[it].id == second[it].id && first[it].source == second[it].source
            }
    }
}

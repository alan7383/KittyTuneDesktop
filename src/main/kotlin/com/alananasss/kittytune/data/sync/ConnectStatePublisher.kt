package com.alananasss.kittytune.data.sync

/** Session-local deduplication. Callers supply immutable queues captured by ConnectQueueWindow. */
internal class ConnectStatePublisher {
    data class Frame(val state: PlaybackSnapshot, val queueVersion: String)
    private var lastSent: PlaybackSnapshot? = null
    private var lastQueue = ""
    private var versionQueue: List<com.alananasss.kittytune.domain.Track>? = null
    private var version = ""
    private var sentAtNanos = 0L

    fun plan(state: PlaybackSnapshot, force: Boolean = false, nowNanos: Long = System.nanoTime()): Frame? {
        if (versionQueue !== state.queue) {
            version = ConnectWire.queueVersion(state)
            versionQueue = state.queue
        }
        val old = lastSent
        val elapsedMs = ((nowNanos - sentAtNanos) / 1_000_000L).coerceAtLeast(0L)
        val projected = old?.let {
            if (it.isPlaying) it.projectedPosition(it.updatedAtMs + elapsedMs) else it.positionMs
        }
        val queueChanged = old != null && old.queue !== state.queue && old.queue != state.queue
        val changed = old == null || old.currentIndex != state.currentIndex ||
            old.isPlaying != state.isPlaying || old.shuffleEnabled != state.shuffleEnabled ||
            old.repeatMode != state.repeatMode || old.volume != state.volume || version != lastQueue ||
            queueChanged || kotlin.math.abs(state.positionMs - (projected ?: 0L)) > 1_000L
        // A paused renderer has no timeline drift; its socket already supplies keepalive.
        if (!force && !changed && (!state.isPlaying || elapsedMs < 60_000L)) return null
        val payload = if (force || queueChanged || lastQueue != version) state else state.copy(queue = emptyList())
        lastSent = state
        lastQueue = version
        sentAtNanos = nowNanos
        return Frame(payload, version)
    }
}

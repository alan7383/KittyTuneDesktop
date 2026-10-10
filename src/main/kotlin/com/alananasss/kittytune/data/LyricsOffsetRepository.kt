package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LyricsOffsetRow

/**
 * A track's persistent lyrics synchronization: one offset, or two points the offset drifts between
 * (see [LyricsSync]).
 *
 * When a user adjusts lyrics timing for a track (e.g. +1s, -0.1s), the sync is remembered per track so
 * returning to the track or replaying it preserves it. Absent means no offset.
 */
object LyricsOffsetRepository {

    private val dao get() = AppDatabase.downloadDao

    /**
     * Bounded and keyed by track id. Sized for a queue rather than a library. [LyricsSync.NONE] stands for
     * "nothing saved", since [BoundedCache] cannot store nulls.
     */
    private val cache = BoundedCache<Long, LyricsSync>(256)

    /** @return the track's saved sync, or [LyricsSync.NONE] when none is set. */
    suspend fun get(trackId: Long): LyricsSync {
        cache[trackId]?.let { return it }
        val stored = runCatching { dao.getLyricsOffset(trackId) }.getOrNull()?.let { row ->
            LyricsSync(
                offsetMs = row.offsetMs,
                anchorMs = row.anchorMs,
                endAtMs = row.endAtMs,
                endOffsetMs = row.endOffsetMs,
            )
        } ?: LyricsSync.NONE
        cache[trackId] = stored
        return stored
    }

    suspend fun put(trackId: Long, sync: LyricsSync) {
        if (sync.isNone) {
            remove(trackId)
            return
        }
        val clamped = sync.copy(
            offsetMs = sync.offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS),
            endOffsetMs = sync.endOffsetMs?.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS),
        )
        cache[trackId] = clamped
        runCatching {
            dao.putLyricsOffset(
                LyricsOffsetRow(
                    trackId = trackId,
                    offsetMs = clamped.offsetMs,
                    updatedAt = System.currentTimeMillis(),
                    anchorMs = clamped.anchorMs,
                    endAtMs = clamped.endAtMs,
                    endOffsetMs = clamped.endOffsetMs,
                )
            )
        }
    }

    suspend fun remove(trackId: Long) {
        cache[trackId] = LyricsSync.NONE
        runCatching { dao.deleteLyricsOffset(trackId) }
    }

    const val MIN_OFFSET_MS = -120_000L
    const val MAX_OFFSET_MS = 120_000L
}

package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LyricsOffsetRow

/**
 * A track's persistent lyrics synchronization offset in milliseconds.
 *
 * When a user adjusts lyrics timing for a track (e.g. +1s, -0.1s), the offset
 * is remembered per track so returning to the track or replaying it preserves
 * the exact sync.
 *
 * 0L (or absent) means no offset. [NO_OVERRIDE] indicates "no offset set / 0L",
 * since [BoundedCache] cannot store nulls.
 */
object LyricsOffsetRepository {

    private val dao get() = AppDatabase.downloadDao

    /**
     * Bounded and keyed by track id. Sized for a queue rather than a library.
     */
    private val cache = BoundedCache<Long, Long>(256)

    /** @return the track's saved offset in milliseconds, or 0L when none is set. */
    suspend fun get(trackId: Long): Long {
        cache[trackId]?.let { return if (it == NO_OVERRIDE) 0L else it }
        val stored = runCatching { dao.getLyricsOffset(trackId)?.offsetMs }.getOrNull()
        cache[trackId] = stored ?: NO_OVERRIDE
        return stored ?: 0L
    }

    suspend fun put(trackId: Long, offsetMs: Long) {
        if (offsetMs == 0L) {
            remove(trackId)
            return
        }
        val clamped = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
        cache[trackId] = clamped
        runCatching {
            dao.putLyricsOffset(
                LyricsOffsetRow(trackId, clamped, System.currentTimeMillis())
            )
        }
    }

    suspend fun remove(trackId: Long) {
        cache[trackId] = NO_OVERRIDE
        runCatching { dao.deleteLyricsOffset(trackId) }
    }

    /** Sentinel value for cache indicating no offset (0L) is saved in DB. */
    private const val NO_OVERRIDE = Long.MIN_VALUE

    const val MIN_OFFSET_MS = -120_000L
    const val MAX_OFFSET_MS = 120_000L
}

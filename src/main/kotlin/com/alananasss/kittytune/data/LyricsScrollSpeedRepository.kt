package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LyricsScrollSpeedRow

/**
 * A track's own auto-scroll speed for lyrics with no timings (issue #33).
 *
 * "Make it so that you can set a custom scrolling speed for each track" — because how fast untimed
 * text should creep is a property of the song, not of the reader: a two-minute punk track and a
 * seven-minute ballad with the same number of lines want very different speeds.
 *
 * A table rather than one preference key per track, the way [TrackTrimRepository] already does it:
 * a library's worth of keys in a settings file that is read whole at startup is a cost paid by
 * everyone for a setting almost nobody sets. Absent means "use the global speed", and the cache
 * holds that absence too, so the common case is not a database round trip per track change.
 */
object LyricsScrollSpeedRepository {

    private val dao get() = AppDatabase.downloadDao

    /**
     * Bounded and keyed by track id. Sized for a queue rather than a library: what is worth
     * remembering is what is about to play again.
     *
     * [NO_OVERRIDE] stands in for "this track has none", since the cache cannot hold nulls.
     */
    private val cache = BoundedCache<Long, Float>(256)

    /** @return the track's own speed, or null when it has none and the global one applies. */
    suspend fun get(trackId: Long): Float? {
        cache[trackId]?.let { return it.takeIf { v -> v != NO_OVERRIDE } }
        val stored = runCatching { dao.getLyricsScrollSpeed(trackId)?.speed }.getOrNull()
        cache[trackId] = stored ?: NO_OVERRIDE
        return stored
    }

    suspend fun put(trackId: Long, speed: Float) {
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        cache[trackId] = clamped
        runCatching {
            dao.putLyricsScrollSpeed(
                LyricsScrollSpeedRow(trackId, clamped, System.currentTimeMillis())
            )
        }
    }

    suspend fun remove(trackId: Long) {
        cache[trackId] = NO_OVERRIDE
        runCatching { dao.deleteLyricsScrollSpeed(trackId) }
    }

    /** Not a speed anyone can set, so it cannot be mistaken for one. */
    private const val NO_OVERRIDE = -1f

    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 4f
}

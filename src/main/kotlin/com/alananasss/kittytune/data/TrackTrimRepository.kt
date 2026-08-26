package com.alananasss.kittytune.data

import com.alananasss.kittytune.audio.TrackTrim
import com.alananasss.kittytune.audio.TrimMode
import com.alananasss.kittytune.audio.TrimSegment
import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.TrackTrimRow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Where a track's trim is kept (issue #33).
 *
 * Read on every track change, so it is cached — and the cache holds "no trim" as well as a real one. That
 * matters more than it sounds: almost no track has a trim, so without caching the misses, the common case
 * would be a database round trip per track for an answer that is nearly always nothing.
 */
object TrackTrimRepository {

    private val gson = Gson()
    private val dao get() = AppDatabase.downloadDao
    private val listType = object : TypeToken<List<StoredSegment>>() {}.type

    /**
     * Bounded because it is keyed by track id and a long session passes thousands of them. Sized for a queue
     * rather than a library: what is worth remembering is what is about to play again.
     */
    private val cache = BoundedCache<Long, TrackTrim>(256)

    /** Kept apart from [TrimSegment] so a stored shape and a runtime one can change independently. */
    private data class StoredSegment(val startMs: Long = 0, val endMs: Long = 0)

    suspend fun get(trackId: Long): TrackTrim {
        cache[trackId]?.let { return it }
        val trim = runCatching { dao.getTrackTrim(trackId)?.let(::parse) }.getOrNull() ?: TrackTrim.none()
        cache[trackId] = trim
        return trim
    }

    suspend fun put(trackId: Long, trim: TrackTrim) {
        // An empty trim is an absent one. Storing a row with no spans would leave a track looking trimmed in
        // any list built from the table while behaving exactly as if it were not.
        if (trim.isEmpty) {
            remove(trackId)
            return
        }
        cache[trackId] = trim
        runCatching {
            dao.putTrackTrim(
                TrackTrimRow(
                    trackId = trackId,
                    mode = trim.mode.name,
                    segments = gson.toJson(trim.segments.map { StoredSegment(it.startMs, it.endMs) }),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun remove(trackId: Long) {
        cache[trackId] = TrackTrim.none()
        runCatching { dao.deleteTrackTrim(trackId) }
    }

    /** Which tracks have one, for a screen that wants to list them. */
    suspend fun trimmedTrackIds(): List<Long> =
        runCatching { dao.getTrimmedTrackIds() }.getOrDefault(emptyList())

    /**
     * A row that will not parse costs that track's trim and nothing else.
     *
     * The alternative — letting it throw — would take down the track change that asked for it, which is a
     * bad trade for a feature that is a convenience.
     */
    private fun parse(row: TrackTrimRow): TrackTrim {
        val mode = runCatching { TrimMode.valueOf(row.mode) }.getOrDefault(TrimMode.CUT)
        val stored: List<StoredSegment> =
            runCatching { gson.fromJson<List<StoredSegment>>(row.segments, listType) }
                .getOrNull()
                .orEmpty()
        return TrackTrim.of(mode, stored.map { TrimSegment(it.startMs, it.endMs) })
    }
}

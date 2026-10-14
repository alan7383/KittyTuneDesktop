package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import java.sql.ResultSet

/**
 * The liked-tracks library, one row per like (RAM, issue #33).
 *
 * The library used to be persisted as a single JSON string of the whole [List] of tracks, stored
 * inside the preferences map. That map is resident for the process lifetime, so the string was
 * too — several megabytes for a few thousand likes, on top of the [Track] objects the UI state
 * already holds, and every save rebuilt it from scratch and re-encoded the whole preferences file
 * around it. Here each like is one row: the write is a transaction over rows, the read is a scan
 * of rows, and the only full-size copy of the library in RAM is the one the UI needs.
 *
 * [Track.likedAt] lives in the JSON as part of the track, and again as a column purely so the
 * library comes back out newest-first — the order every reader of the state flow has always been
 * given. The two are written together and only the JSON is ever read back into a [Track], so
 * there is no second source of truth to drift.
 */
class LikeDao(private val db: AppDatabase) {

    private val gson = Gson()

    /**
     * The whole library, newest like first.
     *
     * A row that will not parse costs that row, not the library: the same rule the sync log and
     * every other table here follow, because one unreadable entry is never a reason to drop the
     * user's favourites on the floor.
     */
    suspend fun loadAll(): List<Track> =
        db.query(
            "SELECT trackJson FROM liked_tracks ORDER BY likedAt DESC",
            mapper = { rs: ResultSet -> rs.getString("trackJson") },
        ).mapNotNull { json ->
            runCatching { gson.fromJson(json, Track::class.java) }.getOrNull()
        }

    /**
     * Replaces the table with exactly [tracks], atomically.
     *
     * A DELETE and the inserts share one transaction, so a crash mid-save leaves the previous
     * library in place rather than an empty table — the library is a projection of the UI state,
     * and the state is what it is at save time, all of it or none of it.
     */
    suspend fun replaceAll(tracks: List<Track>) {
        db.execTransaction {
            val conn = db.raw()
            conn.createStatement().use { it.executeUpdate("DELETE FROM liked_tracks") }
            if (tracks.isEmpty()) return@execTransaction
            conn.prepareStatement(
                "INSERT INTO liked_tracks(trackId, likedAt, trackJson) VALUES(?,?,?)"
            ).use { ps ->
                for (track in tracks) {
                    ps.setLong(1, track.id)
                    ps.setLong(2, track.likedAt ?: 0L)
                    ps.setString(3, gson.toJson(track))
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }
    }

    /** Logout: the table goes with the session, so the next login starts clean. */
    suspend fun clear() = db.exec("DELETE FROM liked_tracks")
}

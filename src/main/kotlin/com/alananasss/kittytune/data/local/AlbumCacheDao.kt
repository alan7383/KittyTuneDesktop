package com.alananasss.kittytune.data.local

import java.sql.ResultSet

/**
 * Persistent cache mapping a track to its resolved album (or the confirmed absence of one).
 * `albumPlaylistId == null` with a non-null title = title-only (from publisher_metadata,
 * not clickable); both null = negative result ("no album found"), subject to a TTL purge.
 */
data class TrackAlbumCacheRow(
    val trackId: Long,
    val albumPlaylistId: Long?,
    val albumTitle: String?,
    val resolvedAt: Long
)

class AlbumCacheDao(private val db: AppDatabase) {

    private fun row(rs: ResultSet): TrackAlbumCacheRow {
        val playlistId = rs.getLong("albumPlaylistId")
        return TrackAlbumCacheRow(
            trackId = rs.getLong("trackId"),
            albumPlaylistId = if (rs.wasNull()) null else playlistId,
            albumTitle = rs.getString("albumTitle"),
            resolvedAt = rs.getLong("resolvedAt")
        )
    }

    suspend fun get(trackId: Long): TrackAlbumCacheRow? =
        db.queryOne("SELECT * FROM track_album_cache WHERE trackId = ?", trackId, mapper = ::row)

    /** Batch lookup — trackIds are our own Longs so inlining into the IN clause is safe. */
    suspend fun getBatch(trackIds: List<Long>): List<TrackAlbumCacheRow> =
        if (trackIds.isEmpty()) emptyList()
        else db.query(
            "SELECT * FROM track_album_cache WHERE trackId IN (${trackIds.joinToString(",")})",
            mapper = ::row
        )

    suspend fun upsert(r: TrackAlbumCacheRow) = db.execSilent(
        "INSERT OR REPLACE INTO track_album_cache(trackId, albumPlaylistId, albumTitle, resolvedAt) VALUES(?,?,?,?)",
        r.trackId, r.albumPlaylistId, r.albumTitle, r.resolvedAt
    )

    /** Drop stale negative results so tracks get re-checked eventually. */
    suspend fun purgeExpiredNegatives(cutoff: Long) = db.execSilent(
        "DELETE FROM track_album_cache WHERE albumPlaylistId IS NULL AND albumTitle IS NULL AND resolvedAt < ?",
        cutoff
    )
}

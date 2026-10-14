package com.alananasss.kittytune.data.local

import kotlinx.coroutines.flow.Flow
import java.sql.ResultSet

/**
 * Desktop BlockedContentDao — same method signatures and SQL as the Android Room DAO,
 * implemented over the JDBC AppDatabase.
 *
 * Queries are split between track blocks and artist blocks to keep them cheap:
 * the full lists are only loaded when the user opens the settings page. For the
 * hot path (playback / list filtering) [com.alananasss.kittytune.data.BlockManager]'s
 * in-memory cache is used instead.
 */
class BlockedContentDao(private val db: AppDatabase) {

    private fun row(rs: ResultSet) = BlockedContent(
        id = rs.getLong("id"),
        trackId = rs.getLong("trackId").let { if (rs.wasNull()) null else it },
        trackTitle = rs.getString("trackTitle"),
        trackArtworkUrl = rs.getString("trackArtworkUrl"),
        artistId = rs.getLong("artistId").let { if (rs.wasNull()) null else it },
        artistName = rs.getString("artistName"),
        artistAvatarUrl = rs.getString("artistAvatarUrl"),
        source = rs.getString("source") ?: "soundcloud",
        reason = rs.getString("reason") ?: "MANUAL",
        blockedAt = rs.getLong("blockedAt"),
    )

    // ─── Inserts ─────────────────────────────────────────────────────────────

    suspend fun insertBlock(block: BlockedContent) {
        db.exec(
            "INSERT OR IGNORE INTO blocked_content " +
                "(trackId, trackTitle, trackArtworkUrl, artistId, artistName, artistAvatarUrl, source, reason, blockedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            block.trackId,
            block.trackTitle,
            block.trackArtworkUrl,
            block.artistId,
            block.artistName,
            block.artistAvatarUrl,
            block.source,
            block.reason,
            block.blockedAt,
        )
    }

    // ─── Track queries ────────────────────────────────────────────────────────

    fun observeBlockedTracks(): Flow<List<BlockedContent>> =
        db.observe {
            db.query(
                "SELECT * FROM blocked_content WHERE trackId IS NOT NULL ORDER BY blockedAt DESC",
                mapper = ::row,
            )
        }

    suspend fun getBlockedTracksList(): List<BlockedContent> =
        db.query(
            "SELECT * FROM blocked_content WHERE trackId IS NOT NULL ORDER BY blockedAt DESC",
            mapper = ::row,
        )

    suspend fun getAllBlockedTrackIds(): List<Long> =
        db.query("SELECT trackId FROM blocked_content WHERE trackId IS NOT NULL") { it.getLong(1) }

    suspend fun isTrackBlocked(trackId: Long): Int =
        db.scalarInt("SELECT COUNT(*) FROM blocked_content WHERE trackId = ?", trackId)

    suspend fun unblockTrack(trackId: Long) {
        db.exec("DELETE FROM blocked_content WHERE trackId = ?", trackId)
    }

    // ─── Artist queries ───────────────────────────────────────────────────────

    fun observeBlockedArtists(): Flow<List<BlockedContent>> =
        db.observe {
            db.query(
                "SELECT * FROM blocked_content WHERE artistId IS NOT NULL ORDER BY blockedAt DESC",
                mapper = ::row,
            )
        }

    suspend fun getBlockedArtistsList(): List<BlockedContent> =
        db.query(
            "SELECT * FROM blocked_content WHERE artistId IS NOT NULL ORDER BY blockedAt DESC",
            mapper = ::row,
        )

    suspend fun getAllBlockedArtistIds(): List<Long> =
        db.query("SELECT artistId FROM blocked_content WHERE artistId IS NOT NULL") { it.getLong(1) }

    suspend fun isArtistBlocked(artistId: Long): Int =
        db.scalarInt("SELECT COUNT(*) FROM blocked_content WHERE artistId = ?", artistId)

    suspend fun unblockArtist(artistId: Long) {
        db.exec("DELETE FROM blocked_content WHERE artistId = ?", artistId)
    }

    // ─── Combined ─────────────────────────────────────────────────────────────

    fun observeAll(): Flow<List<BlockedContent>> =
        db.observe {
            db.query("SELECT * FROM blocked_content ORDER BY blockedAt DESC", mapper = ::row)
        }
}

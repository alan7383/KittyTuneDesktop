package com.alananasss.kittytune.data.local

import kotlinx.coroutines.flow.Flow
import java.sql.ResultSet

data class QueueHistoryEntity(
    val id: String,
    val title: String,
    val trackCount: Int,
    val tracksJson: String,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val contextJson: String?,
    val firstTrackArtworkUrl: String?,
    val firstTrackTitle: String?,
    val firstTrackArtist: String?,
    val savedAt: Long = System.currentTimeMillis()
)

data class QueueHistorySummary(
    val id: String,
    val title: String,
    val trackCount: Int,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val contextJson: String?,
    val firstTrackArtworkUrl: String?,
    val firstTrackTitle: String?,
    val firstTrackArtist: String?,
    val savedAt: Long
)

class QueueHistoryDao(private val db: AppDatabase) {

    private fun summaryRow(rs: ResultSet) = QueueHistorySummary(
        id = rs.getString("id"),
        title = rs.getString("title"),
        trackCount = rs.getInt("trackCount"),
        currentIndex = rs.getInt("currentIndex"),
        currentPositionMs = rs.getLong("currentPositionMs"),
        contextJson = rs.getString("contextJson"),
        firstTrackArtworkUrl = rs.getString("firstTrackArtworkUrl"),
        firstTrackTitle = rs.getString("firstTrackTitle"),
        firstTrackArtist = rs.getString("firstTrackArtist"),
        savedAt = rs.getLong("savedAt")
    )

    private fun fullRow(rs: ResultSet) = QueueHistoryEntity(
        id = rs.getString("id"),
        title = rs.getString("title"),
        trackCount = rs.getInt("trackCount"),
        tracksJson = rs.getString("tracksJson"),
        currentIndex = rs.getInt("currentIndex"),
        currentPositionMs = rs.getLong("currentPositionMs"),
        contextJson = rs.getString("contextJson"),
        firstTrackArtworkUrl = rs.getString("firstTrackArtworkUrl"),
        firstTrackTitle = rs.getString("firstTrackTitle"),
        firstTrackArtist = rs.getString("firstTrackArtist"),
        savedAt = rs.getLong("savedAt")
    )

    fun getAllSummaries(): Flow<List<QueueHistorySummary>> =
        db.observe {
            db.query(
                "SELECT id, title, trackCount, currentIndex, currentPositionMs, contextJson, firstTrackArtworkUrl, firstTrackTitle, firstTrackArtist, savedAt FROM queue_history ORDER BY savedAt DESC",
                mapper = ::summaryRow
            )
        }

    suspend fun getById(id: String): QueueHistoryEntity? =
        db.queryOne(
            "SELECT * FROM queue_history WHERE id = ?",
            id,
            mapper = ::fullRow
        )

    suspend fun insert(entity: QueueHistoryEntity) {
        db.exec(
            "INSERT OR REPLACE INTO queue_history " +
                "(id, title, trackCount, tracksJson, currentIndex, currentPositionMs, contextJson, firstTrackArtworkUrl, firstTrackTitle, firstTrackArtist, savedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            entity.id,
            entity.title,
            entity.trackCount,
            entity.tracksJson,
            entity.currentIndex,
            entity.currentPositionMs,
            entity.contextJson,
            entity.firstTrackArtworkUrl,
            entity.firstTrackTitle,
            entity.firstTrackArtist,
            entity.savedAt
        )
    }

    suspend fun deleteById(id: String) {
        db.exec("DELETE FROM queue_history WHERE id = ?", id)
    }

    suspend fun clearAll() {
        db.exec("DELETE FROM queue_history")
    }

    suspend fun getAllIds(): List<String> =
        db.query("SELECT id FROM queue_history ORDER BY savedAt DESC") { rs -> rs.getString("id") }

    suspend fun deleteByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        db.exec("DELETE FROM queue_history WHERE id IN ($placeholders)", *ids.toTypedArray())
    }
}

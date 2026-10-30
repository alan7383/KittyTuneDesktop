package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.QueueHistoryEntity
import com.alananasss.kittytune.data.local.QueueHistorySummary
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

object QueueHistoryRepository {
    private val dao get() = AppDatabase.queueHistoryDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private const val MAX_HISTORY_ITEMS = 30

    @Volatile
    private var lastSavedTrackIds: List<Long> = emptyList()

    fun getSummaries(): Flow<List<QueueHistorySummary>> {
        return dao.getAllSummaries()
    }

    suspend fun getQueueById(id: String): QueueHistoryEntity? {
        return withContext(Dispatchers.IO) {
            dao.getById(id)
        }
    }

    fun saveQueue(
        queue: List<Track>,
        currentIndex: Int,
        currentPositionMs: Long,
        context: PlaybackContext?,
        customTitle: String? = null,
        onSaved: ((String) -> Unit)? = null
    ): String? {
        if (queue.isEmpty()) return null
        val currentIds = queue.map { it.id }
        if (currentIds == lastSavedTrackIds && customTitle == null) {
            return null
        }

        val id = UUID.randomUUID().toString()
        lastSavedTrackIds = currentIds

        val safeIndex = currentIndex.coerceIn(0, queue.lastIndex)
        val currentTrack = queue.getOrNull(safeIndex) ?: queue.firstOrNull()

        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: context?.displayText?.takeIf { it.isNotBlank() }
            ?: currentTrack?.let {
                if (queue.size > 1) "${it.title} & ${queue.size - 1} more" else it.title
            }
            ?: "Queue (${queue.size} tracks)"

        val entity = QueueHistoryEntity(
            id = id,
            title = title,
            trackCount = queue.size,
            tracksJson = gson.toJson(queue),
            currentIndex = safeIndex,
            currentPositionMs = currentPositionMs.coerceAtLeast(0L),
            contextJson = context?.let { gson.toJson(it) },
            firstTrackArtworkUrl = currentTrack?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: queue.firstOrNull()?.thumbnailUrl,
            firstTrackTitle = currentTrack?.title,
            firstTrackArtist = currentTrack?.displayArtist,
            savedAt = System.currentTimeMillis()
        )

        scope.launch {
            try {
                dao.insert(entity)

                val ids = dao.getAllIds()
                if (ids.size > MAX_HISTORY_ITEMS) {
                    val toDelete = ids.drop(MAX_HISTORY_ITEMS)
                    dao.deleteByIds(toDelete)
                }

                withContext(Dispatchers.Main) {
                    onSaved?.invoke(id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return id
    }

    fun deleteQueue(id: String) {
        scope.launch {
            runCatching { dao.deleteById(id) }
        }
    }

    fun clearAll() {
        scope.launch {
            runCatching {
                lastSavedTrackIds = emptyList()
                dao.clearAll()
            }
        }
    }

    fun deserializeTracks(tracksJson: String): List<Track> {
        return try {
            val arr = gson.fromJson<Array<Track>>(tracksJson, Array<Track>::class.java)
            arr?.filterNotNull() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deserializeContext(contextJson: String?): PlaybackContext? {
        if (contextJson.isNullOrBlank()) return null
        return try {
            gson.fromJson(contextJson, PlaybackContext::class.java)
        } catch (_: Exception) {
            null
        }
    }
}

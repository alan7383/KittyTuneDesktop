package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.RecognitionHistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

object RecognitionHistoryRepository {
    private val dao get() = AppDatabase.recognitionHistoryDao
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init() {}

    fun addToHistory(trackId: Long?, title: String, artist: String, artworkUrl: String?) {
        scope.launch {
            dao.insertItem(
                RecognitionHistoryItem(
                    trackId = trackId,
                    title = title,
                    artist = artist,
                    artworkUrl = artworkUrl
                )
            )
        }
    }

    fun getHistory(): Flow<List<RecognitionHistoryItem>> = dao.getAllItems()

    fun clearHistory() {
        scope.launch { dao.clearHistory() }
    }

    fun deleteItem(itemId: Long) {
        scope.launch { dao.deleteItem(itemId) }
    }
}

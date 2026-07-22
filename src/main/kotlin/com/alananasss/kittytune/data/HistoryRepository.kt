package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.HistoryItem
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object HistoryRepository {
    private val dao get() = AppDatabase.downloadDao
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init() {
        // Database is initialized globally; nothing to wire here.
    }

    fun addToHistory(track: Track) {
        scope.launch {
            val safeSource = (track.source as? String) ?: "soundcloud"

            val item = HistoryItem(
                id = "track:${track.id}",
                numericId = track.id,
                title = track.title ?: str("history_untitled_track"),
                subtitle = track.user?.username ?: str("history_unknown_artist"),
                imageUrl = track.fullResArtwork,
                type = "TRACK",
                isVerified = track.user?.verified == true,
                source = safeSource,
                originalUrl = track.permalinkUrl
            )
            dao.insertHistory(item)
        }
    }

    fun addToHistory(playlist: Playlist, isStation: Boolean = false, isProfile: Boolean = false) {
        scope.launch {
            val isYoutubeRadio = playlist.permalinkUrl?.startsWith("yt_radio:") == true
            val (stringId, type) = when {
                isProfile -> "profile:${playlist.id}" to "PROFILE"
                isYoutubeRadio -> playlist.permalinkUrl!! to "STATION"
                isStation -> "station:${playlist.id}" to "STATION"
                playlist.id == -1L -> "likes" to "PLAYLIST"
                playlist.id == -2L -> "downloads" to "PLAYLIST"
                playlist.id < 0 -> "playlist:${playlist.id}" to "PLAYLIST"
                else -> "playlist:${playlist.id}" to "PLAYLIST"
            }

            val finalSubtitle = when {
                isProfile -> str("history_type_artist")
                isYoutubeRadio -> "YouTube"
                isStation -> playlist.user?.username ?: str("history_type_station")
                playlist.id == -1L || playlist.id == -2L -> str("history_source_library")
                playlist.id < 0 -> str("history_type_local_playlist")
                else -> playlist.user?.username ?: str("history_source_soundcloud")
            }

            val finalTitle = when (playlist.id) {
                -1L -> str("history_title_likes")
                -2L -> str("history_title_downloads")
                else -> playlist.title ?: str("history_default_playlist_title")
            }

            val item = HistoryItem(
                id = stringId,
                numericId = playlist.id,
                title = finalTitle,
                subtitle = finalSubtitle,
                imageUrl = playlist.fullResArtwork,
                type = type,
                isVerified = playlist.user?.verified == true
            )
            dao.insertHistory(item)
        }
    }

    fun removeFromHistory(playlistId: Long) {
        scope.launch {
            dao.deleteHistoryItem("playlist:$playlistId")
            dao.deleteHistoryItem("station:$playlistId")
        }
    }

    fun getHistory() = dao.getHistory()
}

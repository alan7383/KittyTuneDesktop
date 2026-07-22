package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.local.TopArtistResult
import com.alananasss.kittytune.data.local.TopTrackResult
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ListeningStatsRepository {
    private val dao get() = AppDatabase.downloadDao
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init() {
        // Database initialized globally.
    }

    fun recordEvent(track: Track, eventType: String, listenDurationMs: Long = 0) {
        scope.launch {
            val event = ListeningStatsEvent(
                trackId = track.id,
                trackTitle = track.title ?: "Unknown",
                artistName = track.user?.username ?: "Unknown",
                artistId = track.user?.id,
                artistPermalink = track.user?.permalinkUrl,
                artistAvatarUrl = track.user?.avatarUrl,
                artworkUrl = track.fullResArtwork,
                source = track.source ?: "soundcloud",
                eventType = eventType,
                listenDurationMs = listenDurationMs,
                trackDurationMs = track.durationMs ?: 0L
            )
            dao.insertStatsEvent(event)
        }
    }

    suspend fun getTopTracks(since: Long, limit: Int = 10): List<TopTrackResult> =
        dao.getTopTracksAfter(since, limit)

    suspend fun getTopArtists(since: Long, limit: Int = 10): List<TopArtistResult> =
        dao.getTopArtistsAfter(since, limit)

    suspend fun getTopTracksBetween(since: Long, until: Long, limit: Int = 1): List<TopTrackResult> =
        dao.getTopTracksBetween(since, until, limit)

    suspend fun getTopArtistsBetween(since: Long, until: Long, limit: Int = 1): List<TopArtistResult> =
        dao.getTopArtistsBetween(since, until, limit)

    suspend fun getTotalListenTime(since: Long): Long = dao.getTotalListenTimeAfter(since)
    suspend fun getEventCount(type: String, since: Long): Int = dao.getEventCountByType(type, since)
    suspend fun getTotalEvents(since: Long): Int = dao.getTotalEventsAfter(since)
    suspend fun getUniqueTracks(since: Long): Int = dao.getUniqueTracksAfter(since)
    suspend fun getUniqueArtists(since: Long): Int = dao.getUniqueArtistsAfter(since)
    suspend fun getEvents(since: Long): List<ListeningStatsEvent> = dao.getEventsAfter(since)

    fun clearStats() {
        scope.launch { dao.clearStats() }
    }
}

package com.alananasss.kittytune.data.local

/**
 * Persistence entities — desktop port of the Room entities.
 * Room @Entity/@PrimaryKey annotations dropped; the schema lives in AppDatabase (JDBC).
 * Field names & defaults are kept identical for backup/restore compatibility.
 */

// table: downloaded_tracks
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val duration: Long,
    val localAudioPath: String,
    val localArtworkPath: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

// table: downloaded_playlists
data class LocalPlaylist(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val trackCount: Int,
    val isUserCreated: Boolean = false,
    val localCoverPath: String? = null,
    val permalinkUrl: String? = null,
    val isAlbum: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

// table: playlist_track_cross_ref (PK [playlistId, trackId])
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

// table: saved_artists
data class LocalArtist(
    val id: Long,
    val username: String,
    val avatarUrl: String,
    val trackCount: Int,
    val savedAt: Long = System.currentTimeMillis()
)

// table: play_history
data class HistoryItem(
    val id: String,
    val numericId: Long,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isVerified: Boolean = false,
    val source: String = "soundcloud",
    val originalUrl: String? = null
)

// table: recognition_history (autoGenerate id)
data class RecognitionHistoryItem(
    val id: Long = 0,
    val trackId: Long?,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val timestamp: Long = System.currentTimeMillis()
)

// table: listening_stats (autoGenerate id)
data class ListeningStatsEvent(
    val id: Long = 0,
    val trackId: Long,
    val trackTitle: String,
    val artistName: String,
    val artistId: Long? = null,
    val artistPermalink: String? = null,
    val artistAvatarUrl: String? = null,
    val artworkUrl: String,
    val source: String = "soundcloud",
    val eventType: String,          // PLAY_COMPLETE, SKIP_NEXT, SKIP_PREVIOUS, MANUAL_REPLAY, REPEAT_ONE_LOOP
    val listenDurationMs: Long = 0,
    val trackDurationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class TopTrackResult(
    val trackId: Long,
    val trackTitle: String,
    val artistName: String,
    val artworkUrl: String?,
    val source: String?,
    val playCount: Int,
    val totalListenMs: Long
)

data class TopArtistResult(
    val artistName: String,
    val artworkUrl: String?,
    val artistId: Long?,
    val artistPermalink: String?,
    val source: String?,
    val playCount: Int,
    val totalListenMs: Long
)

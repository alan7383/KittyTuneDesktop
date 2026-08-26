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
    val addedAt: Long = System.currentTimeMillis(),
    val isDownloaded: Boolean = false
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
    val timestamp: Long = System.currentTimeMillis(),
    /**
     * How far playback actually got in the track (issue #33).
     *
     * Separate from [listenDurationMs] because they answer different questions: someone who skips the
     * first minute and listens to the rest heard less than the track lasts but did reach the end, and
     * someone who loops the same chorus ten times heard a great deal without ever getting near it.
     * Completion is judged on this; how much was heard is judged on the other. Zero on rows written
     * before it was recorded, which is what
     * [com.alananasss.kittytune.data.stats.StatsSql.IS_COMPLETE] falls back on the ending label for.
     */
    val furthestPositionMs: Long = 0,
    /**
     * The sync event this row came from, `deviceId#seq`, or null for rows older than sync.
     *
     * Unique, so applying the same event twice cannot produce two rows however the sync bookkeeping is
     * disturbed — a restored backup, cleared preferences, a peer re-sending a batch it already sent.
     * Rows this device recorded carry their own id too, so its own log is equally safe to replay.
     */
    val syncEventId: String? = null,
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

/**
 * Every headline number for one span of time, from one query (issue #33).
 *
 * Exists because the statistics screen wanted nine scalars and asked for them one at a time, rescanning
 * the same rows for each. They are all sums over the same set, so they come back together.
 *
 * @param rows every recorded listen in the span, including the ones too short to count. Kept because
 *   skip rates need a denominator that includes what was skipped.
 * @param plays the ones that count, by [com.alananasss.kittytune.data.stats.ListenRules].
 * @param completed the ones that reached the end of the track.
 * @param skips left early, without enough being heard. Not simply "did not complete": pausing halfway
 *   through and coming back tomorrow is neither a completion nor a skip.
 */
data class StatsSnapshot(
    val totalListenMs: Long = 0,
    val rows: Int = 0,
    val plays: Int = 0,
    val completed: Int = 0,
    val skips: Int = 0,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    /** Still counted by how the listen ended, because that is what these two actually are. */
    val replays: Int = 0,
    val loops: Int = 0,
    val firstAtMs: Long? = null,
    val lastAtMs: Long? = null,
) {
    val hasData: Boolean get() = rows > 0

    /** Of what was played, how much was heard through. */
    val completionRate: Float get() = if (rows > 0) completed.toFloat() / rows else 0f

    val skipRate: Float get() = if (rows > 0) skips.toFloat() / rows else 0f
}

/** One calendar month that holds listens, for the timeline. */
data class StatsMonth(
    val year: Int,
    val month: Int,
    val plays: Int,
)

/**
 * A track's trim as it is stored: the mode by name, the spans as JSON.
 *
 * Deliberately dumb. Parsing belongs in the repository, so a row whose JSON has been corrupted by a hand edit
 * or a partial write costs that one track's trim rather than failing the query (issue #33).
 */
data class TrackTrimRow(
    val trackId: Long,
    val mode: String,
    val segments: String,
    val updatedAt: Long,
)

// table: library_folders
data class LibraryFolder(
    val id: Long = 0,
    val name: String,
    val parentFolderId: Long? = null,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// table: library_item_meta
data class LibraryItemMeta(
    val itemKey: String,
    val folderId: Long? = null,
    val isPinned: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)


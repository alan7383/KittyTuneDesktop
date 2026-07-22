package com.alananasss.kittytune.data.local

import kotlinx.coroutines.flow.Flow
import java.sql.ResultSet

/**
 * Desktop DownloadDao — same method signatures and SQL as the Room DAO,
 * implemented over the JDBC AppDatabase.
 */
class DownloadDao(private val db: AppDatabase) {

    // --- row mappers -----------------------------------------------------------------------
    private fun track(rs: ResultSet) = LocalTrack(
        id = rs.getLong("id"),
        title = rs.getString("title"),
        artist = rs.getString("artist"),
        artworkUrl = rs.getString("artworkUrl"),
        duration = rs.getLong("duration"),
        localAudioPath = rs.getString("localAudioPath"),
        localArtworkPath = rs.getString("localArtworkPath"),
        downloadedAt = rs.getLong("downloadedAt"),
    )

    private fun playlist(rs: ResultSet) = LocalPlaylist(
        id = rs.getLong("id"),
        title = rs.getString("title"),
        artist = rs.getString("artist"),
        artworkUrl = rs.getString("artworkUrl"),
        trackCount = rs.getInt("trackCount"),
        isUserCreated = rs.getInt("isUserCreated") == 1,
        localCoverPath = rs.getString("localCoverPath"),
        permalinkUrl = rs.getString("permalinkUrl"),
        isAlbum = rs.getInt("isAlbum") == 1,
        addedAt = rs.getLong("addedAt"),
    )

    private fun ref(rs: ResultSet) = PlaylistTrackCrossRef(
        playlistId = rs.getLong("playlistId"),
        trackId = rs.getLong("trackId"),
        addedAt = rs.getLong("addedAt"),
    )

    private fun artist(rs: ResultSet) = LocalArtist(
        id = rs.getLong("id"),
        username = rs.getString("username"),
        avatarUrl = rs.getString("avatarUrl"),
        trackCount = rs.getInt("trackCount"),
        savedAt = rs.getLong("savedAt"),
    )

    private fun history(rs: ResultSet) = HistoryItem(
        id = rs.getString("id"),
        numericId = rs.getLong("numericId"),
        title = rs.getString("title"),
        subtitle = rs.getString("subtitle"),
        imageUrl = rs.getString("imageUrl"),
        type = rs.getString("type"),
        timestamp = rs.getLong("timestamp"),
        isVerified = rs.getInt("isVerified") == 1,
        source = rs.getString("source"),
        originalUrl = rs.getString("originalUrl"),
    )

    private fun topTrack(rs: ResultSet) = TopTrackResult(
        trackId = rs.getLong("trackId"),
        trackTitle = rs.getString("trackTitle"),
        artistName = rs.getString("artistName"),
        artworkUrl = rs.getString("artworkUrl"),
        source = rs.getString("source"),
        playCount = rs.getInt("playCount"),
        totalListenMs = rs.getLong("totalListenMs"),
    )

    private fun topArtist(rs: ResultSet) = TopArtistResult(
        artistName = rs.getString("artistName"),
        artworkUrl = rs.getString("artworkUrl"),
        artistId = rs.getLong("artistId").let { if (rs.wasNull()) null else it },
        artistPermalink = rs.getString("artistPermalink"),
        source = rs.getString("source"),
        playCount = rs.getInt("playCount"),
        totalListenMs = rs.getLong("totalListenMs"),
    )

    private fun statsEvent(rs: ResultSet) = ListeningStatsEvent(
        id = rs.getLong("id"),
        trackId = rs.getLong("trackId"),
        trackTitle = rs.getString("trackTitle"),
        artistName = rs.getString("artistName"),
        artistId = rs.getLong("artistId").let { if (rs.wasNull()) null else it },
        artistPermalink = rs.getString("artistPermalink"),
        artistAvatarUrl = rs.getString("artistAvatarUrl"),
        artworkUrl = rs.getString("artworkUrl"),
        source = rs.getString("source"),
        eventType = rs.getString("eventType"),
        listenDurationMs = rs.getLong("listenDurationMs"),
        trackDurationMs = rs.getLong("trackDurationMs"),
        timestamp = rs.getLong("timestamp"),
    )

    // --- tracks ----------------------------------------------------------------------------
    suspend fun insertTrack(t: LocalTrack) = db.exec(
        "INSERT OR IGNORE INTO downloaded_tracks(id,title,artist,artworkUrl,duration,localAudioPath,localArtworkPath,downloadedAt) VALUES(?,?,?,?,?,?,?,?)",
        t.id, t.title, t.artist, t.artworkUrl, t.duration, t.localAudioPath, t.localArtworkPath, t.downloadedAt,
    )

    suspend fun updateTrack(t: LocalTrack) = db.exec(
        "UPDATE downloaded_tracks SET title=?,artist=?,artworkUrl=?,duration=?,localAudioPath=?,localArtworkPath=?,downloadedAt=? WHERE id=?",
        t.title, t.artist, t.artworkUrl, t.duration, t.localAudioPath, t.localArtworkPath, t.downloadedAt, t.id,
    )

    suspend fun getTrack(trackId: Long): LocalTrack? =
        db.queryOne("SELECT * FROM downloaded_tracks WHERE id = ?", trackId, mapper = ::track)

    suspend fun deleteTrack(trackId: Long) =
        db.exec("DELETE FROM downloaded_tracks WHERE id = ?", trackId)

    fun getAllTracks(): Flow<List<LocalTrack>> = db.observe {
        db.query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' ORDER BY downloadedAt DESC", mapper = ::track)
    }

    suspend fun getAllTracksList(): List<LocalTrack> =
        db.query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' ORDER BY downloadedAt DESC", mapper = ::track)

    // --- playlists -------------------------------------------------------------------------
    suspend fun insertPlaylist(p: LocalPlaylist) = db.exec(
        "INSERT OR REPLACE INTO downloaded_playlists(id,title,artist,artworkUrl,trackCount,isUserCreated,localCoverPath,permalinkUrl,isAlbum,addedAt) VALUES(?,?,?,?,?,?,?,?,?,?)",
        p.id, p.title, p.artist, p.artworkUrl, p.trackCount, p.isUserCreated, p.localCoverPath, p.permalinkUrl, p.isAlbum, p.addedAt,
    )

    suspend fun updatePlaylist(p: LocalPlaylist) = db.exec(
        "UPDATE downloaded_playlists SET title=?,artist=?,artworkUrl=?,trackCount=?,isUserCreated=?,localCoverPath=?,permalinkUrl=?,isAlbum=?,addedAt=? WHERE id=?",
        p.title, p.artist, p.artworkUrl, p.trackCount, p.isUserCreated, p.localCoverPath, p.permalinkUrl, p.isAlbum, p.addedAt, p.id,
    )

    suspend fun insertPlaylistTrackRef(r: PlaylistTrackCrossRef) = db.exec(
        "INSERT OR IGNORE INTO playlist_track_cross_ref(playlistId,trackId,addedAt) VALUES(?,?,?)",
        r.playlistId, r.trackId, r.addedAt,
    )

    suspend fun updatePlaylistTrackRef(r: PlaylistTrackCrossRef) = db.exec(
        "UPDATE playlist_track_cross_ref SET addedAt=? WHERE playlistId=? AND trackId=?",
        r.addedAt, r.playlistId, r.trackId,
    )

    suspend fun getRef(playlistId: Long, trackId: Long): PlaylistTrackCrossRef? =
        db.queryOne("SELECT * FROM playlist_track_cross_ref WHERE playlistId = ? AND trackId = ?", playlistId, trackId, mapper = ::ref)

    suspend fun deletePlaylist(playlistId: Long) =
        db.exec("DELETE FROM downloaded_playlists WHERE id = ?", playlistId)

    suspend fun deletePlaylistRefs(playlistId: Long) =
        db.exec("DELETE FROM playlist_track_cross_ref WHERE playlistId = ?", playlistId)

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) =
        db.exec("DELETE FROM playlist_track_cross_ref WHERE playlistId = ? AND trackId = ?", playlistId, trackId)

    fun getAllPlaylists(): Flow<List<LocalPlaylist>> = db.observe {
        db.query("SELECT * FROM downloaded_playlists", mapper = ::playlist)
    }

    fun getDownloadedPlaylists(): Flow<List<LocalPlaylist>> = db.observe {
        db.query(
            """SELECT DISTINCT P.* FROM downloaded_playlists P
               INNER JOIN playlist_track_cross_ref R ON P.id = R.playlistId
               INNER JOIN downloaded_tracks T ON R.trackId = T.id
               WHERE T.localAudioPath != ''""",
            mapper = ::playlist,
        )
    }

    fun getUserPlaylists(): Flow<List<LocalPlaylist>> = db.observe {
        db.query("SELECT * FROM downloaded_playlists WHERE isUserCreated = 1", mapper = ::playlist)
    }

    suspend fun getPlaylist(playlistId: Long): LocalPlaylist? =
        db.queryOne("SELECT * FROM downloaded_playlists WHERE id = ?", playlistId, mapper = ::playlist)

    fun getPlaylistFlow(playlistId: Long): Flow<LocalPlaylist?> = db.observe {
        db.queryOne("SELECT * FROM downloaded_playlists WHERE id = ?", playlistId, mapper = ::playlist)
    }

    suspend fun updatePlaylistTitle(playlistId: Long, newTitle: String) =
        db.exec("UPDATE downloaded_playlists SET title = ? WHERE id = ?", newTitle, playlistId)

    suspend fun getOrphanTracksList(): List<LocalTrack> = db.query(
        "SELECT * FROM downloaded_tracks WHERE localAudioPath != '' AND id NOT IN (SELECT trackId FROM playlist_track_cross_ref) ORDER BY downloadedAt DESC",
        mapper = ::track,
    )

    fun getTracksForPlaylist(playlistId: Long): Flow<List<LocalTrack>> = db.observe {
        getTracksForPlaylistSync(playlistId)
    }

    suspend fun getTracksForPlaylistSync(playlistId: Long): List<LocalTrack> = db.query(
        """SELECT downloaded_tracks.* FROM downloaded_tracks
           INNER JOIN playlist_track_cross_ref ON downloaded_tracks.id = playlist_track_cross_ref.trackId
           WHERE playlist_track_cross_ref.playlistId = ?
           ORDER BY playlist_track_cross_ref.addedAt ASC""",
        playlistId, mapper = ::track,
    )

    /** trackId -> when it was added to the playlist (cross-ref timestamps). */
    suspend fun getAddedAtForPlaylist(playlistId: Long): Map<Long, Long> = db.query(
        "SELECT trackId, addedAt FROM playlist_track_cross_ref WHERE playlistId = ?",
        playlistId,
    ) { rs -> rs.getLong("trackId") to rs.getLong("addedAt") }.toMap()

    // --- artists ---------------------------------------------------------------------------
    suspend fun insertArtist(a: LocalArtist) = db.exec(
        "INSERT OR REPLACE INTO saved_artists(id,username,avatarUrl,trackCount,savedAt) VALUES(?,?,?,?,?)",
        a.id, a.username, a.avatarUrl, a.trackCount, a.savedAt,
    )

    suspend fun deleteArtist(artistId: Long) =
        db.exec("DELETE FROM saved_artists WHERE id = ?", artistId)

    suspend fun getArtist(artistId: Long): LocalArtist? =
        db.queryOne("SELECT * FROM saved_artists WHERE id = ?", artistId, mapper = ::artist)

    fun getArtistFlow(artistId: Long): Flow<LocalArtist?> = db.observe {
        db.queryOne("SELECT * FROM saved_artists WHERE id = ?", artistId, mapper = ::artist)
    }

    fun getAllSavedArtists(): Flow<List<LocalArtist>> = db.observe {
        db.query("SELECT * FROM saved_artists ORDER BY savedAt DESC", mapper = ::artist)
    }

    // --- history ---------------------------------------------------------------------------
    suspend fun insertHistory(item: HistoryItem) = db.exec(
        "INSERT OR REPLACE INTO play_history(id,numericId,title,subtitle,imageUrl,type,timestamp,isVerified,source,originalUrl) VALUES(?,?,?,?,?,?,?,?,?,?)",
        item.id, item.numericId, item.title, item.subtitle, item.imageUrl, item.type, item.timestamp, item.isVerified, item.source, item.originalUrl,
    )

    fun getHistory(): Flow<List<HistoryItem>> = db.observe {
        db.query("SELECT * FROM play_history ORDER BY timestamp DESC LIMIT 20", mapper = ::history)
    }

    suspend fun deleteHistoryItem(itemId: String) =
        db.exec("DELETE FROM play_history WHERE id = ?", itemId)

    suspend fun clearHistory() = db.exec("DELETE FROM play_history")

    // --- listening stats -------------------------------------------------------------------
    suspend fun insertStatsEvent(e: ListeningStatsEvent) = db.exec(
        "INSERT INTO listening_stats(trackId,trackTitle,artistName,artistId,artistPermalink,artistAvatarUrl,artworkUrl,source,eventType,listenDurationMs,trackDurationMs,timestamp) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        e.trackId, e.trackTitle, e.artistName, e.artistId, e.artistPermalink, e.artistAvatarUrl, e.artworkUrl, e.source, e.eventType, e.listenDurationMs, e.trackDurationMs, e.timestamp,
    )

    suspend fun getEventsAfter(since: Long): List<ListeningStatsEvent> =
        db.query("SELECT * FROM listening_stats WHERE timestamp >= ? ORDER BY timestamp DESC", since, mapper = ::statsEvent)

    suspend fun getTopTracksAfter(since: Long, limit: Int = 10): List<TopTrackResult> = db.query(
        "SELECT trackId, trackTitle, artistName, artworkUrl, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= ? AND eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP') GROUP BY trackId ORDER BY playCount DESC LIMIT ?",
        since, limit, mapper = ::topTrack,
    )

    suspend fun getTopArtistsAfter(since: Long, limit: Int = 10): List<TopArtistResult> = db.query(
        "SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= ? AND eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP') GROUP BY artistName HAVING SUM(listenDurationMs) >= 60000 ORDER BY totalListenMs DESC LIMIT ?",
        since, limit, mapper = ::topArtist,
    )

    suspend fun getTopTracksBetween(since: Long, until: Long, limit: Int = 1): List<TopTrackResult> = db.query(
        "SELECT trackId, trackTitle, artistName, artworkUrl, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= ? AND timestamp < ? AND eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP') GROUP BY trackId ORDER BY playCount DESC LIMIT ?",
        since, until, limit, mapper = ::topTrack,
    )

    suspend fun getTopArtistsBetween(since: Long, until: Long, limit: Int = 1): List<TopArtistResult> = db.query(
        "SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= ? AND timestamp < ? AND eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP') GROUP BY artistName HAVING SUM(listenDurationMs) >= 60000 ORDER BY totalListenMs DESC LIMIT ?",
        since, until, limit, mapper = ::topArtist,
    )

    suspend fun getTotalListenTimeAfter(since: Long): Long =
        db.scalarLong("SELECT COALESCE(SUM(listenDurationMs), 0) FROM listening_stats WHERE timestamp >= ?", since)

    suspend fun getEventCountByType(type: String, since: Long): Int =
        db.scalarInt("SELECT COUNT(*) FROM listening_stats WHERE eventType = ? AND timestamp >= ?", type, since)

    suspend fun getTotalEventsAfter(since: Long): Int =
        db.scalarInt("SELECT COUNT(*) FROM listening_stats WHERE timestamp >= ?", since)

    suspend fun getUniqueTracksAfter(since: Long): Int =
        db.scalarInt("SELECT COUNT(*) FROM (SELECT trackId FROM listening_stats WHERE timestamp >= ? GROUP BY trackId HAVING SUM(listenDurationMs) > 3000) AS filtered_tracks", since)

    suspend fun getUniqueArtistsAfter(since: Long): Int =
        db.scalarInt("SELECT COUNT(DISTINCT artistName) FROM listening_stats WHERE timestamp >= ?", since)

    suspend fun clearStats() = db.exec("DELETE FROM listening_stats")
}

/** Desktop RecognitionHistoryDao — same signatures & SQL as the Room DAO. */
class RecognitionHistoryDao(private val db: AppDatabase) {

    private fun item(rs: ResultSet) = RecognitionHistoryItem(
        id = rs.getLong("id"),
        trackId = rs.getLong("trackId").let { if (rs.wasNull()) null else it },
        title = rs.getString("title"),
        artist = rs.getString("artist"),
        artworkUrl = rs.getString("artworkUrl"),
        timestamp = rs.getLong("timestamp"),
    )

    suspend fun insertItem(i: RecognitionHistoryItem) = db.exec(
        "INSERT OR REPLACE INTO recognition_history(trackId,title,artist,artworkUrl,timestamp) VALUES(?,?,?,?,?)",
        i.trackId, i.title, i.artist, i.artworkUrl, i.timestamp,
    )

    fun getAllItems(): Flow<List<RecognitionHistoryItem>> = db.observe {
        db.query("SELECT * FROM recognition_history ORDER BY timestamp DESC", mapper = ::item)
    }

    suspend fun clearHistory() = db.exec("DELETE FROM recognition_history")

    suspend fun deleteItem(itemId: Long) =
        db.exec("DELETE FROM recognition_history WHERE id = ?", itemId)
}

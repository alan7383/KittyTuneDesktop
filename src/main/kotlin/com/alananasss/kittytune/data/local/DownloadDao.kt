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
        isDownloaded = runCatching { rs.getInt("isDownloaded") == 1 }.getOrDefault(false)
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
        furthestPositionMs = rs.getLong("furthestPositionMs"),
        syncEventId = rs.getString("syncEventId"),
    )

    private fun trackTrim(rs: ResultSet) = TrackTrimRow(
        trackId = rs.getLong("trackId"),
        mode = rs.getString("mode"),
        segments = rs.getString("segments"),
        updatedAt = rs.getLong("updatedAt"),
    )

    private fun statsSnapshot(rs: ResultSet) = StatsSnapshot(
        totalListenMs = rs.getLong("totalListenMs"),
        rows = rs.getInt("rowCount"),
        plays = rs.getInt("plays"),
        completed = rs.getInt("completed"),
        skips = rs.getInt("skips"),
        uniqueTracks = rs.getInt("uniqueTracks"),
        uniqueArtists = rs.getInt("uniqueArtists"),
        replays = rs.getInt("replays"),
        loops = rs.getInt("loops"),
        firstAtMs = rs.getLong("firstAtMs").let { if (rs.wasNull()) null else it },
        lastAtMs = rs.getLong("lastAtMs").let { if (rs.wasNull()) null else it },
    )

    private fun statsMonth(rs: ResultSet) = StatsMonth(
        year = rs.getInt("y"),
        month = rs.getInt("m"),
        plays = rs.getInt("plays"),
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

    suspend fun deleteAll() {
        db.exec("DELETE FROM downloaded_tracks")
        db.exec("DELETE FROM downloaded_playlists")
        db.exec("DELETE FROM playlist_track_cross_ref")
    }

    fun getAllTracks(): Flow<List<LocalTrack>> = db.observe {
        db.query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' ORDER BY downloadedAt DESC", mapper = ::track)
    }

    suspend fun getAllTracksList(): List<LocalTrack> =
        db.query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' ORDER BY downloadedAt DESC", mapper = ::track)

    suspend fun getAllStoredTracksList(): List<LocalTrack> =
        db.query("SELECT * FROM downloaded_tracks", mapper = ::track)

    // --- playlists -------------------------------------------------------------------------
    suspend fun insertPlaylist(p: LocalPlaylist) = db.exec(
        "INSERT OR REPLACE INTO downloaded_playlists(id,title,artist,artworkUrl,trackCount,isUserCreated,localCoverPath,permalinkUrl,isAlbum,addedAt,isDownloaded) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        p.id, p.title, p.artist, p.artworkUrl, p.trackCount, p.isUserCreated, p.localCoverPath, p.permalinkUrl, p.isAlbum, p.addedAt, if (p.isDownloaded) 1 else 0
    )

    suspend fun updatePlaylist(p: LocalPlaylist) = db.exec(
        "UPDATE downloaded_playlists SET title=?,artist=?,artworkUrl=?,trackCount=?,isUserCreated=?,localCoverPath=?,permalinkUrl=?,isAlbum=?,addedAt=?,isDownloaded=? WHERE id=?",
        p.title, p.artist, p.artworkUrl, p.trackCount, p.isUserCreated, p.localCoverPath, p.permalinkUrl, p.isAlbum, p.addedAt, if (p.isDownloaded) 1 else 0, p.id
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

    suspend fun setPlaylistDownloaded(playlistId: Long, isDownloaded: Boolean) =
        db.exec("UPDATE downloaded_playlists SET isDownloaded = ? WHERE id = ?", if (isDownloaded) 1 else 0, playlistId)

    fun getDownloadedPlaylists(): Flow<List<LocalPlaylist>> = db.observe {
        db.query("""
            SELECT DISTINCT P.* FROM downloaded_playlists P
            LEFT JOIN playlist_track_cross_ref R ON R.playlistId = P.id
            LEFT JOIN downloaded_tracks T ON T.id = R.trackId AND T.localAudioPath != ''
            WHERE P.isDownloaded = 1 OR T.id IS NOT NULL
            ORDER BY P.addedAt DESC
        """, mapper = ::playlist)
    }

    suspend fun cleanEmptyDownloadedPlaylists() = db.exec("""
        UPDATE downloaded_playlists
        SET isDownloaded = 0
        WHERE isDownloaded = 1
          AND id NOT IN (
            SELECT DISTINCT R.playlistId
            FROM playlist_track_cross_ref R
            INNER JOIN downloaded_tracks T ON T.id = R.trackId
            WHERE T.localAudioPath != ''
          )
    """)

    suspend fun getDownloadedPlaylistRefCount(trackId: Long, excludePlaylistId: Long): Int =
        db.queryOne(
            """SELECT COUNT(*) FROM playlist_track_cross_ref R
               INNER JOIN downloaded_playlists P ON R.playlistId = P.id
               WHERE R.trackId = ? AND P.id != ? AND P.isDownloaded = 1""",
            trackId, excludePlaylistId
        ) { rs -> rs.getInt(1) } ?: 0

    fun getUserPlaylists(): Flow<List<LocalPlaylist>> = db.observe {
        db.query("SELECT * FROM downloaded_playlists WHERE isUserCreated = 1 OR id < 0", mapper = ::playlist)
    }

    suspend fun fixNegativeIdPlaylistsUserCreated() =
        db.exec("UPDATE downloaded_playlists SET isUserCreated = 1 WHERE id < 0")

    suspend fun updateHistoryItemImageUrl(itemId: String, newImageUrl: String) =
        db.exec("UPDATE play_history SET imageUrl = ? WHERE id = ?", newImageUrl, itemId)

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

    suspend fun cleanUnreferencedEmptyTracks() =
        db.exec("DELETE FROM downloaded_tracks WHERE localAudioPath = '' AND id NOT IN (SELECT trackId FROM playlist_track_cross_ref)")

    suspend fun deleteNonDownloadedOnlinePlaylists() =
        db.exec("DELETE FROM downloaded_playlists WHERE id > 0 AND isDownloaded = 0 AND (permalinkUrl IS NULL OR permalinkUrl NOT LIKE '%spotify%')")

    suspend fun getPlaylistRefCount(trackId: Long): Int =
        db.queryOne("SELECT COUNT(*) FROM playlist_track_cross_ref WHERE trackId = ?", trackId) { rs -> rs.getInt(1) } ?: 0

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

    suspend fun insertArtists(artists: List<LocalArtist>) {
        if (artists.isEmpty()) return
        for (i in 0 until artists.size - 1) {
            val a = artists[i]
            db.execSilent(
                "INSERT OR REPLACE INTO saved_artists(id,username,avatarUrl,trackCount,savedAt) VALUES(?,?,?,?,?)",
                a.id, a.username, a.avatarUrl, a.trackCount, a.savedAt,
            )
        }
        val last = artists.last()
        db.exec(
            "INSERT OR REPLACE INTO saved_artists(id,username,avatarUrl,trackCount,savedAt) VALUES(?,?,?,?,?)",
            last.id, last.username, last.avatarUrl, last.trackCount, last.savedAt,
        )
    }

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

    suspend fun getHistoryItemById(numericId: Long, id: String): HistoryItem? =
        db.queryOne("SELECT * FROM play_history WHERE numericId = ? OR id = ? LIMIT 1", numericId, id, mapper = ::history)

    suspend fun deleteHistoryItem(itemId: String) =
        db.exec("DELETE FROM play_history WHERE id = ?", itemId)

    suspend fun clearHistory() = db.exec("DELETE FROM play_history")

    suspend fun clearTracksHistory() = db.exec("DELETE FROM play_history WHERE type = 'TRACK'")

    suspend fun clearContextsHistory() = db.exec("DELETE FROM play_history WHERE type != 'TRACK'")

    suspend fun insertHistoryList(items: List<HistoryItem>) {
        for (item in items) insertHistory(item)
    }

    fun getHistoryUnlimited(): Flow<List<HistoryItem>> = db.observe {
        db.query("SELECT * FROM play_history ORDER BY timestamp DESC", mapper = ::history)
    }

    // --- listening stats -------------------------------------------------------------------
    /**
     * Every listening aggregate asks the same question — was enough of this track heard? — instead of
     * asking how it ended (issue #33).
     *
     * The filter used to be `eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP')`, so a
     * track played to its last ten seconds and then skipped counted for nothing while one that ran out
     * on its own counted fully. Same listening, different statistics, depending on which button was
     * pressed: that is the "sometimes tracks just don't appear" report. The rule now lives in exactly
     * one place, [StatsSql], and is shared with the phone so both report the same numbers from the same
     * rows.
     */
    private val playRule = com.alananasss.kittytune.data.stats.StatsSql.COUNTS_AS_PLAY

    /**
     * `INSERT OR IGNORE`, keyed on the sync event id.
     *
     * Applying a synced listen twice is not merely unlikely, it is impossible: the id is unique, so the
     * second attempt is dropped by the database rather than by whatever bookkeeping happened to be
     * intact. Rows recorded on this device carry an id as well, which is what makes replaying our own
     * log safe too (issue #33).
     */
    suspend fun insertStatsEvent(e: ListeningStatsEvent) = db.exec(INSERT_STATS_EVENT, *statsRow(e))

    /**
     * A whole batch in one transaction, for what arrives from another device.
     *
     * @return how many rows were actually new. The `OR IGNORE` means the answer is not the size of the
     *   batch, and the screen reports what it received rather than what it was offered (issue #33).
     */
    suspend fun insertStatsEvents(events: List<ListeningStatsEvent>): Int {
        if (events.isEmpty()) return 0
        return db.execBatch(INSERT_STATS_EVENT, events.map { statsRow(it) })
    }

    private fun statsRow(e: ListeningStatsEvent): Array<out Any?> = arrayOf<Any?>(
        e.trackId, e.trackTitle, e.artistName, e.artistId, e.artistPermalink, e.artistAvatarUrl,
        e.artworkUrl, e.source, e.eventType, e.listenDurationMs, e.trackDurationMs, e.timestamp,
        e.furthestPositionMs, e.syncEventId,
    )

    /**
     * Every number the statistics header shows, in one query (issue #33).
     *
     * This replaces nine separate scalar queries. Nine round trips each rescanning the same rows is
     * what made the screen take seconds to open, and it got worse with every listen — and worse again
     * once a second device's history started landing in the same table. One pass over one index range
     * produces all of it, because every one of those numbers is a different `SUM` over the same rows.
     *
     * @param until exclusive upper bound, or null for "up to now".
     */
    suspend fun getStatsSnapshot(since: Long, until: Long? = null): StatsSnapshot {
        val complete = com.alananasss.kittytune.data.stats.StatsSql.IS_COMPLETE
        val bound = if (until == null) "" else " AND timestamp < ?"
        val sql = """
            SELECT
              COALESCE(SUM(listenDurationMs), 0) AS totalListenMs,
              COUNT(*) AS rowCount,
              COALESCE(SUM(CASE WHEN $playRule THEN 1 ELSE 0 END), 0) AS plays,
              COALESCE(SUM(CASE WHEN $complete THEN 1 ELSE 0 END), 0) AS completed,
              COALESCE(SUM(CASE WHEN NOT $complete AND NOT $playRule THEN 1 ELSE 0 END), 0) AS skips,
              COUNT(DISTINCT CASE WHEN $playRule THEN trackId END) AS uniqueTracks,
              COUNT(DISTINCT CASE WHEN $playRule THEN artistName END) AS uniqueArtists,
              COALESCE(SUM(CASE WHEN eventType = 'MANUAL_REPLAY' THEN 1 ELSE 0 END), 0) AS replays,
              COALESCE(SUM(CASE WHEN eventType = 'REPEAT_ONE_LOOP' THEN 1 ELSE 0 END), 0) AS loops,
              MIN(timestamp) AS firstAtMs,
              MAX(timestamp) AS lastAtMs
            FROM listening_stats WHERE timestamp >= ?$bound
        """.trimIndent()
        val args: Array<Any?> = if (until == null) arrayOf(since) else arrayOf(since, until)
        return db.queryOne(sql, *args, mapper = ::statsSnapshot) ?: StatsSnapshot()
    }

    /**
     * Which months hold anything, newest first, and how much.
     *
     * The timeline used to discover this by walking a month at a time and counting the *whole table*
     * twice per step to guess whether to keep going — and when a month turned up empty it called itself
     * again, so a gap in the history could spin. One grouped query answers it exactly, and the timeline
     * then only asks about months it already knows have something in them (issue #33).
     */
    suspend fun getStatsMonths(): List<StatsMonth> = db.query(
        """
        SELECT
          CAST(strftime('%Y', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS y,
          CAST(strftime('%m', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS m,
          COUNT(*) AS plays
        FROM listening_stats
        WHERE $playRule
        GROUP BY y, m
        ORDER BY y DESC, m DESC
        """.trimIndent(),
        mapper = ::statsMonth,
    )

    suspend fun getEventsAfter(since: Long): List<ListeningStatsEvent> =
        db.query("SELECT * FROM listening_stats WHERE timestamp >= ? ORDER BY timestamp DESC", since, mapper = ::statsEvent)

    suspend fun getTopTracksAfter(since: Long, limit: Int = 10): List<TopTrackResult> = db.query(
        "SELECT trackId, trackTitle, artistName, MAX(artworkUrl) as artworkUrl, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= ? AND $playRule GROUP BY trackId ORDER BY totalListenMs DESC LIMIT ?",
        since, limit, mapper = ::topTrack,
    )

    suspend fun getTopArtistsAfter(since: Long, limit: Int = 10): List<TopArtistResult> = db.query(
        "SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= ? AND $playRule GROUP BY artistName ORDER BY totalListenMs DESC LIMIT ?",
        since, limit, mapper = ::topArtist,
    )

    suspend fun getTopTracksBetween(since: Long, until: Long, limit: Int = 1): List<TopTrackResult> = db.query(
        "SELECT trackId, trackTitle, artistName, MAX(artworkUrl) as artworkUrl, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= ? AND timestamp < ? AND $playRule GROUP BY trackId ORDER BY totalListenMs DESC LIMIT ?",
        since, until, limit, mapper = ::topTrack,
    )

    suspend fun getTopArtistsBetween(since: Long, until: Long, limit: Int = 1): List<TopArtistResult> = db.query(
        "SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= ? AND timestamp < ? AND $playRule GROUP BY artistName ORDER BY totalListenMs DESC LIMIT ?",
        since, until, limit, mapper = ::topArtist,
    )

    suspend fun getTotalListenTimeAfter(since: Long): Long =
        db.scalarLong("SELECT COALESCE(SUM(listenDurationMs), 0) FROM listening_stats WHERE timestamp >= ?", since)

    suspend fun getEventCountByType(type: String, since: Long): Int =
        db.scalarInt("SELECT COUNT(*) FROM listening_stats WHERE eventType = ? AND timestamp >= ?", type, since)

    suspend fun getTotalEventsAfter(since: Long): Int =
        db.scalarInt("SELECT COUNT(*) FROM listening_stats WHERE timestamp >= ?", since)

    suspend fun getUniqueTracksAfter(since: Long): Int =
        db.scalarInt("SELECT COUNT(DISTINCT trackId) FROM listening_stats WHERE timestamp >= ? AND $playRule", since)

    suspend fun getUniqueArtistsAfter(since: Long): Int =
        db.scalarInt("SELECT COUNT(DISTINCT artistName) FROM listening_stats WHERE timestamp >= ? AND $playRule", since)

    suspend fun clearStats() = db.exec("DELETE FROM listening_stats")

    // --- trim / smart skip -----------------------------------------------------------------------
    /**
     * One row per trimmed track, the spans held as JSON.
     *
     * A column rather than a second table because the spans are only ever read and written together, for one
     * track, as a set. A join to fetch two or three timestamps would be work for nothing (issue #33).
     */
    suspend fun getTrackTrim(trackId: Long): TrackTrimRow? = db.queryOne(
        "SELECT * FROM track_trim WHERE trackId = ?", trackId, mapper = ::trackTrim,
    )

    suspend fun getTrimmedTrackIds(): List<Long> =
        db.query("SELECT trackId FROM track_trim", mapper = { it.getLong("trackId") })

    suspend fun putTrackTrim(row: TrackTrimRow) = db.exec(
        "INSERT OR REPLACE INTO track_trim(trackId,mode,segments,updatedAt) VALUES(?,?,?,?)",
        row.trackId, row.mode, row.segments, row.updatedAt,
    )

    suspend fun deleteTrackTrim(trackId: Long) =
        db.exec("DELETE FROM track_trim WHERE trackId = ?", trackId)

    private companion object {
        const val INSERT_STATS_EVENT =
            "INSERT OR IGNORE INTO listening_stats(trackId,trackTitle,artistName,artistId," +
                "artistPermalink,artistAvatarUrl,artworkUrl,source,eventType,listenDurationMs," +
                "trackDurationMs,timestamp,furthestPositionMs,syncEventId) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
    }
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

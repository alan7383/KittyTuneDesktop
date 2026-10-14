package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.core.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

object AppDatabase {

    private val dbFile = File(AppDirs.dataDir, "soundtune_db.sqlite")
    private lateinit var conn: Connection

    /**
     * One thread for the one shared connection. Every statement and transaction below
     * runs here, so an [execBatch]/[execTogether] transaction can never interleave with
     * another write on another IO thread: previously a concurrent exec could commit the
     * batch's half-applied rows, or land inside the transaction and be rolled back with
     * it (lost likes, issue #33). Reads keep their cache-first path and stay cheap.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** Raw-connection access point for callers outside the helpers (e.g. loudness repo):
     * hop onto the same single thread so autoCommit toggling stays race-free. */
    internal suspend fun <T> withDb(block: () -> T): T = withContext(dbDispatcher) { block() }

    private val invalidation = MutableStateFlow(0L)

    private fun invalidate() {
        invalidation.value = invalidation.value + 1
    }

    val downloadDao: DownloadDao by lazy { DownloadDao(this) }
    val recognitionHistoryDao: RecognitionHistoryDao by lazy { RecognitionHistoryDao(this) }
    val albumCacheDao: AlbumCacheDao by lazy { AlbumCacheDao(this) }
    val folderDao: FolderDao by lazy { FolderDao(this) }
    val beatInfoDao: BeatInfoDao by lazy { BeatInfoDao(this) }
    val recentSearchDao: RecentSearchDao by lazy { RecentSearchDao(this) }
    val blockedContentDao: BlockedContentDao by lazy { BlockedContentDao(this) }
    val likeDao: LikeDao by lazy { LikeDao(this) }

    fun init() {
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        conn.autoCommit = true
        conn.createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute("PRAGMA foreign_keys=ON")
            st.execute("PRAGMA synchronous=NORMAL")
            st.execute("PRAGMA busy_timeout=5000")
        }
        createSchema()
    }

    private fun createSchema() {
        val ddl = listOf(
            """CREATE TABLE IF NOT EXISTS downloaded_tracks (
                id INTEGER PRIMARY KEY NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL,
                artworkUrl TEXT NOT NULL, duration INTEGER NOT NULL, localAudioPath TEXT NOT NULL,
                localArtworkPath TEXT NOT NULL, downloadedAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS downloaded_playlists (
                id INTEGER PRIMARY KEY NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL,
                artworkUrl TEXT NOT NULL, trackCount INTEGER NOT NULL, isUserCreated INTEGER NOT NULL DEFAULT 0,
                localCoverPath TEXT, permalinkUrl TEXT, isAlbum INTEGER NOT NULL DEFAULT 0, addedAt INTEGER NOT NULL,
                isDownloaded INTEGER NOT NULL DEFAULT 0)""",
            """CREATE TABLE IF NOT EXISTS playlist_track_cross_ref (
                playlistId INTEGER NOT NULL, trackId INTEGER NOT NULL, addedAt INTEGER NOT NULL,
                PRIMARY KEY (playlistId, trackId))""",
            """CREATE TABLE IF NOT EXISTS saved_artists (
                id INTEGER PRIMARY KEY NOT NULL, username TEXT NOT NULL, avatarUrl TEXT NOT NULL,
                trackCount INTEGER NOT NULL, savedAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS play_history (
                id TEXT PRIMARY KEY NOT NULL, numericId INTEGER NOT NULL, title TEXT NOT NULL,
                subtitle TEXT NOT NULL, imageUrl TEXT NOT NULL, type TEXT NOT NULL, timestamp INTEGER NOT NULL,
                isVerified INTEGER NOT NULL DEFAULT 0, source TEXT NOT NULL DEFAULT 'soundcloud', originalUrl TEXT)""",
            """CREATE TABLE IF NOT EXISTS recognition_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, trackId INTEGER, title TEXT NOT NULL,
                artist TEXT NOT NULL, artworkUrl TEXT, timestamp INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS listening_stats (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, trackId INTEGER NOT NULL, trackTitle TEXT NOT NULL,
                artistName TEXT NOT NULL, artistId INTEGER, artistPermalink TEXT, artistAvatarUrl TEXT,
                artworkUrl TEXT NOT NULL, source TEXT NOT NULL DEFAULT 'soundcloud', eventType TEXT NOT NULL,
                listenDurationMs INTEGER NOT NULL DEFAULT 0, trackDurationMs INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL, furthestPositionMs INTEGER NOT NULL DEFAULT 0,
                syncEventId TEXT)""",
            """CREATE TABLE IF NOT EXISTS track_album_cache (
                trackId INTEGER PRIMARY KEY NOT NULL, albumPlaylistId INTEGER, albumTitle TEXT,
                resolvedAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS beat_info (
                songId TEXT PRIMARY KEY NOT NULL, bpm REAL NOT NULL, firstBeatOffsetMs INTEGER NOT NULL,
                confidence REAL NOT NULL, analyzedAt INTEGER NOT NULL, mixInPointMs INTEGER,
                mixOutPointMs INTEGER, keyPitchClass INTEGER, keyIsMinor INTEGER)""",
            """CREATE TABLE IF NOT EXISTS library_folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, parentFolderId INTEGER,
                isPinned INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS track_trim (
                trackId INTEGER PRIMARY KEY NOT NULL, mode TEXT NOT NULL, segments TEXT NOT NULL,
                updatedAt INTEGER NOT NULL)""",
            // One row per track whose untimed-lyrics scroll speed was set by hand. Absent means
            // "use the global speed", which is almost every track, so nothing is written by default
            // (issue #33).
            """CREATE TABLE IF NOT EXISTS lyrics_scroll_speed (
                trackId INTEGER PRIMARY KEY NOT NULL, speed REAL NOT NULL, updatedAt INTEGER NOT NULL)""",
            // One row per track whose lyrics sync offset was set by hand. Absent means 0L (no offset).
            """CREATE TABLE IF NOT EXISTS lyrics_offset (
                trackId INTEGER PRIMARY KEY NOT NULL, offsetMs INTEGER NOT NULL, updatedAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS track_loudness (
                trackId INTEGER PRIMARY KEY NOT NULL, integratedLufs REAL NOT NULL,
                truePeakDb REAL NOT NULL, updatedAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS library_item_meta (
                itemKey TEXT PRIMARY KEY NOT NULL, folderId INTEGER, isPinned INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL)""",
            // The searches this listener has run, newest first, capped at RecentSearchRepository.MAX_ENTRIES.
            // The term is the key, so searching the same thing twice reorders one row rather than
            // stacking two identical ones (issue #56).
            """CREATE TABLE IF NOT EXISTS recent_search (
                query TEXT PRIMARY KEY NOT NULL, timestamp INTEGER NOT NULL)""",
            // Hidden tracks and blocked artists (issue #41, ported from Android).
            // Exactly one of trackId / artistId is non-null per row; reason is
            // "MANUAL" or "AI_GENERATED". Unique indexes mirror the Room entity.
            """CREATE TABLE IF NOT EXISTS blocked_content (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, trackId INTEGER,
                trackTitle TEXT, trackArtworkUrl TEXT, artistId INTEGER,
                artistName TEXT, artistAvatarUrl TEXT, source TEXT NOT NULL DEFAULT 'soundcloud',
                reason TEXT NOT NULL DEFAULT 'MANUAL', blockedAt INTEGER NOT NULL)""",
            // The liked-tracks library, one row per like (RAM, issue #33). It used to live as a
            // single JSON string inside the preferences map — resident for the process lifetime,
            // re-serialised in full on every save, and re-encoded into the whole prefs file each
            // time. A row per track writes and reads only what changed, and the RAM copy is the
            // StateFlow alone. likedAt is a column only so the library can come back out in the
            // order it was saved in; the track itself (including its own likedAt) is the JSON.
            """CREATE TABLE IF NOT EXISTS liked_tracks (
                trackId INTEGER PRIMARY KEY NOT NULL, likedAt INTEGER NOT NULL,
                trackJson TEXT NOT NULL)""",
        )
        conn.createStatement().use { st ->
            ddl.forEach { st.execute(it) }
            try {
                st.execute("ALTER TABLE downloaded_playlists ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            // A lyrics sync can have a second point (see LyricsSync); single offsets leave these empty.
            for (column in listOf("anchorMs INTEGER NOT NULL DEFAULT 0", "endAtMs INTEGER", "endOffsetMs INTEGER")) {
                try {
                    st.execute("ALTER TABLE lyrics_offset ADD COLUMN $column")
                } catch (_: Exception) {}
            }
            // A saved track's source and page, for tracks that are not SoundCloud's (see LocalTrack.source).
            for (column in listOf("source TEXT", "permalinkUrl TEXT")) {
                try {
                    st.execute("ALTER TABLE downloaded_tracks ADD COLUMN $column")
                } catch (_: Exception) {}
            }
            // How energetic a track is, for choosing how to blend it (see AutomixManager); older rows have none.
            try {
                st.execute("ALTER TABLE beat_info ADD COLUMN energy REAL")
            } catch (_: Exception) {}
            st.execute("UPDATE library_folders SET parentFolderId = NULL WHERE parentFolderId = 0")
            st.execute("UPDATE library_item_meta SET folderId = NULL WHERE folderId = 0")

            // Every listening-statistics query filters on timestamp, and several also group by
            // eventType. Without these the statistics screen ran eleven full scans of the whole
            // table each time it opened, which is why it took so long — and it only got worse as the
            // table grew, now that synced events land in it too (issue #33).
            // Added after the table shipped, so existing installs need them bolted on. Failing is
            // the normal case on the second launch — the column is already there.
            for (column in listOf(
                "furthestPositionMs INTEGER NOT NULL DEFAULT 0",
                "syncEventId TEXT",
            )) {
                try {
                    st.execute("ALTER TABLE listening_stats ADD COLUMN $column")
                } catch (_: Exception) {}
            }

            st.execute("CREATE INDEX IF NOT EXISTS idx_stats_timestamp ON listening_stats(timestamp)")
            st.execute(
                "CREATE INDEX IF NOT EXISTS idx_stats_event_timestamp " +
                    "ON listening_stats(eventType, timestamp)"
            )
            st.execute("CREATE INDEX IF NOT EXISTS idx_stats_track ON listening_stats(trackId)")
            st.execute("CREATE INDEX IF NOT EXISTS idx_stats_artist ON listening_stats(artistName)")
            // Unique indexes for the blocked-content table (Room parity: one block per
            // track / artist, INSERT OR IGNORE keeps re-blocks idempotent).
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_blocked_track ON blocked_content(trackId)")
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_blocked_artist ON blocked_content(artistId)")
            // What makes applying a synced listen twice impossible rather than merely unlikely. SQLite
            // treats NULLs as distinct in a unique index, so the rows from before sync existed — all of
            // which have no id — do not collide with each other (issue #33).
            try {
                st.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_stats_sync_event " +
                        "ON listening_stats(syncEventId)"
                )
            } catch (_: Exception) {
                // An existing install can already hold duplicates from before the index existed. The
                // index is an optimisation on top of INSERT OR IGNORE, not the only defence, so a
                // table that cannot take it still syncs correctly — it just keeps the duplicates it
                // already has.
            }
        }
    }


    internal suspend fun <T> query(sql: String, vararg args: Any?, mapper: (ResultSet) -> T): List<T> =
        withContext(dbDispatcher) {
            conn.prepareStatement(sql).use { ps ->
                bind(ps, args)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapper(rs)) }
                }
            }
        }

    internal suspend fun <T> queryOne(sql: String, vararg args: Any?, mapper: (ResultSet) -> T): T? =
        query(sql, *args, mapper = mapper).firstOrNull()

    internal suspend fun exec(sql: String, vararg args: Any?) = withContext(dbDispatcher) {
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeUpdate()
        }
        invalidate()
    }

    /**
     * One statement, many rows, one transaction, one invalidation.
     *
     * Autocommit makes every [exec] its own transaction, which is the right default for a single write and
     * exactly wrong for a batch: a first sync carrying a year of listening would be thousands of commits
     * and thousands of cache invalidations, each waking every screen watching the database. Here it is one
     * of each (issue #33).
     *
     * The transaction runs on the one shared connection, on the single [dbDispatcher] thread —
     * a write issued from elsewhere now queues behind it instead of joining it, so nothing can
     * be rolled back with it that did not ask to be. The only way this rolls back is a failing
     * disk, and the alternative — a commit per row — is the cost this exists to remove.
     *
     * @return how many rows the statement actually changed. With `INSERT OR IGNORE` a duplicate reports
     *   zero, so this is the count of genuinely new rows and no separate counting query is needed.
     */
    internal suspend fun execBatch(sql: String, rows: List<Array<out Any?>>): Int {
        if (rows.isEmpty()) return 0
        val changed = withContext(dbDispatcher) {
            val previousAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                val counts = conn.prepareStatement(sql).use { ps ->
                    for (row in rows) {
                        bind(ps, row)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                conn.commit()
                // SUCCESS_NO_INFO (-2) means "it worked but I am not saying how many"; counted as one,
                // which is the only sensible reading for a single-row insert.
                counts.sumOf { if (it < 0) if (it == java.sql.Statement.SUCCESS_NO_INFO) 1 else 0 else it }
            } catch (t: Throwable) {
                runCatching { conn.rollback() }
                throw t
            } finally {
                conn.autoCommit = previousAutoCommit
            }
        }
        invalidate()
        return changed
    }

    internal suspend fun execSilent(sql: String, vararg args: Any?) = withContext(dbDispatcher) {
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeUpdate()
        }
    }

    /**
     * Arbitrary statements, one transaction, on the single [dbDispatcher] thread.
     *
     * For the writes that do not fit the shaped helpers — a replace-all is a DELETE plus a batch
     * INSERT, and doing them as two [exec]s would leave a window where a crash drops the table to
     * empty between the two commits. Same autoCommit discipline as [execBatch]/[execTogether];
     * no invalidation, because the one caller so far (the likes table) has nothing observing it.
     */
    internal suspend fun <T> execTransaction(block: () -> T): T = withContext(dbDispatcher) {
        val previousAutoCommit = conn.autoCommit
        conn.autoCommit = false
        try {
            val result = block()
            conn.commit()
            result
        } catch (t: Throwable) {
            runCatching { conn.rollback() }
            throw t
        } finally {
            conn.autoCommit = previousAutoCommit
        }
    }

    /**
     * Several statements, one transaction, one invalidation.
     *
     * For the writes where the second statement is what makes the first one correct — adding a row
     * and then dropping the ones that no longer fit under the cap. Under [exec] each of them would be
     * its own commit and its own invalidation, so a crash between them would leave the table one row
     * over the cap, and every screen watching the database would recompute twice for one keystroke.
     *
     * The transaction runs on the one shared connection, on the single [dbDispatcher] thread, with
     * the same guarantees as [execBatch].
     */
    internal suspend fun execTogether(vararg statements: Pair<String, Array<out Any?>>) {
        if (statements.isEmpty()) return
        withContext(dbDispatcher) {
            val previousAutoCommit = conn.autoCommit
            conn.autoCommit = false
            try {
                for ((sql, args) in statements) {
                    conn.prepareStatement(sql).use { ps ->
                        bind(ps, args)
                        ps.executeUpdate()
                    }
                }
                conn.commit()
            } catch (t: Throwable) {
                runCatching { conn.rollback() }
                throw t
            } finally {
                conn.autoCommit = previousAutoCommit
            }
        }
        invalidate()
    }

    internal suspend fun scalarLong(sql: String, vararg args: Any?): Long =
        queryOne(sql, *args) { it.getLong(1) } ?: 0L

    internal suspend fun scalarInt(sql: String, vararg args: Any?): Int =
        queryOne(sql, *args) { it.getInt(1) } ?: 0

    @OptIn(FlowPreview::class)
    internal fun <T> observe(block: suspend () -> T): Flow<T> =
        invalidation.debounce(250L).map { block() }.onStart { emit(block()) }

    private fun bind(ps: java.sql.PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> ps.setObject(i + 1, null)
                is Long -> ps.setLong(i + 1, a)
                is Int -> ps.setInt(i + 1, a)
                is Boolean -> ps.setInt(i + 1, if (a) 1 else 0)
                is String -> ps.setString(i + 1, a)
                else -> ps.setObject(i + 1, a)
            }
        }
    }

    internal fun raw(): Connection = conn
}

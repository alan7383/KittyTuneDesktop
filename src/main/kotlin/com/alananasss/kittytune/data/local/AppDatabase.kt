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

    private val invalidation = MutableStateFlow(0L)

    private fun invalidate() {
        invalidation.value = invalidation.value + 1
    }

    val downloadDao: DownloadDao by lazy { DownloadDao(this) }
    val recognitionHistoryDao: RecognitionHistoryDao by lazy { RecognitionHistoryDao(this) }
    val albumCacheDao: AlbumCacheDao by lazy { AlbumCacheDao(this) }
    val folderDao: FolderDao by lazy { FolderDao(this) }

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
            """CREATE TABLE IF NOT EXISTS library_folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, parentFolderId INTEGER,
                isPinned INTEGER NOT NULL DEFAULT 0, createdAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS track_trim (
                trackId INTEGER PRIMARY KEY NOT NULL, mode TEXT NOT NULL, segments TEXT NOT NULL,
                updatedAt INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS library_item_meta (
                itemKey TEXT PRIMARY KEY NOT NULL, folderId INTEGER, isPinned INTEGER NOT NULL DEFAULT 0,
                addedAt INTEGER NOT NULL)""",
        )
        conn.createStatement().use { st ->
            ddl.forEach { st.execute(it) }
            try {
                st.execute("ALTER TABLE downloaded_playlists ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")
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
        withContext(Dispatchers.IO) {
            conn.prepareStatement(sql).use { ps ->
                bind(ps, args)
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(mapper(rs)) }
                }
            }
        }

    internal suspend fun <T> queryOne(sql: String, vararg args: Any?, mapper: (ResultSet) -> T): T? =
        query(sql, *args, mapper = mapper).firstOrNull()

    internal suspend fun exec(sql: String, vararg args: Any?) = withContext(Dispatchers.IO) {
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
     * The transaction is on the one shared connection, so a write issued from elsewhere during these few
     * milliseconds joins it and would be rolled back with it. That is worth stating rather than hiding, and
     * it is an acceptable trade here: the only way this rolls back is a failing disk, and the alternative —
     * a commit per row — is the cost this exists to remove.
     *
     * @return how many rows the statement actually changed. With `INSERT OR IGNORE` a duplicate reports
     *   zero, so this is the count of genuinely new rows and no separate counting query is needed.
     */
    internal suspend fun execBatch(sql: String, rows: List<Array<out Any?>>): Int {
        if (rows.isEmpty()) return 0
        val changed = withContext(Dispatchers.IO) {
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

    internal suspend fun execSilent(sql: String, vararg args: Any?) = withContext(Dispatchers.IO) {
        conn.prepareStatement(sql).use { ps ->
            bind(ps, args)
            ps.executeUpdate()
        }
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

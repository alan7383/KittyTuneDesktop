package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.core.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Desktop port of the Room AppDatabase — plain SQLite over JDBC (org.xerial:sqlite-jdbc).
 *
 * Same DB name ("soundtune_db"), same 7-table schema (version 14), same DAO query
 * strings. `Flow`-returning DAO methods re-query on an invalidation signal so Compose
 * collectors update, mirroring Room's observable queries.
 */
object AppDatabase {

    private val dbFile = File(AppDirs.dataDir, "soundtune_db.sqlite")
    private lateinit var conn: Connection

    // Invalidation tick per table group — bumped on writes so Flow queries re-run.
    private val invalidation = MutableStateFlow(0L)

    private fun invalidate() {
        invalidation.value = invalidation.value + 1
    }

    val downloadDao: DownloadDao by lazy { DownloadDao(this) }
    val recognitionHistoryDao: RecognitionHistoryDao by lazy { RecognitionHistoryDao(this) }
    val albumCacheDao: AlbumCacheDao by lazy { AlbumCacheDao(this) }

    fun init() {
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        conn.autoCommit = true
        conn.createStatement().use { st ->
            st.execute("PRAGMA journal_mode=WAL")
            st.execute("PRAGMA foreign_keys=ON")
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
                localCoverPath TEXT, permalinkUrl TEXT, isAlbum INTEGER NOT NULL DEFAULT 0, addedAt INTEGER NOT NULL)""",
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
                timestamp INTEGER NOT NULL)""",
            """CREATE TABLE IF NOT EXISTS track_album_cache (
                trackId INTEGER PRIMARY KEY NOT NULL, albumPlaylistId INTEGER, albumTitle TEXT,
                resolvedAt INTEGER NOT NULL)""",
        )
        conn.createStatement().use { st -> ddl.forEach { st.execute(it) } }
    }

    // --- low-level helpers (used by the DAO classes) --------------------------------------

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

    /** Like [exec] but without bumping the invalidation tick — for high-volume cache writes
     *  (e.g. track_album_cache) that must not re-run every observable query in the app. */
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

    /** Wrap an observable query as a Flow that re-emits on any DB write. */
    internal fun <T> observe(block: suspend () -> T): Flow<T> =
        invalidation.map { block() }.onStart { emit(block()) }

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

    // exposed for BackupManager batch operations
    internal fun raw(): Connection = conn
}

package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.HistoryItem
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.local.LocalArtist
import com.alananasss.kittytune.data.local.LocalPlaylist
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlaylistTrackCrossRef
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Desktop port of the Android BackupManager.
 * Same Gson FullBackupData format & the same restorePrefs coercion rules, so a backup
 * exported from Android imports on desktop and vice-versa. SAF Uri -> java.io.File;
 * SharedPreferences dumps -> the Prefs / NamedPrefs snapshots.
 */
data class FullBackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val localTracks: List<LocalTrack>,
    val localPlaylists: List<LocalPlaylist>,
    val playlistRefs: List<PlaylistTrackCrossRef>,
    val savedArtists: List<LocalArtist>,
    val history: List<HistoryItem>,
    val likedTracks: List<Track>,
    val achievements: Map<String, Any?>? = emptyMap(),
    val playerPrefs: Map<String, Any?>,
    val listeningStats: List<ListeningStatsEvent>? = emptyList()
)

object BackupManager {
    private val gson = Gson()
    private val dao get() = AppDatabase.downloadDao

    suspend fun createBackup(file: File) {
        withContext(Dispatchers.IO) {
            val tracks = dao.getAllTracks().first()
            val playlists = dao.getAllPlaylists().first()
            // Single query for all refs — the previous N+1+M pattern called getTracksForPlaylistSync
            // per playlist (N queries) then getRef per track per playlist (M queries), so a library
            // with 100 playlists of 50 tracks was 5,100 DB round-trips through the single dispatcher
            // (audit Phase 5 IO gain).
            val allRefs = dao.getAllRefs()

            val artists = dao.getAllSavedArtists().first()
            val history = dao.getHistory().first()
            val stats = dao.getEventsAfter(0L)
            val likes = LikeRepository.likedTracks.value
            val playerPrefsMap = Prefs.snapshot().toPlainMap()

            val backupData = FullBackupData(
                localTracks = tracks,
                localPlaylists = playlists,
                playlistRefs = allRefs,
                savedArtists = artists,
                history = history,
                likedTracks = likes,
                achievements = emptyMap(),
                playerPrefs = playerPrefsMap,
                listeningStats = stats
            )

            file.writeText(gson.toJson(backupData))
        }
    }

    suspend fun restoreBackup(file: File) {
        withContext(Dispatchers.IO) {
            val jsonString = file.takeIf { it.exists() }?.readText()
                ?: throw Exception(str("error_backup_read"))

            // Gson happily returns null for a "null" document and leaves missing fields null even
            // on non-null Kotlin properties — both then NPE'd at the first forEach with a stack
            // trace instead of the backup dialog's own message. A backup without a field is a
            // backup with nothing of that kind; a document that is not a backup at all is the
            // read error.
            val data: FullBackupData = runCatching { gson.fromJson(jsonString, FullBackupData::class.java) }.getOrNull()
                ?: throw Exception(str("error_backup_read"))

            // Batch inserts: each dao.insertX(list) call uses execBatch (one transaction per
            // table), replacing the previous per-item inserts that were each their own transaction
            // (N commits + N invalidations). execBatch is already atomic per-table; a crash between
            // tables is unlikely (they're fast) and the restore can be re-run (audit Phase 5 IO gain).
            dao.insertTracks(data.localTracks ?: emptyList())
            dao.insertPlaylists(data.localPlaylists ?: emptyList())
            dao.insertPlaylistTrackRefs(data.playlistRefs ?: emptyList())
            dao.insertArtists(data.savedArtists ?: emptyList())
            dao.insertHistoryList(data.history ?: emptyList())
            dao.insertStatsEvents((data.listeningStats ?: emptyList()).map { it.copy(id = 0) })

            LikeRepository.replaceAllLikes(data.likedTracks ?: emptyList())

            restorePlayerPrefs(data.playerPrefs ?: emptyMap())
        }
    }

    /** Coercion identical to the Android restorePrefs (Gson deserializes numbers as Double). */
    private fun coerce(key: String, value: Any?): JsonElement? = when (value) {
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is List<*> -> JsonPrimitive(value.mapNotNull { it?.toString() }.joinToString(31.toChar().toString()))
        is Number -> when {
            key.startsWith("time_") || key == "last_position" -> JsonPrimitive(value.toLong())
            key == "font_wdth" || key == "font_slnt" || key == "font_rond" || key == "font_grad" || key == "font_opsz" || key == "lyrics_font_size" -> JsonPrimitive(value.toFloat())
            // Longs preserved when they do not fit an Int: the flat toInt() truncated them —
            // cached_user_id (a snowflake-sized id) restored as a different user's id. Small
            // values stay Int so ordinary prefs round-trip byte-identically.
            else -> {
                val asLong = value.toLong()
                if (asLong.toInt().toLong() == asLong) JsonPrimitive(asLong.toInt()) else JsonPrimitive(asLong)
            }
        }
        else -> null
    }

    private fun restorePlayerPrefs(map: Map<String, Any?>) {
        val entries = map.mapNotNull { (k, v) -> coerce(k, v)?.let { k to it } }.toMap()
        Prefs.restore(entries)
    }

    private fun Map<String, JsonElement>.toPlainMap(): Map<String, Any?> = mapValues { (_, v) ->
        val p = v as? JsonPrimitive ?: return@mapValues v.toString()
        when {
            p.isString -> p.contentOrNull
            p.booleanOrNull != null -> p.booleanOrNull
            p.longOrNull != null -> p.longOrNull
            p.doubleOrNull != null -> p.doubleOrNull
            else -> p.contentOrNull
        }
    }

    fun getBackupFileName(): String {
        val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return "KittyTune_Backup_$date.backup"
    }
}

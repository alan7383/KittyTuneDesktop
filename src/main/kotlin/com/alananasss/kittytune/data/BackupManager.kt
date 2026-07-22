package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.NamedPrefs
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
    val achievements: Map<String, Any?>,
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
            val allRefs = mutableListOf<PlaylistTrackCrossRef>()
            playlists.forEach { playlist ->
                val tracksInPlaylist = dao.getTracksForPlaylistSync(playlist.id)
                tracksInPlaylist.forEach { track ->
                    val ref = dao.getRef(playlist.id, track.id)
                    if (ref != null) allRefs.add(ref)
                }
            }

            val artists = dao.getAllSavedArtists().first()
            val history = dao.getHistory().first()
            val stats = dao.getEventsAfter(0L)
            val likes = LikeRepository.likedTracks.value
            val achievementPrefs = NamedPrefs("achievements_prefs").all().toPlainMap()
            val playerPrefsMap = Prefs.snapshot().toPlainMap()

            val backupData = FullBackupData(
                localTracks = tracks,
                localPlaylists = playlists,
                playlistRefs = allRefs,
                savedArtists = artists,
                history = history,
                likedTracks = likes,
                achievements = achievementPrefs,
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

            val data: FullBackupData = gson.fromJson(jsonString, FullBackupData::class.java)

            data.localTracks.forEach { dao.insertTrack(it) }
            data.localPlaylists.forEach { dao.insertPlaylist(it) }
            data.playlistRefs.forEach { dao.insertPlaylistTrackRef(it) }
            data.savedArtists.forEach { dao.insertArtist(it) }
            data.history.forEach { dao.insertHistory(it) }
            data.listeningStats?.forEach { dao.insertStatsEvent(it.copy(id = 0)) }

            LikeRepository.replaceAllLikes(data.likedTracks)

            restoreAchievementPrefs(data.achievements)
            restorePlayerPrefs(data.playerPrefs)

            AchievementManager.init()
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
            else -> JsonPrimitive(value.toInt())
        }
        else -> null
    }

    private fun restorePlayerPrefs(map: Map<String, Any?>) {
        val entries = map.mapNotNull { (k, v) -> coerce(k, v)?.let { k to it } }.toMap()
        Prefs.restore(entries)
    }

    private fun restoreAchievementPrefs(map: Map<String, Any?>) {
        val prefs = NamedPrefs("achievements_prefs")
        val entries = map.mapNotNull { (k, v) -> coerce(k, v)?.let { k to it } }.toMap()
        prefs.restore(entries)
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
        return "SoundTune_Backup_$date.backup"
    }
}

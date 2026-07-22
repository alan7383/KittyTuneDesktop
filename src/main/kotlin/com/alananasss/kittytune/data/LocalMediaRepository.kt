package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileOutputStream
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Desktop port of the Android LocalMediaRepository.
 * SAF DocumentFile tree URIs -> real filesystem paths; MediaMetadataRetriever -> jaudiotagger.
 * Keeps the negative-id convention (id = -abs(path.hashCode())) so local tracks stay distinct.
 */
object LocalMediaRepository {

    init {
        // jaudiotagger is very chatty; silence it.
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    private val AUDIO_EXTS = setOf(
        "mp3", "flac", "wav", "m4a", "aac", "ogg", "wma", "opus", "amr", "mp4",
    )

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress = _scanProgress.asStateFlow()

    private val prefs = PlayerPreferences()
    private val dao get() = AppDatabase.downloadDao

    suspend fun scanLocalMedia() {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = str("pref_local_scan_status_prep")

        withContext(Dispatchers.IO) {
            try {
                val uriSet = prefs.getLocalMediaUris()

                if (uriSet.isEmpty()) {
                    _scanProgress.value = str("pref_local_scan_status_no_folder")
                    _isScanning.value = false
                    return@withContext
                }

                val foundFiles = mutableListOf<File>()
                _scanProgress.value = str("pref_local_scan_status_searching")

                uriSet.forEach { pathStr ->
                    try {
                        val rootDir = File(pathStr)
                        if (rootDir.exists() && rootDir.isDirectory) {
                            collectAudioFiles(rootDir, foundFiles)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (foundFiles.isEmpty()) {
                    _scanProgress.value = str("pref_local_scan_status_no_files")
                    _isScanning.value = false
                    return@withContext
                }

                _scanProgress.value = str("pref_local_scan_status_found", foundFiles.size)

                val tracksToInsert = mutableListOf<LocalTrack>()
                var processed = 0

                foundFiles.forEach { file ->
                    processed++
                    if (processed % 10 == 0) {
                        _scanProgress.value = str("pref_local_scan_status_analyzing", processed, foundFiles.size)
                    }
                    try {
                        processFile(file)?.let { tracksToInsert.add(it) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _scanProgress.value = str("pref_local_scan_status_saving")
                tracksToInsert.forEach { dao.insertTrack(it) }

                _scanProgress.value = str("pref_local_scan_status_done")
            } catch (e: Exception) {
                e.printStackTrace()
                _scanProgress.value = str("pref_local_scan_status_error", e.message ?: "")
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun collectAudioFiles(dir: File, list: MutableList<File>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectAudioFiles(file, list)
            } else {
                val ext = file.extension.lowercase()
                if (ext in AUDIO_EXTS) list.add(file)
            }
        }
    }

    private fun processFile(file: File): LocalTrack? {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension.takeIf { it.isNotBlank() }
                ?: str("untitled_track")

            val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
                ?: str("unknown_artist")

            val duration = (header?.trackLength ?: 0) * 1000L // seconds -> ms

            var artworkPath = ""
            val artwork = tag?.firstArtwork
            if (artwork != null) {
                val bytes = artwork.binaryData
                if (bytes != null && bytes.isNotEmpty()) {
                    val fileName = "local_art_${file.absolutePath.hashCode()}.jpg"
                    val artFile = File(AppDirs.imageCacheDir, fileName)
                    if (!artFile.exists()) {
                        FileOutputStream(artFile).use { it.write(bytes) }
                    }
                    artworkPath = artFile.absolutePath
                }
            }

            var id = file.absolutePath.hashCode().toLong()
            if (id > 0) id *= -1
            if (id == 0L) id = -1L

            LocalTrack(
                id = id,
                title = title,
                artist = artist,
                artworkUrl = artworkPath,
                duration = duration,
                localAudioPath = file.absolutePath,
                localArtworkPath = artworkPath,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isLocalTrack(id: Long): Boolean = id < 0 && id > -9000000000000000000
}

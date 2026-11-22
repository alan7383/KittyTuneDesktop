package com.alananasss.kittytune.data

import com.alananasss.kittytune.audio.releaseUnlockedQuietly
import com.alananasss.kittytune.audio.startUnlocked
import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
        if (!_isScanning.compareAndSet(false, true)) return

        try {
            _scanProgress.value = str("pref_local_scan_status_prep")
            withContext(Dispatchers.IO) {
                val uriSet = prefs.getLocalMediaUris()

                if (uriSet.isEmpty()) {
                    _scanProgress.value = str("pref_local_scan_status_no_folder")
                    return@withContext
                }

                val foundFiles = linkedMapOf<String, File>()
                val visitedFolders = hashSetOf<String>()
                _scanProgress.value = str("pref_local_scan_status_searching")

                uriSet.forEach { pathStr ->
                    try {
                        val rootDir = File(pathStr)
                        if (rootDir.exists() && rootDir.isDirectory) {
                            collectAudioFiles(rootDir, foundFiles, visitedFolders)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (foundFiles.isEmpty()) {
                    _scanProgress.value = str("pref_local_scan_status_no_files")
                    return@withContext
                }

                _scanProgress.value = str("pref_local_scan_status_found", foundFiles.size)

                val tracksToInsert = mutableListOf<LocalTrack>()
                val stored = dao.getAllStoredTracksList()
                val existingTracks = stored.associateBy { it.id }
                val downloads = stored.filterNot { it.isUserLocalMedia }.mapNotNullTo(hashSetOf()) {
                    canonicalMediaPath(it.localAudioPath)
                }
                val localByFile = stored.filter { it.isUserLocalMedia }.sortedByDescending { it.id }
                    .mapNotNull { track -> canonicalMediaPath(track.localAudioPath)?.let { it to track } }.toMap()
                val usedIds = existingTracks.keys.toMutableSet()
                val index = LocalMediaScanIndex(File(AppDirs.cacheDir, "local-scan-index.bin"))
                var processed = 0

                foundFiles.forEach { (key, file) ->
                    if (key in downloads) return@forEach
                    currentCoroutineContext().ensureActive()
                    processed++
                    if (processed % 10 == 0) {
                        _scanProgress.value = str("pref_local_scan_status_analyzing", processed, foundFiles.size)
                    }
                    try {
                        val existing = localByFile[key]
                        val indexedFile = existing?.let { File(it.localAudioPath) }?.takeIf { it.isFile } ?: file
                        index.scan(indexedFile, existingTracks) { target ->
                            val track = processFile(target) ?: return@scan null
                            if (existing != null) {
                                track.copy(id = existing.id, downloadedAt = existing.downloadedAt)
                            } else {
                                var id = track.id
                                while (!usedIds.add(id)) id--
                                track.copy(id = id)
                            }
                        }?.let { tracksToInsert.add(it) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _scanProgress.value = str("pref_local_scan_status_saving")
                // One batched transaction instead of one commit per file. A scan that found
                // 500 files previously issued 500 separate INSERTs — 500 transactions, 500
                // fsyncs, 500 invalidations waking every observer of the tracks table (issue #5-DB).
                dao.insertTracks(tracksToInsert)
                try { index.save() } catch (_: Exception) { /* A missing index just makes the next scan read tags again. */ }

                _scanProgress.value = str("pref_local_scan_status_done")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            _scanProgress.value = str("pref_local_scan_status_error", e.message ?: "")
        } finally {
            _isScanning.value = false
        }
    }

    private fun collectAudioFiles(dir: File, files: MutableMap<String, File>, visited: MutableSet<String>) {
        val path = canonicalMediaPath(dir.path) ?: return
        if (!visited.add(path)) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectAudioFiles(file, files, visited)
            } else if (file.extension.lowercase() in AUDIO_EXTS) {
                canonicalMediaPath(file.path)?.let { files.putIfAbsent(it, file) }
            }
        }
    }

    private fun processFile(file: File): LocalTrack? = readTagged(file) ?: probeWithFfmpeg(file)

    /**
     * Files jaudiotagger cannot parse (Opus, raw ADTS AAC, AMR, a mislabelled extension...) still play:
     * the engine decodes them with FFmpeg. Read what FFmpeg sees instead of dropping them from the library.
     */
    private fun probeWithFfmpeg(file: File): LocalTrack? = runCatching {
        val grabber = org.bytedeco.javacv.FFmpegFrameGrabber(file)
        try {
            grabber.startUnlocked()
            if (grabber.audioChannels <= 0) return null
            val meta = grabber.metadata.orEmpty().mapKeys { it.key.lowercase() }
            LocalTrack(
                id = localMediaTrackId(file.absolutePath),
                title = meta["title"]?.takeIf { it.isNotBlank() }
                    ?: file.nameWithoutExtension.takeIf { it.isNotBlank() }
                    ?: str("untitled_track"),
                artist = (meta["artist"] ?: meta["album_artist"])?.takeIf { it.isNotBlank() } ?: str("unknown_artist"),
                artworkUrl = "",
                duration = (grabber.lengthInTime / 1000L).coerceAtLeast(0L),
                localAudioPath = file.absolutePath,
                source = "local",
                localArtworkPath = "",
            )
        } finally {
            grabber.releaseUnlockedQuietly()
        }
    }.getOrNull()

    private fun readTagged(file: File): LocalTrack? {
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

            LocalTrack(
                id = localMediaTrackId(file.absolutePath),
                title = title,
                artist = artist,
                artworkUrl = artworkPath,
                duration = duration,
                localAudioPath = file.absolutePath,
                source = "local",
                localArtworkPath = artworkPath,
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isLocalTrack(id: Long): Boolean = id < 0 && id > -9000000000000000000

    fun isSupportedAudioFile(file: File): Boolean = file.isFile && file.extension.lowercase() in AUDIO_EXTS

    /**
     * Adds one file to the local library and returns it ready to play — what "Open with KittyTune"
     * does with a file handed over by the OS. Null when it is not an audio file the app can read.
     */
    suspend fun importFile(file: File): Track? = withContext(Dispatchers.IO) {
        if (!isSupportedAudioFile(file)) return@withContext null
        val local = processFile(file) ?: return@withContext null
        dao.insertTrack(local)
        local.toPlayableTrack()
    }

    private fun LocalTrack.toPlayableTrack() = Track(
        id = id,
        title = title,
        artworkUrl = localArtworkPath.ifEmpty { artworkUrl },
        durationMs = duration,
        user = User(0, artist, null),
        description = str("description_local_file", localAudioPath),
        source = "local",
    )
}

internal fun localMediaTrackId(path: String): Long =
    -kotlin.math.abs(path.hashCode().toLong()).coerceAtLeast(1L)

/** Legacy desktop scans used a negative 32-bit path hash and omitted the source. */
internal val LocalTrack.isUserLocalMedia: Boolean
    get() = source == "local" || (source == null && id in Int.MIN_VALUE.toLong()..-1L && localAudioPath.isNotBlank())

/** The source a Track rebuilt from this row must carry; Track's own default would make it "soundcloud". */
internal val LocalTrack.playbackSource: String
    get() = source?.takeIf { it.isNotBlank() } ?: if (isUserLocalMedia) "local" else "soundcloud"

internal fun canonicalMediaPath(path: String): String? =
    if (path.isBlank()) null else runCatching { File(path).canonicalPath }.getOrNull()

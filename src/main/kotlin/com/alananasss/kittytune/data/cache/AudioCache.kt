package com.alananasss.kittytune.data.cache

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Tracks that were actually listened to, kept on disk so the next play needs no network.
 *
 * A track is cached after it has been heard for real (it counted as a play), one at a time in the background,
 * never while it is being streamed for the first time. The cache is bounded by [maxBytes]: when it grows past
 * that, the tracks played longest ago go first. Downloads are separate and never touched.
 */
object AudioCache {
    private const val KEY_ENABLED = "audio_cache_enabled"
    private const val KEY_MAX_MB = "audio_cache_max_mb"
    const val DEFAULT_MAX_MB = 1024

    val dir: File get() = File(AppDirs.audioCacheDir, "tracks").apply { mkdirs() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Bounded and dropping: a burst of skips should not queue up hours of downloading. */
    private val queue = Channel<Track>(capacity = 8, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            for (track in queue) runCatching { store(track) }.onFailure { Logger.w("AudioCache", "Could not cache ${track.id}: ${it.message}") }
        }
    }

    var isEnabled: Boolean
        get() = Prefs.getBoolean(KEY_ENABLED, true)
        set(value) = Prefs.putBoolean(KEY_ENABLED, value)

    var maxMegabytes: Int
        get() = Prefs.getInt(KEY_MAX_MB, DEFAULT_MAX_MB)
        set(value) {
            Prefs.putInt(KEY_MAX_MB, value)
            scope.launch { trim() }
        }

    private val maxBytes: Long get() = maxMegabytes.toLong() * 1024 * 1024

    /** The cached file for [trackId], marked as just used; null when it is not cached or caching is off. */
    fun lookup(trackId: Long): File? {
        if (!isEnabled) return null
        val file = dir.listFiles { f -> f.nameWithoutExtension == trackId.toString() }?.firstOrNull() ?: return null
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    /** Queues [track] for caching; a no-op when it is cached, downloaded, or caching is off. */
    fun offer(track: Track) {
        if (!isEnabled || track.id <= 0L) return
        queue.trySend(track)
    }

    fun sizeBytes(): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private suspend fun store(track: Track) {
        if (lookup(track.id) != null) return
        if (DownloadManager.getLocalTrack(track.id)?.localAudioPath?.let { File(it).exists() } == true) return
        if (!DownloadManager.hasEnoughFreeStorage(512L * 1024 * 1024)) return

        val stream = StreamResolver.resolveStreamWithDrm(track, forDownload = true) ?: return
        // Encrypted streams cannot be kept; a local path is already on disk.
        if (stream.isDrmProtected || !stream.url.startsWith("http")) return

        val isHls = stream.url.contains(".m3u8") || stream.url.contains("hls")
        val ext = if (isHls || stream.url.contains("googlevideo.com")) "m4a" else "mp3"
        val partial = File(dir, "${track.id}.part")
        try {
            if (isHls) {
                DownloadManager.remuxHls(stream.url, partial) {}
            } else {
                FileOutputStream(partial).use { out -> DownloadManager.downloadFileToStream(stream.url, out) {} }
            }
            if (partial.length() > 0 && partial.renameTo(File(dir, "${track.id}.$ext"))) trim()
        } finally {
            partial.delete()
        }
    }

    /** Drops the least recently played tracks until the cache fits in [maxBytes]. */
    private fun trim() {
        val files = dir.listFiles { f -> f.isFile && f.extension != "part" }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }
}

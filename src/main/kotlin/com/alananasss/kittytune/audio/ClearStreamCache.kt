package com.alananasss.kittytune.audio

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.AtomicFiles
import com.alananasss.kittytune.data.drm.SoundCloudDrm
import com.alananasss.kittytune.utils.Logger
import java.io.File
import java.security.MessageDigest

/**
 * A clear copy of a protected stream, for the features that read audio with FFmpeg.
 *
 * Playback, the equalizer and the effects never need this: they read the stream through
 * [HlsStreamAdapter], which decrypts fragment by fragment. But three features open the URL *themselves*
 * — the karaoke/vocal split, the AI detector, the beat analyser — and FFmpeg cannot decrypt CENC. Given
 * a `ctr-encrypted-hls` URL they would read ciphertext and produce nonsense (or nothing).
 *
 * So they ask for a clear copy: it is written once through [DrmStreamDownloader] (the same path a
 * download takes, licence included), kept in the audio cache, and reused on the next ask. The file is
 * a normal MP4, so every analyser reads it as-is.
 */
object ClearStreamCache {

    private const val TAG = "ClearStreamCache"

    /** Below this a file is a failure, not a track. */
    private const val MIN_BYTES = 64L * 1024L

    /** The clear copy if one is already on disk, without touching the network. Never suspends. */
    fun cached(url: String): File? = fileFor(url).takeIf { it.isFile && it.length() > MIN_BYTES }

    /**
     * The clear copy of [url], written on first use.
     *
     * Returns null when the stream needs no CDM (the caller reads the URL directly), when no licence
     * token is known for it, or when the copy cannot be made — an analyser that cannot read the audio
     * is better off skipping than reading noise.
     */
    suspend fun ensure(url: String): File? {
        cached(url)?.let { return it }
        if (!url.contains(".m3u8")) return null
        val token = SoundCloudDrm.licenceFor(url) ?: return null
        if (!SoundCloudDrm.isAvailable) return null
        val target = fileFor(url)
        val partial = File(target.parentFile, "${target.name}.part")
        return try {
            DrmStreamDownloader.download(url, token, partial)
            AtomicFiles.replace(partial, target)
            Logger.d(TAG, "clear copy of ${url.takeLast(48)} is ${target.length()} B")
            target.takeIf { it.isFile && it.length() > MIN_BYTES }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "could not read a clear copy of ${url.takeLast(48)}: ${e.message}")
            null
        } finally {
            runCatching { partial.delete() }
        }
    }

    /** Keyed by URL: the same stream always lands on the same file, whatever track it belongs to. */
    private fun fileFor(url: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        val dir = AppDirs.audioCacheDir.apply { mkdirs() }
        return File(dir, "clear_$hash.m4a")
    }
}

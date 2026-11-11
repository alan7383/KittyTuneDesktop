package com.alananasss.kittytune.audio

import com.alananasss.kittytune.data.drm.SoundCloudDrm
import com.alananasss.kittytune.utils.Logger
import java.io.File

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

    private val files = ClearStreamFiles { com.alananasss.kittytune.data.cache.AudioCache.dir }

    /** The clear copy if one is already on disk, without touching the network. Never suspends. */
    fun cached(url: String): File? = files.cached(url)

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
        return try {
            files.ensure(url) { partial -> DrmStreamDownloader.download(url, token, partial) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "could not read a clear copy of ${url.takeLast(48)}: ${e.message}")
            null
        }
    }
}

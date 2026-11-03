package com.alananasss.kittytune.audio

import com.alananasss.kittytune.data.drm.SoundCloudDrm
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Saves a Widevine-protected SoundCloud stream to disk as ordinary, clear audio.
 *
 * The result is not an encrypted download: every fragment is decrypted through the CDM while it is
 * written, and the encryption boxes are stripped, so the file plays later on any player — including a
 * machine with no CDM at all. That is also why the file is worth keeping: the license is asked for
 * once, at download time, instead of on every playback.
 *
 * The work happens on [SoundCloudDrm]'s download CDM instance, so starting a download never disturbs
 * the track that is currently playing.
 */
object DrmStreamDownloader {

    private const val TAG = "DrmStreamDownloader"

    /**
     * One download at a time.
     *
     * The bridge holds one session per CDM instance, and a second concurrent download with a different
     * key would reset it mid-stream. Downloads are serialised instead, which costs nothing a listener
     * can notice: they are background work.
     */
    private val lock = Mutex()

    private val headers = mapOf(
        "User-Agent" to com.alananasss.kittytune.utils.Config.USER_AGENT,
        "Origin" to "https://soundcloud.com",
        "Referer" to "https://soundcloud.com/",
    )

    /** True when this machine can turn a protected stream into a file. */
    val isAvailable: Boolean get() = SoundCloudDrm.isAvailable

    /**
     * Reads [playlistUrl], decrypting every fragment, and writes the clear stream to [destination].
     *
     * The output is a fragmented MP4 (`ftyp`/`moov` then one `moof`/`mdat` per fragment) for the
     * duration of the track: a normal MP4 that a demuxer reads as-is.
     *
     * @param licenseToken the `licenseAuthToken` resolved with [playlistUrl].
     * @param onProgress called with 0-100 as fragments are read.
     * @return [destination], once it holds the complete stream.
     * @throws IOException when the stream cannot be read or decrypted.
     */
    suspend fun download(
        playlistUrl: String,
        licenseToken: String,
        destination: File,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        require(licenseToken.isNotBlank()) { "a protected download needs a license token" }
        lock.withLock {
            val adapter = HlsStreamAdapter(playlistUrl, headers).apply {
                // The download's own CDM instance: it can never reset the one playback is using.
                sessionProvider = SoundCloudDrm::sessionForDownload
            }
            if (!adapter.isEncrypted) {
                throw IOException("the playlist of a protected track carried no encryption key")
            }
            adapter.licenseToken = licenseToken
            adapter.onFragmentProgress = { read, total ->
                if (total > 0) onProgress(((read * 100) / total).coerceIn(0, 99))
            }

            Logger.d(TAG, "downloading a protected stream (${adapter.totalDurationMs}ms)")
            var written = 0L
            try {
                FileOutputStream(destination).use { out ->
                    adapter.getInputStream(0L).use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            // A cancelled download must stop fetching fragments, not finish quietly.
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            written += read
                        }
                    }
                }
            } finally {
                // The instance survives for the next download; only the licensed session is dropped.
                SoundCloudDrm.releaseDownloadSession()
            }

            if (written == 0L) throw IOException("the protected stream produced no audio")
            Logger.d(TAG, "wrote $written bytes to ${destination.name}")
            onProgress(100)
            destination
        }
    }
}

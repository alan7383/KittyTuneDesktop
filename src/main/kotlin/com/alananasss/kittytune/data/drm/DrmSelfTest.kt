package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.utils.Config
import com.alananasss.kittytune.utils.Logger


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL

/**
 * The "does protected audio actually play" half of the CDM settings page.
 *
 * The rest of the page only proves that a library was found and loaded, which is not the same thing:
 * a CDM can load and still fail to answer a licence, or decrypt to noise when the playlist and this
 * build disagree. Calling the CDM's `GetCdmVersion` tests nothing. So this takes one real protected
 * SoundCloud track (`bladee1000/it-girl`, the one people notice is unplayable), asks SoundCloud for a
 * licence, decrypts its first fragment, and decodes that fragment with FFmpeg.
 *
 * Every step is reported as a line, because the point of the whole thing is a log someone can paste
 * into a bug report when it stops working.
 */
object DrmSelfTest {

    private const val TAG = "DrmSelfTest"
    private const val TRACK_PAGE = "https://soundcloud.com/bladee1000/it-girl"
    private const val FRAMES_TO_DECODE = 20

    /**
     * Runs the whole chain and reports what happened: resolve, licence, decrypt, decode.
     *
     * @param onLine called for each step, on a background thread, in order.
     * @return true when audio was actually decoded.
     */
    suspend fun run(
        onLine: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val report = { line: String ->
            Logger.d(TAG, line)
            onLine(line)
        }
        try {
            report("audio test: $TRACK_PAGE")
            val clientId = Config.CLIENT_ID

            val track = getJson(
                "https://api-v2.soundcloud.com/resolve?url=" +
                    "https%3A%2F%2Fsoundcloud.com%2Fbladee1000%2Fit-girl&client_id=$clientId"
            )
            val authorization = track.optString("track_authorization")
            val transcodings = track.optJSONObject("media")?.optJSONArray("transcodings")
                ?: return@withContext fail(report, "the track has no transcodings")

            var mediaUrl: String? = null
            var preset = ""
            for (i in 0 until transcodings.length()) {
                val t = transcodings.optJSONObject(i) ?: continue
                val format = t.optJSONObject("format") ?: continue
                if (format.optString("protocol") == "ctr-encrypted-hls") {
                    mediaUrl = t.optString("url")
                    preset = t.optString("preset")
                    break
                }
            }
            if (mediaUrl.isNullOrBlank()) {
                return@withContext fail(report, "the track offers no ctr-encrypted-hls stream")
            }
            report("  stream: ctr-encrypted-hls ($preset)")

            val stream = getJson("$mediaUrl?client_id=$clientId&track_authorization=$authorization")
            val licenseToken = stream.optString("licenseAuthToken")
            val playlistUrl = stream.optString("url")
            if (licenseToken.isBlank() || playlistUrl.isBlank()) {
                return@withContext fail(report, "no license token came back with the stream")
            }

            val playlist = getText(playlistUrl)
            val encryption = HlsEncryptionInfo.parse(playlist)
            if (encryption == null || !encryption.isWidevine) {
                return@withContext fail(report, "the playlist carries no Widevine key")
            }
            report("  key: Widevine, kid=${encryption.keyIdHex ?: "?"}")

            val initUrl = Regex("URI=\"([^\"]*init\\.mp4[^\"]*)\"").find(playlist)
                ?.groupValues?.get(1)
            val segmentUrl = playlist.lineSequence()
                .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (initUrl == null || segmentUrl == null) {
                return@withContext fail(report, "the playlist has no init segment or no fragment")
            }

            val init = getBytes(resolve(playlistUrl, initUrl))
            val segment = getBytes(resolve(playlistUrl, segmentUrl))
            report("  fetched: init ${init.size} B, fragment ${segment.size} B")

            val started = System.currentTimeMillis()
            // The download instance, not playback's: a self-test must not disturb what is playing.
            val session = SoundCloudDrm.sessionForDownload(encryption, licenseToken)
            report("  licence: granted in ${System.currentTimeMillis() - started} ms")

            try {
                val trackEncryption = CencDecrypter.parseInit(init)
                    ?: return@withContext fail(report, "the init segment carries no tenc box")
                val clearInit = SoundCloudDrm.clearInit(init)
                val clear = SoundCloudDrm.decryptSegment(segment, trackEncryption, session)
                report("  decrypted: ${segment.size} B -> ${clear.size} B")

                val file = File.createTempFile("kittytune-drm-selftest-", ".mp4")
                try {
                    file.writeBytes(clearInit + clear)
                    var frames = 0
                    var codec = "?"
                    var sampleRate = 0
                    var channels = 0
                    FFmpegFrameGrabber(file).use { grabber ->
                        grabber.start()
                        codec = grabber.audioCodecName ?: "?"
                        sampleRate = grabber.sampleRate
                        channels = grabber.audioChannels
                        while (frames < FRAMES_TO_DECODE) {
                            val frame = grabber.grabSamples() ?: break
                            if (frame.samples != null && frame.samples.isNotEmpty()) frames++
                        }
                        grabber.stop()
                    }
                    report("  decoded: $codec, $sampleRate Hz, $channels ch, $frames frames")
                    if (frames == 0) {
                        return@withContext fail(report, "the decrypted fragment decoded to no audio")
                    }
                } finally {
                    file.delete()
                }
            } finally {
                SoundCloudDrm.releaseDownloadSession()
            }
            report("  result: OK")
            true
        } catch (e: Throwable) {
            // Cancellation must not be swallowed: the page may be dismissed mid-test.
            if (e is kotlinx.coroutines.CancellationException) throw e
            fail(report, e.message ?: e::class.simpleName ?: "unknown error")
        }
    }

    private fun fail(report: (String) -> Unit, reason: String): Boolean {
        report("  result: FAILED ($reason)")
        return false
    }

    private fun resolve(base: String, relative: String): String =
        if (relative.startsWith("http")) relative else URL(URL(base), relative).toString()

    private fun getJson(url: String): JSONObject = JSONObject(getText(url))

    private fun getText(url: String): String = request(url).use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        response.body?.string() ?: throw IOException("empty body for $url")
    }

    private fun getBytes(url: String): ByteArray = request(url).use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
        response.body?.bytes() ?: throw IOException("empty body for $url")
    }

    private fun request(url: String) = com.alananasss.kittytune.data.network.ProxyManager
        .getOkHttpClient()
        .newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", Config.USER_AGENT)
                .header("Origin", "https://soundcloud.com")
                .header("Referer", "https://soundcloud.com/")
                .build()
        )
        .execute()
}

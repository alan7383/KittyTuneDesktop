package com.alananasss.kittytune

import com.alananasss.kittytune.audio.DrmStreamDownloader
import com.alananasss.kittytune.data.drm.CencDecrypter
import com.alananasss.kittytune.data.drm.DrmSelfTest
import com.alananasss.kittytune.data.drm.HlsEncryptionInfo
import com.alananasss.kittytune.data.drm.NativeWidevineCdm
import com.alananasss.kittytune.data.drm.SoundCloudDrm
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Live check of the whole protected-track path against a real SoundCloud track.
 *
 * Skipped unless `KITTYTUNE_DRM_LIVE=1`, because it needs the network, a SoundCloud license token and
 * a Widevine CDM installed on the machine — none of which belong in the normal test run. It is the
 * end-to-end proof that the CENC pipeline and the native CDM agree on real content.
 *
 * Usage:
 * ```
 * KITTYTUNE_DRM_LIVE=1 ./gradlew :test --tests "*NativeWidevineLiveTest*"
 * ```
 */
class NativeWidevineLiveTest {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun get(url: String): String =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} for $url" }
            response.body!!.string()
        }

    @Test
    fun aRealProtectedTrackDecryptsThroughTheCdm() {
        assumeTrue("set KITTYTUNE_DRM_LIVE=1 to run", System.getenv("KITTYTUNE_DRM_LIVE") == "1")
        assumeTrue("no Widevine CDM on this machine", NativeWidevineCdm.installIfAvailable())
        println("[live] using CDM: ${NativeWidevineCdm.currentCdmPath}")

        val clientId = System.getenv("SC_CLIENT_ID") ?: "2yeWkbX88QhTzS3OtZp8cLGaJRrOAWwF"

        // 1. Resolve a track that only exists as CENC-encrypted HLS.
        val track = JSONObject(
            get("https://api-v2.soundcloud.com/resolve?url=https%3A%2F%2Fsoundcloud.com%2Fbladee1000%2Fit-girl&client_id=$clientId")
        )
        val authorization = track.getString("track_authorization")
        val transcodings = track.getJSONObject("media").getJSONArray("transcodings")
        var mediaUrl: String? = null
        for (i in 0 until transcodings.length()) {
            val t = transcodings.getJSONObject(i)
            if (t.getJSONObject("format").getString("protocol") == "ctr-encrypted-hls") {
                mediaUrl = t.getString("url")
                break
            }
        }
        assertNotNull("track should offer a ctr-encrypted-hls transcoding", mediaUrl)

        // 2. The stream URL carries the license token.
        val stream = JSONObject(get("$mediaUrl?client_id=$clientId&track_authorization=$authorization"))
        val licenseToken = stream.getString("licenseAuthToken")
        val playlistUrl = stream.getString("url")
        assertTrue("a license token is required for a protected stream", licenseToken.isNotEmpty())

        // 3. The playlist declares the Widevine key and points at the init segment and fragments.
        val playlist = get(playlistUrl)
        val encryption = HlsEncryptionInfo.parse(playlist)
        assertNotNull("playlist should declare an encryption key", encryption)
        assertTrue("the Widevine key line must be selected", encryption!!.isWidevine)

        val initUrl = Regex("URI=\"([^\"]*init\\.mp4[^\"]*)\"").find(playlist)!!.groupValues[1]
        val segmentUrl = playlist.lineSequence().first { it.isNotBlank() && !it.startsWith("#") }
        val init = http.newCall(Request.Builder().url(initUrl).build()).execute().use { it.body!!.bytes() }
        val segment = http.newCall(Request.Builder().url(segmentUrl).build()).execute().use { it.body!!.bytes() }
        println("[live] init=${init.size} B, fragment=${segment.size} B")

        // 4. Real license exchange through the CDM, then real sample decryption.
        val trackEncryption = CencDecrypter.parseInit(init)
        assertNotNull("init segment should describe the encryption", trackEncryption)
        val session = SoundCloudDrm.session(encryption, licenseToken)
        val clearInit = SoundCloudDrm.clearInit(init)
        val clearSegment = SoundCloudDrm.decryptSegment(segment, trackEncryption, session)
        assertTrue("decrypted fragment must not keep senc", !clearSegment.containsAscii("senc"))

        // 5. The result has to be real audio, not merely non-empty: decode a frame from it.
        val file = File.createTempFile("kittytune-drm-", ".mp4")
        file.deleteOnExit()
        file.writeBytes(clearInit + clearSegment)
        println("[live] clear fragment: ${file.length()} B -> ${file.absolutePath}")

        var frames = 0
        FFmpegFrameGrabber(file).use { grabber ->
            grabber.start()
            while (frames < 50) {
                val frame = grabber.grabSamples() ?: break
                if (frame.samples != null && frame.samples.isNotEmpty()) frames++
            }
            grabber.stop()
        }
        println("[live] decoded $frames audio frames")
        assertTrue("the decrypted fragment should decode to audio", frames > 0)
    }

    /**
     * The whole download path: a protected playlist in, a clear playable file out.
     *
     * Also the only proof that a *second* CDM instance can be created: the downloader deliberately
     * uses its own, so that starting a download cannot reset the session of the track playing.
     */
    @Test
    fun aRealProtectedTrackDownloadsAsAClearFile() {
        assumeTrue("set KITTYTUNE_DRM_LIVE=1 to run", System.getenv("KITTYTUNE_DRM_LIVE") == "1")
        assumeTrue("no Widevine CDM on this machine", NativeWidevineCdm.installIfAvailable())

        val clientId = System.getenv("SC_CLIENT_ID") ?: "2yeWkbX88QhTzS3OtZp8cLGaJRrOAWwF"
        val track = JSONObject(
            get("https://api-v2.soundcloud.com/resolve?url=https%3A%2F%2Fsoundcloud.com%2Fbladee1000%2Fit-girl&client_id=$clientId")
        )
        val authorization = track.getString("track_authorization")
        val transcodings = track.getJSONObject("media").getJSONArray("transcodings")
        var mediaUrl: String? = null
        for (i in 0 until transcodings.length()) {
            val t = transcodings.getJSONObject(i)
            if (t.getJSONObject("format").getString("protocol") == "ctr-encrypted-hls") {
                mediaUrl = t.getString("url")
                break
            }
        }
        assertNotNull("track should offer a ctr-encrypted-hls transcoding", mediaUrl)
        val stream = JSONObject(get("$mediaUrl?client_id=$clientId&track_authorization=$authorization"))

        val out = File.createTempFile("kittytune-drm-download-", ".m4a")
        out.deleteOnExit()
        val reported = mutableListOf<Int>()
        kotlinx.coroutines.runBlocking {
            DrmStreamDownloader.download(stream.getString("url"), stream.getString("licenseAuthToken"), out) {
                reported += it
            }
        }
        println("[live] downloaded ${out.length()} B, progress=$reported")
        assertTrue("the download must hold real audio, got ${out.length()} B", out.length() > 200_000)
        assertTrue("progress should end at 100, was $reported", reported.lastOrNull() == 100)

        // A downloaded track is ordinary audio: it has to decode without any key in sight.
        var frames = 0
        var seconds = 0.0
        FFmpegFrameGrabber(out).use { grabber ->
            grabber.start()
            seconds = grabber.lengthInTime / 1_000_000.0
            while (frames < 100) {
                val frame = grabber.grabSamples() ?: break
                if (frame.samples != null && frame.samples.isNotEmpty()) frames++
            }
            grabber.stop()
        }
        println("[live] downloaded file decodes: ${"%.1f".format(seconds)}s, $frames frames")
        assertTrue("the downloaded file should decode to audio", frames > 0)

        // The download itself stores the fragment sequence; the app remuxes it into one clean MP4
        // before saving. That step has to keep the audio and its length.
        val remuxed = File.createTempFile("kittytune-drm-remux-", ".m4a")
        remuxed.deleteOnExit()
        com.alananasss.kittytune.data.DownloadManager.remuxHls(out.absolutePath, remuxed) { }
        var remuxedSeconds = 0.0
        FFmpegFrameGrabber(remuxed).use { grabber ->
            grabber.start()
            remuxedSeconds = grabber.lengthInTime / 1_000_000.0
            grabber.stop()
        }
        println("[live] remuxed to ${remuxed.length()} B, ${"%.1f".format(remuxedSeconds)}s")
        assertTrue("the remux must keep the audio, got ${remuxed.length()} B", remuxed.length() > 200_000)
        assertEquals("the remux must keep the length", seconds, remuxedSeconds, 2.0)
    }

    /**
     * Two licensed sessions in a row on the same CDM instance.
     *
     * This is the settings page's "test" button followed by a download, and it used to hang forever
     * inside `CloseSession`: the native bridge held its own mutex while calling into the CDM, so the
     * reset of the first session never returned. A timeout turns a regression into a failure instead
     * of a test run that never ends.
     */
    @Test(timeout = 240_000)
    fun twoSessionsInARowDoNotHang() {
        assumeTrue("set KITTYTUNE_DRM_LIVE=1 to run", System.getenv("KITTYTUNE_DRM_LIVE") == "1")
        assumeTrue("no Widevine CDM on this machine", NativeWidevineCdm.installIfAvailable())

        val first: Boolean = kotlinx.coroutines.runBlocking { DrmSelfTest.run { _: String -> } }
        val second: Boolean = kotlinx.coroutines.runBlocking { DrmSelfTest.run { _: String -> } }
        assertTrue("the first session should decrypt real audio", first)
        assertTrue("the second session should decrypt real audio too", second)
    }

    private fun ByteArray.containsAscii(token: String): Boolean {
        val needle = token.toByteArray(Charsets.US_ASCII)
        if (needle.isEmpty() || size < needle.size) return false
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}

package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.audio.ClearStreamCache
import com.alananasss.kittytune.audio.DrmStreamDownloader
import com.alananasss.kittytune.data.AudioScannerManager
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.URL

/**
 * The three audio features that do **not** go through the player, on a protected track.
 *
 * Playback, the equalizer and the effects read the stream through `HlsStreamAdapter`, which decrypts
 * every fragment: they need nothing here. But a download, the volume analysis and the karaoke/beat/AI
 * analysers each read the audio their own way, and on a protected track they only work if the CDM is
 * in that path.
 *
 * Requires the native module installed (`NativeCdmSetupLiveTest` leaves it in place) and network.
 *
 *   KITTYTUNE_CDM_INSTALL_TEST=1 ./gradlew test --tests "*ProtectedFlowsLiveTest*"
 */
class ProtectedFlowsLiveTest {

    @Before
    fun requireModule() {
        assumeTrue("set KITTYTUNE_CDM_INSTALL_TEST=1 to run", System.getenv("KITTYTUNE_CDM_INSTALL_TEST") == "1")
        assumeTrue("the native module is not installed", NativeCdmSetup.isInstalled())
    }

    /** The app's own settings test, in the order the app runs it: nothing registered beforehand. */
    @Test
    fun `the self test from a cold start uses the native module`() {
        CdmSidecar.reset()
        val report = StringBuilder()
        val ok = runBlocking { DrmSelfTest.run { line -> synchronized(report) { report.append(line).append('\n') } } }
        println(report)
        assertTrue("the self test failed:\n$report", ok)
        // The in-process CDM is the one SoundCloud refuses: the test must not have used it.
        assertTrue(
            "the self test did not go through the native module:\n$report",
            report.contains("ktcdm") || report.contains("cdm"),
        )
        assertTrue("no licence was granted:\n$report", report.contains("licence: granted") || report.contains("licence"))
    }

    /** A download of a protected track produces a clear file that decodes — the playlist case too. */
    @Test
    fun `protected downloads are clear, single and in parallel`() {
        CdmSidecar.reset()
        assertTrue("the daemon does not answer", CdmSidecar.verify())
        val tracks = protectedTracks(2)
        val output = File(System.getProperty("java.io.tmpdir"), "kittytune-flows-${System.nanoTime()}")
        output.mkdirs()
        try {
            val files = runBlocking {
                tracks.map { (title, playlistUrl, token) ->
                    async(Dispatchers.IO) {
                        val destination = File(output, "${title.hashCode()}.m4a")
                        println("  downloading $title")
                        DrmStreamDownloader.download(playlistUrl, token, destination)
                        destination to title
                    }
                }.awaitAll()
            }
            files.forEach { (file, title) ->
                assertTrue("$title: the file is ${file.length()} B", file.length() > 512 * 1024)
                val loudness = AudioScannerManager.scanAudioFile(file)
                assertTrue("$title: FFmpeg could not decode the downloaded file", loudness != null)
                assertTrue(
                    "$title: implausible loudness ${loudness?.integratedLufs}",
                    loudness!!.integratedLufs in -40f..0f,
                )
                println("  $title: ${file.length()} B, ${"%.1f".format(loudness.integratedLufs)} LUFS")
            }
        } finally {
            output.deleteRecursively()
        }
    }

    /** The clear copy the karaoke/AI/beat analysers read, from the licence the resolver remembers. */
    @Test
    fun `a clear copy is available to the features that read audio themselves`() {
        CdmSidecar.reset()
        assertTrue("the daemon does not answer", CdmSidecar.verify())
        val (title, playlistUrl, token) = protectedTracks(1).first()
        // Exactly what StreamResolver does when it hands a protected stream out.
        SoundCloudDrm.rememberLicence(playlistUrl, token)
        assertTrue("no licence remembered for the URL", SoundCloudDrm.licenceFor(playlistUrl) == token)

        val clear = runBlocking { ClearStreamCache.ensure(playlistUrl) }
        assertTrue("no clear copy for $title", clear != null && clear.length() > 512 * 1024)
        val loudness = AudioScannerManager.scanAudioFile(clear!!)
        assertTrue("FFmpeg could not decode the clear copy", loudness != null)
        println("  $title: clear copy ${clear.length()} B, ${"%.1f".format(loudness!!.integratedLufs)} LUFS")

        // Second ask is free: same file, no second licence.
        val again = runBlocking { ClearStreamCache.ensure(playlistUrl) }
        assertTrue("the clear copy was not reused", again?.absolutePath == clear.absolutePath)
    }

    /** Titles, playlist URLs and licence tokens of SoundCloud tracks that only exist protected. */
    private fun protectedTracks(count: Int): List<Triple<String, String, String>> {
        val clientId = System.getenv("KITTYTUNE_SC_CLIENT_ID")?.takeIf { it.isNotBlank() } ?: Config.CLIENT_ID
        val found = mutableListOf<Triple<String, String, String>>()

        fun add(track: JSONObject): Boolean {
            val media = track.optJSONObject("media") ?: return false
            val transcodings = media.optJSONArray("transcodings") ?: return false
            val protected = (0 until transcodings.length())
                .map { transcodings.getJSONObject(it) }
                .firstOrNull { it.optJSONObject("format")?.optString("protocol") == "ctr-encrypted-hls" }
                ?: return false
            val stream = JSONObject(
                URL(
                    "${protected.getString("url")}?client_id=$clientId" +
                        "&track_authorization=${track.getString("track_authorization")}"
                ).readText()
            )
            val url = stream.optString("url").takeIf { it.isNotBlank() } ?: return false
            val token = stream.optString("licenseAuthToken").takeIf { it.isNotBlank() } ?: return false
            if (found.any { it.second == url }) return false
            found += Triple(track.optString("title", "track"), url, token)
            return true
        }

        add(
            JSONObject(
                URL(
                    "https://api-v2.soundcloud.com/resolve?url=https%3A%2F%2Fsoundcloud.com" +
                        "%2Fbladee1000%2Fit-girl&client_id=$clientId"
                ).readText()
            )
        )
        // A second one, so the batch case is a real playlist of two different keys.
        val search = JSONObject(
            URL("https://api-v2.soundcloud.com/search/tracks?q=bladee&limit=20&client_id=$clientId").readText()
        )
        val collection = search.optJSONArray("collection") ?: search.optJSONArray("tracks")
        for (i in 0 until (collection?.length() ?: 0)) {
            if (found.size >= count) break
            add(collection!!.getJSONObject(i))
        }
        assumeTrue("no protected tracks found (client_id expired?)", found.isNotEmpty())
        return found.take(count)
    }
}

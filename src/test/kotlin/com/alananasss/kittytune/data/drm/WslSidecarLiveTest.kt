package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.audio.HlsStreamAdapter
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * The WSL sidecar against a real distribution and real protected tracks, and above all the proof
 * that WSL does not stay up once a track no longer needs it.
 *
 * Opt-in, because it installs into the distribution (`/opt/kittytune`), talks to SoundCloud and
 * starts and stops WSL. The shutdown part needs WSL idle when it starts (nothing running in it):
 *
 *     KITTYTUNE_WSL_LIVE=Ubuntu KITTYTUNE_CDM_SIDECAR="C:\Windows\System32\wsl.exe -d Ubuntu --exec /opt/kittytune/ktcdm /opt/kittytune/libwidevinecdm.so" \
 *       ./gradlew :test --tests "*WslSidecarLiveTest" --no-daemon
 *
 * Add KITTYTUNE_WSL_SKIP_INSTALL=1 when the daemon is already built. The environment variable points
 * the sidecar at it, so nothing is written on the Windows side.
 */
class WslSidecarLiveTest {

    @Test(timeout = 900_000)
    fun `a fully decrypted track lets WSL shut down, and the next one starts it again`() {
        com.alananasss.kittytune.utils.Logger.isDebug = true
        val distro = System.getenv("KITTYTUNE_WSL_LIVE")
        assumeTrue("set KITTYTUNE_WSL_LIVE=<distribution> to run", !distro.isNullOrBlank())
        assumeTrue("set KITTYTUNE_CDM_SIDECAR", !System.getenv("KITTYTUNE_CDM_SIDECAR").isNullOrBlank())

        if (System.getenv("KITTYTUNE_WSL_SKIP_INSTALL") != "1") {
            runBlocking {
                WslSidecarSetup.installInDistro(distro!!, onStep = { println("step: $it") }, onLine = { println("  $it") })
            }
            // Installing ran as root outside the lifecycle; let WSL settle as it would on its own.
            waitUntil(180_000) { runningDistros().isEmpty() }
        }
        assumeTrue("WSL must be idle to check that we shut it down", waitUntil(300_000) { runningDistros().isEmpty() && !vmRunning() })

        CdmSidecar.reset()
        assertTrue(CdmSidecar.installIfConfigured())
        assertTrue("registering must not start WSL", runningDistros().isEmpty())

        // The player's own path: read the whole protected stream, as the decoder (or the upfront
        // analysis) does. The last fragment releases the session.
        val (playlistUrl, token) = protectedStream()
        val adapter = HlsStreamAdapter(playlistUrl, emptyMap())
        adapter.licenseToken = token
        val bytes = adapter.getInputStream(0).use { it.readBytes() }
        println("  decrypted the whole track: ${bytes.size} B")
        assertTrue(bytes.size > 100_000)
        assertTrue("WSL should run while the track decrypts", runningDistros().contains(distro) || vmRunning())

        // A few seconds of grace for the next track, then the distribution and the VM go away.
        assertTrue(
            "WSL was still running after the track was fully decrypted: ${runningDistros()}, vm=${vmRunning()}",
            waitUntil(30_000) { runningDistros().isEmpty() && !vmRunning() },
        )

        // Seeking back after that is served from the decrypted fragments: WSL stays down.
        adapter.getInputStream(0).use { it.readNBytes(200_000) }
        assertFalse("a seek back started WSL again", runningDistros().isNotEmpty())

        // And the next protected track simply starts it again.
        assertTrue("track after shutdown did not decode", runBlocking { DrmSelfTest.run(widevine = true) { println("  $it") } })
        assertTrue(
            "WSL was still running after the test track",
            waitUntil(30_000) { runningDistros().isEmpty() && !vmRunning() },
        )
        CdmSidecar.reset()
    }

    private fun protectedStream(): Pair<String, String> {
        val clientId = Config.CLIENT_ID
        val track = JSONObject(
            URL(
                "https://api-v2.soundcloud.com/resolve?url=https%3A%2F%2Fsoundcloud.com%2Fbladee1000%2Fit-girl&client_id=$clientId"
            ).readText()
        )
        val transcodings = track.getJSONObject("media").getJSONArray("transcodings")
        val media = (0 until transcodings.length()).map { transcodings.getJSONObject(it) }
            .first { it.getJSONObject("format").getString("protocol") == "ctr-encrypted-hls" }
        val stream = JSONObject(
            URL("${media.getString("url")}?client_id=$clientId&track_authorization=${track.getString("track_authorization")}").readText()
        )
        return stream.getString("url") to stream.getString("licenseAuthToken")
    }

    private fun runningDistros(): List<String> {
        val builder = ProcessBuilder("wsl.exe", "--list", "--running", "--quiet").redirectErrorStream(true)
        builder.environment()["WSL_UTF8"] = "1"
        val process = builder.start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(20, TimeUnit.SECONDS)
        return WslSidecarSetup.parseDistros(output)
    }

    /** The WSL VM's own process, which holds the memory. */
    private fun vmRunning(): Boolean {
        // ProcessHandle cannot see the VM's protected process; tasklist can.
        val process = ProcessBuilder("tasklist", "/FI", "IMAGENAME eq vmmemWSL", "/NH").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(20, TimeUnit.SECONDS)
        return output.contains("vmmemWSL", ignoreCase = true)
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(1_000)
        }
        return condition()
    }
}

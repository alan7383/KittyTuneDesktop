package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.audio.HlsStreamAdapter
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URL

/**
 * The whole app path on Windows, end to end: [WslSidecarSetup.provision] downloads the published rootfs,
 * imports kittytune-cdm, fetches the CDM, probes and writes the sidecar config — then a real protected
 * track decrypts through *that config* (not an env override). Opt-in and heavy (downloads the rootfs and
 * Chrome's .deb), and it restores the user's backend preference afterwards.
 *
 * Then it uninstalls, and checks nothing is left: the distribution unregistered (WSL1's files or
 * WSL2's virtual disk), its folder, the config, and protected tracks back on PlayReady.
 * KITTYTUNE_PROVISION_VERSION picks WSL1 (default) or WSL2.
 *
 *   KITTYTUNE_RUN_PROVISION=1 KITTYTUNE_PROVISION_VERSION=2 KITTYTUNE_SC_CLIENT_ID=<fresh web id> \
 *     ./gradlew :test --tests "*WslProvisionTest" --no-daemon --no-configuration-cache --rerun-tasks
 */
class WslProvisionTest {

    @Test(timeout = 900_000)
    fun `provision from the release, then decrypt it-girl through the written config`() = runBlocking {
        com.alananasss.kittytune.utils.Logger.isDebug = true
        assumeTrue("set KITTYTUNE_RUN_PROVISION=1", System.getenv("KITTYTUNE_RUN_PROVISION") == "1")
        val clientId = System.getenv("KITTYTUNE_SC_CLIENT_ID")
        assumeTrue("set KITTYTUNE_SC_CLIENT_ID", !clientId.isNullOrBlank())

        val version = System.getenv("KITTYTUNE_PROVISION_VERSION")?.toIntOrNull() ?: 1
        val prefs = PlayerPreferences()
        val originalBackend = prefs.getProtectedBackend()
        try {
            Config.updateClientId(clientId!!)
            WslSidecarSetup.provision(
                version = version,
                onStep = { println("step: $it") },
                onLine = { println("  $it") },
            )
            // provision() selected WSL and wrote the config; decryption now goes through the real path.
            val (playlistUrl, token) = protectedStream(clientId)
            val adapter = HlsStreamAdapter(playlistUrl, emptyMap())
            adapter.licenseToken = token
            val bytes = adapter.getInputStream(0).use { it.readBytes() }
            println("  decrypted the whole track: ${bytes.size} B")
            assertTrue("too few bytes; decryption failed", bytes.size > 100_000)
            assertEquals(version, WslSidecarSetup.detect().installedVersion)

            // And the uninstall leaves nothing behind.
            CdmSidecar.reset()
            WslSidecarSetup.uninstall(featureVersion = null, onStep = { println("uninstall: $it") }, onLine = { println("  $it") })
            val after = WslSidecarSetup.detect()
            println("  after uninstall: ${after.state}")
            assertTrue("the distribution is still registered", after.state != WslSidecarSetup.State.SET_UP && after.installedVersion == null)
            val local = java.io.File(System.getenv("LOCALAPPDATA"), "KittyTune")
            assertFalse("the distribution folder is left", java.io.File(local, "wsl/${WslSidecarSetup.DISTRO}").exists())
            assertFalse("the sidecar config is left", java.io.File(local, "cdm-sidecar.json").exists())
            assertEquals(ProtectedBackend.PLAYREADY, ProtectedBackend.current)
            assertFalse("still connected after uninstall", CdmSidecar.verify())
        } finally {
            // Leave the user's app as it was (the distro stays imported — a harmless head start).
            prefs.setProtectedBackend(originalBackend)
            CdmSidecar.reset()
        }
    }

    private fun protectedStream(clientId: String): Pair<String, String> {
        val track = JSONObject(
            URL("https://api-v2.soundcloud.com/resolve?url=https%3A%2F%2Fsoundcloud.com%2Fbladee1000%2Fit-girl&client_id=$clientId").readText()
        )
        val transcodings = track.getJSONObject("media").getJSONArray("transcodings")
        val media = (0 until transcodings.length()).map { transcodings.getJSONObject(it) }
            .first { it.getJSONObject("format").getString("protocol") == "ctr-encrypted-hls" }
        val stream = JSONObject(
            URL("${media.getString("url")}?client_id=$clientId&track_authorization=${track.getString("track_authorization")}").readText()
        )
        return stream.getString("url") to stream.getString("licenseAuthToken")
    }
}

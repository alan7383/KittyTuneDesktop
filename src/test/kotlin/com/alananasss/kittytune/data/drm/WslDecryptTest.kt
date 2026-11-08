package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.audio.HlsStreamAdapter
import com.alananasss.kittytune.utils.Config
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URL

/**
 * Proves a real protected SoundCloud track (`bladee1000/it-girl`) decrypts end-to-end through a CDM
 * sidecar — used to validate the lightweight WSL1 backend. Unlike WslSidecarLiveTest it makes no
 * WSL2 lifecycle assumptions (WSL1 has no VM), it only checks that bytes come out clear.
 *
 *   KITTYTUNE_SC_CLIENT_ID=<fresh web client_id> \
 *   KITTYTUNE_CDM_SIDECAR="C:\Windows\System32\wsl.exe -d ktw1 -u root --exec /opt/kittytune/ktcdm /opt/kittytune/libwidevinecdm.so" \
 *     ./gradlew :test --tests "*WslDecryptTest" --no-daemon
 */
class WslDecryptTest {

    @Test(timeout = 300_000)
    fun `it-girl decrypts through the sidecar`() {
        com.alananasss.kittytune.utils.Logger.isDebug = true
        val sidecar = System.getenv("KITTYTUNE_CDM_SIDECAR")
        val clientId = System.getenv("KITTYTUNE_SC_CLIENT_ID")
        assumeTrue("set KITTYTUNE_CDM_SIDECAR", !sidecar.isNullOrBlank())
        assumeTrue("set KITTYTUNE_SC_CLIENT_ID to a fresh web client_id", !clientId.isNullOrBlank())
        Config.updateClientId(clientId!!)

        CdmSidecar.reset()
        assertTrue("sidecar did not register (daemon not answering)", CdmSidecar.installIfConfigured())

        val (playlistUrl, token) = protectedStream(clientId)
        val adapter = HlsStreamAdapter(playlistUrl, emptyMap())
        adapter.licenseToken = token
        val bytes = adapter.getInputStream(0).use { it.readBytes() }
        println("  decrypted the whole track: ${bytes.size} B")
        assertTrue("too few bytes came out; decryption likely failed", bytes.size > 100_000)
        CdmSidecar.reset()
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

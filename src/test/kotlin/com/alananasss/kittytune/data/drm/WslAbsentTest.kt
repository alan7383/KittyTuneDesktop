package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * On a Windows without WSL, `System32\wsl.exe` is only an installer stub: running it, for anything, opens a
 * terminal offering to install WSL (one key press reinstalls it). After a full removal from the app, that is
 * exactly what users saw. This drives everything of ours that ever talked to WSL and checks that not a single
 * wsl.exe was started — the stub would still be there, waiting 60 s for a key.
 *
 * Runs only where WSL is absent (a PC that never had it, or after "remove WSL").
 */
class WslAbsentTest {

    private val config = java.io.File(System.getenv("LOCALAPPDATA") ?: ".", "KittyTune/cdm-sidecar.json")

    @Test(timeout = 300_000)
    fun `nothing starts wsl exe when WSL is not installed`() = runBlocking {
        assumeTrue("Windows only", System.getProperty("os.name").startsWith("Windows", true))
        assumeTrue("WSL is installed here", !WslSidecarSetup.wslInstalled)
        assertEquals("a wsl.exe is already running", 0, wslProcesses())
        val prefs = PlayerPreferences()
        val backend = prefs.getProtectedBackend()
        try {
            val detection = WslSidecarSetup.detect()
            assertEquals(WslSidecarSetup.State.FEATURE_OFF, detection.state)
            assertFalse(detection.featureOn(1) || detection.featureOn(2))
            assertFalse(WslSidecarSetup.planWindowsSide().wslPresent)
            assertFalse(WslSidecarSetup.otherDistributionsRegistered())
            assertFalse(WslSidecarSetup.imported())
            assertTrue(WslSidecarSetup.findLeftovers().isEmpty())
            assertEquals(null, WslSidecarSetup.removableFeature())
            assertFalse(WslSidecarSetup.pendingReady())
            // A full uninstall must still go through, quietly.
            WslSidecarSetup.uninstall(featureVersion = null, onStep = {}, onLine = { println("  $it") })
            // The sidecar with a config left pointing at WSL (WSL removed outside the app): unavailable, never started.
            assumeTrue("a real config exists here", !config.exists())
            config.parentFile.mkdirs()
            config.writeText("""{"command":["C:\\Windows\\System32\\wsl.exe","-d","kittytune-cdm","--exec","/opt/kittytune/ktcdm"]}""")
            prefs.setProtectedBackend(ProtectedBackend.WSL.key)
            CdmSidecar.reset()
            assertFalse(CdmSidecar.verify())
            assertTrue(CdmSidecar.lastFailure.orEmpty().contains("WSL is not installed"))
            CdmSidecar.reset()
            assertFalse(CdmSidecar.installIfConfigured())
        } finally {
            config.delete()
            prefs.setProtectedBackend(backend)
            CdmSidecar.reset()
        }
        Thread.sleep(2_000)
        assertEquals("wsl.exe was started — the installer stub would now be asking to install WSL", 0, wslProcesses())
    }

    private fun wslProcesses(): Int {
        val process = ProcessBuilder("tasklist", "/FI", "IMAGENAME eq wsl.exe", "/NH").redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor(20, TimeUnit.SECONDS)
        return out.lines().count { it.trim().startsWith("wsl.exe", ignoreCase = true) }
    }
}

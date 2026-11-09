package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * The leftover detector against real distributions, without touching the user's own: a throwaway
 * distribution (imported from the published rootfs) gets fake KittyTune leftovers plus a decoy, the
 * detector must find them, the removal must delete exactly them and keep the decoy, and the user's
 * other distributions — scanned read-only, never selected — must keep whatever they hold.
 *
 *   KITTYTUNE_RUN_PROVISION=1 ./gradlew :test --tests "*WslLeftoversTest" --no-daemon --no-configuration-cache --rerun-tasks
 */
class WslLeftoversTest {

    private val wsl = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\wsl.exe")
    private val throwaway = "kt-leftover-test"

    @Test(timeout = 600_000)
    fun `finds leftovers in another distribution and removes exactly them`() = runBlocking {
        assumeTrue("set KITTYTUNE_RUN_PROVISION=1", System.getenv("KITTYTUNE_RUN_PROVISION") == "1")
        assumeTrue("needs WSL", wsl.isFile)
        val prefs = PlayerPreferences()
        val originalBackend = prefs.getProtectedBackend()
        val work = File(System.getProperty("java.io.tmpdir"), "kt-leftover-test").apply { deleteRecursively(); mkdirs() }
        try {
            val tgz = File(work, "ktroot.tgz")
            URL(WslSidecarSetup.ROOTFS_URL).openStream().use { input -> tgz.outputStream().use { input.copyTo(it) } }
            // WSL2 works wherever WSL does; WSL1 only when its component is on.
            exec("--import", throwaway, File(work, "fs").absolutePath, tgz.absolutePath, "--version", "2")
            sh(
                "mkdir -p /opt/kittytune /home/tester/.local/share/kittytune /opt/decoy && " +
                    "echo x > /opt/kittytune/ktcdm && echo y > /home/tester/.local/share/kittytune/cdm && " +
                    "echo keep > /opt/decoy/file"
            )

            val before = othersSnapshot()
            val found = WslSidecarSetup.findLeftovers { println("  $it") }
            println("  found: $found")
            val ours = found.single { it.distro == throwaway }
            assertEquals(setOf("/opt/kittytune", "/home/tester/.local/share/kittytune"), ours.paths.toSet())

            WslSidecarSetup.uninstall(
                featureVersion = null,
                leftovers = listOf(ours),
                onStep = { println("uninstall: $it") },
                onLine = { println("  $it") },
            )

            val after = sh("ls -d /opt/kittytune /home/tester/.local/share/kittytune /opt/decoy/file 2>/dev/null; true")
            assertFalse("leftover still there:\n$after", after.contains("/opt/kittytune"))
            assertFalse("leftover still there:\n$after", after.contains("/home/tester/.local/share/kittytune"))
            assertTrue("the decoy was deleted", after.contains("/opt/decoy/file"))
            assertEquals("the user's other distributions changed", before, othersSnapshot())

            // And deleting a whole distribution through the uninstall — the throwaway one, never the user's.
            WslSidecarSetup.uninstall(
                featureVersion = null,
                deleteDistros = listOf(throwaway),
                onStep = { println("uninstall: $it") },
                onLine = { println("  $it") },
            )
            val registered = exec("--list", "--quiet")
            assertFalse("the throwaway distribution is still registered", registered.lines().any { it.trim() == throwaway })
            assertEquals("the user's other distributions changed", before, othersSnapshot())
        } finally {
            runCatching { exec("--unregister", throwaway) }
            work.deleteRecursively()
            prefs.setProtectedBackend(originalBackend)
            CdmSidecar.reset()
        }
    }

    /** What the scan sees in every distribution but the throwaway one: must be identical before and after. */
    private suspend fun othersSnapshot() =
        WslSidecarSetup.findLeftovers().filter { it.distro != throwaway }.map { it.distro to it.paths.sorted() }

    private fun sh(script: String): String = exec("-d", throwaway, "-u", "root", "--exec", "/bin/sh", "-c", script)

    private fun exec(vararg args: String): String {
        val builder = ProcessBuilder(listOf(wsl.absolutePath) + args).redirectErrorStream(true)
        builder.environment()["WSL_UTF8"] = "1"
        val process = builder.start()
        val out = process.inputStream.bufferedReader().readText().replace("\u0000", "")
        check(process.waitFor(300, TimeUnit.SECONDS)) { "wsl ${args.joinToString(" ")} timed out" }
        return out
    }
}

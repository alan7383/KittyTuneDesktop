package com.alananasss.kittytune.data.drm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import com.alananasss.kittytune.data.drm.WslSidecarSetup.UninstallStep as U
import java.io.File
import java.util.concurrent.TimeUnit

class WslSidecarSetupTest {

    @Test
    fun `distribution list survives UTF-16 output and skips helper distributions`() {
        // What an older wsl.exe prints when WSL_UTF8 is ignored: UTF-16LE read as UTF-8.
        val utf16 = "\uFEFFUbuntu-24.04\r\ndocker-desktop\r\nDebian\r\n"
            .toByteArray(Charsets.UTF_16LE).toString(Charsets.UTF_8)
        assertEquals(listOf("Ubuntu-24.04", "Debian"), WslSidecarSetup.parseDistros(utf16))
        assertEquals(emptyList<String>(), WslSidecarSetup.parseDistros("docker-desktop\ndocker-desktop-data\n\n"))
        assertEquals(
            listOf("Ubuntu", "docker-desktop"),
            WslSidecarSetup.parseAllDistros(listOf("Ubuntu", "docker-desktop", "").joinToString("\n")),
        )
    }

    @Test
    fun `the CDM fetch script is valid bash, has no carriage return, and uses only WSL1-safe tools`() {
        val bash = listOf("C:\\Program Files\\Git\\bin\\bash.exe", "/bin/bash", "/usr/bin/bash")
            .map(::File).firstOrNull { it.isFile }
        assumeTrue("bash is not available", bash != null)
        val script = WslSidecarSetup.CDM_SCRIPT
        assertTrue("CDM script has a carriage return", '\r' !in script)
        // GNU tar and dpkg-deb fail under WSL1 (ENOSYS); the fetch must use busybox ar/tar + xz instead.
        assertTrue("fetch should use ar", script.contains("ar x chrome.deb"))
        assertTrue("fetch should use xz", script.contains("xz -dc"))
        assertTrue("fetch must not call dpkg-deb", !script.contains("dpkg-deb"))
        val file = File.createTempFile("kt-wsl-cdm", ".sh")
        try {
            file.writeText(script)
            val process = ProcessBuilder(bash!!.absolutePath, "-n", file.absolutePath).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertTrue("bash -n timed out", process.waitFor(30, TimeUnit.SECONDS))
            assertEquals(output, 0, process.exitValue())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `the CDM fetch only runs tools the rootfs guarantees`() {
        // The rootfs build fails when busybox lacks one of REQUIRED; everything else the script runs must be
        // a shell builtin or one of the real binaries the rootfs ships. (Ubuntu's busybox has no `install`.)
        val build = File("scripts/build-wsl1-rootfs.sh").readText()
        val required = Regex("""REQUIRED="([^"]+)"""").find(build)!!.groupValues[1].split(" ").toSet()
        val shipped = setOf("curl", "xz")
        val builtins = setOf("set", "export", "if", "then", "elif", "else", "fi", "echo", "exit", "cd", "[", "{", "}")
        val commands = WslSidecarSetup.CDM_SCRIPT.lines()
            .map { it.substringBefore(" #").trim() }
            .filterNot { it.startsWith("#") || it.isEmpty() }
            .flatMap { it.split("|", ";", "&&", "||", "\$(") }
            .map { it.trim().trimStart('(').substringAfter("=").trim() }
            .mapNotNull { it.split(Regex("\\s+")).firstOrNull()?.takeIf { w -> w.isNotEmpty() && !w.startsWith("\"") && !w.startsWith("$") } }
            .filter { Regex("[a-z\\[{}][a-z0-9_-]*|\\[").matches(it) }
            .toSet()
        val unknown = commands - required - shipped - builtins
        assertTrue("CDM_SCRIPT runs tools the rootfs does not guarantee: $unknown", unknown.isEmpty())
    }

    @Test
    fun `wsl -l -v is parsed whatever the language, the default marker and the encoding`() {
        // A French Windows, with the default-distribution star and wsl.exe's UTF-16LE read as UTF-8.
        val output = "  NAME             STATE           VERSION\r\n* Ubuntu           Arrêté          2\r\n  kittytune-cdm    En cours d'exécution 1\r\n"
            .toByteArray(Charsets.UTF_16LE).toString(Charsets.UTF_8)
        assertEquals(mapOf("ubuntu" to 2, "kittytune-cdm" to 1), WslSidecarSetup.parseVerbose(output))
        assertEquals(emptyMap<String, Int>(), WslSidecarSetup.parseVerbose("  NAME   STATE   VERSION\n"))
    }

    @Test
    fun `readiness is judged for the chosen version only`() {
        val onlyWsl2 = WslSidecarSetup.Detection(WslSidecarSetup.State.NOT_SET_UP, wsl1Available = false, wsl2Available = true)
        assertEquals(false, onlyWsl2.featureOn(1))
        assertEquals(true, onlyWsl2.featureOn(2))
        val onlyWsl1 = WslSidecarSetup.Detection(WslSidecarSetup.State.NOT_SET_UP, wsl1Available = true, wsl2Available = false)
        assertEquals(true, onlyWsl1.featureOn(1))
        assertEquals(false, onlyWsl1.featureOn(2))
    }

    @Test
    fun `the pinned rootfs is a versioned, immutable release`() {
        assertTrue(WslSidecarSetup.ROOTFS_URL.contains("/cdm-rootfs-v${WslSidecarSetup.ROOTFS_VERSION}/"))
        assertTrue("sha256 must be pinned", Regex("[0-9a-f]{64}").matches(WslSidecarSetup.ROOTFS_SHA256))
    }

    @Test
    fun `uninstall only offers the component our distribution ran on, and only when nothing else needs it`() {
        val ours = WslSidecarSetup.DISTRO
        // WSL2 install, ours the last WSL2 distribution: its VM platform may go.
        assertEquals(2, WslSidecarSetup.removableFeature(2, wsl1On = false, wsl2On = true, versions = mapOf(ours to 2)))
        // WSL2 install next to the user's own WSL2 Ubuntu: never offer to break it.
        assertEquals(null, WslSidecarSetup.removableFeature(2, wsl1On = true, wsl2On = true, versions = mapOf(ours to 2, "ubuntu" to 2)))
        // WSL1 install next to a WSL2 Ubuntu: WSL1's component is ours alone.
        assertEquals(1, WslSidecarSetup.removableFeature(1, wsl1On = true, wsl2On = true, versions = mapOf(ours to 1, "ubuntu" to 2)))
        // Component already off: nothing to offer.
        assertEquals(null, WslSidecarSetup.removableFeature(1, wsl1On = false, wsl2On = true, versions = mapOf(ours to 1)))
        // Distribution already gone: WSL1's, the default, if nothing else runs on it.
        assertEquals(1, WslSidecarSetup.removableFeature(null, wsl1On = true, wsl2On = false, versions = emptyMap()))
    }

    @Test
    fun `only exact KittyTune locations are ever taken from a scan`() {
        val output = listOf(
            "KT_PATH:/opt/kittytune",
            "KT_PATH:/home/alan/.local/share/kittytune",
            "KT_PATH:/root/.local/share/kittytune",
            // Anything else, however it got there, is dropped.
            "KT_PATH:/",
            "KT_PATH:/opt",
            "KT_PATH:/opt/kittytune/../../etc",
            "KT_PATH:/home/a b/.local/share/kittytune",
            "KT_PATH:/home/x/.local/share/kittytune'; rm -rf / #",
            "KT_PATH:/home/../root/.local/share/kittytune",
            "some noise",
        ).joinToString("\n")
        assertEquals(
            listOf("/opt/kittytune", "/home/alan/.local/share/kittytune", "/root/.local/share/kittytune"),
            WslSidecarSetup.parseLeftoverScan(output),
        )
        val script = WslSidecarSetup.removeScript(listOf("/opt/kittytune", "/", "/etc/x'; reboot"))
        assertTrue(script.contains("rm -rf -- '/opt/kittytune'\n"))
        assertTrue(!script.contains("reboot") && !script.contains("'/'"))
        assertEquals("nothing valid: no rm at all", false, WslSidecarSetup.removeScript(listOf("/")).contains("rm -rf"))
    }

    @Test
    fun `the leftover scan is valid shell`() {
        val bash = listOf("C:\\Program Files\\Git\\bin\\bash.exe", "/bin/bash", "/usr/bin/bash").map(::File).firstOrNull { it.isFile }
        assumeTrue("bash is not available", bash != null)
        for (script in listOf(WslSidecarSetup.SCAN_SCRIPT, WslSidecarSetup.removeScript(listOf("/opt/kittytune")))) {
            assertTrue('\r' !in script)
            val file = File.createTempFile("kt-scan", ".sh")
            try {
                file.writeText(script)
                val process = ProcessBuilder(bash!!.absolutePath, "-n", file.absolutePath).redirectErrorStream(true).start()
                val out = process.inputStream.bufferedReader().readText()
                assertTrue(process.waitFor(30, TimeUnit.SECONDS))
                assertEquals(out, 0, process.exitValue())
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun `leftovers are cleaned right after the distribution`() {
        assertEquals(
            listOf(U.STOP, U.DISTRO, U.LEFTOVERS, U.SETTINGS, U.FEATURE),
            WslSidecarSetup.uninstallSteps(removeFeature = true, cleanLeftovers = true),
        )
    }

    @Test
    fun `uninstall steps follow whether the Windows feature is removed too`() {
        assertEquals(listOf(U.STOP, U.DISTRO, U.SETTINGS), WslSidecarSetup.uninstallSteps(removeFeature = false))
        assertEquals(listOf(U.STOP, U.DISTRO, U.SETTINGS, U.FEATURE), WslSidecarSetup.uninstallSteps(removeFeature = true))
    }
}

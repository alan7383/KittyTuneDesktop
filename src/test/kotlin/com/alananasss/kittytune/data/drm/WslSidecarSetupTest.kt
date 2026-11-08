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
    fun `uninstall steps follow whether the Windows feature is removed too`() {
        assertEquals(listOf(U.STOP, U.DISTRO, U.SETTINGS), WslSidecarSetup.uninstallSteps(removeFeature = false))
        assertEquals(listOf(U.STOP, U.DISTRO, U.SETTINGS, U.FEATURE), WslSidecarSetup.uninstallSteps(removeFeature = true))
    }
}

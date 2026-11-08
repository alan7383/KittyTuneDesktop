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
    }

    @Test
    fun `every setup script is valid bash and carries no carriage return`() {
        val bash = listOf("C:\\Program Files\\Git\\bin\\bash.exe", "/bin/bash", "/usr/bin/bash")
            .map(::File).firstOrNull { it.isFile }
        assumeTrue("bash is not available", bash != null)
        val scripts = mapOf(
            "tools" to WslSidecarSetup.TOOLS_SCRIPT,
            "cdm" to WslSidecarSetup.CDM_SCRIPT,
            "build" to WslSidecarSetup.BUILD_SCRIPT,
            "sources" to WslSidecarSetup.sourcesScript(),
            "inspect" to WslSidecarSetup.INSPECT_SCRIPT,
            "remove files" to WslSidecarSetup.REMOVE_FILES_SCRIPT,
            "remove packages" to WslSidecarSetup.removePackagesScript(listOf("curl", "build-essential")),
        )
        for ((name, script) in scripts) {
            assertTrue("$name has a carriage return", '\r' !in script)
            val file = File.createTempFile("kt-wsl-$name", ".sh")
            try {
                file.writeText(script)
                val process = ProcessBuilder(bash!!.absolutePath, "-n", file.absolutePath).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertTrue("$name: bash -n timed out", process.waitFor(30, TimeUnit.SECONDS))
                assertEquals("$name: $output", 0, process.exitValue())
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun `sources script writes every shipped file through a heredoc it closes`() {
        val script = WslSidecarSetup.sourcesScript()
        for (path in listOf("widevine/WidevineBridge.cpp", "cdmsidecar/CdmSidecar.cpp", "widevine/content_decryption_module.h")) {
            assertTrue(path, "cat > ${WslSidecarSetup.INSTALL_DIR}/src/$path <<'KITTYTUNE_SOURCE_EOF'" in script)
        }
        val opened = Regex("<<'KITTYTUNE_SOURCE_EOF'").findAll(script).count()
        val closed = script.lines().count { it == "KITTYTUNE_SOURCE_EOF" }
        assertEquals(opened, closed)
    }

    private fun plan(
        distro: String? = "Ubuntu",
        others: List<String> = emptyList(),
        packages: List<String> = emptyList(),
        distroOwned: Boolean = true,
        wslOwned: Boolean = true,
    ) = WslSidecarSetup.UninstallPlan(
        distro = distro, filesPresent = distro != null, packages = packages, distroInstalledByApp = distroOwned,
        otherDistros = others, wslPresent = true, wslInstalledByApp = wslOwned, configPresent = true,
    )

    @Test
    fun `docker distributions count when deciding whether WSL may go`() {
        assertEquals(
            listOf("Ubuntu", "docker-desktop"),
            WslSidecarSetup.parseAllDistros(listOf("Ubuntu", "docker-desktop", "").joinToString("\n")),
        )
        val withDocker = plan(others = listOf("docker-desktop"))
        assertEquals(false, WslSidecarSetup.canRemoveWsl(withDocker, removeDistro = true))
    }

    @Test
    fun `WSL only goes together with the distribution and when nothing else uses it`() {
        assertEquals(true, WslSidecarSetup.canRemoveWsl(plan(), removeDistro = true))
        assertEquals(false, WslSidecarSetup.canRemoveWsl(plan(), removeDistro = false))
        assertEquals(true, WslSidecarSetup.canRemoveWsl(plan(distro = null), removeDistro = false))
        assertEquals(false, WslSidecarSetup.canRemoveWsl(plan(others = listOf("Debian")), removeDistro = true))
    }

    @Test
    fun `steps follow the choices`() {
        assertEquals(
            listOf(U.STOP, U.DISTRO, U.SETTINGS, U.WSL),
            WslSidecarSetup.uninstallSteps(plan(), removeDistro = true, removeWsl = true),
        )
        assertEquals(
            listOf(U.STOP, U.FILES, U.PACKAGES, U.SETTINGS),
            WslSidecarSetup.uninstallSteps(plan(packages = listOf("curl")), removeDistro = false, removeWsl = true),
        )
        assertEquals(
            listOf(U.STOP, U.FILES, U.SETTINGS),
            WslSidecarSetup.uninstallSteps(plan(), removeDistro = false, removeWsl = false),
        )
        assertEquals(
            listOf(U.STOP, U.SETTINGS),
            WslSidecarSetup.uninstallSteps(plan(distro = null, others = listOf("Debian")), removeDistro = false, removeWsl = true),
        )
    }

    @Test
    fun `package removal only marks recorded names and never purges them outright`() {
        val script = WslSidecarSetup.removePackagesScript(listOf("curl", "build-essential", "evil; rm -rf /", "\$(reboot)"))
        assertTrue(script.contains("apt-mark auto curl build-essential "))
        assertTrue(!script.contains("rm -rf /") && !script.contains("reboot"))
        assertTrue(!script.contains("apt-get purge"))
    }
}

package com.alananasss.kittytune.data.drm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CdmSurvivesUpdateTest {
    @get:Rule val profile = TemporaryFolder()

    @Test fun aLegacyInstallIsMovedOutOfTheMsiFolderAndSurvivesItsRemoval() {
        assumeTrue(NativeCdmSetup.windows)
        val msiFolder = File(profile.root, "KittyTune")
        val installDirectory = File(profile.root, "KittyTuneCdm")
        val config = File(installDirectory, "cdm-sidecar.json")
        val legacyConfig = File(msiFolder, config.name)
        NativeCdmSetup.migrateLegacyInstall(config, legacyConfig)
        // What 1.7.0 left: everything inside the MSI's install directory.
        val legacy = File(msiFolder, "cdm").apply { mkdirs() }
        val host = File(legacy, "ktcdm-0123456789abcdef.exe").apply { writeBytes(ByteArray(4096) { 7 }) }
        val module = File(legacy, "libwidevinecdm.so").apply {
            val bytes = ByteArray(9 * 1024 * 1024)
            byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1).copyInto(bytes)
            bytes[18] = 0x3e
            writeBytes(bytes)
        }
        legacyConfig.writeText(
            JSONObject().put("command", JSONArray().put(host.absolutePath).put(module.absolutePath)).toString()
        )

        NativeCdmSetup.migrateLegacyInstall(config, legacyConfig)

        // An update: the previous version's uninstaller removes its install directory recursively.
        msiFolder.deleteRecursively()
        val command = JSONObject(config.readText()).getJSONArray("command")
        val migratedHost = File(command.getString(0))
        val migratedModule = File(command.getString(1))
        assertTrue(migratedHost.isFile)
        assertTrue(migratedModule.isFile)
        assertEquals(installDirectory.canonicalFile, migratedHost.canonicalFile.parentFile)
        assertEquals(installDirectory.canonicalFile, migratedModule.canonicalFile.parentFile)
    }
}

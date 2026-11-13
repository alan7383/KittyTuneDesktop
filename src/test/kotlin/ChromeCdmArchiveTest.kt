package com.alananasss.kittytune.data.drm

import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class ChromeCdmArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private val member = "./opt/google/chrome/WidevineCdm/_platform_specific/linux_x64/libwidevinecdm.so"
    private fun module() = ByteArray(8 * 1024 * 1024).apply {
        this[0] = 0x7f; this[1] = 'E'.code.toByte(); this[2] = 'L'.code.toByte(); this[3] = 'F'.code.toByte()
        this[4] = 2; this[5] = 1; this[18] = 0x3e
    }

    private fun deb(name: String = member, bytes: ByteArray = module(), truncate: Boolean = false): File {
        val payload = ByteArrayOutputStream()
        XZOutputStream(payload, LZMA2Options(1)).use { xz ->
            TarArchiveOutputStream(xz).use { tar ->
                tar.putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
        }
        val compressed = payload.toByteArray().let { if (truncate) it.copyOf(it.size - 8) else it }
        return temporary.newFile("chrome.deb").apply {
            ArArchiveOutputStream(outputStream()).use { ar ->
                for ((entryName, contents) in listOf("debian-binary" to "2.0\n".toByteArray(), "data.tar.xz" to compressed)) {
                    ar.putArchiveEntry(ArArchiveEntry(entryName, contents.size.toLong()))
                    ar.write(contents)
                    ar.closeArchiveEntry()
                }
            }
        }
    }

    @Test fun extractsOnlyTheModuleWithoutExternalPrograms() {
        val expected = module()
        val target = File(temporary.newFolder("destination"), "libwidevinecdm.so")
        ChromeCdmArchive.extract(deb(bytes = expected), target)
        assertArrayEquals(expected, target.readBytes())
        assertEquals(listOf(target.name), target.parentFile.listFiles()!!.map { it.name })
    }

    @Test fun missingMemberPreservesTheInstalledModule() {
        val target = temporary.newFile("libwidevinecdm.so").apply { writeText("previous") }
        assertThrows(IOException::class.java) { ChromeCdmArchive.extract(deb(name = "other.so"), target) }
        assertEquals("previous", target.readText())
        assertFalse(temporary.root.listFiles()!!.any { it.extension == "part" })
    }

    @Test fun invalidModuleIsNotPublished() {
        val target = File(temporary.root, "libwidevinecdm.so")
        assertThrows(IOException::class.java) { ChromeCdmArchive.extract(deb(bytes = ByteArray(32)), target) }
        assertFalse(target.exists())
        assertFalse(temporary.root.listFiles()!!.any { it.extension == "part" })
    }

    @Test fun truncatedXzFooterDoesNotPublishAnOtherwiseCompleteMember() {
        val target = temporary.newFile("libwidevinecdm.so").apply { writeText("previous") }
        assertThrows(IOException::class.java) { ChromeCdmArchive.extract(deb(truncate = true), target) }
        assertEquals("previous", target.readText())
        assertFalse(temporary.root.listFiles()!!.any { it.extension == "part" })
    }
}

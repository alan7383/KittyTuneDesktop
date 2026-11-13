package com.alananasss.kittytune.data.drm

import com.alananasss.kittytune.core.AtomicFiles
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

internal object ChromeCdmArchive {
    private const val MEMBER = "opt/google/chrome/WidevineCdm/_platform_specific/linux_x64/libwidevinecdm.so"
    private const val MIN_BYTES = 8L * 1024 * 1024
    private const val MAX_BYTES = 128L * 1024 * 1024

    fun extract(packageFile: File, destination: File) {
        val directory = destination.absoluteFile.parentFile.apply { mkdirs() }
        val partial = File.createTempFile("widevine-", ".part", directory)
        try {
            ArArchiveInputStream(packageFile.inputStream().buffered()).use { ar ->
                var entry = ar.nextEntry
                while (entry != null && entry.name != "data.tar.xz") entry = ar.nextEntry
                if (entry == null) throw IOException("Chrome package contains no data.tar.xz")
                // Chrome currently uses a 256 MiB XZ dictionary, plus decoder overhead.
                XZInputStream(ar, 384 * 1024).use { decoded ->
                    TarArchiveInputStream(decoded).use { tar ->
                        var found = false
                        var member = tar.nextEntry
                        while (member != null) {
                            if (member.name.removePrefix("./") == MEMBER) {
                                if (found || !member.isFile || !tar.canReadEntryData(member) || member.size !in MIN_BYTES..MAX_BYTES)
                                    throw IOException("invalid Widevine module entry")
                                val copied = partial.outputStream().buffered().use { tar.copyTo(it) }
                                if (copied != member.size) throw IOException("truncated Widevine module")
                                found = true
                            }
                            member = tar.nextEntry
                        }
                        // TAR end markers can precede the XZ checksum/footer.
                        decoded.copyTo(OutputStream.nullOutputStream())
                        if (!found) throw IOException("Chrome package contains no libwidevinecdm.so")
                    }
                }
            }
            if (!isUsableModule(partial)) throw IOException("the extracted module is not a Linux x86-64 ELF")
            AtomicFiles.replace(partial, destination)
        } finally {
            partial.delete()
        }
    }

    fun isUsableModule(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() !in MIN_BYTES..MAX_BYTES) return false
        val header = file.inputStream().use { it.readNBytes(20) }
        header.size == 20 && header[0] == 0x7f.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte() &&
            header[4] == 2.toByte() && header[5] == 1.toByte() && header[18] == 0x3e.toByte() && header[19] == 0.toByte()
    }.getOrDefault(false)
}

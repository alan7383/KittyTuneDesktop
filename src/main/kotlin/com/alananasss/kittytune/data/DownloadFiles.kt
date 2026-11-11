package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AtomicFiles
import java.io.File

internal object DownloadFiles {
    fun store(source: File, directory: File, trackId: Long, artist: String, title: String, extension: String): File {
        directory.mkdirs()
        val clean = "$artist - $title".replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val label = buildString {
            val points = clean.codePoints().iterator()
            var bytes = 0
            while (points.hasNext()) {
                val point = points.nextInt()
                val size = when {
                    point <= 0x7f -> 1
                    point <= 0x7ff -> 2
                    point <= 0xffff -> 3
                    else -> 4
                }
                if (bytes + size > 180) break
                appendCodePoint(point)
                bytes += size
            }
        }
        val target = File(directory, "$label [$trackId].$extension")
        val partial = File.createTempFile("kittytune-", ".part", directory)
        try {
            source.inputStream().use { input -> partial.outputStream().use { output -> input.copyTo(output) } }
            AtomicFiles.replace(partial, target)
            return target
        } finally {
            partial.delete()
        }
    }
}

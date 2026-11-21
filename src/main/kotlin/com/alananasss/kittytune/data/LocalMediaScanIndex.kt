package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AtomicFiles
import com.alananasss.kittytune.data.local.LocalTrack
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

internal class LocalMediaScanIndex(private val indexFile: File) {
    private data class Stamp(val size: Long, val modified: String)

    private val previous = load()
    private val scanned = HashMap<String, Stamp>()

    fun scan(file: File, existing: LocalTrack?, read: (File) -> LocalTrack?): LocalTrack? {
        val path = file.absolutePath
        val before = stamp(file)
        if (before != null && previous[path] == before && existing?.localAudioPath == path &&
            (existing.localArtworkPath.isEmpty() || File(existing.localArtworkPath).isFile)
        ) {
            scanned[path] = before
            return null
        }
        scanned.remove(path)
        val result = read(file) ?: return null
        if (before != null && result.localAudioPath == path && stamp(file) == before) scanned[path] = before
        return result
    }

    // Save only after the corresponding database inserts have succeeded.
    fun save() {
        if (scanned == previous && indexFile.isFile) return
        val parent = indexFile.absoluteFile.parentFile
        parent.mkdirs()
        val tmp = File.createTempFile("local-scan-", ".tmp", parent)
        try {
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(1)
                out.writeInt(scanned.size)
                for ((path, stamp) in scanned) {
                    out.writeUTF(path)
                    out.writeLong(stamp.size)
                    out.writeUTF(stamp.modified)
                }
            }
            AtomicFiles.replace(tmp, indexFile)
        } finally {
            tmp.delete()
        }
    }

    private fun load(): Map<String, Stamp> = try {
        DataInputStream(indexFile.inputStream().buffered()).use { input ->
            require(input.readInt() == 1)
            val count = input.readInt()
            require(count in 0..1_000_000 && count <= indexFile.length() / 12)
            val entries = HashMap<String, Stamp>()
            repeat(count) { entries[input.readUTF()] = Stamp(input.readLong(), input.readUTF()) }
            require(input.read() == -1)
            entries
        }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun stamp(file: File): Stamp? = try {
        val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        if (attrs.isRegularFile) Stamp(attrs.size(), attrs.lastModifiedTime().toString()) else null
    } catch (_: Exception) {
        null
    }
}

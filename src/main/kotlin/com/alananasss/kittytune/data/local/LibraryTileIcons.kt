package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.core.AppDirs
import java.io.File
import javax.imageio.ImageIO

/**
 * The images standing in for the built-in icons of the fixed library tiles (issue #33).
 *
 * The picked file is copied into the app's own directory rather than remembered by path. A tile
 * pointing at `~/Downloads/heart.png` loses its icon the first time that folder is tidied up, and
 * the library then has a blank square with no way to tell why.
 *
 * The copy only happens for a file that decodes as an image. Handing an undecodable one straight to
 * the image loader is what made the app-icon picker crash from inside composition, and a tile is
 * drawn in the same place — better to refuse the file while the picker is still open and say so.
 */
object LibraryTileIcons {

    private val dir: File by lazy { File(AppDirs.dataDir, "library_tile_icons") }

    /**
     * A tile icon is a 40dp square at most, so anything past a few megabytes is a photo picked by
     * mistake. Decoding it would cost far more memory than the tile can ever use.
     */
    private const val MAX_BYTES = 8L * 1024 * 1024

    /**
     * Copies [source] in as [tile]'s icon.
     *
     * @return the stored file's path, or null when the file is unreadable, too large, or not an
     *   image — in which case nothing is written and any icon already stored stays.
     */
    fun import(tile: String, source: File): String? {
        if (!source.isFile || source.length() == 0L || source.length() > MAX_BYTES) return null
        if (!decodes(source)) return null

        val extension = source.extension.lowercase().takeIf { it.isNotBlank() } ?: "png"
        val target = File(dir, "$tile.$extension")
        return runCatching {
            dir.mkdirs()
            // A previous icon may have had a different extension, and two files for one tile would
            // leave the loser behind forever.
            clear(tile)
            source.copyTo(target, overwrite = true)
            target.absolutePath
        }.getOrNull()
    }

    /** Drops [tile]'s stored icon, if it has one. Safe to call when it has none. */
    fun clear(tile: String) {
        runCatching {
            dir.listFiles { file -> file.nameWithoutExtension == tile }?.forEach { it.delete() }
        }
    }

    /** Whether ImageIO can actually decode this file, which is what the tile will have to do. */
    private fun decodes(file: File): Boolean =
        runCatching { ImageIO.read(file) != null }.getOrDefault(false)
}

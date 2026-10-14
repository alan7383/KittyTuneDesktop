package com.alananasss.kittytune.core

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Replaces [target] with the freshly written [tmp] the way that works on every desktop OS.
 *
 * `File.renameTo` looks atomic but is silently best-effort: on Windows it fails outright while the
 * target exists — it just returns false, no exception, so the caller believes the file was swapped
 * when nothing happened. The queue cache was written that way, which meant the persistent queue
 * never actually updated on Windows (found in the audit: "renameTo Windows sur queue_cache").
 *
 * `Files.move` with [StandardCopyOption.REPLACE_EXISTING] goes through the platform's supported
 * replace (MoveFileEx with MOVEFILE_REPLACE_EXISTING on Windows), which works even while a reader
 * holds the old file open. [StandardCopyOption.ATOMIC_MOVE] is tried first so a crash mid-swap can
 * never leave half of either file; the plain replace is the fallback because not every filesystem
 * (network mounts, some FAT variants) supports atomic moves.
 */
object AtomicFiles {

    fun replace(tmp: File, target: File) {
        val tmpPath: Path = tmp.toPath()
        val targetPath: Path = target.toPath()
        try {
            Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmpPath, targetPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

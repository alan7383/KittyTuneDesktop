package com.alananasss.kittytune.core

import java.io.File

/**
 * Windows/desktop data locations, replacing Android's Context.filesDir / cacheDir / etc.
 *
 * Layout:
 *   %APPDATA%\KittyTune\        -> settings, database, playlists, tokens (roaming)
 *   %LOCALAPPDATA%\KittyTune\   -> caches (images, audio cache)
 *   Music\KittyTune\            -> default download location (user-changeable)
 */
object AppDirs {

    val dataDir: File = File(
        System.getenv("APPDATA") ?: (System.getProperty("user.home") + "/.config"),
        "KittyTune",
    ).apply { mkdirs() }

    val cacheDir: File = File(
        System.getenv("LOCALAPPDATA") ?: (System.getProperty("user.home") + "/.cache"),
        "KittyTune",
    ).apply { mkdirs() }

    val imageCacheDir: File = File(cacheDir, "images").apply { mkdirs() }
    val audioCacheDir: File = File(cacheDir, "audio").apply { mkdirs() }

    /** Default downloads target: ~\Music\KittyTune (overridable in settings, like Android). */
    val defaultDownloadDir: File = File(
        File(System.getProperty("user.home"), "Music"),
        "KittyTune",
    )

    fun sizeOf(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

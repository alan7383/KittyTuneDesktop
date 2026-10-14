package com.alananasss.kittytune.core

import java.awt.Desktop
import java.io.File
import java.net.URI
import java.util.Locale

fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            return
        }
    } catch (_: Throwable) {}

    try {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        when {
            os.contains("linux") || os.contains("nix") -> {
                ProcessBuilder("xdg-open", url).start()
            }
            os.contains("mac") -> {
                ProcessBuilder("open", url).start()
            }
            os.contains("win") -> {
                ProcessBuilder("cmd", "/c", "start", "", url).start()
            }
            else -> {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI(url))
                }
            }
        }
    } catch (_: Throwable) {}
}

fun openDirectoryInFileManager(directory: File) {
    if (!directory.exists()) {
        directory.mkdirs()
    }
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(directory)
            return
        }
    } catch (_: Throwable) {}

    try {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        when {
            os.contains("linux") || os.contains("nix") -> {
                ProcessBuilder("xdg-open", directory.absolutePath).start()
            }
            os.contains("mac") -> {
                ProcessBuilder("open", directory.absolutePath).start()
            }
            os.contains("win") -> {
                ProcessBuilder("explorer.exe", directory.absolutePath).start()
            }
            else -> {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(directory)
                }
            }
        }
    } catch (_: Throwable) {}
}



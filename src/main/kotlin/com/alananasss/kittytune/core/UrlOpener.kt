package com.alananasss.kittytune.core

import java.awt.Desktop
import java.net.URI

fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
            return
        }
    } catch (_: Throwable) {}

    try {
        val os = System.getProperty("os.name").lowercase()
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


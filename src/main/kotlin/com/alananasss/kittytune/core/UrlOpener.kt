package com.alananasss.kittytune.core

import java.awt.Desktop
import java.net.URI

fun openUrl(url: String) {
    try {
        Desktop.getDesktop().browse(URI(url))
    } catch (_: Exception) {
        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("linux") -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
                os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", url))
                os.contains("win") -> Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", url))
            }
        } catch (_: Exception) {}
    }
}

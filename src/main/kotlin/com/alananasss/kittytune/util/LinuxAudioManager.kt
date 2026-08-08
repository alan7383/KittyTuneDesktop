package com.alananasss.kittytune.util

import java.io.BufferedReader
import java.io.InputStreamReader

data class LinuxAudioSink(val id: String, val description: String)

object LinuxAudioManager {
    fun cleanName(rawName: String): String = LinuxAudioNamer.cleanName(rawName)

    fun getDefaultSinkId(): String? {
        if (!System.getProperty("os.name").lowercase().contains("linux")) return null
        return try {
            val process = ProcessBuilder("pactl", "info").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var defaultSink: String? = null
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val nonNullLine = line ?: continue
                if (nonNullLine.startsWith("Default Sink:")) {
                    defaultSink = nonNullLine.substringAfter("Default Sink:").trim()
                    break
                }
            }
            process.waitFor()
            defaultSink
        } catch (e: Exception) {
            null
        }
    }

    fun getOutputSinks(): List<LinuxAudioSink> {
        if (!System.getProperty("os.name").lowercase().contains("linux")) return emptyList()
        val sinks = mutableListOf<LinuxAudioSink>()
        try {
            val process = ProcessBuilder("pactl", "list", "sinks").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var currentName: String? = null
            var currentDesc: String? = null
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (l.startsWith("Name:")) {
                    if (currentName != null && currentDesc != null) {
                        sinks.add(LinuxAudioSink(currentName, currentDesc))
                    }
                    currentName = l.substringAfter("Name:").trim()
                    currentDesc = null
                } else if (l.startsWith("Description:")) {
                    currentDesc = l.substringAfter("Description:").trim()
                }
            }
            if (currentName != null && currentDesc != null) {
                sinks.add(LinuxAudioSink(currentName, currentDesc))
            }
            process.waitFor()
        } catch (e: Exception) {
            // Fallback
        }
        return sinks
    }
}

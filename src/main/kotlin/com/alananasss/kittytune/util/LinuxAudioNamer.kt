package com.alananasss.kittytune.util

import java.io.File

object LinuxAudioNamer {
    private val cardNames = mutableMapOf<Int, String>()
    private var isLoaded = false

    private fun loadCards() {
        if (isLoaded) return
        try {
            val file = File("/proc/asound/cards")
            if (file.exists()) {
                val lines = file.readLines()
                var currentCardId = -1
                for (line in lines) {
                    if (line.matches(Regex("""^\s*\d+\s+\[.*"""))) {
                        // Example: " 1 [Generic        ]: HDA-Intel - HD-Audio Generic"
                        val idMatch = Regex("""^\s*(\d+)""").find(line)
                        if (idMatch != null) {
                            currentCardId = idMatch.groupValues[1].toInt()
                            val parts = line.split(":")
                            if (parts.size >= 2) {
                                val namePart = parts[1].split("-").last().trim()
                                cardNames[currentCardId] = namePart
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        isLoaded = true
    }

    fun cleanName(rawName: String): String {
        val name = rawName.trim()
        if (System.getProperty("os.name").lowercase().contains("linux")) {
            loadCards()

            if (name.lowercase().contains("default")) {
                return com.alananasss.kittytune.core.Strings.get("pref_audio_device_default")
            }

            // Extract plughw or hw
            val hwMatch = Regex("""\[(?:plug)?hw:(\d+),(\d+)\]""").find(name)
            if (hwMatch != null) {
                val cardId = hwMatch.groupValues[1].toInt()
                val devId = hwMatch.groupValues[2].toInt()
                val cardName = cardNames[cardId]
                if (cardName != null) {
                    return "$cardName (Dev $devId)"
                }
            }
        }
        return name
    }
}

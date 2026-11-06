package com.alananasss.kittytune.util

data class LinuxAudioSink(val id: String, val description: String)

object LinuxAudioManager {
    fun cleanName(rawName: String): String = LinuxAudioNamer.cleanName(rawName)

    fun getDefaultSinkId(): String? {
        if (!System.getProperty("os.name").lowercase().contains("linux")) return null
        return com.alananasss.kittytune.audio.LinuxAudioIntegration.command("pactl", "get-default-sink")?.trim()
    }

    fun getOutputSinks(): List<LinuxAudioSink> {
        if (!System.getProperty("os.name").lowercase().contains("linux")) return emptyList()
        return runCatching {
            val json = com.alananasss.kittytune.audio.LinuxAudioIntegration
                .command("pactl", "--format=json", "list", "sinks") ?: return emptyList()
            com.google.gson.JsonParser.parseString(json).asJsonArray.map { item ->
                val sink = item.asJsonObject
                val name = sink.get("name").asString
                LinuxAudioSink(name, sink.get("description")?.asString ?: name)
            }
        }.getOrDefault(emptyList())
    }
}

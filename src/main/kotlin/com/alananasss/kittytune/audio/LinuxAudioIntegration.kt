package com.alananasss.kittytune.audio

import com.alananasss.kittytune.core.LinuxAppIdHelper
import com.alananasss.kittytune.util.LinuxAudioManager
import com.google.gson.JsonParser
import com.sun.jna.NativeLibrary
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/** Identity and routing for this process only. Never changes the server's default sink. */
internal object LinuxAudioIntegration {
    private val isLinux get() = System.getProperty("os.name").lowercase().contains("linux")
    private val processId = ProcessHandle.current().pid().toString()
    private val libc by lazy { NativeLibrary.getInstance("c") }
    private var initialized = false
    private var originalPipewireNode: String? = null
    private var originalPulseSink: String? = null

    internal fun nativeEnvironment(name: String): String? =
        libc.getFunction("getenv").invokePointer(arrayOf(name))?.getString(0)

    private fun environment(name: String, value: String?) {
        val result = if (value == null) libc.getFunction("unsetenv").invokeInt(arrayOf(name))
            else libc.getFunction("setenv").invokeInt(arrayOf<Any>(name, value, 1))
        check(result == 0) { "Cannot configure audio environment: $name" }
    }

    @Synchronized
    fun initialize() {
        if (!isLinux || initialized) return
        originalPipewireNode = nativeEnvironment("PIPEWIRE_NODE")
        originalPulseSink = nativeEnvironment("PULSE_SINK")
        // PIPEWIRE_ALSA is documented by PipeWire's client.conf(5). Keep user tuning.
        val existing = nativeEnvironment("PIPEWIRE_ALSA").orEmpty().trim().removeSurrounding("{", "}")
        val id = LinuxAppIdHelper.APP_ID
        environment("PIPEWIRE_ALSA", "{ $existing application.name=KittyTune application.id=$id " +
            "application.icon-name=$id application.process.id=$processId node.name=$id.playback.$processId " +
            "media.name=KittyTune media.role=Music }")
        // Also covers systems using ALSA's PulseAudio plugin instead of PipeWire's plugin.
        mapOf("application.name" to "KittyTune", "application.id" to id,
            "application.icon_name" to id, "application.process.id" to processId,
            "media.name" to "KittyTune", "media.role" to "music").forEach { (key, value) ->
            environment("PULSE_PROP_$key", value)
        }
        initialized = true
    }

    @Synchronized
    fun openLine(format: AudioFormat, bufferBytes: Int, deviceName: String): SourceDataLine {
        initialize()
        val sinks = LinuxAudioManager.getOutputSinks()
        val serverDevice = deviceName.isEmpty() || sinks.any { it.id == deviceName } ||
            deviceName.startsWith("alsa_output.") || deviceName.startsWith("bluez_output.")
        val target = deviceName.takeIf { selected -> sinks.any { it.id == selected } }
        environment("PIPEWIRE_NODE", target ?: originalPipewireNode)
        environment("PULSE_SINK", target ?: originalPulseSink)
        val info = DataLine.Info(SourceDataLine::class.java, format)
        val mixer = if (serverDevice) null else mixerNamed(deviceName, info)
        val line = (mixer?.getLine(info) ?: AudioSystem.getLine(info)) as SourceDataLine
        try {
            line.open(format, bufferBytes)
            line.start()
            if (serverDevice) {
                val destination = target ?: LinuxAudioManager.getDefaultSinkId()
                if (destination != null) routeOwnStreams(destination)
            }
            return line
        } catch (error: Throwable) {
            line.close()
            throw error
        }
    }

    internal fun ownStreamIds(json: String, pid: String = processId): List<String> =
        JsonParser.parseString(json).asJsonArray.mapNotNull { item ->
            val stream = item.asJsonObject
            val props = stream.getAsJsonObject("properties") ?: return@mapNotNull null
            // An application name alone could belong to another KittyTune instance.
            if (props.get("application.process.id")?.asString != pid) return@mapNotNull null
            stream.get("index")?.asString
        }

    internal fun routeOwnStreams(sink: String): Boolean {
        // The server can publish a newly opened stream asynchronously.
        repeat(10) {
            val json = command("pactl", "--format=json", "list", "sink-inputs") ?: return false
            val ids = runCatching { ownStreamIds(json) }.getOrDefault(emptyList())
            if (ids.isNotEmpty()) {
                // A disappearing crossfade stream must not prevent routing the remaining ones.
                return ids.map { command("pactl", "move-sink-input", it, sink) != null }.all { it }
            }
            Thread.sleep(20)
        }
        return false
    }

    internal fun command(vararg arguments: String): String? = runCatching {
        val process = ProcessBuilder(*arguments).redirectErrorStream(true).start()
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().use { it.readText() } }
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) return@runCatching null
            output.get(1, TimeUnit.SECONDS).takeIf { process.exitValue() == 0 }
        } finally { if (process.isAlive) process.destroyForcibly() }
    }.getOrNull()
}

package com.alananasss.kittytune.audio

import com.alananasss.kittytune.util.LinuxAudioManager
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine

/** A place the sound can go: [id] is what the playback device setting stores, [label] what the listener reads. */
data class OutputDevice(val id: String, val label: String) {
    /** A guess from the name, good enough to pick an icon by. */
    val isHeadphones: Boolean
        get() = HEADPHONE_WORDS.any { label.contains(it, ignoreCase = true) }

    private companion object {
        val HEADPHONE_WORDS = listOf("headphone", "headset", "earphone", "buds", "airpods", "наушник", "гарнитур", "kopfhörer", "casque", "fejhallgató", "tai nghe")
    }
}

/**
 * The output devices sound can be sent to, without the system default, which the setting stores as "".
 *
 * PulseAudio sinks on Linux, where Java's mixers name the sound card rather than the device; Java's mixers that
 * take a playback line elsewhere, ports and duplicate names left out. Asks the sound system, so call it off the UI
 * thread.
 */
fun listOutputDevices(): List<OutputDevice> {
    val isLinux = System.getProperty("os.name").lowercase().contains("linux")
    if (isLinux) {
        val sinks = LinuxAudioManager.getOutputSinks()
        if (sinks.isNotEmpty()) return sinks.map { OutputDevice(it.id, it.description) }
    }
    val devices = mutableListOf<OutputDevice>()
    val seen = mutableSetOf<String>()
    val playback = DataLine.Info(SourceDataLine::class.java, null)
    for (info in runCatching { AudioSystem.getMixerInfo() }.getOrDefault(emptyArray())) {
        val name = info.name.trim()
        if (name.isEmpty() || name in seen || name.contains("Port")) continue
        val canPlay = runCatching { AudioSystem.getMixer(info).isLineSupported(playback) }.getOrDefault(false)
        if (!canPlay) continue
        seen += name
        devices += OutputDevice(name, LinuxAudioManager.cleanName(name))
    }
    return devices
}

/**
 * Java's mixer called [deviceName] when it takes [info], else null for the system default. On Linux the
 * device is a PulseAudio sink chosen as the default one, so there is no mixer to look up.
 */
fun mixerNamed(deviceName: String, info: DataLine.Info): Mixer? {
    if (deviceName.isEmpty()) return null
    val target = runCatching { AudioSystem.getMixerInfo() }.getOrDefault(emptyArray()).firstOrNull { it.name.trim() == deviceName }
        ?: return null
    return runCatching { AudioSystem.getMixer(target).takeIf { it.isLineSupported(info) } }.getOrNull()
}

/** A playback line on the device the listener chose for the music, opened and started. */
fun openPlaybackLine(format: javax.sound.sampled.AudioFormat, bufferBytes: Int): SourceDataLine {
    val info = DataLine.Info(SourceDataLine::class.java, format)
    val mixer = mixerNamed(com.alananasss.kittytune.data.local.PlayerPreferences().getAudioDevice(), info)
    val line = (mixer?.getLine(info) ?: AudioSystem.getLine(info)) as SourceDataLine
    line.open(format, bufferBytes)
    line.start()
    return line
}

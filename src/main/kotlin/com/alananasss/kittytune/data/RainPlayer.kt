package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.data.ambient.AmbientPlayback
import com.alananasss.kittytune.data.ambient.BundledAmbientPcm
import com.alananasss.kittytune.data.ambient.StreamingAmbientPlayback
import com.alananasss.kittytune.data.local.PlayerPreferences
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/** Independent looping ambience, unaffected by the main player's speed or effects. */
class RainPlayer internal constructor(private val createPlayback: (String, Float) -> AmbientPlayback) {
    constructor() : this({ type, volume ->
        StreamingAmbientPlayback(
            openSource = { BundledAmbientPcm(resourceName(type)) },
            openLine = { openOutputLine(PlayerPreferences().getAudioDevice()) },
            initialVolume = volume,
            onReady = { removeLegacyWavs(AppDirs.cacheDir) },
        )
    })

    private var playback: AmbientPlayback? = null
    private var isEnabled = false
    private var volume = 1f
    private var currentType = "rain"

    @Synchronized fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        playback?.setVolume(this.volume)
    }

    @Synchronized fun setAmbientType(type: String) {
        val safeType = if (type.isBlank()) "rain" else type.lowercase()
        if (currentType == safeType) return
        currentType = safeType
        if (isEnabled) restart()
    }

    @Synchronized fun reloadDevice() {
        if (isEnabled) restart()
    }

    @Synchronized fun setEnabled(enabled: Boolean) {
        if (isEnabled == enabled) return
        isEnabled = enabled
        if (enabled) restart() else stop()
    }

    private fun restart() {
        stop()
        try {
            val next = createPlayback(currentType, volume)
            playback = next
            next.start()
        } catch (failure: Exception) {
            stop()
            failure.printStackTrace()
        }
    }

    private fun stop() {
        runCatching { playback?.close() }
        playback = null
    }

    @Synchronized fun release() {
        stop()
        isEnabled = false
    }

    companion object {
        internal fun resourceName(type: String): String = when (type) {
            "fireplace" -> "fireplace.mp3"
            "ocean" -> "ocean.mp3"
            "cafe" -> "cafe.mp3"
            else -> "rain.mp3"
        }

        internal fun removeLegacyWavs(cacheDir: File) {
            for (name in listOf("rain", "ocean", "fireplace", "cafe")) {
                val file = File(cacheDir, "$name.wav")
                if (file.isFile) runCatching { file.delete() }
            }
        }

        private fun openOutputLine(deviceName: String): SourceDataLine {
            val format = AudioFormat(44100f, 16, 2, true, false)
            val info = DataLine.Info(SourceDataLine::class.java, format)
            val selected = if (deviceName.isEmpty()) null else runCatching {
                AudioSystem.getMixerInfo().firstOrNull { it.name.trim() == deviceName }?.let {
                    val mixer = AudioSystem.getMixer(it)
                    if (mixer.isLineSupported(info)) mixer.getLine(info) as SourceDataLine else null
                }
            }.getOrNull()
            val line = selected ?: AudioSystem.getSourceDataLine(format)
            try {
                line.open(format, 88_200)
                return line
            } catch (failure: Throwable) {
                runCatching { line.close() }
                throw failure
            }
        }
    }
}

package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import java.io.File

/**
 * Desktop port of the Android RainPlayer / Ambient Soundscape Player.
 * A second, independent audio output looping the selected ambient sound (rain, fireplace, ocean, cafe),
 * mixing at the OS level alongside the main music (unaffected by the player's speed/effects),
 * exactly like the Android version's second ExoPlayer.
 *
 * Uses javax.sound Clip with LOOP_CONTINUOUSLY. Since Clip can't decode MP3 directly, the
 * asset is decoded once (via FFmpeg through a temp WAV) and cached on disk.
 */
class RainPlayer {

    private var clip: Clip? = null
    private var isEnabled = false
    private var volume: Float = 1.0f
    private var currentType: String = "rain"

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        applyVolume()
    }

    fun setAmbientType(type: String) {
        val safeType = if (type.isBlank()) "rain" else type.lowercase()
        if (currentType == safeType) return
        currentType = safeType
        if (isEnabled) {
            val wasPlaying = clip?.isRunning == true
            releaseClip()
            initPlayer()
            if (wasPlaying) clip?.start()
        }
    }

    private fun applyVolume() {
        val c = clip ?: return
        try {
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val ctrl = c.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val db = if (volume <= 0.0001f) ctrl.minimum
                else (20.0 * Math.log10(volume.toDouble())).toFloat().coerceIn(ctrl.minimum, ctrl.maximum)
                ctrl.value = db
            }
        } catch (_: Exception) {
        }
    }

    private fun initPlayer() {
        if (clip != null) return
        try {
            val wav = ensureDecodedWav(currentType)
            val stream = AudioSystem.getAudioInputStream(wav)
            val c = openClipForStream(stream)
            c.loop(Clip.LOOP_CONTINUOUSLY)
            clip = c
            applyVolume()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openClipForStream(stream: javax.sound.sampled.AudioInputStream): Clip {
        val fmt = stream.format
        val info = javax.sound.sampled.DataLine.Info(Clip::class.java, fmt)

        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        val deviceName = prefs.getAudioDevice()
        var clip: Clip? = null

        if (deviceName.isNotEmpty()) {
            val mixerInfos = AudioSystem.getMixerInfo()
            val targetInfo = mixerInfos.firstOrNull { it.name.trim() == deviceName }
            if (targetInfo != null) {
                try {
                    val m = AudioSystem.getMixer(targetInfo)
                    if (m.isLineSupported(info)) clip = m.getLine(info) as Clip
                } catch (_: Exception) {}
            }
        }

        val c = clip ?: AudioSystem.getClip()
        c.open(stream)
        return c
    }

    fun reloadDevice() {
        if (!isEnabled) return
        val wasPlaying = clip?.isRunning == true
        releaseClip()
        initPlayer()
        if (wasPlaying) clip?.start()
    }

    fun setEnabled(enabled: Boolean) {
        if (this.isEnabled != enabled) {
            this.isEnabled = enabled
            if (enabled) {
                initPlayer()
                clip?.start()
            } else {
                clip?.stop()
            }
        }
    }

    private fun releaseClip() {
        try {
            clip?.stop()
            clip?.close()
        } catch (_: Exception) {}
        clip = null
    }

    fun release() {
        releaseClip()
        isEnabled = false
    }

    companion object {
        private fun getResourceName(type: String): String {
            return when (type) {
                "fireplace" -> "fireplace.mp3"
                "ocean" -> "ocean.mp3"
                "cafe" -> "cafe.mp3"
                else -> "rain.mp3"
            }
        }

        /** Decode the bundled ambient MP3 to a cached WAV that javax.sound Clip can play. */
        private fun ensureDecodedWav(type: String): File {
            val resName = getResourceName(type)
            val wavName = resName.removeSuffix(".mp3") + ".wav"
            val wav = File(AppDirs.cacheDir, wavName)
            if (wav.exists() && wav.length() > 0) return wav

            val tmpMp3 = File.createTempFile("kittytune-ambient-$type", ".mp3")
            tmpMp3.deleteOnExit()
            val stream = RainPlayer::class.java.getResourceAsStream("/raw/$resName")
                ?: RainPlayer::class.java.getResourceAsStream("/raw/rain.mp3")!!
            stream.use { input ->
                tmpMp3.outputStream().use { input.copyTo(it) }
            }

            // Decode MP3 -> 16-bit PCM WAV using FFmpeg (JavaCV recorder).
            val grabber = org.bytedeco.javacv.FFmpegFrameGrabber(tmpMp3.absolutePath)
            grabber.sampleRate = 44100
            grabber.audioChannels = 2
            grabber.start()

            val recorder = org.bytedeco.javacv.FFmpegFrameRecorder(wav.absolutePath, 2).apply {
                format = "wav"
                sampleRate = 44100
                audioChannels = 2
                audioCodec = org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE
                start()
            }

            var frame = grabber.grabSamples()
            while (frame != null) {
                recorder.recordSamples(frame.sampleRate, frame.audioChannels, *frame.samples)
                frame = grabber.grabSamples()
            }

            recorder.stop(); recorder.release()
            grabber.stop(); grabber.release()
            return wav
        }
    }
}

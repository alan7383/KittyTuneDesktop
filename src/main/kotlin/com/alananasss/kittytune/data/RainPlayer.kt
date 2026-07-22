package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import java.io.File

/**
 * Desktop port of the Android RainPlayer.
 * A second, independent audio output looping the bundled rain.mp3, mixing at the OS level
 * alongside the main music (unaffected by the player's speed/effects), exactly like the
 * Android version's second ExoPlayer.
 *
 * Uses javax.sound Clip with LOOP_CONTINUOUSLY. Since Clip can't decode MP3 directly, the
 * asset is decoded once (via FFmpeg through a temp WAV) and cached on disk.
 */
class RainPlayer {

    private var clip: Clip? = null
    private var isEnabled = false
    private var volume: Float = 1.0f

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        applyVolume()
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
            val wav = ensureDecodedWav()
            val stream = AudioSystem.getAudioInputStream(wav)
            val c = AudioSystem.getClip()
            c.open(stream)
            c.loop(Clip.LOOP_CONTINUOUSLY)
            clip = c
            applyVolume()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
        if (enabled) {
            initPlayer()
            clip?.start()
        } else {
            clip?.stop()
        }
    }

    fun release() {
        clip?.stop()
        clip?.close()
        clip = null
    }

    companion object {
        /** Decode the bundled rain.mp3 to a cached WAV that javax.sound Clip can play. */
        private fun ensureDecodedWav(): File {
            val wav = File(AppDirs.cacheDir, "rain.wav")
            if (wav.exists() && wav.length() > 0) return wav

            val tmpMp3 = File.createTempFile("kittytune-rain", ".mp3")
            tmpMp3.deleteOnExit()
            RainPlayer::class.java.getResourceAsStream("/raw/rain.mp3")!!.use { input ->
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

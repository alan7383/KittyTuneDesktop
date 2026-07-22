package com.alananasss.kittytune.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine

/**
 * Low-latency PCM output through javax.sound.sampled (DirectSound/WASAPI on
 * Windows) — the desktop stand-in for AudioTrack/AudioSink.
 *
 * Accepts interleaved stereo float samples at 48 kHz and writes them as
 * 16-bit little-endian PCM.
 */
class AudioOutput : AutoCloseable {

    private val format = AudioFormat(
        AudioDecoder.SAMPLE_RATE.toFloat(),
        16,
        AudioDecoder.CHANNELS,
        true,  // signed
        false, // little-endian
    )

    private val line: SourceDataLine = run {
        val info = DataLine.Info(SourceDataLine::class.java, format)
        (AudioSystem.getLine(info) as SourceDataLine).apply {
            // ~120ms buffer: small enough for responsive seek/effect changes,
            // large enough to survive GC pauses.
            open(format, AudioDecoder.SAMPLE_RATE * AudioDecoder.CHANNELS * 2 * 120 / 1000)
            start()
        }
    }

    private var byteBuffer = ByteArray(0)

    /** Master volume 0..1 applied at the mixer line when supported. */
    fun setVolume(volume: Float) {
        try {
            val gain = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            // Map linear 0..1 to dB (clamped to the control's range).
            val db = if (volume <= 0.0001f) gain.minimum
            else (20.0 * kotlin.math.log10(volume.toDouble())).toFloat()
                .coerceIn(gain.minimum, gain.maximum)
            gain.value = db
        } catch (_: Exception) {
        }
    }

    /** Blocking write of interleaved float samples in [-1, 1]. */
    fun write(samples: FloatArray, count: Int = samples.size) {
        val bytes = count * 2
        if (byteBuffer.size < bytes) byteBuffer = ByteArray(bytes)
        var bi = 0
        for (i in 0 until count) {
            val s = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            byteBuffer[bi++] = (s and 0xFF).toByte()
            byteBuffer[bi++] = ((s shr 8) and 0xFF).toByte()
        }
        line.write(byteBuffer, 0, bytes)
    }

    /** Drop everything queued in the device buffer (used on seek). */
    fun flush() {
        line.flush()
    }

    fun pause() = line.stop()
    fun resume() = line.start()

    /** Samples actually played by the device — used to compute precise position. */
    val playedFrames: Long
        get() = line.longFramePosition

    override fun close() {
        try {
            line.stop()
            line.close()
        } catch (_: Exception) {
        }
    }
}

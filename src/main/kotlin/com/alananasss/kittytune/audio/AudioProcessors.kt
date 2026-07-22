package com.alananasss.kittytune.audio

import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Desktop port of ui/player/audio/AudioProcessors.kt — the exact same DSP algorithms
 * as the Android app, so playback effects sound sample-for-sample identical.
 *
 * All processors operate on interleaved 16-bit signed little-endian PCM shorts at the
 * stream's native rate/channels. Disabled processors are byte-for-byte pass-through.
 * Sink chain order: Fx (muffle -> bass) -> Reverb -> 8D -> Earrape.
 */

// --- 1. 8D AUDIO (Auto-Pan) ---
class EightDAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var time: Double = 0.0
    private var rotationSpeed: Double = 0.00001

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) time = 0.0
    }

    fun setSpeed(normalizedSpeed: Float) {
        rotationSpeed = (0.000002 + normalizedSpeed * 0.000038).coerceIn(0.000002, 0.00004)
    }

    override fun onFlush() {
        if (enabled) time = 0.0
    }

    override fun queueInput(input: ByteBuffer) {
        val remaining = input.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(input)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        while (input.hasRemaining()) {
            if (inputAudioFormat.channelCount == 2) {
                val left = input.getShort().toFloat()
                val right = input.getShort().toFloat()

                time += rotationSpeed
                val pan = sin(time)

                val leftVol = (1.0 - pan) / 2.0
                val rightVol = (1.0 + pan) / 2.0

                val newLeft = (left * leftVol).toInt().toShort()
                val newRight = (right * rightVol).toInt().toShort()

                buffer.putShort(newLeft)
                buffer.putShort(newRight)
            } else {
                buffer.putShort(input.getShort())
            }
        }
        buffer.flip()
    }
}

// --- 2. MULTI-FX (Simultaneous Bass Boost + Muffled) ---
class FxAudioProcessor : BaseAudioProcessor() {

    private var isMuffled = false
    private var isBassBoost = false
    private var bassBoostGain = 10f // dB
    private var muffledCutoff = 800f // Hz

    // MUFFLED filter (Low Pass) coefficients + shared DF-I history
    private var b0_m = 0f; private var b1_m = 0f; private var b2_m = 0f
    private var a1_m = 0f; private var a2_m = 0f
    private var x1_m = 0f; private var x2_m = 0f
    private var y1_m = 0f; private var y2_m = 0f

    // BASS BOOST filter (Low Shelf) coefficients + shared DF-I history
    private var b0_b = 0f; private var b1_b = 0f; private var b2_b = 0f
    private var a1_b = 0f; private var a2_b = 0f
    private var x1_b = 0f; private var x2_b = 0f
    private var y1_b = 0f; private var y2_b = 0f

    fun setEffects(muffled: Boolean, bassBoost: Boolean) {
        if (this.isMuffled != muffled || this.isBassBoost != bassBoost) {
            this.isMuffled = muffled
            this.isBassBoost = bassBoost
            resetStates()
            calculateCoefficients()
        }
    }

    fun setBassBoostGain(normalizedIntensity: Float) {
        val newGain = (4f + normalizedIntensity * 12f).coerceIn(4f, 16f)
        if (newGain != bassBoostGain) {
            bassBoostGain = newGain
            if (isBassBoost) {
                resetStates()
                calculateCoefficients()
            }
        }
    }

    fun setMuffledCutoff(normalizedIntensity: Float) {
        val newCutoff = (400f + normalizedIntensity * 1100f).coerceIn(400f, 1500f)
        if (newCutoff != muffledCutoff) {
            muffledCutoff = newCutoff
            if (isMuffled) {
                resetStates()
                calculateCoefficients()
            }
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        resetStates()
        calculateCoefficients()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetStates()
    }

    private fun resetStates() {
        x1_m = 0f; x2_m = 0f; y1_m = 0f; y2_m = 0f
        x1_b = 0f; x2_b = 0f; y1_b = 0f; y2_b = 0f
    }

    private fun calculateCoefficients() {
        val fs = inputAudioFormat.sampleRate.toFloat().coerceAtLeast(44100f)

        // muffled (low pass), Q = 0.707
        if (isMuffled) {
            val f0 = muffledCutoff
            val q = 0.707f
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val alpha = (sin(w0) / (2.0 * q)).toFloat()
            val cosW0 = cos(w0).toFloat()

            val a0 = 1f + alpha
            b0_m = ((1f - cosW0) / 2f) / a0
            b1_m = (1f - cosW0) / a0
            b2_m = ((1f - cosW0) / 2f) / a0
            a1_m = (-2f * cosW0) / a0
            a2_m = (1f - alpha) / a0
        }

        // bass boost (low shelf) at 100 Hz, S = 1
        if (isBassBoost) {
            val f0 = 100f
            val gain = bassBoostGain
            val S = 1f
            val A = Math.pow(10.0, gain / 40.0).toFloat()
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val sinW0 = sin(w0).toFloat()
            val cosW0 = cos(w0).toFloat()
            val alpha = sinW0 / 2f * Math.sqrt(((A + 1f / A) * (1f / S - 1f) + 2f).toDouble()).toFloat()
            val beta = 2f * Math.sqrt(A.toDouble()).toFloat() * alpha

            val a0 = (A + 1f) + (A - 1f) * cosW0 + beta
            b0_b = (A * ((A + 1f) - (A - 1f) * cosW0 + beta)) / a0
            b1_b = (2f * A * ((A - 1f) - (A + 1f) * cosW0)) / a0
            b2_b = (A * ((A + 1f) - (A - 1f) * cosW0 - beta)) / a0
            a1_b = (-2f * ((A - 1f) + (A + 1f) * cosW0)) / a0
            a2_b = ((A + 1f) + (A - 1f) * cosW0 - beta) / a0
        }
    }

    override fun queueInput(input: ByteBuffer) {
        val remaining = input.remaining()
        if (remaining == 0) return

        if (!isMuffled && !isBassBoost) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(input)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        while (input.hasRemaining()) {
            var x = input.getShort().toFloat()

            if (isMuffled) {
                val y = b0_m * x + b1_m * x1_m + b2_m * x2_m - a1_m * y1_m - a2_m * y2_m
                x2_m = x1_m; x1_m = x
                y2_m = y1_m; y1_m = y
                x = y
            }

            if (isBassBoost) {
                val y = b0_b * x + b1_b * x1_b + b2_b * x2_b - a1_b * y1_b - a2_b * y2_b
                x2_b = x1_b; x1_b = x
                y2_b = y1_b; y1_b = y
                x = y
            }

            val out = x.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            buffer.putShort(out)
        }
        buffer.flip()
    }
}

// --- 3. REVERB (Simple Delay Line) ---
class ReverbAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var buffer: ShortArray = ShortArray(0)
    private var cursor = 0
    private val delayMs = 150
    private var decay = 0.5f

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            if (!enabled) buffer = ShortArray(0)
        }
    }

    fun setDecay(normalizedIntensity: Float) {
        decay = (0.2f + normalizedIntensity * 0.6f).coerceIn(0.2f, 0.8f)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val bufferSize = (inputAudioFormat.sampleRate * (delayMs / 1000.0) * inputAudioFormat.channelCount).toInt()
        buffer = ShortArray(bufferSize)
        cursor = 0
        return inputAudioFormat
    }

    override fun onFlush() {
        cursor = 0
        if (buffer.isNotEmpty()) buffer.fill(0)
    }

    override fun queueInput(input: ByteBuffer) {
        val remaining = input.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(input)
            outputBuffer.flip()
            return
        }

        val outputBuffer = replaceOutputBuffer(remaining)

        while (input.hasRemaining()) {
            val inputSample = input.getShort()
            val delayedSample = buffer[cursor]

            val outputSample = (inputSample + delayedSample * decay).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            outputBuffer.putShort(outputSample)

            buffer[cursor] = outputSample

            cursor++
            if (cursor >= buffer.size) cursor = 0
        }
        outputBuffer.flip()
    }
}

// --- 4. EARRAPE (Hard Clipping Distortion) ---
class EarrapeAudioProcessor : BaseAudioProcessor() {
    private var enabled = false

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun queueInput(input: ByteBuffer) {
        val remaining = input.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(input)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        while (input.hasRemaining()) {
            val inputSample = input.getShort().toFloat()

            var s = inputSample * 40f
            s = s.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())

            s *= 20f
            s = s.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())

            val outputSample = (s * 0.25f).toInt().toShort()

            buffer.putShort(outputSample)
        }
        buffer.flip()
    }
}

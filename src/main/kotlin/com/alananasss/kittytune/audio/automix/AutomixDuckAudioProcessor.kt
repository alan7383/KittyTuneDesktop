package com.alananasss.kittytune.audio.automix

import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-shelf ducking processor used during AutoMix DJ-blend crossfades, so two full
 * basslines don't sum into mud during the overlap.
 *
 * The shelf filter's coefficients are fixed once at configure time and never change again.
 * Instead, the filter runs continuously and the duck amount is a linear blend
 * between the dry and fully-filtered signal, ramped smoothly per-sample toward whatever mix
 * the crossfade loop last requested.
 */
class AutomixDuckAudioProcessor(
    private val shelfFrequencyHz: Double = 150.0,
    private val maxDuckDb: Double = -10.0,
) : BaseAudioProcessor() {

    private var sampleRate = 44100
    private var channelCount = 2

    @Volatile
    private var targetMix: Float = 0f
    private var currentMix: Float = 0f

    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var x1L = 0.0
    private var x2L = 0.0
    private var y1L = 0.0
    private var y2L = 0.0
    private var x1R = 0.0
    private var x2R = 0.0
    private var y1R = 0.0
    private var y2R = 0.0

    companion object {
        private const val MIX_EASE_MS = 15.0
    }

    fun setMix(fraction: Float) {
        targetMix = fraction.coerceIn(0f, 1f)
    }

    fun resetGain() {
        targetMix = 0f
        currentMix = 0f
    }

    private fun computeShelfCoefficients() {
        val A = sqrt(10.0.pow(maxDuckDb / 20.0))
        val omega = 2.0 * PI * shelfFrequencyHz / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / 2.0 * sqrt(2.0) // S=1 shelf slope
        val sqrtA = sqrt(A)
        val aPlusOne = A + 1.0
        val aMinusOne = A - 1.0
        val twoSqrtAAlpha = 2.0 * sqrtA * alpha

        var rb0 = A * (aPlusOne - aMinusOne * cosOmega + twoSqrtAAlpha)
        var rb1 = 2.0 * A * (aMinusOne - aPlusOne * cosOmega)
        var rb2 = A * (aPlusOne - aMinusOne * cosOmega - twoSqrtAAlpha)
        val ra0 = aPlusOne + aMinusOne * cosOmega + twoSqrtAAlpha
        var ra1 = -2.0 * (aMinusOne + aPlusOne * cosOmega)
        var ra2 = aPlusOne + aMinusOne * cosOmega - twoSqrtAAlpha

        rb0 /= ra0; rb1 /= ra0; rb2 /= ra0; ra1 /= ra0; ra2 /= ra0
        b0 = rb0; b1 = rb1; b2 = rb2; a1 = ra1; a2 = ra2
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        computeShelfCoefficients()
        return inputAudioFormat
    }

    override fun onFlush() {
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
    }

    override fun queueInput(input: ByteBuffer) {
        val remaining = input.remaining()
        if (remaining == 0) return

        if (targetMix == 0f && currentMix == 0f) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(input)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)
        val easePerSample = (1000.0 / (MIX_EASE_MS * sampleRate)).toFloat().coerceIn(0f, 1f)

        if (channelCount == 2) {
            val sampleCount = remaining / 4
            for (i in 0 until sampleCount) {
                currentMix += (targetMix - currentMix) * easePerSample
                val inL = input.getShort().toDouble() / 32768.0
                val inR = input.getShort().toDouble() / 32768.0

                val filteredL = b0 * inL + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
                x2L = x1L; x1L = inL; y2L = y1L; y1L = filteredL

                val filteredR = b0 * inR + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
                x2R = x1R; x1R = inR; y2R = y1R; y1R = filteredR

                val outL = inL + (filteredL - inL) * currentMix
                val outR = inR + (filteredR - inR) * currentMix

                buffer.putShort((outL * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                buffer.putShort((outR * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        } else {
            val sampleCount = remaining / 2
            for (i in 0 until sampleCount) {
                currentMix += (targetMix - currentMix) * easePerSample
                val inSample = input.getShort().toDouble() / 32768.0
                val filtered = b0 * inSample + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
                x2L = x1L; x1L = inSample; y2L = y1L; y1L = filtered
                val output = inSample + (filtered - inSample) * currentMix
                buffer.putShort((output * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        }
        buffer.flip()
    }
}

package com.alananasss.kittytune.audio

import com.alananasss.kittytune.audio.AudioDecoder.Companion.SAMPLE_RATE
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * DSP building blocks for the effect chain. Interleaved stereo Float PCM at 48 kHz.
 * Exact algorithms are aligned with the Android app's AudioProcessors.kt once its
 * spec lands; these classes provide the standard implementations (RBJ biquads,
 * Schroeder reverb, auto-pan) they are known to build on.
 */

/** RBJ biquad filter, one instance per channel. */
class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0
    private var y1 = 0.0; private var y2 = 0.0

    fun lowPass(freq: Double, q: Double) {
        val w0 = 2.0 * PI * freq / SAMPLE_RATE
        val alpha = sin(w0) / (2.0 * q)
        val cw = cos(w0)
        val a0 = 1.0 + alpha
        b0 = ((1.0 - cw) / 2.0) / a0
        b1 = (1.0 - cw) / a0
        b2 = ((1.0 - cw) / 2.0) / a0
        a1 = (-2.0 * cw) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun highPass(freq: Double, q: Double) {
        val w0 = 2.0 * PI * freq / SAMPLE_RATE
        val alpha = sin(w0) / (2.0 * q)
        val cw = cos(w0)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + cw) / 2.0) / a0
        b1 = -(1.0 + cw) / a0
        b2 = ((1.0 + cw) / 2.0) / a0
        a1 = (-2.0 * cw) / a0
        a2 = (1.0 - alpha) / a0
    }

    /** Peaking EQ; gainDb boost/cut at freq. */
    fun peaking(freq: Double, q: Double, gainDb: Double) {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * PI * freq / SAMPLE_RATE
        val alpha = sin(w0) / (2.0 * q)
        val cw = cos(w0)
        val a0 = 1.0 + alpha / a
        b0 = (1.0 + alpha * a) / a0
        b1 = (-2.0 * cw) / a0
        b2 = (1.0 - alpha * a) / a0
        a1 = (-2.0 * cw) / a0
        a2 = (1.0 - alpha / a) / a0
    }

    /** Low-shelf; gainDb below freq. */
    fun lowShelf(freq: Double, slope: Double, gainDb: Double) {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * PI * freq / SAMPLE_RATE
        val cw = cos(w0)
        val sw = sin(w0)
        val alpha = sw / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1.0) + (a - 1.0) * cw + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) - (a - 1.0) * cw + twoSqrtAAlpha)) / a0
        b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cw)) / a0
        b2 = (a * ((a + 1.0) - (a - 1.0) * cw - twoSqrtAAlpha)) / a0
        a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cw)) / a0
        a2 = ((a + 1.0) + (a - 1.0) * cw - twoSqrtAAlpha) / a0
    }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }

    fun reset() {
        x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0
    }
}

/** Simple comb filter with damping — Schroeder/Freeverb style. */
class CombFilter(sizeSamples: Int) {
    private val buffer = FloatArray(sizeSamples)
    private var index = 0
    private var filterStore = 0f
    var feedback = 0.84f
    var damp = 0.2f

    fun process(input: Float): Float {
        val output = buffer[index]
        filterStore = output * (1f - damp) + filterStore * damp
        buffer[index] = input + filterStore * feedback
        index = (index + 1) % buffer.size
        return output
    }

    fun mute() {
        buffer.fill(0f)
        filterStore = 0f
    }
}

/** All-pass filter for reverb diffusion. */
class AllPassFilter(sizeSamples: Int) {
    private val buffer = FloatArray(sizeSamples)
    private var index = 0
    var feedback = 0.5f

    fun process(input: Float): Float {
        val bufOut = buffer[index]
        val output = -input + bufOut
        buffer[index] = input + bufOut * feedback
        index = (index + 1) % buffer.size
        return output
    }

    fun mute() = buffer.fill(0f)
}

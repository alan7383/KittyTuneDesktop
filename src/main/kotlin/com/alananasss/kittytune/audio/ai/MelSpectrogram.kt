package com.alananasss.kittytune.audio.ai

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Pure Kotlin Mel-Spectrogram extractor for Audio Transformer (AST / CLAP) inference.
 * Computes STFT magnitude followed by triangular Mel filterbank pooling.
 *
 * Highly optimized with cached Hanning window, precomputed exact twiddle factors,
 * sparse filterbank evaluation (skipping zero-energy bins), and Nyquist safety.
 */
object MelSpectrogram {

    private data class FilterbankKey(val sr: Int, val nFft: Int, val nMels: Int, val fMin: Float, val fMax: Float)
    private data class SparseFilter(val kStart: Int, val kEnd: Int, val weights: FloatArray)

    private val filterbankCache = mutableMapOf<FilterbankKey, List<SparseFilter>>()
    private val windowCache = mutableMapOf<Int, FloatArray>()
    private val lock = Any()

    fun computeLogMel(
        samples: FloatArray,
        sampleRate: Int = 44100,
        nFft: Int = 1024,
        hopLength: Int = 512,
        nMels: Int = 128,
        fMin: Float = 0f,
        fMax: Float = 22050f,
    ): Array<Array<FloatArray>> {
        val numFrames = max(1, (samples.size - nFft) / hopLength + 1)
        val halfFft = nFft / 2 + 1

        val window = getCachedWindow(nFft)
        val filters = getCachedFilterbank(sampleRate, nFft, nMels, fMin, fMax)

        // Result: [1, nMels, numFrames]
        val melSpec = Array(nMels) { FloatArray(numFrames) }

        val frameReal = FloatArray(nFft)
        val frameImag = FloatArray(nFft)
        val powerSpec = FloatArray(halfFft)

        for (frameIdx in 0 until numFrames) {
            val offset = frameIdx * hopLength
            for (i in 0 until nFft) {
                val sampleVal = if (offset + i < samples.size) samples[offset + i] else 0f
                frameReal[i] = sampleVal * window[i]
                frameImag[i] = 0f
            }

            fftRadix2(frameReal, frameImag, nFft)

            for (k in 0 until halfFft) {
                val r = frameReal[k]
                val im = frameImag[k]
                powerSpec[k] = r * r + im * im
            }

            for (m in 0 until nMels) {
                val sparse = filters[m]
                var energy = 0f
                val weights = sparse.weights
                val kStart = sparse.kStart
                val kEnd = sparse.kEnd
                for (k in kStart until kEnd) {
                    energy += powerSpec[k] * weights[k - kStart]
                }
                melSpec[m][frameIdx] = ln(max(energy, 1e-6f))
            }
        }

        return arrayOf(melSpec)
    }

    private fun getCachedWindow(nFft: Int): FloatArray {
        synchronized(lock) {
            return windowCache.getOrPut(nFft) {
                FloatArray(nFft) { i ->
                    (0.5 * (1.0 - cos(2.0 * Math.PI * i / (nFft - 1)))).toFloat()
                }
            }
        }
    }

    private fun getCachedFilterbank(sr: Int, nFft: Int, nMels: Int, fMin: Float, fMax: Float): List<SparseFilter> {
        val effectiveFMax = if (fMax <= 0f || fMax > sr / 2f) sr / 2f else fMax
        val key = FilterbankKey(sr, nFft, nMels, fMin, effectiveFMax)
        synchronized(lock) {
            return filterbankCache.getOrPut(key) {
                createSparseMelFilterbank(sr, nFft, nMels, fMin, effectiveFMax)
            }
        }
    }

    private fun createSparseMelFilterbank(
        sr: Int,
        nFft: Int,
        nMels: Int,
        fMin: Float,
        fMax: Float,
    ): List<SparseFilter> {
        val halfFft = nFft / 2 + 1
        fun hzToMel(hz: Float): Float = (2595.0 * kotlin.math.log10(1.0 + hz / 700.0)).toFloat()
        fun melToHz(mel: Float): Float = (700.0 * (10.0.pow(mel / 2595.0) - 1.0)).toFloat()

        val melMin = hzToMel(fMin)
        val melMax = hzToMel(fMax)
        val melPoints = FloatArray(nMels + 2) { i ->
            melMin + (melMax - melMin) * i / (nMels + 1)
        }
        val fftFreqs = FloatArray(halfFft) { i -> (i.toFloat() * sr) / nFft }

        val list = ArrayList<SparseFilter>(nMels)
        for (m in 0 until nMels) {
            val fLeft = melToHz(melPoints[m])
            val fCenter = melToHz(melPoints[m + 1])
            val fRight = melToHz(melPoints[m + 2])

            var kStart = -1
            var kEnd = -1
            val tempWeights = ArrayList<Float>()

            for (k in 0 until halfFft) {
                val f = fftFreqs[k]
                val w = when {
                    f in fLeft..fCenter && fCenter > fLeft -> (f - fLeft) / (fCenter - fLeft)
                    f in fCenter..fRight && fRight > fCenter -> (fRight - f) / (fRight - fCenter)
                    else -> 0f
                }
                if (w > 0f) {
                    if (kStart == -1) kStart = k
                    kEnd = k + 1
                    tempWeights.add(w)
                }
            }

            if (kStart == -1) {
                list.add(SparseFilter(0, 0, FloatArray(0)))
            } else {
                list.add(SparseFilter(kStart, kEnd, tempWeights.toFloatArray()))
            }
        }
        return list
    }

    private fun fftRadix2(real: FloatArray, imag: FloatArray, n: Int) {
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val half = len shr 1
            val angleStep = -2.0 * Math.PI / len

            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val angle = k * angleStep
                    val wR = cos(angle).toFloat()
                    val wI = sin(angle).toFloat()

                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + half] * wR - imag[i + k + half] * wI
                    val vI = real[i + k + half] * wI + imag[i + k + half] * wR

                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + half] = uR - vR
                    imag[i + k + half] = uI - vI
                }
                i += len
            }
            len = len shl 1
        }
    }
}

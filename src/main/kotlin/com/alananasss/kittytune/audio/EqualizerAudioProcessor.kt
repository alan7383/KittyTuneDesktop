package com.alananasss.kittytune.audio

import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Desktop port of the Android 16-band graphic equalizer.
 *
 * Sixteen peaking biquads in transposed direct form II, one state array per channel, with the
 * same band centres, Q and soft limiter as the mobile processor. Applying the same coefficients
 * on every OS means an AutoEq profile sounds the same whether the headphones are wired or on
 * Bluetooth and whichever desktop platform is playing.
 */
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        const val NUM_BANDS = 16
        val BAND_FREQUENCIES = floatArrayOf(
            32f, 64f, 125f, 200f, 315f, 500f, 800f, 1250f,
            2000f, 3150f, 5000f, 8000f, 10000f, 12500f, 16000f, 20000f,
        )
        private const val DEFAULT_Q = 1.8f
        private const val SOFT_LIMIT_THRESHOLD = 28000f
        private const val MAX_PCM_VAL = 32767f
        private const val DENORMAL_THRESHOLD = 1e-15f
    }

    private class FilterConfig(
        val isEnabled: Boolean,
        val preampLinear: Float,
        val hasPreamp: Boolean,
        val hasAnyActiveBand: Boolean,
        val isBandActive: BooleanArray,
        val b0: FloatArray,
        val b1: FloatArray,
        val b2: FloatArray,
        val a1: FloatArray,
        val a2: FloatArray,
    )

    @Volatile
    private var filterConfig = FilterConfig(
        isEnabled = false,
        preampLinear = 1f,
        hasPreamp = false,
        hasAnyActiveBand = false,
        isBandActive = BooleanArray(NUM_BANDS),
        b0 = FloatArray(NUM_BANDS),
        b1 = FloatArray(NUM_BANDS),
        b2 = FloatArray(NUM_BANDS),
        a1 = FloatArray(NUM_BANDS),
        a2 = FloatArray(NUM_BANDS),
    )

    private var cachedEnabled = false
    private var cachedPreampDb = 0f
    private val cachedGainsDb = FloatArray(NUM_BANDS)

    private var channelCount = 2
    private var s1 = Array(2) { FloatArray(NUM_BANDS) }
    private var s2 = Array(2) { FloatArray(NUM_BANDS) }

    fun setParameters(enabled: Boolean, preampDb: Float, gainsDb: FloatArray) {
        val clampedPreamp = preampDb.coerceIn(-12f, 12f)
        val clampedGains = FloatArray(NUM_BANDS) { index ->
            if (index < gainsDb.size) gainsDb[index].coerceIn(-12f, 12f) else 0f
        }

        synchronized(this) {
            val enabledChanged = cachedEnabled != enabled
            cachedEnabled = enabled
            cachedPreampDb = clampedPreamp
            System.arraycopy(clampedGains, 0, cachedGainsDb, 0, NUM_BANDS)
            rebuildFilterConfig()
            if (!enabled && enabledChanged) resetStates()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        synchronized(this) {
            channelCount = channels
            s1 = Array(channels) { FloatArray(NUM_BANDS) }
            s2 = Array(channels) { FloatArray(NUM_BANDS) }
            rebuildFilterConfig()
        }
        return inputAudioFormat
    }

    override fun onFlush() = resetStates()
    override fun onReset() = resetStates()

    private fun resetStates() {
        for (channel in 0 until channelCount) {
            if (channel < s1.size) s1[channel].fill(0f)
            if (channel < s2.size) s2[channel].fill(0f)
        }
    }

    private fun rebuildFilterConfig() {
        val sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44100
        val fs = sampleRate.toFloat()
        val nyquist = fs * 0.5f

        val b0 = FloatArray(NUM_BANDS)
        val b1 = FloatArray(NUM_BANDS)
        val b2 = FloatArray(NUM_BANDS)
        val a1 = FloatArray(NUM_BANDS)
        val a2 = FloatArray(NUM_BANDS)
        val isBandActive = BooleanArray(NUM_BANDS)
        var anyActive = false

        for (index in 0 until NUM_BANDS) {
            val gain = cachedGainsDb[index]
            if (abs(gain) < 0.05f) continue
            val f0 = BAND_FREQUENCIES[index]
            if (f0 >= nyquist - 100f || f0 <= 0f) continue

            isBandActive[index] = true
            anyActive = true

            val a = 10.0.pow(gain.toDouble() / 40.0)
            val w0 = 2.0 * PI * f0.toDouble() / fs.toDouble()
            val sinW0 = sin(w0)
            val cosW0 = cos(w0)
            val alpha = sinW0 / (2.0 * DEFAULT_Q.toDouble())
            val a0 = 1.0 + alpha / a

            b0[index] = ((1.0 + alpha * a) / a0).toFloat()
            b1[index] = ((-2.0 * cosW0) / a0).toFloat()
            b2[index] = ((1.0 - alpha * a) / a0).toFloat()
            a1[index] = ((-2.0 * cosW0) / a0).toFloat()
            a2[index] = ((1.0 - alpha / a) / a0).toFloat()
        }

        filterConfig = FilterConfig(
            isEnabled = cachedEnabled,
            preampLinear = 10.0.pow(cachedPreampDb.toDouble() / 20.0).toFloat(),
            hasPreamp = abs(cachedPreampDb) > 0.05f,
            hasAnyActiveBand = anyActive,
            isBandActive = isBandActive,
            b0 = b0,
            b1 = b1,
            b2 = b2,
            a1 = a1,
            a2 = a2,
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val config = filterConfig

        if (!config.isEnabled || (!config.hasAnyActiveBand && !config.hasPreamp)) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)
        val channels = channelCount.coerceAtLeast(1)
        val localS1 = s1
        val localS2 = s2
        var channelIndex = 0

        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.getShort().toFloat()
            val channel = channelIndex
            var x = sample * config.preampLinear

            if (config.hasAnyActiveBand && channel < localS1.size) {
                val channelS1 = localS1[channel]
                val channelS2 = localS2[channel]
                for (band in 0 until NUM_BANDS) {
                    if (!config.isBandActive[band]) continue
                    val s1Value = channelS1[band]
                    val s2Value = channelS2[band]
                    val y = config.b0[band] * x + s1Value
                    var nextS1 = config.b1[band] * x - config.a1[band] * y + s2Value
                    var nextS2 = config.b2[band] * x - config.a2[band] * y
                    if (abs(nextS1) < DENORMAL_THRESHOLD) nextS1 = 0f
                    if (abs(nextS2) < DENORMAL_THRESHOLD) nextS2 = 0f
                    channelS1[band] = nextS1
                    channelS2[band] = nextS2
                    x = y
                }
            }

            buffer.putShort(softLimit(x))
            channelIndex = (channelIndex + 1) % channels
        }
        buffer.flip()
    }

    private fun softLimit(sample: Float): Short {
        val absSample = abs(sample)
        if (absSample <= SOFT_LIMIT_THRESHOLD) return sample.toInt().toShort()
        val sign = if (sample >= 0f) 1f else -1f
        val excess = (absSample - SOFT_LIMIT_THRESHOLD) / (MAX_PCM_VAL - SOFT_LIMIT_THRESHOLD)
        val compressed = SOFT_LIMIT_THRESHOLD +
            (MAX_PCM_VAL - SOFT_LIMIT_THRESHOLD) * tanh(excess.toDouble()).toFloat()
        return (sign * compressed).coerceIn(-32768f, 32767f).toInt().toShort()
    }
}

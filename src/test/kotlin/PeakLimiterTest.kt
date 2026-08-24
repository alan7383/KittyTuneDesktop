package com.alananasss.kittytune

import com.alananasss.kittytune.audio.PeakLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/** Look-ahead in frames, mirroring the limiter's own 2.5 ms at this rate. */
private const val SAMPLE_RATE = 44100
private const val CHANNELS = 2
private val LOOK = (SAMPLE_RATE * 0.0025f).toInt()

/**
 * What the limiter must do (catch overshoot above full scale) and, just as importantly, what it
 * must not do (touch material that already fits under the ceiling).
 */
class PeakLimiterTest {

    private fun tone(frames: Int, amplitude: Float): FloatArray {
        val buf = FloatArray(frames * CHANNELS)
        for (f in 0 until frames) {
            val v = amplitude * sin(2.0 * Math.PI * 440.0 * f / SAMPLE_RATE).toFloat()
            buf[f * CHANNELS] = v
            buf[f * CHANNELS + 1] = v
        }
        return buf
    }

    private fun maxAbs(buf: FloatArray, fromFrame: Int = 0): Float {
        var m = 0f
        for (i in fromFrame * CHANNELS until buf.size) {
            val a = abs(buf[i])
            if (a > m) m = a
        }
        return m
    }

    @Test
    fun `material under the ceiling passes through bit for bit`() {
        val frames = 20_000
        val input = tone(frames, 0.9f)
        val work = input.copyOf()
        val limiter = PeakLimiter(SAMPLE_RATE, CHANNELS)

        limiter.process(work, work.size)

        // Output is the input delayed by the look-ahead, with the gain still at unity.
        for (f in LOOK until frames) {
            val expected = input[(f - LOOK) * CHANNELS]
            assertEquals("frame $f", expected, work[f * CHANNELS], 0f)
        }
    }

    @Test
    fun `overshoot above full scale is brought under the ceiling`() {
        val frames = 20_000
        val work = tone(frames, 1.2f)
        val limiter = PeakLimiter(SAMPLE_RATE, CHANNELS)

        limiter.process(work, work.size)

        // Skip the first look-ahead window: it is the ramp the delay line is still filling.
        val peak = maxAbs(work, fromFrame = LOOK * 4)
        assertTrue("peak $peak should sit at the ceiling", peak <= PeakLimiter.CEILING_TRANSPARENT + 1e-3f)
        assertTrue("peak $peak should not be crushed far below it", peak > PeakLimiter.CEILING_TRANSPARENT - 0.05f)
    }

    @Test
    fun `the safe ceiling does pull down a hot master`() {
        val frames = 20_000
        val work = tone(frames, 0.9f)
        val limiter = PeakLimiter(SAMPLE_RATE, CHANNELS)
        limiter.setCeiling(PeakLimiter.CEILING_SAFE)

        limiter.process(work, work.size)

        val peak = maxAbs(work, fromFrame = LOOK * 4)
        assertTrue("peak $peak", peak <= PeakLimiter.CEILING_SAFE + 1e-3f)
        assertTrue("a 0.9 signal must actually be reduced at this ceiling", peak < 0.9f)
    }

    @Test
    fun `nothing ever leaves the limiter outside full scale`() {
        val work = tone(20_000, 4f)
        val limiter = PeakLimiter(SAMPLE_RATE, CHANNELS)
        limiter.process(work, work.size)
        assertTrue(maxAbs(work) <= 1f)
    }
}

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
    fun `sustained overshoot is ridden down and lands inside full scale`() {
        val frames = 20_000
        val work = tone(frames, 1.5f)
        val limiter = PeakLimiter(SAMPLE_RATE, CHANNELS)

        limiter.process(work, work.size)

        // Skip the first look-ahead window: it is the ramp the delay line is still filling.
        val peak = maxAbs(work, fromFrame = LOOK * 4)
        assertTrue("peak $peak must fit in full scale", peak <= 1f)
        assertTrue("peak $peak should not be crushed far below it", peak > 0.95f)
    }

    /**
     * The audible regression this protects against: at -0.5 dBFS the limiter rode gain on every
     * kick of a hot master and pumped the whole mix. A small overshoot must now cost nothing but
     * the rounding of its own top: no gain change on the samples around it.
     */
    @Test
    fun `a small overshoot is rounded off without touching its neighbours`() {
        val frames = 20_000
        val input = tone(frames, 0.5f)
        // One inter-sample peak at +1 dBFS, as an AAC decoder leaves them on a loud kick.
        val spike = 10_000
        input[spike * CHANNELS] = 1.1f
        input[spike * CHANNELS + 1] = 1.1f
        val work = input.copyOf()
        val limiter = PeakLimiter(SAMPLE_RATE, CHANNELS)

        limiter.process(work, work.size)

        for (f in LOOK until frames) {
            val src = f - LOOK
            val expected = if (src == spike) PeakLimiter.softKnee(1.1f) else input[src * CHANNELS]
            assertEquals("frame $f", expected, work[f * CHANNELS], 0f)
        }
        val top = work[(spike + LOOK) * CHANNELS]
        assertTrue("the spike $top must be inside full scale", top < 1f && top > 0.95f)
    }

    @Test
    fun `the soft knee is the identity below it and never passes full scale`() {
        assertEquals(0.5f, PeakLimiter.softKnee(0.5f), 0f)
        assertEquals(-PeakLimiter.SOFT_KNEE, PeakLimiter.softKnee(-PeakLimiter.SOFT_KNEE), 0f)
        var last = PeakLimiter.SOFT_KNEE
        var x = PeakLimiter.SOFT_KNEE + 0.01f
        while (x < 4f) {
            val y = PeakLimiter.softKnee(x)
            assertTrue("monotonic at $x", y >= last)
            assertTrue("inside full scale at $x", y <= 1f)
            assertEquals(-y, PeakLimiter.softKnee(-x), 0f)
            last = y
            x += 0.01f
        }
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

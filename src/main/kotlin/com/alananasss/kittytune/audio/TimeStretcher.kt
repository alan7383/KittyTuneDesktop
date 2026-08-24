package com.alananasss.kittytune.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Pitch/tempo stage — the desktop equivalent of ExoPlayer's PlaybackParameters(speed, pitch),
 * which the Android app drives via the Sonic time-stretcher.
 *
 * Two stages, composed the way Sonic composes them:
 *   1. WSOLA time-stretch by speed/pitch — changes duration, leaves pitch alone.
 *   2. Fractional resample by pitch — changes duration and pitch together (tape speed).
 *
 * With isPitchEnabled (pitch == speed) stage 1 is a no-op and stage 2 does the classic
 * nightcore/daycore resample; with pitch == 1 it is the other way round; at 1x neither runs.
 *
 * Continuity across calls is the whole point. The resampler's fractional read position and
 * the WSOLA overlap tail have to survive from one queue() to the next: restarting either
 * per block drops a sample at every decoded frame boundary, and 40-odd of those per second
 * is a steady buzz at the block rate, not anything you would recognise as a pitch effect.
 *
 * Works per-channel on interleaved 16-bit PCM, on primitive ring buffers — no per-sample
 * boxing on the audio thread. Pull-based: feed input, drain output.
 */
class TimeStretcher(sampleRate: Int, private val channels: Int) {

    private var speed = 1f
    private var pitch = 1f

    // WSOLA parameters (tuned for music at 44.1k; scale with rate)
    private val frameSize = (sampleRate * 0.030).toInt().coerceAtLeast(256)   // 30 ms analysis frame
    private val overlap = frameSize / 2                                        // 50% overlap
    private val seekWindow = (sampleRate * 0.015).toInt().coerceAtLeast(64)    // ±15 ms search

    /** Rising half-Hann crossfade ramp, [overlap] long: 0 at the start, 1 at the end. */
    private val ramp = FloatArray(overlap) {
        (0.5 - 0.5 * cos(PI * it / (overlap - 1).coerceAtLeast(1))).toFloat()
    }

    private val input = FloatRing(channels, 1 shl 14)
    private val mid = FloatRing(channels, 1 shl 14)
    private val output = ShortRing(1 shl 15)

    /** WSOLA carry-over: the frame whose second half still has to be crossfaded out. */
    private var tail: Array<FloatArray>? = null

    /**
     * Stage 2 read position, in frames relative to the front of [mid], as Q32.32 fixed point.
     * Fixed point rather than a double so the phase advances by exactly the same amount every
     * step no matter how long the track runs — an accumulator that drifts would slowly
     * detune the output and make the result depend on where the decoder's blocks happen to fall.
     */
    private var phase = 0L

    /** Q32.32 accumulator for stage 1's analysis hop, so a fractional hop averages out exactly. */
    private var hopPhase = 0L

    private val kernel = PolyphaseSinc(KERNEL_TAPS, KERNEL_PHASES)
    private val coefficients = FloatArray(KERNEL_TAPS)

    private val frame = FloatArray(channels)

    fun setParameters(speed: Float, pitch: Float) {
        this.speed = speed.coerceIn(0.1f, 4f)
        this.pitch = pitch.coerceIn(0.1f, 4f)
    }

    fun flush() {
        input.clear()
        mid.clear()
        output.clear()
        tail = null
        phase = 0L
        hopPhase = 0L
    }

    /** Feed interleaved 16-bit PCM (as shorts). */
    fun queue(samples: ShortArray, count: Int) {
        val frames = count / channels
        input.ensure(frames)
        var i = 0
        for (f in 0 until frames) {
            for (ch in 0 until channels) frame[ch] = samples[i + ch].toFloat()
            input.push(frame)
            i += channels
        }
        process()
    }

    /** Drain up to [max] interleaved shorts; returns number written. */
    fun drain(out: ShortArray, max: Int): Int = output.drainTo(out, max)

    fun available(): Int = output.size

    private fun process() {
        val stretch = speed / pitch
        val stretchIsUnity = abs(stretch - 1f) < 1e-3f
        val pitchIsUnity = abs(pitch - 1f) < 1e-3f

        if (stretchIsUnity && pitchIsUnity) {
            passInputToOutput()
            return
        }

        if (stretchIsUnity) passInputToMid() else timeStretch(stretch)
        if (pitchIsUnity) {
            passMidToOutput()
        } else {
            // Reading the input faster than we play it folds everything above Nyquist/ratio
            // back down as aliasing, so the kernel doubles as the anti-alias filter.
            kernel.ensure(if (pitch > 1f) GUARD / pitch else GUARD)
            resample((pitch.toDouble() * ONE).toLong())
        }
    }

    /** 1x playback: neither stage has anything to do, so hand the input straight over. */
    private fun passInputToOutput() {
        if (mid.size > 0) {
            mid.clear()
            phase = 0L
        }
        for (f in 0 until input.size) {
            for (ch in 0 until channels) output.push(toPcm(input.get(ch, f)))
        }
        input.clear()
    }

    private fun passInputToMid() {
        val n = input.size
        mid.ensure(n)
        for (f in 0 until n) {
            for (ch in 0 until channels) frame[ch] = input.get(ch, f)
            mid.push(frame)
        }
        input.clear()
    }

    private fun passMidToOutput() {
        if (phase >= ONE) {
            mid.drop((phase ushr 32).toInt())
            phase = 0L
        }
        for (f in 0 until mid.size) {
            for (ch in 0 until channels) output.push(toPcm(mid.get(ch, f)))
        }
        mid.clear()
    }

    /**
     * Stage 2: reads [mid] at [step] frames per output frame (Q32.32) through a 16-tap windowed
     * sinc, its phase taken from [kernel]. [phase] carries the fractional position over to the
     * next call, and only the frames behind it (bar the filter's history) are released — so
     * there is no seam where one block ends and the next begins.
     */
    private fun resample(step: Long) {
        val taps = KERNEL_TAPS
        val coef = coefficients
        while (true) {
            val i = (phase ushr 32).toInt()
            if (i + AHEAD >= mid.size) break
            kernel.coefficientsFor((phase and FRACTION_MASK), coef)
            val base = i - HISTORY
            if (base >= 0) {
                for (ch in 0 until channels) {
                    var acc = 0f
                    for (t in 0 until taps) acc += coef[t] * mid.get(ch, base + t)
                    output.push(toPcm(acc))
                }
            } else {
                // Only for the first few frames of a stream or after a seek: hold the edge.
                for (ch in 0 until channels) {
                    var acc = 0f
                    for (t in 0 until taps) {
                        val idx = base + t
                        acc += coef[t] * mid.get(ch, if (idx < 0) 0 else idx)
                    }
                    output.push(toPcm(acc))
                }
            }
            phase += step
        }
        val drop = ((phase ushr 32).toInt() - HISTORY).coerceAtLeast(0)
        if (drop > 0) {
            mid.drop(drop)
            phase -= drop.toLong() shl 32
        }
    }

    /**
     * Stage 1: WSOLA. Emits [overlap] frames per iteration while consuming `overlap * ratio`,
     * crossfading the tail of the previous frame into the best-correlated position in the new
     * one so the waveform stays in phase. [tail] persists across calls.
     */
    private fun timeStretch(ratio: Float) {
        val need = frameSize + seekWindow
        val hopStep = (overlap.toDouble() * ratio * ONE).toLong()
        while (input.size >= need) {
            val cur = tail
            if (cur == null) {
                tail = Array(channels) { ch -> FloatArray(frameSize) { input.get(ch, it) } }
            } else {
                var bestOffset = 0
                var bestCorr = Float.NEGATIVE_INFINITY
                for (off in 0 until seekWindow) {
                    var corr = 0f
                    var k = 0
                    while (k < overlap) {
                        for (ch in 0 until channels) corr += cur[ch][overlap + k] * input.get(ch, off + k)
                        k += 4 // subsample the correlation for speed
                    }
                    if (corr > bestCorr) {
                        bestCorr = corr
                        bestOffset = off
                    }
                }

                mid.ensure(overlap)
                for (k in 0 until overlap) {
                    val w = ramp[k]
                    for (ch in 0 until channels) {
                        frame[ch] = cur[ch][overlap + k] * (1f - w) + input.get(ch, bestOffset + k) * w
                    }
                    mid.push(frame)
                }
                // The shifted frame becomes the next tail; reuse the array we just finished with.
                for (ch in 0 until channels) {
                    for (k in 0 until frameSize) cur[ch][k] = input.get(ch, bestOffset + k)
                }
            }
            hopPhase += hopStep
            var hop = (hopPhase ushr 32).toInt()
            if (hop < 1) hop = 1
            hopPhase -= hop.toLong() shl 32
            if (hopPhase < 0) hopPhase = 0
            input.drop(hop)
        }
    }

    private fun toPcm(v: Float): Short = v.roundToInt().coerceIn(-32768, 32767).toShort()

    /**
     * Polyphase bank of windowed-sinc kernels, one row per phase, built for a given cutoff.
     *
     * A polynomial interpolator is cheaper but its error climbs steeply with frequency — the
     * Catmull-Rom spline this replaced sat only ~12 dB below the signal above 10 kHz, which on
     * bright material is audible as roughness. A 16-tap Blackman-windowed sinc puts it far below
     * the 16-bit floor, and choosing the cutoff by the resampling ratio makes it the anti-alias
     * filter at the same time.
     */
    private class PolyphaseSinc(private val taps: Int, private val phases: Int) {

        // One row per phase, plus a duplicate final row so a phase can be interpolated
        // between its two neighbours without a bounds check.
        private val table = FloatArray((phases + 1) * taps)
        private var cutoff = -1f

        fun ensure(newCutoff: Float) {
            if (newCutoff == cutoff) return
            cutoff = newCutoff
            build(newCutoff)
        }

        /** Fills [out] with the [taps] coefficients for a Q32 fractional position. */
        fun coefficientsFor(fraction: Long, out: FloatArray) {
            val scaled = fraction * phases            // Q32 * phases
            val row = (scaled ushr 32).toInt()
            val blend = (scaled and FRACTION_MASK).toFloat() / ONE_F
            val a = row * taps
            val b = a + taps
            for (t in 0 until taps) {
                val lo = table[a + t]
                out[t] = lo + (table[b + t] - lo) * blend
            }
        }

        private fun build(cutoff: Float) {
            val half = taps / 2
            for (p in 0..phases) {
                val frac = p.toFloat() / phases
                val row = p * taps
                var sum = 0f
                for (t in 0 until taps) {
                    // Tap t reads the input sample at offset (t - half + 1); the kernel is
                    // centred `frac` of a sample later than that.
                    val x = (t - half + 1) - frac
                    val u = (x + half) / taps
                    val w = (0.42 - 0.5 * cos(2.0 * PI * u) + 0.08 * cos(4.0 * PI * u)).toFloat()
                    val v = cutoff * sinc(cutoff * x) * w
                    table[row + t] = v
                    sum += v
                }
                // Unity DC gain on every row, so the level does not wobble with the phase.
                if (sum != 0f) {
                    val norm = 1f / sum
                    for (t in 0 until taps) table[row + t] *= norm
                }
            }
        }

        private fun sinc(x: Float): Float {
            if (x == 0f) return 1f
            val px = (PI * x).toFloat()
            return kotlin.math.sin(px) / px
        }
    }

    private companion object {
        /** 1.0 in Q32.32. */
        const val ONE = 1L shl 32
        const val ONE_F = 4294967296f
        const val FRACTION_MASK = 0xFFFFFFFFL

        const val KERNEL_TAPS = 32
        const val KERNEL_PHASES = 512

        /** Samples the kernel reads behind and ahead of the current position. */
        const val HISTORY = KERNEL_TAPS / 2 - 1
        const val AHEAD = KERNEL_TAPS / 2

        /** Keeps the kernel's transition band inside Nyquist instead of straddling it. */
        const val GUARD = 0.92f
    }

    /** Per-channel ring of float samples, indexed by frame from the front. */
    private class FloatRing(private val channels: Int, initialCapacity: Int) {

        private var buf = Array(channels) { FloatArray(initialCapacity) }
        private var mask = initialCapacity - 1
        private var head = 0

        var size = 0
            private set

        fun get(ch: Int, i: Int): Float = buf[ch][(head + i) and mask]

        fun push(frame: FloatArray) {
            if (size > mask) ensure(1)
            val at = (head + size) and mask
            for (ch in 0 until channels) buf[ch][at] = frame[ch]
            size++
        }

        fun drop(n: Int) {
            val k = n.coerceAtMost(size)
            head = (head + k) and mask
            size -= k
        }

        fun clear() {
            head = 0
            size = 0
        }

        /** Makes room for [extra] more frames, growing and linearising if needed. */
        fun ensure(extra: Int) {
            val needed = size + extra
            var cap = mask + 1
            if (needed <= cap) return
            while (cap < needed) cap = cap shl 1
            buf = Array(channels) { ch ->
                val src = buf[ch]
                FloatArray(cap).also { dst ->
                    for (i in 0 until size) dst[i] = src[(head + i) and mask]
                }
            }
            mask = cap - 1
            head = 0
        }
    }

    /** Ring of interleaved 16-bit samples. */
    private class ShortRing(initialCapacity: Int) {

        private var buf = ShortArray(initialCapacity)
        private var mask = initialCapacity - 1
        private var head = 0

        var size = 0
            private set

        fun push(v: Short) {
            if (size > mask) grow()
            buf[(head + size) and mask] = v
            size++
        }

        fun drainTo(out: ShortArray, max: Int): Int {
            val n = minOf(max, out.size, size)
            for (i in 0 until n) out[i] = buf[(head + i) and mask]
            head = (head + n) and mask
            size -= n
            return n
        }

        fun clear() {
            head = 0
            size = 0
        }

        private fun grow() {
            val cap = (mask + 1) shl 1
            val src = buf
            val dst = ShortArray(cap)
            for (i in 0 until size) dst[i] = src[(head + i) and mask]
            buf = dst
            mask = cap - 1
            head = 0
        }
    }
}

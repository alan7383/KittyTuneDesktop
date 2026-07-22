package com.alananasss.kittytune.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Pitch/tempo stage — the desktop equivalent of ExoPlayer's PlaybackParameters(speed, pitch),
 * which the Android app drives via the Sonic time-stretcher.
 *
 * Two modes (matching KittyTune's `isPitchEnabled`):
 *   - pitch == speed  (isPitchEnabled = true, default): plain resampling -> classic
 *     "tape speed" nightcore/daycore. Implemented as linear resample.
 *   - pitch == 1, speed != 1 (isPitchEnabled = false): WSOLA time-stretch that changes
 *     duration while preserving pitch.
 *
 * General case: WSOLA time-stretch by (speed/pitch) then resample by pitch, which
 * reproduces arbitrary (speed, pitch) pairs the same way Sonic composes them.
 *
 * Works per-channel on interleaved 16-bit PCM. Pull-based: feed input, drain output.
 */
class TimeStretcher(private val sampleRate: Int, private val channels: Int) {

    private var speed = 1f
    private var pitch = 1f

    // WSOLA parameters (tuned for music at 44.1k; scale with rate)
    private val frameSize = (sampleRate * 0.030).toInt().coerceAtLeast(256)   // 30 ms analysis frame
    private val overlap = frameSize / 2                                        // 50% overlap
    private val seekWindow = (sampleRate * 0.015).toInt().coerceAtLeast(64)    // ±15 ms search

    // per-channel ring of pending input samples (as float)
    private val input = Array(channels) { ArrayDeque<Float>() }
    private val output = ArrayDeque<Short>()

    // Hann window for overlap-add
    private val window = FloatArray(frameSize) { 0.5f - 0.5f * cos(2.0 * PI * it / (frameSize - 1)).toFloat() }

    // carry-over of the last synthesized tail per channel for overlap-add
    private var tail: Array<FloatArray>? = null

    fun setParameters(speed: Float, pitch: Float) {
        this.speed = speed.coerceIn(0.1f, 4f)
        this.pitch = pitch.coerceIn(0.1f, 4f)
    }

    fun flush() {
        input.forEach { it.clear() }
        output.clear()
        tail = null
    }

    /** Feed interleaved 16-bit PCM (as shorts). */
    fun queue(samples: ShortArray, count: Int) {
        var i = 0
        while (i < count) {
            for (ch in 0 until channels) {
                input[ch].addLast(samples[i + ch].toFloat())
            }
            i += channels
        }
        process()
    }

    /** Drain up to [max] interleaved shorts; returns number written. */
    fun drain(out: ShortArray, max: Int): Int {
        var n = 0
        while (n < max && output.isNotEmpty()) {
            out[n++] = output.removeFirst()
        }
        return n
    }

    fun available(): Int = output.size

    private fun process() {
        // The WSOLA analysis hop advances by `overlap * speedRatio`, synthesis hop by `overlap`.
        // For the pitch-follow case (speed == pitch) we skip WSOLA and just resample.
        val pitchPreserve = abs(speed - pitch) > 1e-3f || abs(pitch - 1f) < 1e-3f && abs(speed - 1f) > 1e-3f

        if (!pitchPreserve && abs(pitch - 1f) < 1e-3f && abs(speed - 1f) < 1e-3f) {
            // passthrough
            drainRaw()
            return
        }

        if (abs(speed - pitch) < 1e-3f) {
            // pitch follows speed exactly -> pure resample by `speed`
            resampleAll(speed)
            return
        }

        // General: time-stretch by (speed/pitch), then resample by pitch.
        timeStretch(speed / pitch)
        // timeStretch writes to `output`; a second resample pass by pitch:
        if (abs(pitch - 1f) > 1e-3f) resampleOutput(pitch)
    }

    private fun drainRaw() {
        val n = input[0].size
        for (f in 0 until n) {
            for (ch in 0 until channels) {
                output.addLast(input[ch].removeFirst().roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
    }

    /** Linear resample all pending input by [ratio] (>1 = faster/higher). */
    private fun resampleAll(ratio: Float) {
        val n = input[0].size
        if (n < 2) return
        val chans = Array(channels) { ch -> FloatArray(n) { input[ch].elementAt(it) } }
        val outLen = (n / ratio).toInt()
        for (o in 0 until outLen) {
            val src = o * ratio
            val i0 = src.toInt()
            if (i0 + 1 >= n) break
            val frac = src - i0
            for (ch in 0 until channels) {
                val a = chans[ch][i0]
                val b = chans[ch][i0 + 1]
                val v = a + (b - a) * frac
                output.addLast(v.roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
        // consume all input (WSOLA-less mode processes in bulk)
        input.forEach { it.clear() }
    }

    private val stretchBuffer = Array(channels) { ArrayDeque<Float>() }

    /** WSOLA time-stretch by [ratio] (>1 = longer/slower). Writes shorts to `output`. */
    private fun timeStretch(ratio: Float) {
        val need = frameSize + seekWindow
        while (input[0].size >= need) {
            val cur = tail
            // analysis frame from the front of input
            val frame = Array(channels) { ch -> FloatArray(frameSize) { input[ch].elementAt(it) } }

            if (cur == null) {
                // first frame: emit windowed frame directly as tail
                tail = Array(channels) { ch -> FloatArray(frameSize) { frame[ch][it] } }
            } else {
                // find best offset in seek window that maximizes cross-correlation
                // between cur's tail and the new frame (WSOLA similarity search)
                var bestOffset = 0
                var bestCorr = Float.NEGATIVE_INFINITY
                for (off in 0 until seekWindow) {
                    var corr = 0f
                    var k = 0
                    while (k < overlap) {
                        for (ch in 0 until channels) {
                            corr += cur[ch][overlap + k] * input[ch].elementAt(off + k)
                        }
                        k += 4 // subsample the correlation for speed
                    }
                    if (corr > bestCorr) { bestCorr = corr; bestOffset = off }
                }

                // overlap-add cur tail with shifted new frame
                val shifted = Array(channels) { ch -> FloatArray(frameSize) { input[ch].elementAt(bestOffset + it) } }
                val synth = Array(channels) { FloatArray(frameSize) }
                for (ch in 0 until channels) {
                    for (k in 0 until overlap) {
                        val w = window[k]
                        val merged = cur[ch][overlap + k] * (1f - w) + shifted[ch][k] * w
                        output.addLast(merged.roundToInt().coerceIn(-32768, 32767).toShort())
                    }
                    // keep the second half as the next tail
                    for (k in 0 until frameSize) synth[ch][k] = shifted[ch][k]
                }
                tail = synth
            }

            // advance input by the analysis hop = overlap * ratio
            val hop = (overlap * ratio).toInt().coerceAtLeast(1)
            repeat(hop) {
                for (ch in 0 until channels) if (input[ch].isNotEmpty()) input[ch].removeFirst()
            }
        }
    }

    private fun resampleOutput(ratio: Float) {
        if (output.isEmpty()) return
        val src = output.toList()
        output.clear()
        val frames = src.size / channels
        val chans = Array(channels) { ch -> FloatArray(frames) { src[it * channels + ch].toFloat() } }
        val outLen = (frames / ratio).toInt()
        for (o in 0 until outLen) {
            val s = o * ratio
            val i0 = s.toInt()
            if (i0 + 1 >= frames) break
            val frac = s - i0
            for (ch in 0 until channels) {
                val a = chans[ch][i0]
                val b = chans[ch][i0 + 1]
                output.addLast((a + (b - a) * frac).roundToInt().coerceIn(-32768, 32767).toShort())
            }
        }
    }
}

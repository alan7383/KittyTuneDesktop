package com.alananasss.kittytune.audio

import com.alananasss.kittytune.ui.player.AudioEffectsState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.tanh

/**
 * How much headroom the limiter has to buy for a given effect state.
 *
 * [PeakLimiter.CEILING_SAFE] as soon as something after the limiter can push the level back
 * up, [PeakLimiter.CEILING_TRANSPARENT] otherwise — which is the ordinary case: no effects and
 * 1x playback, where the 16-bit conversion right after the limiter is the end of the story.
 *
 * The effect toggles are read reflectively rather than from a hand-kept list, so an effect
 * added to [AudioEffectsState] later is covered without anyone remembering this exists. Two
 * flags are deliberately not effects: `isPitchEnabled` only says whether pitch follows speed
 * (speed itself is checked), and normalisation limits its own output at -1 dBFS while still in
 * float, so it cannot overflow the conversion however much it boosts.
 */
internal fun peakLimiterCeilingFor(state: AudioEffectsState): Float {
    if (state.speed != 1f) return PeakLimiter.CEILING_SAFE
    val anyEffectOn = effectToggles.any { getter ->
        runCatching { getter.invoke(state) as? Boolean }.getOrNull() == true
    }
    return if (anyEffectOn) PeakLimiter.CEILING_SAFE else PeakLimiter.CEILING_TRANSPARENT
}

/** Not effects: one is a mode flag, the other cannot overflow the conversion. */
internal val CEILING_EXEMPT_TOGGLES = setOf("isPitchEnabled", "isNormalizationEnabled")

/** Cached once: every `is…Enabled` getter on the state except [CEILING_EXEMPT_TOGGLES]. */
internal val effectToggles: List<java.lang.reflect.Method> by lazy {
    AudioEffectsState::class.java.methods.filter { m ->
        m.parameterCount == 0 &&
            m.name.startsWith("is") &&
            m.name.endsWith("Enabled") &&
            m.name !in CEILING_EXEMPT_TOGGLES &&
            (m.returnType == java.lang.Boolean.TYPE || m.returnType == java.lang.Boolean::class.java)
    }
}

/**
 * Look-ahead peak limiter — the last thing to touch the signal before it is squeezed
 * into 16-bit PCM.
 *
 * Lossy decoders make no promise that their output fits in [-1, 1]. AAC and Opus
 * reconstruct inter-sample peaks that overshoot full scale by up to ~2 dB on hot
 * masters, and rounding those straight to a Short flat-tops them — which is the
 * crackling you hear on loud tracks and nowhere else. Android does not have the
 * problem because its FDK-AAC decoder runs a PCM limiter of its own; ffmpeg's
 * decoders do not, so we run one here.
 *
 * The ceiling moves with the chain rather than sitting at a fixed worst case: see
 * [CEILING_TRANSPARENT] and [CEILING_SAFE].
 *
 * Channel-linked (a single gain for every channel, so the stereo image does not
 * wobble) with a sliding-window minimum over the look-ahead, so the gain has already
 * come down by the time the peak it was computed for reaches the output. Anything
 * that already fits below the threshold passes through untouched.
 *
 * Its output then goes through a soft knee ([SOFT_KNEE] to full scale) instead of a hard
 * clamp, which is what keeps the transparent setting transparent: small overshoots are
 * rounded off sample by sample, with nothing to pump, and only the large ones, the ones
 * that would crackle, cost a gain reduction.
 */
class PeakLimiter(sampleRate: Int, private val channels: Int) {

    /**
     * The ceiling, in linear amplitude. Written from the UI thread when the effect state
     * changes, read on the audio thread every frame — see [CEILING_TRANSPARENT] and
     * [CEILING_SAFE] for the two settings and why the choice matters.
     */
    @Volatile
    private var threshold = CEILING_TRANSPARENT

    /**
     * Moves the ceiling. Takes effect within the look-ahead; the gain smoother rides the
     * change, so there is no click.
     */
    fun setCeiling(linear: Float) {
        threshold = linear.coerceIn(0.1f, CEILING_TRANSPARENT)
    }

    /** Look-ahead, in frames. Also the window the gain has to ramp down over. */
    private val look = (sampleRate * LOOKAHEAD_SECONDS).toInt().coerceIn(16, 4096)

    /** Sliding minimum spans frames [n - look, n], so one more slot than [look]. */
    private val winLen = look + 1

    private val attackCoef = 1f - exp(-1f / (ATTACK_SECONDS * sampleRate))
    private val releaseCoef = 1f - exp(-1f / (RELEASE_SECONDS * sampleRate))

    /** Ring of undelayed frames, [look] frames deep. */
    private val delay = FloatArray(look * channels)
    private var delayPos = 0

    /** Per-frame gain needed to keep that frame under [threshold]. */
    private val required = FloatArray(winLen) { 1f }

    /** Monotonic deque of frame indices, front = index of the window's smallest value. */
    private val dq = LongArray(winLen)
    private var dqHead = 0
    private var dqSize = 0
    private var frameIndex = 0L

    private var gain = 1f

    fun flush() {
        delay.fill(0f)
        delayPos = 0
        required.fill(1f)
        dqHead = 0
        dqSize = 0
        frameIndex = 0L
        gain = 1f
    }

    /**
     * Limits [length] interleaved samples in place. Output is delayed by [look] frames,
     * so the call returns as many samples as it was given.
     */
    fun process(buf: FloatArray, length: Int) {
        var i = 0
        while (i + channels <= length) {
            var peak = 0f
            for (c in 0 until channels) {
                val a = abs(buf[i + c])
                if (a > peak) peak = a
            }
            pushRequired(if (peak > threshold) threshold / peak else 1f)

            val target = windowMin()
            gain += (target - gain) * (if (target < gain) attackCoef else releaseCoef)

            // Swap the frame that has finished its trip through the delay line in for
            // the incoming one, scaling it by the gain that was computed for it.
            val base = delayPos * channels
            for (c in 0 until channels) {
                val out = delay[base + c] * gain
                delay[base + c] = buf[i + c]
                buf[i + c] = softKnee(out)
            }
            delayPos = if (delayPos + 1 == look) 0 else delayPos + 1
            i += channels
        }
    }

    private fun pushRequired(v: Float) {
        val n = frameIndex
        // Retire frames that have fallen out of the [n - look, n] window first, so none of
        // them is still holding the slot in `required` that frame n is about to reuse.
        while (dqSize > 0 && dq[dqHead] < n - look) {
            dqHead = if (dqHead + 1 == winLen) 0 else dqHead + 1
            dqSize--
        }
        required[(n % winLen).toInt()] = v
        // Drop tail entries this frame undercuts: they can never be the window minimum again.
        while (dqSize > 0 && required[(dq[(dqHead + dqSize - 1) % winLen] % winLen).toInt()] >= v) dqSize--
        dq[(dqHead + dqSize) % winLen] = n
        dqSize++
        frameIndex = n + 1
    }

    private fun windowMin(): Float = required[(dq[dqHead] % winLen).toInt()]

    companion object {
        /**
         * +1 dBFS: the gain-riding threshold when nothing downstream can raise the level
         * again, which is the ordinary case: no effects, and the time-stretcher passing
         * through at 1x. Above full scale on purpose; [softKnee] brings what is left of the
         * overshoot back inside [-1, 1].
         *
         * It used to be -0.5 dBFS, and that was audible. A hot master decoded from AAC
         * crosses -0.5 dBFS on nearly every kick, so the limiter pulled the whole mix down by
         * up to 2 dB and let it back up over 80 ms, on every beat: pumping, less punch, and a
         * clearly worse sound than the same stream in a browser, which hands its floats to
         * the OS mixer untouched. Measured on such a track (aac_96k, peaks at +1.6 dBFS) the
         * old setting rode gain about 30% of the time and kept 31 dB of fidelity to the
         * decoded signal; this one keeps 52 dB, and gain riding is left for the large
         * overshoots that would really crackle.
         */
        const val CEILING_TRANSPARENT = 1.122f

        /**
         * -2 dBFS: the ceiling when an effect is active. Those processors work in 16-bit,
         * after the conversion this limiter feeds, so anything they add on top has nowhere to
         * go — the headroom has to be bought here or not at all.
         *
         * Loudness normalisation deliberately does not count as one: the native DSP limits its
         * own output at -1 dBFS while still in float, so however much it boosts, what it hands
         * back is already in range.
         */
        const val CEILING_SAFE = 0.7943f

        /**
         * Where the output's soft knee starts, -0.9 dBFS. Below it the signal is untouched; above it
         * the curve bends towards full scale and never reaches past it, so an overshoot is
         * rounded off instead of flat-topped. The [CEILING_SAFE] output never gets this high.
         */
        const val SOFT_KNEE = 0.9f

        private const val KNEE_RANGE = 1f - SOFT_KNEE

        /** Identity up to [SOFT_KNEE], then a tanh bend that tends to (and never passes) 1. */
        fun softKnee(x: Float): Float {
            val a = abs(x)
            if (a <= SOFT_KNEE) return x
            val y = SOFT_KNEE + KNEE_RANGE * tanh((a - SOFT_KNEE) / KNEE_RANGE)
            return if (x < 0f) -y else y
        }

        private const val LOOKAHEAD_SECONDS = 0.0025f
        private const val ATTACK_SECONDS = 0.0005f
        private const val RELEASE_SECONDS = 0.080f
    }
}

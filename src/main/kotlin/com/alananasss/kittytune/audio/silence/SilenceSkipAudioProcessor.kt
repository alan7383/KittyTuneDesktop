package com.alananasss.kittytune.audio.silence

import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Skip silence, ported from Android PR #83 for the desktop engine.
 *
 * Two jobs on the decoded 16-bit PCM:
 * - **Trim** (the "Skip silence" setting): a run of near-silence plays for [KEPT_SILENCE_MS] and the
 *   rest of it is dropped, so gaps inside and between songs pass quickly while short pauses stay.
 *   The engine's position comes from the decoder's media time, so dropping frames never puts the
 *   clock off.
 * - **Detect** (the "Instantly skip silence" setting): after [LONG_SILENCE_MS] of near-silence it
 *   fires [onLongSilence] once per run, and the player hops over the rest ([SkipSilence]).
 *
 * The settings are read from [SkipSilence] on every buffer, so every engine, including one created
 * later for a crossfade, follows them without being configured.
 */
class SilenceSkipAudioProcessor : BaseAudioProcessor() {

    /** Called on the decoder thread when a long silence starts, at most once per silent run. */
    @Volatile
    var onLongSilence: (() -> Unit)? = null

    private var sampleRate = 44_100
    private var channelCount = 2

    // Trim state.
    private var trimSilentFrames = 0L

    // Detection state.
    private var detectSilentFrames = 0L
    private var notifiedThisSilence = false

    @Volatile
    private var inLongSilence = false

    @Volatile
    private var framesSinceReset = 0L

    @Volatile
    private var heardSoundSinceReset = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        return inputAudioFormat
    }

    override fun onFlush() = clearState()

    override fun onReset() = clearState()

    /** True while a long silence (see [LONG_SILENCE_MS]) is playing. */
    fun isCurrentlySilent(): Boolean = inLongSilence

    /** Frames decoded since [resetTracking]. */
    fun framesSinceReset(): Long = framesSinceReset

    /** Whether any frame decoded since [resetTracking] was not silent. */
    fun heardSoundSinceReset(): Boolean = heardSoundSinceReset

    /** Starts a new observation, e.g. right before the player hops forward. */
    fun resetTracking() = clearState()

    private fun clearState() {
        trimSilentFrames = 0L
        detectSilentFrames = 0L
        notifiedThisSilence = false
        inLongSilence = false
        framesSinceReset = 0L
        heardSoundSinceReset = false
    }

    override fun queueInput(input: ByteBuffer) {
        val remaining = input.remaining()
        if (remaining == 0) return
        val trim = SkipSilence.trimEnabled
        val detect = SkipSilence.instantEnabled
        val out = replaceOutputBuffer(remaining)
        if (!trim && !detect) {
            clearState()
            out.put(input)
            out.flip()
            return
        }

        input.order(ByteOrder.LITTLE_ENDIAN)
        val frameBytes = 2 * channelCount
        val frames = remaining / frameBytes
        val keptFrames = sampleRate * KEPT_SILENCE_MS / 1000L
        val longSilenceFrames = sampleRate * LONG_SILENCE_MS / 1000L
        val base = input.position()

        for (f in 0 until frames) {
            val at = base + f * frameBytes
            var peak = 0
            for (c in 0 until channelCount) {
                val v = abs(input.getShort(at + c * 2).toInt())
                if (v > peak) peak = v
            }
            framesSinceReset++

            if (peak < DETECT_THRESHOLD) {
                if (detect) {
                    detectSilentFrames++
                    if (detectSilentFrames >= longSilenceFrames) {
                        inLongSilence = true
                        if (!notifiedThisSilence) {
                            notifiedThisSilence = true
                            onLongSilence?.invoke()
                        }
                    }
                }
            } else {
                detectSilentFrames = 0L
                notifiedThisSilence = false
                inLongSilence = false
                heardSoundSinceReset = true
            }

            var keep = true
            if (trim) {
                if (peak < TRIM_THRESHOLD) {
                    trimSilentFrames++
                    // A pause shorter than [KEPT_SILENCE_MS] is the music's own and plays in full.
                    if (trimSilentFrames > keptFrames) keep = false
                } else {
                    trimSilentFrames = 0L
                }
            }
            if (keep) {
                for (b in 0 until frameBytes) out.put(input.get(at + b))
            }
        }
        // Consume everything, a trailing partial frame included.
        input.position(input.limit())
        out.flip()
    }

    companion object {
        /** Peak below which a frame counts as silent for trimming (Media3's default, about -30 dBFS). */
        const val TRIM_THRESHOLD = 1024

        /** Stricter peak for the instant mode (about -42 dBFS): only real silence triggers a jump. */
        const val DETECT_THRESHOLD = 256

        /** How much of each silence is still played before the rest is dropped. */
        const val KEPT_SILENCE_MS = 150L
        const val LONG_SILENCE_MS = 2_000L
    }
}

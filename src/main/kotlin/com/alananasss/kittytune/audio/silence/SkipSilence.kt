package com.alananasss.kittytune.audio.silence

import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The skip-silence settings every engine reads, and the instant mode's jump loop.
 *
 * Instant mode: once [SilenceSkipAudioProcessor] reports two seconds of silence, the playhead jumps
 * forward in [STEP_MS] steps for as long as nothing but silence is decoded after a jump, so a long
 * gap (a hidden track after minutes of nothing) passes in a moment.
 */
object SkipSilence {

    /** The player as the jump loop sees it, so the loop can be tested without an audio device. */
    interface Target {
        /** The processor of the engine that is playing now, or null when there is none. */
        val activeProcessor: SilenceSkipAudioProcessor?
        val positionMs: Long
        val durationMs: Long
        fun seekTo(positionMs: Long)
    }

    const val STEP_MS = 15_000L
    const val SETTLE_MS = 350L
    const val MAX_HOPS = 80

    @Volatile
    var trimEnabled: Boolean = false
        private set

    @Volatile
    var instantEnabled: Boolean = false
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var job: Job? = null

    /** Reads the saved settings; called at start-up and whenever they change. */
    fun applySettings(prefs: PlayerPreferences = PlayerPreferences()) {
        set(prefs.getSkipSilence(), prefs.getInstantSkipSilence())
    }

    /** The instant mode works on top of skip silence, as on Android. */
    fun set(skipSilence: Boolean, instant: Boolean) {
        trimEnabled = skipSilence
        instantEnabled = skipSilence && instant
        if (!instantEnabled) job?.cancel()
    }

    /** Called by an engine's processor (on its decoder thread) when a long silence starts. */
    fun onLongSilence(target: Target, processor: SilenceSkipAudioProcessor) {
        if (!instantEnabled) return
        if (job?.isActive == true) return
        job = scope.launch {
            delay(200)
            jumpOverSilence(target, processor)
        }
    }

    /** Hops forward while only silence is found; returns the number of jumps made. */
    internal suspend fun jumpOverSilence(target: Target, processor: SilenceSkipAudioProcessor): Int {
        val duration = target.durationMs
        if (duration <= STEP_MS) return 0
        var hops = 0
        // Only the engine that is playing is moved: a deck fading out of a crossfade is left alone.
        while (currentCoroutineContext().isActive && instantEnabled && target.activeProcessor === processor) {
            if (hops == 0 && !processor.isCurrentlySilent()) break
            val current = target.positionMs
            val next = (current + STEP_MS).coerceAtMost(duration - 500)
            if (next <= current) break
            processor.resetTracking()
            target.seekTo(next)
            hops++
            if (hops >= MAX_HOPS || next >= duration - 500) break
            delay(SETTLE_MS)
            // Keep going only while everything decoded since the jump was silence.
            var waited = 0
            while (processor.framesSinceReset() == 0L && waited < 20) {
                delay(50)
                waited++
            }
            if (processor.heardSoundSinceReset() || processor.framesSinceReset() == 0L) break
        }
        if (hops > 0) Logger.i("SkipSilence", "Instant silence skip: jumped $hops times")
        return hops
    }
}

package com.alananasss.kittytune.audio.automix

import kotlin.math.roundToInt

/**
 * Turns one step of a Mix transition ([MixTransitionPresets.frame], or [planFrameAt] for a hand-drawn
 * Custom seam) into what the two desktop decks can do: a gain each, a bass-duck shelf each, a sweep filter
 * each and the outgoing deck's speed.
 *
 * Kept pure so the crossfade loop in the player and the tests compute exactly the same thing.
 *
 * Desktop decks have no stem separation: the "bass swap" of a BLEND (deck A gives its lows to deck B at the
 * midpoint bar) is rendered by the outgoing deck's duck shelf going fully down from the swap on. The safety
 * limiter is always engaged on desktop, so a SLAM's limiter hold needs nothing extra.
 */
object CrossfadeShaper {

    /** The slowest the outgoing deck may go in a MELT (the desktop stretcher's floor is 0.5 anyway). */
    const val MIN_MELT_SPEED = 0.05f

    data class DeckControls(
        val fadeOut: Float,
        val fadeIn: Float,
        val duckOut: Float,
        val duckIn: Float,
        val sweepModeOut: MixSweepAudioProcessor.FilterMode,
        val sweepModeIn: MixSweepAudioProcessor.FilterMode,
        val sweepHzOut: Float?,
        val sweepHzIn: Float?,
        val sweepQ: Float,
        val speedOut: Float,
    )

    /** Beats the transition spans, so the presets can quantize to the grid; a style's own phrase when the tempo is unknown. */
    fun beatsFor(durationMs: Long, bpm: Float, style: MixTransitionPresets.MixTransitionStyle?): Int =
        if (bpm > 0f) ((durationMs / 60_000f) * bpm).roundToInt().coerceAtLeast(1)
        else style?.defaultPhraseBeats ?: 16

    /**
     * The decks' controls at [progress] (0..1), or null when neither a style nor exact curves drive this
     * transition (a plain crossfade keeps its own equal-power shape).
     */
    fun shape(
        style: MixTransitionPresets.MixTransitionStyle?,
        exactCurves: TransitionPlan?,
        progress: Float,
        beats: Int,
        bpm: Float,
    ): DeckControls? {
        if (exactCurves != null) {
            val f = planFrameAt(exactCurves, progress, beats, bpm)
            return DeckControls(
                fadeOut = f.fadeOut,
                fadeIn = f.fadeIn,
                duckOut = f.bassDuckOut,
                duckIn = f.bassDuckIn,
                sweepModeOut = f.sweepModeOut ?: MixSweepAudioProcessor.FilterMode.BYPASS,
                sweepModeIn = f.sweepModeIn ?: MixSweepAudioProcessor.FilterMode.BYPASS,
                sweepHzOut = f.sweepHz,
                sweepHzIn = f.sweepHzIn,
                sweepQ = f.sweepQ,
                speedOut = 1f,
            )
        }
        if (style == null) return null
        val f = MixTransitionPresets.frame(style, progress, beats, bpm)
        val (modeOut, modeIn) = when (style) {
            MixTransitionPresets.MixTransitionStyle.RISE ->
                MixSweepAudioProcessor.FilterMode.HIGH_PASS to MixSweepAudioProcessor.FilterMode.BYPASS
            MixTransitionPresets.MixTransitionStyle.WAVE ->
                MixSweepAudioProcessor.FilterMode.BAND_PASS to MixSweepAudioProcessor.FilterMode.BAND_PASS
            else -> MixSweepAudioProcessor.FilterMode.BYPASS to MixSweepAudioProcessor.FilterMode.BYPASS
        }
        return DeckControls(
            fadeOut = f.fadeOut,
            fadeIn = f.fadeIn,
            // From the swap on, the outgoing deck keeps only mids and highs: the incoming one owns the lows.
            duckOut = if (f.bassSwapOut) 1f else f.bassDuckOut,
            duckIn = f.bassDuckIn,
            sweepModeOut = modeOut,
            sweepModeIn = modeIn,
            sweepHzOut = if (modeOut != MixSweepAudioProcessor.FilterMode.BYPASS) f.sweepHz else null,
            sweepHzIn = if (modeIn != MixSweepAudioProcessor.FilterMode.BYPASS) f.sweepHzIn else null,
            sweepQ = f.sweepQ,
            // A MELT's curve ends at exactly 0, which no stretcher accepts.
            speedOut = if (f.speedOut != 1f) f.speedOut.coerceAtLeast(MIN_MELT_SPEED) else 1f,
        )
    }
}

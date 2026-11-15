package com.alananasss.kittytune.audio.automix

import kotlin.math.abs

/**
 * Whether two tempos can be beat-matched, Spotify-Mix style, and the stretch that does it.
 *
 * The incoming tempo is folded by octave (70 BPM against 140 locks at double time); the pair matches when
 * the folded tempos are within [MAX_BPM_DIFF] and the resulting stretch stays within [MAX_TEMPO_STRETCH].
 * A pitch-preserving stretch turns audibly grainy beyond about 8 %, and 10 BPM at 70-80 BPM is already a
 * 12-14 % stretch, so both rules apply.
 */
object MixTempoMatch {

    const val MAX_TEMPO_STRETCH = 0.08f
    const val MAX_BPM_DIFF = 10f

    /** (can match, ratio to apply to the incoming deck); the ratio is 1 when it cannot. */
    fun match(outBpm: Float, inBpm: Float): Pair<Boolean, Float> {
        if (outBpm <= 0f || inBpm <= 0f) return false to 1f
        val bestCandidate = listOf(inBpm, inBpm * 2f, inBpm * 0.5f).minByOrNull { abs(outBpm - it) } ?: inBpm
        if (abs(outBpm - bestCandidate) <= MAX_BPM_DIFF) {
            val ratio = outBpm / bestCandidate
            if (ratio in (1f - MAX_TEMPO_STRETCH)..(1f + MAX_TEMPO_STRETCH)) return true to ratio
        }
        return false to 1f
    }
}

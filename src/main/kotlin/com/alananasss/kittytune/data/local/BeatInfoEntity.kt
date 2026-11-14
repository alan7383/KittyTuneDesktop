package com.alananasss.kittytune.data.local

data class BeatInfoEntity(
    val songId: String,
    val bpm: Float,
    val firstBeatOffsetMs: Long,
    val confidence: Float,
    val analyzedAt: Long = System.currentTimeMillis(),
    /** Where the incoming track should start: first sustained-energy downbeat past intro. */
    val mixInPointMs: Long? = null,
    /** Where the outgoing track's body ends (outro begins); transition starts here. */
    val mixOutPointMs: Long? = null,
    /** 0=C, 1=C#, ... 11=B. Null when track's tonal chroma signal was too weak to call a key. */
    val keyPitchClass: Int? = null,
    val keyIsMinor: Boolean? = null,
    /** 0..1: how loud and driving the body of the track is, from its middle. Null for tracks analysed before this existed. */
    val energy: Float? = null,
    /**
     * Version of the analyzer that produced this row. Rows below [CURRENT_ANALYSIS_VERSION] are analysed
     * again: trusting a cached row from an older analyzer would keep every played track on the old result.
     */
    val analysisVersion: Int = CURRENT_ANALYSIS_VERSION,
) {
    /** True when this row was produced by the current analyzer and can be used as-is. */
    val isCurrent: Boolean get() = analysisVersion >= CURRENT_ANALYSIS_VERSION

    companion object {
        /**
         * 1: rows written before versions were recorded.
         * 2: musical key computed with a semitone-resolving FFT and Pearson correlation. Version 1 keys came
         *    from a 43 Hz-per-bin chroma that mapped most tracks onto the same few keys.
         */
        const val CURRENT_ANALYSIS_VERSION = 2
    }
}

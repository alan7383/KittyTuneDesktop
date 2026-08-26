package com.alananasss.kittytune.audio

/**
 * The per-track volume trim: how loud one particular song plays relative to everything else
 * (issue #33).
 *
 * Volume normalisation was the obvious answer and was rejected for a concrete reason — it measures
 * as it goes, so a track starts at whatever level it was mastered at and is only pulled to the
 * target a few seconds in, which is audible and annoying. A trim remembered against the track has
 * none of that: it is known before the first sample and applied from it.
 *
 * Whole decibels, like the lyrics offset is whole seconds. Finer steps would be below what anyone
 * can set by ear on a single song.
 */
object TrackGain {

    /** No trim. A track at this value is stored as nothing at all, so the default stays cheap. */
    const val NONE = 0

    /** What one press of the button changes, in dB. */
    const val STEP_DB = 1

    val MIN_DB: Int = TRACK_GAIN_MIN_DB.toInt()
    val MAX_DB: Int = TRACK_GAIN_MAX_DB.toInt()

    /** @return [db] brought inside the range the output line will actually accept. */
    fun clamp(db: Int): Int = db.coerceIn(MIN_DB, MAX_DB)

    /**
     * @return [db] moved by [delta] steps and clamped, so holding a button at either end is a no-op
     *   rather than a value that silently means something else.
     */
    fun adjust(db: Int, delta: Int): Int = clamp(db + delta * STEP_DB)

    /**
     * The trim as it reads on screen: "+3 dB", "−6 dB", "0 dB".
     *
     * A real minus sign rather than a hyphen, to match the rest of the numbers in the player, and an
     * explicit plus so a boost cannot be misread as a cut.
     */
    fun label(db: Int): String = when {
        db > 0 -> "+$db dB"
        db < 0 -> "−${-db} dB"
        else -> "0 dB"
    }
}

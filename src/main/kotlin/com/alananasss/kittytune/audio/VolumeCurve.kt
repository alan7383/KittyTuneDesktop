package com.alananasss.kittytune.audio

import kotlin.math.log10
import kotlin.math.pow

/**
 * Maps the volume slider to what the audio line is given.
 *
 * Loudness is heard logarithmically, so the slider moves in equal decibel steps across [RANGE_DB]: the far
 * left is -40 dB (quiet but clearly audible), half way is -20 dB, the far right is full level, and zero is
 * silence. Every percent of the slider is an audible step.
 *
 * Two earlier curves got this wrong in opposite directions. The slider once set the amplitude directly, so
 * half way was only -6 dB and every usable level was crammed into the bottom sixth. A cubic curve replaced it,
 * but a cube falls away too fast at the bottom — 20 % was -42 dB and 10 % was -60 dB — so the lowest fifth
 * of the slider sounded like nothing at all.
 */
object VolumeCurve {
    /** The span the slider covers, from its first step to full level. */
    const val RANGE_DB = 40f

    fun sliderToAmplitude(position: Float): Float {
        val p = position.coerceIn(0f, 1f)
        if (p <= 0f) return 0f
        return 10f.pow(RANGE_DB * (p - 1f) / 20f)
    }

    /** The inverse; anything quieter than the slider's bottom lands on its first step rather than on mute. */
    fun amplitudeToSlider(amplitude: Float): Float {
        val a = amplitude.coerceIn(0f, 1f)
        if (a <= 0f) return 0f
        return (1f + 20f * log10(a) / RANGE_DB).coerceIn(0.01f, 1f)
    }
}

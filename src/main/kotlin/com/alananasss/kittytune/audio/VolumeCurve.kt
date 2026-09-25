package com.alananasss.kittytune.audio

import kotlin.math.cbrt

/**
 * Maps the volume slider to what the audio line is given.
 *
 * Loudness is heard logarithmically, and the slider used to set the amplitude directly: half way was
 * only -6 dB, barely quieter than full, and every usable level was squeezed into the bottom sixth of
 * the track. The cubic curve is what desktop mixers use — half way is about -18 dB, a tenth -60 dB —
 * so equal slider travel sounds like an equal step.
 */
object VolumeCurve {
    fun sliderToAmplitude(position: Float): Float {
        val p = position.coerceIn(0f, 1f)
        return p * p * p
    }

    fun amplitudeToSlider(amplitude: Float): Float = cbrt(amplitude.coerceIn(0f, 1f))
}

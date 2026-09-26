package com.alananasss.kittytune.ui.player.slider

/**
 * The phase of every moving wave in the app, from one clock.
 *
 * Each wavy track used to advance its own phase from its own start, so the seek bar and the volume track
 * drifted apart. Reading the phase from the time instead keeps any two waves with the same speed in step.
 */
internal fun wavePhasePx(speedPx: Float, wavelengthPx: Float): Float {
    if (wavelengthPx <= 0f) return 0f
    val seconds = System.nanoTime() / 1_000_000_000.0
    return ((seconds * speedPx) % wavelengthPx).toFloat()
}

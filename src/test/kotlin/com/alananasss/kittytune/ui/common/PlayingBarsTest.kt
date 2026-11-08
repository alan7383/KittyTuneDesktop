package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bars moved off an infinite transition onto the ambient clock; this pins that they still trace the curve the
 * transition drew: `infiniteRepeatable(tween(period), RepeatMode.Reverse)` from 0.25 to 1.
 */
class PlayingBarsTest {

    @Test
    fun starts_low_peaks_after_one_period_and_comes_back_down() {
        assertEquals(0.25f, bounceAt(0.0, 400), 1e-4f)
        assertEquals(1f, bounceAt(400.0, 400), 1e-4f)
        assertEquals(0.25f, bounceAt(800.0, 400), 1e-4f)
    }

    @Test
    fun eases_like_tween_on_the_way_up_and_mirrors_it_on_the_way_down() {
        val up = bounceAt(100.0, 400)
        val down = bounceAt(700.0, 400)
        assertEquals(0.25f + 0.75f * FastOutSlowInEasing.transform(0.25f), up, 1e-4f)
        assertEquals(up, down, 1e-4f)
    }
}

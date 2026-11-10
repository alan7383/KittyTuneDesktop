package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The curve [rememberAmbientLoop] draws, pinned as a pure function.
 *
 * The decorations that moved off `rememberInfiniteTransition` must trace the same values it did, or the change
 * is visible: a wave that jumps at the seam, a pulse that starts wrong, a spin whose angle is not where the
 * transition left it. The loop is sampled on the shared ambient pulse instead of the window's own frames, but what
 * the time maps to is this function, and this function is what a test can hold still.
 */
class AmbientLoopTest {

    @Test
    fun `a restart loop starts at from and reaches to before the period ends`() {
        assertEquals(0f, ambientLoopValue(0.0, 1000, 0f, 360f, reverse = false, easing = LinearEasing), 1e-4f)
        assertEquals(180f, ambientLoopValue(500.0, 1000, 0f, 360f, reverse = false, easing = LinearEasing), 1e-4f)
        // At exactly one period the next run has already begun; the value is one frame short of `to` before it.
        assertEquals(360f, ambientLoopValue(999.0, 1000, 0f, 360f, reverse = false, easing = LinearEasing), 0.5f)
    }

    @Test
    fun `a restart loop is seamless`() {
        // The frame after the period and the frame after a whole number of them both land on `from` again.
        assertEquals(0f, ambientLoopValue(1_000.01, 1000, 0f, 360f, reverse = false, easing = LinearEasing), 1e-2f)
        assertEquals(0f, ambientLoopValue(7_000.0, 1000, 0f, 360f, reverse = false, easing = LinearEasing), 1e-4f)
    }

    @Test
    fun `a reverse loop goes back down the next period`() {
        assertEquals(1f, ambientLoopValue(1_000.0, 1000, 0f, 1f, reverse = true, easing = LinearEasing), 1e-4f)
        assertEquals(0.5f, ambientLoopValue(1_500.0, 1000, 0f, 1f, reverse = true, easing = LinearEasing), 1e-4f)
        assertEquals(0f, ambientLoopValue(2_000.0, 1000, 0f, 1f, reverse = true, easing = LinearEasing), 1e-4f)
        // Mirrored, not run backwards through the easing curve: this is what infiniteRepeatable did.
        assertEquals(0.25f, ambientLoopValue(250.0, 1000, 0f, 1f, reverse = true, easing = LinearEasing), 1e-4f)
        assertEquals(0.25f, ambientLoopValue(1_750.0, 1000, 0f, 1f, reverse = true, easing = LinearEasing), 1e-4f)
    }

    @Test
    fun `the easing is applied within a period`() {
        val expected = 0.25f + 0.75f * FastOutSlowInEasing.transform(0.25f)
        assertEquals(expected, ambientLoopValue(250.0, 1000, 0.25f, 1f, reverse = false, easing = FastOutSlowInEasing), 1e-4f)
    }

    @Test
    fun `a period that is not positive parks on the target`() {
        assertEquals(360f, ambientLoopValue(12_345.0, 0, 0f, 360f, reverse = false, easing = LinearEasing), 1e-4f)
    }
}

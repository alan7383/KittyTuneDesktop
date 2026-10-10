package com.alananasss.kittytune.ui.main

import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals

/** The My Wave card's waves loop without a seam (round 2 of the tester's list, item 10). */
class WaveLoopTest {

    @Test
    fun `every wave is where it started after a full turn of the phase`() {
        val turn = (2 * PI).toFloat()
        for (wave in 0 until 3) {
            val start = waveShift(0f, wave)
            val end = waveShift(turn, wave)
            // The wave is a sine of its shift, so only the shift modulo a full turn matters.
            assertEquals(sin(start), sin(end), 1e-4f, "wave $wave")
            assertEquals(cos(start), cos(end), 1e-4f, "wave $wave")
        }
    }
}

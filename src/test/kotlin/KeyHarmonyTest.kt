package com.alananasss.kittytune

import com.alananasss.kittytune.audio.automix.KeyHarmony
import com.alananasss.kittytune.audio.automix.KeyHarmony.Fit
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyHarmonyTest {

    @Test
    fun `the same key and its relative are the same notes`() {
        assertEquals(Fit.SAME, KeyHarmony.between("8A", "8A"))
        assertEquals(Fit.SAME, KeyHarmony.between("8A", "8B"))
    }

    @Test
    fun `a step round the wheel in the same mode is a neighbour, across the twelve too`() {
        assertEquals(Fit.NEIGHBOUR, KeyHarmony.between("8A", "9A"))
        assertEquals(Fit.NEIGHBOUR, KeyHarmony.between("12B", "1B"))
    }

    @Test
    fun `two steps up is a lift and unrelated keys clash`() {
        assertEquals(Fit.LIFT, KeyHarmony.between("5A", "7A"))
        assertEquals(Fit.CLASH, KeyHarmony.between("1A", "4A"))
        assertEquals(Fit.CLASH, KeyHarmony.between("1A", "6A"))
        assertEquals(Fit.CLASH, KeyHarmony.between("8A", "11B"))
    }

    @Test
    fun `a step with a letter change is a diagonal, which still mixes`() {
        assertEquals(Fit.DIAGONAL, KeyHarmony.between("8A", "9B"))
        assertEquals(Fit.DIAGONAL, KeyHarmony.between("1B", "12A"))
    }

    @Test
    fun `a fifth is one wheel step, five steps is a semitone clash`() {
        // C major (8B) to G major (9B) is a fifth: one step, a neighbour.
        assertEquals(Fit.NEIGHBOUR, KeyHarmony.between("8B", "9B"))
        // Five steps in the same mode is a one-semitone transposition: it clashes.
        assertEquals(Fit.CLASH, KeyHarmony.between("8B", "1B"))
    }

    @Test
    fun `a key that could not be told is unknown, not a clash`() {
        assertEquals(Fit.UNKNOWN, KeyHarmony.between(null, "8A"))
        assertEquals(Fit.UNKNOWN, KeyHarmony.between("8A", "nonsense"))
    }
}

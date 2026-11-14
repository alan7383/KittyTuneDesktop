package com.alananasss.kittytune.audio.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class KeyDetectionTest {

    private val rate = 44_100

    /** A few seconds of the given MIDI notes played together, with a quiet octave below each. */
    private fun chord(vararg midi: Int, seconds: Double = 6.0): FloatArray {
        val n = (rate * seconds).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / rate
            midi.sumOf { m ->
                val f = 440.0 * 2.0.pow((m - 69) / 12.0)
                sin(2 * PI * f * t) + 0.4 * sin(2 * PI * f / 2 * t)
            }.toFloat() / (midi.size * 1.4f)
        }
    }

    /** A cadence that spells the key: I, IV, V, I triads. */
    private fun progression(tonicMidi: Int, minor: Boolean): FloatArray {
        val third = if (minor) 3 else 4
        val triad = { root: Int -> intArrayOf(root, root + third, root + 7) }
        val parts = listOf(
            chord(*triad(tonicMidi), seconds = 3.0),
            chord(*intArrayOf(tonicMidi + 5, tonicMidi + 5 + third, tonicMidi + 12), seconds = 2.0),
            chord(*intArrayOf(tonicMidi + 7, tonicMidi + 11, tonicMidi + 14), seconds = 2.0),
            chord(*triad(tonicMidi), seconds = 3.0),
        )
        return parts.reduce { a, b -> a + b }
    }

    @Test
    fun `c major cadence reads as c major`() {
        assertEquals(0 to false, BeatAnalyzer.estimateKey(progression(60, minor = false), rate))
    }

    @Test
    fun `a minor cadence reads as a minor`() {
        assertEquals(9 to true, BeatAnalyzer.estimateKey(progression(57, minor = true), rate))
    }

    @Test
    fun `neighbouring keys are told apart`() {
        // The old 43 Hz chroma merged keys a semitone apart; D major and C# major must differ.
        assertEquals(2 to false, BeatAnalyzer.estimateKey(progression(62, minor = false), rate))
        assertEquals(1 to false, BeatAnalyzer.estimateKey(progression(61, minor = false), rate))
    }

    @Test
    fun `silence and too-short input have no key`() {
        assertNull(BeatAnalyzer.estimateKey(FloatArray(rate * 4), rate))
        assertNull(BeatAnalyzer.estimateKey(FloatArray(1000), rate))
    }

    @Test
    fun `chroma fft resolves semitones at common rates`() {
        assertEquals(8192, BeatAnalyzer.keyFftSize(44_100, 44_100 * 10))
        assertEquals(8192, BeatAnalyzer.keyFftSize(48_000, 48_000 * 10))
        assertEquals(0, BeatAnalyzer.keyFftSize(44_100, 4_000))
    }
}

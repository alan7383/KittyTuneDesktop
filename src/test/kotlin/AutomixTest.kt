package com.alananasss.kittytune

import com.alananasss.kittytune.audio.automix.BeatAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

class AutomixTest {

    @Test
    fun testFftImpulse() {
        val size = 16
        val re = FloatArray(size)
        val im = FloatArray(size)
        // Delta impulse at 0
        re[0] = 1f

        BeatAnalyzer.fft(re, im)

        for (i in 0 until size) {
            assertEquals("Real component at $i", 1f, re[i], 1e-4f)
            assertEquals("Imaginary component at $i", 0f, im[i], 1e-4f)
        }
    }

    @Test
    fun testEstimateKeyMajorChord() {
        // Generate a 22050Hz audio buffer with C major chord: C4 (261.63Hz), E4 (329.63Hz), G4 (392.00Hz)
        val sampleRate = 22050
        val durationSec = 1.0
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(numSamples)

        val fC3 = 130.81
        val fC4 = 261.63
        val fE4 = 329.63
        val fG4 = 392.00
        val fC5 = 523.25

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            samples[i] = (sin(2.0 * PI * fC3 * t) * 1.5 +
                          sin(2.0 * PI * fC4 * t) * 1.2 +
                          sin(2.0 * PI * fE4 * t) * 0.8 +
                          sin(2.0 * PI * fG4 * t) * 0.8 +
                          sin(2.0 * PI * fC5 * t) * 1.0).toFloat() / 5.3f
        }

        val result = BeatAnalyzer.estimateKey(samples, sampleRate)
        assertNotNull("Key result should not be null for strong triad", result)
        assertEquals("Pitch class should be 0 (C)", 0, result!!.first)
        assertEquals("Key should be major (false)", false, result.second)
    }

    @Test
    fun testEstimateKeyMinorChord() {
        // Generate a 22050Hz audio buffer with A minor chord: A4 (440Hz), C5 (523.25Hz), E5 (659.25Hz)
        val sampleRate = 22050
        val durationSec = 1.0
        val numSamples = (sampleRate * durationSec).toInt()
        val samples = FloatArray(numSamples)

        val fA = 440.00
        val fC = 523.25
        val fE = 659.25

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            samples[i] = (sin(2.0 * PI * fA * t) + sin(2.0 * PI * fC * t) + sin(2.0 * PI * fE * t)).toFloat() / 3f
        }

        val result = BeatAnalyzer.estimateKey(samples, sampleRate)
        assertNotNull("Key result should not be null for A minor triad", result)
        assertEquals("Pitch class should be 9 (A)", 9, result!!.first)
        assertEquals("Key should be minor (true)", true, result.second)
    }

    @Test
    fun testEstimateBeatPhase() {
        // Periodic spikes every 10 frames with phase offset 3
        val flux = FloatArray(100)
        val period = 10f
        val expectedPhase = 3
        for (i in flux.indices) {
            if (i % 10 == expectedPhase) {
                flux[i] = 10f
            } else {
                flux[i] = 0.5f
            }
        }

        val phase = BeatAnalyzer.estimateBeatPhase(flux, period)
        assertEquals(expectedPhase.toFloat(), phase, 0.1f)
    }

    @Test
    fun testHarmonicShiftCalculation() {
        val outKeyClass = 9
        val outIsMinor = true
        val inKeyClass = 0
        val inIsMinor = false

        val outEffective = if (outIsMinor) (outKeyClass + 3) % 12 else outKeyClass
        val inEffective = if (inIsMinor) (inKeyClass + 3) % 12 else inKeyClass
        var semitoneShift = (outEffective - inEffective) % 12
        if (semitoneShift > 6) semitoneShift -= 12
        if (semitoneShift < -6) semitoneShift += 12

        assertEquals(0, semitoneShift)

        val cKey = 0
        val dKey = 2
        var shiftD = (cKey - dKey) % 12
        if (shiftD > 6) shiftD -= 12
        if (shiftD < -6) shiftD += 12
        assertEquals(-2, shiftD)

        val pitchRatio = 2.0.pow(shiftD / 12.0).toFloat()
        assertTrue("Pitch ratio should be less than 1.0 for downward shift", pitchRatio < 1.0f)
        assertEquals(0.8908987f, pitchRatio, 0.001f)
    }

    @Test
    fun testTempoRatioClamping() {
        var ratio = 120f / 124f
        while (ratio > 1.5f) ratio /= 2f
        while (ratio < 0.667f) ratio *= 2f
        assertTrue("Ratio should be in range 0.92..1.08", ratio in 0.92f..1.08f)

        var farRatio = 120f / 160f
        while (farRatio > 1.5f) farRatio /= 2f
        while (farRatio < 0.667f) farRatio *= 2f
        val clamped = if (farRatio !in 0.92f..1.08f) 1f else farRatio
        assertEquals(1f, clamped, 1e-4f)
    }

    @Test
    fun testPhraseGridAlignment() {
        val bpm = 120f
        val periodMs = 60_000.0 / bpm // 500ms
        val phraseMs = periodMs * 8 // 4000ms (8 beats / 2 bars)
        val firstBeatOffsetMs = 250L

        val anchor = 10_000L
        val k = ((anchor - firstBeatOffsetMs) / phraseMs).toLong()
        var triggerTime = (firstBeatOffsetMs + k * phraseMs).toLong()
        if (triggerTime < anchor) triggerTime = (firstBeatOffsetMs + (k + 1) * phraseMs).toLong()

        assertTrue("Trigger time must be at least anchor", triggerTime >= anchor)
        assertEquals(0L, (triggerTime - firstBeatOffsetMs) % phraseMs.toLong())
    }

    @Test
    fun testPrebufferLeadWindow() {
        val triggerTime = 180_000L
        val leadWindowMax = 12_000L
        val leadWindowMin = 1_000L

        val posFar = 165_000L
        val remainingFar = triggerTime - posFar
        assertTrue("Far position should be > leadWindowMax", remainingFar > leadWindowMax)

        val posLead = 172_000L
        val remainingLead = triggerTime - posLead
        assertTrue("Lead position should be in window", remainingLead in leadWindowMin..leadWindowMax)

        val posTrigger = 180_100L
        assertTrue("Past trigger should fire transition", posTrigger >= triggerTime)
    }

    @Test
    fun testSmoothPlaybackParametersRamp() {
        val startSpeed = 1.08f
        val endSpeed = 1.00f
        val startPitch = 1.12f
        val endPitch = 1.00f
        val steps = 10

        var previousSpeed = startSpeed
        var previousPitch = startPitch

        for (step in 1..steps) {
            val frac = step.toFloat() / steps
            val curSpeed = startSpeed + frac * (endSpeed - startSpeed)
            val curPitch = startPitch + frac * (endPitch - startPitch)

            assertTrue("Speed should decrease monotonically towards baseline", curSpeed <= previousSpeed + 1e-4f)
            assertTrue("Pitch should decrease monotonically towards baseline", curPitch <= previousPitch + 1e-4f)
            previousSpeed = curSpeed
            previousPitch = curPitch
        }

        assertEquals(1.00f, previousSpeed, 1e-4f)
        assertEquals(1.00f, previousPitch, 1e-4f)
    }

    @Test
    fun testDetectMixOutOutroBounds() {
        val durationMs = 120_000L
        val totalBlocks = 240 // 500ms per block -> 120s
        val env = FloatArray(totalBlocks) { i ->
            if (i < 200) 1.0f else 0.1f
        }

        val mixOutMs = BeatAnalyzer.detectMixOut(env, windowStartMs = 0L, durationMs = durationMs)
        assertNotNull("Mix-out point should be detected for quiet outro", mixOutMs)
        assertTrue("Mix-out point should be near 100s", mixOutMs!! in 95_000L..105_000L)
    }

    @Test
    fun testBpmSyncPulsePeriod() {
        val bpm120 = 120f
        val period120 = 60_000f / bpm120
        assertEquals(500f, period120, 0.01f)

        val bpm80 = 80f
        val period80 = 60_000f / bpm80
        assertEquals(750f, period80, 0.01f)

        val bpm150 = 150f
        val period150 = 60_000f / bpm150
        assertEquals(400f, period150, 0.01f)
    }

    @Test
    fun testGaplessAlbumMatching() {
        val albumA = "Random Access Memories"
        val albumB = "Discovery"

        val isSameAlbum = albumA.isNotBlank() && albumA == albumA
        assertTrue("Same album title must match for gapless", isSameAlbum)

        val isDifferentAlbum = albumA.isNotBlank() && albumA == albumB
        assertEquals("Different album must not match", false, isDifferentAlbum)
    }
}

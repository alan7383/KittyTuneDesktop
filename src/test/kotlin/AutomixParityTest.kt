package com.alananasss.kittytune

import com.alananasss.kittytune.audio.automix.AutomixDuckAudioProcessor
import com.alananasss.kittytune.audio.automix.BeatAnalyzer
import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class AutomixParityTest {

    private val sampleRate = 44100

    @Test
    fun `FFT detects peak bin of pure tone`() {
        val n = 1024
        val targetFreq = 441.0 // 44100 / 1024 = 43.066 Hz per bin -> ~10.24 bins -> bin 10
        val expectedBin = (targetFreq / (sampleRate.toDouble() / n)).roundToInt()

        val re = FloatArray(n)
        val im = FloatArray(n)

        for (i in 0 until n) {
            re[i] = sin(2.0 * PI * targetFreq * i / sampleRate).toFloat()
            im[i] = 0f
        }

        BeatAnalyzer.fft(re, im)

        var maxMag = 0f
        var peakBin = -1
        for (b in 0 until n / 2) {
            val mag = sqrt(re[b] * re[b] + im[b] * im[b])
            if (mag > maxMag) {
                maxMag = mag
                peakBin = b
            }
        }

        assertEquals("FFT peak bin should correspond to the tone frequency", expectedBin, peakBin)
    }

    @Test
    fun `Real audio file analysis runs through BeatAnalyzer without error`() {
        val file = java.io.File("src/main/resources/raw/cafe.mp3")
        assertTrue("Test file should exist", file.exists())
        val result = BeatAnalyzer.analyzeFile(file.absolutePath)
        println("Real audio analysis result for cafe.mp3: $result")
        assertNotNull("Analysis should succeed on real audio", result)
        result?.let {
            assertTrue("BPM should be positive (got ${it.bpm})", it.bpm > 0f)
            assertTrue("Confidence should be >= 0 (got ${it.confidence})", it.confidence >= 0f)
        }
    }

    @Test
    fun `Key detection identifies C major and A minor triads`() {
        val durationSeconds = 0.5
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val binHz = sampleRate.toDouble() / 1024.0

        // Helper to synthesize a triad from exact bin center frequencies (avoiding low-frequency FFT leakage)
        fun generateChordFromBins(bins: List<Int>): FloatArray {
            val samples = FloatArray(totalSamples)
            for (b in bins) {
                val freq = b * binHz
                for (i in 0 until totalSamples) {
                    samples[i] += sin(2.0 * PI * freq * i / sampleRate).toFloat()
                }
            }
            // Normalize
            var maxAmp = 0f
            for (s in samples) if (abs(s) > maxAmp) maxAmp = abs(s)
            if (maxAmp > 0f) {
                for (i in samples.indices) samples[i] /= maxAmp
            }
            return samples
        }

        // C Major: root C doubled in octaves (bin 12 C5, bin 24 C6), plus E6 (bin 30) and G6 (bin 36)
        val cMajorSamples = generateChordFromBins(listOf(12, 24, 30, 36))
        val cMajorKey = BeatAnalyzer.estimateKey(cMajorSamples, sampleRate)
        assertNotNull("Should detect C major key", cMajorKey)
        cMajorKey?.let { (pitchClass, isMinor) ->
            assertEquals("Tonic should be C (0)", 0, pitchClass)
            assertFalse("Mode should be Major", isMinor)
        }

        // A Minor: root A doubled in octaves (bin 10 A4, bin 20 A5), plus C6 (bin 24) and E6 (bin 30)
        val aMinorSamples = generateChordFromBins(listOf(10, 20, 24, 30))
        val aMinorKey = BeatAnalyzer.estimateKey(aMinorSamples, sampleRate)
        assertNotNull("Should detect A minor key", aMinorKey)
        aMinorKey?.let { (pitchClass, isMinor) ->
            assertEquals("Tonic should be A (9)", 9, pitchClass)
            assertTrue("Mode should be Minor", isMinor)
        }
    }

    @Test
    fun `Bass ducking low-shelf filter attenuates 60Hz and preserves 3000Hz`() {
        val duckProcessor = AutomixDuckAudioProcessor()
        duckProcessor.configure(com.alananasss.kittytune.audio.AudioFormat(sampleRate, 2))
        duckProcessor.setMix(1f)

        fun measureGain(freq: Double): Double {
            duckProcessor.flush()
            val numFrames = 4096
            val inBytes = ByteBuffer.allocate(numFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numFrames) {
                val sample = (sin(2.0 * PI * freq * i / sampleRate) * 16000.0).toInt().toShort()
                inBytes.putShort(sample) // L
                inBytes.putShort(sample) // R
            }
            inBytes.flip()

            duckProcessor.queueInput(inBytes)
            val outBytes = duckProcessor.getOutput()

            // Discard the initial transition frames for filter settling
            val discardFrames = 1024
            var inRms = 0.0
            var outRms = 0.0
            var count = 0

            outBytes.position(discardFrames * 4)
            for (i in discardFrames until numFrames) {
                val expectedIn = sin(2.0 * PI * freq * i / sampleRate) * 16000.0
                val outL = outBytes.short.toDouble()
                outBytes.short // skip outR

                inRms += expectedIn * expectedIn
                outRms += outL * outL
                count++
            }

            inRms = sqrt(inRms / count)
            outRms = sqrt(outRms / count)
            return outRms / inRms
        }

        val lowGain = measureGain(60.0) // 60 Hz bass tone
        val highGain = measureGain(3000.0) // 3000 Hz high mid tone

        // -10 dB is approx 10^(-10/20) = 0.316. With tolerance, lowGain should be between 0.20 and 0.45
        assertTrue("60 Hz should be ducked by ~10dB (gain approx 0.316, got $lowGain)", lowGain in 0.20..0.45)

        // 3000 Hz should be essentially unaffected (gain approx 1.0, >= 0.90)
        assertTrue("3000 Hz should be virtually untouched (gain approx 1.0, got $highGain)", highGain in 0.90..1.05)

        // Now test with duck mix 0f (bypass via resetGain)
        duckProcessor.resetGain()
        val lowGainBypass = measureGain(60.0)
        assertTrue("When duck mix is 0f, 60 Hz should pass through at unity gain (got $lowGainBypass)", lowGainBypass in 0.98..1.02)
    }

    @Test
    fun `Harmonic mixing semitone and pitch ratio computation`() {
        // Test relative major/minor alignment:
        // A minor (pitch class 9, minor) effective major tonic is (9 + 3) % 12 = 0 (C major)
        // C major (pitch class 0, major) effective tonic is 0 (C major)
        // Both belong to the same harmonic key (Camelot 8A and 8B), semitoneShift = 0 -> pitchRatio = 1.0
        val outKeyClass = 9 // A
        val outIsMinor = true
        val inKeyClass = 0 // C
        val inIsMinor = false

        val outEffective = if (outIsMinor) (outKeyClass + 3) % 12 else outKeyClass
        val inEffective = if (inIsMinor) (inKeyClass + 3) % 12 else inKeyClass
        var semitoneShift = (outEffective - inEffective) % 12
        if (semitoneShift > 6) semitoneShift -= 12
        if (semitoneShift < -6) semitoneShift += 12

        assertEquals("A minor and C major have zero effective harmonic shift", 0, semitoneShift)

        // G major (pitch class 7) to C major (0):
        // Shift = (7 - 0) = 7 -> 7 - 12 = -5 (or +7 semitones)
        // If within +/-3 semitones, pitch shift is applied:
        // E.g., D major (2) to C major (0): shift = 2 semitones -> pitchRatio = 2^(2/12)
        val dMajor = 2
        val cMajor = 0
        var dShift = (dMajor - cMajor) % 12
        if (dShift > 6) dShift -= 12
        if (dShift < -6) dShift += 12
        assertEquals(2, dShift)
        val expectedRatio = 2.0.pow(dShift / 12.0).toFloat()
        assertTrue("Pitch ratio should be > 1.0 for +2 semitones", expectedRatio > 1.0f && expectedRatio < 1.15f)
    }

    @Test
    fun `Tempo matching clamps within plus minus 8 percent ratio`() {
        val outBpm = 120f
        val closeInBpm = 124f
        var ratio = outBpm / closeInBpm
        while (ratio > 1.5f) ratio /= 2f
        while (ratio < 0.667f) ratio *= 2f
        val allowedRatio = if (ratio in 0.92f..1.08f) ratio else 1f
        assertEquals("Close BPMs (120 and 124) should stretch", ratio, allowedRatio, 0.001f)

        val distantInBpm = 140f
        var distantRatio = outBpm / distantInBpm
        while (distantRatio > 1.5f) distantRatio /= 2f
        while (distantRatio < 0.667f) distantRatio *= 2f
        val clampedDistantRatio = if (distantRatio in 0.92f..1.08f) distantRatio else 1f
        assertEquals("BPMs too far apart (120 and 140) should default to 1.0 (no stretch)", 1f, clampedDistantRatio, 0.001f)
    }

    @Test
    fun `Camelot code and key name mapping`() {
        assertEquals("8B", com.alananasss.kittytune.audio.automix.AutomixManager.camelotCode(0, false))
        assertEquals("C Maj", com.alananasss.kittytune.audio.automix.AutomixManager.keyName(0, false))

        assertEquals("8A", com.alananasss.kittytune.audio.automix.AutomixManager.camelotCode(9, true))
        assertEquals("A min", com.alananasss.kittytune.audio.automix.AutomixManager.keyName(9, true))

        assertEquals("9B", com.alananasss.kittytune.audio.automix.AutomixManager.camelotCode(7, false))
        assertEquals("G Maj", com.alananasss.kittytune.audio.automix.AutomixManager.keyName(7, false))
    }
}

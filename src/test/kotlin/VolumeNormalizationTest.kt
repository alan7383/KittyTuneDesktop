package com.alananasss.kittytune

import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.audio.NormalizationAudioProcessor
import com.alananasss.kittytune.data.AudioScannerManager
import com.alananasss.kittytune.data.TrackLoudnessRepository
import com.alananasss.kittytune.ui.player.NormalizationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class VolumeNormalizationTest {

    private val sampleRate = 44100
    private val channels = 2
    private val chunkSize = 2048 // frames per chunk (~46.4 ms)

    @Before
    fun setUp() {
        NormalizationAudioProcessor.loadNativeLibrary()
    }

    private fun createProcessor(level: NormalizationLevel = NormalizationLevel.NORMAL): NormalizationAudioProcessor {
        val proc = NormalizationAudioProcessor()
        proc.setParameters(enabled = true, level = level)
        proc.configure(AudioFormat(sampleRate, channels))
        proc.flush()
        return proc
    }

    private fun generateTone(frames: Int, amplitude: Float, freqHz: Double = 440.0): ByteBuffer {
        val bytes = frames * channels * 2
        val buf = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until frames) {
            val v = (amplitude * sin(2.0 * Math.PI * freqHz * f / sampleRate) * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            buf.putShort(v)
            buf.putShort(v)
        }
        buf.flip()
        return buf
    }

    private fun generateSilence(frames: Int): ByteBuffer {
        val bytes = frames * channels * 2
        val buf = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until frames * channels) {
            buf.putShort(0)
        }
        buf.flip()
        return buf
    }

    private fun measureRmsDb(buf: ByteBuffer): Float {
        val count = buf.remaining() / 2
        if (count == 0) return -120f
        val sb = buf.asShortBuffer()
        var sumSq = 0.0
        val pos = sb.position()
        for (i in 0 until count) {
            val s = sb.get(pos + i).toDouble() / 32768.0
            sumSq += s * s
        }
        val rms = sqrt(sumSq / count)
        if (rms <= 1e-9) return -120f
        return (20.0 * log10(rms)).toFloat()
    }

    private fun measurePeak(buf: ByteBuffer): Float {
        val count = buf.remaining() / 2
        if (count == 0) return 0f
        val sb = buf.asShortBuffer()
        var m = 0f
        val pos = sb.position()
        for (i in 0 until count) {
            val a = abs(sb.get(pos + i).toFloat() / 32768f)
            if (a > m) m = a
        }
        return m
    }

    @Test
    fun `loud song does not start loud and then collapse in volume`() {
        // A hot master: ~ -8 LUFS (amplitude 0.6)
        // With NormalizationLevel.NORMAL (-14 LUFS), required attenuation is ~ -6 dB.
        // In the broken implementation, chunk 0 played at 0 dB (or boosted), and after 3s dropped by 6-10 dB!
        val proc = createProcessor(NormalizationLevel.NORMAL)

        val inputRms = measureRmsDb(generateTone(chunkSize, 0.6f))

        // Process 3.5 seconds in chunks
        val totalChunks = (sampleRate * 3.5 / chunkSize).toInt()
        val chunkRmsList = mutableListOf<Float>()
        val gainDbList = mutableListOf<Float>()

        for (i in 0 until totalChunks) {
            val inBuf = generateTone(chunkSize, 0.6f)
            proc.queueInput(inBuf)
            val outBuf = proc.getOutput()
            chunkRmsList.add(measureRmsDb(outBuf))
            gainDbList.add(proc.getCurrentGainDb())
        }

        val firstChunkRms = chunkRmsList.first()
        val steadyStateRms = chunkRmsList.last()
        val firstChunkGain = gainDbList.first()
        val steadyStateGain = gainDbList.last()

        println("Loud Song Test: Input RMS = ${inputRms}dB")
        println("Chunk 0: RMS = ${firstChunkRms}dB, Gain = ${firstChunkGain}dB")
        println("Steady state: RMS = ${steadyStateRms}dB, Gain = ${steadyStateGain}dB")

        // 1. Chunk 0 must already be attenuated, NOT played at 0 dB gain or louder!
        assertTrue("Chunk 0 gain ($firstChunkGain dB) must be attenuating (< -3.0 dB)", firstChunkGain < -3.0f)
        assertTrue("Chunk 0 RMS ($firstChunkRms dB) must be attenuated relative to input ($inputRms dB)", firstChunkRms < inputRms - 3.0f)

        // 2. The difference between chunk 0 and steady state must be minimal (no sudden volume drop!)
        val volumeDrop = firstChunkRms - steadyStateRms
        assertTrue("Volume drop ($volumeDrop dB) between start and steady state must be small (<= 2.5 dB)", volumeDrop <= 2.5f)
    }

    @Test
    fun `silence before music does not prime the gain to blast when music hits`() {
        // Track starts with 1.5 seconds of silence, then a loud track hits (-8 LUFS)
        val proc = createProcessor(NormalizationLevel.NORMAL)

        val silenceChunks = (sampleRate * 1.5 / chunkSize).toInt()
        for (i in 0 until silenceChunks) {
            val inBuf = generateSilence(chunkSize)
            proc.queueInput(inBuf)
            proc.getOutput()
            // Gain must NOT be boosted during silence!
            val gain = proc.getCurrentGainDb()
            assertTrue("Gain during silence ($gain dB) must not exceed 0 dB", gain <= 0.05f)
        }

        // Now music hits
        val inMusic = generateTone(chunkSize, 0.7f)
        proc.queueInput(inMusic)
        val outMusic = proc.getOutput()

        val firstMusicRms = measureRmsDb(outMusic)
        val firstMusicPeak = measurePeak(outMusic)
        val gainAtHit = proc.getCurrentGainDb()

        println("Silence-to-Music Test: Gain at hit = ${gainAtHit}dB, Peak = $firstMusicPeak, RMS = ${firstMusicRms}dB")

        // Must not blast or clip
        assertTrue("Output must not clip when music hits after silence", firstMusicPeak <= 1.0f)
        assertTrue("Gain ($gainAtHit dB) must remain safe and attenuating", gainAtHit <= -3.0f)
    }

    @Test
    fun `quiet song is boosted cleanly without clipping`() {
        // Quiet acoustic song: amplitude 0.08 (~ -25 LUFS)
        // With -14 LUFS target, should be boosted
        val proc = createProcessor(NormalizationLevel.NORMAL)

        val totalChunks = (sampleRate * 3.0 / chunkSize).toInt()
        var lastRms = -120f
        var lastPeak = 0f
        for (i in 0 until totalChunks) {
            val inBuf = generateTone(chunkSize, 0.08f)
            proc.queueInput(inBuf)
            val outBuf = proc.getOutput()
            lastRms = measureRmsDb(outBuf)
            lastPeak = measurePeak(outBuf)
        }

        val gain = proc.getCurrentGainDb()
        println("Quiet Song Test: Final Gain = ${gain}dB, RMS = ${lastRms}dB, Peak = $lastPeak")

        assertTrue("Quiet track should receive positive gain", gain > 0f)
        assertTrue("Peak must stay comfortably below 1.0", lastPeak < 0.95f)
    }

    @Test
    fun `pre-scanned track loudness applies exact static gain from frame zero`() {
        // When track loudness is known (-8.5 LUFS), target is -14 LUFS:
        // Gain must be exact -5.5 dB at chunk 0 and remain completely static
        val proc = createProcessor(NormalizationLevel.NORMAL)
        proc.setTrackLoudness(-8.5f, -0.5f)

        val gains = mutableListOf<Float>()
        for (i in 0 until 20) {
            val inBuf = generateTone(chunkSize, 0.5f)
            proc.queueInput(inBuf)
            proc.getOutput()
            gains.add(proc.getCurrentGainDb())
        }

        println("Known Track Test: Chunk 0 gain = ${gains.first()}dB, Chunk 19 gain = ${gains.last()}dB")
        assertEquals(-5.5f, gains.first(), 0.1f)
        assertEquals(-5.5f, gains.last(), 0.1f)
    }

    @Test
    fun `seeking does not cause volume blast or gain instability`() {
        val proc = createProcessor(NormalizationLevel.NORMAL)

        // Play for 1 second of loud audio
        for (i in 0 until (sampleRate * 1.0 / chunkSize).toInt()) {
            val inBuf = generateTone(chunkSize, 0.6f)
            proc.queueInput(inBuf)
            proc.getOutput()
        }

        // Simulate seek (flush)
        proc.flush()

        // First chunk after seek
        val inBufAfterSeek = generateTone(chunkSize, 0.6f)
        proc.queueInput(inBufAfterSeek)
        val outAfterSeek = proc.getOutput()

        val gainAfterSeek = proc.getCurrentGainDb()
        val rmsAfterSeek = measureRmsDb(outAfterSeek)

        println("Seek Test: Gain after seek = ${gainAfterSeek}dB, RMS after seek = ${rmsAfterSeek}dB")
        assertTrue("Gain after seek ($gainAfterSeek dB) must stay attenuating", gainAfterSeek < -3.0f)
    }

    @Test
    fun `different normalization levels scale volume accordingly`() {
        val quietProc = createProcessor(NormalizationLevel.QUIET)   // -19 LUFS
        val normalProc = createProcessor(NormalizationLevel.NORMAL) // -14 LUFS
        val loudProc = createProcessor(NormalizationLevel.LOUD)     // -11 LUFS

        val numChunks = (sampleRate * 2.0 / chunkSize).toInt()
        var quietRms = 0f
        var normalRms = 0f
        var loudRms = 0f

        for (i in 0 until numChunks) {
            val qIn = generateTone(chunkSize, 0.4f)
            quietProc.queueInput(qIn)
            quietRms = measureRmsDb(quietProc.getOutput())

            val nIn = generateTone(chunkSize, 0.4f)
            normalProc.queueInput(nIn)
            normalRms = measureRmsDb(normalProc.getOutput())

            val lIn = generateTone(chunkSize, 0.4f)
            loudProc.queueInput(lIn)
            loudRms = measureRmsDb(loudProc.getOutput())
        }

        println("Levels Test: Quiet = ${quietRms}dB, Normal = ${normalRms}dB, Loud = ${loudRms}dB")
        assertTrue("Quiet must be quieter than Normal", quietRms < normalRms - 3.0f)
        assertTrue("Normal must be quieter than Loud", normalRms < loudRms - 1.5f)
    }

    @Test
    fun `peak limiter strictly guarantees output never exceeds full scale`() {
        val proc = createProcessor(NormalizationLevel.LOUD)
        // Feed an excessively hot signal (+6 dB over full scale)
        val hotIn = generateTone(sampleRate * 2, 2.0f)
        val chunkCount = hotIn.remaining() / (chunkSize * channels * 2)

        for (i in 0 until chunkCount) {
            val slice = ByteBuffer.allocateDirect(chunkSize * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (j in 0 until chunkSize * channels) {
                slice.putShort(hotIn.getShort())
            }
            slice.flip()
            proc.queueInput(slice)
            val out = proc.getOutput()
            val peak = measurePeak(out)
            assertTrue("Peak ($peak) must never exceed 1.0", peak <= 1.0f)
        }
    }

    @Test
    fun `audio scanner accurately analyzes real audio files`() {
        val files = listOf(
            File("src/main/resources/raw/rain.mp3"),
            File("src/main/resources/raw/ocean.mp3"),
            File("src/main/resources/raw/cafe.mp3"),
            File("src/main/resources/raw/fireplace.mp3")
        )

        for (file in files) {
            if (!file.exists()) continue
            val result = AudioScannerManager.scanAudioFile(file)
            println("Scanned ${file.name}: LUFS = ${result?.integratedLufs}, TruePeak = ${result?.truePeakDb}dBTP")
            assertTrue("Scan result for ${file.name} must not be null", result != null)
            assertTrue("LUFS must be between -45 and -5 LUFS", result!!.integratedLufs in -45f..-5f)
            assertTrue("True peak must be <= 3.0 dBTP", result.truePeakDb <= 3.0f)
        }
    }

    @Test
    fun `track loudness repository caches and persists scan results`() {
        TrackLoudnessRepository.saveLoudness(999991L, -13.5f, -0.8f)
        val cached = TrackLoudnessRepository.getLoudness(999991L)
        assertTrue("Loudness must be retrievable", cached != null)
        assertEquals(-13.5f, cached!!.integratedLufs, 0.05f)
        assertEquals(-0.8f, cached.truePeakDb, 0.05f)
    }
}

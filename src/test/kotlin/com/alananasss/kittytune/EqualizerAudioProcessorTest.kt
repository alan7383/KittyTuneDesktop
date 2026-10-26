package com.alananasss.kittytune

import com.alananasss.kittytune.audio.AudioFormat
import com.alananasss.kittytune.audio.EqualizerAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerAudioProcessorTest {

    private val frames = 6_000
    private val sampleRate = 44_100

    @Test
    fun disabledEqualizerIsBitPerfect() {
        val input = sineBuffer(1_000)
        val expected = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val processor = EqualizerAudioProcessor().apply {
            configure(AudioFormat(sampleRate, 2))
            setParameters(false, 0f, FloatArray(16))
            queueInput(input)
        }
        val output = processor.getOutput()

        assertEquals(expected.remaining(), output.remaining())
        while (expected.hasRemaining()) {
            assertEquals(expected.short, output.short)
        }
    }

    @Test
    fun boostLiftsTheSelectedBand() {
        val measured = rmsAfterProcessing(3_150f, 12f)
        assertTrue("Boost should raise the 3.15 kHz band (was $measured)", measured > 860)
    }

    @Test
    fun cutLowersTheSelectedBand() {
        val measured = rmsAfterProcessing(3_150f, -12f)
        assertTrue("Cut should lower the 3.15 kHz band (was $measured)", measured < 700)
    }

    private fun rmsAfterProcessing(frequency: Float, gainDb: Float): Double {
        val gains = FloatArray(16)
        val band = EqualizerAudioProcessor.BAND_FREQUENCIES.indexOfFirst { it == 3_150f }
        gains[band] = gainDb
        val processor = EqualizerAudioProcessor().apply {
            configure(AudioFormat(sampleRate, 2))
            setParameters(true, 0f, gains)
            queueInput(sineBuffer(1_000, frequency))
        }
        val output = processor.getOutput()
        val values = ShortArray(frames * 2)
        output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(values)

        // Skip the filter's startup transient, then measure the left channel.
        var sum = 0.0
        var count = 0
        for (index in 1_500 until 5_500) {
            val sample = values[index * 2].toDouble()
            sum += sample * sample
            count++
        }
        return sqrt(sum / count)
    }

    private fun sineBuffer(amplitude: Int, frequency: Float = 3_150f): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (frame in 0 until frames) {
            val sample = (amplitude * sin(2.0 * PI * frequency * frame / sampleRate)).toInt().toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        buffer.flip()
        return buffer
    }
}

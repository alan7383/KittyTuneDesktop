package com.alananasss.kittytune.audio

import com.alananasss.kittytune.audio.silence.SilenceSkipAudioProcessor
import com.alananasss.kittytune.audio.silence.SkipSilence
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SilenceSkipTest {

    private val rate = 44_100

    @After
    fun tearDown() = SkipSilence.set(skipSilence = false, instant = false)

    /** Stereo 16-bit PCM: [ms] milliseconds at constant amplitude [level]. */
    private fun pcm(ms: Int, level: Short): ByteBuffer {
        val frames = rate * ms / 1000
        val b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { b.putShort(level); b.putShort((-level).toShort()) }
        b.flip()
        return b
    }

    private fun SilenceSkipAudioProcessor.run(input: ByteBuffer): Int {
        queueInput(input)
        assertEquals("input fully consumed", 0, input.remaining())
        return getOutput().remaining()
    }

    private fun processor() = SilenceSkipAudioProcessor().apply { configure(AudioFormat(rate, 2)) }

    @Test
    fun `everything passes when skip silence is off`() {
        val p = processor()
        assertEquals(rate * 4 * 3, p.run(pcm(3_000, 0)))
    }

    @Test
    fun `a long silence keeps its first 150 ms and loses the rest`() {
        SkipSilence.set(skipSilence = true, instant = false)
        val p = processor()
        val kept = p.run(pcm(3_000, 0))
        assertEquals(rate * 150 / 1000 * 4, kept)
    }

    @Test
    fun `short pauses and music are left untouched`() {
        SkipSilence.set(skipSilence = true, instant = false)
        val p = processor()
        assertEquals(rate * 100 / 1000 * 4, p.run(pcm(100, 0)))
        assertEquals(rate * 4, p.run(pcm(1_000, 8_000)))
        // The music reset the run: a new short pause plays in full again.
        assertEquals(rate * 100 / 1000 * 4, p.run(pcm(100, 0)))
    }

    @Test
    fun `instant mode reports a long silence once per run`() {
        SkipSilence.set(skipSilence = true, instant = true)
        val p = processor()
        var calls = 0
        p.onLongSilence = { calls++ }
        p.run(pcm(1_500, 0))
        assertFalse(p.isCurrentlySilent())
        p.run(pcm(1_000, 0))
        assertTrue(p.isCurrentlySilent())
        p.run(pcm(3_000, 0))
        assertEquals(1, calls)
        p.run(pcm(200, 8_000))
        assertFalse(p.isCurrentlySilent())
        p.run(pcm(2_500, 0))
        assertEquals(2, calls)
    }

    @Test
    fun `instant mode needs skip silence on`() {
        SkipSilence.set(skipSilence = false, instant = true)
        assertFalse(SkipSilence.instantEnabled)
    }

    /** A track that is silent from 60 s to 120 s, decoded on every seek through [processor]. */
    private class FakeTrack(val processor: SilenceSkipAudioProcessor, val rate: Int) : SkipSilence.Target {
        override val activeProcessor get() = processor
        override var positionMs = 61_000L
        override val durationMs = 200_000L
        val seeks = mutableListOf<Long>()
        override fun seekTo(positionMs: Long) {
            this.positionMs = positionMs
            seeks += positionMs
            processor.flush()
            val level: Short = if (positionMs in 60_000L until 120_000L) 0 else 8_000
            val frames = rate / 10
            val b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
            repeat(frames) { b.putShort(level); b.putShort(level) }
            b.flip()
            processor.queueInput(b)
            processor.getOutput()
        }
    }

    @Test
    fun `jumps until sound is found again`() = runBlocking {
        SkipSilence.set(skipSilence = true, instant = true)
        val p = processor()
        p.run(pcm(2_500, 0))
        assertTrue(p.isCurrentlySilent())
        val track = FakeTrack(p, rate)
        val hops = SkipSilence.jumpOverSilence(track, p)
        // 61 s -> 76 -> 91 -> 106 (still silent) -> 121 (sound): four jumps, then it stops.
        assertEquals(listOf(76_000L, 91_000L, 106_000L, 121_000L), track.seeks)
        assertEquals(4, hops)
    }

    @Test
    fun `a deck that is no longer playing is not moved`() = runBlocking {
        SkipSilence.set(skipSilence = true, instant = true)
        val p = processor()
        p.run(pcm(2_500, 0))
        val other = processor()
        val track = object : SkipSilence.Target {
            override val activeProcessor = other
            override val positionMs = 61_000L
            override val durationMs = 200_000L
            override fun seekTo(positionMs: Long) = error("must not seek")
        }
        assertEquals(0, SkipSilence.jumpOverSilence(track, p))
    }
}

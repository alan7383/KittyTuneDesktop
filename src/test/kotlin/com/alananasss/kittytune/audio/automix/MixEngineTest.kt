package com.alananasss.kittytune.audio.automix

import com.alananasss.kittytune.audio.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class MixEngineTest {

    @Test
    fun `tempo match folds octaves and refuses big stretches`() {
        val (canMatch, ratio) = MixTempoMatch.match(126f, 124f)
        assertTrue(canMatch)
        assertEquals(126f / 124f, ratio, 1e-4f)
        // 70 against 140 locks at double time.
        assertEquals(true to 1f, MixTempoMatch.match(140f, 70f))
        // 75 against 84 is within 10 BPM but a 12 % stretch: refused.
        assertFalse(MixTempoMatch.match(75f, 84f).first)
        assertFalse(MixTempoMatch.match(120f, 0f).first)
    }

    @Test
    fun `plain crossfades keep their own shape`() {
        assertNull(CrossfadeShaper.shape(null, null, 0.5f, 16, 120f))
    }

    @Test
    fun `blend hands the lows over at the midpoint`() {
        val before = CrossfadeShaper.shape(MixTransitionPresets.MixTransitionStyle.BLEND, null, 0.45f, 16, 120f)!!
        val after = CrossfadeShaper.shape(MixTransitionPresets.MixTransitionStyle.BLEND, null, 0.55f, 16, 120f)!!
        assertTrue("outgoing keeps some lows before the swap", before.duckOut < 1f)
        assertEquals("outgoing gives all its lows away after the swap", 1f, after.duckOut, 0f)
        assertTrue("incoming opens its lows", after.duckIn < before.duckIn)
    }

    @Test
    fun `rise sweeps a high pass on the outgoing deck only`() {
        val c = CrossfadeShaper.shape(MixTransitionPresets.MixTransitionStyle.RISE, null, 0.3f, 8, 128f)!!
        assertEquals(MixSweepAudioProcessor.FilterMode.HIGH_PASS, c.sweepModeOut)
        assertEquals(MixSweepAudioProcessor.FilterMode.BYPASS, c.sweepModeIn)
        assertNotNull(c.sweepHzOut)
        assertNull(c.sweepHzIn)
    }

    @Test
    fun `melt never asks for a stopped deck`() {
        val end = CrossfadeShaper.shape(MixTransitionPresets.MixTransitionStyle.MELT, null, 1f, 4, 120f)!!
        assertTrue(end.speedOut >= CrossfadeShaper.MIN_MELT_SPEED)
        val start = CrossfadeShaper.shape(MixTransitionPresets.MixTransitionStyle.MELT, null, 0f, 4, 120f)!!
        assertEquals(1f, start.speedOut, 1e-4f)
    }

    @Test
    fun `a custom seam plays its own curves`() {
        val plan = TransitionPlan().withPresetSelected(TransitionPreset.CUSTOM)
        val c = CrossfadeShaper.shape(MixTransitionPresets.MixTransitionStyle.BLEND, plan, 0.5f, 16, 120f)!!
        val f = planFrameAt(plan, 0.5f, 16, 120f)
        assertEquals(f.fadeOut, c.fadeOut, 0f)
        assertEquals(f.fadeIn, c.fadeIn, 0f)
    }

    @Test
    fun `transition beats follow the tempo and fall back to the style phrase`() {
        assertEquals(16, CrossfadeShaper.beatsFor(8_000L, 120f, null))
        assertEquals(MixTransitionPresets.MixTransitionStyle.RISE.defaultPhraseBeats,
            CrossfadeShaper.beatsFor(8_000L, 0f, MixTransitionPresets.MixTransitionStyle.RISE))
    }

    @Test
    fun `saved plans survive a round trip`() {
        val plan = TransitionPlan().withPresetSelected(TransitionPreset.CUSTOM)
        val back = TransitionPlan.deserialize(plan.serialize())
        assertEquals(plan, back)
    }

    @Test
    fun `plan lookup tries every route key, most specific first`() {
        assertEquals(
            listOf("123", "playlist_detail:123", "global"),
            TransitionStore.candidateKeys("123", 123L),
        )
        assertEquals(listOf("likes", "playlist_detail:likes", "global"), TransitionStore.candidateKeys("likes"))
        assertEquals(listOf("global"), TransitionStore.candidateKeys(null))
    }

    @Test
    fun `duck shelf treats both channels the same`() {
        val p = AutomixDuckAudioProcessor()
        p.configure(AudioFormat(44_100, 2))
        p.setMix(1f)
        val frames = 8_192
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frames) {
            val s = (sin(2.0 * PI * 60.0 * i / 44_100.0) * 16_000.0).toInt().toShort()
            input.putShort(s); input.putShort(s)
        }
        input.flip()
        p.queueInput(input)
        val out = p.getOutput()
        var maxDiff = 0
        while (out.remaining() >= 4) {
            val l = out.getShort().toInt()
            val r = out.getShort().toInt()
            maxDiff = maxOf(maxDiff, abs(l - r))
        }
        assertEquals("left and right must stay identical", 0, maxDiff)
    }
}

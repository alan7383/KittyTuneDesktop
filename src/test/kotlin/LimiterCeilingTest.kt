package com.alananasss.kittytune

import com.alananasss.kittytune.audio.CEILING_EXEMPT_TOGGLES
import com.alananasss.kittytune.audio.PeakLimiter
import com.alananasss.kittytune.audio.effectToggles
import com.alananasss.kittytune.audio.peakLimiterCeilingFor
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.NormalizationLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The limiter's ceiling policy. What these protect: a fixed -2 dBFS ceiling meant the limiter
 * rode ~2 dB of gain reduction on every master that peaks at full scale — most of them — even
 * with nothing downstream that could ever need that headroom.
 */
class LimiterCeilingTest {

    @Test
    fun `nothing on means the transparent ceiling`() {
        assertEquals(PeakLimiter.CEILING_TRANSPARENT, peakLimiterCeilingFor(AudioEffectsState()), 0f)
    }

    @Test
    fun `normalisation alone stays transparent because the dsp limits its own output`() {
        val state = AudioEffectsState(
            isNormalizationEnabled = true,
            normalizationLevel = NormalizationLevel.LOUD
        )
        assertEquals(PeakLimiter.CEILING_TRANSPARENT, peakLimiterCeilingFor(state), 0f)
    }

    @Test
    fun `the pitch mode flag is not an effect`() {
        // isPitchEnabled only says whether pitch follows speed; at 1x neither stage does work.
        val state = AudioEffectsState(isPitchEnabled = true, speed = 1f)
        assertEquals(PeakLimiter.CEILING_TRANSPARENT, peakLimiterCeilingFor(state), 0f)
    }

    @Test
    fun `an effect buys headroom`() {
        listOf(
            AudioEffectsState(isReverbEnabled = true),
            AudioEffectsState(isBassBoostEnabled = true),
            AudioEffectsState(isEarrapeEnabled = true),
            AudioEffectsState(isMonoEnabled = true)
        ).forEach { state ->
            assertEquals(PeakLimiter.CEILING_SAFE, peakLimiterCeilingFor(state), 0f)
        }
    }

    @Test
    fun `off-speed playback buys headroom`() {
        assertEquals(PeakLimiter.CEILING_SAFE, peakLimiterCeilingFor(AudioEffectsState(speed = 1.25f)), 0f)
        assertEquals(PeakLimiter.CEILING_SAFE, peakLimiterCeilingFor(AudioEffectsState(speed = 0.8f)), 0f)
    }

    /**
     * The reflective filter is the part that keeps this correct as effects are added, so check
     * it against the state class itself rather than against a number written down here.
     */
    @Test
    fun `every effect toggle is covered except the exempt ones`() {
        val allToggles = AudioEffectsState::class.java.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("is") && it.name.endsWith("Enabled") }
            .map { it.name }
            .toSet()

        assertTrue("state class should expose effect toggles", allToggles.size > 20)
        assertTrue(allToggles.containsAll(CEILING_EXEMPT_TOGGLES))

        val covered = effectToggles.map { it.name }.toSet()
        assertEquals(allToggles - CEILING_EXEMPT_TOGGLES, covered)
    }

    @Test
    fun `the transparent ceiling is the higher of the two and both are below full scale`() {
        assertTrue(PeakLimiter.CEILING_TRANSPARENT > PeakLimiter.CEILING_SAFE)
        assertTrue(PeakLimiter.CEILING_TRANSPARENT < 1f)
    }
}

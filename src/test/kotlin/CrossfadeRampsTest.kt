package com.alananasss.kittytune

import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.media.CROSSFADE_SPAN_END
import com.alananasss.kittytune.media.CROSSFADE_SPAN_START
import com.alananasss.kittytune.media.equalPowerIn
import com.alananasss.kittytune.media.equalPowerOut
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The crossfade's two gain ramps, and the progress the engine publishes for the seek bar's mix glide.
 *
 * Kept from the cover glow's tests when the glow went (issue #66): the seek bar reads the same progress.
 */
class CrossfadeRampsTest {

    private val sample = List(101) { it / 100f }

    @Test
    fun testEnginePublishesItsOwnProgress() {
        val player = source("media/PlayerCompat.kt")
        assertTrue(
            player.contains("AutomixManager.setMixProgress(progress)"),
            "The loop already had the progress; it has to publish it rather than be re-derived",
        )
        // The fade ends by every route: finished, interrupted, or the track ended underneath it.
        assertTrue(player.contains("AutomixManager.setMixProgress(0f)"), "A fade that is over must say so")
        assertTrue(
            AutomixManager.mixProgress.value >= 0f && AutomixManager.mixProgress.value <= 1f,
            "The published progress is a fraction, not a millisecond",
        )
    }

    /**
     * The mix has to hold its level through the whole fade: with an equal-power crossfade the sum of the
     * two gains squared is constant. Two ramps over different spans sagged by about 9 dB halfway through.
     */
    @Test
    fun testTheMixHoldsItsLevelThroughTheFade() {
        val power = sample.map { p ->
            val o = equalPowerOut(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, p)
            val i = equalPowerIn(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, p)
            o * o + i * i
        }
        val spread = power.max() - power.min()
        assertTrue(spread < 0.01f, "The summed level moved by $spread across the fade")

        val mismatched = sample.map { p ->
            val o = equalPowerOut(0f, 0.6f, p)
            val i = equalPowerIn(0.4f, 1f, p)
            o * o + i * i
        }
        assertTrue(1f - mismatched.min() > 0.5f, "Ramps over different spans must still sag")
    }

    @Test
    fun testTheTwoRampsShareOneSpan() {
        val player = source("media/PlayerCompat.kt")
        assertTrue(player.contains("internal const val CROSSFADE_SPAN_START"))
        assertTrue(player.contains("internal const val CROSSFADE_SPAN_END"))
        assertTrue(
            player.contains("equalPowerOut(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, progress)"),
            "The engine must use the shared span it publishes",
        )
        assertTrue(CROSSFADE_SPAN_START < 0.5f && CROSSFADE_SPAN_END > 0.5f)
        val out = equalPowerOut(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, 0.5f)
        val inn = equalPowerIn(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, 0.5f)
        assertEquals(out, inn, 0.02f, "The two tracks are equally loud at the halfway point")
    }

    private fun source(path: String): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/$path")
        assertTrue(file.exists(), "$path should exist")
        return file.readText()
    }
}

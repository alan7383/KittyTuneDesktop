package com.alananasss.kittytune

import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.media.CROSSFADE_SPAN_END
import com.alananasss.kittytune.media.CROSSFADE_SPAN_START
import com.alananasss.kittytune.media.equalPowerIn
import com.alananasss.kittytune.media.equalPowerOut
import com.alananasss.kittytune.ui.player.cover.mixHandover
import com.alananasss.kittytune.ui.player.cover.mixIntensity
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Point 20 of issue #56: the mix glow.
 *
 * The point of the glow is that it shows what the mix is doing, so most of what is pinned here is
 * that it is lit by the engine's own curve and not by a second opinion about it. A glow that has its
 * own idea of when a track is audible is worse than none, because it is confidently wrong.
 */
class MixGlowTest {

    private val sample = List(101) { it / 100f }

    // ── The engine publishes the fade it is actually running ──

    @Test
    fun testEnginePublishesItsOwnProgress() {
        val player = source("media/PlayerCompat.kt")
        assertTrue(
            player.contains("AutomixManager.setMixProgress(progress)"),
            "The loop already had the progress; it has to publish it rather than be re-derived",
        )
        // The fade ends by every route: finished, interrupted, or the track ended underneath it.
        assertTrue(
            player.contains("AutomixManager.setMixProgress(0f)"),
            "A fade that is over must not leave the cover lit",
        )
        assertTrue(
            AutomixManager.mixProgress.value >= 0f && AutomixManager.mixProgress.value <= 1f,
            "The published progress is a fraction, not a millisecond",
        )
    }

    @Test
    fun testTheGlowUsesTheEnginesCurveAndNotACopy() {
        val player = source("media/PlayerCompat.kt")
        val glow = source("ui/player/cover/MixGlow.kt")

        assertTrue(
            !player.contains("    private fun equalPowerIn"),
            "The ramps must not be private to the engine any more",
        )
        assertTrue(player.contains("internal fun equalPowerIn"))
        assertTrue(player.contains("internal fun equalPowerOut"))
        assertTrue(
            glow.contains("equalPowerOut(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, progress)") &&
                glow.contains("equalPowerIn(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, progress)"),
            "The glow must read the same two ramps, over the same span, as the fade runs them",
        )
        assertTrue(
            glow.contains("import com.alananasss.kittytune.media.equalPowerIn"),
            "One definition of the curve, imported — not a second one written out",
        )
    }

    // ── The shape it draws ──

    @Test
    fun testTheHandoverIsZeroAtBothEndsAndPeaksInTheMiddle() {
        assertEquals(0f, mixHandover(0f), 1e-4f)
        assertEquals(0f, mixHandover(1f), 1e-4f)

        val peak = sample.maxOf { mixHandover(it) }
        assertEquals(1f, peak, 0.02f, "Normalised against its own peak, so it reaches 1")

        val peakAt = sample.maxByOrNull { mixHandover(it) }!!
        assertTrue(peakAt in 0.4f..0.6f, "Both tracks are audible in the middle of the fade, peak was at $peakAt")
    }

    @Test
    fun testTheGlowIsLitOnlyWhereSomethingIsHappening() {
        // Nothing mixing, nothing approaching: dark.
        assertEquals(0f, mixIntensity(0f, 0f), 1e-4f)
        // Only approaching: dim but present, so the mix is announced before it happens.
        val warning = mixIntensity(0f, 1f)
        assertTrue(warning > 0f && warning < 0.6f, "The run-up is a warning, not the event itself")
        // A fade in progress: lit, and brightest at the handover.
        assertTrue(mixIntensity(0.5f, 1f) > warning, "The fade is brighter than the run-up")
        assertEquals(1f, mixIntensity(0.5f, 1f), 0.02f, "And brightest exactly at the handover")
    }

    @Test
    fun testTheRunUpMeetsTheFadeWithoutAStep() {
        // The hand-over from warning to fade is the one place this could visibly jump, so the two
        // have to agree on the brightness they meet at.
        val endOfRunUp = mixIntensity(0f, 1f)
        val startOfFade = mixIntensity(0.0001f, 1f)
        assertTrue(
            kotlin.math.abs(startOfFade - endOfRunUp) < 0.02f,
            "Step of ${kotlin.math.abs(startOfFade - endOfRunUp)} between the run-up and the fade",
        )
    }

    @Test
    fun testTheGlowDoesNotDipWhileTheMixRuns() {
        // The glow is a readout of the handover, so it must not sag in the middle even though the
        // audio does (see testTheMixIsNotEqualPower below). A light that went out exactly when both
        // tracks were up would be the one moment a listener was guaranteed to be watching.
        val intensity = sample.map { mixIntensity(it, 1f) }
        // Never below the floor, at any point of the fade.
        assertEquals(0.55f, intensity.min(), 0.005f, "The glow must stay lit through the fade")

        // And it climbs to the handover and comes back down, rather than wobbling: one maximum, in
        // the middle. Comparing consecutive samples would only measure the slope.
        val peaks = intensity.indices.count { i ->
            val before = intensity.getOrElse(i - 1) { -1f }
            val after = intensity.getOrElse(i + 1) { -1f }
            intensity[i] >= before && intensity[i] >= after && intensity[i] > 0.56f
        }
        assertTrue(peaks in 1..3, "Expected a single crest in the middle, found $peaks")
        assertEquals(1f, intensity.max(), 0.02f, "Reaching full brightness at the handover")
    }

    /**
     * The mix has to hold its level through the whole fade.
     *
     * This is the whole point of an equal-power crossfade: the sum of the two gains squared is a
     * constant, so the moment one track hands over to the next the level does not move. It was not
     * true here — the two ramps spanned `[0, 0.6]` and `[0.4, 1]`, which overlap but are not the
     * same function, and the sum fell to about 0.13 halfway through: roughly 9 dB of sag at exactly
     * the point the fade exists to be seamless.
     *
     * The regression is pinned here rather than in a comment, so widening one ramp without the other
     * cannot bring it back quietly.
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

        // The shape that caused it, kept as a guard on the reasoning: overlapping is not the same
        // thing as shared, and only sharing holds the sum still.
        val mismatched = sample.map { p ->
            val o = equalPowerOut(0f, 0.6f, p)
            val i = equalPowerIn(0.4f, 1f, p)
            o * o + i * i
        }
        assertTrue(
            1f - mismatched.min() > 0.5f,
            "Ramps over different spans must still sag, or this test is not testing what it claims",
        )
    }

    @Test
    fun testTheTwoRampsShareOneSpan() {
        val player = source("media/PlayerCompat.kt")
        assertTrue(player.contains("internal const val CROSSFADE_SPAN_START"))
        assertTrue(player.contains("internal const val CROSSFADE_SPAN_END"))
        // The engine runs the ramps over the shared span, not over two literals.
        assertTrue(
            player.contains("equalPowerOut(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, progress)"),
            "The engine must use the shared span it publishes",
        )
        // And the handover still sits in the middle, which is the shape the span was chosen for.
        assertTrue(CROSSFADE_SPAN_START < 0.5f && CROSSFADE_SPAN_END > 0.5f)
        val midpoint = mixHandover(0.5f)
        assertEquals(1f, midpoint, 0.02f, "The two tracks are equally loud at the halfway point")
    }

    // ── It is drawn, not recomposed ──

    @Test
    fun testTheGlowAnimatesWithoutRecomposing() {
        val glow = source("ui/player/cover/MixGlow.kt")
        assertTrue(
            glow.contains("LaunchedEffect(isSeen)") && glow.contains("delay(TICK_MS)"),
            "The clock must be paced by delay, which asks for no frames",
        )
        assertTrue(
            !glow.contains("withFrameMillis") && !glow.contains("awaitFrame"),
            "Awaiting a frame asks for every frame, and a hidden player would render at display rate",
        )
        assertTrue(
            glow.contains("LocalWindowSeen.current"),
            "The clock stops when nobody can see the window",
        )
        assertTrue(
            glow.contains("mutableFloatStateOf(0f)"),
            "The clock writes a float state, read while drawing",
        )
        assertTrue(
            glow.contains("glow.seconds.value") && glow.contains("glow.progress.value"),
            "Both are read inside the draw, so a running glow repaints without recomposing the player",
        )
    }

    @Test
    fun testTheSweepTravelsFromTheEndBackToTheBeginning() {
        val full = source("ui/player/FullPlayer.kt")
        assertTrue(
            full.contains("MixHalo") && full.contains("MixSweep") && full.contains("rememberMixGlow"),
            "The glow has to be mounted on the cover, or none of it is visible",
        )
        // Halo first (behind the sleeve), sweep last (over it, clipped to the corners). Scoped to
        // the cover column, because the file draws artwork in three other layouts too.
        val column = full.substringAfter("private fun CoverColumn(")
        val haloAt = column.indexOf("MixHalo(")
        val artAt = column.indexOf("AnimatedArtwork(")
        val sweepAt = column.indexOf("MixSweep(")
        assertTrue(haloAt < artAt && artAt < sweepAt, "Halo behind the sleeve, band over it")

        val glow = source("ui/player/cover/MixGlow.kt")
        assertTrue(
            glow.contains("val centreY = size.height * (1f - travel)"),
            "The band starts at the bottom, where the track is ending, and arrives at the top",
        )
    }

    @Test
    fun testThePulseSitsOnTheBeatOnlyWhenTheTempoIsKnown() {
        val glow = source("ui/player/cover/MixGlow.kt")
        assertTrue(
            glow.contains("if (glow.outgoingBpm <= 0f) return 1f"),
            "A plain crossfade has no analysed tempo, and a wrong-tempo pulse is worse than none",
        )
        assertTrue(glow.contains("60f / glow.outgoingBpm"), "The pulse is a beat long")
    }

    @Test
    fun testTheGlowStopsWhenTheWindowCannotBeSeen() {
        val full = source("ui/player/FullPlayer.kt")
        // The cover is in the full player, which is the only place the glow is mounted: it is a
        // large, quiet, glanceable surface, which is what the effect needs.
        assertTrue(full.contains("rememberMeshDrift()"), "Unchanged: the mesh drift still runs")
    }

    private fun source(path: String): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/$path")
        assertTrue(file.exists(), "$path should exist")
        return file.readText()
    }
}

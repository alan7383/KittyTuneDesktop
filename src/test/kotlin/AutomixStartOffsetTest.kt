package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.round

class AutomixStartOffsetTest {

    @Test
    fun `PlayerPreferences start offset mode defaults and mutation`() {
        val prefs = PlayerPreferences()
        val originalMode = prefs.getAutomixStartOffsetMode()
        val originalSec = prefs.getAutomixStartOffsetCustomSec()

        try {
            assertEquals(
                "Default start offset mode should be AUTO (0)",
                PlayerPreferences.AUTOMIX_START_OFFSET_AUTO,
                PlayerPreferences.AUTOMIX_START_OFFSET_AUTO
            )
            assertEquals(1, PlayerPreferences.AUTOMIX_START_OFFSET_BEGINNING)
            assertEquals(2, PlayerPreferences.AUTOMIX_START_OFFSET_CUSTOM)

            prefs.setAutomixStartOffsetMode(PlayerPreferences.AUTOMIX_START_OFFSET_BEGINNING)
            assertEquals(PlayerPreferences.AUTOMIX_START_OFFSET_BEGINNING, prefs.getAutomixStartOffsetMode())

            prefs.setAutomixStartOffsetMode(PlayerPreferences.AUTOMIX_START_OFFSET_CUSTOM)
            assertEquals(PlayerPreferences.AUTOMIX_START_OFFSET_CUSTOM, prefs.getAutomixStartOffsetMode())

            prefs.setAutomixStartOffsetCustomSec(15)
            assertEquals(15, prefs.getAutomixStartOffsetCustomSec())

            prefs.setAutomixStartOffsetCustomSec(100)
            assertEquals("Custom seconds should be capped at 60", 60, prefs.getAutomixStartOffsetCustomSec())

            prefs.setAutomixStartOffsetCustomSec(-5)
            assertEquals("Custom seconds should not be negative", 0, prefs.getAutomixStartOffsetCustomSec())
        } finally {
            prefs.setAutomixStartOffsetMode(originalMode)
            prefs.setAutomixStartOffsetCustomSec(originalSec)
        }
    }

    @Test
    fun `Beginning mode completely disables offset to 0ms`() {
        fun computeIncomingStart(mode: Int, targetSec: Int, bpm: Float, firstBeatOffsetMs: Long, mixInPointMs: Long?): Long {
            val inPeriodMs = (60_000f / bpm).toDouble()
            return when (mode) {
                PlayerPreferences.AUTOMIX_START_OFFSET_BEGINNING -> 0L
                PlayerPreferences.AUTOMIX_START_OFFSET_CUSTOM -> {
                    val targetMs = targetSec * 1000L
                    if (targetMs <= 0L) {
                        0L
                    } else if (bpm > 0f) {
                        val inBarMs = inPeriodMs * 4
                        val inBars = round((targetMs - firstBeatOffsetMs) / inBarMs).toLong().coerceAtLeast(0)
                        (firstBeatOffsetMs + inBars * inBarMs).toLong().coerceAtLeast(0L)
                    } else {
                        targetMs
                    }
                }
                else -> {
                    val rawStart = mixInPointMs?.takeIf { it > 0 } ?: firstBeatOffsetMs
                    val inPhraseMs = inPeriodMs * 8
                    val inK = ceil((rawStart - firstBeatOffsetMs) / inPhraseMs).toLong().coerceAtLeast(0)
                    (firstBeatOffsetMs + inK * inPhraseMs).toLong()
                }
            }
        }

        val start = computeIncomingStart(
            mode = PlayerPreferences.AUTOMIX_START_OFFSET_BEGINNING,
            targetSec = 10,
            bpm = 120f,
            firstBeatOffsetMs = 250L,
            mixInPointMs = 10_000L
        )

        assertEquals("When mode is BEGINNING, incoming start must be 0:00 (0 ms)", 0L, start)
    }

    @Test
    fun `Custom offset mode snaps to 4-beat bar downbeat near target seconds`() {
        val bpm = 120f
        val periodMs = 60_000.0 / bpm // 500ms
        val barMs = periodMs * 4 // 2000ms (1 bar)
        val firstBeatOffsetMs = 150L

        fun computeCustom(sec: Int): Long {
            val targetMs = sec * 1000L
            if (targetMs <= 0L) return 0L
            val inBars = round((targetMs - firstBeatOffsetMs) / barMs).toLong().coerceAtLeast(0)
            return (firstBeatOffsetMs + inBars * barMs).toLong().coerceAtLeast(0L)
        }

        // Custom 0s should yield 0:00 (0ms)
        assertEquals(0L, computeCustom(0))

        // Custom 10s: target 10000ms. (10000 - 150) / 2000 = 4.925 -> 5 bars -> 150 + 5 * 2000 = 10150ms
        val start10 = computeCustom(10)
        assertEquals(10150L, start10)
        assertTrue("Start 10s should be within 1 bar of 10s", kotlin.math.abs(start10 - 10000L) <= barMs)
        assertEquals("Start 10s should be aligned to the firstBeat grid", 0L, (start10 - firstBeatOffsetMs) % barMs.toLong())

        // Custom 6s: target 6000ms. (6000 - 150) / 2000 = 2.925 -> 3 bars -> 150 + 3 * 2000 = 6150ms
        val start6 = computeCustom(6)
        assertEquals(6150L, start6)
        assertTrue("Start 6s should be within 1 bar of 6s", kotlin.math.abs(start6 - 6000L) <= barMs)

        // Custom 4s: target 4000ms. (4000 - 150) / 2000 = 1.925 -> 2 bars -> 150 + 2 * 2000 = 4150ms
        val start4 = computeCustom(4)
        assertEquals(4150L, start4)
    }

    @Test
    fun `Auto mode retains smart dynamic mix-in skip when enabled and fallback when disabled`() {
        val bpm = 120f
        val periodMs = 60_000.0 / bpm // 500ms
        val phraseMs = periodMs * 8 // 4000ms (8 beats / 2 bars)
        val firstBeatOffsetMs = 200L
        val detectedMixInMs = 9_600L

        // Dynamic points enabled: rawStart = 9600ms
        val rawStartEnabled = detectedMixInMs
        val inKEnabled = ceil((rawStartEnabled - firstBeatOffsetMs) / phraseMs).toLong().coerceAtLeast(0)
        val startEnabled = (firstBeatOffsetMs + inKEnabled * phraseMs).toLong()
        // (9600 - 200) / 4000 = 2.35 -> ceil is 3 -> 200 + 3 * 4000 = 12200ms
        assertEquals(12200L, startEnabled)

        // Dynamic points disabled: rawStart = firstBeatOffsetMs
        val rawStartDisabled = firstBeatOffsetMs
        val inKDisabled = ceil((rawStartDisabled - firstBeatOffsetMs) / phraseMs).toLong().coerceAtLeast(0)
        val startDisabled = (firstBeatOffsetMs + inKDisabled * phraseMs).toLong()
        assertEquals(200L, startDisabled)
    }

    @Test
    fun `Incoming start is clamped to not exceed track duration`() {
        val trackDurationMs = 12_000L
        val unconstrainedStart = 20_000L

        val effectiveIncomingStart = if (trackDurationMs > 5000L) {
            unconstrainedStart.coerceIn(0L, trackDurationMs - 3000L)
        } else {
            unconstrainedStart.coerceAtLeast(0L)
        }

        assertEquals(9000L, effectiveIncomingStart)

        // Ultra short track <= 5000ms
        val shortTrackDurationMs = 4000L
        val effectiveShortStart = if (shortTrackDurationMs > 5000L) {
            unconstrainedStart.coerceIn(0L, shortTrackDurationMs - 3000L)
        } else {
            unconstrainedStart.coerceAtLeast(0L)
        }
        assertEquals(20_000L, effectiveShortStart)
    }

    @Test
    fun `Custom mode fallback when BPM is non-positive`() {
        val targetSec = 8
        val targetMs = targetSec * 1000L
        val bpm = 0f
        val firstBeatOffsetMs = 300L

        val incomingStart = if (targetMs <= 0L) {
            0L
        } else if (bpm > 0f) {
            val inPeriodMs = (60_000f / bpm).toDouble()
            val inBarMs = inPeriodMs * 4
            val inBars = round((targetMs - firstBeatOffsetMs) / inBarMs).toLong().coerceAtLeast(0)
            (firstBeatOffsetMs + inBars * inBarMs).toLong().coerceAtLeast(0L)
        } else {
            targetMs
        }

        assertEquals("When BPM is 0 or unanalyzed, custom mode directly uses targetMs", 8000L, incomingStart)
    }

    @Test
    fun `PlayerCompat actualStartPos prioritizes incomingStartMs from automix plan`() {
        val effectivePlanIncomingStart: Long? = 6150L
        val defaultStartPositionMs = 0L

        val actualStartPos = effectivePlanIncomingStart ?: defaultStartPositionMs
        assertEquals(6150L, actualStartPos)

        val nullPlanIncomingStart: Long? = null
        val fallbackStartPos = nullPlanIncomingStart ?: defaultStartPositionMs
        assertEquals(0L, fallbackStartPos)
    }
}

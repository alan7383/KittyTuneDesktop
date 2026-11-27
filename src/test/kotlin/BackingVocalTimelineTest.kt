package com.alananasss.kittytune.ui.player.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackingVocalTimelineTest {
    @Test
    fun separatedBracketedPhrasesLeaveAQuietIntervalBetweenThem() {
        val line = LyricLine(
            text = "(oh) hold on (yeah)",
            startTime = 1_000,
            endTime = 5_000,
            words = listOf(
                LyricWord("(oh)", 1_000, 1_300),
                LyricWord("hold ", 1_500, 2_300),
                LyricWord("on", 2_300, 3_000),
                LyricWord("(yeah)", 3_900, 4_180),
            ),
        )
        val main = buildSyncedLyrics(listOf(line), isWordSynced = true, splitBacking = true)
            .lines.single() as KaraokeLine.MainKaraokeLine
        val (first, second) = backingVocalRuns(main.accompanimentLines.orEmpty(), main.end)

        assertEquals("hold on", main.syllables.joinToString("") { it.content })
        assertEquals("oh", first.line.syllables.single().content)
        assertEquals("yeah", second.line.syllables.single().content)
        assertFalse(first.isVisibleAt(700))
        assertTrue(first.isVisibleAt(900))
        assertEquals(0.4f, first.syllableOpacityAt(first.line.syllables.single(), 900), 0.001f)
        assertEquals(1f, first.syllableOpacityAt(first.line.syllables.single(), 1_150), 0.001f)
        assertEquals(0.5f, first.opacityAt(1_420), 0.001f)
        assertFalse(first.isVisibleAt(2_000))
        assertFalse(second.isVisibleAt(2_000))
        assertTrue(second.isVisibleAt(3_800))
        assertFalse(second.isVisibleAt(4_500))
    }

    @Test
    fun consecutiveTtmlPhrasesAppearInOrderAndFadeTogetherAtTheEnd() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="1.000s" end="4.000s">
                <span begin="1.000s" end="3.900s">Lead line</span>
                <span ttm:role="x-bg"><span begin="1.100s" end="1.410s">first</span></span>
                <span ttm:role="x-bg"><span begin="1.730s" end="2.010s">second</span></span>
                <span ttm:role="x-bg"><span begin="2.320s" end="2.600s">third</span></span>
              </p></div></body>
            </tt>
        """.trimIndent()
        val parsed = LyricsUtils.parseTtml(ttml, 4_000).single()
        assertEquals(listOf(0, 1, 2), parsed.words.filter { it.isBackground }.map { it.backgroundPhrase })

        val main = buildSyncedLyrics(listOf(parsed), isWordSynced = true).lines.single() as KaraokeLine.MainKaraokeLine
        val run = backingVocalRuns(main.accompanimentLines.orEmpty(), main.end).single()
        val (first, second, third) = run.line.syllables
        assertEquals(listOf("first ", "second ", "third"), run.line.syllables.map { it.content })
        assertEquals(0.4f, run.syllableOpacityAt(first, 1_040), 0.001f)
        assertEquals(0f, run.syllableOpacityAt(second, 1_100), 0.001f)
        assertEquals(1f, run.syllableOpacityAt(first, 1_690), 0.001f)
        assertEquals(0.4f, run.syllableOpacityAt(second, 1_690), 0.001f)
        assertEquals(0f, run.syllableOpacityAt(third, 1_900), 0.001f)
        assertEquals(1f, run.syllableOpacityAt(third, 2_800), 0.001f)
        assertEquals(1f, run.opacityAt(3_800), 0.001f)
        assertEquals(0.5f, run.opacityAt(main.end + 120), 0.001f)
        assertFalse(run.isVisibleAt(main.end + 240))
        assertFalse(run.isVisibleAt(600))
    }
}

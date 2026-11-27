package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics
import com.alananasss.kittytune.ui.player.lyrics.formatLyricWordContents
import com.alananasss.kittytune.ui.player.lyrics.normalizeLyricCommas
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsPunctuationTest {
    @Test
    fun canonicalTextReconcilesTwoTimedCommasWithoutMovingTheNextWord() {
        val words = listOf(
            LyricWord("no,", 1000L, 1300L),
            LyricWord(",", 1700L, 1710L),
            LyricWord("expensive", 1400L, 2000L),
        )
        val line = LyricLine("no,, expensive", 1000L, 2500L, words)

        assertEquals(listOf("no, ", "", "expensive"), formatLyricWordContents(line.text, words))
        val karaoke = buildSyncedLyrics(listOf(line), isWordSynced = true).lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(listOf("no, ", "expensive"), karaoke.syllables.map { it.content })
        assertEquals(listOf(1000, 1400), karaoke.syllables.map { it.start })
    }

    @Test
    fun keepsSyllableBoundariesAndOtherPunctuation() {
        val words = listOf("Don", "&apos;t", "stop", "now...").map { LyricWord(it, 0L, 1L) }
        assertEquals(
            listOf("Don", "'t, ", "stop ", "now..."),
            formatLyricWordContents("Don't, stop now...", words),
        )
        assertEquals("no, expensive?!", "no , , expensive?!".normalizeLyricCommas())
    }

    @Test
    fun aMismatchedLineDoesNotReorderTheTimedWords() {
        val words = listOf("no, ", " , ", "expensive").map { LyricWord(it, 0L, 1L) }
        assertEquals("no, expensive", formatLyricWordContents("expensive, no", words).joinToString(""))
    }
}

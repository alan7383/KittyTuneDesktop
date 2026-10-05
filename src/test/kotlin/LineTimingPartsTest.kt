import com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics
import com.alananasss.kittytune.ui.player.lyrics.instantSyllables
import com.alananasss.kittytune.ui.player.lyrics.revealSyllables
import com.alananasss.kittytune.ui.player.lyrics.splitBackingVocals
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Backing vocals and word-by-word lighting for lyrics timed by the line only (issue #66). */
class LineTimingPartsTest {

    @Test
    fun bracketsAreTheBackingVocal() {
        assertEquals("I'm falling" to "falling down", splitBackingVocals("I'm falling (falling down)"))
        assertEquals("say it again" to "again oh", splitBackingVocals("(again) say it (oh) again"))
    }

    @Test
    fun aLineThatIsAllBracketsOrHasNoneStaysWhole() {
        assertEquals("(ooh, ooh)" to null, splitBackingVocals("(ooh, ooh)"))
        assertEquals("no brackets here" to null, splitBackingVocals("no brackets here"))
        assertEquals("empty () pair" to null, splitBackingVocals("empty () pair"))
    }

    @Test
    fun revealedWordsStartInOrderAndAllFinishFast() {
        val words = revealSyllables("one two three four five", start = 10_000, end = 14_000)
        assertEquals(5, words.size)
        assertEquals(listOf("one ", "two ", "three ", "four ", "five"), words.map { it.content })
        assertTrue(words.zipWithNext().all { (a, b) -> b.start > a.start })
        assertEquals(10_000, words.first().start)
        assertTrue(words.last().end <= 10_000 + 1_000, "lit within a second: ${words.last().end}")
    }

    @Test
    fun aShortLineIsNeverRevealedPastItsEnd() {
        val words = revealSyllables("a b c d e f g h", start = 0, end = 200)
        assertTrue(words.all { it.start in 0 until 200 && it.end <= 200 && it.end > it.start })
    }

    @Test
    fun instantWordsAreLitAtTheStart() {
        val words = instantSyllables("right now", start = 500, end = 3_000)
        assertTrue(words.all { it.start == 500 && it.end == 501 })
    }

    @Test
    fun aBracketedLineGetsABackingLineAndAPlainOneDoesNot() {
        val lyrics = buildSyncedLyrics(
            entries = listOf(
                LyricLine(text = "I'm falling (falling down)", startTime = 1_000, endTime = 4_000),
                LyricLine(text = "nothing to split", startTime = 5_000, endTime = 8_000),
            ),
            isWordSynced = false,
            splitBacking = true,
        )
        val first = assertIs<KaraokeLine.MainKaraokeLine>(lyrics.lines[0])
        assertEquals("I'm falling", first.syllables.joinToString("") { it.content })
        assertEquals("falling down", first.accompanimentLines!!.single().syllables.joinToString("") { it.content })
        assertIs<SyncedLine>(lyrics.lines[1])
    }

    @Test
    fun withTheOptionOffTheBracketsStayInTheLine() {
        val lyrics = buildSyncedLyrics(
            entries = listOf(LyricLine(text = "I'm falling (falling down)", startTime = 1_000, endTime = 4_000)),
            isWordSynced = false,
        )
        assertIs<SyncedLine>(lyrics.lines.single())
    }

    @Test
    fun revealingTurnsEveryLineIntoTimedWords() {
        val lyrics = buildSyncedLyrics(
            entries = listOf(LyricLine(text = "light me up", startTime = 1_000, endTime = 4_000)),
            isWordSynced = false,
            revealWords = true,
        )
        val line = assertIs<KaraokeLine.MainKaraokeLine>(lyrics.lines.single())
        assertEquals(3, line.syllables.size)
        assertNull(line.accompanimentLines)
    }
}

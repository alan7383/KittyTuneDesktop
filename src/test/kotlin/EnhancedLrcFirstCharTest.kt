import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Regression tests for the "missing first character" bug in enhanced (A2-extension) LRC parsing.
 *
 * Some providers (notably SimpMusic relaying Musixmatch richSyncLyrics) emit enhanced LRC where
 * the first syllable of each line comes **directly after the closing `]`** of the line timestamp,
 * without its own leading <mm:ss.ms> marker:
 *
 *   [00:10.12]P<00:10.20>aris is <00:10.80>burning
 *
 * The old ENHANCED_WORD_PATTERN only captured text that followed a <time> tag, so the `P`
 * before the first `<` was silently dropped. The correct behaviour is to treat any text before
 * the first `<` as a word segment that inherits the line's own timestamp.
 */
class EnhancedLrcFirstCharTest {

    private val duration = 240_000L

    private fun parse(lrc: String) = LyricsUtils.parseLrc(lrc, duration)

    // ---- Core regression: single leading character before first <time> --------

    @Test
    fun `leading single char before first word-timestamp is not dropped`() {
        // Simulates Musixmatch richSyncLyrics / SimpMusic format
        val lrc = "[00:10.12]P<00:10.20>aris is <00:10.80>burning"
        val lines = parse(lrc)

        assertEquals(1, lines.size, "should parse exactly one line")
        val line = lines.first()

        assertEquals("Paris is burning", line.text)

        val words = line.words
        assert(words.isNotEmpty()) { "word list must not be empty" }
        assertEquals("P", words.first().text, "first word should be 'P'")
    }

    // ---- Multi-char leading text ---------------------------------------------

    @Test
    fun `leading multi-char word before first word-timestamp is not dropped`() {
        val lrc = "[00:20.00]Hello <00:20.50>World"
        val lines = parse(lrc)

        assertEquals(1, lines.size)
        assertEquals("Hello World", lines.first().text)

        val words = lines.first().words
        assert(words.isNotEmpty())
        assertEquals("Hello ", words.first().text)
    }

    // ---- Format where every syllable already has its own <time> (unchanged) --

    @Test
    fun `format where every syllable has own timestamp is unaffected`() {
        val lrc = "[00:10.12]<00:10.12>Hello <00:10.70>World"
        val lines = parse(lrc)

        assertEquals(1, lines.size)
        assertEquals("Hello World", lines.first().text)
        assertEquals(2, lines.first().words.size, "should have 2 words")
        assertEquals("Hello ", lines.first().words[0].text)
        assertEquals("World", lines.first().words[1].text)
    }

    // ---- Plain LRC without word timestamps (unchanged) -----------------------

    @Test
    fun `plain lrc without word timestamps is unaffected`() {
        val lrc = "[00:10.12]Hello World"
        val lines = parse(lrc)

        assertEquals(1, lines.size)
        assertEquals("Hello World", lines.first().text)
        assert(lines.first().words.isEmpty()) { "no word-level data expected" }
    }

    // ---- Multi-line: first char must be preserved on every line --------------

    @Test
    fun `multi-line enhanced lrc preserves first char on every line`() {
        val lrc = "[00:10.00]S<00:10.10>etting <00:10.80>sun\n[00:14.00]R<00:14.10>ising <00:14.70>moon"
        val lines = parse(lrc)

        assertEquals(2, lines.size)
        assertEquals("Setting sun", lines[0].text)
        assertEquals("Rising moon", lines[1].text)
        assertEquals("S", lines[0].words.first().text)
        assertEquals("R", lines[1].words.first().text)
    }

    // ---- Leading char must inherit the line's start time --------------------

    @Test
    fun `leading char inherits the line start time`() {
        val lrc = "[00:10.12]P<00:10.20>aris"
        val lines = parse(lrc)

        assertEquals(1, lines.size)
        val words = lines.first().words
        assert(words.isNotEmpty())
        // 'P' has no own <time> — must get the line's startTime (10 120 ms)
        assertEquals(10_120L, words.first().startTime, "leading char must inherit line timestamp")
    }
}

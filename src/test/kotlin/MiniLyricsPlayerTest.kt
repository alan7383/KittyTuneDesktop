import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.mini.resolveActiveChunk
import com.alananasss.kittytune.ui.player.mini.splitIntoChunks
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniLyricsPlayerTest {

    @Test
    fun short_line_stays_as_one_chunk() {
        val words = listOf(
            LyricWord("Hold ", 0L, 500L),
            LyricWord("on", 500L, 1000L),
        )
        val text = "Hold on"
        val chunks = splitIntoChunks(text, words, maxChunkChars = 36)
        assertEquals(1, chunks.size)
        assertEquals("Hold on", chunks[0].text)
        assertEquals(0L, chunks[0].startTime)
        assertEquals(1000L, chunks[0].endTime)
    }

    @Test
    fun long_line_splits_on_word_boundaries() {
        val words = listOf(
            LyricWord("Brought ", 0L, 800L),
            LyricWord("to ", 800L, 1200L),
            LyricWord("you ", 1200L, 1600L),
            LyricWord("with ", 1600L, 2000L),
            LyricWord("Apple ", 2000L, 2600L),
            LyricWord("Music ", 2600L, 3200L),
            LyricWord("and ", 3200L, 3500L),
            LyricWord("KittyTune ", 3500L, 4200L),
            LyricWord("Desktop ", 4200L, 4800L),
            LyricWord("Player", 4800L, 5500L),
        )
        val text = words.joinToString("") { it.text }
        val chunks = splitIntoChunks(text, words, maxChunkChars = 32)
        assertTrue(chunks.size >= 2, "Long line should be split into at least 2 chunks")
        for (chunk in chunks) {
            assertTrue(chunk.text.isNotBlank())
            assertTrue(chunk.words.isNotEmpty())
        }
        assertEquals(0L, chunks.first().startTime)
        assertEquals(5500L, chunks.last().endTime)
    }

    @Test
    fun active_chunk_resolves_correctly_by_playback_time() {
        val words1 = listOf(LyricWord("Part ", 0L, 1000L), LyricWord("one ", 1000L, 2000L))
        val words2 = listOf(LyricWord("Part ", 2000L, 3000L), LyricWord("two", 3000L, 4000L))
        val text = "Part one Part two"
        val chunks = splitIntoChunks(text, words1 + words2, maxChunkChars = 10)
        assertEquals(2, chunks.size)

        assertEquals(0, resolveActiveChunk(chunks, 500f))
        assertEquals(0, resolveActiveChunk(chunks, 1999f))
        assertEquals(1, resolveActiveChunk(chunks, 2500f))
        assertEquals(1, resolveActiveChunk(chunks, 3900f))
        assertEquals(1, resolveActiveChunk(chunks, 9999f))
    }
}

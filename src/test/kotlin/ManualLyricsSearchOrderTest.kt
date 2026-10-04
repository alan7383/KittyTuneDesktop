import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.ui.player.UnifiedLyricResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualLyricsSearchOrderTest {

    private fun cleanLyricsPreview(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.lines()
            .map { line ->
                line.replace(Regex("""\[\d+:\d+(?:\.\d+)?\]"""), "")
                    .replace(Regex("""<\d+:\d+(?:\.\d+)?>"""), "")
                    .replace(Regex("""^\[[a-zA-Z]+:[^\]]*\]"""), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(" / ")
            .ifBlank { null }
    }

    @Test
    fun `sorting places synchronized lyrics first, then word-sync within synced, then plain text`() {
        val plain1 = UnifiedLyricResult(
            id = "1",
            name = "Song A",
            artistName = "Artist",
            albumName = "Album",
            durationSec = 180.0,
            hasLineSync = false,
            hasWordSync = false,
            provider = "Genius",
            previewText = "Plain text lyrics here"
        )
        val plain2 = UnifiedLyricResult(
            id = "2",
            name = "Song B",
            artistName = "Artist",
            albumName = "Album",
            durationSec = 180.0,
            hasLineSync = false,
            hasWordSync = false,
            provider = "A-Z Lyrics",
            previewText = "Another plain text"
        )
        val lineSynced = UnifiedLyricResult(
            id = "3",
            name = "Song C",
            artistName = "Artist",
            albumName = "Album",
            durationSec = 180.0,
            hasLineSync = true,
            hasWordSync = false,
            provider = "LRCLIB",
            previewText = "Line synced lyrics"
        )
        val wordSynced = UnifiedLyricResult(
            id = "4",
            name = "Song D",
            artistName = "Artist",
            albumName = "Album",
            durationSec = 180.0,
            hasLineSync = true,
            hasWordSync = true,
            provider = "Musixmatch",
            previewText = "Word synced lyrics"
        )

        val unsortedList = listOf(plain1, lineSynced, plain2, wordSynced)

        val sortedList = unsortedList.sortedWith(
            compareByDescending<UnifiedLyricResult> { it.hasLineSync || it.hasWordSync }
                .thenByDescending { it.hasWordSync }
                .thenBy { it.name }
        )

        // Word-sync first, line-sync next, plain text last
        assertEquals("4", sortedList[0].id, "Word synced should be first")
        assertEquals("3", sortedList[1].id, "Line synced should be second")
        assertTrue(
            !sortedList[2].hasLineSync && !sortedList[2].hasWordSync,
            "Third result should be unsynchronized"
        )
        assertTrue(
            !sortedList[3].hasLineSync && !sortedList[3].hasWordSync,
            "Fourth result should be unsynchronized"
        )
    }

    @Test
    fun `cleanLyricsPreview strips LRC tags, timestamps and joins first two lines`() {
        val lrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [00:12.34]First meaningful line of lyrics
            [00:15.67]<00:15.80>Second <00:16.10>meaningful line
            [00:20.00]Third line should be ignored
        """.trimIndent()

        val preview = cleanLyricsPreview(lrc)
        assertEquals("First meaningful line of lyrics / Second meaningful line", preview)
    }

    @Test
    fun `cleanLyricsPreview handles plain text with leading blank lines`() {
        val plain = "\n\n   \nHello world\nThis is a song\nExtra line"
        val preview = cleanLyricsPreview(plain)
        assertEquals("Hello world / This is a song", preview)
    }

    @Test
    fun `cleanLyricsPreview returns null for blank or only tags`() {
        val onlyTags = "[ti:Title]\n[ar:Artist]\n[al:Album]"
        val preview = cleanLyricsPreview(onlyTags)
        assertNull(preview)
    }

    @Test
    fun `PreferredLyricsProvider fromName handles both enum name and display name case-insensitively`() {
        assertEquals(PreferredLyricsProvider.MUSIXMATCH, PreferredLyricsProvider.fromName("MUSIXMATCH"))
        assertEquals(PreferredLyricsProvider.MUSIXMATCH, PreferredLyricsProvider.fromName("musixmatch"))
        assertEquals(PreferredLyricsProvider.MUSIXMATCH, PreferredLyricsProvider.fromName("Musixmatch"))
        assertEquals(PreferredLyricsProvider.LRCLIB, PreferredLyricsProvider.fromName("LRCLIB"))
        assertEquals(PreferredLyricsProvider.LRCLIB, PreferredLyricsProvider.fromName("lrclib"))
        assertEquals(PreferredLyricsProvider.BETTER_LYRICS, PreferredLyricsProvider.fromName("BETTER_LYRICS"))
        assertEquals(PreferredLyricsProvider.BETTER_LYRICS, PreferredLyricsProvider.fromName("BetterLyrics"))
        assertNull(PreferredLyricsProvider.fromName("ALL"))
    }
}

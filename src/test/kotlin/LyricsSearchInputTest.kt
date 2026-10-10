import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.ui.player.ManualLyricsSearch
import com.alananasss.kittytune.ui.player.UnifiedLyricResult
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What a lyrics search is made of: the title it searches for, and which lyrics it believes. */
class LyricsSearchInputTest {

    @Test
    fun `a dash inside a word stays in the title`() {
        val (artist, title) = LyricsMatcher.splitArtistAndTitle("ELA-ELA (feat. kai angel)", "9mice & kai angel")
        assertEquals("9mice & kai angel", artist)
        assertEquals("ELA-ELA", title)
    }

    @Test
    fun `a spaced dash still separates artist and title`() {
        val (artist, title) = LyricsMatcher.splitArtistAndTitle("9mice - ELA-ELA", "some label")
        assertEquals("9mice", artist)
        assertEquals("ELA-ELA", title)
    }

    @Test
    fun `a two-song title is split into its songs`() {
        assertEquals(listOf("your love", "narcotic"), LyricsMatcher.titleParts("your love / narcotic"))
        assertTrue(LyricsMatcher.titleParts("AC/DC tribute").isEmpty())
        assertTrue(LyricsMatcher.titleParts("New-York").isEmpty())
    }

    @Test
    fun `generator watermarks are recognised and real lines are not`() {
        assertTrue(LyricsUtils.isWatermarkLine("—-www.LRCgenerator.com—-"))
        assertTrue(LyricsUtils.isWatermarkLine("Lyrics by RentAnAdviser.com"))
        assertTrue(LyricsUtils.isWatermarkLine("https://lrclib.net"))
        assertFalse(LyricsUtils.isWatermarkLine("I walked all night through New York"))
        assertFalse(LyricsUtils.isWatermarkLine("Я звоню тебе, но ты не берёшь"))
    }

    @Test
    fun `watermark lines are dropped from synced lyrics`() {
        val lrc = """
            [00:01.00]—-www.LRCgenerator.com—-
            [00:05.00]First line
            [00:09.00]Second line
            [00:13.00]Third line
        """.trimIndent()
        val lines = LyricsUtils.parseLyricsContent(lrc, 60_000L).filterNot { it.isInstrumental }
        assertEquals(listOf("First line", "Second line", "Third line"), lines.map { it.text })
    }

    @Test
    fun `stamps that never advance are not synced lyrics`() {
        val lrc = (1..8).joinToString("\n") { "[00:00.00]Line $it" }
        assertTrue(LyricsUtils.parseLyricsContent(lrc, 180_000L).isEmpty())
    }

    @Test
    fun `plain text keeps the words and loses stamps and header tags`() {
        val raw = "[ar:9mice]\n[00:00.00]First\n[00:00.00]Second\nwww.lrcgenerator.com"
        assertEquals("First\nSecond", LyricsUtils.cleanPlainLyrics(raw))
    }

    @Test
    fun `copies of one song from one source collapse to the closest length`() {
        val target = LyricsMatcher.Target(title = "your love", artist = "9mice", durationMs = 150_000L)
        fun hit(id: String, seconds: Double) = UnifiedLyricResult(
            id = id, name = "your love", artistName = "9mice", albumName = null, durationSec = seconds,
            hasLineSync = true, hasWordSync = false, provider = "LRCLIB",
        )
        val merged = ManualLyricsSearch.merge(emptyList(), listOf(hit("1", 170.0), hit("2", 151.0), hit("3", 120.0)), target)
        assertEquals(listOf("2"), merged.map { it.id })
    }

    @Test
    fun `the right song with timings outranks a stranger`() {
        val target = LyricsMatcher.Target(title = "your love", artist = "9mice", durationMs = 150_000L)
        val right = UnifiedLyricResult("1", "your love", "9mice", null, 150.0, true, false, "LRCLIB")
        val stranger = UnifiedLyricResult("2", "something else", "someone", null, 150.0, true, true, "MUSIXMATCH")
        val merged = ManualLyricsSearch.merge(listOf(stranger), listOf(right), target)
        assertEquals("1", merged.first().id)
    }

    @Test
    fun `run-together words and hyphens get spaced variants`() {
        val variants = LyricsMatcher.queryVariants("NEWYORK 9mice", "9mice - New-York")
        assertTrue("NEW YORK 9mice".lowercase() in variants.map { it.lowercase() }, "variants were $variants")
        assertTrue("New York 9mice" in LyricsMatcher.queryVariants("New-York 9mice", "x"))
    }
}

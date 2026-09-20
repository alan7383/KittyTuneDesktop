import com.alananasss.kittytune.data.lyrics.clients.BetterLyricsClient
import com.alananasss.kittytune.ui.player.lyrics.LyricSinger
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BetterLyricsIntegrationTest {

    @Test
    fun testBetterLyricsTvGirlNotAllowedLive() = runBlocking {
        val result = BetterLyricsClient.getLyrics(
            title = "Not Allowed",
            artist = "TV Girl",
            album = "Who Really Cares",
            durationSeconds = 167
        )

        assertTrue(result.isSuccess, "BetterLyrics should return success for TV Girl - Not Allowed: ${result.exceptionOrNull()?.message}")
        val ttml = result.getOrThrow()
        assertTrue(ttml.contains("<tt") && ttml.contains("</tt>"), "Result should contain TTML XML content")

        val parsedLines = LyricsUtils.parseLyricsContent(ttml, 167_000L)
        assertFalse(parsedLines.isEmpty(), "Parsed lines must not be empty")

        println("Parsed ${parsedLines.size} lines from BetterLyrics TV Girl - Not Allowed (Desktop)")

        val hasWordSync = parsedLines.any { it.words.isNotEmpty() }
        assertTrue(hasWordSync, "BetterLyrics TV Girl - Not Allowed should have word-synced lyrics")

        val hasSinger1 = parsedLines.any { it.singer == LyricSinger.SINGER_1 || it.agent?.lowercase() == "v1" }
        val hasSinger2 = parsedLines.any { it.singer == LyricSinger.SINGER_2 || it.agent?.lowercase() == "v2" }

        println("Has Singer 1 (v1): $hasSinger1, Has Singer 2 (v2): $hasSinger2")
        assertTrue(hasSinger1, "TV Girl - Not Allowed should have singer 1 (v1)")
        assertTrue(hasSinger2, "TV Girl - Not Allowed should have singer 2 (v2)")

        val lineWithWords = parsedLines.first { it.words.isNotEmpty() }
        assertTrue(lineWithWords.startTime >= 0, "Line start time should be >= 0")
        assertTrue(lineWithWords.endTime > lineWithWords.startTime, "Line end time should be > line start time")
        val firstWord = lineWithWords.words.first()
        assertTrue(firstWord.endTime >= firstWord.startTime, "Word end time should be >= word start time")
    }

    @Test
    fun testBetterLyricsWithNoisyTitleFallback() = runBlocking {
        val result = BetterLyricsClient.getLyrics(
            title = "TV Girl - Not Allowed (Official Audio)",
            artist = "TV Girl",
            album = "",
            durationSeconds = 167
        )

        assertTrue(result.isSuccess, "BetterLyrics should resolve noisy title via smart fallback: ${result.exceptionOrNull()?.message}")
        val ttml = result.getOrThrow()
        val parsedLines = LyricsUtils.parseLyricsContent(ttml, 167_000L)
        kotlin.test.assertEquals(60, parsedLines.size, "Should parse 60 lines from resolved TTML")
    }

    @Test
    fun testBetterLyricsWithCombinedQueryFromManualSearch() = runBlocking {
        val result = BetterLyricsClient.getLyrics(
            title = "Not Allowed TV Girl",
            artist = "TV Girl",
            album = "",
            durationSeconds = 167
        )

        assertTrue(result.isSuccess, "BetterLyrics should resolve combined manual search query: ${result.exceptionOrNull()?.message}")
        val ttml = result.getOrThrow()
        val parsedLines = LyricsUtils.parseLyricsContent(ttml, 167_000L)
        kotlin.test.assertEquals(60, parsedLines.size, "Should parse 60 lines from resolved TTML")
    }

    @Test
    fun testBetterLyricsWithSoundcloudUploaderAndArtistInTitle() = runBlocking {
        val result = BetterLyricsClient.getLyrics(
            title = "TV Girl - Not Allowed (Official Audio)",
            artist = "RandomSoundcloudUploader",
            album = "",
            durationSeconds = 167
        )

        assertTrue(result.isSuccess, "BetterLyrics should extract artist from title and succeed: ${result.exceptionOrNull()?.message}")
        val ttml = result.getOrThrow()
        val parsedLines = LyricsUtils.parseLyricsContent(ttml, 167_000L)
        kotlin.test.assertEquals(60, parsedLines.size, "Should parse 60 lines from resolved TTML")
    }
}

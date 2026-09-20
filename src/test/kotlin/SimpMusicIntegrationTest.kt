import com.alananasss.kittytune.data.lyrics.clients.SimpMusicClient
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimpMusicIntegrationTest {

    @Test
    fun testSimpMusicTvGirlNotAllowedLive() = runBlocking {
        val result = SimpMusicClient.getLyrics(
            title = "Not Allowed",
            artist = "TV Girl",
            duration = 167
        )

        assertTrue(result.isSuccess, "SimpMusic should return lyrics for TV Girl - Not Allowed: ${result.exceptionOrNull()?.message}")
        val raw = result.getOrThrow()
        assertTrue(raw.isNotBlank(), "Result should contain lyrics")

        val parsedLines = LyricsUtils.parseLyricsContent(raw, 167_000L)
        assertFalse(parsedLines.isEmpty(), "Parsed lines must not be empty")
        println("SimpMusic Desktop parsed ${parsedLines.size} lines for TV Girl - Not Allowed")
    }

    @Test
    fun testSimpMusicWithNoisySoundCloudTitle() = runBlocking {
        val result = SimpMusicClient.getLyrics(
            title = "TV Girl - Not Allowed (Official Audio)",
            artist = "SoundCloudReuploadAcc",
            duration = 167
        )

        assertTrue(result.isSuccess, "SimpMusic should resolve noisy SoundCloud title: ${result.exceptionOrNull()?.message}")
        val raw = result.getOrThrow()
        val parsedLines = LyricsUtils.parseLyricsContent(raw, 167_000L)
        assertFalse(parsedLines.isEmpty(), "Parsed lines must not be empty")
    }

    @Test
    fun testSimpMusicSearchAndDirectVideoId() = runBlocking {
        val searchResults = SimpMusicClient.search("TV Girl - Not Allowed")
        assertFalse(searchResults.isEmpty(), "Search results must not be empty")

        val found = searchResults.find { it.videoId == "TPGfJcTycHw" }
        assertNotNull(found, "Should contain known videoId TPGfJcTycHw")

        val directResult = SimpMusicClient.getLyrics(videoId = "TPGfJcTycHw", duration = 167)
        assertTrue(directResult.isSuccess, "Direct videoId fetch should succeed")
    }
}

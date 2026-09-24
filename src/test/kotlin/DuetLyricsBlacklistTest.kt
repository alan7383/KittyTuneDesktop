import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricSinger
import com.alananasss.kittytune.ui.player.lyrics.buildSyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuetLyricsBlacklistTest {

    private fun dummyTrack(id: Long, title: String = "Test Title"): Track {
        return Track(
            id = id,
            title = title,
            artworkUrl = null,
            durationMs = 180000L,
            user = User(id = 1L, username = "Artist", avatarUrl = null),
        )
    }

    @Test
    fun `PlayerPreferences duet blacklist stores and retrieves track IDs`() {
        val prefs = PlayerPreferences()
        val originalBlacklist = prefs.getLyricsDuetBlacklist()

        try {
            val testTrackId = 999123456L
            prefs.setTrackDuetBlacklisted(testTrackId, true)
            assertTrue(prefs.isTrackDuetBlacklisted(testTrackId), "Track must be blacklisted")
            assertTrue(prefs.getLyricsDuetBlacklist().contains(testTrackId.toString()), "Blacklist set must contain track id string")

            prefs.setTrackDuetBlacklisted(testTrackId, false)
            assertFalse(prefs.isTrackDuetBlacklisted(testTrackId), "Track must not be blacklisted after removal")
        } finally {
            prefs.setLyricsDuetBlacklist(originalBlacklist)
        }
    }

    @Test
    fun `buildSyncedLyrics respects isDuetEnabled and returns single stream when disabled`() {
        val duetLines = listOf(
            LyricLine(
                text = "Singer 1 line",
                startTime = 1000L,
                endTime = 3000L,
                singer = LyricSinger.SINGER_1,
                words = listOf(com.alananasss.kittytune.ui.player.lyrics.LyricWord("Singer", 1000L, 2000L))
            ),
            LyricLine(
                text = "Singer 2 line",
                startTime = 4000L,
                endTime = 6000L,
                singer = LyricSinger.SINGER_2,
                words = listOf(com.alananasss.kittytune.ui.player.lyrics.LyricWord("Singer", 4000L, 5000L))
            )
        )

        // With duet enabled: singer 2 aligns to End
        val duetActiveResult = buildSyncedLyrics(
            entries = duetLines,
            isWordSynced = true,
            isDuetEnabled = true,
            userAlignment = LyricsAlignment.LEFT
        )
        val secondLineDuet = duetActiveResult.lines[1] as KaraokeLine.MainKaraokeLine
        assertEquals(KaraokeAlignment.End, secondLineDuet.alignment, "Duet line 2 must align to End when duet enabled")

        // With duet disabled (blacklisted): singer 2 reverts to normal single-stream (Start)
        val duetDisabledResult = buildSyncedLyrics(
            entries = duetLines,
            isWordSynced = true,
            isDuetEnabled = false,
            userAlignment = LyricsAlignment.LEFT
        )
        val secondLineSingle = duetDisabledResult.lines[1] as KaraokeLine.MainKaraokeLine
        assertEquals(KaraokeAlignment.Start, secondLineSingle.alignment, "Duet line 2 must revert to Start alignment when duet disabled")
    }
}

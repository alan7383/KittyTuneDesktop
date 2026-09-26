import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.stats.ListeningReports
import com.alananasss.kittytune.data.stats.ReportPeriod
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListeningReportTest {

    private val zone = ZoneId.of("Europe/Moscow")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun listen(
        time: Long,
        trackId: Long = 1,
        artist: String = "A",
        listenMs: Long = 180_000,
        durationMs: Long = 180_000,
        avatar: String? = null,
        artwork: String = "cover$trackId",
    ) = ListeningStatsEvent(
        trackId = trackId, trackTitle = "T$trackId", artistName = artist, artistAvatarUrl = avatar,
        artworkUrl = artwork, eventType = "PLAY_COMPLETE", listenDurationMs = listenMs,
        trackDurationMs = durationMs, timestamp = time, furthestPositionMs = listenMs,
    )

    @Test
    fun theWeekIsTheCalendarWeekFromMonday() {
        // Thursday 25 September 2026.
        val window = ListeningReports.windowFor(ReportPeriod.WEEK, at(2026, 9, 25), zone)
        assertEquals(at(2026, 9, 21, 0), window.startMs)
        assertEquals(at(2026, 9, 28, 0), window.endMs)
    }

    @Test
    fun countsPlaysSkipsAndTheDailyBars() {
        val window = ListeningReports.windowFor(ReportPeriod.WEEK, at(2026, 9, 25), zone)
        val events = listOf(
            listen(at(2026, 9, 21, 9)),
            listen(at(2026, 9, 21, 22), trackId = 2),
            listen(at(2026, 9, 23, 22), trackId = 2, listenMs = 5_000), // a skip
            listen(at(2026, 9, 14)), // last week: not in the span
        )
        val report = ListeningReports.build(ReportPeriod.WEEK, window, events, previousListenMs = 180_000, zone = zone)

        assertEquals(2, report.plays)
        assertEquals(1, report.skips)
        assertEquals(7, report.activity.size)
        assertEquals(360_000L, report.activity[0].listenMs)
        assertEquals(5_000L, report.activity[2].listenMs)
        assertEquals(22, report.peakHour)
        assertEquals(1.0277778f, report.change!!, 0.001f)
    }

    @Test
    fun theLongestStreakCountsConsecutiveDays() {
        val window = ListeningReports.windowFor(ReportPeriod.MONTH, at(2026, 9, 25), zone)
        val days = listOf(1, 2, 3, 10, 11)
        val report = ListeningReports.build(ReportPeriod.MONTH, window, days.map { listen(at(2026, 9, it)) }, null, zone)
        assertEquals(3, report.longestStreakDays)
        assertEquals(5, report.activeDays)
        assertEquals(30, report.activity.size)
        assertNull(report.change)
    }

    @Test
    fun anArtistWithOnlyTheDefaultAvatarShowsTheirTopCover() {
        val window = ListeningReports.windowFor(ReportPeriod.WEEK, at(2026, 9, 25), zone)
        val events = listOf(
            listen(at(2026, 9, 22), artist = "B", avatar = "https://a1.sndcdn.com/images/default_avatar_large.png", artwork = "bcover"),
            listen(at(2026, 9, 22), trackId = 3, artist = "C", avatar = "https://i1.sndcdn.com/avatars-c.jpg"),
        )
        val report = ListeningReports.build(ReportPeriod.WEEK, window, events, null, zone)
        assertEquals("bcover", report.topArtists.first { it.name == "B" }.imageUrl)
        assertEquals("https://i1.sndcdn.com/avatars-c.jpg", report.topArtists.first { it.name == "C" }.imageUrl)
    }
}

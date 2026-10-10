import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.home.ReleaseWeeks
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/** New songs grouped by release Friday (issue #66). */
class ReleaseWeeksTest {

    private fun track(id: Long, date: String?) =
        Track(id = id, title = "t$id", artworkUrl = null, durationMs = 1_000L, user = null, releaseDate = date)

    @Test
    fun `songs fall into this friday, last friday and earlier`() {
        // Tuesday 7 October 2026: this Friday is 2 October, last Friday 25 September.
        val today = LocalDate.of(2026, 10, 7)
        val groups = ReleaseWeeks.group(
            listOf(
                track(1, "2026-10-03T00:00:00Z"),
                track(2, "2026-10-02"),
                track(3, "2026-09-27"),
                track(4, "2026-09-01"),
                track(5, null),
            ),
            today,
        )
        assertEquals(listOf(ReleaseWeeks.Week.THIS_FRIDAY, ReleaseWeeks.Week.LAST_FRIDAY, ReleaseWeeks.Week.EARLIER), groups.map { it.week })
        assertEquals(listOf(1L, 2L), groups[0].tracks.map { it.id })
        assertEquals(LocalDate.of(2026, 10, 2), groups[0].since)
        assertEquals(listOf(3L), groups[1].tracks.map { it.id })
        assertEquals(listOf(4L, 5L), groups[2].tracks.map { it.id })
    }
}

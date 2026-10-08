import com.alananasss.kittytune.ui.profile.ReleaseDate
import org.junit.Test
import java.time.LocalDate
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A release is dated, not counted in days (round 2 of the tester's list, item 7). */
class ReleaseDateTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val ru = Locale.forLanguageTag("ru")
    private val en = Locale.forLanguageTag("en")

    @Test
    fun `this year has no year, in the right case of the month`() {
        assertEquals("9 сентября", ReleaseDate.format(LocalDate.of(2026, 9, 9), today, ru))
        assertEquals("September 9", ReleaseDate.format(LocalDate.of(2026, 9, 9), today, en))
    }

    @Test
    fun `another year carries its year`() {
        assertEquals("9 сентября 2025", ReleaseDate.format(LocalDate.of(2025, 9, 9), today, ru))
        assertEquals("September 9, 2025", ReleaseDate.format(LocalDate.of(2025, 9, 9), today, en))
    }

    @Test
    fun `a new year makes last year's releases carry it`() {
        val nextYear = LocalDate.of(2027, 1, 2)
        assertEquals("9 сентября 2026", ReleaseDate.format(LocalDate.of(2026, 9, 9), nextYear, ru))
    }

    @Test
    fun `the other languages write it their way`() {
        val date = LocalDate.of(2025, 3, 5)
        assertEquals("5. März 2025", ReleaseDate.format(date, today, Locale.forLanguageTag("de")))
        assertEquals("5 mars 2025", ReleaseDate.format(date, today, Locale.forLanguageTag("fr")))
        assertEquals("2025. március 5.", ReleaseDate.format(date, today, Locale.forLanguageTag("hu")))
    }

    @Test
    fun `it reads the dates the catalogues give`() {
        assertEquals(LocalDate.of(2025, 9, 9), ReleaseDate.parse("2025-09-09"))
        assertEquals(LocalDate.of(2025, 9, 9), ReleaseDate.parse("2025-09-09T12:00:00Z"))
        assertNull(ReleaseDate.parse(""))
        assertNull(ReleaseDate.parse("soon"))
    }
}

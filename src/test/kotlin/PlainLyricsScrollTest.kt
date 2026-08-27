import com.alananasss.kittytune.ui.player.lyrics.LyricsScrolling
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The untimed-lyrics scroll position, which is a pure function of the playback position.
 *
 * That purity is the whole point of the rework in issue #33: it is what makes the text resume where
 * it was after a restart, keep its place while the panel is shut, and be right on the first frame
 * after it opens. So it is worth pinning down as a function rather than only in the UI.
 */
class PlainLyricsScrollTest {

    private fun target(positionMs: Float, speed: Float = 1f, lines: Int = 100) =
        LyricsScrolling.plainScrollTarget(positionMs, speed, lines)

    @Test
    fun `starts at the top`() {
        val t = target(0f)
        assertEquals(0, t.index)
        assertEquals(0f, t.fraction)
    }

    @Test
    fun `advances at the base rate`() {
        // 0.3 lines per second at 1x, so ten seconds is three lines exactly.
        val t = target(10_000f)
        assertEquals(3, t.index)
        assertEquals(0f, t.fraction, 0.001f)
    }

    @Test
    fun `speed scales the rate`() {
        assertEquals(6, target(10_000f, speed = 2f).index)
        assertEquals(1, target(10_000f, speed = 0.5f).index)
    }

    @Test
    fun `part way through a line is reported as a fraction`() {
        // Half a line past line three.
        val halfLineMs = 500f / LyricsScrolling.PLAIN_BASE_LINES_PER_SEC
        val t = target(10_000f + halfLineMs)
        assertEquals(3, t.index)
        assertEquals(0.5f, t.fraction, 0.01f)
    }

    @Test
    fun `settles on the last line instead of straining past it`() {
        val t = target(10_000_000f, lines = 20)
        assertEquals(19, t.index)
        // Zero, not a fraction of a line that does not exist below it.
        assertEquals(0f, t.fraction)
    }

    @Test
    fun `no lines is not a crash`() {
        val t = target(10_000f, lines = 0)
        assertEquals(0, t.index)
        assertEquals(0f, t.fraction)
    }

    /**
     * The property the panel relies on: asking twice for the same position gives the same answer, so
     * closing and reopening cannot lose or double the progress.
     */
    @Test
    fun `same position gives the same place`() {
        repeat(20) { i ->
            val ms = i * 3_137f
            assertEquals(target(ms), target(ms))
        }
    }

    @Test
    fun `progress never goes backwards as the track plays`() {
        var last = target(0f)
        for (ms in 0..600_000 step 997) {
            val now = target(ms.toFloat())
            assertTrue(
                now.index > last.index || (now.index == last.index && now.fraction >= last.fraction - 0.001f),
                "went backwards at $ms ms: $last -> $now",
            )
            last = now
        }
    }

    @Test
    fun `wheel step is notches times lines times line height`() {
        assertEquals(360f, LyricsScrolling.wheelStepPx(notches = 1f, lines = 3f, lineHeightPx = 120f))
        assertEquals(-720f, LyricsScrolling.wheelStepPx(notches = -2f, lines = 3f, lineHeightPx = 120f))
        // Doubling the setting doubles the distance, which is the point of exposing it.
        assertEquals(
            2 * LyricsScrolling.wheelStepPx(1f, 3f, 120f),
            LyricsScrolling.wheelStepPx(1f, 6f, 120f),
        )
    }
}

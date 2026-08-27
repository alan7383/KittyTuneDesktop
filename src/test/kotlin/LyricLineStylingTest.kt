import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.LyricsDisplayStyle
import com.alananasss.kittytune.ui.player.lyrics.LyricLineStyling
import com.alananasss.kittytune.ui.player.lyrics.LyricsScrolling
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a line of lyrics is treated by its distance from the one being sung (issue #33).
 *
 * Written down because this decision used to exist twice — once in the full screen, once in the side panel —
 * and the two had drifted into doing opposite things under the same setting name. One copy is only half the
 * fix; the other half is a test that says what the copy is supposed to do.
 *
 * The scale figures come from the request itself, which arrived as a sketch of five stacked lines labelled
 * "more lower / lower / standard / lower / more lower": size falls away with distance, symmetrically, and
 * stops falling once a line is far enough away to be decoration.
 */
class LyricLineStylingTest {

    private fun scaleAt(distance: Int, style: LyricsDisplayStyle = LyricsDisplayStyle.SCALE) =
        LyricLineStyling.treatmentFor(style, distance).scale

    @Test
    fun `the current line is never scaled down`() {
        for (style in LyricsDisplayStyle.entries) {
            assertEquals(1f, LyricLineStyling.treatmentFor(style, 0).scale, "style $style")
        }
    }

    /** The whole point of the request: a step per line, not one step for everything else. */
    @Test
    fun `scale falls away with distance`() {
        assertTrue(scaleAt(1) < scaleAt(0), "one line away should be smaller than the current one")
        assertTrue(scaleAt(2) < scaleAt(1), "two lines away should be smaller than one")
        assertTrue(scaleAt(3) < scaleAt(2), "three lines away should be smaller than two")
    }

    /** Symmetric: the sketch shows the same reduction above and below the current line. */
    @Test
    fun `scale is the same either side of the current line`() {
        for (distance in 1..4) {
            assertEquals(scaleAt(distance), scaleAt(-distance), "distance $distance")
        }
    }

    /**
     * It stops falling. Without a floor, a long song would shrink its far lines to nothing, and the far
     * lines are still text someone might want to read.
     */
    @Test
    fun `scale bottoms out rather than vanishing`() {
        assertEquals(scaleAt(3), scaleAt(40))
        assertTrue(scaleAt(40) > 0.5f)
    }

    /** The other two styles do not touch size at all — that is what makes them the other two styles. */
    @Test
    fun `only the scale style scales`() {
        for (distance in -3..3) {
            assertEquals(1f, scaleAt(distance, LyricsDisplayStyle.STANDARD))
            assertEquals(1f, scaleAt(distance, LyricsDisplayStyle.FOCUS))
        }
    }

    @Test
    fun `focus blurs everything but the current line`() {
        val active = LyricLineStyling.treatmentFor(LyricsDisplayStyle.FOCUS, 0)
        val other = LyricLineStyling.treatmentFor(LyricsDisplayStyle.FOCUS, 1)
        assertEquals(0.dp, active.blur)
        assertTrue(other.blur > 0.dp)
        assertTrue(other.alpha < active.alpha)
    }

    /** Blur is the one figure a view may set for itself: a panel line is a third the size of a headline. */
    @Test
    fun `the focus blur radius is the caller's to choose`() {
        val soft = LyricLineStyling.treatmentFor(LyricsDisplayStyle.FOCUS, 1, focusBlur = 1.dp)
        assertEquals(1.dp, soft.blur)
    }

    @Test
    fun `nothing but focus blurs`() {
        assertEquals(0.dp, LyricLineStyling.treatmentFor(LyricsDisplayStyle.STANDARD, 2).blur)
        assertEquals(0.dp, LyricLineStyling.treatmentFor(LyricsDisplayStyle.SCALE, 2).blur)
    }

    /**
     * What is still to come stays brighter than what has gone.
     *
     * Not cosmetic: the lines below the current one are the ones being read ahead, and dimming them equally
     * with the ones already sung is what makes a lyrics view feel like a log rather than a score.
     */
    @Test
    fun `upcoming lines are brighter than sung ones`() {
        for (style in listOf(LyricsDisplayStyle.STANDARD, LyricsDisplayStyle.SCALE)) {
            val past = LyricLineStyling.treatmentFor(style, -1).alpha
            val upcoming = LyricLineStyling.treatmentFor(style, 1).alpha
            assertTrue(upcoming > past, "style $style: upcoming $upcoming should beat past $past")
            assertTrue(past > 0f)
        }
    }
}

/**
 * The pace of the untimed-lyrics auto-scroll (issue #33).
 *
 * The unit is the whole point. It was dp per second, and dp is wrong for reading: the full screen draws plain
 * text at the size the reader chose while the side panel draws it small, so the same figure moved a third of a
 * line per second in one and nearly a whole line in the other. "1.5×" meant two different speeds depending on
 * where you were reading.
 *
 * It is now lines per second outright — the scroll target is a line index and a fraction of one, with no type
 * size anywhere in it — so the two views cannot disagree at all rather than merely agreeing today. What is
 * left to pin down is that the pace itself did not change while the unit did.
 *
 * @see PlainLyricsScrollTest for the target function's own behaviour.
 */
class PlainScrollPaceTest {

    /** How far the text gets in one second at 1x, in lines. */
    private fun linesPerSecond(speed: Float = 1f): Float {
        val after = LyricsScrolling.plainScrollTarget(1_000f, speed, lineCount = 1_000)
        return after.index + after.fraction
    }

    @Test
    fun `the speed setting scales the pace`() {
        assertEquals(linesPerSecond() * 1.5f, linesPerSecond(1.5f), 0.0001f)
        assertEquals(linesPerSecond() * 0.5f, linesPerSecond(0.5f), 0.0001f)
    }

    /**
     * The pace the full screen already had at a 42 sp setting, which is what the report was comparing
     * against: about 18 dp per second, which at a 59 dp line is 0.3 lines per second.
     */
    @Test
    fun `the reference pace is unchanged`() {
        val fullScreenLineDp = 42f * 1.4f
        val dpPerSecond = linesPerSecond() * fullScreenLineDp
        assertTrue(dpPerSecond in 17f..18.5f, "expected about 18 dp/s at a 42 sp setting, got $dpPerSecond")
    }

    /**
     * No type size takes part, so a line takes the same time to pass in a side panel as on a full
     * screen by construction rather than by arithmetic that happens to line up.
     */
    @Test
    fun `the pace does not depend on the view`() {
        assertEquals(LyricsScrolling.PLAIN_BASE_LINES_PER_SEC, linesPerSecond(), 0.0001f)
    }
}

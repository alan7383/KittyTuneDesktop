import com.alananasss.kittytune.data.LyricsSync
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Two-point lyrics sync: an offset that drifts evenly from one point of the song to another. */
class LyricsSyncTest {

    // +3 s at 0:10, +12 s at 3:10: the tester's example.
    private val drifting = LyricsSync(offsetMs = 3_000, anchorMs = 10_000, endAtMs = 190_000, endOffsetMs = 12_000)

    @Test
    fun aSingleOffsetIsTheSameEverywhere() {
        val single = LyricsSync(offsetMs = 1_500)
        assertEquals(1_500, single.offsetAt(0))
        assertEquals(1_500, single.offsetAt(200_000))
        assertEquals(8_500, single.audioPositionFor(10_000))
    }

    @Test
    fun twoPointsAreJoinedByAStraightLineThatCarriesOn() {
        assertEquals(3_000, drifting.offsetAt(10_000))
        assertEquals(12_000, drifting.offsetAt(190_000))
        assertEquals(7_500, drifting.offsetAt(100_000))
        // Beyond the points the drift goes on at the same rate.
        assertEquals(2_500, drifting.offsetAt(0))
        assertEquals(13_000, drifting.offsetAt(210_000))
    }

    @Test
    fun seekingToALineLandsWhereTheLyricsReachIt() {
        for (lyricTime in listOf(0L, 15_000L, 104_000L, 230_000L)) {
            val audio = drifting.audioPositionFor(lyricTime)
            val reached = audio + drifting.offsetAt(audio)
            assertTrue(kotlin.math.abs(reached - lyricTime) <= 1, "lyric $lyricTime reached at $reached")
        }
    }

    @Test
    fun theButtonsChangeThePointNearerThePlayhead() {
        assertEquals(3_500, drifting.adjustedAt(30_000, 500).offsetMs)
        assertEquals(12_000, drifting.adjustedAt(30_000, 500).endOffsetMs)
        assertEquals(12_500, drifting.adjustedAt(170_000, 500).endOffsetMs)
        assertEquals(3_000, drifting.adjustedAt(170_000, 500).offsetMs)
    }

    @Test
    fun pinningKeepsTheOffsetTheLyricsHaveThere() {
        val single = LyricsSync(offsetMs = 2_000)
        val started = single.withStartAt(15_000)
        assertEquals(15_000, started.anchorMs)
        assertEquals(2_000, started.offsetMs)
        val ended = started.withEndAt(180_000)!!
        assertTrue(ended.isTwoPoint)
        assertEquals(2_000, ended.offsetAt(180_000))
        assertEquals(drifting.offsetAt(100_000), drifting.singleAt(100_000).offsetAt(0))
        assertFalse(drifting.singleAt(100_000).isTwoPoint)
    }

    @Test
    fun anEndTooCloseToTheStartIsRefused() {
        assertNull(LyricsSync(offsetMs = 0, anchorMs = 60_000).withEndAt(70_000))
        assertNull(LyricsSync(offsetMs = 0, anchorMs = 60_000).withEndAt(30_000))
    }

    @Test
    fun movingTheStartPastTheEndDropsTheEnd() {
        val moved = drifting.withStartAt(185_000)
        assertFalse(moved.isTwoPoint)
        assertEquals(drifting.offsetAt(185_000), moved.offsetMs)
    }

    @Test
    fun nothingSetIsNone() {
        assertTrue(LyricsSync.NONE.isNone)
        assertFalse(LyricsSync(offsetMs = 100).isNone)
        assertFalse(drifting.isNone)
    }
}

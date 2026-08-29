import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where a click on a lyric line sends the playhead (issue #33).
 *
 * "When you click on the text, playback does not start from the very beginning, but continues from the
 * line where the song stopped or is currently playing."
 *
 * The two lyrics views each had their own copy of this arithmetic, and the clamp that was added to stop
 * a mismatched sheet seeking past the end of a track introduced a second way to land at zero. These are
 * the cases that distinguish the two.
 */
class LyricSeekTargetTest {

    private fun line(startMs: Long) = LyricLine(text = "…", startTime = startMs, endTime = startMs + 3_000)

    @Test
    fun `a line inside the track seeks to that line`() {
        assertEquals(
            120_000L,
            LyricsUtils.seekTargetFor(line(120_000L), lyricsOffsetMs = 0L, durationMs = 200_000L),
        )
    }

    /**
     * The regression this exists for. A track whose metadata carried no duration reports 0 until the
     * decoder opens the stream, and clamping to `duration - 1` then bounded every seek by zero.
     */
    @Test
    fun `an unknown duration does not collapse the seek to the start`() {
        assertEquals(
            120_000L,
            LyricsUtils.seekTargetFor(line(120_000L), lyricsOffsetMs = 0L, durationMs = 0L),
        )
    }

    /**
     * A sheet matched from a longer song. Clamping to the last millisecond hands the decoder an
     * immediate EOF, so the track "ends": the queue advances, or repeat-one starts it again from the
     * beginning — the report by a longer route. Nothing to seek to means no seek.
     */
    @Test
    fun `a line starting after the track ends seeks nowhere`() {
        assertNull(LyricsUtils.seekTargetFor(line(400_000L), lyricsOffsetMs = 0L, durationMs = 200_000L))
        assertNull(
            LyricsUtils.seekTargetFor(line(200_000L), lyricsOffsetMs = 0L, durationMs = 200_000L),
            "the last millisecond is past the end as far as the decoder is concerned",
        )
    }

    /** The offset shifts the lyrics against the audio, so it comes off the line's own start. */
    @Test
    fun `the lyrics offset is applied`() {
        assertEquals(
            118_500L,
            LyricsUtils.seekTargetFor(line(120_000L), lyricsOffsetMs = 1_500L, durationMs = 200_000L),
        )
        assertEquals(
            121_500L,
            LyricsUtils.seekTargetFor(line(120_000L), lyricsOffsetMs = -1_500L, durationMs = 200_000L),
        )
    }

    /** An offset large enough to push the first lines before the track starts. */
    @Test
    fun `an offset past the start of the track lands on the start`() {
        assertEquals(
            0L,
            LyricsUtils.seekTargetFor(line(800L), lyricsOffsetMs = 5_000L, durationMs = 200_000L),
        )
    }

    /** Clicking the opening line of a normal sheet, which is what "from the beginning" is allowed to mean. */
    @Test
    fun `the first line still seeks to zero`() {
        assertEquals(0L, LyricsUtils.seekTargetFor(line(0L), lyricsOffsetMs = 0L, durationMs = 200_000L))
    }
}

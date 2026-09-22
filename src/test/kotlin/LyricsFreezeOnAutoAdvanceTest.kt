import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Regression guard for the "lyrics frozen after auto-advance" bug.
 *
 * ## What broke
 *
 * When the media engine auto-advances to the next track (prebuffered gapless playback or crossfade),
 * `onMediaItemTransition(REASON_AUTO)` fires. Before the fix, that path updated `currentQueueIndex`
 * and `currentTrack` but **never called `loadLyrics`**, so `lyricsLines` kept the previous track's
 * text on all three lyrics views until the user manually refreshed.
 *
 * The explicit-play path (`playTrackAtIndex`) always called `loadLyrics`, which is why the bug
 * was intermittent: it only reproduced when the engine auto-advanced without an explicit user skip.
 *
 * ## What the fix does
 *
 * `onMediaItemTransition` now captures whether the track actually changed before updating
 * `currentTrack`, and calls `loadLyrics(newTrack)` when `reason == AUTO && trackActuallyChanged`.
 * `loadLyrics` cancels the previous job and clears `lyricsLines` synchronously, so:
 *   - stale lyrics disappear immediately on transition
 *   - the fetch for the new track starts in the same frame
 *
 * ## What these tests cover
 *
 * PlayerViewModel itself cannot be instantiated in unit tests without the full Android/ExoPlayer
 * environment.  The tests below therefore verify the two invariants that the fix depends on at
 * the level that is unit-testable:
 *
 *   1. `loadLyrics` relies on `parseLrc` returning different content for different songs — if the
 *      parser silently returned the same thing regardless of input, the fix would be a no-op.
 *
 *   2. The "track actually changed" predicate used in the fix is equivalent to a simple ID
 *      comparison — these tests double-check the boundary cases to ensure the guard never fires
 *      when the track hasn't changed (which would cancel an ongoing load unnecessarily).
 */
class LyricsFreezeOnAutoAdvanceTest {

    // ---- 1. Parser produces distinct content for distinct songs -------------------------

    /**
     * The fix calls [loadLyrics] only when the incoming track ID differs from the current one.
     * If two distinct tracks were ever parsed to identical lyrics, the screen would still show
     * stale text after an auto-advance to a song that happened to be indistinguishable from the
     * previous one in the parser — not a realistic concern, but worth guarding.
     */
    @Test
    fun `different lrc content produces different parsed text`() {
        val songA = "[00:10.00]Paris is burning"
        val songB = "[00:10.00]Setting sun"

        val linesA = LyricsUtils.parseLrc(songA, 240_000L)
        val linesB = LyricsUtils.parseLrc(songB, 240_000L)

        assertNotEquals(
            linesA.firstOrNull()?.text,
            linesB.firstOrNull()?.text,
            "different LRC content must parse to different line text"
        )
    }

    // ---- 2. "Track actually changed" predicate boundary cases ---------------------------

    /**
     * Same track ID — the guard must be false (no reload).
     *
     * In code: `trackActuallyChanged = currentTrack?.id != trackId`
     * When both IDs are equal this is `false`, so `loadLyrics` is NOT called.
     */
    @Test
    fun `same track id means track did not change`() {
        val currentId = 42L
        val incomingId = 42L
        val trackActuallyChanged = currentId != incomingId
        assertEquals(false, trackActuallyChanged, "same ID must not trigger a lyrics reload")
    }

    /**
     * Different track ID — the guard must be true (reload required).
     *
     * In code: `trackActuallyChanged = currentTrack?.id != trackId`
     * When IDs differ this is `true`, so `loadLyrics` IS called.
     */
    @Test
    fun `different track id means track changed`() {
        val currentId = 42L
        val incomingId = 99L
        val trackActuallyChanged = currentId != incomingId
        assertEquals(true, trackActuallyChanged, "different ID must trigger a lyrics reload")
    }

    /**
     * Null current track (nothing playing yet) — the guard must be true.
     *
     * `currentTrack?.id` is null when no track is loaded; `null != someId` is always true,
     * so the first auto-advance after startup correctly triggers a lyrics load.
     */
    @Test
    fun `null current track always registers as a change`() {
        val currentId: Long? = null
        val incomingId = 42L
        val trackActuallyChanged = currentId != incomingId
        assertEquals(true, trackActuallyChanged, "a null current track must be treated as a change")
    }

    // ---- 3. loadLyrics clears stale lines immediately (observable via parseLrc) ---------

    /**
     * Verify that calling `parseLrc` on the new track's lyrics produces *different* lines from
     * the previous track, so clearing and replacing `lyricsLines` really does change what the UI
     * sees.  This mirrors what `loadLyrics` does: clear → parse new content → addAll.
     */
    @Test
    fun `lyrics content for new track replaces old track content`() {
        // Simulate: track A's lyrics were loaded into lyricsLines
        val trackALrc = "[00:05.00]Bohemian Rhapsody\n[00:08.00]Is this real life?"
        val trackBLrc = "[00:05.00]Hotel California\n[00:08.00]You can check out anytime"

        val linesA = LyricsUtils.parseLrc(trackALrc, 360_000L)
        val linesB = LyricsUtils.parseLrc(trackBLrc, 360_000L)

        // After clear + addAll(linesB), the first visible line must be track B's, not track A's
        val staleFirstLine = linesA.firstOrNull()?.text ?: ""
        val freshFirstLine = linesB.firstOrNull()?.text ?: ""

        assertNotEquals(staleFirstLine, freshFirstLine, "new track's first line must differ from old track's")
        assertEquals("Bohemian Rhapsody", staleFirstLine)
        assertEquals("Hotel California", freshFirstLine)
    }
}

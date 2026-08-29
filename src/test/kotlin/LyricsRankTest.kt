import com.alananasss.kittytune.data.LyricsMatcher
import org.junit.Test
import kotlin.test.assertTrue

/**
 * The order provider results are believed in (issue #33).
 *
 * "As for the lyrics, I still find other lyrics, and when I do a manual search, it gives me the correct
 * one, without any changes."
 *
 * That sentence is the whole specification. The correct sheet was in the same response the automatic
 * search read — picking it by hand needed no different query — so nothing was missing and nothing was
 * being rejected. It was being outranked, and it was being outranked by a rule that could not lose:
 * `syncTier * 10 + matchScore`, where the score never exceeds 1.
 */
class LyricsRankTest {

    private val wordSync = LyricsMatcher.SYNC_TIER_WORD
    private val lineSync = LyricsMatcher.SYNC_TIER_LINE
    private val plain = LyricsMatcher.SYNC_TIER_PLAIN

    /** The report, as two numbers. */
    @Test
    fun `the right song without word sync beats a stranger with it`() {
        val rightSongLineSynced = LyricsMatcher.rank(lineSync, matchScore = 0.82f)
        val wrongSongWordSynced = LyricsMatcher.rank(wordSync, matchScore = 0.44f)
        assertTrue(
            rightSongLineSynced > wrongSongWordSynced,
            "a confident match must not lose to a better-synchronised stranger",
        )
    }

    /**
     * The strongest form of the same claim, and the one worth stating outright: words the reader can
     * follow are worth something even untimed, and timed words from another song are worth less than
     * nothing.
     */
    @Test
    fun `the right song with no timings at all still beats a stranger in perfect sync`() {
        assertTrue(
            LyricsMatcher.rank(plain, matchScore = 0.90f) >
                LyricsMatcher.rank(wordSync, matchScore = 0.50f)
        )
    }

    /** Within one bracket nothing has changed: sync is still what decides. */
    @Test
    fun `among equally confident matches the timings decide`() {
        assertTrue(
            LyricsMatcher.rank(wordSync, matchScore = 0.62f) >
                LyricsMatcher.rank(lineSync, matchScore = 0.95f)
        )
        assertTrue(
            LyricsMatcher.rank(lineSync, matchScore = 0.62f) >
                LyricsMatcher.rank(plain, matchScore = 0.95f)
        )
    }

    /** And among two plausible-but-not-confident candidates, likewise. */
    @Test
    fun `among equally unconfident matches the timings still decide`() {
        assertTrue(
            LyricsMatcher.rank(wordSync, matchScore = 0.36f) >
                LyricsMatcher.rank(lineSync, matchScore = 0.59f)
        )
    }

    /** Score settles ties inside a bracket and a tier, which is all it was ever meant to do. */
    @Test
    fun `the match score breaks ties within a tier`() {
        assertTrue(
            LyricsMatcher.rank(wordSync, matchScore = 0.91f) >
                LyricsMatcher.rank(wordSync, matchScore = 0.72f)
        )
    }

    /**
     * The provider preference is a nudge, not a vote. It decides a genuine tie; it must never carry a
     * candidate past one that fits the track better.
     */
    @Test
    fun `the provider preference cannot outweigh a better match`() {
        val preferredButWorse = LyricsMatcher.rank(wordSync, matchScore = 0.65f, providerBonus = 0.05f)
        val otherButBetter = LyricsMatcher.rank(wordSync, matchScore = 0.80f)
        assertTrue(otherButBetter > preferredButWorse)

        val tieGoesToPreferred = LyricsMatcher.rank(wordSync, matchScore = 0.80f, providerBonus = 0.05f)
        assertTrue(tieGoesToPreferred > otherButBetter)
    }

    /** The bracket boundary is the same number a title needs to pass on its own. */
    @Test
    fun `the confidence bracket starts exactly at the acceptance threshold`() {
        val justConfident = LyricsMatcher.rank(plain, matchScore = LyricsMatcher.CONFIDENT_MATCH)
        val justShortOfIt = LyricsMatcher.rank(wordSync, matchScore = LyricsMatcher.CONFIDENT_MATCH - 0.01f)
        assertTrue(justConfident > justShortOfIt)
    }
}

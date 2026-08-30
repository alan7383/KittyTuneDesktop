import com.alananasss.kittytune.data.mix.MixProfile
import com.alananasss.kittytune.data.mix.MixRanking
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two things that made the mix misbehave in his hands (issue #33).
 *
 * "J'écris snorunt, un vrai artiste qui existe sur SoundCloud, et il me sort absolument rien" and "la queue est
 * bizarre parfois, parfois il va sortir un titre".
 *
 * Both turned out to be about what a recommender does with a result it should not have accepted. The first was
 * upstream of the arithmetic — the wrong artist was resolved and the only endpoint used for them 404s — and is
 * covered by [MixArtistSeedTest]. This is the second: a candidate SoundCloud will not stream, which looks exactly
 * like a good one apart from two fields nothing else in the app has to care about.
 */
class MixPlayabilityTest {

    private val now = 1_700_000_000_000L

    private fun track(
        id: Long,
        policy: String? = "MONETIZE",
        streamable: Boolean? = true,
        durationMs: Long = 200_000,
    ) = Track(
        id = id,
        title = "track $id",
        artworkUrl = null,
        user = User(id = id * 100, username = "artist $id", avatarUrl = null),
        durationMs = durationMs,
        playbackCount = 1_000,
        policy = policy,
        streamable = streamable,
    )

    private val emptyTaste = MixProfile.build(emptyList(), emptyList(), now)

    /**
     * A blocked track is the blank row in his queue. Every other list in the app survives one because a human
     * chose it and can see it failed; a mix chooses a hundred nobody looked at.
     */
    @Test
    fun `a blocked track is not a candidate`() {
        assertFalse(MixRanking.isPlayable(track(1, policy = "BLOCK")))
        assertNull(MixRanking.score(MixRanking.Candidate(track(1, policy = "BLOCK"), 1f), emptyTaste))
    }

    /** Worse than a block, because it plays and then stops after thirty seconds. */
    @Test
    fun `a snippet is not a candidate`() {
        assertFalse(MixRanking.isPlayable(track(2, policy = "SNIPPET")))
        assertNull(MixRanking.score(MixRanking.Candidate(track(2, policy = "SNIPPET"), 1f), emptyTaste))
    }

    @Test
    fun `an unstreamable track is not a candidate`() {
        assertFalse(MixRanking.isPlayable(track(3, streamable = false)))
        assertNull(MixRanking.score(MixRanking.Candidate(track(3, streamable = false), 1f), emptyTaste))
    }

    /** The normal case, and the two fields being absent must not be read as a refusal. */
    @Test
    fun `a monetized track is playable and so is one that says nothing`() {
        assertTrue(MixRanking.isPlayable(track(4, policy = "MONETIZE")))
        assertTrue(MixRanking.isPlayable(track(5, policy = "ALLOW")))
        assertTrue(MixRanking.isPlayable(track(6, policy = null, streamable = null)))
    }

    /** Their casing is not consistent across endpoints, and a lowercase block is still a block. */
    @Test
    fun `the policy is read whatever its casing`() {
        assertFalse(MixRanking.isPlayable(track(7, policy = "block")))
        assertFalse(MixRanking.isPlayable(track(8, policy = "Snippet")))
    }

    /** And the whole point: an unplayable track never reaches the queue, however well it scored otherwise. */
    @Test
    fun `unplayable tracks are absent from the running order`() {
        val candidates = listOf(
            MixRanking.Candidate(track(10, policy = "BLOCK"), 1f),
            MixRanking.Candidate(track(11, streamable = false), 1f),
            MixRanking.Candidate(track(12, policy = "SNIPPET"), 1f),
            MixRanking.Candidate(track(13), 0.2f),
        )
        val order = MixRanking.order(candidates, emptyTaste, size = 10, seed = 1)
        assertEquals(listOf(13L), order.map { it.id })
    }

    /**
     * A hundred is what he asked for, and it is reachable only if the candidates are there — so this checks the
     * shaping does not quietly cap it lower. Fifty artists, two tracks each, is exactly the boundary.
     */
    @Test
    fun `a hundred slots are filled when there are candidates for them`() {
        val candidates = (1L..60L).flatMap { artist ->
            listOf(
                MixRanking.Candidate(
                    Track(
                        id = artist * 10,
                        title = "a$artist",
                        artworkUrl = null,
                        user = User(id = artist, username = "artist$artist", avatarUrl = null),
                        durationMs = 200_000,
                        playbackCount = 100,
                    ),
                    1f,
                ),
                MixRanking.Candidate(
                    Track(
                        id = artist * 10 + 1,
                        title = "b$artist",
                        artworkUrl = null,
                        user = User(id = artist, username = "artist$artist", avatarUrl = null),
                        durationMs = 200_000,
                        playbackCount = 90,
                    ),
                    1f,
                ),
            )
        }
        val order = MixRanking.order(candidates, emptyTaste, size = 100, seed = 7)
        assertEquals(100, order.size, "a hundred candidates' worth of artists should fill a hundred slots")
        assertEquals(100, order.map { it.id }.distinct().size, "and none of them twice")
    }
}

import com.alananasss.kittytune.domain.User
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArtistSearchAndPresetsTest {

    private fun formatFollowers(count: Long): String {
        return when {
            count >= 1_000_000 -> "${count / 1_000_000}M followers"
            count >= 1_000 -> "${count / 1_000}K followers"
            count > 0 -> "$count followers"
            else -> "Artist"
        }
    }

    @Test
    fun `formats follower counts into human readable metrics`() {
        assertEquals("12M followers", formatFollowers(12_400_000L))
        assertEquals("1M followers", formatFollowers(1_000_000L))
        assertEquals("350K followers", formatFollowers(350_000L))
        assertEquals("1K followers", formatFollowers(1_200L))
        assertEquals("450 followers", formatFollowers(450L))
        assertEquals("Artist", formatFollowers(0L))
    }

    @Test
    fun `top result ranker prioritizes verified popular artists over unverified low-follower matches`() {
        val fanAccount = User(
            id = 101L,
            username = "jul_fan_92",
            avatarUrl = null,
            verified = false,
            followersCount = 42,
            trackCount = 1
        )
        val officialJul = User(
            id = 202L,
            username = "Jul",
            avatarUrl = "https://example.com/jul.jpg",
            verified = true,
            followersCount = 149_000,
            trackCount = 250
        )
        val tributeAccount = User(
            id = 303L,
            username = "JUL Team",
            avatarUrl = null,
            verified = false,
            followersCount = 300,
            trackCount = 5
        )

        // fanAccount is first in raw results (index 0), official Jul is at index 1
        val rawResults = listOf(fanAccount, officialJul, tributeAccount)

        val bestArtist = com.alananasss.kittytune.domain.TopResultRanker.findTopArtist(rawResults, "jul")

        assertNotNull(bestArtist)
        assertEquals(officialJul.id, bestArtist.id)
        assertEquals("Jul", bestArtist.username)
        assertTrue(bestArtist.verified)
    }

    @Test
    fun `top result ranker handles daft punk query correctly`() {
        val coverBand = User(
            id = 1L,
            username = "Daft Punk Tribute",
            avatarUrl = null,
            verified = false,
            followersCount = 1_500
        )
        val officialDaftPunk = User(
            id = 2L,
            username = "Daft Punk",
            avatarUrl = "https://example.com/dp.jpg",
            verified = true,
            followersCount = 3_200_000
        )

        val winner = com.alananasss.kittytune.domain.TopResultRanker.findTopArtist(
            listOf(coverBand, officialDaftPunk),
            "daft punk"
        )
        assertNotNull(winner)
        assertEquals(officialDaftPunk.id, winner.id)
    }
}

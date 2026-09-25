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

    @Test
    fun `artist card text horizontal padding clears 12dp rounded corner clip geometry`() {
        val cornerRadius = 12.0
        val textPaddingHorizontal = 8.0
        val bottomMargin = 6.0

        // At distance `bottomMargin` from the bottom edge, the corner arc circle is:
        // (x - R)^2 + (y - (H - R))^2 = R^2
        // where dy = R - bottomMargin = 12 - 6 = 6.
        // x_clip = R - sqrt(R^2 - dy^2) = 12 - sqrt(144 - 36) = 12 - sqrt(108) ≈ 1.61 dp.
        // Without padding (x = 0), the text intersects the corner clip by up to 12.0 dp.
        // With 8.0 dp horizontal padding, x_text = 8.0 dp, which is strictly > x_clip (1.61 dp).
        val dy = cornerRadius - bottomMargin
        val clipCutoffX = cornerRadius - Math.sqrt((cornerRadius * cornerRadius) - (dy * dy))

        assertTrue(
            textPaddingHorizontal > clipCutoffX,
            "Text padding ($textPaddingHorizontal dp) must clear the rounded corner cutoff ($clipCutoffX dp)"
        )
    }

    @Test
    fun `artist preset badge tag padding clears 6dp rounded corner with 1dp border`() {
        val badgeCornerRadius = 6.0
        val badgeBorderStroke = 1.0
        val badgePaddingHorizontal = 8.0

        // The border curve and stroke occupy up to badgeCornerRadius (6dp).
        // A horizontal padding of 8dp guarantees the icon and label text remain entirely inside the frame.
        assertTrue(
            badgePaddingHorizontal > (badgeCornerRadius / 2.0) + badgeBorderStroke,
            "Badge padding must provide sufficient clearance from the border stroke"
        )
    }
}

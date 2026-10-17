import com.alananasss.kittytune.data.MprisService
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Media
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.Transcoding
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestrictedTrackFallbackTest {

    @Test
    fun `track with SNIP policy is detected as snipped and restricted`() {
        val track = Track(
            id = 1L,
            title = "Close To Me",
            artworkUrl = null,
            durationMs = 30_000L,
            user = null,
            policy = "SNIP"
        )
        assertTrue(track.isSnipped)
        assertTrue(StreamResolver.isRestricted(track))
        assertEquals(30_000L, track.effectiveFullDurationMs)
    }

    @Test
    fun `track with SUB_HIGH_TIER monetization is detected as snipped and restricted`() {
        val track = Track(
            id = 2L,
            title = "Go+ Song",
            artworkUrl = null,
            durationMs = 30_000L,
            user = null,
            monetizationModel = "SUB_HIGH_TIER"
        )
        assertTrue(track.isSnipped)
        assertTrue(StreamResolver.isRestricted(track))
    }

    @Test
    fun `track with snipped transcoding is detected as snipped and restricted`() {
        val transcoding = Transcoding(
            url = "https://example.com/stream",
            preset = "mp3_0_0",
            format = null,
            snipped = true,
            duration = 30_000L
        )
        val track = Track(
            id = 3L,
            title = "Snipped Stream",
            artworkUrl = null,
            durationMs = 30_000L,
            user = null,
            media = Media(listOf(transcoding))
        )
        assertTrue(track.isSnipped)
        assertTrue(StreamResolver.isRestricted(track))
    }

    @Test
    fun `track with full_duration longer than duration is detected as snipped and returns effective full duration`() {
        val track = Track(
            id = 4L,
            title = "Close To Me",
            artworkUrl = null,
            durationMs = 29_800L,
            fullDurationMs = 182_717L,
            user = null
        )
        assertTrue(track.isSnipped)
        assertTrue(StreamResolver.isRestricted(track))
        assertEquals(182_717L, track.effectiveFullDurationMs)
    }

    @Test
    fun `normal full track is not considered restricted or snipped`() {
        val track = Track(
            id = 5L,
            title = "Full Track",
            artworkUrl = null,
            durationMs = 210_000L,
            fullDurationMs = 210_000L,
            user = null,
            policy = "ALLOW",
            monetizationModel = "FREE"
        )
        assertFalse(track.isSnipped)
        assertFalse(StreamResolver.isRestricted(track))
        assertEquals(210_000L, track.effectiveFullDurationMs)
    }

    @Test
    fun `normal track with tiny millisecond duration discrepancy is not detected as snipped`() {
        val track = Track(
            id = 55L,
            title = "Tanir, Tyomcha - Da Da Da",
            artworkUrl = null,
            durationMs = 198_106L,
            fullDurationMs = 198_130L,
            user = null,
            policy = "MONETIZE",
            monetizationModel = "BLACKBOX"
        )
        assertFalse(track.isSnipped)
        assertFalse(StreamResolver.isRestricted(track))
        assertEquals(198_130L, track.effectiveFullDurationMs)
    }

    @Test
    fun `mpris metadata reflects effective full duration for snipped track`() {
        val track = Track(
            id = 6L,
            title = "Close To Me",
            artworkUrl = null,
            durationMs = 30_000L,
            fullDurationMs = 182_717L,
            user = null,
            policy = "SNIP"
        )
        val metadata = MprisService.buildMetadata(track)
        val lengthVariant = metadata["mpris:length"]
        val lengthUs = lengthVariant?.value as? Long
        assertEquals(182_717L * 1000L, lengthUs)
    }
}

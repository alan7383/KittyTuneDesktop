import com.alananasss.kittytune.data.cover.AnimatedCoverResolver
import com.alananasss.kittytune.data.cover.CanvasIndex
import com.alananasss.kittytune.data.cover.CanvasMatchEntry
import com.alananasss.kittytune.data.cover.CanvasMatchTier
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnimatedCoverResolverTest {

    @Test
    fun `empty title returns empty resolved animated cover`() = runBlocking {
        val result = AnimatedCoverResolver.resolve(
            title = "",
            artist = "Any Artist",
            album = null,
            durationSeconds = null,
            isrc = null
        )
        assertNull(result.squareUrl)
        assertNull(result.tallUrl)
    }

    @Test
    fun `canvas index stores and retrieves entries by isrc and song artist`() {
        val entry = CanvasMatchEntry(
            isrc = "USUM71900001",
            appleCatalogId = "1499378607",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200000L,
            sourceUrl = "https://example.com/square.mp4",
            matchTier = CanvasMatchTier.ISRC_EXACT,
            confidence = 100,
            lastMatchedAtMs = System.currentTimeMillis()
        )

        CanvasIndex.put(entry)

        val retrievedByIsrc = CanvasIndex.getByIsrc("USUM71900001")
        assertNotNull(retrievedByIsrc)
        assertEquals("https://example.com/square.mp4", retrievedByIsrc.sourceUrl)
        assertEquals(CanvasMatchTier.ISRC_EXACT, retrievedByIsrc.matchTier)

        val retrievedBySongArtist = CanvasIndex.getBySongArtist("blinding lights", "the weeknd")
        assertNotNull(retrievedBySongArtist)
        assertEquals("1499378607", retrievedBySongArtist.appleCatalogId)
    }

    @Test
    fun `canvas index preserves higher confidence match`() {
        val lowConf = CanvasMatchEntry(
            isrc = "USUM71900002",
            appleCatalogId = "1",
            title = "Song A",
            artist = "Artist A",
            album = null,
            durationMs = 180000L,
            sourceUrl = "https://example.com/low.mp4",
            matchTier = CanvasMatchTier.FUZZY,
            confidence = 50,
            lastMatchedAtMs = 1000L
        )
        val highConf = CanvasMatchEntry(
            isrc = "USUM71900002",
            appleCatalogId = "2",
            title = "Song A",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180000L,
            sourceUrl = "https://example.com/high.mp4",
            matchTier = CanvasMatchTier.ISRC_EXACT,
            confidence = 100,
            lastMatchedAtMs = 2000L
        )

        CanvasIndex.put(lowConf)
        assertEquals("https://example.com/low.mp4", CanvasIndex.getByIsrc("USUM71900002")?.sourceUrl)

        CanvasIndex.put(highConf)
        assertEquals("https://example.com/high.mp4", CanvasIndex.getByIsrc("USUM71900002")?.sourceUrl)
    }
}

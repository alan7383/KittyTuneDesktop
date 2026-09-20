import com.alananasss.kittytune.data.cover.HlsVariantResolver
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HlsVariantResolverTest {

    @Test
    fun `non-m3u8 urls are returned unmodified`() {
        val mp4Url = "https://example.com/video.mp4"
        assertEquals(mp4Url, HlsVariantResolver.resolveOptimalVariant(mp4Url))
    }

    @Test
    fun `apple music master playlist resolves to optimal single stream variant`() {
        val masterUrl = "https://mvod.itunes.apple.com/itunes-assets/HLSMusic116/v4/66/99/ad/6699ad43-68e2-8067-654b-aff5ed85a8c7/P359493201_default.m3u8"
        val resolved = HlsVariantResolver.resolveOptimalVariant(masterUrl)

        // Must resolve to a single variant m3u8, NOT the master default.m3u8
        assertFalse(resolved.endsWith("default.m3u8"), "Should resolve to a sub-variant, not master")
        assertTrue(resolved.contains("P359493201_Anull_video_gr"), "Should contain variant identifier")
        assertTrue(resolved.endsWith(".m3u8"))
    }
}

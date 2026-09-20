import com.alananasss.kittytune.audio.providers.AudioProviderOrder
import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
import com.alananasss.kittytune.audio.providers.ProviderIsrc
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProvider
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProxy
import com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode
import com.alananasss.kittytune.audio.providers.deezer.deezerCookieValue
import com.alananasss.kittytune.audio.providers.deezer.isDeezerCookieConfigured
import com.alananasss.kittytune.audio.providers.deezer.normalizeDeezerCookieInput
import com.alananasss.kittytune.audio.providers.tidal.extractTidalAccessToken
import com.alananasss.kittytune.audio.providers.tidal.extractTidalRefreshToken
import com.alananasss.kittytune.audio.providers.tidal.isTidalCookieConfigured
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioProvidersParityTest {

    @Test
    fun testAudioProviderOrderDefaultAndSerialization() {
        val defaultList = AudioProviderOrder.Default
        assertTrue(defaultList.contains(AudioProviderOrderItem.SOUNDCLOUD))
        assertTrue(defaultList.contains(AudioProviderOrderItem.YOUTUBE_MUSIC))
        assertTrue(defaultList.contains(AudioProviderOrderItem.DEEZER))
        assertTrue(defaultList.contains(AudioProviderOrderItem.TIDAL))
        assertTrue(defaultList.contains(AudioProviderOrderItem.QOBUZ))

        val serialized = AudioProviderOrder.serialize(defaultList)
        val deserialized = AudioProviderOrder.deserialize(serialized)
        assertEquals(defaultList, deserialized)

        // Test partial decoding adds missing items in default order
        val partial = AudioProviderOrder.deserialize("DEEZER,TIDAL")
        assertEquals(AudioProviderOrderItem.DEEZER, partial[0])
        assertEquals(AudioProviderOrderItem.TIDAL, partial[1])
        assertTrue(partial.contains(AudioProviderOrderItem.QOBUZ))
        assertTrue(partial.contains(AudioProviderOrderItem.SOUNDCLOUD))
        assertTrue(partial.contains(AudioProviderOrderItem.YOUTUBE_MUSIC))
    }

    @Test
    fun testProviderIsrcNormalize() {
        assertEquals("USUM71900001", ProviderIsrc.normalize("US-UM7-19-00001"))
        assertEquals("GBAYE0601477", ProviderIsrc.normalize("gb-aye-06-01477"))
        assertNull(ProviderIsrc.normalize("INVALID_ISRC"))
        assertNull(ProviderIsrc.normalize(null))
        assertNull(ProviderIsrc.normalize(""))
    }

    @Test
    fun testDeezerCookieUtils() {
        // Raw ARL string (192 hex characters typically)
        val dummyArl = "a".repeat(128)
        val rawHeader = "arl=$dummyArl; sid=xyz123; Path=/; Domain=.deezer.com"
        assertTrue(isDeezerCookieConfigured(rawHeader))

        val parsedArl = deezerCookieValue(rawHeader, "arl")
        assertEquals(dummyArl, parsedArl)
        val parsedSid = deezerCookieValue(rawHeader, "sid")
        assertEquals("xyz123", parsedSid)

        val normalized = normalizeDeezerCookieInput(rawHeader)
        assertNotNull(normalized)
        assertTrue(normalized!!.contains("arl=$dummyArl"))
    }

    @Test
    fun testTidalCookieUtils() {
        val dummyRefreshToken = "refresh_token_1234567890abcdef"
        val rawHeader = "refresh_token=$dummyRefreshToken; Path=/; Domain=.tidal.com"
        assertTrue(isTidalCookieConfigured(rawHeader))

        val token = extractTidalRefreshToken(rawHeader)
        assertEquals(dummyRefreshToken, token)
    }

    @Test
    fun testDeezerAudioProxyUriDetection() {
        assertTrue(DeezerAudioProxy.isDeezerUri("metrofuse-deezer://stream?trackId=123"))
        assertFalse(DeezerAudioProxy.isDeezerUri("https://deezer.com/track/123"))

        assertTrue(DeezerAudioProxy.isDeezerProxyUrl("http://127.0.0.1:8080/deezer-stream?url=abc"))
        assertFalse(DeezerAudioProxy.isDeezerProxyUrl("http://example.com/deezer-stream"))
    }

    @Test
    fun testDeezerEffectiveProxyUrl() {
        // Direct mode
        val directUrl = DeezerAudioProvider.effectiveProxyUrl(
            configuredProxyMode = DeezerProxyMode.DIRECT,
            configuredProxyUrl = "https://custom.proxy.com",
            globalProxyEnabled = false
        )
        assertEquals("", directUrl)

        // Render mode
        val renderUrl = DeezerAudioProvider.effectiveProxyUrl(
            configuredProxyMode = DeezerProxyMode.RENDER,
            configuredProxyUrl = "",
            globalProxyEnabled = false
        )
        assertEquals(DeezerAudioProvider.normalizeProxyUrl(DeezerAudioProvider.RENDER_PROXY_BASE_URL), renderUrl)

        // Custom mode
        val customUrl = DeezerAudioProvider.effectiveProxyUrl(
            configuredProxyMode = DeezerProxyMode.CUSTOM,
            configuredProxyUrl = "https://myproxy.example.com",
            globalProxyEnabled = false
        )
        assertEquals(DeezerAudioProvider.normalizeProxyUrl("https://myproxy.example.com"), customUrl)
    }
}

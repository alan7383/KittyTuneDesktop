import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.TrackSourceBadgeStyle
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.AudioSourceType
import com.alananasss.kittytune.ui.home.SearchSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrackSourceAndLyricsTest {

    private fun createSampleTrack(
        id: Long = 100L,
        title: String = "Test Song",
        source: String? = "soundcloud",
        permalinkUrl: String? = null
    ): Track {
        return Track(
            id = id,
            title = title,
            artworkUrl = null,
            durationMs = 180000L,
            user = User(id = 1L, username = "Artist", avatarUrl = null),
            source = source,
            permalinkUrl = permalinkUrl
        )
    }

    @Test
    fun `test AudioSourceType resolution from track source`() {
        val soundcloudTrack = createSampleTrack(source = "soundcloud")
        assertEquals(AudioSourceType.SOUNDCLOUD, AudioSourceType.fromTrack(soundcloudTrack))

        val ytTrack = createSampleTrack(source = "youtube")
        assertEquals(AudioSourceType.YOUTUBE, AudioSourceType.fromTrack(ytTrack))

        val ytmTrack = createSampleTrack(source = "youtube_music")
        assertEquals(AudioSourceType.YOUTUBE_MUSIC, AudioSourceType.fromTrack(ytmTrack))

        val spotifyTrack = createSampleTrack(source = "spotify")
        assertEquals(AudioSourceType.SPOTIFY, AudioSourceType.fromTrack(spotifyTrack))

        val deezerTrack = createSampleTrack(source = "deezer")
        assertEquals(AudioSourceType.DEEZER, AudioSourceType.fromTrack(deezerTrack))

        val tidalTrack = createSampleTrack(source = "tidal")
        assertEquals(AudioSourceType.TIDAL, AudioSourceType.fromTrack(tidalTrack))

        val qobuzTrack = createSampleTrack(source = "qobuz")
        assertEquals(AudioSourceType.QOBUZ, AudioSourceType.fromTrack(qobuzTrack))

        val appleTrack = createSampleTrack(source = "apple_music")
        assertEquals(AudioSourceType.APPLE_MUSIC, AudioSourceType.fromTrack(appleTrack))

        val localTrack = createSampleTrack(source = "local")
        assertEquals(AudioSourceType.LOCAL, AudioSourceType.fromTrack(localTrack))
    }

    @Test
    fun `test AudioSourceType resolution from stream source override`() {
        // Track might be from Apple Music catalogue but playing via YouTube stream
        val appleTrack = createSampleTrack(source = "apple_music")
        assertEquals(AudioSourceType.YOUTUBE, AudioSourceType.fromTrack(appleTrack, resolvedSource = "youtube"))
        assertEquals(AudioSourceType.YOUTUBE_MUSIC, AudioSourceType.fromTrack(appleTrack, resolvedSource = "youtube_music"))
        assertEquals(AudioSourceType.SOUNDCLOUD, AudioSourceType.fromTrack(appleTrack, resolvedSource = "soundcloud"))
    }

    @Test
    fun `test AudioSourceType resolution fallback to permalink`() {
        val ytPermalinkTrack = createSampleTrack(source = null, permalinkUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(AudioSourceType.YOUTUBE, AudioSourceType.fromTrack(ytPermalinkTrack))

        val scPermalinkTrack = createSampleTrack(source = null, permalinkUrl = "https://soundcloud.com/artist/track")
        assertEquals(AudioSourceType.SOUNDCLOUD, AudioSourceType.fromTrack(scPermalinkTrack))
    }

    @Test
    fun `test SearchSource includes YouTube Music`() {
        val ytm = SearchSource.YOUTUBE_MUSIC
        assertNotNull(ytm)
        assertEquals("YOUTUBE_MUSIC", ytm.name)
    }

    @Test
    fun `test AudioSourceType icon mappings exist`() {
        val typesWithDrawables = listOf(
            AudioSourceType.SOUNDCLOUD,
            AudioSourceType.YOUTUBE,
            AudioSourceType.YOUTUBE_MUSIC,
            AudioSourceType.DEEZER,
            AudioSourceType.TIDAL,
            AudioSourceType.QOBUZ,
            AudioSourceType.SPOTIFY,
        )
        for (type in typesWithDrawables) {
            val resPath = type.iconRes
            assertNotNull(resPath, "Expected iconRes for $type")
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(resPath)
            assertNotNull(stream, "Resource file $resPath must exist on classpath for $type")
            stream.close()
        }
    }

    @Test
    fun `test AudioSourceType inline sizing optical balance`() {
        for (type in AudioSourceType.values()) {
            // Visual height for every platform must align with typography (~14dp)
            assertTrue(type.inlineHeight.value in 13f..15.5f, "Height of $type must be between 13dp and 15.5dp")
            // Width must be at least height
            assertTrue(type.inlineWidth.value >= type.inlineHeight.value, "Width of $type must be >= height")
        }
        // SoundCloud is wide (ratio ~2.28)
        assertEquals(32f, AudioSourceType.SOUNDCLOUD.inlineWidth.value)
        assertEquals(14f, AudioSourceType.SOUNDCLOUD.inlineHeight.value)
        // YouTube is rectangular (ratio ~1.43)
        assertEquals(20f, AudioSourceType.YOUTUBE.inlineWidth.value)
        assertEquals(14f, AudioSourceType.YOUTUBE.inlineHeight.value)
        // YouTube Music is 1:1
        assertEquals(14f, AudioSourceType.YOUTUBE_MUSIC.inlineWidth.value)
        assertEquals(14f, AudioSourceType.YOUTUBE_MUSIC.inlineHeight.value)
    }

    @Test
    fun `test fullPlayerSourceIndicatorEnabled preference toggle`() {
        val prefs = PlayerPreferences()
        val original = prefs.getFullPlayerSourceIndicatorEnabled()
        try {
            prefs.setFullPlayerSourceIndicatorEnabled(false)
            assertEquals(false, prefs.getFullPlayerSourceIndicatorEnabled())
            prefs.setFullPlayerSourceIndicatorEnabled(true)
            assertEquals(true, prefs.getFullPlayerSourceIndicatorEnabled())
        } finally {
            prefs.setFullPlayerSourceIndicatorEnabled(original)
        }
    }
}

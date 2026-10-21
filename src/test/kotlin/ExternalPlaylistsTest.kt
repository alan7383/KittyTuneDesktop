package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.ExternalPlaylists
import com.alananasss.kittytune.data.spotify.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder
import kotlin.math.abs

class ExternalPlaylistsTest {

    @Test
    fun `youtube seed id ignores route encoding`() {
        val url = "https://www.youtube.com/watch?v=abc123&list=RDabc123"
        val encoded = URLEncoder.encode(url, "UTF-8")

        val expected = abs(url.hashCode().toLong())
        assertEquals(expected, ExternalPlaylists.radioStableId("yt_radio:$url"))
        assertEquals(expected, ExternalPlaylists.radioStableId("yt_radio:$encoded"))
        assertEquals(expected, ExternalPlaylists.libraryIdFor("yt_radio:$encoded", fallbackId = 42L))
    }

    @Test
    fun `spotify radio uses the same stable id as its track`() {
        val seed = "4uLU6hMCjMI75M1A2tKUQC"
        val expected = SpotifyTrack(
            id = seed,
            name = "Seed",
            durationMs = 0,
            artists = emptyList(),
        ).toTrack().id

        assertEquals(expected, ExternalPlaylists.radioStableId("spotify_radio:$seed"))
        assertEquals(expected, ExternalPlaylists.radioStableId("station_spotify:$seed"))
        assertEquals(expected, ExternalPlaylists.spotifyStableId(seed))
    }

    @Test
    fun `saving a radio uses its seed route instead of the remote id`() {
        val seed = "4uLU6hMCjMI75M1A2tKUQC"
        assertEquals(
            ExternalPlaylists.radioStableId("spotify_radio:$seed"),
            ExternalPlaylists.libraryIdFor("spotify_radio:$seed", fallbackId = 123L),
        )
        assertEquals(123L, ExternalPlaylists.libraryIdFor("https://soundcloud.com/artist/sets/x", fallbackId = 123L))
    }

    @Test
    fun `radio routes and remote providers count as external`() {
        assertTrue(ExternalPlaylists.isExternalPermalink("yt_radio:https://www.youtube.com/watch?v=abc123"))
        assertTrue(ExternalPlaylists.isExternalPermalink("spotify_radio:4uLU6hMCjMI75M1A2tKUQC"))
        assertTrue(ExternalPlaylists.isExternalPermalink("https://open.spotify.com/playlist/abc"))
        assertTrue(ExternalPlaylists.isExternalPermalink("https://www.youtube.com/watch?v=abc123"))
        assertTrue(ExternalPlaylists.isExternalPermalink("https://www.deezer.com/album/1"))
        assertFalse(ExternalPlaylists.isExternalPermalink("https://soundcloud.com/artist/sets/x"))
        assertFalse(ExternalPlaylists.isExternalPermalink(null))
    }

    @Test
    fun `non radio routes have no radio stable id`() {
        assertNull(ExternalPlaylists.radioStableId("spotify:playlist:abc"))
        assertNull(ExternalPlaylists.radioStableId("https://soundcloud.com/artist/sets/x"))
    }
}

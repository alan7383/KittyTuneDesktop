package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.spotify.SpotifyPlaylist
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyRadioParsingTest {

    @Test
    fun `seed to playlist protobuf body yields the editorial radio id`() {
        // Shape of a real response: a few protobuf framing bytes, then the playlist uri.
        val body = "\u0010\u0001:)\u0012'spotify:playlist:37i9dQZF1E8UXBoz02kGID\u0018\u0002"
        assertEquals("37i9dQZF1E8UXBoz02kGID", SpotifyRepository.extractRadioPlaylistId(body))
    }

    @Test
    fun `body without an editorial playlist yields nothing`() {
        assertNull(SpotifyRepository.extractRadioPlaylistId("\u0010\u0001spotify:playlist:0abcUserPlaylist"))
        assertNull(SpotifyRepository.extractRadioPlaylistId(""))
    }

    @Test
    fun `official spotify radios beat user playlists named radio`() {
        val user = SpotifyPlaylist(id = "5xUserRadio", name = "Mr. Brightside Radio", ownerName = "someone")
        val regional = SpotifyPlaylist(id = "37i9dQZF1E4aaaa", name = "Mr. Brightside Radio", ownerName = "Spotify")
        val other = SpotifyPlaylist(id = "37i9dQZF1E4bbbb", name = "Chill Radio", ownerName = "Spotify")

        val picked = SpotifyRepository.officialRadioCandidates(listOf(user, other, regional), "Mr. Brightside")
        assertEquals(listOf(regional), picked)
    }

    @Test
    fun `falls back to any official radio when none matches the name`() {
        val other = SpotifyPlaylist(id = "37i9dQZF1E4bbbb", name = "Chill Radio", ownerName = "Spotify")
        val user = SpotifyPlaylist(id = "5xUserRadio", name = "Song Radio", ownerName = "someone")
        assertEquals(listOf(other), SpotifyRepository.officialRadioCandidates(listOf(user, other), "Song"))
    }
}

package com.alananasss.kittytune

import com.alananasss.kittytune.data.spotify.SpotifyArtistRef
import com.alananasss.kittytune.data.spotify.SpotifyPathfinderApi
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import com.alananasss.kittytune.data.spotify.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyRepositoryTest {

    @Test
    fun testTrackConversion() {
        val spotifyTrack = SpotifyTrack(
            id = "4uLU6hMCjMI75M1A2tKUQC",
            name = "Never Gonna Give You Up",
            durationMs = 213000L,
            artists = listOf(SpotifyArtistRef(id = "0gxyHStUsqpMadRV0Di1Qt", name = "Rick Astley")),
            albumName = "Whenever You Need Somebody",
            artworkUrl = "https://i.scdn.co/image/ab67616d0000b2735755e164993798e0c9ef7d7a",
            releaseDate = "1987-11-12",
            explicit = false
        )

        val track = spotifyTrack.toTrack()

        assertEquals("Never Gonna Give You Up", track.title)
        assertEquals("Rick Astley", track.displayArtist)
        assertEquals("spotify", track.source)
        assertEquals(213000L, track.durationMs)
        assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", track.permalinkUrl)
        assertTrue(track.id > 0)
    }

    @Test
    fun testPathfinderQueryUrlGeneration() {
        val searchUrl = SpotifyPathfinderApi.buildSearchUrl("Daft Punk")
        assertTrue(searchUrl.contains(SpotifyPathfinderApi.PATHFINDER_URL))
        assertTrue(searchUrl.contains("operationName=searchDesktop"))
        assertTrue(searchUrl.contains(SpotifyPathfinderApi.Hashes.SEARCH_DESKTOP))

        val trackUrl = SpotifyPathfinderApi.buildTrackUrl("4uLU6hMCjMI75M1A2tKUQC")
        assertTrue(trackUrl.contains("operationName=getTrack"))
        assertTrue(trackUrl.contains(SpotifyPathfinderApi.Hashes.GET_TRACK))

        val playlistUrl = SpotifyPathfinderApi.buildPlaylistUrl("37i9dQZEVXbMDoHDwVN2tF")
        assertTrue(playlistUrl.contains("operationName=fetchPlaylist"))
        assertTrue(playlistUrl.contains(SpotifyPathfinderApi.Hashes.FETCH_PLAYLIST))
    }

    @Test
    fun testArtistConversion() {
        val spotifyArtist = com.alananasss.kittytune.data.spotify.SpotifyArtist(
            id = "0gxyHStUsqpMadRV0Di1Qt",
            name = "Rick Astley",
            avatarUrl = "https://i.scdn.co/image/artist_avatar.jpg",
            verified = true,
            monthlyListeners = 15000000L
        )

        val user = spotifyArtist.toUser()

        assertEquals("Rick Astley", user.username)
        assertEquals("https://i.scdn.co/image/artist_avatar.jpg", user.avatarUrl)
        assertEquals(true, user.verified)
        assertEquals(15000000, user.followersCount)
        assertTrue(user.id > 0)
    }

    @Test
    fun testArtistUrlGeneration() {
        val artistUrl = SpotifyPathfinderApi.buildArtistUrl("0gxyHStUsqpMadRV0Di1Qt")
        assertTrue(artistUrl.contains("operationName=queryArtistOverview"))
        assertTrue(artistUrl.contains(SpotifyPathfinderApi.Hashes.QUERY_ARTIST_OVERVIEW))

        val relatedUrl = SpotifyPathfinderApi.buildArtistRelatedUrl("0gxyHStUsqpMadRV0Di1Qt")
        assertTrue(relatedUrl.contains("operationName=queryArtistRelated"))
        assertTrue(relatedUrl.contains(SpotifyPathfinderApi.Hashes.QUERY_ARTIST_RELATED))
    }

    @Test
    fun testDiscographyUrlGeneration() {
        val url = SpotifyPathfinderApi.buildArtistDiscographyUrl("0gxyHStUsqpMadRV0Di1Qt", offset = 50)
        assertTrue(url.contains("operationName=queryArtistDiscographyAll"))
        assertTrue(url.contains(SpotifyPathfinderApi.Hashes.ARTIST_DISCOGRAPHY_ALL))
        assertTrue(url.contains("offset"))
    }

    @Test
    fun testExtractIdFromUrn() {
        assertEquals("4uLU6hMCjMI75M1A2tKUQC", SpotifyRepository.extractId("spotify:track:4uLU6hMCjMI75M1A2tKUQC"))
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", SpotifyRepository.extractId("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"))
    }

    @Test
    fun testExtractIdFromInternalPrefixes() {
        assertEquals("0gxyHStUsqpMadRV0Di1Qt", SpotifyRepository.extractId("spotify_artist:0gxyHStUsqpMadRV0Di1Qt"))
        assertEquals("37i9dQZF1E4abcdef", SpotifyRepository.extractId("spotify_radio:37i9dQZF1E4abcdef"))
        assertEquals("37i9dQZF1E4abcdef", SpotifyRepository.extractId("station_spotify:37i9dQZF1E4abcdef"))
    }

    @Test
    fun testExtractIdFromUrls() {
        assertEquals(
            "4uLU6hMCjMI75M1A2tKUQC",
            SpotifyRepository.extractId("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC?si=abc123")
        )
        // Regionalized share URLs keep working.
        assertEquals(
            "4uLU6hMCjMI75M1A2tKUQC",
            SpotifyRepository.extractId("https://open.spotify.com/intl-fr/track/4uLU6hMCjMI75M1A2tKUQC")
        )
        assertEquals(
            "0gxyHStUsqpMadRV0Di1Qt",
            SpotifyRepository.extractId("https://open.spotify.com/artist/0gxyHStUsqpMadRV0Di1Qt/")
        )
    }

    @Test
    fun testChartsDefinitions() {
        val charts = SpotifyRepository.getCharts()
        assertTrue(charts.isNotEmpty())
        val globalTop50 = charts.firstOrNull { it.key == "top-50-global" }
        assertNotNull(globalTop50)
        assertEquals("37i9dQZEVXbMDoHDwVN2tF", globalTop50?.playlistId)
    }
}

package com.alananasss.kittytune

import com.alananasss.kittytune.data.spotify.SpotifyArtistRef
import com.alananasss.kittytune.data.spotify.SpotifyCreditArtist
import com.alananasss.kittytune.data.spotify.SpotifyAlbum
import com.alananasss.kittytune.data.spotify.SpotifyTrack
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.main.mergeCreditArtists
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistDeduplicationTest {

    @Test
    fun `SpotifyAlbum artistName deduplicates repeated artists`() {
        val album = SpotifyAlbum(
            id = "album1",
            name = "Test Album",
            artists = listOf(
                SpotifyArtistRef(id = "art1", name = "Daft Punk"),
                SpotifyArtistRef(id = "art1", name = "Daft Punk")
            )
        )
        assertEquals("Daft Punk", album.artistName)
    }

    @Test
    fun `SpotifyTrack artistName and toTrack deduplicates repeated artists`() {
        val spotifyTrack = SpotifyTrack(
            id = "track1",
            name = "One More Time",
            durationMs = 320000L,
            artists = listOf(
                SpotifyArtistRef(id = "art1", name = "Daft Punk"),
                SpotifyArtistRef(id = "art1", name = "Daft Punk")
            )
        )
        assertEquals("Daft Punk", spotifyTrack.artistName)

        val track = spotifyTrack.toTrack()
        assertEquals(1, track.artists?.size)
        assertEquals("Daft Punk", track.artists?.first()?.name)
        assertEquals("Daft Punk", track.displayArtist)
        assertEquals("Daft Punk", track.user?.username)
    }

    @Test
    fun `Track displayArtist deduplicates multiple artist entries with same name or id`() {
        val track = Track(
            id = 12345L,
            title = "Collab Song",
            artworkUrl = null,
            durationMs = 200000L,
            artists = listOf(
                SpotifyArtistRef(id = "art1", name = "The Weeknd"),
                SpotifyArtistRef(id = "art1", name = "The Weeknd"),
                SpotifyArtistRef(id = "art2", name = "Daft Punk")
            ),
            user = User(id = 1L, username = "The Weeknd, Daft Punk", avatarUrl = null)
        )
        assertEquals("The Weeknd, Daft Punk", track.displayArtist)
    }

    @Test
    fun `mergeCreditArtists combines subroles and avoids duplicate artist rows in right-side panel`() {
        val credits = listOf(
            SpotifyCreditArtist(
                id = "art1",
                name = "The Weeknd",
                uri = "spotify:artist:art1",
                imageUri = "https://example.com/avatar.jpg",
                subroles = listOf("Main Artist")
            ),
            SpotifyCreditArtist(
                id = "art1",
                name = "The Weeknd",
                uri = "spotify:artist:art1",
                imageUri = null,
                subroles = listOf("Vocals")
            ),
            SpotifyCreditArtist(
                id = "art2",
                name = "Daft Punk",
                uri = "spotify:artist:art2",
                imageUri = null,
                subroles = listOf("Featured Artist")
            )
        )

        val merged = credits.mergeCreditArtists()
        assertEquals(2, merged.size)

        val weeknd = merged.first { it.id == "art1" }
        assertEquals("The Weeknd", weeknd.name)
        assertEquals(listOf("Main Artist", "Vocals"), weeknd.subroles)
        assertEquals("https://example.com/avatar.jpg", weeknd.imageUri)

        val daftPunk = merged.first { it.id == "art2" }
        assertEquals("Daft Punk", daftPunk.name)
        assertEquals(listOf("Featured Artist"), daftPunk.subroles)
    }

    @Test
    fun `mergeCreditArtists deduplicates by case-insensitive name if id is blank`() {
        val writers = listOf(
            SpotifyCreditArtist(
                id = "",
                name = "Max Martin",
                uri = "",
                subroles = listOf("Composer")
            ),
            SpotifyCreditArtist(
                id = "",
                name = "max martin",
                uri = "",
                subroles = listOf("Lyricist")
            )
        )

        val merged = writers.mergeCreditArtists()
        assertEquals(1, merged.size)
        assertEquals("Max Martin", merged[0].name)
        assertEquals(listOf("Composer", "Lyricist"), merged[0].subroles)
    }
}

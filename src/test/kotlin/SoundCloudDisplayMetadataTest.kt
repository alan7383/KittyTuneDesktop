package com.alananasss.kittytune.domain

import com.alananasss.kittytune.data.spotify.SpotifyArtistRef
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundCloudDisplayMetadataTest {
    private val uploader = User(id = 10L, username = "Archive account", avatarUrl = null)

    @Test
    fun existingTrackUsesTheTitleCreditWhenPublisherOnlyNamesTheUploader() {
        val raw = song("Performer — The Song – Extended Version").copy(
            source = null,
            publisherMetadata = TrackPublisherMetadata(artist = " archive ACCOUNT ", albumTitle = "PERFORMER — Collection"),
        )
        val gson = Gson()
        val saved = gson.fromJson(gson.toJson(raw), Track::class.java)

        assertEquals("Performer", saved.displayArtist)
        assertEquals("The Song – Extended Version", saved.displayTitle)
        assertEquals("Collection", saved.displayAlbumTitle)
        assertEquals("Performer — The Song – Extended Version", saved.title)
        assertEquals(" archive ACCOUNT ", saved.publisherMetadata?.artist)
        assertEquals("PERFORMER — Collection", saved.publisherMetadata?.albumTitle)
        assertEquals(uploader.id, saved.user?.id)
    }

    @Test
    fun sharedAccountCanCreditTheSoloArtistNamedInTheTitle() {
        val track = song("9MICE - So Good").copy(user = uploader.copy(username = "Kai Angel & 9mice"))

        assertEquals("9mice", track.displayArtist)
        assertEquals("So Good", track.displayTitle)
        assertEquals("So Good", track.copy(title = "9MICE\u00a0—\u2009So Good").displayTitle)
    }

    @Test
    fun explicitArtistWinsAndSupportsAnArtistNameWithDashes() {
        val track = song("Band - Duo — New Song").copy(
            publisherMetadata = TrackPublisherMetadata(artist = "Band - Duo", releaseTitle = "BAND - DUO–First Album"),
        )

        assertEquals("Band - Duo", track.displayArtist)
        assertEquals("New Song", track.displayTitle)
        assertEquals("First Album", track.displayAlbumTitle)
        assertEquals("Someone Else — Song", track.copy(title = "Someone Else — Song").displayTitle)

        val structured = track.copy(artists = listOf(SpotifyArtistRef(id = "1", name = "Official Credit")))
        assertEquals("Official Credit", structured.displayArtist)
        assertEquals("Band - Duo — New Song", structured.displayTitle)
    }

    @Test
    fun versionSuffixesHyphenatedWordsAndOtherProvidersKeepTheirTitles() {
        val unchanged = listOf(
            "Re-entry", "The Song - Live at Home", "The Song – 2024 Remaster", "Records — Vol. II",
            "The Song — feat. Guest", "The Song - [Official Video]", "Title — ",
        )
        for (title in unchanged) {
            val track = song(title)
            assertEquals(title, track.displayTitle)
            assertEquals("Archive account", track.displayArtist)
        }
        val external = song("Band — Song").copy(source = "spotify")
        assertEquals("Band — Song", external.displayTitle)
        assertEquals("Band — Song", external.copy(source = null, permalink = "spotify:track:1").displayTitle)
        assertNull(song(null).displayTitle)
    }

    @Test
    fun onlyMatchingArtistPrefixesAreRemovedFromSoundCloudAlbumCards() {
        val album = Playlist(
            id = 12L,
            title = "archive account — Live — Collector’s Edition",
            artworkUrl = null,
            calculatedArtworkUrl = null,
            trackCount = 10,
            user = uploader,
            isAlbum = true,
        )

        assertEquals("Live — Collector’s Edition", album.displayTitle)
        assertEquals("Live", album.copy(title = "Archive account -Live").displayTitle)
        assertEquals(album.title, album.copy(isAlbum = false).displayTitle)
        assertEquals(album.title, album.copy(urn = "spotify:album:12").displayTitle)
        assertEquals("Guest — Album", album.copy(title = "Guest — Album").displayTitle)
    }

    private fun song(title: String?) = Track(
        id = 11L,
        title = title,
        artworkUrl = null,
        durationMs = 90_000,
        user = uploader,
    )
}

package com.alananasss.kittytune

import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the cover chain a coverless playlist goes through. The regression these cover:
 * every playlist without artwork rendered the same stock photo, because fullResArtwork
 * ends on a fixed picsum URL that the image cache then reuses for all of them.
 */
class PlaylistCoverTest {

    private fun playlist(
        artworkUrl: String? = null,
        calculatedArtworkUrl: String? = null,
        tracks: List<Track>? = null
    ) = Playlist(
        id = 42L,
        title = "Set",
        artworkUrl = artworkUrl,
        calculatedArtworkUrl = calculatedArtworkUrl,
        trackCount = tracks?.size,
        user = User(7L, "someone", "https://i1.sndcdn.com/avatars-000-large.jpg"),
        tracks = tracks
    )

    private fun track(artworkUrl: String?) = Track(
        id = 1L,
        title = "Song",
        artworkUrl = artworkUrl,
        durationMs = 1000L,
        user = User(7L, "someone", null)
    )

    @Test
    fun `no cover and no tracks resolves to nothing rather than a stock photo`() {
        assertNull(playlist().usableArtwork)
    }

    @Test
    fun `own artwork wins and is upscaled`() {
        assertEquals(
            "https://i1.sndcdn.com/artworks-abc-t500x500.jpg",
            playlist(artworkUrl = "https://i1.sndcdn.com/artworks-abc-large.jpg").usableArtwork
        )
    }

    @Test
    fun `an avatar standing in as artwork is not a cover`() {
        assertNull(playlist(artworkUrl = "https://i1.sndcdn.com/avatars-000-large.jpg").usableArtwork)
        assertNull(playlist(calculatedArtworkUrl = "https://i1.sndcdn.com/avatars-000-large.jpg").usableArtwork)
    }

    @Test
    fun `first track with real artwork stands in`() {
        val resolved = playlist(
            tracks = listOf(track(null), track("https://i1.sndcdn.com/artworks-xyz-large.jpg"))
        ).usableArtwork
        assertEquals("https://i1.sndcdn.com/artworks-xyz-t500x500.jpg", resolved)
    }

    @Test
    fun `tracks that only have the owner avatar do not stand in`() {
        assertNull(playlist(tracks = listOf(track(null))).usableArtwork)
    }
}

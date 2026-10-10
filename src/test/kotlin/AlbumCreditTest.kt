package com.alananasss.kittytune.ui.library

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A record's artist from its tracks, not from a shared account's name (issue #66). */
class AlbumCreditTest {

    private val shared = User(1L, "Kai Angel & 9mice", null)

    private fun track(id: Long, artist: String?) = Track(
        id = id, title = "t$id", artworkUrl = null, durationMs = 1_000L, user = shared,
        publisherMetadata = artist?.let { com.alananasss.kittytune.domain.TrackPublisherMetadata(artist = it) },
    )

    @Test
    fun `a record one artist sings is credited to that artist`() {
        assertEquals("9mice", albumCreditFor(listOf(track(1, "9mice"), track(2, "9mice"), track(3, "9mice"))))
    }

    @Test
    fun `no majority leaves the account name`() {
        assertNull(albumCreditFor(listOf(track(1, "9mice"), track(2, "Kai Angel"))))
    }
}

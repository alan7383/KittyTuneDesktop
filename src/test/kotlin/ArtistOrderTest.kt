package com.alananasss.kittytune.ui.profile

import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/** What an artist's lists are ordered by (round 2 of the tester's list, item 8). */
class ArtistOrderTest {

    private val today = LocalDate.of(2026, 10, 8)

    private fun track(id: Long, plays: Int, released: String?, likes: Int = 0) = Track(
        id = id, title = "t$id", artworkUrl = null, durationMs = 180_000L, user = null,
        releaseDate = released, playbackCount = plays, likesCount = likes,
    )

    private fun playlist(id: Long, title: String, released: String?, likes: Int = 0) = Playlist(
        id = id, title = title, artworkUrl = null, calculatedArtworkUrl = null, trackCount = 10, user = null,
        releaseDate = released, likesCount = likes,
    )

    // The case from the report: an old song with the most plays of all time over the ones played now.
    private val lipstick = track(1, plays = 3_000_000, released = "2021-03-01")
    private val babylon = track(2, plays = 900_000, released = "2026-04-01")
    private val warhol = track(3, plays = 1_500_000, released = "2026-06-01")
    private val rest = track(4, plays = 50_000, released = "2024-01-01")

    @Test
    fun `popular starts with the songs the card shows, in the card's order`() {
        val sorted = ArtistOrder.sortTracks(
            ArtistOrder.TrackSort.NOW, listOf(lipstick, babylon, warhol, rest), cardOrder = listOf(warhol, babylon), today = today,
        )
        assertEquals(listOf(3L, 2L), sorted.take(2).map { it.id })
    }

    @Test
    fun `the rest follow by plays a day, so a younger song with fewer plays can come first`() {
        val sorted = ArtistOrder.sortTracks(ArtistOrder.TrackSort.NOW, listOf(lipstick, babylon, rest), today = today)
        assertEquals(listOf(2L, 1L, 4L), sorted.map { it.id })
    }

    @Test
    fun `all time is by plays, however long they took`() {
        val sorted = ArtistOrder.sortTracks(ArtistOrder.TrackSort.ALL_TIME, listOf(babylon, lipstick, warhol, rest))
        assertEquals(listOf(1L, 3L, 2L, 4L), sorted.map { it.id })
    }

    @Test
    fun `newest and oldest go by release date, undated songs last in both`() {
        val undated = track(5, plays = 1, released = null)
        val all = listOf(lipstick, undated, warhol, rest)
        assertEquals(listOf(3L, 4L, 1L, 5L), ArtistOrder.sortTracks(ArtistOrder.TrackSort.NEWEST, all).map { it.id })
        assertEquals(listOf(1L, 4L, 3L, 5L), ArtistOrder.sortTracks(ArtistOrder.TrackSort.OLDEST, all).map { it.id })
    }

    @Test
    fun `a day-old song is not a trend`() {
        val justOut = track(6, plays = 10_000, released = "2026-10-07")
        val steady = track(7, plays = 100_000, released = "2024-10-08")
        // 10,000 a day over a month counts as about 333 a day; 100,000 over two years is about 137.
        val sorted = ArtistOrder.sortTracks(ArtistOrder.TrackSort.NOW, listOf(steady, justOut), today = today)
        assertEquals(listOf(6L, 7L), sorted.map { it.id })
    }

    @Test
    fun `releases sort by date, title and likes`() {
        val a = playlist(1, "Zebra", "2023-05-01", likes = 5)
        val b = playlist(2, "apple", "2025-05-01", likes = 50)
        val c = playlist(3, "Mango", "2024-05-01", likes = 20)
        val all = listOf(a, b, c)
        assertEquals(listOf(2L, 3L, 1L), ArtistOrder.sortReleases(ArtistOrder.ReleaseSort.NEWEST, all).map { it.id })
        assertEquals(listOf(1L, 3L, 2L), ArtistOrder.sortReleases(ArtistOrder.ReleaseSort.OLDEST, all).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), ArtistOrder.sortReleases(ArtistOrder.ReleaseSort.TITLE, all).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), ArtistOrder.sortReleases(ArtistOrder.ReleaseSort.LIKES, all).map { it.id })
    }
}

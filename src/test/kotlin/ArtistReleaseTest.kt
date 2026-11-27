package com.alananasss.kittytune.ui.profile

import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.domain.UserPlaylistsResponse
import java.lang.reflect.Proxy
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistReleaseTest {
    private val artist = User(id = 12L, username = "Artist", avatarUrl = null)
    private val today = LocalDate.of(2026, 11, 26)

    @Test
    fun albumTrackIdsCollapseFourteenTracksAndKeepAnUnrelatedTrackWithTheSameTitle() {
        val songs = (1L..14L).map { song(it, "Track $it") }
        val record = album(tracks = songs.map { it.copy(title = null, user = null) })
        val standalone = song(15L, record.title.orEmpty())
        val playlist = record.copy(id = 501L, isAlbum = false, tracks = listOf(standalone))

        val releases = ArtistRelease.latestOf(songs + standalone + standalone, listOf(record, record, playlist))

        assertEquals(listOf(ArtistRelease.Record(record), ArtistRelease.Song(standalone)), releases)
    }

    @Test
    fun publisherMetadataMatchesItsOwnAlbumWithoutHidingAnotherArtistsTrack() {
        val record = album(tracks = emptyList())
        val byTitle = song(1L, "Album track").copy(
            publisherMetadata = TrackPublisherMetadata(albumTitle = "  the    album "),
        )
        val byId = song(2L, "Another album track").copy(
            publisherMetadata = TrackPublisherMetadata(albumId = "soundcloud:playlists:${record.id}"),
        )
        val other = byTitle.copy(id = 3L, user = artist.copy(id = 99L))

        assertEquals(
            listOf(ArtistRelease.Record(record), ArtistRelease.Song(other)),
            ArtistRelease.latestOf(listOf(byTitle, byId, other), listOf(record)),
        )
    }

    @Test
    fun metadataOnlyAlbumGroupingAcceptsThePrefixedAndDisplayNames() {
        val songWithPrefix = song(1L, "Artist – First Song").copy(
            publisherMetadata = TrackPublisherMetadata(releaseTitle = "Artist — The Album"),
        )
        val songWithDisplayName = song(2L, "Artist – Second Song").copy(
            publisherMetadata = TrackPublisherMetadata(albumTitle = "The Album"),
        )
        val record = album(tracks = emptyList()).copy(title = "Artist — The Album")

        assertEquals("First Song", ArtistRelease.Song(songWithPrefix).title)
        assertEquals("The Album", ArtistRelease.Record(record).title)
        for (album in listOf(record, record.copy(title = "The Album"))) {
            assertEquals(
                listOf(ArtistRelease.Record(album)),
                ArtistRelease.latestOf(listOf(songWithPrefix, songWithDisplayName), listOf(album)),
            )
        }
    }

    @Test
    fun releaseDateWinsAndUndatedEntriesStayLastInEitherOrder() {
        val recent = ArtistRelease.Song(song(1L, "Recent").copy(releaseDate = "2026-11-19"))
        val releasedToday = ArtistRelease.Record(album(tracks = emptyList()))
        val unknown = ArtistRelease.Song(song(2L, "Unknown").copy(releaseDate = "invalid", createdAt = null))
        val old = ArtistRelease.Song(song(3L, "Old").copy(releaseDate = "2026-11-18"))
        val releases = listOf(unknown, recent, releasedToday, old)

        assertEquals(listOf(releasedToday, recent, old, unknown), orderReleases(releases))
        assertEquals(listOf(old, recent, releasedToday, unknown), orderReleases(releases, newestFirst = false))
        assertEquals(ReleaseFreshness.TODAY, releasedToday.freshness(today))
        assertEquals(ReleaseFreshness.RECENT, recent.freshness(today))
        assertNull(old.freshness(today))
        assertNull(unknown.freshness(today))
        assertNull(releasedToday.freshness(today.minusDays(1)))
    }

    @Test
    fun albumListHydratesOnlyRecordsMissingTrackIds() = runBlocking {
        val songs = listOf(song(1L, "First"), song(2L, "Second"))
        val incomplete = album(tracks = songs.take(1)).copy(trackCount = 2)
        val complete = album(tracks = songs).copy(id = 600L)
        val requested = mutableListOf<Long>()
        val api = Proxy.newProxyInstance(
            SoundCloudApi::class.java.classLoader,
            arrayOf(SoundCloudApi::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getUserAlbums" -> UserPlaylistsResponse(listOf(incomplete, complete), null)
                "getPlaylist" -> {
                    requested += arguments[0] as Long
                    incomplete.copy(tracks = songs)
                }
                else -> error("Unexpected API method ${method.name}")
            }
        } as SoundCloudApi

        val records = checkNotNull(api.loadReleaseAlbums(artist.id))
        assertEquals(listOf(incomplete.id), requested)
        assertEquals(listOf(songs, songs), records.map { it.tracks })
    }

    private fun song(id: Long, title: String) = Track(
        id = id,
        title = title,
        artworkUrl = null,
        durationMs = 90_000L,
        user = artist,
        createdAt = "2026-11-26",
    )

    private fun album(tracks: List<Track>) = Playlist(
        id = 500L,
        title = "The Album",
        artworkUrl = null,
        calculatedArtworkUrl = null,
        trackCount = tracks.size,
        user = artist,
        tracks = tracks,
        isAlbum = true,
        releaseDate = "2026-11-26",
        createdAt = "2026-11-02",
    )
}

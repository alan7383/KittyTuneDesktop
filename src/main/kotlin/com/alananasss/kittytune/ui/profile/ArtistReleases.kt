package com.alananasss.kittytune.ui.profile

import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.withoutArtistPrefix
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal sealed interface ArtistRelease {
    val key: String
    val title: String
    val artist: String
    val artworkUrl: String?
    val day: LocalDate?

    data class Song(val track: Track) : ArtistRelease {
        override val key get() = "track:${track.catalogSource}:${track.id}"
        override val title get() = track.displayTitle.orEmpty()
        override val artist get() = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
        override val artworkUrl get() = track.fullResArtwork
        override val day = ReleaseDate.parse(track.releaseDate) ?: ReleaseDate.parse(track.createdAt)
    }

    data class Record(val playlist: Playlist) : ArtistRelease {
        val destination get() = playlist.urn?.takeIf { it.startsWith("spotify:album:") } ?: playlist.id.toString()
        override val key get() = "album:$destination"
        override val title get() = playlist.displayTitle.orEmpty()
        override val artist get() = playlist.user?.username.orEmpty()
        override val artworkUrl get() = playlist.fullResArtwork
        override val day = ReleaseDate.parse(playlist.releaseDate) ?: ReleaseDate.parse(playlist.createdAt)
    }

    companion object {
        /** Album membership comes from the catalogue, including playlists that contain ID-only track stubs. */
        fun latestOf(tracks: List<Track>, records: List<Playlist>, count: Int = Int.MAX_VALUE): List<ArtistRelease> {
            val albums = records.filter { it.id != 0L && it.isRealAlbum }.map(::Record).distinctBy { it.key }
            val trackIds = albums.flatMap { album ->
                album.playlist.tracks.orEmpty().filterNotNull().map { (album.playlist.catalogSource) to it.id }
            }.toHashSet()
            val albumIds = albums.flatMap { album ->
                val playlist = album.playlist
                val source = playlist.catalogSource
                listOfNotNull(
                    playlist.id.toString(),
                    playlist.urn?.substringAfterLast(':'),
                ).map { source to it }
            }.toHashSet()
            val ownedTitles = albums.flatMap { album ->
                val owner = album.playlist.user?.id?.takeIf { it > 0 } ?: return@flatMap emptyList()
                listOfNotNull(album.playlist.title.releaseTitleKey(), album.title.releaseTitleKey())
                    .map { Triple(album.playlist.catalogSource, owner, it) }
            }.toHashSet()

            val standalone = tracks.asSequence()
                .filter { it.id != 0L }
                .filterNot { track ->
                    val source = track.catalogSource
                    val metadata = track.publisherMetadata
                    val albumId = metadata?.albumId?.trim()?.substringAfterLast(':')?.takeIf { it.isNotEmpty() }
                    val owner = track.user?.id
                    (source to track.id) in trackIds ||
                        albumId != null && (source to albumId) in albumIds ||
                        albumId == null && owner != null && listOf(metadata?.albumTitle, metadata?.releaseTitle)
                            .any { title ->
                                val shortTitle = if (source == "soundcloud") withoutArtistPrefix(title, track.user?.username) else title
                                listOf(title, shortTitle).any { candidate ->
                                    candidate.releaseTitleKey()?.let { Triple(source, owner, it) in ownedTitles } == true
                                }
                            }
                }
                .map(::Song)
                .distinctBy { it.key }
                .toList()

            return orderReleases(albums + standalone).take(count)
        }
    }
}

internal fun orderReleases(releases: List<ArtistRelease>, newestFirst: Boolean = true): List<ArtistRelease> {
    val dates = if (newestFirst) reverseOrder<LocalDate>() else naturalOrder<LocalDate>()
    return releases.sortedWith(compareBy(nullsLast(dates)) { it.day })
}

internal enum class ReleaseFreshness { TODAY, RECENT }

internal fun ArtistRelease.freshness(today: LocalDate): ReleaseFreshness? {
    val releaseDay = day ?: return null
    return when {
        releaseDay == today -> ReleaseFreshness.TODAY
        releaseDay in today.minusDays(7)..today.minusDays(1) -> ReleaseFreshness.RECENT
        else -> null
    }
}

private val Track.catalogSource: String get() = source?.takeIf { it.isNotBlank() } ?: "soundcloud"
private val Playlist.catalogSource: String get() = if (urn?.startsWith("spotify:album:") == true) "spotify" else "soundcloud"

private val RELEASE_TITLE_SPACES = Regex("\\s+")

private fun String?.releaseTitleKey(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()?.replace(RELEASE_TITLE_SPACES, " ")

/** Fetch details only when the album listing did not include all of its track IDs. */
internal suspend fun SoundCloudApi.loadReleaseAlbums(artistId: Long): List<Playlist>? {
    val listing = fetchReleaseData { getUserAlbums(artistId).collection.filterNotNull() } ?: return null
    val records = listing.filter { it.id > 0L && it.isRealAlbum && (it.user == null || it.user.id == artistId) }
        .distinctBy { it.id }
    return records.chunked(4).flatMap { batch ->
        coroutineScope {
            batch.map { record ->
                async {
                    val knownIds = record.tracks.orEmpty().filterNotNull().map { it.id }.filter { it > 0 }.toSet()
                    if ((record.trackCount ?: 1) <= knownIds.size) return@async record

                    val details = fetchReleaseData { getPlaylist(record.id) } ?: return@async record
                    val tracks = (details.tracks.orEmpty().filterNotNull() + record.tracks.orEmpty().filterNotNull())
                        .distinctBy { it.id }
                    record.copy(tracks = tracks, trackCount = details.trackCount ?: record.trackCount)
                }
            }.awaitAll()
        }
    }
}

internal suspend fun <T> fetchReleaseData(fetch: suspend () -> T): T? = try {
    fetch()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

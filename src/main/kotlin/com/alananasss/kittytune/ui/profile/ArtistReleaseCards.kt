package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.ui.common.ScrollableLazyRow
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.time.LocalDate

@Composable
internal fun ArtistReleaseStrip(
    releases: List<ArtistRelease>,
    onOpen: (ArtistRelease) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    ScrollableLazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        fadeColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        items(releases, key = { it.key }) { release ->
            ArtistReleaseTile(release, today, { onOpen(release) }, Modifier.width(156.dp))
        }
    }
}

@Composable
internal fun ArtistReleaseTile(
    release: ArtistRelease,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = hapticAction(action = onClick),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.15f).background(scheme.surfaceContainerHigh)) {
                AsyncImage(
                    model = release.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                ReleaseFreshnessBadge(release.freshness(today), Modifier.align(Alignment.TopStart).padding(8.dp))
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = release.title.ifBlank { str("untitled_track") },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = release.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReleaseKindBadge(release)
                    release.day?.let { day ->
                        Text(
                            text = formatReleaseDay(day, today),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReleaseKindBadge(release: ArtistRelease) {
    val scheme = MaterialTheme.colorScheme
    val album = release is ArtistRelease.Record
    Surface(
        shape = CircleShape,
        color = if (album) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        contentColor = if (album) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
    ) {
        Text(
            text = if (album) str("release_kind_album") else str("release_kind_track"),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun ReleaseFreshnessBadge(freshness: ReleaseFreshness?, modifier: Modifier = Modifier) {
    if (freshness == null) return

    val scheme = MaterialTheme.colorScheme
    val isToday = freshness == ReleaseFreshness.TODAY
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (isToday) scheme.primary else scheme.tertiaryContainer,
        contentColor = if (isToday) scheme.onPrimary else scheme.onTertiaryContainer,
    ) {
        Text(
            text = if (isToday) str("date_today") else str("home_releases_recent"),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun formatReleaseDay(day: LocalDate, today: LocalDate): String =
    ReleaseDate.format(day, today, com.alananasss.kittytune.core.Strings.locale())

@Composable
internal fun rememberVisibleArtistReleases(releases: List<ArtistRelease>): List<ArtistRelease> {
    val blockedTracks by BlockManager.blockedTrackIdsFlow.collectAsState()
    val blockedArtists by BlockManager.blockedArtistIdsFlow.collectAsState()
    return remember(releases, blockedTracks, blockedArtists) {
        releases.filter { release ->
            when (release) {
                is ArtistRelease.Song -> !BlockManager.isBlocked(release.track)
                is ArtistRelease.Record -> release.playlist.user?.id !in blockedArtists
            }
        }
    }
}

@Composable
internal fun rememberArtistReleases(viewModel: ProfileViewModel): List<ArtistRelease> {
    val tracks = if (viewModel.isSpotifyProfile) emptyList() else viewModel.allTracks.toList()
    val records = viewModel.albums.toList() + viewModel.singles.toList() + viewModel.compilations.toList()
    val releases = remember(tracks, records) { ArtistRelease.latestOf(tracks, records) }
    return rememberVisibleArtistReleases(releases)
}

internal fun PlayerViewModel.openArtistRelease(
    release: ArtistRelease,
    context: PlaybackContext?,
    onAlbum: (String) -> Unit = { navigateToPlaylistId = it },
) {
    when (release) {
        is ArtistRelease.Song -> playPlaylist(listOf(release.track), 0, context)
        is ArtistRelease.Record -> onAlbum(release.destination)
    }
}

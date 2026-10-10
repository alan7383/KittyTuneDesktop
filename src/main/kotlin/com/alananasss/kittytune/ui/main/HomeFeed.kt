package com.alananasss.kittytune.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment

import com.alananasss.kittytune.utils.SoundCloudLocalizationUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.alananasss.kittytune.data.local.HistoryItem
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.home.HomeViewModel
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.util.Calendar

/**
 * The home page (issue #66).
 *
 * It was a grid of nine recently played things, a mix card and SoundCloud's own shelves, and two days of steady
 * listening used none of it but the statistics. It leads with My Wave now, one press for music picked for this
 * listener that keeps going and learns; then what was playing lately as one scrolling row instead of a grid that
 * lost a row whenever the side panel opened; the statistics; the country's chart; new songs from the artists
 * already liked; and SoundCloud's shelves last. The mix card is off unless it is switched on: My Wave does that
 * job, and a second card for it at the foot of the page only repeated it (round 2, item 10.1).
 */
@Composable
internal fun HomeFeed(
    vm: HomeViewModel,
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    val history by vm.historyFlow.collectAsState(initial = emptyList())
    val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences() }
    val showHomeYourMix by prefs.showHomeYourMixFlow().collectAsState(initial = prefs.getShowHomeYourMix())
    val showHomeListeningStats by prefs.showHomeListeningStatsFlow().collectAsState(initial = prefs.getShowHomeListeningStats())

    val contextHistory = remember(history) {
        history.filter { it.id != "playlist:0" && !it.title.equals("history", ignoreCase = true) }
            .distinctBy { it.id }
    }
    LaunchedEffect(Unit) { if (vm.chartPreview.isEmpty() && !vm.isChartPreviewLoading) vm.loadChartPreview() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "greeting") {
            Text(
                text = homeGreeting(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = HOME_PADDING),
            )
            Spacer(Modifier.height(12.dp))
            MyWaveCard(playerViewModel, Modifier.padding(horizontal = HOME_PADDING))
        }

        if (contextHistory.isNotEmpty()) {
            item(key = "continue") {
                HomeShelfTitle(str("home_continue"))
                ContinueGrid(contextHistory.take(CONTINUE_COUNT), playerViewModel)
            }
        }

        if (showHomeListeningStats) {
            item(key = "stats") { Box(Modifier.padding(horizontal = HOME_PADDING)) { ListeningStatsCard(navController) } }
        }

        item(key = "chart") {
            com.alananasss.kittytune.ui.home.HomeChartSection(vm, playerViewModel, onOpenCharts = { navController.navigate("charts") })
        }

        // New videos of the artists being listened to, on the home page as on an artist's own (round 3, 24), under the chart.
        item(key = "clips") { com.alananasss.kittytune.ui.profile.HomeClipsShelf(playerViewModel) }

        item(key = "from_artists") { com.alananasss.kittytune.ui.home.FromYourArtistsSection(vm, playerViewModel) }

        if (showHomeYourMix) {
            item(key = "mix") { Box(Modifier.padding(horizontal = HOME_PADDING)) { StartMixingCard(playerViewModel) } }
        }

        items(vm.homeSections, key = { it.title }) { section ->
            Column {
                HomeShelfTitle(
                    SoundCloudLocalizationUtils.localizeSectionTitle(section.title),
                    SoundCloudLocalizationUtils.localizeSectionSubtitle(section.subtitle),
                )
                com.alananasss.kittytune.ui.common.ScrollableLazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = HOME_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    fadeColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    items(section.content) { item ->
                        when (item) {
                            is Track -> MediaCard(
                                title = item.title ?: "",
                                subtitle = item.displayArtist.ifBlank { item.user?.username.orEmpty() },
                                artworkUrl = item.fullResArtwork,
                                round = false,
                                onRightClick = { playerViewModel.showTrackOptions(item) }
                            ) {
                                playerViewModel.playPlaylist(listOf(item), 0)
                            }
                            is Playlist -> MediaCard(
                                title = item.title ?: "",
                                subtitle = item.user?.username ?: "",
                                artworkUrl = item.fullResArtwork,
                                round = false,
                                onRightClick = { playerViewModel.showPlaylistOptions(item) }
                            ) {
                                playerViewModel.navigateToPlaylistId = getStationNavId(item)
                            }
                            is User -> MediaCard(
                                title = item.username ?: "",
                                subtitle = str("lib_artists"),
                                artworkUrl = item.avatarUrl,
                                round = true,
                            ) {
                                playerViewModel.navigateToPlaylistId = item.profileNavId
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A shelf's title and, under it, what it is, both inset like the page. */
@Composable
private fun HomeShelfTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = HOME_PADDING).padding(bottom = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (!subtitle.isNullOrBlank()) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Good evening", by the hour and in the app's language. */
@Composable
private fun homeGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val lang = Strings.resolvedLanguage
    return when (hour) {
        in 5..11 -> when (lang) { "fr" -> "Bonjour"; "hu" -> "Jó reggelt"; "ru" -> "Доброе утро"; "de" -> "Guten Morgen"; "vi" -> "Chào buổi sáng"; else -> "Good morning" }
        in 12..17 -> when (lang) { "fr" -> "Bon après-midi"; "hu" -> "Jó napot"; "ru" -> "Добрый день"; "de" -> "Guten Tag"; "vi" -> "Chào buổi chiều"; else -> "Good afternoon" }
        else -> when (lang) { "fr" -> "Bonsoir"; "hu" -> "Jó estét"; "ru" -> "Добрый вечер"; "de" -> "Guten Abend"; "vi" -> "Chào buổi tối"; else -> "Good evening" }
    }
}

/** One recently played thing: a playlist, an artist, a station or a track, opened the way it was played. */
@Composable
private fun HistoryEntryTile(
    entry: com.alananasss.kittytune.data.local.HistoryItem,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val isLikes = entry.id == "likes" || entry.numericId == -1L || entry.id == "pin_likes" ||
            entry.title.equals("Titres Likés", ignoreCase = true) ||
            entry.title.equals(str("lib_liked_tracks"), ignoreCase = true) ||
            entry.title.equals(str("history_title_likes"), ignoreCase = true) ||
            entry.title.equals("Liked Tracks", ignoreCase = true)
    val isDownloads = entry.id == "downloads" || entry.numericId == -2L || entry.id == "pin_downloads" ||
            entry.title.equals(str("lib_downloads"), ignoreCase = true) ||
            entry.title.equals(str("history_title_downloads"), ignoreCase = true) ||
            entry.title.equals("Downloads", ignoreCase = true)
    val isLocalFiles = entry.id == "local_files" || entry.id == "pin_local" ||
            entry.title.equals(str("lib_local_media"), ignoreCase = true)

    QuickTile(
        title = entry.title,
        imageUrl = if (isLikes || isDownloads || isLocalFiles) null else entry.imageUrl,
        isLikes = isLikes,
        isDownloads = isDownloads,
        isLocalFiles = isLocalFiles,
        modifier = modifier,
        // Right-click on tile = playlist/track options sheet.
        onRightClick = when {
            entry.id.startsWith("playlist:") -> {
                {
                    playerViewModel.showPlaylistOptions(
                        Playlist(
                            id = entry.numericId,
                            title = entry.title,
                            artworkUrl = entry.imageUrl,
                            calculatedArtworkUrl = null,
                            trackCount = null,
                            user = null,
                            tracks = null,
                        )
                    )
                }
            }
            entry.type == "TRACK" || entry.id.startsWith("track:") -> {
                {
                    playerViewModel.showTrackOptions(
                        Track(
                            id = entry.numericId,
                            title = entry.title,
                            artworkUrl = entry.imageUrl,
                            durationMs = null,
                            user = User(0, entry.subtitle ?: "", null),
                            source = entry.source,
                            permalinkUrl = entry.originalUrl
                        )
                    )
                }
            }
            else -> null
        },
    ) {
        openHistoryEntry(entry, playerViewModel)
    }
}

/** What pressing a history entry does: plays a song, or opens the album, playlist, station or profile it stands for. */
private fun openHistoryEntry(entry: com.alananasss.kittytune.data.local.HistoryItem, playerViewModel: PlayerViewModel) {
    if (entry.type == "TRACK" || entry.id.startsWith("track:")) {
        val trackToPlay = Track(
            id = entry.numericId,
            title = entry.title,
            artworkUrl = entry.imageUrl,
            durationMs = null,
            user = User(0, entry.subtitle ?: "", null),
            source = entry.source,
            permalinkUrl = entry.originalUrl
        )
        playerViewModel.playPlaylist(listOf(trackToPlay), 0)
    } else {
        playerViewModel.navigateToPlaylistId = when {
            entry.id.startsWith("playlist:") -> entry.numericId.toString()
            entry.id.startsWith("spotify_artist:") -> entry.id
            entry.id.startsWith("spotify_radio:") -> entry.id
            entry.id.startsWith("spotify:") -> entry.id
            entry.type == "STATION" && entry.id.contains("spotify") -> {
                val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(entry.id)
                "spotify_radio:$clean"
            }
            entry.type == "PROFILE" -> {
                val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(entry.id)
                if (clean.isNotBlank() && clean != "0" && (entry.id.contains("spotify") || clean.length == 22)) {
                    "spotify_artist:$clean"
                } else if (entry.id == "profile:0" || entry.numericId == 0L) {
                    if (entry.title.isNotBlank()) {
                        "profile:${entry.title}"
                    } else {
                        entry.id
                    }
                } else {
                    entry.id
                }
            }
            else -> entry.id
        }
    }
}

private val HOME_PADDING = 20.dp
private val CONTINUE_TILE_WIDTH = 270.dp
private const val CONTINUE_COUNT = 16

/**
 * "Continue listening" as one row that scrolls sideways: a cover, the name, and what kind of thing it is under it. A row of
 * square tiles could not say a track from an album from a playlist, and two columns of these made the page long.
 */
@Composable
private fun ContinueGrid(entries: List<com.alananasss.kittytune.data.local.HistoryItem>, playerViewModel: PlayerViewModel) {
    com.alananasss.kittytune.ui.common.ScrollableLazyRow(
        contentPadding = PaddingValues(horizontal = HOME_PADDING),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        fadeColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        items(entries, key = { it.id }) { entry ->
            ContinueRow(entry, playerViewModel, Modifier.width(CONTINUE_TILE_WIDTH))
        }
    }
}

@Composable
private fun ContinueRow(
    entry: com.alananasss.kittytune.data.local.HistoryItem,
    playerViewModel: PlayerViewModel,
    modifier: Modifier,
) {
    val isTrack = entry.type == "TRACK" || entry.id.startsWith("track:")
    val isArtist = entry.type == "PROFILE" || entry.id.startsWith("profile:") || entry.id.startsWith("spotify_artist:")
    val isStation = entry.type == "STATION"
    // Collections have no cover of their own; they get the icon they have in the library.
    val collectionIcon = when {
        entry.id == "likes" || entry.numericId == -1L || entry.id == "pin_likes" -> Icons.Rounded.Favorite
        entry.id == "downloads" || entry.numericId == -2L || entry.id == "pin_downloads" -> Icons.Rounded.Download
        entry.id == "local_files" || entry.id == "pin_local" -> Icons.Rounded.FolderOpen
        else -> null
    }
    val kind = when {
        isTrack -> str("release_kind_track")
        isArtist -> str("lib_artists")
        isStation -> str("lib_stations")
        collectionIcon != null -> str("lib_playlists")
        else -> str("lib_playlists")
    }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) { openHistoryEntry(entry, playerViewModel) },
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            if (collectionIcon != null) {
                Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(collectionIcon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            } else {
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = null,
                    contentScale = if (isArtist) ContentScale.Crop else ContentScale.Crop,
                    modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    kind,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

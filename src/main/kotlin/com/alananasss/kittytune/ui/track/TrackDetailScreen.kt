@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.track

import androidx.compose.material3.IconButtonDefaults

import androidx.compose.material3.ButtonDefaults

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.library.TrackListItem
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.ArtistAvatar
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background

@Composable
fun TrackDetailScreen(
    trackId: Long,
    initialTab: Int = 0,
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    detailViewModel: TrackDetailViewModel = viewModel(key = "track_detail_$trackId") { TrackDetailViewModel(AppInstance.application) }
) {
    val pagerState = rememberPagerState(initialPage = initialTab) { 4 }
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        str("detail_likers"),
        str("detail_reposters"),
        str("detail_in_playlists"),
        str("detail_related")
    )

    LaunchedEffect(trackId) {
        detailViewModel.loadTrackDetails(trackId)
    }

    val track = detailViewModel.track

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(str("detail_track_title"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("btn_back"))
                    }
                }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = detailViewModel.isLoading,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(200)))
            },
            label = "trackDetailContent",
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) { isLoadingState ->
            if (isLoadingState) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator()
                }
            } else if (track == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(str("no_tracks_found"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column {
                    TrackDetailHeader(track!!, playerViewModel)
                    // The tabs speak the right panel's language: icon, name and count on a tonal pill (issue #66).
                    DetailTabs(
                        selected = pagerState.currentPage,
                        tabs = listOf(
                            DetailTab(Icons.Rounded.Favorite, tabs[0], track.likesCount),
                            DetailTab(Icons.Rounded.Repeat, tabs[1], track.repostsCount),
                            DetailTab(Icons.AutoMirrored.Rounded.QueueMusic, tabs[2], null),
                            DetailTab(Icons.Rounded.AutoAwesome, tabs[3], null),
                        ),
                        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    )

                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0 -> UserList(
                                users = detailViewModel.likers,
                                onNavigate = onNavigate,
                                onLoadMore = { detailViewModel.loadMoreLikers() },
                                isLoadingMore = detailViewModel.isLikersLoadingMore,
                                isSortedByFollowers = detailViewModel.isUsersSortedByFollowers,
                                onToggleSort = { detailViewModel.toggleUsersSort() },
                                isSortLoading = detailViewModel.isUsersSortLoading,
                            )
                            1 -> UserList(
                                users = detailViewModel.reposters,
                                onNavigate = onNavigate,
                                onLoadMore = { detailViewModel.loadMoreReposters() },
                                isLoadingMore = detailViewModel.isRepostersLoadingMore,
                                isSortedByFollowers = detailViewModel.isUsersSortedByFollowers,
                                onToggleSort = { detailViewModel.toggleUsersSort() },
                                isSortLoading = detailViewModel.isUsersSortLoading,
                            )
                            2 -> PlaylistList(
                                playlists = detailViewModel.inPlaylists,
                                onNavigate = onNavigate,
                                onLoadMore = { detailViewModel.loadMorePlaylists() },
                                isLoadingMore = detailViewModel.isPlaylistsLoadingMore,
                                isSortedByLikes = detailViewModel.isPlaylistsSortedByLikes,
                                onToggleSort = { detailViewModel.toggleSortPlaylists() },
                                isSortLoading = detailViewModel.isPlaylistsSortLoading,
                            )
                            3 -> TrackList(
                                tracks = detailViewModel.relatedTracks,
                                playerViewModel = playerViewModel,
                                onLoadMore = { detailViewModel.loadMoreRelated() },
                                isLoadingMore = detailViewModel.isRelatedLoadingMore
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun UserList(
    users: List<User>,
    onNavigate: (String) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    /** Whether the list is by followers; null where it is not offered a choice of order. */
    isSortedByFollowers: Boolean? = null,
    onToggleSort: () -> Unit = {},
    isSortLoading: Boolean = false,
) {
    if (users.isEmpty() && !isLoadingMore) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(str("detail_no_one_yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else Column(Modifier.fillMaxSize()) {
        if (isSortedByFollowers != null) Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = isSortedByFollowers,
                onClick = onToggleSort,
                label = { Text(str("track_sorted_by_followers")) },
                leadingIcon = {
                    Icon(
                        if (isSortedByFollowers) Icons.Rounded.Check else Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                shape = CircleShape,
            )
            if (isSortLoading) {
                Spacer(Modifier.width(8.dp))
                CircularWavyProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(users) { index, user ->
                if (index >= users.size - 5) {
                    LaunchedEffect(Unit) { onLoadMore() }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigate("profile:${user.numericId}") }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtistAvatar(avatarUrl = user.avatarUrl, modifier = Modifier.size(48.dp).clip(CircleShape))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.username ?: str("unknown_artist"), fontWeight = FontWeight.SemiBold)
                            if (user.verified) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            }
                        }
                        Text(
                            text = "${compactCount(user.followersCount.toLong())} ${str("profile_followers")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}


@Composable
fun PlaylistList(
    playlists: List<Playlist>,
    onNavigate: (String) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
    isSortedByLikes: Boolean,
    onToggleSort: () -> Unit,
    isSortLoading: Boolean = false,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = isSortedByLikes,
                onClick = onToggleSort,
                label = { Text(str("track_sorted_by_popularity")) },
                leadingIcon = {
                    Icon(
                        if (isSortedByLikes) Icons.Rounded.Check else Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                shape = CircleShape,
                interactionSource = interactionSource,
            )
            if (isSortLoading) {
                Spacer(Modifier.width(8.dp))
                CircularWavyProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
            }
        }

        if (playlists.isEmpty() && !isLoadingMore) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(str("detail_no_public_playlist"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(playlists) { index, playlist ->
                    if (index >= playlists.size - 5) {
                        LaunchedEffect(Unit) { onLoadMore() }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onNavigate("${playlist.id}") }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = playlist.fullResArtwork,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(playlist.title ?: str("lib_playlists"), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                str("playlist_num_tracks", playlist.trackCount ?: 0) + " • " + str("playlist_by_user", playlist.user?.username ?: "") + if (playlist.likesCount != null && playlist.likesCount > 0) " • ♥ ${compactCount(playlist.likesCount.toLong())}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TrackList(
    tracks: List<Track>,
    playerViewModel: PlayerViewModel,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean
) {
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val downloadedIds by DownloadManager.downloadedIds.collectAsState()

    if (tracks.isEmpty() && !isLoadingMore) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(str("detail_no_similar"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Button(
                    onClick = { playerViewModel.playPlaylist(tracks, 0) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(str("artist_listen"))
                }
            }
            itemsIndexed(tracks) { index, track ->
                if (index >= tracks.size - 5) {
                    LaunchedEffect(Unit) { onLoadMore() }
                }

                val progress = downloadProgress[track.id]
                val isDownloading = progress != null
                val isDownloaded = remember(track.id, downloadedIds) {
                    (track.id < 0 && track.source != "youtube") || downloadedIds.contains(track.id)
                }

                TrackListItem(
                    track = track,
                    currentlyPlayingTrack = playerViewModel.currentTrack,
                    index = index,
                    isDownloading = isDownloading,
                    isDownloaded = isDownloaded,
                    downloadProgress = progress ?: 0,
                    onClick = { playerViewModel.playPlaylist(tracks, index) },
                    onOptionClick = { playerViewModel.showTrackOptions(track) },
                    onArtistClick = { playerViewModel.navigateToTrackArtist(it) }
                )
            }
            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}


/** The track as the head of its page: cover over a blur of itself, the title, the artist and its numbers. */
@Composable
private fun TrackDetailHeader(track: Track, playerViewModel: PlayerViewModel) {
    Box(Modifier.fillMaxWidth().height(220.dp)) {
        AsyncImage(
            model = track.fullResArtwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(40.dp),
        )
        Box(
            Modifier.matchParentSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                    1f to MaterialTheme.colorScheme.background,
                )
            )
        )
        Row(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalAlignment = Alignment.Bottom) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                modifier = Modifier.size(150.dp).clip(RoundedCornerShape(20.dp)).viewableCover(track.fullResArtwork),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(20.dp))
            Column {
                Text(
                    track.title ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtistLinkText(
                        track = track,
                        onArtistClick = { playerViewModel.navigateToTrackArtist(it) },
                        text = track.displayArtist.ifBlank { track.user?.username ?: "" }
                    )
                    if (track.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Icons.Rounded.PlayArrow to track.playbackCount,
                        Icons.Rounded.Favorite to track.likesCount,
                        Icons.Rounded.Repeat to track.repostsCount,
                        Icons.Rounded.ChatBubble to track.commentCount,
                    ).filter { it.second > 0 }.forEach { (icon, count) ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(compactCount(count.toLong()), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DetailTab(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val count: Int?)

@Composable
private fun DetailTabs(selected: Int, tabs: List<DetailTab>, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selected
            val container by androidx.compose.animation.animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "detailTab",
            )
            Surface(
                onClick = { onSelect(index) },
                shape = CircleShape,
                color = container,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(tab.icon, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                    if (tab.count != null && tab.count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(compactCount(tab.count.toLong()), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun compactCount(value: Long): String =
    java.text.NumberFormat.getCompactNumberInstance(com.alananasss.kittytune.core.Strings.locale(), java.text.NumberFormat.Style.SHORT).format(value)

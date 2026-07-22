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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
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
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.library.TrackListItem
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.ArtistAvatar
import kotlinx.coroutines.launch

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
                // Removed redundant navigationIcon
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = detailViewModel.isLoading || track == null,
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
            } else {
                Column {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track!!.fullResArtwork,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).viewableCover(track.fullResArtwork),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(track.title ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(track.user?.username ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (track.user?.verified == true) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // Desktop: cap the tab strip width, the content panel can be very wide.
                    SecondaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        modifier = Modifier.widthIn(max = 640.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(text = title) }
                            )
                        }
                    }

                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0 -> UserList(
                                users = detailViewModel.likers,
                                onNavigate = onNavigate,
                                onLoadMore = { detailViewModel.loadMoreLikers() },
                                isLoadingMore = detailViewModel.isLikersLoadingMore
                            )
                            1 -> UserList(
                                users = detailViewModel.reposters,
                                onNavigate = onNavigate,
                                onLoadMore = { detailViewModel.loadMoreReposters() },
                                isLoadingMore = detailViewModel.isRepostersLoadingMore
                            )
                            2 -> PlaylistList(
                                playlists = detailViewModel.inPlaylists,
                                onNavigate = onNavigate,
                                onLoadMore = { detailViewModel.loadMorePlaylists() },
                                isLoadingMore = detailViewModel.isPlaylistsLoadingMore,
                                isSortedByLikes = detailViewModel.isPlaylistsSortedByLikes,
                                onToggleSort = { detailViewModel.toggleSortPlaylists() }
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
    isLoadingMore: Boolean
) {
    if (users.isEmpty() && !isLoadingMore) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(str("detail_no_one_yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(users) { index, user ->
                if (index >= users.size - 5) {
                    LaunchedEffect(Unit) { onLoadMore() }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate("profile:${user.numericId}") }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
                            text = "${user.followersCount} ${str("profile_followers")}",
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
    onToggleSort: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            onClick = { onToggleSort() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = androidx.compose.ui.graphics.RectangleShape,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Sorted by popularity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = isSortedByLikes,
                    onCheckedChange = { onToggleSort() },
                    interactionSource = interactionSource,
                    thumbContent = {
                        if (isSortedByLikes) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                                tint = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                )
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
                            .clickable { onNavigate("${playlist.id}") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = playlist.fullResArtwork,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(playlist.title ?: str("lib_playlists"), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                str("playlist_num_tracks", playlist.trackCount ?: 0) + " • " + str("playlist_by_user", playlist.user?.username ?: "") + if (playlist.likesCount != null && playlist.likesCount > 0) " • ${playlist.likesCount} likes" else "",
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
                    onOptionClick = { playerViewModel.showTrackOptions(track) }
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

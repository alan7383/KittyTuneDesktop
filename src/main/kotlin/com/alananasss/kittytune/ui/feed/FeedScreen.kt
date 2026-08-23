@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.StreamItem
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    playerViewModel: PlayerViewModel,
    navController: NavController,
    feedViewModel: FeedViewModel = viewModel { FeedViewModel(AppInstance.application) },
) {
    val listState = rememberLazyListState()

    // Detect end of list for load-more
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 5 && totalItems > 0 && feedViewModel.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !feedViewModel.isLoadingMore) {
            feedViewModel.loadMore()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = str("nav_feed"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { feedViewModel.refresh() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = str("feed_refresh"))
            }
        }

        when {
            feedViewModel.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator()
                }
            }
            feedViewModel.error != null && feedViewModel.feedItems.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(str("error_generic"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { feedViewModel.refresh() }) {
                            Text(str("btn_retry"))
                        }
                    }
                }
            }
            feedViewModel.feedItems.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.DynamicFeed, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(str("feed_empty"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(feedViewModel.feedItems.size) { index ->
                        val item = feedViewModel.feedItems[index]
                        FeedItem(
                            item = item,
                            playerViewModel = playerViewModel,
                            navController = navController,
                        )
                    }

                    if (feedViewModel.isLoadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedItem(
    item: StreamItem,
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    val track = item.track
    val playlist = item.playlist

    val isRepost = item.type == "track-repost" || item.type == "playlist-repost"
    val reposter = if (isRepost) item.user else null

    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    if (track != null) {
        TrackFeedItem(
            track = track,
            reposter = reposter,
            createdAt = item.createdAt,
            isRepost = isRepost,
            hovered = hovered,
            interactionSource = interactionSource,
            onClick = { playerViewModel.playPlaylist(listOf(track), 0) },
            onArtistClick = {
                track.user?.id?.let { uid ->
                    navController.navigate("profile/$uid")
                }
            },
            onReposterClick = {
                reposter?.id?.let { uid ->
                    navController.navigate("profile/$uid")
                }
            },
            onRightClick = { playerViewModel.showTrackOptions(track) },
        )
    } else if (playlist != null) {
        PlaylistFeedItem(
            playlist = playlist,
            reposter = reposter,
            createdAt = item.createdAt,
            isRepost = isRepost,
            hovered = hovered,
            interactionSource = interactionSource,
            onClick = {
                val permalink = playlist.permalinkUrl
                val isTrackStation = playlist.isTrackStation || permalink?.contains("track-stations") == true || playlist.urn?.contains("track-stations") == true
                val isArtistStation = playlist.isArtistStation || permalink?.contains("artist-stations") == true || playlist.urn?.contains("artist-stations") == true

                val dest = when {
                    isTrackStation -> "station:${playlist.numericId}"
                    isArtistStation -> "station_artist:${playlist.numericId}"
                    playlist.kind == "system-playlist" && playlist.urn != null -> "system_playlist:${playlist.urn}"
                    playlist.id < 0 -> "local_playlist:${playlist.id}"
                    else -> playlist.id.toString()
                }
                playerViewModel.navigateToPlaylistId = dest
            },
            onArtistClick = {
                playlist.user?.id?.let { uid ->
                    navController.navigate("profile/$uid")
                }
            },
            onReposterClick = {
                reposter?.id?.let { uid ->
                    navController.navigate("profile/$uid")
                }
            },
            onRightClick = { playerViewModel.showPlaylistOptions(playlist) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackFeedItem(
    track: Track,
    reposter: com.alananasss.kittytune.domain.User?,
    createdAt: String?,
    isRepost: Boolean,
    hovered: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onArtistClick: () -> Unit,
    onReposterClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onRightClick)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            // Reposter info row (if repost)
            if (isRepost && reposter != null) {
                ReposterRow(
                    reposter = reposter,
                    createdAt = createdAt,
                    onClick = onReposterClick,
                )
                Spacer(Modifier.height(8.dp))
            }

            // Track content row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Cover art
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )

                // Track info
                Column(modifier = Modifier.weight(1f)) {
                    // Artist
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onArtistClick)) {
                        Text(
                            text = track.user?.username ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (track.user?.verified == true) {
                            Spacer(Modifier.width(3.dp))
                            Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        }
                    }
                    // Track title
                    Text(
                        text = track.title ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    // Stats row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (track.playbackCount > 0) {
                            StatChip(
                                icon = Icons.Rounded.PlayArrow,
                                label = formatCount(track.playbackCount),
                            )
                        }
                        if (track.likesCount > 0) {
                            StatChip(
                                icon = Icons.Rounded.Favorite,
                                label = formatCount(track.likesCount),
                            )
                        }
                        if (track.commentCount > 0) {
                            StatChip(
                                icon = Icons.Rounded.Comment,
                                label = formatCount(track.commentCount),
                            )
                        }
                    }
                }

                // Options button
                IconButton(
                    onClick = onRightClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Posted time (if not a repost, show post date at the bottom)
            if (!isRepost) {
                createdAt?.let { time ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatRelativeTime(time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistFeedItem(
    playlist: Playlist,
    reposter: com.alananasss.kittytune.domain.User?,
    createdAt: String?,
    isRepost: Boolean,
    hovered: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onArtistClick: () -> Unit,
    onReposterClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onRightClick)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp)) {
            if (isRepost && reposter != null) {
                ReposterRow(
                    reposter = reposter,
                    createdAt = createdAt,
                    onClick = onReposterClick,
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Cover art
                AsyncImage(
                    model = playlist.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )

                Column(modifier = Modifier.weight(1f)) {
                    // Artist
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onArtistClick)) {
                        Text(
                            text = playlist.user?.username ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (playlist.user?.verified == true) {
                            Spacer(Modifier.width(3.dp))
                            Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        }
                    }
                    // Playlist title
                    Text(
                        text = playlist.title ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    // Playlist badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            val trackCount = playlist.trackCount
                            val kind = playlist.kind
                            val label = when {
                                kind == "album" -> str("detail_album")
                                trackCount != null -> str("feed_playlist_tracks", trackCount)
                                else -> str("feed_ep")
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onRightClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!isRepost) {
                createdAt?.let { time ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatRelativeTime(time),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReposterRow(
    reposter: com.alananasss.kittytune.domain.User,
    createdAt: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = reposter.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape),
        )
        Icon(
            Icons.Rounded.Repeat,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = reposter.username ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        createdAt?.let { time ->
            Text(
                text = "· ${formatRelativeTime(time)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${(count / 1_000_000.0).let { if (it % 1 == 0.0) it.toInt().toString() else String.format("%.1f", it) }}M"
        count >= 1_000 -> "${(count / 1_000.0).let { if (it % 1 == 0.0) it.toInt().toString() else String.format("%.1f", it) }}K"
        else -> count.toString()
    }
}

private fun parseDateToMillis(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toLongOrNull()?.let { return it }

    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy/MM/dd HH:mm:ss Z",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    for (pattern in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(trimmed)
            if (date != null) return date.time
        } catch (_: Exception) {}
    }
    return null
}

private fun formatRelativeTime(rawTime: String): String {
    val timeMillis = parseDateToMillis(rawTime) ?: return rawTime
    val now = System.currentTimeMillis()
    val diff = (now - timeMillis).coerceAtLeast(0)

    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30
    val years = days / 365

    return when {
        minutes < 1 -> str("time_now")
        minutes < 60 -> str("time_minutes_ago", minutes.toInt())
        hours < 24 -> str("time_hours_ago", hours.toInt())
        days < 7 -> str("time_days_ago", days.toInt())
        weeks < 5 -> str("time_weeks_ago", weeks.toInt())
        months < 12 -> str("time_months_ago", months.toInt())
        years == 1L -> str("time_one_year_ago")
        else -> str("time_years_ago", years.toInt())
    }
}

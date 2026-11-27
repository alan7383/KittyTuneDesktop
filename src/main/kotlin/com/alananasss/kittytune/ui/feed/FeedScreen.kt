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
import com.alananasss.kittytune.ui.common.pressScale
import com.alananasss.kittytune.ui.common.TrackArtistClick
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.mix.MixRanking
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
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.StreamItem
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.common.rememberFilteredTracks
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonDefaults

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    playerViewModel: PlayerViewModel,
    navController: NavController,
    feedViewModel: FeedViewModel = viewModel { FeedViewModel(AppInstance.application) },
) {
    var following by rememberSaveable { mutableStateOf(false) }
    var followingPreviews by rememberSaveable { mutableStateOf(false) }
    val preferences = playerViewModel.playerPrefs
    val prefsState by Prefs.flow.collectAsState()
    val officialOnly = remember(prefsState) { preferences.getFeedOfficialOnlyEnabled() }
    val videoSamples = remember(prefsState) { preferences.getFeedVideoSamplesEnabled() }
    LaunchedEffect(officialOnly) { feedViewModel.applyOfficialOnly(officialOnly) }
    LaunchedEffect(following) { if (following) feedViewModel.ensureFollowing() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = !following, onClick = { following = false }, label = { Text(str("feed_tab_discover")) })
            FilterChip(selected = following, onClick = { following = true }, label = { Text(str("feed_tab_following")) })
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = officialOnly,
                onClick = { preferences.setFeedOfficialOnlyEnabled(!officialOnly) },
                label = { Text(str("pref_feed_official_only")) },
            )
            if (following && feedViewModel.isLoggedIn) {
                FilterChip(
                    selected = followingPreviews,
                    onClick = { followingPreviews = !followingPreviews },
                    label = { Text(str("feed_previews")) },
                )
            }
            IconButton(onClick = { if (following) feedViewModel.refresh() else feedViewModel.resetDiscover() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = str("feed_refresh"))
            }
        }

        if (following && !feedViewModel.isLoggedIn) {
            FeedMessage(str("feed_sign_in_hint"))
        } else if (following && !followingPreviews) {
            FollowingFeedList(playerViewModel, navController, feedViewModel, officialOnly)
        } else {
            val posts = if (following) {
                val tracks = feedViewModel.feedItems.mapNotNull { it.track }
                val visibleIds = rememberFilteredTracks(tracks).map { it.id }.toSet()
                feedViewModel.feedItems.mapNotNull { post ->
                    val track = post.track ?: return@mapNotNull null
                    if (track.id !in visibleIds || officialOnly && !MixRanking.isOfficialArtist(track)) return@mapNotNull null
                    PreviewPost(track, post.user, post.caption, post.type == "track-repost")
                }
            } else {
                rememberFilteredTracks(feedViewModel.discover.toList()).map { PreviewPost(it, it.user) }
            }
            val loading = if (following) feedViewModel.isLoading || feedViewModel.isLoadingMore else feedViewModel.discoverLoading
            val requestMore = if (following) feedViewModel::loadMore else feedViewModel::loadDiscover
            val failed = if (following) feedViewModel.error else feedViewModel.discoverFailed
            if (posts.isEmpty()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularWavyProgressIndicator() }
                    failed -> FeedMessage(str("error_generic"), if (following) feedViewModel::refresh else requestMore)
                    following -> FeedMessage(str("feed_empty"), if (feedViewModel.hasMore) requestMore else null, "feed_more")
                    else -> FeedMessage(str("feed_empty_discover"), requestMore)
                }
            } else {
                key(following, officialOnly) {
                    FeedPreviewPager(posts, playerViewModel, feedViewModel, videoSamples, requestMore)
                }
            }
        }
    }
}

@Composable
internal fun FeedMessage(message: String, onAction: (() -> Unit)? = null, actionLabel: String = "btn_retry") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onAction != null) Button(onClick = onAction) { Text(str(actionLabel)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FollowingFeedList(
    playerViewModel: PlayerViewModel,
    navController: NavController,
    feedViewModel: FeedViewModel,
    officialOnly: Boolean,
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
    LaunchedEffect(shouldLoadMore, feedViewModel.feedItems.size) {
        if (shouldLoadMore && !feedViewModel.isLoadingMore) {
            feedViewModel.loadMore()
        }
    }

    // Hide blocked tracks/artists (issue #41); playlists are never filtered.
    val visibleTracks = rememberFilteredTracks(feedViewModel.feedItems.mapNotNull { it.track })
    val feedItems = remember(feedViewModel.feedItems.toList(), visibleTracks, officialOnly) {
        val visibleIds = visibleTracks.map { it.id }.toSet()
        feedViewModel.feedItems.filter {
            val track = it.track
            track == null || track.id in visibleIds && (!officialOnly || MixRanking.isOfficialArtist(track))
        }
    }

    Column(Modifier.fillMaxSize()) {
        when {
            feedViewModel.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularWavyProgressIndicator()
                }
            }
            feedViewModel.error && feedViewModel.feedItems.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(str("error_generic"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(shapes = ButtonDefaults.shapes(), onClick = { feedViewModel.refresh() }) {
                            Text(str("btn_retry"))
                        }
                    }
                }
            }
            feedItems.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.DynamicFeed, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(str("feed_empty"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (feedViewModel.hasMore) TextButton(onClick = feedViewModel::loadMore) { Text(str("feed_more")) }
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
                    items(feedItems.size) { index ->
                        val item = feedItems[index]
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
            onArtistClick = playerViewModel::navigateToTrackArtist,
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
    onArtistClick: TrackArtistClick,
    onReposterClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = com.alananasss.kittytune.ui.theme.animatePaletteColor(
            selected = hovered,
            selectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unselectedColor = MaterialTheme.colorScheme.surfaceContainer,
            animationSpec = androidx.compose.animation.core.tween(120),
            label = "feedCardHover",
        ),
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onRightClick)
            .pressScale(interactionSource, pressedScale = 0.98f)
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple(), onClick = onClick),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ArtistLinkText(
                            track = track,
                            onArtistClick = onArtistClick,
                            text = track.displayArtist.ifBlank { track.user?.username.orEmpty() },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f, fill = false)
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
        color = com.alananasss.kittytune.ui.theme.animatePaletteColor(
            selected = hovered,
            selectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unselectedColor = MaterialTheme.colorScheme.surfaceContainer,
            animationSpec = androidx.compose.animation.core.tween(120),
            label = "feedCardHover",
        ),
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onRightClick)
            .pressScale(interactionSource, pressedScale = 0.98f)
            .clickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple(), onClick = onClick),
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
        com.alananasss.kittytune.ui.common.UserAvatar(
            url = reposter.avatarUrl,
            modifier = Modifier.size(20.dp),
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

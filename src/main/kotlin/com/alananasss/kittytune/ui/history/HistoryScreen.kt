package com.alananasss.kittytune.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBackClick: (() -> Unit)? = null,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    historyViewModel: HistoryViewModel
) {
    var showClearDialog by remember { mutableStateOf(false) }

    val currentTab = historyViewModel.selectedTab
    val displayedTracks = historyViewModel.displayedTracks
    val displayedContexts = historyViewModel.displayedContexts

    val tracksListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val contextsListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val groupedTracks = remember(displayedTracks.toList()) {
        displayedTracks.groupBy { item ->
            formatDateHeader(item.playedAt)
        }
    }

    val groupedContexts = remember(displayedContexts.toList()) {
        displayedContexts.groupBy { item ->
            formatDateHeader(item.playedAt)
        }
    }

    val shouldLoadMoreTracks by remember {
        derivedStateOf {
            val layoutInfo = tracksListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 8
        }
    }

    val shouldLoadMoreContexts by remember {
        derivedStateOf {
            val layoutInfo = contextsListState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 8
        }
    }

    LaunchedEffect(shouldLoadMoreTracks) {
        if (shouldLoadMoreTracks && !historyViewModel.isLoadingMoreTracks && historyViewModel.canLoadMoreTracks) {
            historyViewModel.loadMoreTracks()
        }
    }

    LaunchedEffect(shouldLoadMoreContexts) {
        if (shouldLoadMoreContexts && !historyViewModel.isLoadingMoreContexts && historyViewModel.canLoadMoreContexts) {
            historyViewModel.loadMoreContexts()
        }
    }

    if (showClearDialog) {
        val dialogTitle = if (currentTab == HistoryTab.TRACKS) {
            str("history_clear_tracks_title")
        } else {
            str("history_clear_contexts_title")
        }

        val dialogDesc = if (currentTab == HistoryTab.TRACKS) {
            str("history_clear_tracks_desc")
        } else {
            str("history_clear_contexts_desc")
        }

        EscapableAlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
            text = { Text(dialogDesc) },
            confirmButton = {
                Button(
                    onClick = {
                        historyViewModel.clearHistoryForCurrentTab()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(str("btn_clear"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Desktop Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = str("btn_back")
                    )
                }
            }

            Text(
                text = str("history_title"),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.weight(1f))

            val hasItems = if (currentTab == HistoryTab.TRACKS) {
                historyViewModel.tracksHistory.isNotEmpty()
            } else {
                historyViewModel.contextsHistory.isNotEmpty()
            }

            if (hasItems) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = str("btn_clear"),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            IconButton(onClick = { historyViewModel.loadData(forceRefresh = true) }) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = str("btn_retry")
                )
            }
        }

        // Desktop Search & Tab controls toolbar (FlowRow wraps gracefully on smaller window sizes)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ExpressiveConnectedButtonGroup(
                options = listOf(HistoryTab.TRACKS, HistoryTab.CONTEXTS),
                selectedOption = currentTab,
                onOptionSelected = { historyViewModel.selectedTab = it },
                fillMaxWidth = false,
                labelProvider = { tab ->
                    Text(
                        text = when (tab) {
                            HistoryTab.TRACKS -> str("history_tab_tracks")
                            HistoryTab.CONTEXTS -> str("history_tab_contexts")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )

            // Sleek desktop search bar
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .widthIn(min = 220.dp, max = 320.dp)
                    .height(38.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (historyViewModel.searchQuery.isEmpty()) {
                            Text(
                                text = str("history_search_hint"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = historyViewModel.searchQuery,
                            onValueChange = { historyViewModel.searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .trackTextInput()
                        )
                    }
                    if (historyViewModel.searchQuery.isNotEmpty()) {
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = { historyViewModel.searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = str("btn_cancel"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (historyViewModel.isGuest) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str("lib_guest_mode"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = str("history_guest_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = { onNavigate("profile") }) {
                        Text(
                            str("login_soundcloud"),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                if (targetState == HistoryTab.CONTEXTS) {
                    (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> (width * 0.15f).toInt() } + fadeIn(animationSpec = tween(280)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> (-width * 0.15f).toInt() } + fadeOut(animationSpec = tween(180)))
                } else {
                    (slideInHorizontally(animationSpec = tween(280, easing = FastOutSlowInEasing)) { width -> (-width * 0.15f).toInt() } + fadeIn(animationSpec = tween(280)))
                        .togetherWith(slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> (width * 0.15f).toInt() } + fadeOut(animationSpec = tween(180)))
                }
            },
            label = "HistoryTabTransition",
            modifier = Modifier.fillMaxSize()
        ) { tab ->
            when (tab) {
                HistoryTab.TRACKS -> {
                    if (historyViewModel.isLoadingTracks && displayedTracks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (displayedTracks.isEmpty()) {
                        EmptyHistoryView(
                            title = str("history_empty_tracks"),
                            subtitle = if (historyViewModel.searchQuery.isNotBlank()) str("no_results") else "",
                            onExploreClick = { onNavigate("home") }
                        )
                    } else {
                        LazyColumn(
                            state = tracksListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            groupedTracks.forEach { (dateHeader, items) ->
                                item(key = "header_$dateHeader") {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp)
                                    )
                                }

                                itemsIndexed(
                                    items = items,
                                    key = { index, historyTrack ->
                                        "${dateHeader}_track_${historyTrack.track.id}_${historyTrack.playedAt}_$index"
                                    }
                                ) { _, historyTrack ->
                                    val isPlaying = playerViewModel.currentTrack?.id == historyTrack.track.id
                                    HistoryTrackRow(
                                        item = historyTrack,
                                        isPlaying = isPlaying,
                                        onClick = {
                                            val tracksList = displayedTracks.map { it.track }
                                            val clickedIndex =
                                                tracksList.indexOfFirst { it.id == historyTrack.track.id }
                                            if (clickedIndex != -1) {
                                                val queue = tracksList.drop(clickedIndex)
                                                playerViewModel.playPlaylist(
                                                    tracks = queue,
                                                    startIndex = 0,
                                                    context = PlaybackContext(
                                                        displayText = str("history_title"),
                                                        navigationId = "history"
                                                    )
                                                )
                                            }
                                        },
                                        onMoreClick = {
                                            playerViewModel.showTrackOptions(historyTrack.track)
                                        },
                                        onArtistClick = { playerViewModel.navigateToTrackArtist(it) }
                                    )
                                }
                            }

                            if (historyViewModel.isLoadingMoreTracks) {
                                item(key = "loader_more_tracks") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HistoryTab.CONTEXTS -> {
                    if (historyViewModel.isLoadingContexts && displayedContexts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (displayedContexts.isEmpty()) {
                        EmptyHistoryView(
                            title = str("history_empty_contexts"),
                            subtitle = if (historyViewModel.searchQuery.isNotBlank()) str("no_results") else "",
                            onExploreClick = { onNavigate("home") }
                        )
                    } else {
                        LazyColumn(
                            state = contextsListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                        ) {
                            groupedContexts.forEach { (dateHeader, items) ->
                                item(key = "header_ctx_$dateHeader") {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp)
                                    )
                                }

                                itemsIndexed(
                                    items = items,
                                    key = { index, contextItem ->
                                        "${dateHeader}_ctx_${contextItem.id}_${contextItem.playedAt}_$index"
                                    }
                                ) { _, contextItem ->
                                    HistoryContextRow(
                                        item = contextItem,
                                        onClick = {
                                            val target = contextItem.targetNavId
                                            when {
                                                target.startsWith("profile:") -> onNavigate("profile/${target.removePrefix("profile:")}")
                                                target.startsWith("station:") -> onNavigate("playlist_detail/station:${target.removePrefix("station:")}")
                                                target.startsWith("station_artist:") -> onNavigate("playlist_detail/station_artist:${target.removePrefix("station_artist:")}")
                                                target == "likes" -> onNavigate("playlist_detail/likes")
                                                target == "downloads" -> onNavigate("playlist_detail/downloads")
                                                else -> onNavigate("playlist_detail/$target")
                                            }
                                        }
                                    )
                                }
                            }

                            if (historyViewModel.isLoadingMoreContexts) {
                                item(key = "loader_more_contexts") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTrackRow(
    item: HistoryTrackItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onArtistClick: ((Track) -> Unit)? = null
) {
    val track = item.track
    val timeStr = remember(item.playedAt) {
        val millis = if (item.playedAt in 1..99_999_999_999L) item.playedAt * 1000L else item.playedAt
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    Surface(
        onClick = onClick,
        color = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val artworkModel = track.artworkUrl?.takeIf { it.isNotBlank() && !it.contains("picsum.photos") }
                    ?: track.fullResArtwork.takeIf { it.isNotBlank() && !it.contains("picsum.photos") }
                    ?: track.user?.avatarUrl?.takeIf { it.isNotBlank() && !it.contains("picsum.photos") }

                if (!artworkModel.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: str("history_untitled_track"),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ArtistLinkText(
                        track = track,
                        onArtistClick = onArtistClick,
                        text = track.user?.username?.takeIf { it.isNotBlank() }
                            ?: str("history_unknown_artist"),
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (track.user?.verified == true) {
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "• $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun HistoryContextRow(
    item: HistoryContextItem,
    onClick: () -> Unit
) {
    val timeStr = remember(item.playedAt) {
        val millis = if (item.playedAt in 1..99_999_999_999L) item.playedAt * 1000L else item.playedAt
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    val typeLabel = when (item.type) {
        HistoryContextType.PLAYLIST -> str("history_type_playlist")
        HistoryContextType.ALBUM -> str("lib_albums")
        HistoryContextType.ARTIST_STATION, HistoryContextType.TRACK_STATION -> str("history_type_station")
        HistoryContextType.ARTIST -> str("history_type_artist")
        HistoryContextType.LIKES -> str("history_type_likes")
        else -> str("history_type_playlist")
    }

    val typeIcon = when (item.type) {
        HistoryContextType.PLAYLIST, HistoryContextType.ALBUM -> Icons.Rounded.QueueMusic
        HistoryContextType.ARTIST_STATION, HistoryContextType.TRACK_STATION -> Icons.Rounded.Radio
        HistoryContextType.ARTIST -> Icons.Rounded.Person
        HistoryContextType.LIKES -> Icons.Rounded.Favorite
        else -> Icons.Rounded.Folder
    }

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isLikes = item.type == HistoryContextType.LIKES || item.id == "likes" || item.id == "pin_likes" ||
                    item.title.equals("Titres Likés", ignoreCase = true) ||
                    item.title.equals(str("lib_liked_tracks"), ignoreCase = true) ||
                    item.title.equals(str("history_title_likes"), ignoreCase = true) ||
                    item.title.equals("Liked Tracks", ignoreCase = true)
            val isCircle = item.type == HistoryContextType.ARTIST
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(if (isCircle) CircleShape else RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (isLikes) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = onClick,
                        label = {
                            Text(typeLabel, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = null,
                        modifier = Modifier.height(22.dp)
                    )

                    if (item.subtitle.isNotBlank() && item.subtitle != typeLabel) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Text(
                        text = "• $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun EmptyHistoryView(
    title: String,
    subtitle: String,
    onExploreClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(onClick = onExploreClick) {
                Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(str("explorer_title"))
            }
        }
    }
}

private fun formatDateHeader(timestamp: Long): String {
    if (timestamp <= 0L) return str("date_today")
    val millis = if (timestamp in 1..99_999_999_999L) timestamp * 1000L else timestamp

    val calendar = Calendar.getInstance()
    val today = calendar.get(Calendar.DAY_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)

    calendar.timeInMillis = millis
    val itemDay = calendar.get(Calendar.DAY_OF_YEAR)
    val itemYear = calendar.get(Calendar.YEAR)

    return if (year == itemYear) {
        when (today - itemDay) {
            0 -> str("date_today")
            1 -> str("date_yesterday")
            else -> SimpleDateFormat("dd MMMM", com.alananasss.kittytune.core.Strings.locale()).format(Date(millis))
        }
    } else {
        SimpleDateFormat("dd MMMM yyyy", com.alananasss.kittytune.core.Strings.locale()).format(Date(millis))
    }
}

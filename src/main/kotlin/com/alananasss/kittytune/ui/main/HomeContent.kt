@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import com.alananasss.kittytune.core.trackTextInput
import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.onClick
import androidx.compose.foundation.PointerMatcher
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import com.alananasss.kittytune.domain.isDefaultAvatar
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.alananasss.kittytune.ui.common.Tip
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import com.alananasss.kittytune.data.local.HistoryItem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alananasss.kittytune.ui.modifiers.squish
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import androidx.compose.ui.graphics.graphicsLayer
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.home.HomeViewModel
import com.alananasss.kittytune.ui.home.SearchFilter
import com.alananasss.kittytune.ui.home.SearchSource
import com.alananasss.kittytune.ui.home.YandexNotice
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.util.Calendar

/**
 * Home feed: greeting, recently-played quick tiles (2x3 grid like the reference)
 * and the SoundCloud personalized section carousels from HomeViewModel.
 * When a search is active, results replace the feed (embedded search, like Android).
 */
@Composable
fun HomeContent(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    val vm = homeViewModel

    if (vm.isSearching) {
        SearchResults(vm, playerViewModel, navController)
        return
    }

    if (vm.isLoading && vm.homeSections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator()
        }
        return
    }

    val history by vm.historyFlow.collectAsState(initial = emptyList())

    val contextHistory = remember(history) {
        history.filter { it.id != "playlist:0" && !it.title.equals("history", ignoreCase = true) }
            .distinctBy { it.id }
    }

    var quickPage by remember { mutableStateOf(0) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 880.dp
        val pageSize = if (isWideScreen) 9 else 6
        val maxPages = ((contextHistory.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val currentPage = quickPage.coerceIn(0, maxPages - 1)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Greeting
            item {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                // Desktop-only greeting (no Android key for this) — localized by app language.
                val lang = Strings.resolvedLanguage
                val greeting = when (hour) {
                    in 5..11 -> when (lang) { "fr" -> "Bonjour"; "hu" -> "Jó reggelt"; "ru" -> "Доброе утро"; else -> "Good morning" }
                    in 12..17 -> when (lang) { "fr" -> "Bon après-midi"; "hu" -> "Jó napot"; "ru" -> "Добрый день"; else -> "Good afternoon" }
                    else -> when (lang) { "fr" -> "Bonsoir"; "hu" -> "Jó estét"; "ru" -> "Добрый вечер"; else -> "Good evening" }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (maxPages > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (currentPage > 0) quickPage = currentPage - 1 },
                                enabled = currentPage > 0,
                                modifier = Modifier.size(32.dp),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { if (currentPage < maxPages - 1) quickPage = currentPage + 1 },
                                enabled = currentPage < maxPages - 1,
                                modifier = Modifier.size(32.dp),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }

            // Quick tiles: recently played contexts (3 rows x 3 cols or 2 rows x 3 cols with pagination)
            if (contextHistory.isNotEmpty()) {
                item {
                    val pageItems = contextHistory.drop(currentPage * pageSize).take(pageSize)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pageItems.chunked(3).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { entry ->
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
                                        modifier = Modifier.weight(1f),
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
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            // "Your Mix" and "Listening Stats" split side-by-side on wide window, stacked on narrow (issue #33)
            item {
                if (isWideScreen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(Modifier.weight(1.15f)) {
                            StartMixingCard(playerViewModel)
                        }
                        Box(Modifier.weight(1f)) {
                            ListeningStatsCard(navController)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        StartMixingCard(playerViewModel)
                        ListeningStatsCard(navController)
                    }
                }
            }

            // Section carousels
            items(vm.homeSections, key = { it.title }) { section ->
                Column {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    section.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                
                Box {
                    LazyRow(
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(section.content) { item ->
                            when (item) {
                                is Track -> MediaCard(
                                    title = item.title ?: "",
                                    subtitle = item.user?.username ?: "",
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
                    
                    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
                    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
                    val surfaceColor = MaterialTheme.colorScheme.surface
                    
                    val alphaLeft by androidx.compose.animation.core.animateFloatAsState(if (canScrollBackward) 1f else 0f)
                    val alphaRight by androidx.compose.animation.core.animateFloatAsState(if (canScrollForward) 1f else 0f)
                    
                    Box(Modifier.matchParentSize()) {
                        if (alphaLeft > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(72.dp)
                                    .align(Alignment.CenterStart)
                                    .graphicsLayer { alpha = alphaLeft }
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(surfaceColor, Color.Transparent)
                                        )
                                    ),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                IconButton(onClick = {
                                        scope.launch {
                                            val first = listState.firstVisibleItemIndex
                                            listState.animateScrollToItem(maxOf(0, first - 3))
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                                }
                            }
                        }
                        
                        if (alphaRight > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(72.dp)
                                    .align(Alignment.CenterEnd)
                                    .graphicsLayer { alpha = alphaRight }
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color.Transparent, surfaceColor)
                                        )
                                    ),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                IconButton(onClick = {
                                        scope.launch {
                                            val first = listState.firstVisibleItemIndex
                                            listState.animateScrollToItem(minOf(section.content.size - 1, first + 3))
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                }
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
private fun QuickTile(
    title: String,
    imageUrl: String?,
    isLikes: Boolean = false,
    isDownloads: Boolean = false,
    isLocalFiles: Boolean = false,
    modifier: Modifier = Modifier,
    onRightClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest
             else MaterialTheme.colorScheme.surfaceContainerHigh

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .hoverable(interaction)
            .let { m ->
                if (onRightClick != null) {
                    m.onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Secondary),
                        onClick = onRightClick
                    ).clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                } else {
                    m.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isLikes -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            isDownloads -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF00C853), Color(0xFF69F0AE)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DownloadForOffline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            isLocalFiles -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF0091EA), Color(0xFF40C4FF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            !imageUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp),
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    round: Boolean,
    onRightClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    androidx.compose.material3.TextButton(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
            containerColor = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(6.dp),
        modifier = Modifier
            .width(160.dp)
            .let { m ->
                if (onRightClick != null) {
                    m.onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Secondary),
                        onClick = onRightClick
                    )
                } else m
            }
    ) {
        Column {
            AsyncImage(
                model = if (round && artworkUrl.isDefaultAvatar()) null else artworkUrl,
                contentDescription = null,
                error = if (round) androidx.compose.ui.res.painterResource("drawable/ic_default_user_artwork_placeholder_round.xml") else null,
                fallback = if (round) androidx.compose.ui.res.painterResource("drawable/ic_default_user_artwork_placeholder_round.xml") else null,
                modifier = Modifier
                    .size(148.dp)
                    .clip(if (round) CircleShape else RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Full Search Experience — mirrors Android KittyTune's SearchScreen
// ──────────────────────────────────────────────────────────────────────

/**
 * Which platform to search, as one button rather than one button each (issue #33).
 *
 * "Only after them make the platform selection button, again 1 button, and when you click on it you can
 * switch to another one."
 *
 * A menu rather than a button that cycles, for the reason he gave in the same message: he wants Apple
 * Music and Yandex Music adding. Cycling through three is a shortcut; cycling through five is a puzzle,
 * and a menu is the same single button either way.
 */
@Composable
private fun SearchSourceButton(vm: HomeViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val sources = listOf(
        SearchSource.SOUNDCLOUD,
        SearchSource.YOUTUBE,
        SearchSource.SPOTIFY,
        SearchSource.APPLE_MUSIC,
        SearchSource.YANDEX_MUSIC,
    )

    Box {
        com.alananasss.kittytune.ui.common.Tip(str("search_source_tooltip")) {
            androidx.compose.material3.OutlinedButton(
                onClick = { expanded = true },
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text(
                    text = searchSourceLabel(vm.activeSearchSource),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sources.forEach { source ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(searchSourceLabel(source)) },
                    leadingIcon = {
                        // The current one is marked rather than merely styled: a menu of three names with
                        // no indication of which is live reads as three actions, not as a choice.
                        if (source == vm.activeSearchSource) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        if (source != vm.activeSearchSource) vm.onSearchSourceChanged(source)
                    },
                )
            }
        }
    }
}

/** Platform names are brands, so they are not translated. */
private fun searchSourceLabel(source: SearchSource): String = when (source) {
    SearchSource.SOUNDCLOUD -> "SoundCloud"
    SearchSource.YOUTUBE -> "YouTube"
    SearchSource.SPOTIFY -> "Spotify"
    SearchSource.APPLE_MUSIC -> "Apple Music"
    SearchSource.YANDEX_MUSIC -> "Yandex Music"
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SearchResults(
    vm: HomeViewModel,
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    val hasQuery = vm.searchQuery.isNotBlank()
    val listState = rememberLazyListState()

    // Detect end-of-list for load-more
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisible >= totalItems - 3 && totalItems > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasQuery && vm.activeFilter != SearchFilter.ALL && !vm.isSearchLoadingMore) {
            vm.loadMoreSearchResults()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Top bar: Source toggle + Filter chips ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // What you are looking for comes before where to look for it (issue #33).
            //
            // "Make the first filter 'All', then 'Tracks', 'Artists', 'Playlists' and only after them make
            // the platform selection button, again 1 button, and when you click on it you can switch to
            // another one."
            //
            // He is right about the order for a reason worth stating: the platform is set once and then
            // left alone, while All/Tracks/Artists is touched on every search. The thing you reach for
            // constantly was sitting to the right of the thing you almost never change, and three
            // side-by-side platform buttons took as much of the row as the four filters did — which is
            // also why the row started scrolling sideways on a narrow window.
            if (vm.activeSearchSource == SearchSource.SOUNDCLOUD || vm.activeSearchSource == SearchSource.SPOTIFY) {
                val filters = listOf(
                    SearchFilter.ALL,
                    SearchFilter.TRACKS,
                    SearchFilter.ARTISTS,
                    SearchFilter.PLAYLISTS,
                )
                com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup(
                    options = filters,
                    selectedOption = vm.activeFilter,
                    onOptionSelected = { vm.onFilterChanged(it) },
                    fillMaxWidth = false,
                    labelProvider = { filter ->
                        val label = when (filter) {
                            SearchFilter.ALL -> str("search_filter_all")
                            SearchFilter.TRACKS -> str("search_filter_tracks")
                            SearchFilter.ARTISTS -> str("lib_artists")
                            SearchFilter.PLAYLISTS -> str("lib_playlists")
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                )

                VerticalDivider(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            SearchSourceButton(vm)
        }

        // ── Loading bar ──
        if (vm.isSearchLoading) {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }

        // ── Content ──
        if (!hasQuery) {
            // Browse categories when search bar is empty
            BrowseCategories(vm, navController)
        } else if (vm.isSearchLoading &&
            vm.searchResultsTracks.isEmpty() &&
            vm.searchResultsArtists.isEmpty() &&
            vm.searchResultsPlaylists.isEmpty() &&
            vm.searchResultsYoutube.isEmpty() &&
            vm.searchResultsSpotify.isEmpty() &&
            vm.searchResultsApple.isEmpty()
        ) {
            // Initial loading — centered spinner
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
        } else {
            // Actual results
            when (vm.activeSearchSource) {
                SearchSource.YOUTUBE -> YoutubeResults(vm, playerViewModel, listState)
                SearchSource.SPOTIFY -> SpotifyResults(vm, playerViewModel, navController)
                // One list for both catalogues, because they produce the same type and behave the same way:
                // press a row, it goes and finds the song on a source that can play it (issue #33).
                SearchSource.APPLE_MUSIC, SearchSource.YANDEX_MUSIC ->
                    CatalogResults(vm, playerViewModel, listState)
                SearchSource.SOUNDCLOUD -> SoundCloudResults(vm, playerViewModel, navController, listState)
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        shapes = androidx.compose.material3.ButtonDefaults.shapes(),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        border = if (!selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Browse Categories (shown when search is active but query is empty)
// ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowseCategories(vm: HomeViewModel, navController: NavController) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Personalized categories
        if (vm.personalizedCategories.isNotEmpty()) {
            item {
                Text(
                    text = str("search_section_personalized"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    vm.personalizedCategories.forEach { cat ->
                        CategoryChip(cat.title, cat.icon) {
                            navController.navigate("tag/${cat.query}")
                        }
                    }
                }
            }
        }

        // Moods
        item {
            Text(
                text = str("search_section_moods"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.moodCategories.forEach { cat ->
                    CategoryChip(cat.title, cat.icon) {
                        navController.navigate("tag/${cat.query}")
                    }
                }
            }
        }

        // Genres
        item {
            Text(
                text = str("search_section_genres"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                vm.genreCategories.forEach { cat ->
                    CategoryChip(cat.title, cat.icon) {
                        navController.navigate("tag/${cat.query}")
                    }
                }
            }
        }
    }
    }


@Composable
private fun CategoryChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  SoundCloud Search Results
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun SoundCloudResults(
    vm: HomeViewModel,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val tracks = vm.searchResultsTracks
    val artists = vm.searchResultsArtists
    val playlists = vm.searchResultsPlaylists

    if (tracks.isEmpty() && artists.isEmpty() && playlists.isEmpty() && !vm.isSearchLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = str("search_no_results"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // ── ALL mode: grouped sections ──
        if (vm.activeFilter == SearchFilter.ALL) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // Left Column: Tracks
                    Column(modifier = Modifier.weight(1f)) {
                        if (tracks.isNotEmpty()) {
                            SectionHeader(
                                title = str("search_filter_tracks"),
                                icon = Icons.Rounded.MusicNote,
                                onSeeAll = { vm.onFilterChanged(SearchFilter.TRACKS) }
                            )
                            Spacer(Modifier.height(8.dp))
                            tracks.take(5).forEach { track ->
                                SearchTrackRow(track, playerViewModel)
                            }
                        }
                    }
                    
                    // Right Column: Artists & Playlists
                    Column(modifier = Modifier.weight(1f)) {
                        if (artists.isNotEmpty()) {
                            SectionHeader(
                                title = str("search_filter_artists"),
                                icon = Icons.Rounded.Person,
                                onSeeAll = { vm.onFilterChanged(SearchFilter.ARTISTS) }
                            )
                            Spacer(Modifier.height(8.dp))
                            artists.take(4).forEach { user ->
                                SearchArtistRow(user) {
                                    playerViewModel.navigateToPlaylistId = user.profileNavId
                                }
                            }
                        }
                        
                        if (playlists.isNotEmpty()) {
                            if (artists.isNotEmpty()) Spacer(Modifier.height(24.dp))
                            SectionHeader(
                                title = str("search_filter_playlists"),
                                icon = Icons.Rounded.QueueMusic,
                                onSeeAll = { vm.onFilterChanged(SearchFilter.PLAYLISTS) }
                            )
                            Spacer(Modifier.height(8.dp))
                            playlists.take(4).forEach { playlist ->
                                SearchPlaylistRow(
                                    playlist,
                                    onRightClick = { playerViewModel.showPlaylistOptions(playlist) },
                                ) {
                                    val dest = when {
                                        playlist.isTrackStation || playlist.permalinkUrl?.contains("track-stations") == true || playlist.urn?.contains("track-stations") == true -> "station:${playlist.numericId}"
                                        playlist.isArtistStation || playlist.permalinkUrl?.contains("artist-stations") == true || playlist.urn?.contains("artist-stations") == true -> "station_artist:${playlist.numericId}"
                                        playlist.urn?.startsWith("soundcloud:system-playlists:") == true -> "system_playlist:${playlist.urn}"
                                        playlist.id < 0 -> "local_playlist:${playlist.id}"
                                        else -> playlist.id.toString()
                                    }
                                    playerViewModel.navigateToPlaylistId = dest
                                }
                            }
                        }
                    }
                }
            }
        }
        // ── TRACKS filter ──
        else if (vm.activeFilter == SearchFilter.TRACKS) {
            items(tracks) { track ->
                SearchTrackRow(track, playerViewModel)
            }
            if (vm.isSearchLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
        // ── ARTISTS filter ──
        else if (vm.activeFilter == SearchFilter.ARTISTS) {
            items(artists) { user ->
                SearchArtistRow(user) {
                    playerViewModel.navigateToPlaylistId = user.profileNavId
                }
            }
            if (vm.isSearchLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
        // ── PLAYLISTS filter ──
        else if (vm.activeFilter == SearchFilter.PLAYLISTS) {
            items(playlists) { playlist ->
                SearchPlaylistRow(
                    playlist,
                    onRightClick = { playerViewModel.showPlaylistOptions(playlist) },
                ) {
                    playerViewModel.navigateToPlaylistId = playlist.id.toString()
                }
            }
            if (vm.isSearchLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
        }
    }
    }


// ──────────────────────────────────────────────────────────────────────
//  YouTube Search Results
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun YoutubeResults(
    vm: HomeViewModel,
    playerViewModel: PlayerViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    val tracks = vm.searchResultsYoutube

    if (tracks.isEmpty() && !vm.isSearchLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = str("search_no_results"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(tracks) { track ->
            SearchTrackRow(track, playerViewModel)
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Spotify Search Results
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun CatalogResults(
    vm: HomeViewModel,
    playerViewModel: PlayerViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    if (vm.searchResultsApple.isEmpty() && !vm.isSearchLoading) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                // "Nothing matched" and "this is not available where you are" look identical as an empty
                // list and are not the same thing to read. Yandex has two such states — no token, and a
                // country it does not serve — so it says which (issue #33).
                text = when (vm.yandexNotice) {
                    YandexNotice.NOT_CONNECTED -> str("yandex_not_connected")
                    YandexNotice.REGION_BLOCKED -> str("yandex_region_blocked")
                    YandexNotice.FAILED -> str("yandex_failed")
                    null -> str("search_no_results")
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        // Said once at the top rather than on every row: these results come from a catalogue, and pressing
        // one goes looking for the same song somewhere it can be played. Better to explain the rule than to
        // decorate forty rows with the same badge (issue #33).
        item {
            Text(
                text = when (vm.activeSearchSource) {
                    SearchSource.YANDEX_MUSIC -> str("search_yandex_notice")
                    else -> str("search_apple_music_notice")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(vm.searchResultsApple, key = { it.key }) { song ->
            CatalogSongRow(
                song = song,
                resolving = vm.resolvingAppleSongId == song.key,
                onClick = {
                    vm.resolveAppleSong(song) { track ->
                        if (track != null) {
                            playerViewModel.playPlaylist(listOf(track), 0)
                        } else {
                            playerViewModel.notify(str("search_apple_music_unplayable"))
                        }
                    }
                },
            )
        }
    }
}

/**
 * One catalogue entry.
 *
 * Shaped like the other search rows on purpose — artwork, title, artist — because it is the same kind of
 * thing to the person reading it. What is different is only what happens on a press, and the spinner in
 * place of nothing is what says so: this press is a search, and it takes as long as a search does.
 */
@Composable
private fun CatalogSongRow(
    song: com.alananasss.kittytune.data.catalog.CatalogSong,
    resolving: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = !resolving, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        coil3.compose.AsyncImage(
            model = song.artworkUrl,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(song.artist, song.album).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (resolving) {
            CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
        } else if (song.durationMs > 0L) {
            Text(
                text = com.alananasss.kittytune.utils.makeTimeString(song.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SpotifyResults(
    vm: HomeViewModel,
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    val hasTracks = vm.searchResultsSpotify.isNotEmpty()
    val hasArtists = vm.searchResultsSpotifyArtists.isNotEmpty()
    val hasPlaylistsOrAlbums = vm.searchResultsSpotifyPlaylists.isNotEmpty() || vm.searchResultsSpotifyAlbums.isNotEmpty()

    if (!hasTracks && !hasArtists && !hasPlaylistsOrAlbums && !vm.isSearchLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = str("search_no_results"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // ARTISTS SECTION
        if (hasArtists && (vm.activeFilter == SearchFilter.ALL || vm.activeFilter == SearchFilter.ARTISTS)) {
            if (vm.activeFilter == SearchFilter.ALL) {
                item {
                    SectionHeader(
                        title = str("lib_artists"),
                        icon = Icons.Rounded.Person,
                        onSeeAll = { vm.onFilterChanged(SearchFilter.ARTISTS) }
                    )
                }
                item {
                    vm.searchResultsSpotifyArtists.take(4).forEach { artist ->
                        SearchArtistRow(artist.toUser()) {
                            playerViewModel.navigateToPlaylistId = "spotify_artist:${artist.id}"
                        }
                    }
                }
            } else {
                items(vm.searchResultsSpotifyArtists.size) { index ->
                    val artist = vm.searchResultsSpotifyArtists[index]
                    SearchArtistRow(artist.toUser()) {
                        playerViewModel.navigateToPlaylistId = "spotify_artist:${artist.id}"
                    }
                }
            }
        }

        // TRACKS SECTION
        if (hasTracks && (vm.activeFilter == SearchFilter.ALL || vm.activeFilter == SearchFilter.TRACKS)) {
            if (vm.activeFilter == SearchFilter.ALL) {
                item {
                    SectionHeader(
                        title = str("search_filter_tracks"),
                        icon = Icons.Rounded.MusicNote,
                        onSeeAll = { vm.onFilterChanged(SearchFilter.TRACKS) }
                    )
                }
                item {
                    vm.searchResultsSpotify.take(5).forEach { track ->
                        SearchTrackRow(track, playerViewModel)
                    }
                }
            } else {
                items(vm.searchResultsSpotify.size) { index ->
                    val track = vm.searchResultsSpotify[index]
                    SearchTrackRow(track, playerViewModel)
                }
            }
        }

        // PLAYLISTS & ALBUMS SECTION
        if (hasPlaylistsOrAlbums && (vm.activeFilter == SearchFilter.ALL || vm.activeFilter == SearchFilter.PLAYLISTS)) {
            val allPlaylists = vm.searchResultsSpotifyPlaylists.map { it.toPlaylist() } +
                    vm.searchResultsSpotifyAlbums.map { it.toPlaylist() }

            if (vm.activeFilter == SearchFilter.ALL) {
                item {
                    SectionHeader(
                        title = str("lib_playlists"),
                        icon = Icons.Rounded.QueueMusic,
                        onSeeAll = { vm.onFilterChanged(SearchFilter.PLAYLISTS) }
                    )
                }
                item {
                    allPlaylists.take(4).forEach { playlist ->
                        SearchPlaylistRow(
                            playlist,
                            onRightClick = { playerViewModel.showPlaylistOptions(playlist) },
                        ) {
                            val navId = playlist.urn ?: if (playlist.isRealAlbum) "spotify:album:${playlist.permalink ?: playlist.id}" else "spotify:playlist:${playlist.permalink ?: playlist.id}"
                            playerViewModel.navigateToPlaylistId = navId
                        }
                    }
                }
            } else {
                items(allPlaylists.size) { index ->
                    val playlist = allPlaylists[index]
                    SearchPlaylistRow(
                        playlist,
                        onRightClick = { playerViewModel.showPlaylistOptions(playlist) },
                    ) {
                        val navId = playlist.urn ?: if (playlist.isRealAlbum) "spotify:album:${playlist.permalink ?: playlist.id}" else "spotify:playlist:${playlist.permalink ?: playlist.id}"
                        playerViewModel.navigateToPlaylistId = navId
                    }
                }
            }
        }
    }
}

/** Same station-marker resolution as the Android home screen. */
private fun getStationNavId(playlist: Playlist): String {
    val isLikedBy = playlist.permalinkUrl == "liked_by_marker"
    val isArtistStation = playlist.permalinkUrl == "artist_station_marker"
    val isTrackStation = playlist.permalinkUrl == "track_station_marker"
    val isYoutubeRadio = playlist.permalinkUrl?.startsWith("yt_radio:") == true
    val isSpotifyRadio = playlist.permalinkUrl?.startsWith("spotify_radio:") == true || playlist.permalinkUrl?.startsWith("spotify:") == true
    val isSystemPlaylist = playlist.urn?.startsWith("soundcloud:system-playlists:") == true

    return when {
        isSystemPlaylist -> "system_playlist:${playlist.urn}"
        isLikedBy -> "liked_by:${playlist.id}"
        isArtistStation -> "station_artist:${playlist.id}"
        isYoutubeRadio -> playlist.permalinkUrl!!
        isSpotifyRadio -> playlist.permalinkUrl!!
        isTrackStation -> "station:${playlist.id}"
        else -> playlist.id.toString()
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Search Result Row Components
// ──────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSeeAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = str("search_see_all"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSeeAll)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchTrackRow(track: Track, playerViewModel: PlayerViewModel) {
    val durationMs = track.durationMs ?: 0L
    val minutes = durationMs / 60000
    val seconds = (durationMs % 60000) / 1000
    val durationText = if (durationMs > 0) "${minutes}:${seconds.toString().padStart(2, '0')}" else ""

    TextButton(
        onClick = { playerViewModel.playPlaylist(listOf(track), 0) },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                onClick = { playerViewModel.showTrackOptions(track) }
            )
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.fullResArtwork,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtistLinkText(
                    track = track,
                    onArtistClick = { playerViewModel.navigateToTrackArtist(it) },
                    text = track.user?.username ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.user?.verified == true) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                }
                // A search result is exactly where you cannot tell whether you already have the
                // track, which is what the markers are for (issue #33).
                com.alananasss.kittytune.ui.common.TrackRowSocialMarkers(track)
            }
        }
        if (durationText.isNotEmpty()) {
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        IconButton(onClick = { playerViewModel.showTrackOptions(track) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
    }
}

@Composable
private fun SearchArtistRow(user: User, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = if (user.avatarUrl.isDefaultAvatar()) null else user.avatarUrl,
            contentDescription = null,
            error = androidx.compose.ui.res.painterResource("drawable/ic_default_user_artwork_placeholder_round.xml"),
            fallback = androidx.compose.ui.res.painterResource("drawable/ic_default_user_artwork_placeholder_round.xml"),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.username ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = if (user.urn?.startsWith("spotify") == true) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            val followersCount = user.followersCount
            val followersText = when {
                followersCount >= 1_000_000 -> "${followersCount / 1_000_000}M followers"
                followersCount >= 1_000 -> "${followersCount / 1_000}K followers"
                followersCount > 0 -> "$followersCount followers"
                else -> str("lib_artists")
            }
            Text(
                text = followersText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPlaylistRow(playlist: Playlist, onRightClick: (() -> Unit)? = null, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .let { m ->
                if (onRightClick != null) {
                    m.onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Secondary),
                        onClick = onRightClick
                    )
                } else m
            }
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = playlist.fullResArtwork,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = playlist.user?.username ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (playlist.user?.verified == true) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                }
                val trackCount = playlist.trackCount ?: 0
                if (trackCount > 0) {
                    Text(
                        text = " · $trackCount ${str("search_filter_tracks").lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    }
}

/**
 * "Start mixing" — the button, what it is built from, and what happens when it is not that simple (issue #33).
 *
 * "Just click the start mixing button, which, according to your interests, gives out songs that you like (these
 * are not your favorite songs), you can also customize your mix, select a genre or artist's songs, for example,
 * in the Yeat style."
 *
 * Two controls, because there are two things somebody wants from this. The big one is *press and go* — no
 * choices, no dialog, it reads your listening and plays. The small one beside it opens the customisation, for
 * when you already know what you are in the mood for.
 *
 * ## Why the card names its own sources
 *
 * "Qu'est-ce que ça fait quand on fait start mix tout seul ? Ça se base sur quoi ?" A button that reads your
 * history and then says nothing about what it read is indistinguishable from a shuffle, so the card carries the
 * answer: the three artists it is leaning on, their faces, and how many more are behind them. Nobody should have
 * to ask a recommender what it is recommending from.
 *
 * Four states worth showing plainly rather than as a spinner and a silence:
 *
 *  - **Building.** It is five or six requests deep and takes a couple of seconds, so it says so.
 *  - **Done.** A hundred tracks went into the queue, from something: the card says what, because the queue is on
 *    the far side of the window and a press with no visible consequence reads as a press that failed.
 *  - **Nothing to go on.** A fresh install has no listening to profile. "Play something first" is the honest
 *    answer and it is better than an empty mix that looks like a bug.
 *  - **Found nothing.** Seeds existed and every expansion came back empty, which in practice means the network.
 */
private data class VibeStation(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val recipe: com.alananasss.kittytune.data.mix.MixEngine.Recipe
)

@Composable
private fun StartMixingCard(playerViewModel: PlayerViewModel) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<MixState>(MixState.Idle) }
    var showOptions by remember { mutableStateOf(false) }

    val basis by produceState<com.alananasss.kittytune.data.mix.MixEngine.Basis?>(initialValue = null) {
        value = com.alananasss.kittytune.data.mix.MixEngine.basis()
    }

    val topArtist = basis?.topArtists?.firstOrNull()
    val stations = remember(topArtist) {
        buildList {
            add(
                VibeStation(
                    id = "my_vibe",
                    title = str("mix_vibe_my_taste"),
                    subtitle = str("mix_vibe_my_taste_sub"),
                    icon = Icons.Rounded.AutoAwesome,
                    color = Color(0xFFB388FF),
                    recipe = com.alananasss.kittytune.data.mix.MixEngine.Recipe.MyTaste
                )
            )
            add(
                VibeStation(
                    id = "rock",
                    title = str("mix_vibe_rock"),
                    subtitle = str("mix_vibe_rock_sub"),
                    icon = Icons.Rounded.ElectricBolt,
                    color = Color(0xFF448AFF),
                    recipe = com.alananasss.kittytune.data.mix.MixEngine.Recipe.InGenre("rock")
                )
            )
            add(
                VibeStation(
                    id = "sad",
                    title = str("mix_vibe_sad"),
                    subtitle = str("mix_vibe_sad_sub"),
                    icon = Icons.Rounded.WaterDrop,
                    color = Color(0xFF00E5FF),
                    recipe = com.alananasss.kittytune.data.mix.MixEngine.Recipe.InGenre("sad")
                )
            )
            add(
                VibeStation(
                    id = "rap",
                    title = str("mix_vibe_rap"),
                    subtitle = str("mix_vibe_rap_sub"),
                    icon = Icons.Rounded.Mic,
                    color = Color(0xFF00E676),
                    recipe = com.alananasss.kittytune.data.mix.MixEngine.Recipe.InGenre("hiphop")
                )
            )
            add(
                VibeStation(
                    id = "dance",
                    title = str("mix_vibe_dance"),
                    subtitle = str("mix_vibe_dance_sub"),
                    icon = Icons.Rounded.MusicNote,
                    color = Color(0xFFFF9100),
                    recipe = com.alananasss.kittytune.data.mix.MixEngine.Recipe.InGenre("electronic")
                )
            )
            add(
                VibeStation(
                    id = "pop",
                    title = str("mix_vibe_pop"),
                    subtitle = str("mix_vibe_pop_sub"),
                    icon = Icons.Filled.Favorite,
                    color = Color(0xFFFF4081),
                    recipe = com.alananasss.kittytune.data.mix.MixEngine.Recipe.InGenre("pop")
                )
            )
            if (topArtist != null) {
                add(
                    VibeStation(
                        id = "artist",
                        title = str("mix_vibe_artist", topArtist.artistName),
                        subtitle = str("mix_title"),
                        icon = Icons.Rounded.Person,
                        color = Color(0xFFE040FB),
                        recipe = com.alananasss.kittytune.data.mix.MixEngine.Recipe.LikeArtist(topArtist.artistId, topArtist.artistName)
                    )
                )
            }
        }
    }

    var selectedStationIndex by remember { mutableStateOf(0) }
    val currentStation = stations.getOrElse(selectedStationIndex) { stations.first() }

    fun start(recipe: com.alananasss.kittytune.data.mix.MixEngine.Recipe) {
        if (state is MixState.Building) return
        state = MixState.Building
        scope.launch {
            val result = com.alananasss.kittytune.data.mix.MixEngine.mix(recipe)
            state = when (result) {
                is com.alananasss.kittytune.data.mix.MixEngine.Result.Mixed -> {
                    playerViewModel.playPlaylist(result.tracks, 0)
                    MixState.Done(result.tracks.size, result.describedBy.takeIf { it.isNotBlank() })
                }
                com.alananasss.kittytune.data.mix.MixEngine.Result.NotEnoughHistory ->
                    MixState.Empty(str("mix_needs_history"))
                is com.alananasss.kittytune.data.mix.MixEngine.Result.NothingFound -> MixState.Empty(
                    when (result.stage) {
                        com.alananasss.kittytune.data.mix.MixEngine.Result.Stage.NO_SEEDS ->
                            str("mix_no_seeds")
                        com.alananasss.kittytune.data.mix.MixEngine.Result.Stage.NO_CANDIDATES ->
                            str("mix_nothing_found")
                        com.alananasss.kittytune.data.mix.MixEngine.Result.Stage.ALL_FILTERED ->
                            str("mix_all_known")
                    }
                )
            }
        }
    }

    if (showOptions) {
        MixOptionsDialog(
            onDismiss = { showOptions = false },
            onPick = { recipe ->
                showOptions = false
                start(recipe)
            },
        )
    }

    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Column: Header, Subtitle, [▶ Enable mix ▶] Button & Tune Icon
            Column(
                modifier = Modifier.weight(1.15f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = str("mix_title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when (val current = state) {
                        is MixState.Empty -> current.message
                        is MixState.Done ->
                            if (current.from == null) str("mix_track_count", current.count)
                            else str("mix_done", current.count, current.from)
                        MixState.Building -> str("mix_building")
                        MixState.Idle -> str("mix_card_subtitle")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state) {
                        is MixState.Done -> accent
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { start(currentStation.recipe) },
                        shapes = ButtonDefaults.shapes(),
                        enabled = state !is MixState.Building,
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (state is MixState.Building) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(str("mix_enable"), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }

                    Tip(str("mix_customise")) {
                        FilledTonalIconButton(
                            onClick = { showOptions = true },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = str("mix_customise"))
                        }
                    }
                }
            }

            // Right Column: Vertical Vibe Stations Selector
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stations.forEachIndexed { index, station ->
                        val isSelected = index == selectedStationIndex
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) station.color.copy(alpha = 0.16f) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedStationIndex = index }
                                .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(java.awt.Cursor.HAND_CURSOR)))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(station.color.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        station.icon,
                                        contentDescription = null,
                                        tint = station.color,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = station.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) station.color else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = station.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(20.dp)
                                            .clip(CircleShape)
                                            .background(station.color)
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

/**
 * The artists the mix is leaning on: three faces, overlapped, and the names.
 *
 * Overlapped rather than spaced because three separated avatars read as three things to press, while a stack
 * reads as one fact about the mix — which is what it is. The ring is the card's own colour, so a photo with a
 * dark edge still separates from the one behind it.
 */
@Composable
private fun MixBasisRow(basis: com.alananasss.kittytune.data.mix.MixEngine.Basis) {
    val ring = MaterialTheme.colorScheme.surfaceContainer
    val names = basis.topArtists.joinToString(", ") { it.artistName }
    val rest = (basis.artistCount - basis.topArtists.size).coerceAtLeast(0)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            basis.topArtists.forEachIndexed { index, artist ->
                Box(
                    modifier = Modifier
                        .padding(start = (index * 15).dp)
                        .size(24.dp)
                        .border(2.dp, ring, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (artist.artworkUrl.isNullOrBlank()) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                    } else {
                        AsyncImage(
                            model = artist.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = str("mix_basis", names).let {
                if (rest > 0) "$it · ${str("mix_basis_more", rest)}" else it
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What the card is doing. Not an enum, because two of the four carry something to say. */
private sealed interface MixState {
    data object Idle : MixState
    data object Building : MixState

    /** A mix went into the queue: how many tracks, and what it was built around when that can be named. */
    data class Done(val count: Int, val from: String?) : MixState
    data class Empty(val message: String) : MixState
}

/**
 * Choosing an artist or a genre to build around.
 *
 * The artist field is free text rather than a picker on purpose: "in the Yeat style" is something you type, and
 * the name you have in mind is very often not somebody in your own history — which is the whole point of asking.
 * It is resolved by search when the mix is built.
 *
 * ## Why the genres are searched rather than listed
 *
 * "On peut chercher des genres dans la recherche ?" You could not. The dialog showed the first twelve of the
 * app's fifty-odd categories in alphabetical order — Afro through Celtic, a list with no relationship to anybody's
 * taste and no way at all to reach the other forty-five. So there is a field, and it does two things: it filters
 * every category the app knows, and anything typed that matches none of them is offered as a tag in its own
 * right. SoundCloud's tags are not a fixed vocabulary, "sigilkore" is a real thing to want a mix of, and the
 * genre expansion takes whatever word it is given (issue #33).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixOptionsDialog(
    onDismiss: () -> Unit,
    onPick: (com.alananasss.kittytune.data.mix.MixEngine.Recipe) -> Unit,
) {
    var artist by remember { mutableStateOf("") }
    var genreQuery by remember { mutableStateOf("") }

    val all = remember { com.alananasss.kittytune.data.GenreData.getGenres() }
    val needle = genreQuery.trim()
    val matches = remember(needle, all) {
        if (needle.isEmpty()) all
        else all.filter { it.title.contains(needle, true) || it.query.contains(needle, true) }
    }
    // Typed something the app has no chip for? It is still a tag SoundCloud may well know, so it is offered as one.
    val custom = needle.takeIf {
        it.isNotEmpty() && all.none { known -> known.title.equals(it, true) || known.query.equals(it, true) }
    }

    fun startArtist() {
        if (artist.isNotBlank()) {
            onPick(
                com.alananasss.kittytune.data.mix.MixEngine.Recipe.LikeArtist(
                    artistId = null,
                    artistName = artist.trim(),
                )
            )
        }
    }

    com.alananasss.kittytune.core.BackHandler(onBack = onDismiss)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.width(520.dp).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            str("mix_customise"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            str("mix_customise_sub"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The only way out that the dialog needs: a "Cancel" button at the bottom of a panel whose
                    // every other control starts something was one press too many and read as the primary action.
                    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                    }
                }

                Spacer(Modifier.height(22.dp))
                MixSectionLabel(Icons.Rounded.Person, str("mix_in_the_style_of"))
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(str("mix_artist_hint")) },
                    // Where the "Start mixing" button used to sit beside the field, at half the width of the one
                    // on the card and doing the same thing. Enter does it too, which is what a text field implies.
                    trailingIcon = {
                        if (artist.isNotBlank()) {
                            IconButton(onClick = { startArtist() }, shapes = IconButtonDefaults.shapes()) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = str("mix_start"),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackTextInput()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter)
                            ) {
                                startArtist()
                                true
                            } else false
                        },
                )

                Spacer(Modifier.height(24.dp))
                MixSectionLabel(Icons.Rounded.MusicNote, str("mix_by_genre"))
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = genreQuery,
                    onValueChange = { genreQuery = it },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text(str("mix_genre_search_hint")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (genreQuery.isNotEmpty()) {
                            IconButton(onClick = { genreQuery = "" }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackTextInput()
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter)
                            ) {
                                // Enter takes the tag as typed, or the first chip still standing after filtering.
                                val target = custom ?: matches.firstOrNull()?.query
                                if (target != null) {
                                    onPick(com.alananasss.kittytune.data.mix.MixEngine.Recipe.InGenre(target))
                                }
                                true
                            } else false
                        },
                )

                Spacer(Modifier.height(14.dp))
                // Every category, scrolled, rather than the twelve that fit: the list is fifty-odd long and the
                // field above it is what makes that a feature instead of a wall.
                Box(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        custom?.let { tag ->
                            AssistChip(
                                onClick = {
                                    onPick(com.alananasss.kittytune.data.mix.MixEngine.Recipe.InGenre(tag))
                                },
                                label = { Text(str("mix_genre_custom", tag)) },
                                // A hash, not the sparkles the start controls use: this chip's whole point is that
                                // the word is being taken as a raw tag rather than as one of the app's categories.
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Tag,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    labelColor = MaterialTheme.colorScheme.primary,
                                    leadingIconContentColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                        matches.forEach { genre ->
                            AssistChip(
                                onClick = {
                                    onPick(
                                        com.alananasss.kittytune.data.mix.MixEngine.Recipe.InGenre(genre.query)
                                    )
                                },
                                label = { Text(genre.title) },
                                leadingIcon = {
                                    Icon(genre.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A heading inside the customisation dialog: an icon, a label, and a rule running out to the edge. */
@Composable
private fun MixSectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
    }
}

/**
 * The week's listening, on the home page (issue #33).
 *
 * Statistics were already being recorded and there was a screen for them, but nothing navigated to
 * it, so they were invisible. A card is the form that was asked for: the three numbers worth
 * knowing at a glance, the artist behind them, and a way through to everything else.
 *
 * Absent entirely until there is something to show. A card reading zero minutes on a fresh install
 * is noise, and it is the state every new listener starts in.
 */

@Composable
private fun ListeningStatsCard(navController: NavController) {
    val weekAgo = remember { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 }
    val summary by produceState<HomeStatsSummary?>(initialValue = null, key1 = weekAgo) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val repo = com.alananasss.kittytune.data.ListeningStatsRepository
                HomeStatsSummary(
                    listenedMs = repo.getTotalListenTime(weekAgo),
                    uniqueTracks = repo.getUniqueTracks(weekAgo),
                    uniqueArtists = repo.getUniqueArtists(weekAgo),
                    topArtist = repo.getTopArtists(weekAgo, limit = 1).firstOrNull(),
                )
            }.getOrNull()
        }
    }

    val stats = summary ?: return
    if (stats.listenedMs <= 0L) return

    Surface(
        onClick = { navController.navigate("listening_stats") },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = str("listening_stats_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = str("listening_stats_period_week"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatFigure(
                    value = listenedLabel(stats.listenedMs),
                    label = str("listening_stats_time_listened"),
                    modifier = Modifier.weight(1f),
                )
                StatFigure(
                    value = stats.uniqueTracks.toString(),
                    label = str("listening_stats_unique_tracks"),
                    modifier = Modifier.weight(1f),
                )
                StatFigure(
                    value = stats.uniqueArtists.toString(),
                    label = str("listening_stats_unique_artists"),
                    modifier = Modifier.weight(1f),
                )
            }

            stats.topArtist?.let { artist ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = artist.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = str("listening_stats_top_artists"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = artist.artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** What the card needs, fetched in one pass so the card appears whole rather than in pieces. */
private data class HomeStatsSummary(
    val listenedMs: Long,
    val uniqueTracks: Int,
    val uniqueArtists: Int,
    val topArtist: com.alananasss.kittytune.data.local.TopArtistResult?,
)

@Composable
private fun StatFigure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Listening time at a glance: hours once there are hours, minutes below that.
 *
 * Never seconds. A card is read in passing and "4 h 12" tells you what you wanted; the exact figure
 * is on the screen behind it.
 */
private fun listenedLabel(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} h ${minutes}" else "$totalMinutes min"
}

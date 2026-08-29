@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    val pageSize = 6
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

        // Quick tiles: recently played contexts (2 rows x 3 cols with pagination)
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
                                            else -> entry.id // likes, downloads, station:, profile:, yt_radio:
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

        // Listening statistics, as a card on the home page rather than an entry in a menu: it was
        // asked for as something you come across, not something you go looking for (issue #33).
        item { ListeningStatsCard(navController) }

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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
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

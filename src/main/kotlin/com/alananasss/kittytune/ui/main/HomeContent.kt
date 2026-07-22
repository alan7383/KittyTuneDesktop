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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
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
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.home.HomeViewModel
import com.alananasss.kittytune.ui.home.SearchFilter
import com.alananasss.kittytune.ui.home.SearchSource
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
                in 5..11 -> when (lang) { "fr" -> "Bonjour"; "hu" -> "Jó reggelt"; else -> "Good morning" }
                in 12..17 -> when (lang) { "fr" -> "Bon après-midi"; "hu" -> "Jó napot"; else -> "Good afternoon" }
                else -> when (lang) { "fr" -> "Bonsoir"; "hu" -> "Jó estét"; else -> "Good evening" }
            }
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // Quick tiles: recently played (2 rows x 3 cols like the reference)
        if (history.isNotEmpty()) {
            item {
                val quick = history.take(6)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quick.chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { entry ->
                                QuickTile(
                                    title = entry.title,
                                    imageUrl = entry.imageUrl,
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
                                    val dest = if (item.kind == "system-playlist" && item.urn != null) {
                                        "system_playlist:${item.urn}"
                                    } else {
                                        item.id.toString()
                                    }
                                    playerViewModel.navigateToPlaylistId = dest
                                }
                                is User -> MediaCard(
                                    title = item.username ?: "",
                                    subtitle = str("lib_artists"),
                                    artworkUrl = item.avatarUrl,
                                    round = true,
                                ) { item.id?.let { id -> playerViewModel.navigateToPlaylistId = "profile:$id" } }
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
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
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
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(148.dp)
                    .clip(if (round) CircleShape else RoundedCornerShape(8.dp)),
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Source chips (SoundCloud / YouTube)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceChip(
                    label = "SoundCloud",
                    selected = vm.activeSearchSource == SearchSource.SOUNDCLOUD,
                    onClick = { vm.onSearchSourceChanged(SearchSource.SOUNDCLOUD) }
                )
                SourceChip(
                    label = "YouTube",
                    selected = vm.activeSearchSource == SearchSource.YOUTUBE,
                    onClick = { vm.onSearchSourceChanged(SearchSource.YOUTUBE) }
                )
            }

            // Filter chips (only for SoundCloud)
            if (vm.activeSearchSource == SearchSource.SOUNDCLOUD) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val filters = listOf(
                        SearchFilter.ALL to str("search_filter_all"),
                        SearchFilter.TRACKS to str("search_filter_tracks"),
                        SearchFilter.ARTISTS to str("lib_artists"),
                        SearchFilter.PLAYLISTS to str("lib_playlists"),
                    )
                    filters.forEach { (filter, label) ->
                        val isSelected = vm.activeFilter == filter
                        androidx.compose.material3.Button(
                            onClick = { vm.onFilterChanged(filter) },
                            shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
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
            vm.searchResultsYoutube.isEmpty()
        ) {
            // Initial loading — centered spinner
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator()
            }
        } else {
            // Actual results
            if (vm.activeSearchSource == SearchSource.YOUTUBE) {
                YoutubeResults(vm, playerViewModel, listState)
            } else {
                SoundCloudResults(vm, playerViewModel, navController, listState)
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
                                    user.id?.let { playerViewModel.navigateToPlaylistId = "profile:$it" }
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
                                    playerViewModel.navigateToPlaylistId = playlist.id.toString()
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
                    user.id?.let { playerViewModel.navigateToPlaylistId = "profile:$it" }
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
            Text(
                text = track.user?.username ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            model = user.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
            Row {
                Text(
                    text = playlist.user?.username ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

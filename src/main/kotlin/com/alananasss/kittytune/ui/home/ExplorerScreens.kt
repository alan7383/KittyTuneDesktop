@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.home

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.onClick
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator

import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.GenreData
import com.alananasss.kittytune.data.OfficialPlaylistsData
import com.alananasss.kittytune.data.SearchCategory
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.SquareCardShimmer
import com.alananasss.kittytune.ui.common.TrackListItemShimmer
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

/* ------------------------------------------------------------------ */
/* Explorer (genres/moods grid)                                        */
/* ------------------------------------------------------------------ */

@Composable
fun GenresScreen(
    onNavigate: (String) -> Unit,
) {
    val moods = remember { GenreData.getMoods() }
    val genres = remember { GenreData.getGenres() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 200.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = str("explorer_title"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        // Desktop version of the Android Home "Explorer" shortcut row.
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExplorerShortcutCard(
                    title = str("explorer_new_releases"),
                    icon = Icons.Rounded.NewReleases,
                    baseColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                ) { onNavigate("new_releases") }
                ExplorerShortcutCard(
                    title = str("explorer_charts"),
                    icon = Icons.Rounded.TrendingUp,
                    baseColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                ) { onNavigate("charts") }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = str("search_section_moods"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )
        }
        items(moods) { category ->
            SearchCategoryCard(category) {
                onNavigate("genre_playlists/${URLEncoder.encode(category.title, "UTF-8")}/${URLEncoder.encode(category.query, "UTF-8")}")
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = str("search_section_genres"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
        }
        items(genres) { category ->
            SearchCategoryCard(category) {
                onNavigate("genre_detail/${URLEncoder.encode(category.title, "UTF-8")}/${URLEncoder.encode(category.query, "UTF-8")}")
            }
        }
    }
}

@Composable
fun SearchCategoryCard(
    category: SearchCategory,
    onClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        modifier = Modifier.fillMaxWidth().height(110.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(100.dp)
                    .offset(x = 20.dp, y = 20.dp)
                    .graphicsLayer { rotationZ = -10f; alpha = 0.5f }
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier.size(32.dp).background(color = contentColor.copy(alpha = 0.2f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp), tint = contentColor)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Genre detail                                                        */
/* ------------------------------------------------------------------ */

class GenreDetailViewModel(application: Application) : ViewModel() {
    private val api = RetrofitClient.create()
    private val gson = Gson()

    var isLoading by mutableStateOf(true)
    var genreTitle by mutableStateOf("")

    val popularTracks = mutableStateListOf<Track>()
    val officialPlaylists = mutableStateListOf<Playlist>()
    val communityPlaylists = mutableStateListOf<Playlist>()
    val albums = mutableStateListOf<Playlist>()
    var selectedSourceIndex by mutableStateOf(0)
    private var currentGenreQuery by mutableStateOf("")

    init {
        autodetectCountry()
    }

    private fun autodetectCountry() {
        val deviceCountryCode = Locale.getDefault().country.lowercase(Locale.ROOT)
        val matchedIndex = OfficialPlaylistsData.sources.indexOfFirst {
            val sourceCode = it.soundCloudUsername.substringAfter("sc-playlists-").lowercase(Locale.ROOT)
            sourceCode == deviceCountryCode || (deviceCountryCode == "gb" && sourceCode == "uk")
        }
        if (matchedIndex != -1) selectedSourceIndex = matchedIndex
    }

    fun loadData(name: String, query: String) {
        genreTitle = name
        currentGenreQuery = query
        viewModelScope.launch {
            isLoading = true
            popularTracks.clear()
            officialPlaylists.clear()
            communityPlaylists.clear()
            albums.clear()
            try {
                coroutineScope {
                    val popularDef = async { api.searchTracks(query = query, limit = 50).collection }
                    val communityDef = async { api.searchPlaylists(query = query, limit = 20).collection }
                    val albumsDef = async { api.searchAlbums(query = query, limit = 20).collection }

                    popularTracks.addAll(popularDef.await())

                    val realPlaylists = communityDef.await().filter { !it.isAlbum }.distinctBy { it.id }.take(10)
                    val realAlbums = albumsDef.await().filter { it.isAlbum }.distinctBy { it.id }.take(10)
                    communityPlaylists.addAll(realPlaylists)
                    albums.addAll(realAlbums)

                    loadOfficialPlaylists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadOfficialPlaylists() {
        if (currentGenreQuery.isBlank()) return
        viewModelScope.launch {
            if (officialPlaylists.isEmpty()) isLoading = true
            officialPlaylists.clear()
            try {
                val source = OfficialPlaylistsData.sources[selectedSourceIndex]
                val userUrl = "https://soundcloud.com/${source.soundCloudUsername}"
                val resolvedUserJson = api.resolveUrl(userUrl)
                val user = gson.fromJson(resolvedUserJson, User::class.java)
                if (user.id != 0L) {
                    val userPlaylistsResponse = api.getUserCreatedPlaylists(userId = user.id, limit = 200)
                    officialPlaylists.addAll(userPlaylistsResponse.collection.filter { playlist ->
                        playlist.title?.contains(currentGenreQuery, ignoreCase = true) == true
                    })
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
}

@Composable
fun GenreDetailScreen(
    genreName: String,
    genreQuery: String,
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
) {
    val viewModel: GenreDetailViewModel = viewModel(key = "genre_$genreQuery") { GenreDetailViewModel(AppInstance.application) }
    var showCountrySelector by remember { mutableStateOf(false) }

    LaunchedEffect(genreName, genreQuery) {
        viewModel.loadData(genreName, genreQuery)
    }

    if (showCountrySelector) {
        Dialog(onDismissRequest = { showCountrySelector = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(380.dp)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(OfficialPlaylistsData.sources) { index, source ->
                        CountrySelectionCard(
                            countryName = source.countryName,
                            flagEmoji = source.flagEmoji,
                            isSelected = index == viewModel.selectedSourceIndex,
                            onClick = {
                                viewModel.selectedSourceIndex = index
                                viewModel.loadOfficialPlaylists()
                                showCountrySelector = false
                            }
                        )
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                viewModel.genreTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (viewModel.isLoading && viewModel.popularTracks.isEmpty()) {
            LazyColumn {
                items(10) { TrackListItemShimmer() }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (viewModel.popularTracks.isNotEmpty()) {
                    item { SectionTitle(str("profile_tab_popular")) }
                    item {
                        val pages = viewModel.popularTracks.chunked(5)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(pages) { pageIndex, trackColumn ->
                                Column(
                                    modifier = Modifier.width(320.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    trackColumn.forEachIndexed { itemIndexInColumn, track ->
                                        val absoluteIndex = pageIndex * 5 + itemIndexInColumn
                                        PopularTrackListItem(
                                            track = track,
                                            currentlyPlayingTrack = playerViewModel.currentTrack,
                                            onClick = { playerViewModel.playPlaylist(viewModel.popularTracks, absoluteIndex) },
                                            onOptionClick = { playerViewModel.showTrackOptions(track) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (viewModel.officialPlaylists.isNotEmpty()) {
                    item {
                        Column {
                            SectionTitle(str("genre_official_playlists"))
                            Surface(
                                onClick = { showCountrySelector = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(OfficialPlaylistsData.sources[viewModel.selectedSourceIndex].flagEmoji)
                                    Spacer(Modifier.width(8.dp))
                                    Text(OfficialPlaylistsData.sources[viewModel.selectedSourceIndex].countryName, style = MaterialTheme.typography.labelLarge)
                                    Icon(Icons.Rounded.KeyboardArrowDown, null)
                                }
                            }
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(viewModel.officialPlaylists) { playlist ->
                                SquareCard(playlist) { onNavigate("playlist_detail/${playlist.id}") }
                            }
                        }
                    }
                }

                if (viewModel.communityPlaylists.isNotEmpty()) {
                    item {
                        SectionTitle(
                            str("genre_community_playlists"),
                            showMore = true,
                            onMoreClick = {
                                onNavigate("genre_playlists/${URLEncoder.encode(genreName, "UTF-8")}/${URLEncoder.encode(genreQuery, "UTF-8")}")
                            }
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(viewModel.communityPlaylists) { playlist ->
                                SquareCard(playlist) { onNavigate("playlist_detail/${playlist.id}") }
                            }
                        }
                    }
                }

                if (viewModel.albums.isNotEmpty()) {
                    item { SectionTitle(str("profile_tab_albums")) }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(viewModel.albums) { album ->
                                SquareCard(album) { onNavigate("playlist_detail/${album.id}") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, showMore: Boolean = false, onMoreClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (showMore && onMoreClick != null) {
            TextButton(onClick = onMoreClick) { Text(str("btn_see_all")) }
        }
    }
}

@Composable
fun SquareCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        AsyncImage(
            model = playlist.fullResArtwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(148.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title ?: "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playlist.user?.username ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PopularTrackListItem(
    track: Track,
    currentlyPlayingTrack: Track?,
    onClick: () -> Unit,
    onOptionClick: () -> Unit
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    androidx.compose.material3.TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                onClick = onOptionClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                if (isCurrent) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: str("untitled_track"),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = titleColor
                )
                Text(
                    text = "${track.user?.username ?: str("unknown_artist")} • ${formatPlayCount(track.playbackCount)} ${str("playback_count_formatted")}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOptionClick) {
                Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatPlayCount(count: Int): String {
    if (count < 1000) return count.toString()
    val k = count / 1000.0
    val m = count / 1000000.0
    return when {
        m >= 1.0 -> String.format(Locale.getDefault(), "%.1f M", m)
        k >= 1.0 -> String.format(Locale.getDefault(), "%.1f k", k)
        else -> count.toString()
    }
}

@Composable
private fun CountrySelectionCard(
    countryName: String,
    flagEmoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = flagEmoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = countryName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            }
            if (isSelected) Icon(Icons.Rounded.Check, null, tint = contentColor)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Genre playlists (paginated grid)                                    */
/* ------------------------------------------------------------------ */

class GenrePlaylistsViewModel(application: Application) : ViewModel() {
    private val api = RetrofitClient.create()

    val playlists = mutableStateListOf<Playlist>()
    var isLoading by mutableStateOf(true)
    var title by mutableStateOf("")

    private var nextHref: String? = null
    var isLoadingMore by mutableStateOf(false)
        private set

    fun loadGenre(displayTitle: String, query: String) {
        title = displayTitle
        isLoading = true
        playlists.clear()
        nextHref = null
        viewModelScope.launch {
            try {
                val response = api.searchPlaylists(query, limit = 50)
                playlists.addAll(response.collection)
                nextHref = response.next_href
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || nextHref == null) return
        viewModelScope.launch {
            isLoadingMore = true
            try {
                val response = api.getSearchPlaylistsNextPage(nextHref!!)
                playlists.addAll(response.collection)
                nextHref = response.next_href
            } catch (e: Exception) {
                e.printStackTrace()
                nextHref = null
            } finally {
                isLoadingMore = false
            }
        }
    }
}
@Composable
fun GenrePlaylistsScreen(
    genreTitle: String,
    query: String,
    onBackClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
) {
    val viewModel: GenrePlaylistsViewModel = viewModel(key = "genre_playlists_$query") { GenrePlaylistsViewModel(AppInstance.application) }
    val listState = rememberLazyGridState()

    LaunchedEffect(query) {
        viewModel.loadGenre(genreTitle, query)
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 10
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !viewModel.isLoadingMore) {
            viewModel.loadMore()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                viewModel.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (viewModel.isLoading) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(10) { SquareCardShimmer() }
            }
        } else if (viewModel.playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(str("no_results"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                state = listState,
                columns = GridCells.Adaptive(minSize = 180.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(viewModel.playlists) { playlist ->
                    CinematicPlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
                }
                if (viewModel.isLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularWavyProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinematicPlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = playlist.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    text = playlist.title ?: str("untitled_track"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (playlist.user?.avatarUrl != null) {
                        AsyncImage(
                            model = playlist.user?.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = playlist.user?.username ?: str("unknown_artist"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        text = str("playlist_num_tracks", playlist.trackCount ?: 0),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Desktop version of the Android Home `ExplorerButton` â€” a wide tonal card
 * with an oversized ghost icon, used as Charts / New releases shortcuts at the
 * top of the Explorer grid.
 */
@Composable
private fun ExplorerShortcutCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    baseColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = baseColor.copy(alpha = 0.16f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.height(96.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = baseColor.copy(alpha = 0.35f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(72.dp)
                    .offset(x = 12.dp)
                    .graphicsLayer { rotationZ = -10f }
            )
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = baseColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


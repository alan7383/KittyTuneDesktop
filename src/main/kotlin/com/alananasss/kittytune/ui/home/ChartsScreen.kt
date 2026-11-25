package com.alananasss.kittytune.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.common.TrackArtistClick
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.common.rememberFilteredTracks
import com.alananasss.kittytune.ui.player.ArtworkPalette
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.ArtistAvatar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop replacement for the Android Palette-based dominant color extraction
 * (Coil bitmap + androidx.palette) — same visual role, backed by [ArtworkPalette].
 */
@Composable
fun rememberDominantColor(url: String?, defaultColor: Color = MaterialTheme.colorScheme.surface): State<Color> {
    val color = remember(url) { mutableStateOf(defaultColor) }

    LaunchedEffect(url) {
        if (url != null) {
            val extracted = withContext(Dispatchers.IO) {
                ArtworkPalette.dominantColorCached(url, preferLight = false)
            }
            if (extracted != null) color.value = extracted
        }
    }
    return color
}

@Composable
fun ChartsScreen(
    onBackClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: ChartsViewModel = viewModel { ChartsViewModel(AppInstance.application) }
) {
    val entries = viewModel.chartEntries
    val allTracks = remember(entries) { entries.map { it.track } }
    val visibleTracks = rememberFilteredTracks(allTracks)
    val chartEntries = remember(entries, visibleTracks) {
        val visibleIds = visibleTracks.map { it.id }.toSet()
        entries.filter { it.track.id in visibleIds }
    }
    ChartsContent(
        kind = viewModel.chartKind,
        genre = viewModel.chartGenre,
        country = viewModel.chartCountry,
        entries = chartEntries,
        isLoading = viewModel.isChartLoading,
        currentTrack = playerViewModel.uiCurrentTrack,
        onKindChange = { viewModel.loadChart(it, viewModel.chartGenre) },
        onGenreChange = { viewModel.loadChart(viewModel.chartKind, it) },
        onCountryChange = viewModel::selectChartCountry,
        onRetry = { viewModel.loadChart(viewModel.chartKind, viewModel.chartGenre) },
        onPlayFrom = { index ->
            playerViewModel.playPlaylist(
                tracks = visibleTracks,
                startIndex = index,
                context = PlaybackContext(displayText = str(viewModel.chartKind.labelKey()), navigationId = "charts"),
            )
        },
        onArtistClick = playerViewModel::navigateToTrackArtist,
        onBackClick = onBackClick,
    )
}

@Composable
internal fun ChartsContent(
    kind: ChartKind,
    genre: ChartGenre,
    country: ChartCountry,
    entries: List<ChartEntry>,
    isLoading: Boolean,
    currentTrack: Track?,
    onKindChange: (ChartKind) -> Unit,
    onGenreChange: (ChartGenre) -> Unit,
    onCountryChange: (ChartCountry) -> Unit,
    onRetry: () -> Unit,
    onPlayFrom: (Int) -> Unit,
    onArtistClick: TrackArtistClick,
    onBackClick: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(kind, genre, country) { listState.scrollToItem(0) }
    val chartWidth = Modifier.widthIn(max = 920.dp).fillMaxWidth()
    val colors = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth().background(colors.surfaceContainerLow).padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = chartWidth.heightIn(min = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = str("btn_back"))
                    }
                    Text(
                        text = str("home_charts"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SongChart(
                    kind = kind,
                    genre = genre,
                    genres = ChartsViewModel.chartGenres,
                    onKindChange = onKindChange,
                    onGenreChange = onGenreChange,
                    country = country,
                    onCountryChange = onCountryChange,
                    modifier = chartWidth.padding(bottom = 10.dp),
                )
            }
        },
        containerColor = colors.surfaceContainerLow,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                isLoading -> item { ChartLoadingRows(chartWidth) }
                entries.isEmpty() -> item { EmptyChart(onRetry, chartWidth) }
                else -> {
                    val firstRow = if (entries.first().rank == 1) 1 else 0
                    if (firstRow > 0) {
                        item(key = "chart-lead") {
                            ChartLeadTrack(
                                entry = entries.first(),
                                currentTrack = currentTrack,
                                onClick = { onPlayFrom(0) },
                                onArtistClick = onArtistClick,
                                modifier = chartWidth.padding(bottom = 9.dp),
                            )
                        }
                    }
                    val rowCount = entries.size - firstRow
                    items(count = rowCount, key = { entries[it + firstRow].rank }) { row ->
                        val index = row + firstRow
                        val entry = entries[index]
                        ChartTrackRow(
                            track = entry.track,
                            rank = entry.rank,
                            currentlyPlayingTrack = currentTrack,
                            onClick = { onPlayFrom(index) },
                            onArtistClick = onArtistClick,
                            modifier = chartWidth,
                            shape = getSettingsShape(rowCount, row),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistRankRow(
    ranking: ArtistRanking,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // rank number
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Text(
                text = "${ranking.rank}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            )
        }

        Spacer(Modifier.width(12.dp))

        ArtistAvatar(
            avatarUrl = ranking.user.avatarUrl,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
        )

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ranking.user.username ?: str("unknown_artist"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatCompactNumber(ranking.user.followersCount.toLong()) + " " + str("profile_followers").lowercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onMenuClick) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = str("btn_options"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ArtistMenuOption(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ChartsCountryCard(
    countryName: String,
    flagEmoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer,
        label = "bgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "textColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "borderColor"
    )

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
                Text(
                    text = countryName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = str("desc_selected"),
                    tint = contentColor
                )
            }
        }
    }
}

@Composable
fun ChartPlaylistCard(
    playlist: com.alananasss.kittytune.domain.Playlist,
    onClick: () -> Unit
) {
    val dominantColor by rememberDominantColor(url = playlist.fullResArtwork)

    val animatedColor by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(500),
        label = "dominantColor"
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .width(160.dp)
            .wrapContentHeight()
    ) {
        Column {
            Box(modifier = Modifier.size(160.dp)) {
                AsyncImage(
                    model = playlist.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    animatedColor.copy(alpha = 0.1f),
                                    animatedColor.copy(alpha = 0.4f)
                                )
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = playlist.title?.uppercase() ?: str("home_charts").uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                val subtitle = when {
                    playlist.trackCount != null && playlist.trackCount > 0 -> str("playlist_num_tracks", playlist.trackCount)
                    !playlist.user?.username.isNullOrBlank() -> playlist.user.username
                    else -> str("lib_playlists")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// helper to format numbers nicely (1.2M, 14k)
fun formatCompactNumber(count: Long): String {
    if (count < 1000) return count.toString()
    val k = count / 1000.0
    val m = count / 1000000.0
    return when {
        m >= 1.0 -> String.format(Locale.US, "%.1fM", m)
        k >= 1.0 -> String.format(Locale.US, "%.1fk", k)
        else -> count.toString()
    }
}

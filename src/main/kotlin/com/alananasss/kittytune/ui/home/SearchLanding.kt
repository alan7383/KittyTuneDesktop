package com.alananasss.kittytune.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.common.ScrollableLazyRow
import com.alananasss.kittytune.ui.common.pressScale
import com.alananasss.kittytune.ui.common.rememberDefaultAvatarPainter
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * What the search screen shows while the field is empty.
 *
 * This used to be two chip walls — "Moods & moments" and "Genres" — under a row of personalised
 * tags. Fifty-five genre chips is a list of every kind of music, said identically to every listener,
 * and it pushed everything worth reading off the screen. What is here now is either about this
 * listener (what they searched for, what the artists they like have published) or is a chart, which
 * is the one shelf that is worth showing someone who has not searched for anything yet.
 *
 * The order is deliberate: what you just did, then what is big now, then what your artists did, then
 * the two doors into the full screens.
 */
@Composable
fun SearchLanding(
    vm: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onOpenCharts: () -> Unit,
    onOpenNewReleases: () -> Unit,
    onOpenTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            RecentSearchesSection(
                searches = vm.recentSearches,
                onRun = { vm.runRecentSearch(it) },
                onForget = { vm.forgetSearch(it) },
                onClearAll = { vm.clearRecentSearches() },
            )
        }

        item {
            ChartPreviewSection(
                kind = vm.chartPreviewKind,
                entries = vm.chartPreview,
                isLoading = vm.isChartPreviewLoading,
                currentTrack = playerViewModel.currentTrack,
                onKindChange = { vm.loadChartPreview(it) },
                onPlayFrom = { index ->
                    playerViewModel.playPlaylist(
                        tracks = vm.chartPreview.map { it.track },
                        startIndex = index,
                        context = PlaybackContext(
                            displayText = str(
                                if (vm.chartPreviewKind == ChartKind.TOP) "chart_kind_top"
                                else "chart_kind_trending"
                            ),
                            navigationId = "charts",
                        ),
                    )
                },
                onArtistClick = { playerViewModel.navigateToTrackArtist(it) },
                onSeeAll = onOpenCharts,
            )
        }

        if (vm.likedArtistUpdates.isNotEmpty()) {
            item {
                LandingSectionTitle(str("home_from_your_artists"), str("home_from_your_artists_sub"))
            }
            item {
                ScrollableLazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    fadeColor = MaterialTheme.colorScheme.surface,
                ) {
                    items(vm.likedArtistUpdates.size) { index ->
                        val track = vm.likedArtistUpdates[index]
                        val isCurrent = playerViewModel.currentTrack?.id == track.id
                        LandingTrackCard(
                            track = track,
                            isCurrent = isCurrent,
                            onClick = {
                                playerViewModel.playPlaylist(
                                    tracks = vm.likedArtistUpdates.toList(),
                                    startIndex = index,
                                    context = PlaybackContext(
                                        displayText = str("home_from_your_artists"),
                                        navigationId = "home",
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LandingDoorCard(
                    title = str("explorer_charts"),
                    icon = Icons.Rounded.TrendingUp,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenCharts,
                )
                LandingDoorCard(
                    title = str("explorer_new_releases"),
                    icon = Icons.Rounded.NewReleases,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenNewReleases,
                )
            }
        }

        if (vm.personalizedCategories.isNotEmpty()) {
            item { LandingSectionTitle(str("search_section_personalized"), null) }
            item {
                // A plain scrolling row rather than a wrapping one: the tags are derived from the
                // liked list and there can be ten of them, and FlowRow made the page jump about
                // while they loaded in.
                ScrollableLazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    fadeColor = MaterialTheme.colorScheme.surface,
                ) {
                    items(vm.personalizedCategories.size) { index ->
                        val cat = vm.personalizedCategories[index]
                        Surface(
                            onClick = { onOpenTag(cat.query) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Text(
                                text = cat.title,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The searches already run.
 *
 * Collapsed to [COLLAPSED] with a "see more", the way a long list of anything should be: a listener
 * opening search wants their last one or two, and the other eighteen are a scroll away rather than
 * the whole screen. Each row deletes on its own so a single stale query does not mean clearing
 * everything.
 */
@Composable
private fun RecentSearchesSection(
    searches: List<String>,
    onRun: (String) -> Unit,
    onForget: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    if (searches.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = if (expanded) searches else searches.take(COLLAPSED_RECENT_SEARCHES)

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = str("search_recent_searches"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearAll) {
                Text(str("search_clear_all"), style = MaterialTheme.typography.labelLarge)
            }
        }

        visible.forEach { term ->
            RecentSearchRow(term = term, onRun = { onRun(term) }, onForget = { onForget(term) })
        }

        if (searches.size > COLLAPSED_RECENT_SEARCHES) {
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "seeMoreChevron")
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = str(if (expanded) "search_see_less" else "search_see_more"),
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(18.dp)
                        .rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    term: String,
    onRun: () -> Unit,
    onForget: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onRun,
            )
            .pressScale(interactionSource)
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = term,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Only offered on hover: a row of eight crosses is eight targets the pointer can miss.
        AnimatedVisibility(
            visible = hovered,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            IconButton(onClick = onForget) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = str("search_remove_recent"),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The first few songs of a chart, and the door to the rest of it. */
@Composable
private fun ChartPreviewSection(
    kind: ChartKind,
    entries: List<ChartEntry>,
    isLoading: Boolean,
    currentTrack: Track?,
    onKindChange: (ChartKind) -> Unit,
    onPlayFrom: (Int) -> Unit,
    onArtistClick: (Track) -> Unit,
    onSeeAll: () -> Unit,
) {
    Column {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            SongChart(
                kind = kind,
                genre = ChartsViewModel.chartGenres.first(),
                genres = ChartsViewModel.chartGenres,
                onKindChange = onKindChange,
                onGenreChange = {},
                // The landing previews one genre; picking between them belongs to the chart itself.
                showGenreRow = false,
            )
        }

        if (isLoading && entries.isEmpty()) {
            repeat(3) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        }

        if (entries.isNotEmpty()) {
            entries.forEachIndexed { index, entry ->
                ChartTrackRow(
                    track = entry.track,
                    rank = entry.rank,
                    currentlyPlayingTrack = currentTrack,
                    onClick = { onPlayFrom(index) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onArtistClick = onArtistClick,
                )
            }
            ChartSectionHeader(str("chart_see_full"), onSeeAll, Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun LandingSectionTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A square cover and two lines, for the horizontal shelves. */
@Composable
private fun LandingTrackCard(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .width(152.dp)
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .pressScale(interactionSource)
            .padding(4.dp)
            .animateContentSize(),
    ) {
        AsyncImage(
            model = track.fullResArtwork,
            contentDescription = null,
            error = rememberDefaultAvatarPainter(),
            fallback = rememberDefaultAvatarPainter(),
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = track.title ?: str("untitled_track"),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A large tappable shortcut, for the two screens that are more than a search away. */
@Composable
private fun LandingDoorCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(20.dp),
        color = tint.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .height(84.dp)
            .pressScale(interactionSource),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
    }
}

/** How many recent searches are shown before "see more". */
private const val COLLAPSED_RECENT_SEARCHES = 3

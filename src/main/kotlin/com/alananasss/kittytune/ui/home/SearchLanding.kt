package com.alananasss.kittytune.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
 * and it pushed everything worth reading off the screen. What is here instead is either about this
 * listener or is a chart.
 *
 * Every section is introduced by the same [LandingHeader], and every section's way into more is a
 * link in that header rather than a row of its own underneath. A "see the full chart" line that is
 * set like a title reads as the name of the next section, which is what made the first cut of this
 * screen look like it had lost its way halfway down.
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
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        if (vm.recentSearches.isNotEmpty()) {
            item {
                RecentSearchesSection(
                    searches = vm.recentSearches,
                    onRun = { vm.runRecentSearch(it) },
                    onForget = { vm.forgetSearch(it) },
                    onClearAll = { vm.clearRecentSearches() },
                )
            }
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
                Column {
                    LandingHeader(str("home_from_your_artists"), action = null)
                    Text(
                        text = str("home_from_your_artists_sub"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = CONTENT_PADDING),
                    )
                    Spacer(Modifier.height(10.dp))
                    ScrollableLazyRow(
                        contentPadding = PaddingValues(horizontal = CONTENT_PADDING),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        fadeColor = MaterialTheme.colorScheme.surface,
                    ) {
                        items(vm.likedArtistUpdates.size) { index ->
                            val track = vm.likedArtistUpdates[index]
                            LandingTrackCard(
                                track = track,
                                isCurrent = playerViewModel.currentTrack?.id == track.id,
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
        }

        item {
            Column {
                LandingHeader(str("explore"), action = null)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CONTENT_PADDING),
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
        }

        if (vm.personalizedCategories.isNotEmpty()) {
            item {
                Column {
                    LandingHeader(str("search_section_personalized"), action = null)
                    Spacer(Modifier.height(10.dp))
                    // A plain scrolling row rather than a wrapping one: there can be ten of these and
                    // the wrapping one made the page jump about while they loaded in.
                    ScrollableLazyRow(
                        contentPadding = PaddingValues(horizontal = CONTENT_PADDING),
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
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The one way this screen introduces a section: a title, and optionally a link into more.
 *
 * Every section uses it, so the eye learns it once. A section that has nowhere else to go passes
 * `null` and gets the title alone, which is why the spacing reads the same all the way down.
 */
@Composable
private fun LandingHeader(title: String, action: Pair<String, () -> Unit>?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CONTENT_PADDING, end = CONTENT_PADDING - 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = action.second) {
                Text(
                    text = action.first,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * The searches already run.
 *
 * Collapsed to [COLLAPSED_RECENT_SEARCHES] with a "see more", the way a long list of anything should
 * be: someone opening search wants their last one or two, and the other eighteen are a scroll away
 * rather than the whole screen. Each row deletes on its own, so one stale query does not mean
 * clearing everything.
 */
@Composable
private fun RecentSearchesSection(
    searches: List<String>,
    onRun: (String) -> Unit,
    onForget: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column {
        LandingHeader(
            title = str("search_recent_searches"),
            action = str("search_clear_all") to onClearAll,
        )

        val visible = if (expanded) searches else searches.take(COLLAPSED_RECENT_SEARCHES)
        visible.forEach { term ->
            RecentSearchRow(term = term, onRun = { onRun(term) }, onForget = { onForget(term) })
        }

        if (searches.size > COLLAPSED_RECENT_SEARCHES) {
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "seeMoreChevron")
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(start = CONTENT_PADDING - 12.dp, top = 2.dp),
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

/**
 * One stored query.
 *
 * The delete cross is always laid out and only its opacity follows the pointer. An `IconButton`
 * appeared here first and carried Material's 48 dp minimum touch target, so the row grew by a third
 * the moment the pointer came near it and shoved the whole page down under the cursor — which is the
 * opposite of what hovering is for.
 */
@Composable
private fun RecentSearchRow(
    term: String,
    onRun: () -> Unit,
    onForget: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val crossSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val background by animateColorAsState(
        targetValue = if (hovered) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            Color.Transparent
        },
        label = "recentSearchBackground",
    )
    val crossAlpha by animateFloatAsState(if (hovered) 1f else 0f, label = "recentSearchCross")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CONTENT_PADDING - 6.dp)
            .height(ROW_HEIGHT)
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onRun,
            )
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = term,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = crossSource,
                    indication = null,
                    onClick = onForget,
                )
                .alpha(crossAlpha),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = str("search_remove_recent"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** The first few songs of a chart, and the way into all of it. */
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
        LandingHeader(
            title = str("chart_section_title"),
            action = str("search_see_all") to onSeeAll,
        )

        SongChart(
            kind = kind,
            genre = ChartsViewModel.chartGenres.first(),
            genres = ChartsViewModel.chartGenres,
            onKindChange = onKindChange,
            onGenreChange = {},
            // The landing previews one genre; choosing between them belongs to the chart itself.
            showGenreRow = false,
            isSwitching = isLoading && entries.isNotEmpty(),
            modifier = Modifier.padding(horizontal = CONTENT_PADDING - 6.dp),
        )

        Spacer(Modifier.height(6.dp))

        if (entries.isEmpty() && isLoading) {
            repeat(3) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CONTENT_PADDING, vertical = 3.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                    onArtistClick = onArtistClick,
                )
            }
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
            .width(150.dp)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .pressScale(interactionSource),
    ) {
        AsyncImage(
            model = track.fullResArtwork,
            contentDescription = null,
            error = rememberDefaultAvatarPainter(),
            fallback = rememberDefaultAvatarPainter(),
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
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
        Spacer(Modifier.height(2.dp))
        Text(
            text = track.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A compact shortcut, for the two screens that are more than a search away. */
@Composable
private fun LandingDoorCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val alpha by animateFloatAsState(if (hovered) 0.24f else 0.14f, label = "doorCard")

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = alpha),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .height(72.dp)
            .pressScale(interactionSource),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
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

/** One shared left/right inset, so every section starts on the same line. */
private val CONTENT_PADDING = 20.dp

/** Gap between sections, and the height a recent-search row is pinned to. */
private val SECTION_GAP = 14.dp
private val ROW_HEIGHT = 40.dp

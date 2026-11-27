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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.alananasss.kittytune.ui.common.InlineSectionHeader
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.alananasss.kittytune.ui.common.TrackArtistClick
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.common.ScrollableLazyRow
import com.alananasss.kittytune.ui.common.pressScale
import com.alananasss.kittytune.ui.common.rememberDefaultAvatarPainter
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.profile.ArtistRelease
import com.alananasss.kittytune.ui.profile.ArtistReleaseStrip
import com.alananasss.kittytune.ui.profile.openArtistRelease
import com.alananasss.kittytune.ui.profile.rememberVisibleArtistReleases
import com.alananasss.kittytune.ui.player.PlayerViewModel
import androidx.compose.material3.ButtonDefaults

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
    val visits by vm.recentVisits.collectAsState()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        if (vm.recentSearches.isNotEmpty() || visits.isNotEmpty()) {
            item {
                RecentSearchesSection(
                    visits = visits,
                    searches = vm.recentSearches,
                    onRun = { vm.runRecentSearch(it) },
                    onForget = { vm.forgetSearch(it) },
                    onOpenVisit = { visit ->
                        when (visit.kind) {
                            com.alananasss.kittytune.data.search.RecentVisits.Kind.TRACK ->
                                visit.track?.let { playerViewModel.playPlaylist(listOf(it), 0) }
                            else -> visit.destination?.let { playerViewModel.navigateToPlaylistId = it }
                        }
                    },
                    onForgetVisit = { vm.forgetVisit(it) },
                    onClearAll = { vm.clearRecentSearches() },
                )
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
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
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
 * The country's chart, a few songs and the way into all of it, for the home page: it moved there from the search
 * page, where it was a thing to scroll past on the way to typing (issue #66).
 */
@Composable
internal fun HomeChartSection(viewModel: ChartsViewModel, playerViewModel: PlayerViewModel, onOpenCharts: () -> Unit) {
    val chart = rememberVisibleChartEntries(viewModel)
    val entries = remember(chart) { chart.take(CHART_PREVIEW_LENGTH) }
    val tracks = remember(entries) { entries.map(ChartEntry::track) }
    ChartPreviewSection(
        kind = viewModel.chartKind,
        entries = entries,
        isLoading = viewModel.isChartLoading,
        currentTrack = playerViewModel.uiCurrentTrack,
        isPlaying = playerViewModel.isPlaying,
        onKindChange = { viewModel.loadChart(it, viewModel.chartGenre) },
        onPlayFrom = { index ->
            playerViewModel.playPlaylist(
                tracks = tracks,
                startIndex = index,
                context = PlaybackContext(
                    displayText = str(
                        viewModel.chartKind.labelKey()
                    ),
                    navigationId = "charts",
                ),
            )
        },
        onArtistClick = playerViewModel::navigateToTrackArtist,
        onOptionClick = { track -> playerViewModel.showTrackOptions(track) },
        onSeeAll = onOpenCharts,
    )
}

@Composable
internal fun FromYourArtistsSection(
    releases: List<ArtistRelease>,
    playerViewModel: PlayerViewModel,
) {
    val visible = rememberVisibleArtistReleases(releases)
    if (visible.isEmpty()) return

    val title = str("home_from_your_artists")
    Column {
        LandingHeader(title, action = null)
        ArtistReleaseStrip(
            releases = visible,
            onOpen = { release ->
                playerViewModel.openArtistRelease(
                    release,
                    PlaybackContext(displayText = title, navigationId = "home"),
                )
            },
        )
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
    InlineSectionHeader(
        title = title,
        titleStyle = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = CONTENT_PADDING, end = CONTENT_PADDING, top = 4.dp, bottom = 8.dp),
        inlineTrailingAction = if (action != null) {
            {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = action.second,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 32.dp),
                ) {
                    Text(
                        text = action.first,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else null,
    )
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
    visits: List<com.alananasss.kittytune.data.search.RecentVisits.Visit>,
    searches: List<String>,
    onRun: (String) -> Unit,
    onForget: (String) -> Unit,
    onOpenVisit: (com.alananasss.kittytune.data.search.RecentVisits.Visit) -> Unit,
    onForgetVisit: (com.alananasss.kittytune.data.search.RecentVisits.Visit) -> Unit,
    onClearAll: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column {
        LandingHeader(
            title = str("search_recent_searches"),
            action = str("search_clear_all") to onClearAll,
        )

        // What was opened comes first, as itself: the artist with their picture is one click from their page,
        // where the words typed to find them were two (issue #66).
        val total = visits.size + searches.size
        val shownVisits = if (expanded) visits else visits.take(COLLAPSED_RECENT_SEARCHES)
        shownVisits.forEach { visit ->
            RecentVisitRow(visit = visit, onOpen = { onOpenVisit(visit) }, onForget = { onForgetVisit(visit) })
        }
        val roomForTerms = if (expanded) searches.size else (COLLAPSED_RECENT_SEARCHES - shownVisits.size).coerceAtLeast(0)
        searches.take(roomForTerms).forEach { term ->
            RecentSearchRow(term = term, onRun = { onRun(term) }, onForget = { onForget(term) })
        }

        if (total > COLLAPSED_RECENT_SEARCHES) {
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "seeMoreChevron")
            TextButton(shapes = ButtonDefaults.shapes(),
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

/** Something opened from search before: an artist, a track or a playlist, with its picture. */
@Composable
private fun RecentVisitRow(
    visit: com.alananasss.kittytune.data.search.RecentVisits.Visit,
    onOpen: () -> Unit,
    onForget: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val crossSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = hovered,
        selectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unselectedColor = Color.Transparent,
        label = "recentVisitBackground",
    )
    val crossAlpha by animateFloatAsState(if (hovered) 1f else 0f, label = "recentVisitCross")
    val isArtist = visit.kind == com.alananasss.kittytune.data.search.RecentVisits.Kind.ARTIST
    val subtitle = when (visit.kind) {
        com.alananasss.kittytune.data.search.RecentVisits.Kind.ARTIST -> str("search_kind_artist")
        com.alananasss.kittytune.data.search.RecentVisits.Kind.TRACK -> str("search_kind_track", visit.subtitle.orEmpty())
        com.alananasss.kittytune.data.search.RecentVisits.Kind.PLAYLIST ->
            listOfNotNull(str("search_kind_playlist"), visit.subtitle?.takeIf { it.isNotBlank() }).joinToString(" · ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CONTENT_PADDING - 6.dp)
            .height(VISIT_ROW_HEIGHT)
            .hoverable(interactionSource)
            .pressScale(interactionSource, pressedScale = 0.98f)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = visit.imageUrl,
            contentDescription = null,
            placeholder = if (isArtist) rememberDefaultAvatarPainter() else null,
            error = if (isArtist) rememberDefaultAvatarPainter() else null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = visit.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (visit.isVerified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(interactionSource = crossSource, indication = null, onClick = onForget)
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

private val VISIT_ROW_HEIGHT = 56.dp

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

    val background = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = hovered,
        selectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unselectedColor = Color.Transparent,
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
internal fun ChartPreviewSection(
    kind: ChartKind,
    entries: List<ChartEntry>,
    isLoading: Boolean,
    currentTrack: Track?,
    onKindChange: (ChartKind) -> Unit,
    onPlayFrom: (Int) -> Unit,
    onArtistClick: TrackArtistClick,
    onSeeAll: () -> Unit,
    isPlaying: Boolean = false,
    onOptionClick: ((Track) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = CONTENT_PADDING)) {
        val widePreview = maxWidth >= 940.dp
        val showRowMetadata = if (widePreview) maxWidth * 0.60f >= 560.dp else maxWidth >= 680.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SongChart(
                kind = kind,
                genre = ChartsViewModel.chartGenres.first(),
                genres = ChartsViewModel.chartGenres,
                onKindChange = onKindChange,
                onGenreChange = {},
                showGenreRow = false,
                leadingContent = {
                    InlineSectionHeader(
                        title = str("chart_section_title"),
                        titleStyle = MaterialTheme.typography.headlineSmall,
                        actionTooltip = str("btn_see_all"),
                        arrowSize = 26.dp,
                        onAction = onSeeAll,
                    )
                },
            )
            when {
                isLoading -> if (widePreview) {
                    com.alananasss.kittytune.ui.common.MorphingGrid(
                        columns = 2,
                        spacing = 12.dp,
                        weights = listOf(0.40f, 0.60f),
                    ) {
                        ChartLoadingRows()
                        ChartLoadingRows()
                    }
                } else {
                    ChartLoadingRows()
                }
                entries.isEmpty() -> EmptyChart(onRetry = { onKindChange(kind) })
                widePreview && entries.size >= 2 -> {
                    val leader = entries.first()
                    val tail = entries.drop(1)
                    com.alananasss.kittytune.ui.common.MorphingGrid(
                        columns = 2,
                        spacing = 12.dp,
                        weights = listOf(0.40f, 0.60f),
                    ) {
                        ChartLeaderCard(
                            track = leader.track,
                            currentlyPlayingTrack = currentTrack,
                            onClick = { onPlayFrom(0) },
                            isPlaying = isPlaying,
                            onOptionClick = onOptionClick?.let { cb -> { cb(leader.track) } },
                            onArtistClick = onArtistClick,
                            modifier = Modifier.fillMaxHeight(),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            tail.forEachIndexed { idx, entry ->
                                val playIdx = idx + 1
                                ChartTrackRow(
                                    track = entry.track,
                                    rank = entry.rank,
                                    currentlyPlayingTrack = currentTrack,
                                    onClick = { onPlayFrom(playIdx) },
                                    index = idx,
                                    totalCount = tail.size,
                                    isPlaying = isPlaying,
                                    showExtraMetadata = showRowMetadata,
                                    onOptionClick = onOptionClick?.let { cb -> { cb(entry.track) } },
                                    onArtistClick = onArtistClick,
                                )
                            }
                        }
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    entries.forEachIndexed { index, entry ->
                        ChartTrackRow(
                            track = entry.track,
                            rank = entry.rank,
                            currentlyPlayingTrack = currentTrack,
                            onClick = { onPlayFrom(index) },
                            index = index,
                            totalCount = entries.size,
                            isPlaying = isPlaying,
                            showExtraMetadata = showRowMetadata,
                            onOptionClick = onOptionClick?.let { cb -> { cb(entry.track) } },
                            onArtistClick = onArtistClick,
                        )
                    }
                }
            }
        }
    }
}

/** A square cover and two lines, for the horizontal shelves. */
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
private const val CHART_PREVIEW_LENGTH = 5

/** Gap between sections, and the height a recent-search row is pinned to. */
private val SECTION_GAP = 14.dp
private val ROW_HEIGHT = 40.dp

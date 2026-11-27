@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.alananasss.kittytune.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.onClick
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.GoPlusBadge
import com.alananasss.kittytune.ui.common.PlayingBars
import com.alananasss.kittytune.ui.common.ShimmerBox
import com.alananasss.kittytune.ui.common.ShimmerLine
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.common.TrackArtistClick
import com.alananasss.kittytune.ui.common.TrackRowSocialMarkers
import com.alananasss.kittytune.ui.common.pressScale
import com.alananasss.kittytune.utils.makeTimeString
import java.text.NumberFormat

@Composable
internal fun SongChart(
    kind: ChartKind,
    genre: ChartGenre,
    genres: List<ChartGenre>,
    onKindChange: (ChartKind) -> Unit,
    onGenreChange: (ChartGenre) -> Unit,
    modifier: Modifier = Modifier,
    showGenreRow: Boolean = true,
    country: ChartCountry? = null,
    onCountryChange: (ChartCountry) -> Unit = {},
    leadingContent: (@Composable () -> Unit)? = null,
    trailingFallback: (@Composable () -> Unit)? = null,
) {
    val hasCountryFilter = country != null && kind == ChartKind.COUNTRY
    val hasGenreFilter = showGenreRow && kind == ChartKind.TOP
    val filterControl: (@Composable () -> Unit)? = when {
        hasCountryFilter -> {
            {
                val currentCountry = country
                val deviceCountry = ChartCountry.forDevice(Strings.resolvedLanguage)
                val countries = remember(deviceCountry) {
                    listOf(deviceCountry) + ChartCountry.entries.filterNot { it == deviceCountry }
                }
                ChartFilter(
                    options = countries,
                    selectedOption = currentCountry,
                    icon = Icons.Rounded.Public,
                    label = { str("chart_country_${it.code.lowercase()}") },
                    onSelect = onCountryChange,
                )
            }
        }
        hasGenreFilter -> {
            {
                ChartFilter(
                    options = genres,
                    selectedOption = genre,
                    icon = Icons.Rounded.Tune,
                    label = { str(if (it.id == "all") "genre_all_music" else "chart_genre_${it.id}") },
                    onSelect = onGenreChange,
                )
            }
        }
        else -> null
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val isWide = maxWidth >= 820.dp
        val isStacked = maxWidth < 540.dp
        if (isWide) {
            if (leadingContent != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        leadingContent()
                    }
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            modifier = Modifier.padding(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ChartKindGroup(
                                kind = kind,
                                onKindChange = onKindChange,
                                stacked = false,
                                modifier = Modifier.widthIn(min = 380.dp, max = 520.dp),
                            )
                            filterControl?.invoke()
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ChartKindGroup(
                            kind = kind,
                            onKindChange = onKindChange,
                            stacked = false,
                            modifier = Modifier.widthIn(min = 380.dp, max = 540.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        val rightSlot = filterControl ?: trailingFallback
                        rightSlot?.invoke()
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                leadingContent?.invoke()
                ChartKindGroup(
                    kind = kind,
                    onKindChange = onKindChange,
                    stacked = isStacked,
                    modifier = Modifier.fillMaxWidth(),
                )
                filterControl?.invoke()
            }
        }
    }
}

@Composable
private fun ChartKindGroup(
    kind: ChartKind,
    onKindChange: (ChartKind) -> Unit,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    ExpressiveConnectedButtonGroup(
        options = ChartKind.entries,
        selectedOption = kind,
        onOptionSelected = { if (it != kind) onKindChange(it) },
        modifier = modifier.selectableGroup(),
        fillMaxWidth = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        uncheckedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconSpacing = 8.dp,
        iconProvider = if (stacked) null else { option -> ChartKindIcon(option) },
        labelProvider = { option ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (stacked) ChartKindIcon(option)
                Text(
                    text = str(option.labelKey()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    minLines = if (stacked) 2 else 1,
                    maxLines = if (stacked) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    )
}

@Composable
private fun ChartKindIcon(kind: ChartKind) {
    val icon = when (kind) {
        ChartKind.COUNTRY -> Icons.Rounded.Public
        ChartKind.TOP -> Icons.Rounded.Equalizer
        ChartKind.TRENDING -> Icons.AutoMirrored.Rounded.TrendingUp
    }
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
}

@Composable
private fun <T> ChartFilter(
    options: List<T>,
    selectedOption: T,
    icon: ImageVector,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton(
            onClick = { expanded = true },
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label(selectedOption), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp).widthIn(min = 200.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            options.forEach { option ->
                val isSelected = option == selectedOption
                DropdownMenuItem(
                    text = { Text(label(option), fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                    modifier = Modifier.semantics { selected = isSelected },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = {
                        expanded = false
                        if (!isSelected) onSelect(option)
                    },
                )
            }
        }
    }
}

private val CHART_OUTER_RADIUS = 24.dp
private val CHART_INNER_RADIUS = 6.dp
private val CHART_SELECTED_RADIUS = 22.dp
private val CHART_PRESSED_RADIUS = 20.dp
private val CHART_HOVER_RADIUS = 12.dp
private val CHART_FOCUS_RADIUS = 14.dp

internal fun chartGroupItemShape(index: Int, totalCount: Int): RoundedCornerShape {
    if (totalCount <= 1) return RoundedCornerShape(CHART_OUTER_RADIUS)
    val top = if (index == 0) CHART_OUTER_RADIUS else CHART_INNER_RADIUS
    val bottom = if (index == totalCount - 1) CHART_OUTER_RADIUS else CHART_INNER_RADIUS
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

@Composable
private fun chartSegmentedShapes(index: Int, totalCount: Int): ListItemShapes {
    val base = chartGroupItemShape(index, totalCount)
    val topHover = if (index == 0) CHART_OUTER_RADIUS else CHART_HOVER_RADIUS
    val bottomHover = if (index == totalCount - 1) CHART_OUTER_RADIUS else CHART_HOVER_RADIUS
    val topFocus = if (index == 0) CHART_OUTER_RADIUS else CHART_FOCUS_RADIUS
    val bottomFocus = if (index == totalCount - 1) CHART_OUTER_RADIUS else CHART_FOCUS_RADIUS
    return ListItemShapes(
        shape = base,
        selectedShape = RoundedCornerShape(CHART_SELECTED_RADIUS),
        pressedShape = RoundedCornerShape(CHART_PRESSED_RADIUS),
        focusedShape = RoundedCornerShape(topStart = topFocus, topEnd = topFocus, bottomEnd = bottomFocus, bottomStart = bottomFocus),
        hoveredShape = RoundedCornerShape(topStart = topHover, topEnd = topHover, bottomEnd = bottomHover, bottomStart = bottomHover),
        draggedShape = RoundedCornerShape(CHART_SELECTED_RADIUS),
    )
}

@Composable
internal fun ChartTrackRow(
    track: Track,
    rank: Int,
    currentlyPlayingTrack: Track?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = (rank - 1).coerceAtLeast(0),
    totalCount: Int = 1,
    isPlaying: Boolean = false,
    showExtraMetadata: Boolean = false,
    onOptionClick: (() -> Unit)? = null,
    onArtistClick: TrackArtistClick? = null,
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val isLead = rank == 1
    val onPodium = rank <= 3
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme
    val itemShapes = chartSegmentedShapes(index, totalCount)

    val hoverContainer = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = (hovered || focused) && !isCurrent,
        selectedColor = colors.surfaceContainerHighest,
        unselectedColor = colors.surfaceContainerHigh,
        animationSpec = spring(),
        label = "chartRowHover",
    )
    val containerColor = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = isCurrent,
        selectedColor = colors.secondaryContainer,
        unselectedColor = hoverContainer,
        animationSpec = spring(),
        label = "chartRowContainer",
    )
    val contentColor = if (isCurrent) colors.onSecondaryContainer else colors.onSurface
    val supportingColor = if (isCurrent) colors.onSecondaryContainer.copy(alpha = 0.85f) else colors.onSurfaceVariant

    val rowModifier = modifier
        .fillMaxWidth()
        .pressScale(interactionSource, pressedScale = 0.985f)
        .pointerHoverIcon(PointerIcon.Hand)
        .let { m ->
            if (onOptionClick != null) {
                m.onClick(
                    matcher = PointerMatcher.mouse(PointerButton.Secondary),
                    onClick = onOptionClick,
                )
            } else {
                m
            }
        }

    SegmentedListItem(
        selected = isCurrent,
        onClick = onClick,
        shapes = itemShapes,
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        onLongClick = onOptionClick,
        onLongClickLabel = if (onOptionClick != null) str("btn_options") else null,
        colors = ListItemDefaults.segmentedColors(
            containerColor = containerColor,
            contentColor = colors.onSurface,
            leadingContentColor = colors.onSurface,
            trailingContentColor = colors.onSurfaceVariant,
            supportingContentColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.secondaryContainer,
            selectedContentColor = colors.onSecondaryContainer,
            selectedLeadingContentColor = colors.onSecondaryContainer,
            selectedTrailingContentColor = colors.onSecondaryContainer,
            selectedSupportingContentColor = colors.onSecondaryContainer.copy(alpha = 0.85f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        interactionSource = interactionSource,
        leadingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ChartRankBadge(
                    rank = rank,
                    isLead = isLead,
                    onPodium = onPodium,
                    isCurrent = isCurrent,
                    hovered = hovered || pressed,
                    supportingColor = supportingColor,
                )
                ChartArtwork(
                    track = track,
                    size = 48.dp,
                    shape = RoundedCornerShape(12.dp),
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    showPlayOverlay = hovered || focused,
                )
            }
        },
        supportingContent = {
            ChartTrackCredits(
                track = track,
                onArtistClick = onArtistClick,
                supportingColor = supportingColor,
                hoverColor = contentColor,
                verifiedTint = if (isCurrent) colors.onSecondaryContainer else colors.primary,
            )
        },
        trailingContent = {
            ChartTrackTrailing(
                track = track,
                isCurrent = isCurrent,
                showExtraMetadata = showExtraMetadata,
                onOptionClick = onOptionClick,
            )
        },
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = track.title ?: str("untitled_track"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (onPodium || isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (track.isGoPlus) {
                    GoPlusBadge(compact = true)
                }
            }
        },
    )
}

/**
 * Featured #1 podium card paired beside rows #2..#5 on wide desktop home screens so the chart
 * preview fills horizontal space cleanly instead of leaving stretched empty track rows.
 */
@Composable
internal fun ChartLeaderCard(
    track: Track,
    currentlyPlayingTrack: Track?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onOptionClick: (() -> Unit)? = null,
    onArtistClick: TrackArtistClick? = null,
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = MaterialTheme.colorScheme
    val hoverLeader = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = (hovered || focused) && !isCurrent,
        selectedColor = colors.surfaceContainerHighest,
        unselectedColor = colors.surfaceContainerHigh,
        animationSpec = spring(),
        label = "chartLeaderHover",
    )
    val containerColor = com.alananasss.kittytune.ui.theme.animatePaletteColor(
        selected = isCurrent,
        selectedColor = colors.secondaryContainer,
        unselectedColor = hoverLeader,
        animationSpec = spring(),
        label = "chartLeaderContainer",
    )
    val contentColor = if (isCurrent) colors.onSecondaryContainer else colors.onSurface
    val supportingColor = if (isCurrent) colors.onSecondaryContainer.copy(alpha = 0.85f) else colors.onSurfaceVariant

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(CHART_OUTER_RADIUS),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource, pressedScale = 0.985f)
            .pointerHoverIcon(PointerIcon.Hand)
            .let { m ->
                if (onOptionClick != null) {
                    m.onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Secondary),
                        onClick = onOptionClick,
                    )
                } else {
                    m
                }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ChartRankBadge(
                    rank = 1,
                    isLead = true,
                    onPodium = true,
                    isCurrent = isCurrent,
                    hovered = hovered || pressed,
                    supportingColor = supportingColor,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    track.genre?.takeIf { it.isNotBlank() }?.let { genre ->
                        ChartMetricPill(
                            text = genre,
                            icon = null,
                            isCurrent = isCurrent,
                            maxWidth = 132.dp,
                        )
                    }
                    if (onOptionClick != null) {
                        IconButton(
                            onClick = onOptionClick,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(34.dp),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = supportingColor),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = str("btn_options"),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ChartArtwork(
                    track = track,
                    size = 96.dp,
                    shape = RoundedCornerShape(18.dp),
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    showPlayOverlay = hovered || focused,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = track.title ?: str("untitled_track"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (track.isGoPlus) {
                            GoPlusBadge(compact = true)
                        }
                    }
                    ChartTrackCredits(
                        track = track,
                        onArtistClick = onArtistClick,
                        supportingColor = supportingColor,
                        hoverColor = contentColor,
                        verifiedTint = if (isCurrent) colors.onSecondaryContainer else colors.primary,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (track.playbackCount > 0) {
                    val locale = Strings.locale()
                    val count = remember(track.playbackCount, locale) {
                        NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.SHORT)
                            .apply { maximumFractionDigits = 1 }
                            .format(track.playbackCount)
                    }
                    ChartMetricPill(
                        text = count,
                        icon = Icons.Rounded.PlayArrow,
                        isCurrent = isCurrent,
                    )
                }
                if ((track.durationMs ?: 0L) > 0L) {
                    ChartMetricPill(
                        text = makeTimeString(track.durationMs ?: 0L),
                        icon = null,
                        isCurrent = isCurrent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartRankBadge(
    rank: Int,
    isLead: Boolean,
    onPodium: Boolean,
    isCurrent: Boolean,
    hovered: Boolean,
    supportingColor: Color,
) {
    val colors = MaterialTheme.colorScheme
    val cookieRotation by animateFloatAsState(
        targetValue = if (isLead && hovered) 24f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 360f),
        label = "rankCookieRot",
    )
    Box(
        modifier = Modifier.width(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLead -> {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer { rotationZ = cookieRotation }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = colors.onPrimary,
                        modifier = Modifier.graphicsLayer { rotationZ = -cookieRotation },
                        textAlign = TextAlign.Center,
                    )
                }
            }
            onPodium -> {
                val badgeBg = when {
                    rank == 2 -> colors.primaryContainer
                    isCurrent -> colors.onSecondaryContainer.copy(alpha = 0.14f)
                    else -> colors.surfaceContainerHighest
                }
                val badgeFg = when {
                    rank == 2 -> colors.onPrimaryContainer
                    isCurrent -> colors.onSecondaryContainer
                    else -> colors.primary
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg)
                        .then(
                            if (rank == 3) {
                                Modifier.border(
                                    1.dp,
                                    (if (isCurrent) colors.onSecondaryContainer else colors.outlineVariant).copy(alpha = 0.4f),
                                    RoundedCornerShape(12.dp),
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Text(
                    text = rank.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = supportingColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ChartArtwork(
    track: Track,
    size: Dp,
    shape: Shape,
    isCurrent: Boolean,
    isPlaying: Boolean,
    showPlayOverlay: Boolean,
) {
    val hasArtwork = !track.artworkUrl.isNullOrBlank() || !track.user?.avatarUrl.isNullOrBlank()
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(colors.surfaceContainerHighest)
            .border(0.5.dp, colors.outlineVariant.copy(alpha = 0.25f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(size * 0.42f),
            tint = colors.onSurfaceVariant,
        )
        if (hasArtwork) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.56f)),
                contentAlignment = Alignment.Center,
            ) {
                PlayingBars(
                    isPlaying = isPlaying,
                    color = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else if (showPlayOverlay) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.48f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ChartTrackCredits(
    track: Track,
    onArtistClick: TrackArtistClick?,
    supportingColor: Color,
    hoverColor: Color,
    verifiedTint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistLinkText(
            track = track,
            onArtistClick = onArtistClick,
            style = MaterialTheme.typography.bodySmall,
            color = supportingColor,
            hoverColor = hoverColor,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (track.user?.verified == true) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.Verified,
                contentDescription = null,
                tint = verifiedTint,
                modifier = Modifier.size(13.dp),
            )
        }
        TrackRowSocialMarkers(track)
    }
}

@Composable
private fun ChartTrackTrailing(
    track: Track,
    isCurrent: Boolean,
    showExtraMetadata: Boolean,
    onOptionClick: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val locale = Strings.locale()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showExtraMetadata) {
            track.genre?.takeIf { it.isNotBlank() }?.let { genre ->
                ChartMetricPill(
                    text = genre,
                    icon = null,
                    isCurrent = isCurrent,
                    maxWidth = 128.dp,
                )
            }
        }
        if (track.playbackCount > 0) {
            val count = remember(track.playbackCount, locale) {
                NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.SHORT)
                    .apply { maximumFractionDigits = 1 }
                    .format(track.playbackCount)
            }
            Tip(text = "$count ${str("playback_count_formatted")}") {
                ChartMetricPill(
                    text = count,
                    icon = Icons.Rounded.PlayArrow,
                    isCurrent = isCurrent,
                )
            }
            if (showExtraMetadata && (track.durationMs ?: 0L) > 0L) {
                ChartMetricPill(
                    text = makeTimeString(track.durationMs ?: 0L),
                    icon = null,
                    isCurrent = isCurrent,
                )
            }
        } else if ((track.durationMs ?: 0L) > 0L) {
            ChartMetricPill(
                text = makeTimeString(track.durationMs ?: 0L),
                icon = null,
                isCurrent = isCurrent,
            )
        }

        if (onOptionClick != null) {
            VerticalDivider(
                modifier = Modifier.height(24.dp).width(1.dp),
                color = (if (isCurrent) colors.onSecondaryContainer else colors.outlineVariant).copy(alpha = 0.35f),
            )
            IconButton(
                onClick = onOptionClick,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (isCurrent) colors.onSecondaryContainer else colors.onSurfaceVariant,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = str("btn_options"),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ChartMetricPill(
    text: String,
    icon: ImageVector?,
    isCurrent: Boolean,
    maxWidth: Dp = Dp.Unspecified,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (isCurrent) {
            colors.onSecondaryContainer.copy(alpha = 0.12f)
        } else {
            colors.surfaceContainerHighest
        },
        contentColor = if (isCurrent) colors.onSecondaryContainer else colors.onSurfaceVariant,
        modifier = if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ChartLoadingRows(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val count = 5
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        repeat(count) { index ->
            Surface(
                shape = chartGroupItemShape(index, count),
                color = colors.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                        ShimmerBox(Modifier.size(if (index == 0) 36.dp else 28.dp).clip(CircleShape))
                    }
                    ShimmerBox(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShimmerLine(Modifier.fillMaxWidth(if (index == 0) 0.62f else 0.52f))
                        ShimmerLine(Modifier.fillMaxWidth(0.34f).height(12.dp))
                    }
                    ShimmerBox(Modifier.width(58.dp).height(26.dp).clip(CircleShape))
                }
            }
        }
    }
}

@Composable
internal fun EmptyChart(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(CHART_OUTER_RADIUS),
        color = colors.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicOff,
                    contentDescription = null,
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = str("no_tracks_found"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(str("btn_retry"))
            }
        }
    }
}

package com.alananasss.kittytune.ui.home

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
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
import com.alananasss.kittytune.ui.common.ShimmerBox
import com.alananasss.kittytune.ui.common.TrackArtistClick
import com.alananasss.kittytune.ui.common.pressScale
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
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 540.dp
            ExpressiveConnectedButtonGroup(
                options = ChartKind.entries,
                selectedOption = kind,
                onOptionSelected = { if (it != kind) onKindChange(it) },
                modifier = Modifier.selectableGroup(),
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

        if (country != null && kind == ChartKind.COUNTRY) {
            val deviceCountry = ChartCountry.forDevice(Strings.resolvedLanguage)
            val countries = remember(deviceCountry) {
                listOf(deviceCountry) + ChartCountry.entries.filterNot { it == deviceCountry }
            }
            ChartFilter(
                options = countries,
                selectedOption = country,
                icon = Icons.Rounded.Public,
                label = { str("chart_country_${it.code.lowercase()}") },
                onSelect = onCountryChange,
            )
        }

        if (showGenreRow && kind == ChartKind.TOP) {
            ChartFilter(
                options = genres,
                selectedOption = genre,
                icon = Icons.Rounded.Tune,
                label = { str(if (it.id == "all") "genre_all_music" else "chart_genre_${it.id}") },
                onSelect = onGenreChange,
            )
        }
    }
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

@Composable
internal fun ChartLeadTrack(
    entry: ChartEntry,
    currentTrack: Track?,
    onClick: () -> Unit,
    onArtistClick: TrackArtistClick,
    modifier: Modifier = Modifier,
) {
    val track = entry.track
    val isCurrent = currentTrack?.id == track.id
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 440.dp
        val shape = RoundedCornerShape(26.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interaction, pressedScale = 0.985f)
                .clip(shape)
                .background(colors.primaryContainer)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    role = Role.Button,
                    onClickLabel = str("btn_play"),
                    onClick = onClick,
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChartArtwork(
                track = track,
                size = if (compact) 72.dp else 106.dp,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 28.dp, bottomStart = 18.dp, bottomEnd = 28.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "#${entry.rank}",
                    modifier = Modifier.clip(CircleShape).background(colors.primary).padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onPrimary,
                )
                Text(
                    text = track.title ?: str("untitled_track"),
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ChartTrackCredits(track, onArtistClick, colors.onPrimaryContainer)
            }
            Box(
                modifier = Modifier.size(if (compact) 36.dp else 48.dp).clip(CircleShape).background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                ChartPlayIcon(isCurrent, colors.onPrimary)
            }
        }
    }
}

@Composable
internal fun ChartTrackRow(
    track: Track,
    rank: Int,
    currentlyPlayingTrack: Track?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: TrackArtistClick? = null,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val onPodium = rank <= 3
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = when {
            isCurrent -> colors.secondaryContainer
            hovered -> colors.surfaceContainerHighest
            else -> colors.surfaceContainerHigh
        },
        label = "chartTrackContainer",
    )
    val content = if (isCurrent) colors.onSecondaryContainer else colors.onSurface
    val supporting = if (isCurrent) colors.onSecondaryContainer else colors.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource, pressedScale = 0.99f)
            .clip(shape)
            .background(container)
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = str("btn_play"),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(if (onPodium) colors.tertiaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                style = if (onPodium) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (onPodium) colors.onTertiaryContainer else supporting,
            )
        }
        ChartArtwork(track, size = 44.dp, shape = RoundedCornerShape(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: str("untitled_track"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (onPodium || isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ChartTrackCredits(track, onArtistClick, supporting)
        }
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (isCurrent || hovered) ChartPlayIcon(isCurrent, content)
        }
    }
}

@Composable
private fun ChartArtwork(track: Track, size: Dp, shape: Shape) {
    val hasArtwork = !track.artworkUrl.isNullOrBlank() || !track.user?.avatarUrl.isNullOrBlank()
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.size(size).clip(shape).background(colors.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(size * 0.4f),
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
    }
}

@Composable
private fun ChartTrackCredits(track: Track, onArtistClick: TrackArtistClick?, color: Color) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistLinkText(
            track = track,
            onArtistClick = onArtistClick,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            hoverColor = color,
        )
        if (track.playbackCount > 0) {
            val locale = Strings.locale()
            val count = remember(track.playbackCount, locale) {
                NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.SHORT)
                    .apply { maximumFractionDigits = 1 }
                    .format(track.playbackCount)
            }
            Text(
                text = "$count ${str("playback_count_formatted")}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChartPlayIcon(isCurrent: Boolean, color: Color) {
    Icon(
        imageVector = if (isCurrent) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
        contentDescription = if (isCurrent) str("player_playing_now") else null,
        tint = color,
    )
}

@Composable
internal fun ChartLoadingRows(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        ShimmerBox(Modifier.fillMaxWidth().height(122.dp))
        Spacer(Modifier.height(6.dp))
        repeat(4) {
            ShimmerBox(Modifier.fillMaxWidth().height(60.dp))
        }
    }
}

@Composable
internal fun EmptyChart(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = str("no_tracks_found"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) { Text(str("btn_retry")) }
        }
    }
}

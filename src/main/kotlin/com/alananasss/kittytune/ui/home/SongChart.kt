package com.alananasss.kittytune.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.pressScale
import com.alananasss.kittytune.ui.common.rememberDefaultAvatarPainter

/**
 * A song chart: the songs in order, with their place in the order.
 *
 * Two choices, both of which the server decides: which list (`top` is the biggest songs, `trending`
 * is the ones climbing) and which genre. The rank is the row's identity here, so it is set in the
 * accent colour and never shares a column with the play count — a chart you have to count down to
 * read is a list.
 */
@Composable
fun SongChart(
    kind: ChartKind,
    genre: ChartGenre,
    genres: List<ChartGenre>,
    onKindChange: (ChartKind) -> Unit,
    onGenreChange: (ChartGenre) -> Unit,
    modifier: Modifier = Modifier,
    showGenreRow: Boolean = true,
    /**
     * A switch is in flight, so the list below is about to be replaced.
     *
     * Without this the segmented button flips and the rows underneath stay put for as long as the
     * request takes, which is indistinguishable from the switch doing nothing.
     */
    isSwitching: Boolean = false,
) {
    Column(modifier = modifier) {
        Box {
            ExpressiveConnectedButtonGroup(
                options = ChartKind.entries,
                selectedOption = kind,
                onOptionSelected = onKindChange,
                fillMaxWidth = true,
                labelProvider = { option ->
                    Text(
                        text = str(
                            if (option == ChartKind.TOP) "chart_kind_top" else "chart_kind_trending"
                        ),
                        maxLines = 1,
                    )
                },
            )
            if (isSwitching) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }

        if (showGenreRow && kind == ChartKind.TOP) {
            // Only for the curated Top 50. The trending feed has no genre to pick — asking it for one
            // returns an empty chart — so a genre row shown there would be a control that does
            // nothing, which is worse than no control.
            Spacer(Modifier.padding(top = 4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(genres) { option ->
                    val selected = option == genre
                    FilterChip(
                        selected = selected,
                        onClick = { onGenreChange(option) },
                        label = { Text(str("chart_genre_${option.id}"), maxLines = 1) },
                        shape = FilterChipDefaults.shape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * One row of a chart.
 *
 * The top three are the ones a listener looks for, so they are set in the accent colour and given the
 * emphasised title; from fourth down the rank steps back to a supporting tone, which is what turns a
 * column of fifty numbers into a podium with a long tail behind it.
 */
@Composable
fun ChartTrackRow(
    track: Track,
    rank: Int,
    currentlyPlayingTrack: Track?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: ((Track) -> Unit)? = null,
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val onPodium = rank <= 3
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()

    val rankColor by animateColorAsState(
        targetValue = when {
            isCurrent -> MaterialTheme.colorScheme.primary
            onPodium -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "chartRankColor",
    )
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "chartTitleColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
            )
            // The same interaction source the hover and the press scale read, so the row does not
            // answer a click the pointer was never over.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .pressScale(interactionSource)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            style = if (onPodium) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.Bold,
            color = rankColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(34.dp),
        )

        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                error = rememberDefaultAvatarPainter(),
                fallback = rememberDefaultAvatarPainter(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )

            when {
                isCurrent -> Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = str("player_playing_now"),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                // A chart is a queue: pressing a song means playing the chart from there, so the cover
                // offers that on hover rather than hiding it behind a menu.
                hovered -> Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
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

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: str("untitled_track"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent || onPodium) FontWeight.Bold else FontWeight.Medium,
                color = titleColor,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtistLinkText(
                    track = track,
                    onArtistClick = onArtistClick,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = " • ${formatCompactNumber(track.playbackCount.toLong())} ${str("playback_count_formatted")}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

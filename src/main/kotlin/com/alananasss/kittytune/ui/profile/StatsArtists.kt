@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.ui.common.ScrollableLazyRow
import com.alananasss.kittytune.ui.common.hapticAction

private const val TOP_ARTISTS_SHOWN = 12

@Composable
internal fun TopArtistsSection(
    artists: List<ReportArtist>,
    onOpenAll: () -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            StatsPanelHeader(
                title = str("listening_stats_top_artists"),
                icon = Icons.Rounded.People,
                iconShape = MaterialShapes.Cookie6Sided.toShape(),
                iconContainer = scheme.primaryContainer,
                iconContent = scheme.onPrimaryContainer,
                actionLabel = str("listening_stats_show_all"),
                actionIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                onAction = onOpenAll,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            ScrollableLazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                fadeColor = scheme.surfaceContainer,
                fadeLength = 24.dp,
            ) {
                itemsIndexed(artists.take(TOP_ARTISTS_SHOWN), key = { _, artist -> artist.name }) { index, artist ->
                    TopArtistCard(rank = index + 1, artist = artist) { onArtistClick(artist) }
                }
            }
        }
    }
}

@Composable
private fun TopArtistCard(rank: Int, artist: ReportArtist, onClick: () -> Unit) {
    val featured = rank == 1
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> 14.dp
            featured -> 26.dp
            else -> 20.dp
        },
        label = "artistCardCorner",
    )
    val rankSize = if (featured) 36.dp else 28.dp
    val captionColor = if (featured) scheme.onPrimaryContainer else scheme.onSurfaceVariant

    Surface(
        onClick = hapticAction(action = onClick),
        interactionSource = interaction,
        shape = RoundedCornerShape(corner),
        color = if (featured) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (featured) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier.width(if (featured) 188.dp else 144.dp).pointerHoverIcon(PointerIcon.Hand),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(136.dp)) {
                StatsCover(artist.imageUrl, Modifier.matchParentSize(), placeholder = Icons.Rounded.Person)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .defaultMinSize(minWidth = rankSize, minHeight = rankSize)
                        .background(
                            color = if (featured) scheme.primary else scheme.surfaceContainer,
                            shape = if (featured) MaterialShapes.Cookie7Sided.toShape() else CircleShape,
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (featured) scheme.onPrimary else scheme.onSurface,
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (featured) FontWeight.Bold else FontWeight.SemiBold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = captionColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = formatDuration(artist.listenMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = captionColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

package com.alananasss.kittytune.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.ArtworkPalette
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** One colour of the liked songs, and the songs whose covers are of it. */
private data class Swatch(val color: Color, val tracks: List<Track>)

/**
 * The liked songs' own look (issue #66): "you can only tell it is your favourites by the title and the cover". The
 * covers of the latest likes are read for their main colours; the card is painted with the five most common, and a
 * colour, pressed, plays the favourites whose covers are of it. Under it, the artists liked most.
 */
@Composable
internal fun LikesVibeCard(likes: List<Track>, playerViewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val sample = remember(likes.size) { likes.take(SAMPLE_SIZE) }
    val swatches by produceState<List<Swatch>>(initialValue = emptyList(), sample) {
        value = withContext(Dispatchers.IO) { swatchesOf(sample) }
    }
    val topArtists = remember(likes.size) {
        likes.mapNotNull { t -> t.displayArtist.ifBlank { t.user?.username.orEmpty() }.takeIf { it.isNotBlank() } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
    }

    AnimatedVisibility(visible = swatches.size >= 2, enter = fadeIn() + expandVertically(), modifier = modifier) {
        val colors = swatches.map { it.color }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .background(Brush.horizontalGradient(colors.map { it.copy(alpha = 0.85f) }))
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.35f))))
                    .padding(20.dp)
            ) {
                Column {
                    Text(str("likes_vibe_title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(str("likes_vibe_sub"), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        swatches.forEach { swatch ->
                            SwatchButton(swatch) {
                                playerViewModel.playPlaylist(
                                    tracks = swatch.tracks.shuffled(),
                                    startIndex = 0,
                                    context = PlaybackContext(str("lib_liked_tracks"), "likes"),
                                    respectShuffle = false,
                                )
                            }
                        }
                    }
                    if (topArtists.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            str("likes_vibe_top", topArtists.joinToString(", ")),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchButton(swatch: Swatch, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.12f else 1f, label = "swatchScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(swatch.color)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
    ) {
        Surface(shape = CircleShape, color = Color.Transparent, border = BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)), modifier = Modifier.size(46.dp)) {}
        if (hovered) Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * The main colours of [tracks]' covers, by hue: covers too grey to have one are left out, the hues are put in
 * twelve buckets, and the five fullest buckets become the swatches.
 */
private suspend fun swatchesOf(tracks: List<Track>): List<Swatch> = coroutineScope {
    val reading = Semaphore(6)
    val colored = tracks.map { track ->
        async {
            reading.withPermit {
                val url = track.thumbnailUrl ?: track.fullResArtwork ?: return@withPermit null
                runCatching { ArtworkPalette.dominantColorCached(url, preferLight = false) }.getOrNull()?.let { track to it }
            }
        }
    }.awaitAll().filterNotNull()

    colored.mapNotNull { (track, color) ->
        val hsb = FloatArray(3)
        val argb = color.toArgb()
        java.awt.Color.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, hsb)
        if (hsb[1] < MIN_SATURATION || hsb[2] < MIN_BRIGHTNESS) null else Triple(track, color, (hsb[0] * HUE_BUCKETS).toInt() % HUE_BUCKETS)
    }
        .groupBy { it.third }
        .values
        .sortedByDescending { it.size }
        .take(SWATCHES)
        .map { group ->
            val h = group.map { it.second }
            Swatch(
                color = Color(h.map { it.red }.average().toFloat(), h.map { it.green }.average().toFloat(), h.map { it.blue }.average().toFloat()),
                tracks = group.map { it.first },
            )
        }
        .sortedBy { swatch ->
            val hsb = FloatArray(3)
            val argb = swatch.color.toArgb()
            java.awt.Color.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, hsb)
            hsb[0]
        }
}

private const val SAMPLE_SIZE = 80
private const val SWATCHES = 5
private const val HUE_BUCKETS = 12
private const val MIN_SATURATION = 0.18f
private const val MIN_BRIGHTNESS = 0.18f

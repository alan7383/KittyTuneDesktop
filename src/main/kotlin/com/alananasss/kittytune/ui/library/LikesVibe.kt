package com.alananasss.kittytune.ui.library

import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
 * The colour of the liked songs, from the top of their page (issue #66; redone in round 2 of the tester's list, 13).
 *
 * The covers of the latest likes are read for their main colours and mixed, the mix drawn toward the colours of the
 * theme the app is in so it belongs to it, and it comes down from the top of the page behind the header. It drifts
 * and shimmers while a liked song is playing; with nothing playing, or a song that is not among the likes, it fades
 * out, and comes back when one starts. (It was a card of coloured dots to press, which was not what was meant.)
 */
@Composable
internal fun LikesAura(likes: List<Track>, playerViewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val sample = remember(likes.size) { likes.take(SAMPLE_SIZE) }
    val swatches by produceState<List<Swatch>>(initialValue = emptyList(), sample) {
        value = withContext(Dispatchers.IO) { swatchesOf(sample) }
    }
    val scheme = MaterialTheme.colorScheme
    val palette = remember(swatches, scheme.primary, scheme.tertiary, scheme.secondary) {
        AuraPalette.of(swatches.map { it.color to it.tracks.size }, listOf(scheme.primary, scheme.tertiary, scheme.secondary))
    }
    // Lit by listening to the liked songs, that is, playing from this very list, not by any song that happens to be liked.
    val isLikedPlaying = playerViewModel.isPlaying && playerViewModel.currentContext?.navigationId == "likes"
    val intensity by animateFloatAsState(
        targetValue = if (isLikedPlaying) 1f else 0f,
        animationSpec = tween(if (isLikedPlaying) 900 else 1500),
        label = "likesAura",
    )
    // Nothing drawn, and nothing running, once it has faded out.
    if (intensity <= 0.01f) return

    val transition = rememberInfiniteTransition(label = "likesAuraDrift")
    // A whole turn, started over: the blobs go round by whole turns of it (see below), so the loop has no seam.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(AURA_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "likesAuraPhase",
    )
    AuraCanvas(palette, intensity, drift, modifier)
}

/** The aura itself: [drift] is a phase from 0 to a full turn, [intensity] how much of it is shown. */
@Composable
internal fun AuraCanvas(palette: AuraPalette, intensity: Float, drift: Float, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(AURA_HEIGHT)
            // Drawn in its own layer so the bottom can be faded out to nothing whatever the page behind is
            // made of, instead of painting a colour that has to match it.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val w = size.width
        val h = size.height
        // The mix, as a quiet wash from the very top.
        drawRect(Brush.verticalGradient(listOf(palette.mix.copy(alpha = 0.20f * intensity), Color.Transparent)))
        palette.colors.forEachIndexed { i, color ->
            val turns = AURA_TURNS[i % AURA_TURNS.size]
            val angle = drift * turns + i * 2.1f
            val centre = Offset(
                x = w * (0.16f + 0.34f * i) + cos(angle) * w * 0.10f,
                y = h * 0.22f + sin(angle) * h * 0.10f,
            )
            // A slow swell on each, so the colour breathes as well as moves.
            val swell = 0.85f + 0.15f * sin(drift * 2f + i)
            val radius = w * 0.55f
            val a = 0.34f * swell * intensity
            // Eased falloff, so no edge of the blob can be told apart.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = a),
                    0.35f to color.copy(alpha = a * 0.55f),
                    0.7f to color.copy(alpha = a * 0.15f),
                    1f to Color.Transparent,
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }
        // Gone by the bottom.
        drawRect(
            brush = Brush.verticalGradient(0f to Color.White, 0.45f to Color.White, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** The colours the aura is drawn in. Pure, so that what it makes of the covers and the theme can be tested. */
internal class AuraPalette(val colors: List<Color>, val mix: Color) {

    companion object {
        /**
         * @param covers the covers' colours with how many songs each stands for.
         * @param theme the theme's accents, which each cover colour is drawn a third of the way toward.
         */
        fun of(covers: List<Pair<Color, Int>>, theme: List<Color>): AuraPalette {
            val fallback = theme.take(3)
            if (covers.isEmpty()) return AuraPalette(fallback, fallback.firstOrNull() ?: Color.Gray)
            val chosen = covers.sortedByDescending { it.second }.take(3)
            val harmonised = chosen.mapIndexed { i, (color, _) -> lerp(color, theme[i % theme.size], THEME_PULL) }
            val total = chosen.sumOf { it.second }.coerceAtLeast(1).toFloat()
            val mix = harmonised.foldIndexed(Color(0f, 0f, 0f, 1f)) { i, acc, color ->
                val share = chosen[i].second / total
                Color(acc.red + color.red * share, acc.green + color.green * share, acc.blue + color.blue * share, 1f)
            }
            return AuraPalette(harmonised, mix)
        }

        /** How far a cover's colour is drawn toward the theme's. */
        const val THEME_PULL = 0.33f
    }
}

private val AURA_HEIGHT = 420.dp
private const val AURA_PERIOD_MS = 18_000

/** Whole turns of the drift for each blob, one backwards: the loop is seamless and they go their own ways. */
private val AURA_TURNS = intArrayOf(1, -1, 2)

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

package com.alananasss.kittytune.ui.player.mini

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.alananasss.kittytune.ui.player.ArtworkPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The same artwork and progress layers are used by both floating mini-player layouts. */
@Composable
internal fun MiniPlayerAppearance(
    artwork: String?,
    coverAsBackground: Boolean,
    progressFill: Boolean,
    progress: () -> Float,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val backdrop = artwork?.takeIf { coverAsBackground && it.isNotBlank() }
    val paletteSource = artwork?.takeIf { progressFill && backdrop == null && it.isNotBlank() }
    val lightSurface = scheme.surface.luminance() > 0.5f
    val artworkTint by produceState<Color?>(null, paletteSource, lightSurface) {
        value = null
        if (paletteSource != null) {
            value = withContext(Dispatchers.IO) {
                ArtworkPalette.dominantColorCached(paletteSource, preferLight = lightSurface)
            }
        }
    }
    val contentScheme = remember(scheme, backdrop != null) {
        if (backdrop == null) scheme else scheme.copy(
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.85f),
            primary = lerp(scheme.primary, Color.White, 0.65f),
            onPrimary = Color(0xFF17171B),
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().background(Color(0xFF17171B)),
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Black.copy(alpha = 0.62f),
                    )
                )
            )
        }
        if (progressFill) {
            val tint = if (backdrop != null) Color.White.copy(alpha = 0.20f)
                else lerp(scheme.surfaceContainerHigh, artworkTint ?: scheme.primary, 0.34f).copy(alpha = 0.72f)
            Canvas(Modifier.matchParentSize()) {
                val fraction = progress().coerceIn(0f, 1f)
                drawRect(tint, size = Size(size.width * fraction, size.height))
            }
        }
        MaterialTheme(colorScheme = contentScheme, content = content)
    }
}

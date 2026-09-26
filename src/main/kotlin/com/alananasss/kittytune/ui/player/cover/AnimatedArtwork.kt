package com.alananasss.kittytune.ui.player.cover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
fun AnimatedArtwork(
    artworkUrl: String?,
    animatedCoverUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    Box(modifier = modifier) {
        // Underneath everything: what shows while the cover loads, and for good when there is none
        // (local files without embedded art) or it fails. It used to be an empty black square.
        ArtworkPlaceholder(Modifier.fillMaxSize())

        // Base static artwork
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Animated cover video overlay
        if (!animatedCoverUrl.isNullOrBlank()) {
            CanvasVideo(
                canvasUrl = animatedCoverUrl,
                isPlaying = isPlaying,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** A note on the theme's container colour, sized to the box so it reads at 48 dp and at 400 dp. */
@Composable
private fun ArtworkPlaceholder(modifier: Modifier) {
    BoxWithConstraints(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(minOf(maxWidth, maxHeight) * 0.4f),
        )
    }
}

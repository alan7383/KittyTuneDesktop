package com.alananasss.kittytune.ui.player.cover

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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

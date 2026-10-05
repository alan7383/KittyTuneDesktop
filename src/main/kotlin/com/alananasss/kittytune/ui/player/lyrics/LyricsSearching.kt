package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str

/**
 * What a lyrics view shows while the words are still being looked up.
 *
 * The label is set in the lyrics font, at a lyric's weight, so the wait reads as part of the lyrics
 * view rather than a stray caption in the interface font (issue #66).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LyricsSearchingIndicator(
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
    large: Boolean = true,
) {
    val breathing = rememberInfiniteTransition(label = "lyricsSearching")
    val labelAlpha by breathing.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "lyricsSearchingAlpha",
    )
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContainedLoadingIndicator()
        Spacer(Modifier.height(if (large) 20.dp else 16.dp))
        Text(
            text = str("lyrics_searching"),
            style = (if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge)
                .copy(fontFamily = fontFamily, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
        )
    }
}

package com.alananasss.kittytune.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.wave.WaveMode
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * My Wave's card, the first thing on the home page (issue #66).
 *
 * One press plays music picked for this listener, without choosing anything; its four modes say what kind of mix it
 * is, and switching one while the wave plays changes what comes next, not the song on now.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MyWaveCard(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val isActive = vm.isMyWaveActive
    val isPlayingWave = isActive && vm.isPlaying

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().height(CARD_HEIGHT),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        str("wave_title"),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        str("wave_subtitle"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(16.dp))
                WavePlayButton(vm, isPlayingWave)
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isActive) NowInWave(vm)
                WaveModeRow(selected = vm.waveMode, onSelect = { vm.startMyWave(it) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WavePlayButton(vm: PlayerViewModel, isPlayingWave: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = {
                when {
                    vm.isWaveLoading -> Unit
                    vm.isMyWaveActive -> vm.togglePlayPause()
                    else -> vm.startMyWave()
                }
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(PLAY_SIZE),
        ) {
            AnimatedContent(
                targetState = when {
                    vm.isWaveLoading -> 0
                    isPlayingWave -> 1
                    else -> 2
                },
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "wavePlayIcon",
            ) { state ->
                when (state) {
                    0 -> ContainedLoadingIndicator(modifier = Modifier.size(36.dp))
                    1 -> Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(36.dp))
                    else -> Icon(Icons.Rounded.PlayArrow, contentDescription = str("wave_title"), modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

/** The song the wave is on, small, so the card shows the wave is playing and what. */
@Composable
private fun NowInWave(vm: PlayerViewModel) {
    val track = vm.currentTrack ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                str("wave_now_playing"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                listOfNotNull(track.title, track.displayArtist.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WaveModeRow(selected: WaveMode, onSelect: (WaveMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WaveMode.entries.forEach { mode ->
            WaveModeChip(mode, isSelected = mode == selected, onClick = { onSelect(mode) })
        }
    }
}

@Composable
private fun WaveModeChip(mode: WaveMode, isSelected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHighest.copy(alpha = 0.7f),
        label = "waveModeContainer",
    )
    val content by animateColorAsState(
        if (isSelected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        label = "waveModeContent",
    )
    val (icon, label) = waveModeVisual(mode)
    run {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = container,
            contentColor = content,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun waveModeVisual(mode: WaveMode): Pair<ImageVector, String> = when (mode) {
    WaveMode.BALANCED -> Icons.Rounded.AutoAwesome to str("wave_mode_balanced")
    WaveMode.FAVORITES -> Icons.Rounded.Favorite to str("wave_mode_favorites")
    WaveMode.DISCOVER -> Icons.Rounded.Explore to str("wave_mode_discover")
    WaveMode.POPULAR -> Icons.Rounded.TrendingUp to str("wave_mode_popular")
}

private val CARD_HEIGHT = 210.dp
private val PLAY_SIZE = 72.dp


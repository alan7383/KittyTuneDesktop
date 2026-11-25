package com.alananasss.kittytune.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.wave.WaveMode
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.PlayingBars
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * My Wave's card, the first thing on the home page (issue #66).
 *
 * One press plays music picked for this listener, without choosing anything; its four modes say what kind of mix it
 * is, and switching one while the wave plays changes what comes next, not the song on now.
 *
 * Built from Material 3 Expressive buttons only: a medium play button whose corners square off when pressed, a wide
 * tonal arcade button, and the four modes as big toggle tiles — a stadium while off, a soft square once chosen.
 * Nothing here moves on its own, so the card costs no frames while the page sits still.
 */
@Composable
internal fun MyWaveCard(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    MyWaveCardContent(
        mode = vm.waveMode,
        isActive = vm.isMyWaveActive,
        isPlaying = vm.isPlaying,
        isLoading = vm.isWaveLoading,
        track = vm.currentTrack,
        onPlay = {
            when {
                vm.isWaveLoading -> Unit
                vm.isMyWaveActive -> vm.togglePlayPause()
                else -> vm.startMyWave()
            }
        },
        onSelectMode = { vm.startMyWave(it) },
        modifier = modifier,
    )
}

/** The card itself, without the view model, so it can be previewed and rendered in tests. */
@Composable
internal fun MyWaveCardContent(
    mode: WaveMode,
    isActive: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    track: Track?,
    onPlay: () -> Unit,
    onSelectMode: (WaveMode) -> Unit,
    modifier: Modifier = Modifier,
    onArcade: () -> Unit = { com.alananasss.kittytune.ui.game.ArcadeLauncher.open() },
) {
    val isPlayingWave = isActive && isPlaying
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.padding(12.dp)) {
            if (maxWidth >= WIDE_MIN) {
                Row(Modifier.height(GRID_HEIGHT), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WaveIntro(
                        isActive, isPlayingWave, isLoading, track, onPlay, onArcade,
                        Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                    ModeGrid(mode, isPlayingWave, onSelectMode, Modifier.width(GRID_WIDTH).fillMaxHeight())
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WaveIntro(
                        isActive, isPlayingWave, isLoading, track, onPlay, onArcade,
                        Modifier.fillMaxWidth().height(184.dp).padding(start = 12.dp, top = 12.dp),
                    )
                    ModeStrip(mode, isPlayingWave, onSelectMode)
                }
            }
        }
    }
}

/** Title, what the wave is (or the song it is on), and the two actions. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WaveIntro(
    isActive: Boolean,
    isPlayingWave: Boolean,
    isLoading: Boolean,
    track: Track?,
    onPlay: () -> Unit,
    onArcade: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                str("wave_title"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            // While the wave is on, the song it is on takes the subtitle's place, so the card keeps its height.
            AnimatedContent(
                targetState = track.takeIf { isActive },
                contentKey = { it?.id },
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith fadeOut(tween(120))
                },
                label = "waveSubtitle",
            ) { current ->
                if (current != null) {
                    NowInWave(current)
                } else {
                    Text(
                        str("wave_subtitle"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val iconSize = ButtonDefaults.iconSizeFor(ACTION_HEIGHT)
            Button(
                onClick = onPlay,
                shapes = ButtonDefaults.shapesFor(ACTION_HEIGHT),
                contentPadding = ButtonDefaults.contentPaddingFor(ACTION_HEIGHT),
                modifier = Modifier.height(ACTION_HEIGHT),
            ) {
                Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                    when {
                        isLoading -> LoadingIndicator(color = scheme.onPrimary, modifier = Modifier.size(iconSize + 8.dp))
                        isPlayingWave -> Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.fillMaxSize())
                        else -> Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ACTION_HEIGHT)))
                Text(
                    if (isPlayingWave) str("action_pause") else str("action_play"),
                    style = ButtonDefaults.textStyleFor(ACTION_HEIGHT),
                    maxLines = 1,
                )
            }
            Tip(str("arcade_title")) {
                FilledTonalIconButton(
                    onClick = onArcade,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(width = ACTION_HEIGHT + 24.dp, height = ACTION_HEIGHT),
                ) {
                    Icon(Icons.Rounded.SportsEsports, contentDescription = str("arcade_title"), modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/** The four modes as a two-by-two grid of large toggle tiles, on a wide card. */
@Composable
private fun ModeGrid(selected: WaveMode, isPlayingWave: Boolean, onSelect: (WaveMode) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        WaveMode.entries.chunked(2).forEach { pair ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
                pair.forEach { mode ->
                    ModeTile(mode, mode == selected, isPlayingWave, onSelect, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** The same tiles in one scrolling row, for a narrow window. */
@Composable
private fun ModeStrip(selected: WaveMode, isPlayingWave: Boolean, onSelect: (WaveMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TILE_GAP),
    ) {
        WaveMode.entries.forEach { mode ->
            ModeTile(mode, mode == selected, isPlayingWave, onSelect, Modifier.width(136.dp).height(96.dp))
        }
    }
}

/**
 * One mode: a stadium while off, a soft square once chosen, and tighter still while held down — the toggle
 * button's own shape morph. The chosen one shows the playing bars in place of its icon while the wave plays.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeTile(mode: WaveMode, checked: Boolean, isPlayingWave: Boolean, onSelect: (WaveMode) -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (icon, label) = waveModeVisual(mode)
    ToggleButton(
        checked = checked,
        onCheckedChange = { if (!checked) onSelect(mode) },
        shapes = TILE_SHAPES,
        colors = ToggleButtonDefaults.colors(
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurfaceVariant,
            checkedContainerColor = scheme.primary,
            checkedContentColor = scheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        modifier = modifier.semantics { role = Role.RadioButton },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                if (checked && isPlayingWave) {
                    PlayingBars(true, Modifier.size(20.dp), color = scheme.onPrimary)
                } else {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The song the wave is on, small, so the card shows the wave is playing and what. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NowInWave(track: Track) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(scheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                str("wave_now_playing"),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                listOfNotNull(track.title, track.displayArtist.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

/** Off: a stadium. Pressed: tight corners. On: a soft square. The toggle button animates between them. */
private val TILE_SHAPES = ToggleButtonShapes(
    shape = RoundedCornerShape(50),
    pressedShape = RoundedCornerShape(14.dp),
    checkedShape = RoundedCornerShape(26.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ACTION_HEIGHT = ButtonDefaults.MediumContainerHeight
private val WIDE_MIN = 640.dp
private val GRID_WIDTH = 340.dp
private val GRID_HEIGHT = 212.dp
private val TILE_GAP = 8.dp

package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerBarStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.ui.common.ScrollableColumn
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.slider.PlayerSlider
import kotlin.math.roundToInt

/**
 * Dedicated Player Design Screen (issue #56).
 * Provides deep customization of the bottom playback bar:
 * - Shape: Standard (12dp), Rounded (20dp), Floating dock (24dp)
 * - Track Progress Slider: Bar, Wavy, Slim, Squiggly
 * - Volume Slider: Horizontal in bar vs Vertical hover popup
 * - Visible Buttons: like, lyrics, miniplayer, panel, queue, shuffle, repeat
 * - Seek wheel scrubbing sensitivity
 */
@Composable
fun PlayerDesignScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit
) {
    val prefs = remember { PlayerPreferences() }

    var playerBarStyle by remember { mutableStateOf(prefs.getPlayerBarStyle()) }
    var sliderStyle by remember { mutableStateOf(prefs.getPlayerSliderStyle()) }
    var verticalVolumeSlider by remember { mutableStateOf(prefs.getVerticalVolumeSlider()) }
    var playerBarButtons by remember { mutableStateOf(prefs.getPlayerBarButtons()) }
    var showLyricsButton by remember { mutableStateOf(prefs.getShowLyricsButtonEnabled()) }
    var seekWheelSeconds by remember { mutableFloatStateOf(prefs.getSeekWheelSeconds()) }

    SettingsScaffold(
        title = str("pref_player_design", "Design du lecteur"),
        onBackClick = onBackClick
    ) { innerPadding ->
        ScrollableColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 860.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. Live Interactive Player Preview Card
                PlayerLivePreviewCard(
                    playerViewModel = playerViewModel,
                    playerBarStyle = playerBarStyle,
                    sliderStyle = sliderStyle,
                    verticalVolume = verticalVolumeSlider,
                    visibleButtons = playerBarButtons,
                    showLyricsButton = showLyricsButton
                )

                // 2. Forme du lecteur (Player Shape)
                PlayerShapeSection(
                    currentStyle = playerBarStyle,
                    onSelect = {
                        playerBarStyle = it
                        prefs.setPlayerBarStyle(it)
                    }
                )

                // 3. Curseurs & Progression (Sliders)
                PlayerSlidersSection(
                    sliderStyle = sliderStyle,
                    onSliderStyleSelected = {
                        sliderStyle = it
                        prefs.setPlayerSliderStyle(it)
                    },
                    verticalVolumeSlider = verticalVolumeSlider,
                    onVolumeOrientationChanged = {
                        verticalVolumeSlider = it
                        prefs.setVerticalVolumeSlider(it)
                    }
                )

                // 4. Menu des boutons visibles (Player Buttons)
                PlayerButtonsSection(
                    visibleButtons = playerBarButtons,
                    showLyricsButton = showLyricsButton,
                    onToggle = { key, enabled ->
                        if (key == PlayerPreferences.PLAYER_BAR_BUTTON_LYRICS) {
                            showLyricsButton = enabled
                            prefs.setShowLyricsButtonEnabled(enabled)
                        }
                        val next = if (enabled) playerBarButtons + key else playerBarButtons - key
                        playerBarButtons = next
                        prefs.setPlayerBarButtons(next)
                    }
                )

                // 5. Options avancées de contrôle
                PlayerAdvancedSection(
                    seekWheelSeconds = seekWheelSeconds,
                    onSeekWheelChanged = {
                        seekWheelSeconds = it
                        prefs.setSeekWheelSeconds(it)
                    }
                )

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/**
 * Real-time mini preview of the bottom player bar reflecting chosen shape, sliders & buttons.
 */
@Composable
private fun PlayerLivePreviewCard(
    playerViewModel: PlayerViewModel,
    playerBarStyle: PlayerBarStyle,
    sliderStyle: PlayerSliderStyle,
    verticalVolume: Boolean,
    visibleButtons: Set<String>,
    showLyricsButton: Boolean
) {
    val track = playerViewModel.currentTrack
    val title = track?.title ?: "KittyTune Player"
    val artist = track?.displayArtist?.ifBlank { track.user?.username } ?: "Alan Walker, Au/Ra"

    val shape = when (playerBarStyle) {
        PlayerBarStyle.FLOATING -> RoundedCornerShape(24.dp)
        PlayerBarStyle.ROUNDED -> RoundedCornerShape(20.dp)
        PlayerBarStyle.DEFAULT -> RoundedCornerShape(12.dp)
    }

    val shadowElevation by animateDpAsState(
        targetValue = if (playerBarStyle == PlayerBarStyle.FLOATING) 8.dp else 0.dp,
        label = "previewShadow"
    )

    val border = if (playerBarStyle == PlayerBarStyle.FLOATING) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = str("player_preview_title", "Aperçu en temps réel"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = str("player_preview_sub", "Voyez vos changements immédiatement appliqués"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = when (playerBarStyle) {
                            PlayerBarStyle.FLOATING -> str("player_shape_floating", "Flottant")
                            PlayerBarStyle.ROUNDED -> str("player_shape_rounded", "Arrondi")
                            PlayerBarStyle.DEFAULT -> str("player_shape_default", "Standard")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Simulated Player Bar Dock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(
                        horizontal = if (playerBarStyle == PlayerBarStyle.FLOATING) 16.dp else 4.dp,
                        vertical = if (playerBarStyle == PlayerBarStyle.FLOATING) 12.dp else 4.dp
                    )
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = border,
                    shadowElevation = shadowElevation
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Cover + Title + Like
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (PlayerPreferences.PLAYER_BAR_BUTTON_LIKE in visibleButtons) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Center: Controls + Progress Slider
                        Column(
                            modifier = Modifier
                                .weight(1.4f)
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (PlayerPreferences.PLAYER_BAR_BUTTON_SHUFFLE in visibleButtons) {
                                    Icon(
                                        Icons.Filled.Shuffle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.SkipPrevious, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Pause, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (PlayerPreferences.PLAYER_BAR_BUTTON_REPEAT in visibleButtons) {
                                    Icon(
                                        Icons.Filled.Repeat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Interactive slider preview
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            ) {
                                Text("1:24", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PlayerSlider(
                                    value = 0.38f,
                                    onValueChange = {},
                                    onValueChangeFinished = {},
                                    sliderStyle = sliderStyle,
                                    isPlaying = true,
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                                )
                                Text("3:42", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Right: Optional Buttons & Volume
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (showLyricsButton && PlayerPreferences.PLAYER_BAR_BUTTON_LYRICS in visibleButtons) {
                                Icon(
                                    imageVector = Icons.Rounded.TextSnippet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            if (PlayerPreferences.PLAYER_BAR_BUTTON_MINIPLAYER in visibleButtons) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureInPictureAlt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            if (PlayerPreferences.PLAYER_BAR_BUTTON_PANEL in visibleButtons) {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            if (PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE in visibleButtons) {
                                Icon(
                                    imageVector = Icons.Outlined.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }

                            // Volume
                            if (verticalVolume) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.VolumeDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { 0.72f },
                                        modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("72%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section for selecting the Player Bar silhouette: Standard, Rounded, Floating.
 */
@Composable
private fun PlayerShapeSection(
    currentStyle: PlayerBarStyle,
    onSelect: (PlayerBarStyle) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = str("player_shape_title", "Forme du lecteur"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str("player_shape_desc", "Personnalisez la silhouette et l'intégration du lecteur au bas de la fenêtre"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShapeOptionCard(
                    title = str("player_shape_default", "Standard"),
                    subtitle = str("player_shape_default_desc", "Coins arrondis 12dp alignés aux panneaux"),
                    icon = Icons.Rounded.Splitscreen,
                    isSelected = currentStyle == PlayerBarStyle.DEFAULT,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(PlayerBarStyle.DEFAULT) }
                )

                ShapeOptionCard(
                    title = str("player_shape_rounded", "Arrondi"),
                    subtitle = str("player_shape_rounded_desc", "Bords adoucis modernes (20dp)"),
                    icon = Icons.Rounded.RoundedCorner,
                    isSelected = currentStyle == PlayerBarStyle.ROUNDED,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(PlayerBarStyle.ROUNDED) }
                )

                ShapeOptionCard(
                    title = str("player_shape_floating", "Flottant"),
                    subtitle = str("player_shape_floating_desc", "Dock flottant avec marges aérées (24dp)"),
                    icon = Icons.Rounded.Layers,
                    isSelected = currentStyle == PlayerBarStyle.FLOATING,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(PlayerBarStyle.FLOATING) }
                )
            }
        }
    }
}

@Composable
private fun ShapeOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        label = "shapeCardContainer"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Section for configuring progress bar slider styles and volume orientation.
 */
@Composable
private fun PlayerSlidersSection(
    sliderStyle: PlayerSliderStyle,
    onSliderStyleSelected: (PlayerSliderStyle) -> Unit,
    verticalVolumeSlider: Boolean,
    onVolumeOrientationChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Track Progress Sliders
            Column {
                Text(
                    text = str("pref_slider_style", "Style du curseur de lecture"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str("pref_slider_style_desc", "Personnalisez l'animation et le style de la barre de progression"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SliderStyleCard(
                    title = str("slider_style_bar", "Bar"),
                    style = PlayerSliderStyle.BAR,
                    isSelected = sliderStyle == PlayerSliderStyle.BAR,
                    modifier = Modifier.weight(1f),
                    onClick = { onSliderStyleSelected(PlayerSliderStyle.BAR) }
                )

                SliderStyleCard(
                    title = str("slider_style_wavy", "Wavy"),
                    style = PlayerSliderStyle.WAVY,
                    isSelected = sliderStyle == PlayerSliderStyle.WAVY,
                    modifier = Modifier.weight(1f),
                    onClick = { onSliderStyleSelected(PlayerSliderStyle.WAVY) }
                )

                SliderStyleCard(
                    title = str("slider_style_slim", "Slim"),
                    style = PlayerSliderStyle.SLIM,
                    isSelected = sliderStyle == PlayerSliderStyle.SLIM,
                    modifier = Modifier.weight(1f),
                    onClick = { onSliderStyleSelected(PlayerSliderStyle.SLIM) }
                )

                SliderStyleCard(
                    title = str("slider_style_squiggly", "Squiggly"),
                    style = PlayerSliderStyle.SQUIGGLY,
                    isSelected = sliderStyle == PlayerSliderStyle.SQUIGGLY,
                    modifier = Modifier.weight(1f),
                    onClick = { onSliderStyleSelected(PlayerSliderStyle.SQUIGGLY) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Volume Slider Mode (Horizontal vs Vertical)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = str("pref_volume_slider_title", "Curseur de volume"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (verticalVolumeSlider) {
                            str("pref_vertical_volume_slider_sub", "Afficher le curseur de volume verticalement au survol de l'icône")
                        } else {
                            str("pref_horizontal_volume_slider_sub", "Curseur de volume horizontal intégré directement dans le lecteur")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(16.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = !verticalVolumeSlider,
                        onClick = { onVolumeOrientationChanged(false) },
                        label = { Text(str("volume_horizontal", "Horizontal")) },
                        leadingIcon = {
                            Icon(Icons.Rounded.LinearScale, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    FilterChip(
                        selected = verticalVolumeSlider,
                        onClick = { onVolumeOrientationChanged(true) },
                        label = { Text(str("volume_vertical", "Vertical")) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Height, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderStyleCard(
    title: String,
    style: PlayerSliderStyle,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                PlayerSlider(
                    value = 0.45f,
                    onValueChange = {},
                    onValueChangeFinished = {},
                    sliderStyle = style,
                    isPlaying = true,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Section for enabling / disabling buttons on the bottom playback panel.
 */
@Composable
private fun PlayerButtonsSection(
    visibleButtons: Set<String>,
    showLyricsButton: Boolean,
    onToggle: (String, Boolean) -> Unit
) {
    val items = listOf(
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_LIKE,
            label = str("player_button_like", "Bouton J'aime"),
            desc = str("player_button_like_desc", "Affiche le cœur à côté de la pochette pour aimer le morceau"),
            icon = Icons.Filled.Favorite,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_LIKE in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_LYRICS,
            label = str("player_button_lyrics", "Bouton Paroles"),
            desc = str("player_button_lyrics_desc", "Ouvre instantanément les paroles synchronisées"),
            icon = Icons.Rounded.TextSnippet,
            enabled = showLyricsButton && PlayerPreferences.PLAYER_BAR_BUTTON_LYRICS in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_MINIPLAYER,
            label = str("mini_player_title", "Mini-lecteur"),
            desc = str("player_button_miniplayer_desc", "Basculer vers la fenêtre flottante Picture-in-Picture"),
            icon = Icons.Rounded.PictureInPictureAlt,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_MINIPLAYER in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_PANEL,
            label = str("player_button_panel", "Panneau de lecture"),
            desc = str("player_button_panel_desc", "Ouvre le volet latéral avec détails, paroles et effets"),
            icon = Icons.Outlined.Tune,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_PANEL in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE,
            label = str("player_button_queue", "File d'attente"),
            desc = str("player_button_queue_desc", "Accès direct à la liste des morceaux suivants"),
            icon = Icons.Outlined.QueueMusic,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_SHUFFLE,
            label = str("player_button_shuffle", "Lecture aléatoire"),
            desc = str("player_button_shuffle_desc", "Activer ou désactiver le mélange des titres"),
            icon = Icons.Filled.Shuffle,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_SHUFFLE in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_REPEAT,
            label = str("player_button_repeat", "Répétition"),
            desc = str("player_button_repeat_desc", "Répéter un morceau ou la liste de lecture"),
            icon = Icons.Filled.Repeat,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_REPEAT in visibleButtons
        ),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = str("player_buttons_title", "Boutons du lecteur"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str("player_buttons_desc", "Affichez ou masquez les boutons du lecteur selon vos envies"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEachIndexed { index, item ->
                    ButtonToggleRow(
                        item = item,
                        shape = when {
                            items.size == 1 -> RoundedCornerShape(16.dp)
                            index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                            index == items.size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                            else -> RoundedCornerShape(4.dp)
                        },
                        onToggle = { onToggle(item.key, it) }
                    )
                }
            }
        }
    }
}

private data class ButtonConfigItem(
    val key: String,
    val label: String,
    val desc: String,
    val icon: ImageVector,
    val enabled: Boolean
)

@Composable
private fun ButtonToggleRow(
    item: ButtonConfigItem,
    shape: androidx.compose.ui.graphics.Shape,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        onClick = { onToggle(!item.enabled) },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (item.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(38.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (item.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(12.dp))

            Switch(
                checked = item.enabled,
                onCheckedChange = onToggle
            )
        }
    }
}

/**
 * Advanced sensitivity & wheel settings.
 */
@Composable
private fun PlayerAdvancedSection(
    seekWheelSeconds: Float,
    onSeekWheelChanged: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = str("player_advanced_title", "Contrôles avancés"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str("player_advanced_desc", "Sensibilité du défilement avec la molette de la souris"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = str("pref_seek_wheel", "Saut par cran de molette"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${seekWheelSeconds.roundToInt()}s",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = seekWheelSeconds,
                    onValueChange = onSeekWheelChanged,
                    valueRange = 1f..30f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

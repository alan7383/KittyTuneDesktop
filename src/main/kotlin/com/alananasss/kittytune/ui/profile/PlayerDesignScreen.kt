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
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
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
    playerViewModel: PlayerViewModel? = null,
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
        title = str("pref_player_design"),
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
                // 1. Forme du lecteur (Player Shape)
                PlayerShapeSection(
                    currentStyle = playerBarStyle,
                    onSelect = {
                        playerBarStyle = it
                        prefs.setPlayerBarStyle(it)
                    }
                )

                // 2. Curseurs & Progression (Sliders)
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

                // 3. Menu des boutons visibles (Player Buttons)
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

                // 4. Options avancées de contrôle
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
                    text = str("player_shape_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str("player_shape_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShapeOptionCard(
                    title = str("player_shape_default"),
                    subtitle = str("player_shape_default_desc"),
                    icon = Icons.Rounded.Splitscreen,
                    isSelected = currentStyle == PlayerBarStyle.DEFAULT,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(PlayerBarStyle.DEFAULT) }
                )

                ShapeOptionCard(
                    title = str("player_shape_rounded"),
                    subtitle = str("player_shape_rounded_desc"),
                    icon = Icons.Rounded.RoundedCorner,
                    isSelected = currentStyle == PlayerBarStyle.ROUNDED,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(PlayerBarStyle.ROUNDED) }
                )

                ShapeOptionCard(
                    title = str("player_shape_floating"),
                    subtitle = str("player_shape_floating_desc"),
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
                    text = str("pref_slider_style"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str("pref_slider_style_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SliderStyleCard(
                    title = str("slider_style_bar"),
                    style = PlayerSliderStyle.BAR,
                    isSelected = sliderStyle == PlayerSliderStyle.BAR,
                    modifier = Modifier.weight(1f),
                    onClick = { onSliderStyleSelected(PlayerSliderStyle.BAR) }
                )

                SliderStyleCard(
                    title = str("slider_style_wavy"),
                    style = PlayerSliderStyle.WAVY,
                    isSelected = sliderStyle == PlayerSliderStyle.WAVY,
                    modifier = Modifier.weight(1f),
                    onClick = { onSliderStyleSelected(PlayerSliderStyle.WAVY) }
                )

                SliderStyleCard(
                    title = str("slider_style_slim"),
                    style = PlayerSliderStyle.SLIM,
                    isSelected = sliderStyle == PlayerSliderStyle.SLIM,
                    modifier = Modifier.weight(1f),
                    onClick = { onSliderStyleSelected(PlayerSliderStyle.SLIM) }
                )

                SliderStyleCard(
                    title = str("slider_style_squiggly"),
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
                        text = str("pref_volume_slider_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (verticalVolumeSlider) {
                            str("pref_vertical_volume_slider_sub")
                        } else {
                            str("pref_horizontal_volume_slider_sub")
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
                        label = { Text(str("volume_horizontal")) },
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
                        label = { Text(str("volume_vertical")) },
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
            label = str("player_button_like"),
            desc = str("player_button_like_desc"),
            icon = Icons.Filled.Favorite,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_LIKE in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_LYRICS,
            label = str("player_button_lyrics"),
            desc = str("player_button_lyrics_desc"),
            icon = Icons.AutoMirrored.Rounded.TextSnippet,
            enabled = showLyricsButton && PlayerPreferences.PLAYER_BAR_BUTTON_LYRICS in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_MINIPLAYER,
            label = str("mini_player_title"),
            desc = str("player_button_miniplayer_desc"),
            icon = Icons.Rounded.PictureInPictureAlt,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_MINIPLAYER in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_PANEL,
            label = str("player_button_panel"),
            desc = str("player_button_panel_desc"),
            icon = Icons.Outlined.Tune,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_PANEL in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE,
            label = str("player_button_queue"),
            desc = str("player_button_queue_desc"),
            icon = Icons.AutoMirrored.Outlined.QueueMusic,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_SHUFFLE,
            label = str("player_button_shuffle"),
            desc = str("player_button_shuffle_desc"),
            icon = Icons.Filled.Shuffle,
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_SHUFFLE in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_REPEAT,
            label = str("player_button_repeat"),
            desc = str("player_button_repeat_desc"),
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
                    text = str("player_buttons_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str("player_buttons_desc"),
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
                    text = str("player_advanced_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str("player_advanced_desc"),
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
                        text = str("pref_seek_wheel"),
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

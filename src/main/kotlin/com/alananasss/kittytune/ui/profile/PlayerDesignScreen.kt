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
import androidx.compose.material.icons.outlined.QueueMusic
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerBarStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.ui.common.ScrollableColumn
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
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
            Box(Modifier.fillMaxWidth().widthIn(max = 860.dp)) { PlayerDesignContent() }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** The player's shape, sliders, bar buttons and scroll step; shared by its own screen and the settings page. */
@Composable
fun PlayerDesignContent(modifier: Modifier = Modifier) {
    val prefs = remember { PlayerPreferences() }

    var playerBarStyle by remember { mutableStateOf(prefs.getPlayerBarStyle()) }
    var sliderStyle by remember { mutableStateOf(prefs.getPlayerSliderStyle()) }
    var verticalVolumeSlider by remember { mutableStateOf(prefs.getVerticalVolumeSlider()) }
    var volumeStyle by remember { mutableStateOf(prefs.getVolumeSliderStyle()) }
    var playerBarButtons by remember { mutableStateOf(prefs.getPlayerBarButtons()) }
    var showLyricsButton by remember { mutableStateOf(prefs.getShowLyricsButtonEnabled()) }
    var seekWheelSeconds by remember { mutableFloatStateOf(prefs.getSeekWheelSeconds()) }
    var showRemainingTime by remember { mutableStateOf(prefs.getShowRemainingTime()) }

    Column(
        modifier = modifier.fillMaxWidth(),
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

        // The floating bar's own shape, only while it is the chosen one.
        androidx.compose.animation.AnimatedVisibility(
            visible = playerBarStyle == PlayerBarStyle.FLOATING,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        ) {
            FloatingBarSection()
        }

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
            },
            volumeStyle = volumeStyle,
            onVolumeStyleSelected = {
                volumeStyle = it
                prefs.setVolumeSliderStyle(it)
            },
            showRemainingTime = showRemainingTime,
            onShowRemainingTimeChanged = {
                showRemainingTime = it
                prefs.setShowRemainingTime(it)
            },
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
    onVolumeOrientationChanged: (Boolean) -> Unit,
    volumeStyle: PlayerSliderStyle?,
    onVolumeStyleSelected: (PlayerSliderStyle?) -> Unit,
    showRemainingTime: Boolean,
    onShowRemainingTimeChanged: (Boolean) -> Unit,
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

            // Remaining Time toggle (-00:14 countdown vs total duration)
            Surface(
                onClick = { onShowRemainingTimeChanged(!showRemainingTime) },
                shape = RoundedCornerShape(16.dp),
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
                        color = if (showRemainingTime) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = if (showRemainingTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = str("pref_show_remaining_time"),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = str("pref_show_remaining_time_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Switch(
                        checked = showRemainingTime,
                        onCheckedChange = onShowRemainingTimeChanged
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // The volume control: where it sits, and its own style (or the seek bar's).
            Text(str("pref_volume_slider_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                VolumeOrientationCard(str("volume_horizontal"), vertical = false, isSelected = !verticalVolumeSlider, style = volumeStyle ?: sliderStyle, modifier = Modifier.weight(1f)) {
                    onVolumeOrientationChanged(false)
                }
                VolumeOrientationCard(str("volume_vertical"), vertical = true, isSelected = verticalVolumeSlider, style = volumeStyle ?: sliderStyle, modifier = Modifier.weight(1f)) {
                    onVolumeOrientationChanged(true)
                }
            }
            Text(str("volume_style_title"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                (listOf<PlayerSliderStyle?>(null) + PlayerSliderStyle.entries).forEach { option ->
                    VolumeStyleChip(
                        label = option?.let { str(sliderStyleKey(it)) } ?: str("volume_style_same"),
                        style = option ?: sliderStyle,
                        isSelected = volumeStyle == option,
                        modifier = Modifier.weight(1f),
                    ) { onVolumeStyleSelected(option) }
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
    // The icons are the ones the bar itself draws, so a row here is recognisable as that button.
    val items = listOf(
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_LIKE,
            label = str("player_button_like"),
            desc = str("player_button_like_desc"),
            mark = rememberVectorPainter(Icons.Filled.Favorite),
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_LIKE in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_LYRICS,
            label = str("player_button_lyrics"),
            desc = str("player_button_lyrics_desc"),
            // The bar draws this one from a file, so the list has to as well.
            mark = painterResource("icons/lyrics.svg"),
            enabled = showLyricsButton && PlayerPreferences.PLAYER_BAR_BUTTON_LYRICS in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_MINIPLAYER,
            label = str("mini_player_title"),
            desc = str("player_button_miniplayer_desc"),
            mark = rememberVectorPainter(Icons.Rounded.PictureInPictureAlt),
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_MINIPLAYER in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_PANEL,
            label = str("player_button_panel"),
            desc = str("player_button_panel_desc"),
            mark = rememberVectorPainter(Icons.Outlined.Tune),
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_PANEL in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE,
            label = str("player_button_queue"),
            desc = str("player_button_queue_desc"),
            mark = rememberVectorPainter(Icons.Outlined.QueueMusic),
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_SHUFFLE,
            label = str("player_button_shuffle"),
            desc = str("player_button_shuffle_desc"),
            mark = rememberVectorPainter(Icons.Filled.Shuffle),
            enabled = PlayerPreferences.PLAYER_BAR_BUTTON_SHUFFLE in visibleButtons
        ),
        ButtonConfigItem(
            key = PlayerPreferences.PLAYER_BAR_BUTTON_REPEAT,
            label = str("player_button_repeat"),
            desc = str("player_button_repeat_desc"),
            mark = rememberVectorPainter(Icons.Filled.Repeat),
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
                    SettingsItem(
                        shape = groupRowShape(items.size, index),
                        title = item.label,
                        subtitle = item.desc,
                        mark = item.mark,
                        hasSwitch = true,
                        switchState = item.enabled,
                        onSwitchChange = { onToggle(item.key, it) },
                    )
                }
            }
        }
    }
}

/**
 * One row of the player-bar button list.
 *
 * [mark] is a painter rather than a vector because one of these buttons is not a Material icon — the
 * bar draws its lyrics button from `icons/lyrics.svg` — and a settings list that draws a different
 * glyph from the thing it is configuring is worse than no glyph. Same shape as the mark on a
 * settings row and on a help card: one required value, both kinds through one path.
 */
private data class ButtonConfigItem(
    val key: String,
    val label: String,
    val desc: String,
    val mark: androidx.compose.ui.graphics.painter.Painter,
    val enabled: Boolean
)

/**
 * The corner treatment for a run of rows inside one card: rounded where the run begins and ends,
 * tucked where it meets its neighbour.
 */
private fun groupRowShape(count: Int, index: Int): androidx.compose.ui.graphics.Shape = when {
    count == 1 -> RoundedCornerShape(16.dp)
    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    index == count - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    else -> RoundedCornerShape(4.dp)
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


/** Screensaver toggle — dims the full-screen player after a period of inactivity. */
@Composable
private fun ScreensaverSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = str("pref_screensaver_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = str("pref_screensaver_desc"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            com.alananasss.kittytune.ui.common.SettingsSwitch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

private fun sliderStyleKey(style: PlayerSliderStyle): String = when (style) {
    PlayerSliderStyle.BAR -> "slider_style_bar"
    PlayerSliderStyle.WAVY -> "slider_style_wavy"
    PlayerSliderStyle.SLIM -> "slider_style_slim"
    PlayerSliderStyle.SQUIGGLY -> "slider_style_squiggly"
}

@Composable
private fun previewCardColors(isSelected: Boolean) =
    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)

@Composable
private fun previewCardBorder(isSelected: Boolean) = BorderStroke(
    if (isSelected) 2.dp else 1.dp,
    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
)

/** Horizontal or vertical volume, drawn as it will look: a speaker and a track, or a popup above a speaker. */
@Composable
private fun VolumeOrientationCard(
    title: String,
    vertical: Boolean,
    isSelected: Boolean,
    style: PlayerSliderStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(16.dp),
        color = previewCardColors(isSelected),
        border = previewCardBorder(isSelected),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (vertical) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            com.alananasss.kittytune.ui.main.VolumeTrackPreview(style, vertical = true, modifier = Modifier.height(64.dp).padding(horizontal = 4.dp, vertical = 6.dp))
                        }
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        com.alananasss.kittytune.ui.main.VolumeTrackPreview(style, vertical = false, modifier = Modifier.width(96.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("60%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** One volume style, previewed. */
@Composable
private fun VolumeStyleChip(label: String, style: PlayerSliderStyle, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(14.dp),
        color = previewCardColors(isSelected),
        border = previewCardBorder(isSelected),
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            com.alananasss.kittytune.ui.main.VolumeTrackPreview(style, vertical = false, modifier = Modifier.fillMaxWidth())
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}


/** An icon for each menu tile, so the list reads like the player's buttons above it. */
private fun menuTileIcon(id: String): ImageVector = when (id) {
    "like" -> Icons.Rounded.FavoriteBorder
    "shuffle" -> Icons.Rounded.Shuffle
    "repeat" -> Icons.Rounded.Repeat
    "play_next" -> Icons.Rounded.QueuePlayNext
    "add_queue" -> Icons.Rounded.AddToQueue
    "comments" -> Icons.Rounded.ChatBubbleOutline
    "repost" -> Icons.Rounded.Repeat
    "details" -> Icons.Rounded.Info
    "lyrics" -> Icons.Rounded.Lyrics
    "duet_lyrics_blacklist" -> Icons.Rounded.RecordVoiceOver
    "add_playlist" -> Icons.Rounded.PlaylistAdd
    "go_album" -> Icons.Rounded.Album
    "go_artist" -> Icons.Rounded.Person
    "edit_track" -> Icons.Rounded.Edit
    "track_radio" -> Icons.Rounded.Radio
    "share" -> Icons.Rounded.Share
    "remove_from_playlist" -> Icons.Rounded.RemoveCircleOutline
    "sleep_timer" -> Icons.Rounded.Bedtime
    "trim" -> Icons.Rounded.ContentCut
    "download" -> Icons.Rounded.Download
    "play" -> Icons.Rounded.PlayArrow
    else -> Icons.Rounded.Apps
}

/**
 * Which tiles the track or playlist menu shows, in the same look as the player's buttons: an icon on a circle
 * that is filled while the tile is on and grey while it is off.
 */
@Composable
internal fun MenuTilesSection(title: String, menu: String, catalogue: List<com.alananasss.kittytune.ui.main.MenuTiles.Tile>) {
    val prefs = remember { PlayerPreferences() }
    var hidden by remember(menu) { mutableStateOf(prefs.getHiddenMenuTiles(menu)) }
    val items = catalogue.map { tile ->
        ButtonConfigItem(
            key = tile.id,
            label = str(tile.labelKey),
            desc = "",
            mark = rememberVectorPainter(menuTileIcon(tile.id)),
            enabled = tile.id !in hidden,
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(str("menu_tiles_desc"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    prefs.resetMenuTiles(menu)
                    hidden = emptySet()
                }) { Text(str("menu_tiles_reset")) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEachIndexed { index, item ->
                    SettingsItem(
                        shape = groupRowShape(items.size, index),
                        title = item.label,
                        subtitle = item.desc,
                        mark = item.mark,
                        hasSwitch = true,
                        switchState = item.enabled,
                        onSwitchChange = { on ->
                            hidden = if (on) hidden - item.key else hidden + item.key
                            prefs.setHiddenMenuTiles(menu, hidden)
                        },
                    )
                }
            }
        }
    }
}


/** Corner, width, distance from the bottom and see-through, for the floating bar. */
@Composable
private fun FloatingBarSection() {
    val prefs = remember { PlayerPreferences() }
    var look by remember { mutableStateOf(prefs.getFloatingBarLook()) }
    fun update(next: com.alananasss.kittytune.data.local.FloatingBarLook) {
        look = next
        prefs.setFloatingBarLook(next)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str("floating_bar_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { update(com.alananasss.kittytune.data.local.FloatingBarLook.DEFAULT) }) { Text(str("btn_reset")) }
            }
            // A live miniature of the bar with the chosen corner and width.
            Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape((look.cornerDp * 0.5f).dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (look.isTranslucent) 0.85f else 1f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth(look.widthPercent / 100f * 0.92f).height(36.dp),
                ) {}
            }
            FloatingSlider(str("floating_bar_corner"), "${look.cornerDp} dp", look.cornerDp.toFloat(), 0f..40f) { update(look.copy(cornerDp = it.toInt())) }
            FloatingSlider(str("floating_bar_width"), "${look.widthPercent} %", look.widthPercent.toFloat(), 50f..100f) { update(look.copy(widthPercent = it.toInt())) }
            FloatingSlider(str("floating_bar_margin"), "${look.marginDp} dp", look.marginDp.toFloat(), 4f..40f) { update(look.copy(marginDp = it.toInt())) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { update(look.copy(isTranslucent = !look.isTranslucent)) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(str("floating_bar_translucent"), style = MaterialTheme.typography.bodyLarge)
                    Text(str("floating_bar_translucent_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.alananasss.kittytune.ui.common.SettingsSwitch(checked = look.isTranslucent, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun FloatingSlider(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        com.alananasss.kittytune.ui.common.Slider(value = current, onValueChange = onChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

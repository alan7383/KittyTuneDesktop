package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import kotlinx.coroutines.delay

/**
 * Desktop port of the Android AudioControlDock (effects sheet): speed/pitch,
 * bass boost (+earrape), 8D, muffled, reverb, rain.
 *
 * Desktop adaptation: long-press on a tile becomes right-click to open the
 * intensity dialog; left-click toggles the effect.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsPanel(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val isPrecise = viewModel.isPreciseSpeedEnabled
    var showRainVolumeDialog by remember { mutableStateOf(false) }
    var showBassBoostDialog by remember { mutableStateOf(false) }
    var showEightDDialog by remember { mutableStateOf(false) }
    var showMuffledDialog by remember { mutableStateOf(false) }
    var showReverbDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // Speed + pitch card
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${viewModel.effectsState.speed}x",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                val isPitchActive = viewModel.effectsState.isPitchEnabled
                val pitchContainerColor by animateColorAsState(
                    targetValue = if (isPitchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    label = "pitchContainer"
                )
                val pitchContentColor by animateColorAsState(
                    targetValue = if (isPitchActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    label = "pitchContent"
                )
                Surface(
                    onClick = { viewModel.togglePitchEnabled(!isPitchActive) },
                    shape = CircleShape,
                    color = pitchContainerColor,
                    border = if (isPitchActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    contentColor = pitchContentColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(visible = isPitchActive) {
                            Row {
                                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                        Text(str("player_pitch"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Slider(
                value = viewModel.effectsState.speed,
                onValueChange = { viewModel.setCustomSpeed(it) },
                valueRange = 0.5f..2.0f,
                steps = if (isPrecise) 29 else 14,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            str("player_special_effects"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        )
        FlowRow(
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val itemModifier = Modifier.weight(1f).fillMaxWidth()
            FxTile(str("effect_bass_boost"), Icons.Rounded.Bolt, viewModel.effectsState.isBassBoostEnabled, { viewModel.toggleBassBoost() }, { showBassBoostDialog = true }, itemModifier)
            FxTile(str("effect_8d"), Icons.Rounded.SurroundSound, viewModel.effectsState.is8DEnabled, { viewModel.toggle8D() }, { showEightDDialog = true }, itemModifier, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
            FxTile(str("effect_muffled"), Icons.Rounded.BlurOn, viewModel.effectsState.isMuffledEnabled, { viewModel.toggleMuffled() }, { showMuffledDialog = true }, itemModifier, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
            FxTile(str("effect_reverb"), Icons.Rounded.GraphicEq, viewModel.effectsState.isReverbEnabled, { viewModel.toggleReverb() }, { showReverbDialog = true }, itemModifier)
            FxTile(str("effect_rain"), Icons.Rounded.WaterDrop, viewModel.effectsState.isRainEnabled, { viewModel.toggleRain() }, { showRainVolumeDialog = true }, itemModifier, Color(0xFF81D4FA), Color(0xFF004BA0))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            str("desktop_fx_right_click_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(24.dp))

        if (showRainVolumeDialog) {
            IntensityDialog(
                icon = Icons.Rounded.WaterDrop,
                title = str("effect_rain"),
                label = str("label_volume", (viewModel.effectsState.rainVolume * 100).toInt()),
                value = viewModel.effectsState.rainVolume,
                onValueChange = {
                    viewModel.setRainVolume(it)
                    if (!viewModel.effectsState.isRainEnabled) viewModel.toggleRain()
                },
                onDismiss = { showRainVolumeDialog = false }
            )
        }
        if (showEightDDialog) {
            IntensityDialog(
                icon = Icons.Rounded.SurroundSound,
                title = str("effect_8d"),
                label = str("label_speed_8d", (viewModel.effectsState.eightDSpeed * 100).toInt()),
                value = viewModel.effectsState.eightDSpeed,
                onValueChange = {
                    viewModel.setEightDSpeed(it)
                    if (!viewModel.effectsState.is8DEnabled) viewModel.toggle8D()
                },
                onDismiss = { showEightDDialog = false }
            )
        }
        if (showMuffledDialog) {
            IntensityDialog(
                icon = Icons.Rounded.BlurOn,
                title = str("effect_muffled"),
                label = str("label_cutoff", (viewModel.effectsState.muffledIntensity * 100).toInt()),
                value = viewModel.effectsState.muffledIntensity,
                onValueChange = {
                    viewModel.setMuffledIntensity(it)
                    if (!viewModel.effectsState.isMuffledEnabled) viewModel.toggleMuffled()
                },
                onDismiss = { showMuffledDialog = false }
            )
        }
        if (showReverbDialog) {
            IntensityDialog(
                icon = Icons.Rounded.GraphicEq,
                title = str("effect_reverb"),
                label = str("label_intensity", (viewModel.effectsState.reverbIntensity * 100).toInt()),
                value = viewModel.effectsState.reverbIntensity,
                onValueChange = {
                    viewModel.setReverbIntensity(it)
                    if (!viewModel.effectsState.isReverbEnabled) viewModel.toggleReverb()
                },
                onDismiss = { showReverbDialog = false }
            )
        }
        if (showBassBoostDialog) {
            var showEarrapeWarning by remember { mutableStateOf(false) }
            AlertDialog(
                onDismissRequest = { showBassBoostDialog = false },
                icon = { Icon(Icons.Rounded.Bolt, null) },
                title = { Text(str("effect_bass_boost")) },
                text = {
                    Column {
                        val isEarrape = viewModel.effectsState.isEarrapeEnabled
                        Text(
                            str("label_intensity", (viewModel.effectsState.bassBoostIntensity * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = viewModel.effectsState.bassBoostIntensity,
                            onValueChange = {
                                viewModel.setBassBoostIntensity(it)
                                if (!viewModel.effectsState.isBassBoostEnabled) viewModel.toggleBassBoost()
                            },
                            valueRange = 0f..1f
                        )
                        Spacer(Modifier.height(24.dp))
                        val iconScale by animateFloatAsState(
                            targetValue = if (isEarrape) 1.25f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioHighBouncy,
                                stiffness = Spring.StiffnessMedium
                            ), label = "earrapeIconScale"
                        )
                        FilledTonalButton(
shape = RoundedCornerShape(20.dp),
                            onClick = {
                                if (!viewModel.hasSeenEarrapeWarning()) {
                                    showEarrapeWarning = true
                                } else {
                                    viewModel.toggleEarrape()
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally).height(48.dp).padding(horizontal = 8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isEarrape) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                contentColor = if (isEarrape) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = if (isEarrape) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(str("btn_earrape"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                },
                confirmButton = { TextButton(
onClick = { showBassBoostDialog = false }) { Text(str("btn_ok")) } }
            )
            if (showEarrapeWarning) {
                var countdown by remember { mutableStateOf(5) }
                LaunchedEffect(Unit) {
                    while (countdown > 0) {
                        delay(1000)
                        countdown--
                    }
                }
                AlertDialog(
                    onDismissRequest = { showEarrapeWarning = false },
                    title = { Text(str("warning_title")) },
                    text = { Text(str("earrape_warning")) },
                    confirmButton = {
                        TextButton(

                            onClick = {
                                viewModel.setHasSeenEarrapeWarning(true)
                                viewModel.toggleEarrape()
                                showEarrapeWarning = false
                            },
                            enabled = countdown == 0
                        ) {
                            Text(if (countdown > 0) "${str("btn_ok")} (${countdown}s)" else str("btn_ok"))
                        }
                    },
                    dismissButton = {
                        TextButton(
onClick = { showEarrapeWarning = false }) { Text(str("btn_cancel")) }
                    }
                )
            }
        }
    }
}

@Composable
private fun IntensityDialog(
    icon: ImageVector,
    title: String,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(title) },
        text = {
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
                Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f)
            }
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text(str("btn_ok")) } }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FxTile(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(300), label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeContentColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300), label = "contentColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "iconScale"
    )

    FilledTonalButton(
        shapes = ButtonDefaults.shapes(),
        onClick = onClick,
        modifier = modifier.height(76.dp).onPointerEvent(PointerEventType.Press) {
            if (it.buttons.isSecondaryPressed) onSecondaryClick()
        },
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

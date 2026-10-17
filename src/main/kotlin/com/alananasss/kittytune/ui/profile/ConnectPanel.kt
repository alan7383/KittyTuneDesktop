package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.data.sync.ConnectPlatform
import com.alananasss.kittytune.data.sync.SyncPeers
import com.alananasss.kittytune.ui.common.pressScale

@Composable
fun ConnectPanel() {
    val peers by ConnectManager.peers.collectAsState()
    val feedback by ConnectManager.feedback.collectAsState()
    val selected by ConnectManager.selectedDevice.collectAsState()
    val busy by ConnectManager.transferring.collectAsState()
    val independent by ConnectManager.independent.collectAsState()

    var url by remember { mutableStateOf(ConnectManager.relayUrl) }
    var error by remember { mutableStateOf("") }
    var localRelay by remember { mutableStateOf(ConnectPlatform.localRelayUrl.isNotBlank()) }
    var showSettings by remember { mutableStateOf(false) }
    var showPairDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ConnectManager.refresh() }

    if (showPairDialog) {
        PairDeviceDialog(
            onDismiss = { showPairDialog = false },
            onPaired = {
                showPairDialog = false
                ConnectManager.refresh()
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = str("connect_available_devices"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = str("connect_devices_subtitle"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { showSettings = !showSettings },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = if (showSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = str("connect_settings"),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Expandable relay / connection settings
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = str("connect_relay_title"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Text(
                        text = str("connect_relay_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = localRelay,
                            onCheckedChange = { value ->
                                localRelay = value
                                ConnectPlatform.localRelayUrl = if (value) "ws://127.0.0.1:8787" else ""
                                ConnectManager.relayUrl = ConnectManager.relayUrl
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = str("connect_relay_local"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            error = ""
                        },
                        singleLine = true,
                        label = { Text(str("connect_relay_url")) },
                        placeholder = { Text(str("connect_relay_url_hint")) },
                        isError = error.isNotBlank(),
                        supportingText = if (error.isNotBlank()) {
                            { Text(error, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier.fillMaxWidth().trackTextInput(),
                    )

                    Button(
                        onClick = {
                            runCatching { ConnectManager.relayUrl = url }.onFailure {
                                error = str("connect_relay_url_error")
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(str("connect_save"))
                    }
                }
            }
        }

        // Current device (This device / Local playback)
        DeviceChoiceCard(
            name = str("connect_this_device"),
            status = if (independent) str("connect_independent_playback") else str("connect_local_playback"),
            icon = Icons.Rounded.Speaker,
            isSelected = selected == null,
            isEnabled = !busy && (!independent || selected != null),
            isOnline = true,
            onClick = {
                if (!independent && selected != null) ConnectManager.switchOutput(null)
            },
        )

        // Paired devices list or Empty State
        val known = remember(peers) { SyncPeers.all() }
        if (known.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Rounded.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = str("connect_no_devices"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = str("connect_no_devices_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { showPairDialog = true },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(str("connect_pair_device"))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                known.sortedByDescending { peers[it.deviceId]?.connected == true }.forEach { peer ->
                    val live = peers[peer.deviceId]
                    val isOnline = live?.connected == true
                    val status = when {
                        independent -> str("connect_disable_independent_hint")
                        !isOnline -> str("connect_offline")
                        live.transport == "LAN" -> str("connect_lan")
                        else -> str("connect_internet")
                    }
                    DeviceChoiceCard(
                        name = peer.label,
                        status = status,
                        icon = if (peer.platform == "android") Icons.Rounded.PhoneAndroid else Icons.Rounded.Computer,
                        isSelected = selected == peer.deviceId,
                        isEnabled = !busy && !independent && isOnline,
                        isOnline = isOnline,
                        onClick = {
                            ConnectManager.switchOutput(peer.deviceId)
                        },
                    )
                }

                // Add device button below list
                OutlinedButton(
                    onClick = { showPairDialog = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("connect_pair_device"))
                }
            }
        }

        // Connection error alert
        val connectionError = peers.values.firstOrNull { !it.connected && it.error.isNotBlank() }?.error
        if (!independent && connectionError != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (connectionError.startsWith("HTTP")) {
                            connectionError
                        } else {
                            connectionError
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // Transferring progress
        if (busy) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = str("connect_moving_playback"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // Feedback error if any
        if (feedback.isNotBlank() && feedback != "✓") {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = friendlyFeedback(feedback),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Play independently toggle card (MicroG-style M3 SwitchBar)
        val cardBg by animateColorAsState(
            targetValue = if (independent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            label = "cardBg",
        )
        val titleColor by animateColorAsState(
            targetValue = if (independent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            label = "titleColor",
        )
        val descColor by animateColorAsState(
            targetValue = if (independent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            label = "descColor",
        )

        val switchInteractionSource = remember { MutableInteractionSource() }

        Surface(
            onClick = { ConnectManager.setIndependent(!independent) },
            enabled = !busy,
            shape = RoundedCornerShape(48.dp),
            color = cardBg,
            interactionSource = switchInteractionSource,
            modifier = Modifier
                .fillMaxWidth()
                .pointerHoverIcon(if (busy) PointerIcon.Default else PointerIcon.Hand),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = str("connect_play_independently"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = titleColor,
                    )
                    Text(
                        text = str("connect_play_independently_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = descColor,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = independent,
                    enabled = !busy,
                    onCheckedChange = null,
                    interactionSource = switchInteractionSource,
                    thumbContent = {
                        Icon(
                            imageVector = if (independent) Icons.Rounded.Check else Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                            tint = if (independent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }
        }
    }
}

@Composable
private fun DeviceChoiceCard(
    name: String,
    status: String,
    icon: ImageVector,
    isSelected: Boolean,
    isEnabled: Boolean,
    isOnline: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        isHovered && isEnabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val border = if (isSelected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    } else null

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = border,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .hoverable(interactionSource)
            .pointerHoverIcon(if (isEnabled) PointerIcon.Hand else PointerIcon.Default),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOnline) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = str("connect_active"),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else if (isEnabled) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun friendlyFeedback(message: String): String = when {
    message.contains("Timed out") -> str("connect_feedback_timeout")
    message == "Device is offline" || message == "Source is unavailable" -> str("connect_feedback_offline")
    message == "Automatic transfer cancelled" -> str("connect_feedback_cancelled")
    message == "Local files are unavailable on another device" -> str("connect_feedback_local_only")
    message == "Track changed; choose the device again" -> str("connect_feedback_track_changed")
    message == "Unable to prepare this track" -> str("connect_feedback_unable_to_open")
    else -> message
}

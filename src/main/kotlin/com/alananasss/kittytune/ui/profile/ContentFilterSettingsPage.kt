@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.alananasss.kittytune.ui.common.ScrollableColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.audio.ai.AiDetectionManager
import com.alananasss.kittytune.audio.ai.GpuDetector
import com.alananasss.kittytune.audio.ai.GpuDevice
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.data.local.AiDetectionWindow
import com.alananasss.kittytune.data.local.AiModelType
import com.alananasss.kittytune.data.local.BlockedContent
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsHighlightManager
import com.alananasss.kittytune.ui.common.SettingsItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Misc → Filtered Content: Desktop port of Android's BlockedContentSettingsScreen.
 *
 * Provides a desktop-tailored layout with:
 * - Segmented tabs for AI Music Detector, Hidden Tracks, and Blocked Artists with live counts.
 * - ArtifactNet Neural Model status card with offline ready badges and XNNPACK acceleration details.
 * - Sensitivity threshold slider with quick-preset chips (Sensitive, Balanced, Strict).
 * - Full-text search and reason filter chips (All, Manual, AI-detected) for blocked lists.
 * - Rich desktop cards with covers/avatars, platform sources, and block dates.
 * - Reversible unblock actions and clear-all confirmation dialogs.
 */
@Composable
fun ContentFilterSettingsPage() {
    val prefs = remember { PlayerPreferences() }

    val currentModel by AiDetectionManager.currentModelFlow.collectAsState()
    val modelDownloadState by AiDetectionManager.downloadState.collectAsState()
    val activeAcceleration by AiDetectionManager.activeAccelerationProvider.collectAsState()

    var aiAutoSkip by remember { mutableStateOf(prefs.aiAutoSkip) }
    var aiAutoBlock by remember { mutableStateOf(prefs.aiAutoBlock) }
    var aiSpareFavorites by remember { mutableStateOf(prefs.aiSpareFavorites) }
    var aiThreshold by remember { mutableStateOf(prefs.aiScoreThreshold) }
    var aiShowBadge by remember { mutableStateOf(prefs.aiShowBadge) }
    var aiShowHumanBadge by remember { mutableStateOf(prefs.aiShowHumanBadge) }
    var aiWindow by remember { mutableStateOf(prefs.aiDetectionWindow) }

    var gpuAccelerationEnabled by remember { mutableStateOf(prefs.aiGpuAccelerationEnabled) }
    var selectedGpuId by remember { mutableStateOf(prefs.aiSelectedGpuId) }
    val detectedGpus = remember { GpuDetector.getDetectedGpus() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var reasonFilter by remember { mutableIntStateOf(0) } // 0 = All, 1 = Manual, 2 = AI

    var showAiWindowDialog by remember { mutableStateOf(false) }
    var showDownloadPromptDialog by remember { mutableStateOf(false) }
    var pendingEnableFeature by remember { mutableStateOf<String?>(null) }
    var showDeleteModelDialog by remember { mutableStateOf(false) }
    var showGpuSelectionDialog by remember { mutableStateOf(false) }

    val blockedTracks by BlockManager.observeBlockedTracks().collectAsState(initial = emptyList())
    val blockedArtists by BlockManager.observeBlockedArtists().collectAsState(initial = emptyList())

    var trackToUnblock by remember { mutableStateOf<BlockedContent?>(null) }
    var artistToUnblock by remember { mutableStateOf<BlockedContent?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    // Synchronize active tab with settings search highlights
    LaunchedEffect(Unit) {
        val currentKey = SettingsHighlightManager.highlightKey
        if (currentKey != null) {
            when {
                currentKey.startsWith("ai_") || currentKey.startsWith("block_model") || currentKey == "block_section_ai" -> {
                    selectedTab = 0
                }
                currentKey == "blocked_content_list" || currentKey == "block_section_tracks" -> {
                    selectedTab = 1
                }
                currentKey == "block_section_artists" -> {
                    selectedTab = 2
                }
            }
        }
    }

    // ─── Dialogs ─────────────────────────────────────────────────────────────

    if (trackToUnblock != null) {
        EscapableAlertDialog(
            onDismissRequest = { trackToUnblock = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("block_unblock_track_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = str("block_unblock_track_body", trackToUnblock!!.trackTitle ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        trackToUnblock?.trackId?.let { BlockManager.unblockTrack(it) }
                        trackToUnblock = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(str("block_restore")) }
            },
            dismissButton = {
                TextButton(
                    onClick = { trackToUnblock = null },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(str("btn_cancel")) }
            },
        )
    }

    if (artistToUnblock != null) {
        EscapableAlertDialog(
            onDismissRequest = { artistToUnblock = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("block_unblock_artist_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = str("block_unblock_artist_body", artistToUnblock!!.artistName ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        artistToUnblock?.artistId?.let { BlockManager.unblockArtist(it) }
                        artistToUnblock = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(str("block_restore")) }
            },
            dismissButton = {
                TextButton(
                    onClick = { artistToUnblock = null },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(str("btn_cancel")) }
            },
        )
    }

    if (showClearAllDialog) {
        val totalCount = blockedTracks.size + blockedArtists.size
        EscapableAlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("block_clear_all_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = str("block_clear_all_body"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${blockedTracks.size} tracks, ${blockedArtists.size} artists ($totalCount total)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        blockedTracks.forEach { it.trackId?.let { id -> BlockManager.unblockTrack(id) } }
                        blockedArtists.forEach { it.artistId?.let { id -> BlockManager.unblockArtist(id) } }
                        showClearAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(str("block_clear_all_confirm")) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearAllDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(str("btn_cancel")) }
            },
        )
    }

    if (showDownloadPromptDialog) {
        val targetModel = currentModel
        val isTargetReady = AiDetectionManager.isModelReady(targetModel)

        EscapableAlertDialog(
            onDismissRequest = {
                showDownloadPromptDialog = false
                pendingEnableFeature = null
            },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("block_model_download_prompt_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = str("block_model_download_prompt_body"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                ) {
                                    Text(
                                        text = targetModel.runtimeTag,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = formatModelSize(targetModel.totalBytes),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                if (isTargetReady) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            text = str("block_model_status_ready"),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = str(targetModel.descKey),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!isTargetReady) {
                            AiDetectionManager.downloadModel(targetModel)
                        } else {
                            if (pendingEnableFeature == "block") {
                                aiAutoBlock = true
                                prefs.aiAutoBlock = true
                            } else {
                                aiAutoSkip = true
                                prefs.aiAutoSkip = true
                            }
                        }
                        showDownloadPromptDialog = false
                        pendingEnableFeature = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = if (isTargetReady) Icons.Rounded.Check else Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isTargetReady) {
                            str("block_model_install_btn")
                        } else {
                            "${str("block_model_download_btn")} (${formatModelSize(targetModel.totalBytes)})"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDownloadPromptDialog = false
                        pendingEnableFeature = null
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(str("btn_cancel")) }
            },
        )
    }

    if (showDeleteModelDialog) {
        val modelName = str(currentModel.titleKey)
        val modelSize = formatModelSize(currentModel.totalBytes)
        EscapableAlertDialog(
            onDismissRequest = { showDeleteModelDialog = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("block_model_delete_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = str("block_model_delete_prompt", modelName, modelSize),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        AiDetectionManager.deleteModel(currentModel)
                        aiAutoSkip = false
                        prefs.aiAutoSkip = false
                        aiAutoBlock = false
                        prefs.aiAutoBlock = false
                        showDeleteModelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(str("block_model_delete_btn")) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteModelDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) { Text(str("btn_cancel")) }
            },
        )
    }

    if (showGpuSelectionDialog) {
        val allOptions = listOf(GpuDevice.AUTO) + detectedGpus + listOf(GpuDevice.CPU)
        EscapableAlertDialog(
            onDismissRequest = { showGpuSelectionDialog = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DeveloperBoard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("block_gpu_select_dialog_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                ScrollableColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (option in allOptions) {
                        val isSelected = selectedGpuId == option.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedGpuId = option.id
                                    prefs.aiSelectedGpuId = option.id
                                    AiDetectionManager.reloadHardwareAcceleration()
                                    showGpuSelectionDialog = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    ),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = when (option.id) {
                                                GpuDevice.ID_AUTO -> str("block_gpu_device_auto")
                                                GpuDevice.ID_CPU -> str("block_gpu_device_cpu")
                                                else -> option.name
                                            },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (!option.isSpecial) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (option.isDiscrete) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            ) {
                                                Text(
                                                    text = if (option.isDiscrete) str("block_gpu_discrete") else str("block_gpu_integrated"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (option.isDiscrete) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    if (option.driverInfo.isNotBlank() || option.apiSupport.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = listOf(option.driverInfo, option.apiSupport).filter { it.isNotBlank() }.joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showGpuSelectionDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(str("btn_cancel"))
                }
            },
        )
    }

    if (showAiWindowDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showAiWindowDialog = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("block_ai_window_dialog_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (option in AiDetectionWindow.entries) {
                        val isSelected = aiWindow == option
                        val containerColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            label = "aiWindowContainer",
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            },
                            label = "aiWindowBorder",
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    aiWindow = option
                                    prefs.aiDetectionWindow = option
                                    showAiWindowDialog = false
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = containerColor,
                            border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    ),
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = str(option.titleKey),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = str(option.descKey),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 18.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAiWindowDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        text = str("btn_cancel"),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            },
        )
    }

    // ─── Main Content ────────────────────────────────────────────────────────

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Desktop segmented tabs
        ExpressiveConnectedButtonGroup(
            options = listOf(0, 1, 2),
            selectedOption = selectedTab,
            onOptionSelected = { selectedTab = it },
            modifier = Modifier.fillMaxWidth(),
            fillMaxWidth = true,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            uncheckedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            uncheckedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconProvider = { index ->
                val icon = when (index) {
                    0 -> Icons.Rounded.SmartToy
                    1 -> Icons.Rounded.VisibilityOff
                    2 -> Icons.Rounded.PersonOff
                    else -> Icons.Rounded.SmartToy
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            labelProvider = { index ->
                when (index) {
                    0 -> Text(
                        text = str("block_tab_ai"),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    1 -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = str("block_tab_tracks"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (blockedTracks.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    "${blockedTracks.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    2 -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = str("block_tab_artists"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (blockedArtists.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = if (selectedTab == 2) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedTab == 2) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    "${blockedArtists.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            },
        )

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(140))
            },
            label = "FilterTabsContent",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                    // ─── TAB 0: AI Music Detector ─────────────────────────────
                    ScrollableColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // AI Neural Model Architecture & Download Card
                            AiModelCard(
                                currentModel = currentModel,
                                downloadState = modelDownloadState,
                                activeAcceleration = activeAcceleration,
                                onDownload = { AiDetectionManager.downloadModel(currentModel) },
                                onDelete = { showDeleteModelDialog = true },
                            )

                            // 2. GPU Hardware Acceleration (ONNX Runtime)
                            GpuHardwareAccelerationSection(
                                gpuEnabled = gpuAccelerationEnabled,
                                onGpuEnabledChange = { enabled ->
                                    gpuAccelerationEnabled = enabled
                                    prefs.aiGpuAccelerationEnabled = enabled
                                    AiDetectionManager.reloadHardwareAcceleration()
                                },
                                selectedGpuId = selectedGpuId,
                                detectedGpus = detectedGpus,
                                activeAcceleration = activeAcceleration,
                                onSelectGpuClick = { showGpuSelectionDialog = true },
                            )

                            SettingsGroupTitle(str("block_section_ai"))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Block 1: Auto-skip group
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val autoSkipBottomRadius by animateDpAsState(
                                        targetValue = if (aiAutoSkip) 4.dp else 24.dp,
                                        label = "AutoSkipCornerAnim",
                                    )

                                    SettingsItem(
                                        shape = RoundedCornerShape(
                                            topStart = 24.dp,
                                            topEnd = 24.dp,
                                            bottomStart = autoSkipBottomRadius,
                                            bottomEnd = autoSkipBottomRadius,
                                        ),
                                        title = str("block_ai_auto_skip"),
                                        subtitle = str("block_ai_auto_skip_sub"),
                                        icon = Icons.Rounded.SkipNext,
                                        hasSwitch = true,
                                        switchState = aiAutoSkip,
                                        onSwitchChange = { enabled ->
                                            if (enabled && !AiDetectionManager.isModelReady()) {
                                                pendingEnableFeature = "skip"
                                                showDownloadPromptDialog = true
                                            } else {
                                                aiAutoSkip = enabled
                                                prefs.aiAutoSkip = enabled
                                                if (!enabled) {
                                                    aiAutoBlock = false
                                                    prefs.aiAutoBlock = false
                                                }
                                            }
                                        },
                                        highlightKey = "ai_auto_skip",
                                    )

                                    AnimatedVisibility(
                                        visible = aiAutoSkip,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            SettingsItem(
                                                shape = RoundedCornerShape(4.dp),
                                                title = str("block_ai_auto_block"),
                                                subtitle = str("block_ai_auto_block_sub"),
                                                icon = Icons.Rounded.Block,
                                                hasSwitch = true,
                                                switchState = aiAutoBlock,
                                                onSwitchChange = {
                                                    aiAutoBlock = it
                                                    prefs.aiAutoBlock = it
                                                },
                                                highlightKey = "ai_auto_block",
                                            )
                                            SettingsItem(
                                                shape = RoundedCornerShape(4.dp),
                                                title = str("block_ai_spare_favorites"),
                                                subtitle = str("block_ai_spare_favorites_sub"),
                                                icon = Icons.Rounded.Favorite,
                                                hasSwitch = true,
                                                switchState = aiSpareFavorites,
                                                onSwitchChange = {
                                                    aiSpareFavorites = it
                                                    prefs.aiSpareFavorites = it
                                                },
                                                highlightKey = "ai_spare_favorites",
                                            )

                                            val pct = (aiThreshold * 100).toInt()
                                            SettingsItem(
                                                shape = RoundedCornerShape(4.dp),
                                                title = str("block_ai_threshold", pct),
                                                subtitle = str("block_ai_threshold_sub", pct),
                                                icon = Icons.Rounded.Tune,
                                                hasSlider = true,
                                                sliderValue = aiThreshold,
                                                sliderRange = 0.5f..0.95f,
                                                onSliderChange = {
                                                    aiThreshold = it
                                                    prefs.aiScoreThreshold = it
                                                },
                                                highlightKey = "ai_threshold",
                                            )

                                            // Sensitivity Preset Chips Row for quick desktop clicking
                                            Card(
                                                shape = RoundedCornerShape(
                                                    topStart = 4.dp,
                                                    topEnd = 4.dp,
                                                    bottomStart = 24.dp,
                                                    bottomEnd = 24.dp,
                                                ),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        text = str("block_ai_preset_header"),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    SensitivityPresetButton(
                                                        label = str("block_ai_preset_sensitive"),
                                                        value = 0.55f,
                                                        currentValue = aiThreshold,
                                                        onSelect = {
                                                            aiThreshold = 0.55f
                                                            prefs.aiScoreThreshold = 0.55f
                                                        },
                                                    )
                                                    SensitivityPresetButton(
                                                        label = str("block_ai_preset_balanced"),
                                                        value = 0.70f,
                                                        currentValue = aiThreshold,
                                                        onSelect = {
                                                            aiThreshold = 0.70f
                                                            prefs.aiScoreThreshold = 0.70f
                                                        },
                                                    )
                                                    SensitivityPresetButton(
                                                        label = str("block_ai_preset_strict"),
                                                        value = 0.85f,
                                                        currentValue = aiThreshold,
                                                        onSelect = {
                                                            aiThreshold = 0.85f
                                                            prefs.aiScoreThreshold = 0.85f
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Block 2: AI Analysis window selector
                                SettingsItem(
                                    shape = RoundedCornerShape(24.dp),
                                    title = str("block_ai_window_title"),
                                    subtitle = str(aiWindow.titleKey),
                                    icon = Icons.Rounded.Speed,
                                    onClick = { showAiWindowDialog = true },
                                    highlightKey = "ai_detection_window",
                                )

                                // Block 3: Badges group
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val badgeBottomRadius by animateDpAsState(
                                        targetValue = if (aiShowBadge) 4.dp else 24.dp,
                                        label = "BadgeCornerAnim",
                                    )

                                    SettingsItem(
                                        shape = RoundedCornerShape(
                                            topStart = 24.dp,
                                            topEnd = 24.dp,
                                            bottomStart = badgeBottomRadius,
                                            bottomEnd = badgeBottomRadius,
                                        ),
                                        title = str("pref_ai_show_badge"),
                                        subtitle = str("pref_ai_show_badge_sub"),
                                        icon = Icons.Rounded.SmartToy,
                                        hasSwitch = true,
                                        switchState = aiShowBadge,
                                        onSwitchChange = {
                                            aiShowBadge = it
                                            prefs.aiShowBadge = it
                                        },
                                        highlightKey = "ai_show_badge",
                                    )

                                    AnimatedVisibility(
                                        visible = aiShowBadge,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut(),
                                    ) {
                                        SettingsItem(
                                            shape = RoundedCornerShape(
                                                topStart = 4.dp,
                                                topEnd = 4.dp,
                                                bottomStart = 24.dp,
                                                bottomEnd = 24.dp,
                                            ),
                                            title = str("pref_ai_show_human_badge"),
                                            subtitle = str("pref_ai_show_human_badge_sub"),
                                            icon = Icons.Rounded.VerifiedUser,
                                            hasSwitch = true,
                                            switchState = aiShowHumanBadge,
                                            onSwitchChange = {
                                                aiShowHumanBadge = it
                                                prefs.aiShowHumanBadge = it
                                            },
                                            highlightKey = "ai_show_human_badge",
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }

                1 -> {
                    // ─── TAB 1: Hidden Tracks ─────────────────────────────────
                    val filteredTracks = remember(blockedTracks, searchQuery, reasonFilter) {
                        blockedTracks.filter { item ->
                            val matchQuery = searchQuery.isBlank() ||
                                (item.trackTitle?.contains(searchQuery, ignoreCase = true) == true)
                            val matchReason = when (reasonFilter) {
                                1 -> item.reason != BlockManager.REASON_AI_GENERATED
                                2 -> item.reason == BlockManager.REASON_AI_GENERATED
                                else -> true
                            }
                            matchQuery && matchReason
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Desktop Search and Filter Bar (pinned at top)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text(str("block_search_tracks_placeholder"), style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (searchQuery.isNotBlank()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .trackTextInput(),
                                )

                                if (blockedTracks.isNotEmpty()) {
                                    TextButton(
                                        onClick = { showClearAllDialog = true },
                                        shapes = ButtonDefaults.shapes(),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteSweep,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(str("block_clear_all_title"))
                                    }
                                }
                            }

                            // Reason Filter Chips
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val manualCount = blockedTracks.count { it.reason != BlockManager.REASON_AI_GENERATED }
                                val aiCount = blockedTracks.count { it.reason == BlockManager.REASON_AI_GENERATED }

                                FilterChip(
                                    selected = reasonFilter == 0,
                                    onClick = { reasonFilter = 0 },
                                    label = { Text("${str("block_filter_all")} (${blockedTracks.size})") },
                                    colors = FilterChipDefaults.filterChipColors(),
                                )
                                FilterChip(
                                    selected = reasonFilter == 1,
                                    onClick = { reasonFilter = 1 },
                                    label = { Text("${str("block_filter_manual")} ($manualCount)") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.VisibilityOff, null, modifier = Modifier.size(14.dp))
                                    },
                                )
                                FilterChip(
                                    selected = reasonFilter == 2,
                                    onClick = { reasonFilter = 2 },
                                    label = { Text("${str("block_filter_ai")} ($aiCount)") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.SmartToy, null, modifier = Modifier.size(14.dp))
                                    },
                                )
                            }
                        }

                        // Scrollable list area
                        ScrollableColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (filteredTracks.isEmpty()) {
                                    EmptyBlockedItem(
                                        icon = Icons.Rounded.VisibilityOff,
                                        message = if (searchQuery.isNotBlank() || reasonFilter != 0) {
                                            str("block_search_no_match_tracks")
                                        } else {
                                            str("block_empty_tracks")
                                        },
                                    )
                                } else {
                                    filteredTracks.forEach { item ->
                                        BlockedTrackCard(
                                            item = item,
                                            onRestoreClick = { trackToUnblock = item },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    }
                }

                2 -> {
                    // ─── TAB 2: Blocked Artists ───────────────────────────────
                    val filteredArtists = remember(blockedArtists, searchQuery, reasonFilter) {
                        blockedArtists.filter { item ->
                            val matchQuery = searchQuery.isBlank() ||
                                (item.artistName?.contains(searchQuery, ignoreCase = true) == true)
                            val matchReason = when (reasonFilter) {
                                1 -> item.reason != BlockManager.REASON_AI_GENERATED
                                2 -> item.reason == BlockManager.REASON_AI_GENERATED
                                else -> true
                            }
                            matchQuery && matchReason
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Desktop Search and Filter Bar (pinned at top)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text(str("block_search_artists_placeholder"), style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (searchQuery.isNotBlank()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .trackTextInput(),
                                )

                                if (blockedArtists.isNotEmpty()) {
                                    TextButton(
                                        onClick = { showClearAllDialog = true },
                                        shapes = ButtonDefaults.shapes(),
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteSweep,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(str("block_clear_all_title"))
                                    }
                                }
                            }

                            // Reason Filter Chips
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val manualCount = blockedArtists.count { it.reason != BlockManager.REASON_AI_GENERATED }
                                val aiCount = blockedArtists.count { it.reason == BlockManager.REASON_AI_GENERATED }

                                FilterChip(
                                    selected = reasonFilter == 0,
                                    onClick = { reasonFilter = 0 },
                                    label = { Text("${str("block_filter_all")} (${blockedArtists.size})") },
                                    colors = FilterChipDefaults.filterChipColors(),
                                )
                                FilterChip(
                                    selected = reasonFilter == 1,
                                    onClick = { reasonFilter = 1 },
                                    label = { Text("${str("block_filter_manual")} ($manualCount)") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.PersonOff, null, modifier = Modifier.size(14.dp))
                                    },
                                )
                                FilterChip(
                                    selected = reasonFilter == 2,
                                    onClick = { reasonFilter = 2 },
                                    label = { Text("${str("block_filter_ai")} ($aiCount)") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.SmartToy, null, modifier = Modifier.size(14.dp))
                                    },
                                )
                            }
                        }

                        // Scrollable list area
                        ScrollableColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (filteredArtists.isEmpty()) {
                                    EmptyBlockedItem(
                                        icon = Icons.Rounded.PersonOff,
                                        message = if (searchQuery.isNotBlank() || reasonFilter != 0) {
                                            str("block_search_no_match_artists")
                                        } else {
                                            str("block_empty_artists")
                                        },
                                    )
                                } else {
                                    filteredArtists.forEach { item ->
                                        BlockedArtistCard(
                                            item = item,
                                            onRestoreClick = { artistToUnblock = item },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── Sub-Components ──────────────────────────────────────────────────────────

@Composable
private fun SensitivityPresetButton(
    label: String,
    value: Float,
    currentValue: Float,
    onSelect: () -> Unit,
) {
    val isSelected = kotlin.math.abs(currentValue - value) < 0.04f
    if (isSelected) {
        Button(
            onClick = onSelect,
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    } else {
        FilledTonalButton(
            onClick = onSelect,
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(38.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

@Composable
private fun BlockedTrackCard(
    item: BlockedContent,
    onRestoreClick: () -> Unit,
) {
    val isAi = item.reason == BlockManager.REASON_AI_GENERATED
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (!item.trackArtworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.trackArtworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.trackTitle ?: str("untitled_track"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Reason badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAi) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = if (isAi) Icons.Rounded.SmartToy else Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                tint = if (isAi) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = if (isAi) str("block_filter_ai") else str("block_filter_manual"),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = if (isAi) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    // Source tag
                    if (item.source.isNotBlank()) {
                        Text(
                            text = item.source.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Blocked date
                    val dateStr = formatBlockedDate(item.blockedAt)
                    if (dateStr.isNotBlank()) {
                        Text(
                            text = "· $dateStr",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            FilledTonalButton(
                onClick = onRestoreClick,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Rounded.RestoreFromTrash,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(str("block_restore"), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun BlockedArtistCard(
    item: BlockedContent,
    onRestoreClick: () -> Unit,
) {
    val isAi = item.reason == BlockManager.REASON_AI_GENERATED
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (!item.artistAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.artistAvatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.artistName ?: str("unknown_artist"),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isAi) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(
                                imageVector = if (isAi) Icons.Rounded.SmartToy else Icons.Rounded.PersonOff,
                                contentDescription = null,
                                tint = if (isAi) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = if (isAi) str("block_filter_ai") else str("block_filter_manual"),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = if (isAi) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    if (item.source.isNotBlank()) {
                        Text(
                            text = item.source.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val dateStr = formatBlockedDate(item.blockedAt)
                    if (dateStr.isNotBlank()) {
                        Text(
                            text = "· $dateStr",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            FilledTonalButton(
                onClick = onRestoreClick,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            ) {
                Icon(
                    Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(str("block_restore"), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun EmptyBlockedItem(
    icon: ImageVector,
    message: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 36.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = str("block_empty_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}



@Composable
private fun GpuHardwareAccelerationSection(
    gpuEnabled: Boolean,
    onGpuEnabledChange: (Boolean) -> Unit,
    selectedGpuId: String,
    detectedGpus: List<GpuDevice>,
    activeAcceleration: String,
    onSelectGpuClick: () -> Unit,
) {
    val resolvedGpu = remember(selectedGpuId, detectedGpus) { GpuDetector.resolveGpu(selectedGpuId) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsGroupTitle(str("block_gpu_section_title"))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val switchRadius by animateDpAsState(
                targetValue = if (gpuEnabled) 4.dp else 24.dp,
                label = "GpuCornerAnim",
            )

            SettingsItem(
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = switchRadius,
                    bottomEnd = switchRadius,
                ),
                title = str("block_gpu_enable"),
                subtitle = str("block_gpu_enable_sub"),
                icon = Icons.Rounded.Memory,
                hasSwitch = true,
                switchState = gpuEnabled,
                onSwitchChange = onGpuEnabledChange,
                highlightKey = "gpu_acceleration",
            )

            AnimatedVisibility(
                visible = gpuEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val gpuSubtitle = when {
                        selectedGpuId == GpuDevice.ID_AUTO -> {
                            val target = GpuDetector.resolveGpu(GpuDevice.ID_AUTO)
                            if (target != GpuDevice.AUTO && target != GpuDevice.CPU) {
                                "${str("block_gpu_device_auto")} → ${target.name}"
                            } else {
                                str("block_gpu_device_auto")
                            }
                        }
                        selectedGpuId == GpuDevice.ID_CPU -> str("block_gpu_device_cpu")
                        else -> "${resolvedGpu.name} (${resolvedGpu.vendor.displayName})"
                    }

                    SettingsItem(
                        shape = RoundedCornerShape(4.dp),
                        title = str("block_gpu_device_title"),
                        subtitle = gpuSubtitle,
                        icon = Icons.Rounded.DeveloperBoard,
                        onClick = onSelectGpuClick,
                        highlightKey = "gpu_device_select",
                    )

                    // Hardware status badge card
                    Card(
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 4.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp,
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "${str("block_gpu_backend_active")}:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = activeAcceleration,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (detectedGpus.isNotEmpty()) {
                                Text(
                                    text = str("block_gpu_detected_count", detectedGpus.size) + ": " +
                                        detectedGpus.joinToString(", ") { "${it.name} (${if (it.isDiscrete) str("block_gpu_discrete") else str("block_gpu_integrated")})" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp,
                                )
                            } else {
                                Text(
                                    text = str("block_gpu_device_cpu"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiModelCard(
    currentModel: AiModelType,
    downloadState: AiDetectionManager.ModelDownloadState,
    activeAcceleration: String,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val isReady = downloadState is AiDetectionManager.ModelDownloadState.Ready
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Header (Android parity: icon + card title + live status badge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.SmartToy,
                            contentDescription = null,
                            tint = if (isReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str("block_model_card_title"),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when (downloadState) {
                            is AiDetectionManager.ModelDownloadState.Ready -> str("block_model_status_ready")
                            is AiDetectionManager.ModelDownloadState.Downloading -> str("block_model_downloading", (downloadState.progress * 100).toInt())
                            else -> str("block_model_card_subtitle")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (downloadState) {
                            is AiDetectionManager.ModelDownloadState.Ready,
                            is AiDetectionManager.ModelDownloadState.Downloading -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // 2. Runtime and Size Tags
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = currentModel.runtimeTag,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = formatModelSize(currentModel.totalBytes),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (isReady) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = str("block_model_active_ready"),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            // 3. Description
            Text(
                text = str(currentModel.descKey),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            // 4. Action area: Ready / Downloading / NotDownloaded
            when (downloadState) {
                is AiDetectionManager.ModelDownloadState.Ready -> {
                    FilledTonalButton(
                        onClick = onDelete,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = str("block_model_delete_btn"),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
                is AiDetectionManager.ModelDownloadState.Downloading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = str("block_model_downloading", (downloadState.progress * 100).toInt()),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val mbDownloaded = "%.1f".format(Locale.US, downloadState.downloadedBytes / (1024f * 1024f))
                            val mbTotal = "%.1f".format(Locale.US, downloadState.totalBytes / (1024f * 1024f))
                            val unit = when {
                                com.alananasss.kittytune.core.Strings.appLanguage.startsWith("fr", ignoreCase = true) -> "Mo"
                                com.alananasss.kittytune.core.Strings.appLanguage.startsWith("ru", ignoreCase = true) -> "МБ"
                                else -> "MB"
                            }
                            Text(
                                text = "$mbDownloaded / $mbTotal $unit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearWavyProgressIndicator(
                            progress = { downloadState.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                }
                is AiDetectionManager.ModelDownloadState.NotDownloaded,
                is AiDetectionManager.ModelDownloadState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (downloadState is AiDetectionManager.ModelDownloadState.Error) {
                            Text(
                                text = "Error: ${downloadState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = onDownload,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${str("block_model_download_btn")} (${formatModelSize(currentModel.totalBytes)})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatBlockedDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return try {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}

internal fun formatModelSize(bytes: Long): String {
    val lang = com.alananasss.kittytune.core.Strings.appLanguage.ifEmpty { Locale.getDefault().language }
    val isFr = lang.startsWith("fr", ignoreCase = true)
    val isRu = lang.startsWith("ru", ignoreCase = true)
    val isDe = lang.startsWith("de", ignoreCase = true)
    val locale = when {
        isFr -> Locale.FRENCH
        isDe -> Locale.GERMAN
        else -> Locale.US
    }

    if (bytes < 1024 * 1024) {
        val kbUnit = when {
            isFr -> "Ko"
            isRu -> "КБ"
            else -> "KB"
        }
        val kb = bytes / 1024.0
        return String.format(locale, "%.0f %s", kb, kbUnit)
    }

    val unit = when {
        isFr -> "Mo"
        isRu -> "МБ"
        else -> "MB"
    }
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 100.0) {
        String.format(locale, "%.1f %s", mb, unit)
    } else {
        String.format(locale, "%.0f %s", mb, unit)
    }
}

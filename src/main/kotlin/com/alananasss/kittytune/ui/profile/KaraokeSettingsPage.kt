@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.audio.ai.GpuDevice
import com.alananasss.kittytune.audio.ai.GpuDetector
import com.alananasss.kittytune.audio.ai.karaoke.KaraokeManager
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.Toaster
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.KaraokeEngineMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.ScrollableColumn
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings → Playback & Audio → Karaoke & AI Vocal Separation.
 *
 * Unified with ContentFilterSettingsPage styling:
 * - Pinned desktop segmented tabs (SecondaryTabRow) fixed at top of pane.
 * - Clean surface container cards matching ContentFilter without awkward outlines or borders.
 * - AnimatedContent transitions between tabs.
 * - Unified KaraokeModelCard (modeled on AiModelCard) with engine selector, tags, description, and actions.
 * - Hardware acceleration and playback auto-separation groups using standard SettingsGroupTitle and SettingsItem.
 * - Stem cache inspection and 100% Acapella Voice Boost controls.
 */
@Composable
fun KaraokeSettingsPage(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val prefs = remember { PlayerPreferences() }
    var selectedTab by remember { mutableIntStateOf(0) }

    var engineMode by remember { mutableStateOf(prefs.karaokeEngineMode) }
    var gpuEnabled by remember { mutableStateOf(prefs.karaokeGpuAccelerationEnabled) }
    var autoSeparate by remember { mutableStateOf(prefs.karaokeAutoSeparateOnPlay) }
    var showGpuSelectionDialog by remember { mutableStateOf(false) }
    var selectedGpuId by remember { mutableStateOf(prefs.karaokeSelectedGpuId) }
    val detectedGpus = remember { GpuDetector.getDetectedGpus() }
    val resolvedGpu = remember(selectedGpuId, detectedGpus) { GpuDetector.resolveGpu(selectedGpuId) }

    val downloadState by KaraokeManager.downloadState.collectAsState()
    val separationState by KaraokeManager.separationState.collectAsState()
    val stemVersion by KaraokeManager.stemVersion.collectAsState()
    val activeProvider by KaraokeManager.activeAccelerationProvider.collectAsState()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var stemToDelete by remember { mutableStateOf<KaraokeManager.CachedStemInfo?>(null) }
    var cachedStemsList by remember { mutableStateOf(KaraokeManager.getCachedStems()) }

    // Refresh stems on cache change or when separation completes
    LaunchedEffect(separationState, stemVersion) {
        cachedStemsList = KaraokeManager.getCachedStems()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ─── Pinned Desktop Segmented Tabs ──────────────────────────────────
        ExpressiveConnectedButtonGroup(
            options = listOf(0, 1, 2),
            selectedOption = selectedTab,
            onOptionSelected = {
                if (it == 1) {
                    cachedStemsList = KaraokeManager.getCachedStems()
                }
                selectedTab = it
            },
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
                    1 -> Icons.Rounded.GraphicEq
                    2 -> Icons.Rounded.RecordVoiceOver
                    else -> Icons.Rounded.SmartToy
                }
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            },
            labelProvider = { index ->
                when (index) {
                    0 -> Text(
                        text = str("karaoke_tab_model"),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    1 -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = str("karaoke_tab_stems"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (cachedStemsList.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    "${cachedStemsList.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    2 -> Text(
                        text = str("karaoke_tab_voice_boost"),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        )

        // ─── Animated Tab Content ───────────────────────────────────────────
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(140))
            },
            label = "KaraokeTabsContent",
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { tabIndex ->
            when (tabIndex) {
                0 -> {
                    // ─── Tab 0: AI Separation & Model Architecture ───────────
                    ScrollableColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            // 1. Unified KaraokeModelCard (modeled on AiModelCard)
                            KaraokeModelCard(
                                engineMode = engineMode,
                                onEngineModeChange = { mode ->
                                    engineMode = mode
                                    prefs.karaokeEngineMode = mode
                                    MusicManager.player.reapplyEffects()
                                },
                                downloadState = downloadState,
                                activeProvider = activeProvider,
                                onDownload = { KaraokeManager.startModelDownload() },
                                onCancelDownload = { KaraokeManager.cancelModelDownload() },
                                onDelete = { showDeleteConfirm = true },
                            )

                            // 2. Hardware Acceleration Section
                            SettingsGroupTitle(str("block_gpu_section_title"))

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val gpuCornerRadius by animateDpAsState(
                                    targetValue = if (gpuEnabled) 4.dp else 24.dp,
                                    label = "GpuCornerAnim",
                                )

                                SettingsItem(
                                    shape = RoundedCornerShape(
                                        topStart = 24.dp,
                                        topEnd = 24.dp,
                                        bottomStart = gpuCornerRadius,
                                        bottomEnd = gpuCornerRadius,
                                    ),
                                    title = str("block_gpu_accel_title"),
                                    subtitle = str("karaoke_gpu_desc"),
                                    icon = Icons.Rounded.Memory,
                                    hasSwitch = true,
                                    switchState = gpuEnabled,
                                    onSwitchChange = {
                                        gpuEnabled = it
                                        prefs.karaokeGpuAccelerationEnabled = it
                                        KaraokeManager.invalidateSession()
                                        KaraokeManager.updateActiveProviderStatus()
                                    },
                                    highlightKey = "karaoke_gpu_acceleration",
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
                                            shape = RoundedCornerShape(
                                                topStart = 4.dp,
                                                topEnd = 4.dp,
                                                bottomStart = 24.dp,
                                                bottomEnd = 24.dp,
                                            ),
                                            title = str("block_gpu_device_title"),
                                            subtitle = gpuSubtitle,
                                            icon = Icons.Rounded.DeveloperBoard,
                                            onClick = { showGpuSelectionDialog = true },
                                            highlightKey = "karaoke_gpu_device",
                                        )
                                    }
                                }
                            }

                            // 3. Playback Auto-Separation Section
                            SettingsGroupTitle(str("settings_cat_playback"))

                            SettingsItem(
                                shape = RoundedCornerShape(24.dp),
                                title = str("karaoke_auto_separate_label"),
                                subtitle = "Isole automatiquement en arrière-plan dès le lancement ou redémarrage de la lecture sans intervention manuelle.",
                                icon = Icons.Rounded.AutoAwesome,
                                hasSwitch = true,
                                switchState = autoSeparate,
                                onSwitchChange = {
                                    autoSeparate = it
                                    prefs.karaokeAutoSeparateOnPlay = it
                                },
                                highlightKey = "karaoke_auto_separate",
                            )

                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }

                1 -> {
                    // ─── Tab 1: Cached Stems ─────────────────────────────────
                    ScrollableColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            val totalCount = cachedStemsList.size
                            val totalSizeMb = cachedStemsList.sumOf { it.totalSizeBytes } / (1024f * 1024f)

                            // Stats and Purge Card
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(44.dp),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.GraphicEq,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(24.dp),
                                                    )
                                                }
                                            }
                                            Column {
                                                Text(
                                                    text = str("karaoke_cache_title"),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = str("karaoke_cache_stats", totalCount, "%.1f".format(totalSizeMb)),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }

                                        if (totalCount > 0) {
                                            FilledTonalButton(
                                                onClick = { showClearCacheConfirm = true },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                                ),
                                                shapes = ButtonDefaults.shapes(),
                                            ) {
                                                Icon(Icons.Rounded.DeleteSweep, null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(str("karaoke_cache_clear_btn"))
                                            }
                                        }
                                    }

                                    Text(
                                        text = str("karaoke_cache_desc"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // Stems List
                            if (cachedStemsList.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(56.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.FolderOpen,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }
                                        }
                                        Text(
                                            text = str("karaoke_empty_stems"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = str("karaoke_empty_stems_desc"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            } else {
                                val dateFormat = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()) }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    cachedStemsList.forEach { stem ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        modifier = Modifier.size(40.dp),
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Rounded.MusicNote,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(20.dp),
                                                            )
                                                        }
                                                    }

                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text(
                                                            text = "Piste audio #${stem.trackId}",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        ) {
                                                            val sizeMb = "%.1f Mo".format(stem.totalSizeBytes / (1024f * 1024f))
                                                            Text(
                                                                text = "$sizeMb · ${dateFormat.format(Date(stem.lastModified))}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                            if (stem.instrumentalFile?.exists() == true) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                                ) {
                                                                    Text(
                                                                        text = "Inst",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                    )
                                                                }
                                                            }
                                                            if (stem.vocalFile?.exists() == true) {
                                                                Surface(
                                                                    shape = RoundedCornerShape(4.dp),
                                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                                ) {
                                                                    Text(
                                                                        text = "Vocals",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                FilledTonalIconButton(
                                                    onClick = { stemToDelete = stem },
                                                    modifier = Modifier.size(34.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DeleteOutline,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(32.dp))
                        }
                    }
                }

                2 -> {
                    // ─── Tab 2: Voice Boost & Studio Acapella ─────────────────
                    ScrollableColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            val currentTrack = playerViewModel.currentTrack

                            // Studio Acapella Presentation Card
                            Card(
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(44.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.RecordVoiceOver,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                        Column {
                                            Text(
                                                text = str("karaoke_acapella_title"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = "HTDemucs v4 Neural Acapella Studio",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }

                                    Text(
                                        text = str("karaoke_acapella_desc"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // Current Track Status Card
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (currentTrack != null) {
                                        val hasVocalStem = remember(currentTrack.id, separationState, stemVersion) {
                                            val sep = separationState
                                            KaraokeManager.hasVocalStem(currentTrack.id) ||
                                                (sep is KaraokeManager.SeparationState.Completed && sep.trackId == currentTrack.id)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = currentTrack.title ?: "",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = currentTrack.user?.username ?: "Artiste inconnu",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }

                                            if (hasVocalStem) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Verified,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.size(14.dp),
                                                        )
                                                        Text(
                                                            text = "Acapella Prêt",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        when (val sep = separationState) {
                                            is KaraokeManager.SeparationState.Processing -> {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text(
                                                            text = str("karaoke_separating_track", (sep.progress * 100).toInt()),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                        )
                                                        FilledTonalButton(
                                                            onClick = {
                                                                KaraokeManager.cancelSeparation()
                                                                Toaster.show(str("karaoke_separation_cancelled"))
                                                            },
                                                            shapes = ButtonDefaults.shapes(),
                                                        ) {
                                                            Icon(Icons.Rounded.Close, null, modifier = Modifier.size(14.dp))
                                                            Spacer(Modifier.width(4.dp))
                                                            Text(str("btn_cancel"), style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    }
                                                    LinearWavyProgressIndicator(
                                                        progress = { sep.progress },
                                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                                    )
                                                    Text(
                                                        text = str("karaoke_cancel_tip"),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                            else -> {
                                                if (!hasVocalStem) {
                                                    if (KaraokeManager.isModelReady()) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Text(
                                                                text = "L'isolation se lance automatiquement lors de la lecture",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                            FilledTonalButton(
                                                                onClick = {
                                                                    playerViewModel.checkAndTriggerKaraokeSeparation(currentTrack, force = true, requireVocalStem = true)
                                                                },
                                                                shapes = ButtonDefaults.shapes(),
                                                            ) {
                                                                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                                                Spacer(Modifier.width(6.dp))
                                                                Text("Isoler maintenant")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = "Aucun morceau en cours de lecture · Lancez une piste pour tester l'acapella en direct",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }

                            // Live Acapella / Voice Boost Slider & Control Card
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Activer le Boost Vocal / Studio Acapella",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = if (playerViewModel.effectsState.isVocalBoostEnabled) "Effet actif en direct" else "Désactivé",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (playerViewModel.effectsState.isVocalBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = playerViewModel.effectsState.isVocalBoostEnabled,
                                            onCheckedChange = { playerViewModel.toggleVocalBoost() },
                                        )
                                    }

                                    val level = playerViewModel.effectsState.vocalBoostIntensity
                                    val percent = (level * 100).toInt()
                                    Text(
                                        text = if (percent >= 99) "Studio Acapella Pure (100% voix · instruments coupés)" else str("label_vocal_boost_level", percent),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (percent >= 99) FontWeight.Bold else FontWeight.Normal,
                                        color = if (percent >= 99) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                    )

                                    Slider(
                                        value = level,
                                        onValueChange = {
                                            playerViewModel.setVocalBoostIntensity(it)
                                            if (!playerViewModel.effectsState.isVocalBoostEnabled) playerViewModel.toggleVocalBoost()
                                        },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    val presets = remember {
                                        listOf(
                                            0.50f to "50% Boost",
                                            0.75f to "75% Voix claire",
                                            1.00f to "100% Acapella",
                                        )
                                    }
                                    val selectedPreset = presets.firstOrNull {
                                        kotlin.math.abs(it.first - level) < 0.05f
                                    }
                                    ExpressiveConnectedButtonGroup(
                                        options = presets,
                                        selectedOption = selectedPreset,
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                        onOptionSelected = { (value, _) ->
                                            playerViewModel.setVocalBoostIntensity(value)
                                            if (!playerViewModel.effectsState.isVocalBoostEnabled) playerViewModel.toggleVocalBoost()
                                        },
                                        labelProvider = { (_, lbl) ->
                                            Text(lbl, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
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

    // ─── Dialogs ─────────────────────────────────────────────────────────────

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
                                    prefs.karaokeSelectedGpuId = option.id
                                    KaraokeManager.invalidateSession()
                                    KaraokeManager.updateActiveProviderStatus()
                                    showGpuSelectionDialog = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow,
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

    if (showDeleteConfirm) {
        EscapableAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("karaoke_delete_model_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = str("karaoke_delete_model_body"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        KaraokeManager.deleteModel()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(str("block_model_delete_btn"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(str("btn_cancel"))
                }
            },
        )
    }

    if (showClearCacheConfirm) {
        EscapableAlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("karaoke_clear_cache_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Text(
                    text = str("karaoke_clear_cache_body"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        KaraokeManager.clearCache()
                        cachedStemsList = emptyList()
                        showClearCacheConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(str("karaoke_cache_clear_btn"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearCacheConfirm = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(str("btn_cancel"))
                }
            },
        )
    }

    if (stemToDelete != null) {
        val target = stemToDelete!!
        EscapableAlertDialog(
            onDismissRequest = { stemToDelete = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            title = {
                Text(
                    text = str("karaoke_delete_stem_confirm"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                val sizeMb = "%.1f Mo".format(target.totalSizeBytes / (1024f * 1024f))
                Text(
                    text = "Piste audio #${target.trackId} ($sizeMb)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        KaraokeManager.deleteStem(target.trackId)
                        cachedStemsList = KaraokeManager.getCachedStems()
                        stemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(str("btn_delete"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { stemToDelete = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(str("btn_cancel"))
                }
            },
        )
    }
}

// ─── Sub-Components ──────────────────────────────────────────────────────────

/**
 * Unified model configuration card modeled directly on [AiModelCard].
 */
@Composable
private fun KaraokeModelCard(
    engineMode: KaraokeEngineMode,
    onEngineModeChange: (KaraokeEngineMode) -> Unit,
    downloadState: KaraokeManager.ModelDownloadState,
    activeProvider: String,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val isReady = downloadState is KaraokeManager.ModelDownloadState.Ready
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
            // 1. Header (icon + title + status subtitle)
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
                            imageVector = if (engineMode == KaraokeEngineMode.MEL_BAND_ROFORMER) Icons.Rounded.SmartToy else Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = if (isReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HTDemucs v4 (Isolation Vocale & Karaoke)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when (downloadState) {
                            is KaraokeManager.ModelDownloadState.Ready -> str("block_model_status_ready")
                            is KaraokeManager.ModelDownloadState.Downloading -> str("block_model_downloading", (downloadState.progress * 100).toInt())
                            else -> "Demucs-RS · $activeProvider"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (downloadState) {
                            is KaraokeManager.ModelDownloadState.Ready,
                            is KaraokeManager.ModelDownloadState.Downloading -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            // 2. Material 3 Expressive Segmented ButtonGroup for Engine Switcher
            val engineOptions = remember {
                listOf(
                    KaraokeEngineMode.DSP to str("karaoke_engine_dsp_title"),
                    KaraokeEngineMode.MEL_BAND_ROFORMER to str("karaoke_engine_roformer_title"),
                )
            }
            val selectedOption = engineOptions.firstOrNull { it.first == engineMode }
            ExpressiveConnectedButtonGroup(
                options = engineOptions,
                selectedOption = selectedOption,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                onOptionSelected = { opt -> onEngineModeChange(opt.first) },
                labelProvider = { opt ->
                    Text(opt.second, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // 3. Runtime and Specs Tags
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = "VRAM ~1.5 Go",
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
                        text = formatModelSize(KaraokeManager.MODEL_TOTAL_BYTES),
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

            // 4. Description
            Text(
                text = str(engineMode.descKey),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            // 5. Action area: Ready / Downloading / NotDownloaded
            when (val state = downloadState) {
                is KaraokeManager.ModelDownloadState.Ready -> {
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
                is KaraokeManager.ModelDownloadState.Downloading -> {
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
                                text = str("block_model_downloading", (state.progress * 100).toInt()),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val mbDownloaded = "%.1f".format(Locale.US, state.bytesRead / (1024f * 1024f))
                            val mbTotal = "%.1f".format(Locale.US, state.totalBytes / (1024f * 1024f))
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
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        FilledTonalButton(
                            onClick = onCancelDownload,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = str("btn_cancel"),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state is KaraokeManager.ModelDownloadState.Error) {
                            Text(
                                text = "Error: ${state.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = onDownload,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${str("block_model_download_btn")} (${formatModelSize(KaraokeManager.MODEL_TOTAL_BYTES)})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
                }
            }
        }
    }
}

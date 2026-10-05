package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.common.SplitSettingsItem
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * Audio Settings: separated into clean sub-page folders (Playback, Sound & Output, Transitions, Sleep Timer).
 */
@Composable
fun AudioSettingsScreen(
    onBackClick: (() -> Unit)? = null,
    playerViewModel: PlayerViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AudioPlaybackPage(playerViewModel)
        Spacer(Modifier.height(16.dp))
        AudioQualityPage(playerViewModel)
        Spacer(Modifier.height(16.dp))
        AudioTransitionsPage(playerViewModel)
        Spacer(Modifier.height(16.dp))
        AudioSleepTimerPage()
    }
}

/**
 * Audio → Playback: autoplay, continuous playback, queue persistence, seek step, speed.
 */
@Composable
fun AudioPlaybackPage(playerViewModel: PlayerViewModel) {
    val prefs = remember { PlayerPreferences() }
    var autoplayEnabled by remember { mutableStateOf(prefs.getAutoplayEnabled()) }
    var continuousPlaybackEnabled by remember { mutableStateOf(prefs.getContinuousPlaybackEnabled()) }
    var stopOnTaskClear by remember { mutableStateOf(prefs.getStopOnTaskClear()) }
    var persistentQueueEnabled by remember { mutableStateOf(prefs.getPersistentQueueEnabled()) }
    var savePositionEnabled by remember { mutableStateOf(prefs.getSavePositionEnabled()) }
    var youtubeFallbackEnabled by remember { mutableStateOf(prefs.getYouTubeFallbackEnabled()) }
    var seekWheelSeconds by remember { mutableStateOf(prefs.getSeekWheelSeconds()) }
    var showSeekWheelDialog by remember { mutableStateOf(false) }

    if (showSeekWheelDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showSeekWheelDialog = false },
            title = { Text(str("pref_seek_wheel_step")) },
            text = {
                Column {
                    Text(
                        str("pref_seek_wheel_step_sub"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        str("pref_seek_wheel_step_value", seekWheelSeconds.toInt().toString()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Slider(
                        value = seekWheelSeconds,
                        onValueChange = {
                            seekWheelSeconds = kotlin.math.round(it)
                            prefs.setSeekWheelSeconds(seekWheelSeconds)
                        },
                        valueRange = PlayerPreferences.SEEK_WHEEL_SECONDS_MIN..PlayerPreferences.SEEK_WHEEL_SECONDS_MAX,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSeekWheelDialog = false }) { Text(str("btn_ok")) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SettingsGroupTitle(str("settings_cat_playback"))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val isGuest = com.alananasss.kittytune.data.TokenManager.isGuestMode()
            val totalVisibleItems = if (!isGuest) 10 else 9

            SettingsItem(shape = getSettingsShape(totalVisibleItems, 0), title = str("pref_autoplay"), subtitle = str("pref_autoplay_sub"), hasSwitch = true, switchState = autoplayEnabled, onSwitchChange = { autoplayEnabled = it; prefs.setAutoplayEnabled(it) }, highlightKey = "pref_autoplay")
            SettingsItem(shape = getSettingsShape(totalVisibleItems, 1), title = str("pref_continuous_playback"), subtitle = str("pref_continuous_playback_sub"), hasSwitch = true, switchState = continuousPlaybackEnabled, onSwitchChange = { continuousPlaybackEnabled = it; prefs.setContinuousPlaybackEnabled(it) }, highlightKey = "pref_continuous_playback")
            SettingsItem(shape = getSettingsShape(totalVisibleItems, 2), title = str("pref_stop_on_task_clear"), hasSwitch = true, switchState = stopOnTaskClear, onSwitchChange = { stopOnTaskClear = it; prefs.setStopOnTaskClear(it) }, highlightKey = "pref_stop_on_task_clear")
            SettingsItem(shape = getSettingsShape(totalVisibleItems, 3), title = str("pref_persist_queue"), subtitle = str("pref_persist_queue_sub"), hasSwitch = true, switchState = persistentQueueEnabled, onSwitchChange = { persistentQueueEnabled = it; prefs.setPersistentQueueEnabled(it) }, highlightKey = "pref_persist_queue")
            SettingsItem(shape = getSettingsShape(totalVisibleItems, 4), title = str("pref_queue_preserve_upcoming"), subtitle = str("pref_queue_preserve_upcoming_sub"), hasSwitch = true, switchState = playerViewModel.isQueuePreserveUpcomingEnabled, onSwitchChange = { playerViewModel.toggleQueuePreserveUpcoming(it) }, highlightKey = "pref_queue_preserve_upcoming")
            SettingsItem(shape = getSettingsShape(totalVisibleItems, 5), title = str("pref_save_position"), subtitle = str("pref_save_position_sub"), hasSwitch = true, switchState = savePositionEnabled, onSwitchChange = { savePositionEnabled = it; prefs.setSavePositionEnabled(it) }, highlightKey = "pref_save_position")
            SettingsItem(shape = getSettingsShape(totalVisibleItems, 6), title = str("pref_youtube_fallback"), subtitle = str("pref_youtube_fallback_sub"), hasSwitch = true, switchState = youtubeFallbackEnabled, onSwitchChange = { youtubeFallbackEnabled = it; prefs.setYouTubeFallbackEnabled(it) }, highlightKey = "pref_youtube_fallback")
            SettingsItem(shape = getSettingsShape(totalVisibleItems, 7), title = str("pref_precise_speed"), subtitle = str("pref_precise_speed_sub"), hasSwitch = true, switchState = playerViewModel.isPreciseSpeedEnabled, onSwitchChange = { playerViewModel.togglePreciseSpeedEnabled(it) }, highlightKey = "pref_precise_speed")

            if (!isGuest) {
                var scHistorySyncEnabled by remember { mutableStateOf(prefs.getSoundCloudHistorySyncEnabled()) }
                SettingsItem(
                    shape = getSettingsShape(totalVisibleItems, 8),
                    title = str("pref_sc_sync_title"),
                    subtitle = str("pref_sc_sync_sub"),
                    hasSwitch = true,
                    switchState = scHistorySyncEnabled,
                    onSwitchChange = {
                        scHistorySyncEnabled = it
                        prefs.setSoundCloudHistorySyncEnabled(it)
                    }
                )
            }

            SettingsItem(
                shape = getSettingsShape(totalVisibleItems, if (!isGuest) 9 else 8),
                title = str("pref_seek_wheel_step"),
                subtitle = str(
                    "pref_seek_wheel_step_value",
                    seekWheelSeconds.let { s ->
                        val rounded = kotlin.math.round(s)
                        if (rounded == s) rounded.toInt().toString() else s.toString()
                    },
                ),
                onClick = { showSeekWheelDialog = true }
            )
        }
    }
}

/**
 * Audio → Sound & Output: output device, quality bitrate, mono audio, loudness normalization.
 */
@Composable
fun AudioQualityPage(playerViewModel: PlayerViewModel) {
    val prefs = remember { PlayerPreferences() }
    var audioQuality by remember { mutableStateOf(prefs.getAudioQuality()) }
    var currentDevice by remember { mutableStateOf(prefs.getAudioDevice()) }
    var systemDefaultSinkDesc by remember { mutableStateOf<String?>(null) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showNormDialog by remember { mutableStateOf(false) }
    var showNormalizationInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var lastSystemDefaultId: String? = null
        while (isActive) {
            val newDefaultId = withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.alananasss.kittytune.util.LinuxAudioManager.getDefaultSinkId()
            }
            val newDefaultDesc = if (newDefaultId != null) {
                com.alananasss.kittytune.util.LinuxAudioManager.getOutputSinks()
                    .firstOrNull { it.id == newDefaultId }?.description
            } else null

            systemDefaultSinkDesc = newDefaultDesc

            if (newDefaultId != null && newDefaultId != lastSystemDefaultId && lastSystemDefaultId != null) {
                currentDevice = ""
                prefs.setAudioDevice("")
                playerViewModel.changeOutputDevice("")
            }
            lastSystemDefaultId = newDefaultId
            delay(2000)
        }
    }

    val availableDevices = remember {
        val isLinux = System.getProperty("os.name").lowercase().contains("linux")
        if (isLinux) {
            val sinks = com.alananasss.kittytune.util.LinuxAudioManager.getOutputSinks()
            if (sinks.isNotEmpty()) return@remember sinks.map { it.id to it.description }
        }
        val list = mutableListOf<Pair<String, String>>()
        try {
            val mixerInfos = javax.sound.sampled.AudioSystem.getMixerInfo()
            val seenNames = mutableSetOf<String>()
            for (info in mixerInfos) {
                val rawName = info.name.trim()
                if (rawName.isNotEmpty() && !seenNames.contains(rawName) && !rawName.contains("Port")) {
                    try {
                        val mixer = javax.sound.sampled.AudioSystem.getMixer(info)
                        if (mixer.isLineSupported(javax.sound.sampled.DataLine.Info(javax.sound.sampled.SourceDataLine::class.java, null))) {
                            seenNames.add(rawName)
                            list.add(rawName to com.alananasss.kittytune.util.LinuxAudioManager.cleanName(rawName))
                        }
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
        list
    }

    if (showQualityDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(str("pref_quality")) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { audioQuality = "HIGH"; prefs.setAudioQuality("HIGH"); showQualityDialog = false }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = audioQuality == "HIGH", onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column { Text(str("quality_high"), fontWeight = FontWeight.SemiBold); Text(str("quality_high_sub"), style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { audioQuality = "LOW"; prefs.setAudioQuality("LOW"); showQualityDialog = false }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = audioQuality == "LOW", onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column { Text(str("quality_low"), fontWeight = FontWeight.SemiBold); Text(str("quality_low_sub"), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showDeviceDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text(str("pref_audio_device_title")) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentDevice = ""
                                playerViewModel.changeOutputDevice("")
                                showDeviceDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentDevice == "", onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(str("pref_audio_device_default"))
                            if (currentDevice.isEmpty() && systemDefaultSinkDesc != null) {
                                Text(
                                    text = systemDefaultSinkDesc!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    availableDevices.forEach { (devId, devLabel) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentDevice = devId
                                    playerViewModel.changeOutputDevice(devId)
                                    showDeviceDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentDevice == devId, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(devLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDeviceDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showNormDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showNormDialog = false },
            icon = { Icon(androidx.compose.material.icons.Icons.Rounded.Equalizer, null) },
            title = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = str("pref_norm_title"),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp)
                    )
                    IconButton(
                        onClick = { showNormalizationInfoDialog = true },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors()
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Info,
                            contentDescription = str("pref_norm_info_title"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(str("pref_norm_sub"), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup(
                        options = com.alananasss.kittytune.ui.player.NormalizationLevel.entries,
                        selectedOption = playerViewModel.effectsState.normalizationLevel,
                        onOptionSelected = { level ->
                            playerViewModel.setNormalizationLevel(level)
                            if (!playerViewModel.effectsState.isNormalizationEnabled) playerViewModel.toggleNormalization(true)
                        },
                        labelProvider = { level ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when (level) {
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.QUIET -> str("pref_norm_quiet")
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.NORMAL -> str("pref_norm_normal")
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.LOUD -> str("pref_norm_loud")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (level) {
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.QUIET -> "\u221219 LUFS"
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.NORMAL -> "\u221214 LUFS"
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.LOUD -> "\u221211 LUFS"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showNormDialog = false }) { Text(str("btn_ok")) } }
        )
    }

    if (showNormalizationInfoDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showNormalizationInfoDialog = false },
            icon = { Icon(androidx.compose.material.icons.Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    text = str("pref_norm_info_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = str("pref_norm_info_body_1"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = str("pref_norm_info_body_2"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNormalizationInfoDialog = false }) {
                    Text(str("btn_ok"))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SettingsGroupTitle(str("settings_audio_quality_title"))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsItem(
                shape = getSettingsShape(4, 0),
                title = str("pref_audio_device_title"),
                subtitle = if (currentDevice.isEmpty()) {
                    systemDefaultSinkDesc ?: str("pref_audio_device_default")
                } else {
                    availableDevices.firstOrNull { it.first == currentDevice }?.second
                        ?: com.alananasss.kittytune.util.LinuxAudioManager.cleanName(currentDevice)
                },
                onClick = { showDeviceDialog = true }
            )
            SettingsItem(
                shape = getSettingsShape(4, 1),
                title = str("pref_quality"),
                subtitle = if (audioQuality == "HIGH") str("quality_high") else str("quality_low"),
                onClick = { showQualityDialog = true }
            )
            SettingsItem(
                shape = getSettingsShape(4, 2),
                title = str("pref_audio_mono"),
                subtitle = str("pref_audio_mono_sub"),
                hasSwitch = true,
                switchState = playerViewModel.effectsState.isMonoEnabled,
                onSwitchChange = { playerViewModel.toggleMono() },
                highlightKey = "pref_audio_mono"
            )
            SplitSettingsItem(
                shape = getSettingsShape(4, 3),
                title = str("pref_norm_title"),
                subtitle = str("pref_norm_sub"),
                onClick = { showNormDialog = true },
                switchState = playerViewModel.effectsState.isNormalizationEnabled,
                onSwitchChange = { playerViewModel.toggleNormalization(it) },
                highlightKey = "pref_norm"
            )
        }
    }
}

/**
 * Audio → Transitions & Mix: crossfade, automix overlap, start offset, tempo match, harmonic mix, bass ducking.
 */
@Composable
fun AudioTransitionsPage(playerViewModel: PlayerViewModel) {
    val prefs = remember { PlayerPreferences() }
    var crossfadeEnabled by remember { mutableStateOf(prefs.getCrossfadeEnabled()) }
    var crossfadeDuration by remember { mutableStateOf(prefs.getCrossfadeDuration()) }
    var showCrossfadeDurationDialog by remember { mutableStateOf(false) }

    var automixEnabled by remember { mutableStateOf(prefs.getAutomixEnabled()) }
    var automixOverlapMode by remember { mutableStateOf(prefs.getAutomixOverlapMode()) }
    var automixStartOffsetMode by remember { mutableStateOf(prefs.getAutomixStartOffsetMode()) }
    var automixStartOffsetCustomSec by remember { mutableStateOf(prefs.getAutomixStartOffsetCustomSec()) }
    var automixTempoMatch by remember { mutableStateOf(prefs.getAutomixTempoMatchEnabled()) }
    var automixHarmonicMix by remember { mutableStateOf(prefs.getAutomixHarmonicMixEnabled()) }
    var automixDynamicMix by remember { mutableStateOf(prefs.getAutomixDynamicMixPointsEnabled()) }
    var automixBassDucking by remember { mutableStateOf(prefs.getAutomixBassDuckingEnabled()) }
    var automixDebugOverlay by remember { mutableStateOf(prefs.getAutomixDebugOverlayEnabled()) }
    var showAutomixOverlapDialog by remember { mutableStateOf(false) }
    var showAutomixStartOffsetDialog by remember { mutableStateOf(false) }

    if (showCrossfadeDurationDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showCrossfadeDurationDialog = false },
            title = { Text(str("pref_crossfade_title")) },
            text = {
                Column {
                    Text(
                        text = str("pref_crossfade_duration").replace("%d", crossfadeDuration.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = crossfadeDuration.toFloat(),
                        onValueChange = {
                            crossfadeDuration = it.toInt()
                            prefs.setCrossfadeDuration(it.toInt())
                        },
                        valueRange = 1f..12f,
                        steps = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCrossfadeDurationDialog = false }) { Text(str("btn_ok")) }
            }
        )
    }

    if (showAutomixOverlapDialog) {
        val options = listOf(
            0 to str(com.alananasss.kittytune.R.string.automix_overlap_auto),
            1 to str(com.alananasss.kittytune.R.string.automix_overlap_2bars),
            2 to str(com.alananasss.kittytune.R.string.automix_overlap_4bars),
            3 to str(com.alananasss.kittytune.R.string.automix_overlap_8bars),
            4 to str(com.alananasss.kittytune.R.string.automix_overlap_custom)
        )
        EscapableAlertDialog(
            onDismissRequest = { showAutomixOverlapDialog = false },
            title = { Text(str(com.alananasss.kittytune.R.string.automix_overlap_mode)) },
            text = {
                Column {
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    automixOverlapMode = mode
                                    prefs.setAutomixOverlapMode(mode)
                                    showAutomixOverlapDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = automixOverlapMode == mode,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontWeight = FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutomixOverlapDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    if (showAutomixStartOffsetDialog) {
        var tempMode by remember { mutableStateOf(automixStartOffsetMode) }
        var tempCustomSec by remember { mutableStateOf(automixStartOffsetCustomSec) }
        val options = listOf(
            PlayerPreferences.AUTOMIX_START_OFFSET_AUTO to str(com.alananasss.kittytune.R.string.automix_start_offset_auto),
            PlayerPreferences.AUTOMIX_START_OFFSET_BEGINNING to str(com.alananasss.kittytune.R.string.automix_start_offset_beginning),
            PlayerPreferences.AUTOMIX_START_OFFSET_CUSTOM to str(com.alananasss.kittytune.R.string.automix_start_offset_custom),
        )
        EscapableAlertDialog(
            onDismissRequest = { showAutomixStartOffsetDialog = false },
            title = { Text(str(com.alananasss.kittytune.R.string.automix_start_offset)) },
            text = {
                Column {
                    Text(
                        text = str(com.alananasss.kittytune.R.string.automix_start_offset_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { tempMode = mode }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = tempMode == mode,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontWeight = FontWeight.Normal)
                        }
                    }
                    if (tempMode == PlayerPreferences.AUTOMIX_START_OFFSET_CUSTOM) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = str(com.alananasss.kittytune.R.string.automix_start_offset_custom_value).replace("%d", tempCustomSec.toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = tempCustomSec.toFloat(),
                            onValueChange = { tempCustomSec = it.toInt() },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    automixStartOffsetMode = tempMode
                    automixStartOffsetCustomSec = tempCustomSec
                    prefs.setAutomixStartOffsetMode(tempMode)
                    prefs.setAutomixStartOffsetCustomSec(tempCustomSec)
                    com.alananasss.kittytune.audio.automix.AutomixManager.clearPlan()
                    showAutomixStartOffsetDialog = false
                }) {
                    Text(str("btn_ok"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutomixStartOffsetDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SettingsGroupTitle(str("pref_crossfade_title"))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val fadeBottomRadius by animateDpAsState(
                targetValue = if (crossfadeEnabled) 4.dp else 24.dp,
                label = "CrossfadeCornerAnimation"
            )

            SettingsItem(
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = fadeBottomRadius,
                    bottomEnd = fadeBottomRadius
                ),
                title = str("pref_crossfade_title"),
                subtitle = str("pref_crossfade_duration").replace("%d", crossfadeDuration.toString()),
                hasSwitch = true,
                switchState = crossfadeEnabled,
                onSwitchChange = { 
                    crossfadeEnabled = it
                    prefs.setCrossfadeEnabled(it)
                },
                highlightKey = "pref_crossfade"
            )

            AnimatedVisibility(
                visible = crossfadeEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsItem(
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp
                    ),
                    title = str("label_duration"),
                    subtitle = str("pref_crossfade_duration").replace("%d", crossfadeDuration.toString()),
                    onClick = { showCrossfadeDurationDialog = true }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val automixOverlapLabel = when (automixOverlapMode) {
            1 -> str(com.alananasss.kittytune.R.string.automix_overlap_2bars)
            2 -> str(com.alananasss.kittytune.R.string.automix_overlap_4bars)
            3 -> str(com.alananasss.kittytune.R.string.automix_overlap_8bars)
            4 -> str(com.alananasss.kittytune.R.string.automix_overlap_custom)
            else -> str(com.alananasss.kittytune.R.string.automix_overlap_auto)
        }
        val automixStartOffsetLabel = when (automixStartOffsetMode) {
            PlayerPreferences.AUTOMIX_START_OFFSET_BEGINNING -> str(com.alananasss.kittytune.R.string.automix_start_offset_beginning)
            PlayerPreferences.AUTOMIX_START_OFFSET_CUSTOM -> "${str(com.alananasss.kittytune.R.string.automix_start_offset_custom)} (${automixStartOffsetCustomSec}s)"
            else -> str(com.alananasss.kittytune.R.string.automix_start_offset_auto)
        }

        SettingsGroupTitle(str(com.alananasss.kittytune.R.string.automix))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val automixBottomRadius by animateDpAsState(
                targetValue = if (automixEnabled) 4.dp else 24.dp,
                label = "AutomixCornerAnimation"
            )

            SettingsItem(
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = automixBottomRadius,
                    bottomEnd = automixBottomRadius
                ),
                title = str(com.alananasss.kittytune.R.string.automix),
                subtitle = str(com.alananasss.kittytune.R.string.automix_desc),
                hasSwitch = true,
                switchState = automixEnabled,
                onSwitchChange = { 
                    automixEnabled = it
                    prefs.setAutomixEnabled(it)
                },
                highlightKey = "pref_automix"
            )

            AnimatedVisibility(
                visible = automixEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsItem(
                        shape = RoundedCornerShape(4.dp),
                        title = str(com.alananasss.kittytune.R.string.automix_overlap_mode),
                        subtitle = automixOverlapLabel,
                        onClick = { showAutomixOverlapDialog = true }
                    )
                    SettingsItem(
                        shape = RoundedCornerShape(4.dp),
                        title = str(com.alananasss.kittytune.R.string.automix_start_offset),
                        subtitle = automixStartOffsetLabel,
                        onClick = { showAutomixStartOffsetDialog = true }
                    )
                    SettingsItem(
                        shape = RoundedCornerShape(4.dp),
                        title = str(com.alananasss.kittytune.R.string.automix_tempo_match),
                        subtitle = str(com.alananasss.kittytune.R.string.automix_tempo_match_desc),
                        hasSwitch = true,
                        switchState = automixTempoMatch,
                        onSwitchChange = {
                            automixTempoMatch = it
                            prefs.setAutomixTempoMatchEnabled(it)
                        }
                    )
                    SettingsItem(
                        shape = RoundedCornerShape(4.dp),
                        title = str(com.alananasss.kittytune.R.string.automix_harmonic_mix),
                        subtitle = str(com.alananasss.kittytune.R.string.automix_harmonic_mix_desc),
                        hasSwitch = true,
                        switchState = automixHarmonicMix,
                        onSwitchChange = {
                            automixHarmonicMix = it
                            prefs.setAutomixHarmonicMixEnabled(it)
                        }
                    )
                    SettingsItem(
                        shape = RoundedCornerShape(4.dp),
                        title = str(com.alananasss.kittytune.R.string.automix_dynamic_mix),
                        subtitle = str(com.alananasss.kittytune.R.string.automix_dynamic_mix_desc),
                        hasSwitch = true,
                        switchState = automixDynamicMix,
                        onSwitchChange = {
                            automixDynamicMix = it
                            prefs.setAutomixDynamicMixPointsEnabled(it)
                        }
                    )
                    SettingsItem(
                        shape = RoundedCornerShape(4.dp),
                        title = str(com.alananasss.kittytune.R.string.automix_bass_ducking),
                        subtitle = str(com.alananasss.kittytune.R.string.automix_bass_ducking_desc),
                        hasSwitch = true,
                        switchState = automixBassDucking,
                        onSwitchChange = {
                            automixBassDucking = it
                            prefs.setAutomixBassDuckingEnabled(it)
                        }
                    )
                    SettingsItem(
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 4.dp,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp
                        ),
                        title = str(com.alananasss.kittytune.R.string.automix_debug),
                        subtitle = str(com.alananasss.kittytune.R.string.automix_debug_desc),
                        hasSwitch = true,
                        switchState = automixDebugOverlay,
                        onSwitchChange = {
                            automixDebugOverlay = it
                            prefs.setAutomixDebugOverlayEnabled(it)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Audio → Sleep Timer: fade on/off, fade duration slider.
 */
@Composable
fun AudioSleepTimerPage() {
    val prefs = remember { PlayerPreferences() }
    var fadeEnabled by remember { mutableStateOf(prefs.getSleepTimerFadeEnabled()) }
    var fadeDuration by remember { mutableStateOf(prefs.getSleepTimerFadeDuration()) }
    var showFadeDurationDialog by remember { mutableStateOf(false) }

    if (showFadeDurationDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showFadeDurationDialog = false },
            title = { Text(str("sleep_timer_fade_title")) },
            text = {
                Column {
                    Text(
                        text = str("sleep_timer_fade_subtitle").replace("%1\$d", fadeDuration.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = fadeDuration.toFloat(),
                        onValueChange = {
                            fadeDuration = it.toInt()
                            prefs.setSleepTimerFadeDuration(it.toInt())
                        },
                        valueRange = 5f..60f,
                        steps = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFadeDurationDialog = false }) { Text(str("btn_ok")) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SettingsGroupTitle(str("sleep_timer_title"))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val fadeBottomRadius by animateDpAsState(
                targetValue = if (fadeEnabled) 4.dp else 24.dp,
                label = "FadeCornerAnimation"
            )

            SettingsItem(
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = fadeBottomRadius,
                    bottomEnd = fadeBottomRadius
                ),
                title = str("sleep_timer_fade_title"),
                subtitle = str("sleep_timer_fade_subtitle").replace("%1\$d", fadeDuration.toString()),
                hasSwitch = true,
                switchState = fadeEnabled,
                onSwitchChange = { 
                    fadeEnabled = it
                    prefs.setSleepTimerFadeEnabled(it)
                }
            )

            AnimatedVisibility(
                visible = fadeEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SettingsItem(
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp
                    ),
                    title = str("label_duration"),
                    subtitle = str("sleep_timer_fade_subtitle").replace("%1\$d", fadeDuration.toString()),
                    onClick = { showFadeDurationDialog = true }
                )
            }
        }
    }
}

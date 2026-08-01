package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SplitSettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
fun AudioSettingsScreen(
    onBackClick: (() -> Unit)? = null,
    playerViewModel: PlayerViewModel
) {
    val prefs = remember { PlayerPreferences() }

    var autoplayEnabled by remember { mutableStateOf(prefs.getAutoplayEnabled()) }
    var continuousPlaybackEnabled by remember { mutableStateOf(prefs.getContinuousPlaybackEnabled()) }
    var stopOnTaskClear by remember { mutableStateOf(prefs.getStopOnTaskClear()) }
    var persistentQueueEnabled by remember { mutableStateOf(prefs.getPersistentQueueEnabled()) }
    var savePositionEnabled by remember { mutableStateOf(prefs.getSavePositionEnabled()) }
    var audioQuality by remember { mutableStateOf(prefs.getAudioQuality()) }

    var youtubeFallbackEnabled by remember { mutableStateOf(prefs.getYouTubeFallbackEnabled()) }
    var fadeEnabled by remember { mutableStateOf(prefs.getSleepTimerFadeEnabled()) }
    var fadeDuration by remember { mutableStateOf(prefs.getSleepTimerFadeDuration()) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showFadeDurationDialog by remember { mutableStateOf(false) }

    var showDeviceDialog by remember { mutableStateOf(false) }
    var currentDevice by remember { mutableStateOf(prefs.getAudioDevice()) }

    val availableDevices = remember {
        val list = mutableListOf<String>()
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
                            list.add(rawName)
                        }
                    } catch (e: Exception) {}
                }
            }
        } catch (e: Exception) {}
        list
    }

    var crossfadeEnabled by remember { mutableStateOf(prefs.getCrossfadeEnabled()) }
    var crossfadeDuration by remember { mutableStateOf(prefs.getCrossfadeDuration()) }
    var showCrossfadeDurationDialog by remember { mutableStateOf(false) }

    var showNormDialog by remember { mutableStateOf(false) }

    if (showNormDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showNormDialog = false },
            title = { Text(str("pref_norm_level_title")) },
            text = {
                Column {
                    val levels = listOf(
                        com.alananasss.kittytune.ui.player.NormalizationLevel.QUIET to str("pref_norm_quiet"),
                        com.alananasss.kittytune.ui.player.NormalizationLevel.NORMAL to str("pref_norm_normal"),
                        com.alananasss.kittytune.ui.player.NormalizationLevel.LOUD to str("pref_norm_loud")
                    )
                    levels.forEach { (lvl, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { 
                                playerViewModel.setNormalizationLevel(lvl)
                                showNormDialog = false 
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = playerViewModel.effectsState.normalizationLevel == lvl, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNormDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

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
                        valueRange = PlayerPreferences.SLEEP_TIMER_FADE_DURATION_MIN.toFloat()..PlayerPreferences.SLEEP_TIMER_FADE_DURATION_MAX.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFadeDurationDialog = false }) {
                    Text(str("btn_ok"))
                }
            }
        )
    }

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
                        valueRange = 1f..15f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCrossfadeDurationDialog = false }) {
                    Text(str("btn_ok"))
                }
            }
        )
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
                        Text(str("pref_audio_device_default"))
                    }

                    availableDevices.forEach { dev ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentDevice = dev
                                    playerViewModel.changeOutputDevice(dev)
                                    showDeviceDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = currentDevice == dev, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(dev, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDeviceDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(str("settings_cat_playback"))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val isNormEnabled = playerViewModel.effectsState.isNormalizationEnabled
                        val totalVisibleItems = if (isNormEnabled) 9 else 8

                        SettingsItem(shape = getSettingsShape(totalVisibleItems, 0), title = str("pref_autoplay"), subtitle = str("pref_autoplay_sub"), hasSwitch = true, switchState = autoplayEnabled, onSwitchChange = { autoplayEnabled = it; prefs.setAutoplayEnabled(it) })
                        SettingsItem(shape = getSettingsShape(totalVisibleItems, 1), title = str("pref_continuous_playback"), subtitle = str("pref_continuous_playback_sub"), hasSwitch = true, switchState = continuousPlaybackEnabled, onSwitchChange = { continuousPlaybackEnabled = it; prefs.setContinuousPlaybackEnabled(it) })
                        SettingsItem(shape = getSettingsShape(totalVisibleItems, 2), title = str("pref_stop_on_task_clear"), hasSwitch = true, switchState = stopOnTaskClear, onSwitchChange = { stopOnTaskClear = it; prefs.setStopOnTaskClear(it) })
                        SettingsItem(shape = getSettingsShape(totalVisibleItems, 3), title = str("pref_persist_queue"), subtitle = str("pref_persist_queue_sub"), hasSwitch = true, switchState = persistentQueueEnabled, onSwitchChange = { persistentQueueEnabled = it; prefs.setPersistentQueueEnabled(it) })
                        SettingsItem(shape = getSettingsShape(totalVisibleItems, 4), title = str("pref_save_position"), subtitle = str("pref_save_position_sub"), hasSwitch = true, switchState = savePositionEnabled, onSwitchChange = { savePositionEnabled = it; prefs.setSavePositionEnabled(it) })
                        SettingsItem(shape = getSettingsShape(totalVisibleItems, 5), title = str("pref_youtube_fallback"), subtitle = str("pref_youtube_fallback_sub"), hasSwitch = true, switchState = youtubeFallbackEnabled, onSwitchChange = { youtubeFallbackEnabled = it; prefs.setYouTubeFallbackEnabled(it) })
                        SettingsItem(shape = getSettingsShape(totalVisibleItems, 6), title = str("pref_precise_speed"), subtitle = str("pref_precise_speed_sub"), hasSwitch = true, switchState = playerViewModel.isPreciseSpeedEnabled, onSwitchChange = { playerViewModel.togglePreciseSpeedEnabled(it) })
                        
                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 7),
                            title = str("pref_norm_title"),
                            subtitle = str("pref_norm_sub"),
                            hasSwitch = true,
                            switchState = isNormEnabled,
                            onSwitchChange = { playerViewModel.toggleNormalization(it) }
                        )

                        AnimatedVisibility(
                            visible = isNormEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, 8),
                                title = str("pref_norm_level_title"),
                                subtitle = str("pref_norm_level_sub") + " : " + when(playerViewModel.effectsState.normalizationLevel) {
                                    com.alananasss.kittytune.ui.player.NormalizationLevel.QUIET -> str("pref_norm_quiet")
                                    com.alananasss.kittytune.ui.player.NormalizationLevel.NORMAL -> str("pref_norm_normal")
                                    com.alananasss.kittytune.ui.player.NormalizationLevel.LOUD -> str("pref_norm_loud")
                                },
                                onClick = { showNormDialog = true }
                            )
                        }
                    }
                }
            }
            
            Box {
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
                            }
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
                }
            }

            Box {
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

            Box {
                SettingsGroup(
                    title = str("settings_cat_audio"),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_audio_device_title"),
                                subtitle = if (currentDevice.isEmpty()) str("pref_audio_device_default") else currentDevice,
                                onClick = { showDeviceDialog = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_quality"),
                                subtitle = if (audioQuality == "HIGH") str("quality_high") else str("quality_low"),
                                onClick = { showQualityDialog = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_audio_mono"),
                                subtitle = str("pref_audio_mono_sub"),
                                hasSwitch = true,
                                switchState = playerViewModel.effectsState.isMonoEnabled,
                                onSwitchChange = { playerViewModel.toggleMono() }
                            )
                        }
                    )
                )
            }
        }
    }

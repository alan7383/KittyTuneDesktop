package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    onNavigateToDrmExplanation: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val prefs = remember { PlayerPreferences() }

    var autoplayEnabled by remember { mutableStateOf(prefs.getAutoplayEnabled()) }
    var stopOnTaskClear by remember { mutableStateOf(prefs.getStopOnTaskClear()) }
    var persistentQueueEnabled by remember { mutableStateOf(prefs.getPersistentQueueEnabled()) }
    var audioQuality by remember { mutableStateOf(prefs.getAudioQuality()) }

    var youtubeFallbackEnabled by remember { mutableStateOf(prefs.getYouTubeFallbackEnabled()) }
    var downloadDrmEnabled by remember { mutableStateOf(prefs.getDownloadDrmStreamsEnabled()) }
    var fadeEnabled by remember { mutableStateOf(prefs.getSleepTimerFadeEnabled()) }
    var fadeDuration by remember { mutableStateOf(prefs.getSleepTimerFadeDuration()) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showFadeDurationDialog by remember { mutableStateOf(false) }

    if (showFadeDurationDialog) {
        AlertDialog(
            onDismissRequest = { showFadeDurationDialog = false },
            title = { Text(str("sleep_timer_fade_title")) },
            text = {
                Column {
                    Text(
                        text = str("sleep_timer_fade_subtitle").replace("%d", fadeDuration.toString()),
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

    if (showQualityDialog) {
        AlertDialog(
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

    SettingsScaffold(
        title = str("pref_audio_title"),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(str("settings_cat_playback"))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val totalVisibleItems = 6

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 0),
                            title = str("pref_autoplay"),
                            subtitle = str("pref_autoplay_sub"),
                            hasSwitch = true,
                            switchState = autoplayEnabled,
                            onSwitchChange = { autoplayEnabled = it; prefs.setAutoplayEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 1),
                            title = str("pref_stop_on_task_clear"),
                            hasSwitch = true,
                            switchState = stopOnTaskClear,
                            onSwitchChange = { stopOnTaskClear = it; prefs.setStopOnTaskClear(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 2),
                            title = str("pref_persist_queue"),
                            subtitle = str("pref_persist_queue_sub"),
                            hasSwitch = true,
                            switchState = persistentQueueEnabled,
                            onSwitchChange = { persistentQueueEnabled = it; prefs.setPersistentQueueEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 3),
                            title = str("pref_youtube_fallback"),
                            subtitle = str("pref_youtube_fallback_sub"),
                            hasSwitch = true,
                            switchState = youtubeFallbackEnabled,
                            onSwitchChange = { youtubeFallbackEnabled = it; prefs.setYouTubeFallbackEnabled(it) }
                        )

                        SplitSettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 4),
                            title = str("pref_download_drm"),
                            subtitle = str("pref_download_drm_sub"),
                            onClick = onNavigateToDrmExplanation,
                            switchState = downloadDrmEnabled,
                            onSwitchChange = { downloadDrmEnabled = it; prefs.setDownloadDrmStreamsEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 5),
                            title = str("pref_precise_speed"),
                            subtitle = str("pref_precise_speed_sub"),
                            hasSwitch = true,
                            switchState = playerViewModel.isPreciseSpeedEnabled,
                            onSwitchChange = { playerViewModel.togglePreciseSpeedEnabled(it) }
                        )
                    }
                }
            }

            item {
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
                            subtitle = str("sleep_timer_fade_subtitle").replace("%d", fadeDuration.toString()),
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
                                subtitle = str("sleep_timer_fade_subtitle").replace("%d", fadeDuration.toString()),
                                onClick = { showFadeDurationDialog = true }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(
                    title = str("settings_cat_audio"),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_quality"),
                                subtitle = if (audioQuality == "HIGH") str("quality_high") else str("quality_low"),
                                onClick = { showQualityDialog = true }
                            )
                        }
                    )
                )
            }
        }
    }
}

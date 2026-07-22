package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alananasss.kittytune.core.str
import androidx.compose.ui.unit.dp

import com.alananasss.kittytune.data.local.DiscordStatusDisplay
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.getSettingsShape

import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
fun DiscordSettingsScreen(
    onBackClick: (() -> Unit)? = null,
    onNavigateToLogin: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val prefs = remember { PlayerPreferences() }

    var token by remember { mutableStateOf(prefs.getDiscordToken()) }
    var isEnabled by remember { mutableStateOf(prefs.getDiscordRpcEnabled()) }
    var statusDisplay by remember { mutableStateOf(prefs.getDiscordStatusDisplay()) }
    val isLoggedIn = !token.isNullOrEmpty()

    var showStatusDialog by remember { mutableStateOf(false) }

    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text(str("pref_discord_status_display")) },
            text = {
                Column {
                    StatusDisplayRadioButton(str("discord_status_activity"), DiscordStatusDisplay.ACTIVITY, statusDisplay) {
                        statusDisplay = it
                        prefs.setDiscordStatusDisplay(it)
                        showStatusDialog = false
                        playerViewModel?.updateDiscordPresence()
                    }
                    StatusDisplayRadioButton(str("discord_status_soundcloud"), DiscordStatusDisplay.SOUNDCLOUD, statusDisplay) {
                        statusDisplay = it
                        prefs.setDiscordStatusDisplay(it)
                        showStatusDialog = false
                        playerViewModel?.updateDiscordPresence()
                    }
                    StatusDisplayRadioButton(str("discord_status_artist"), DiscordStatusDisplay.ARTIST, statusDisplay) {
                        statusDisplay = it
                        prefs.setDiscordStatusDisplay(it)
                        showStatusDialog = false
                        playerViewModel?.updateDiscordPresence()
                    }
                    StatusDisplayRadioButton(str("discord_status_song"), DiscordStatusDisplay.SONG, statusDisplay) {
                        statusDisplay = it
                        prefs.setDiscordStatusDisplay(it)
                        showStatusDialog = false
                        playerViewModel?.updateDiscordPresence()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStatusDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    SettingsScaffold(
        title = str("discord_rpc_title"),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsGroup(
                    title = str("discord_status_header"),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = if(isLoggedIn) str("discord_connected") else str("discord_not_connected"),
                                subtitle = if(isLoggedIn) str("discord_token_present") else str("discord_connect_desc"),
                                onClick = { if(!isLoggedIn) onNavigateToLogin() }
                            )
                        },
                        { shape ->
                            if (isLoggedIn) {
                                SettingsItem(
                                    shape = shape,
                                    title = str("discord_logout"),
                                    onClick = {
                                        prefs.setDiscordToken(null)
                                        prefs.setDiscordRpcEnabled(false)
                                        token = null
                                        isEnabled = false
                                        playerViewModel?.closeDiscordRpc()
                                    }
                                )
                            }
                        }
                    )
                )

                if (isLoggedIn) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SettingsGroupTitle(str("discord_options_header"))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val animatedBottomRadius by animateDpAsState(
                                targetValue = if (isEnabled) 4.dp else 24.dp,
                                animationSpec = tween(400),
                                label = "DiscordRpcCornerAnimation"
                            )

                            SettingsItem(
                                shape = RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomStart = animatedBottomRadius,
                                    bottomEnd = animatedBottomRadius
                                ),
                                title = str("discord_enable_rpc"),
                                subtitle = str("discord_enable_rpc_desc"),
                                hasSwitch = true,
                                switchState = isEnabled,
                                onSwitchChange = {
                                    isEnabled = it
                                    prefs.setDiscordRpcEnabled(it)
                                    playerViewModel?.updateDiscordPresence()
                                }
                            )

                            AnimatedVisibility(
                                visible = isEnabled,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                SettingsItem(
                                    shape = getSettingsShape(2, 1),
                                    title = str("pref_discord_status_display"),
                                    subtitle = when(statusDisplay) {
                                        DiscordStatusDisplay.ACTIVITY -> str("discord_status_activity")
                                        DiscordStatusDisplay.SOUNDCLOUD -> str("discord_status_soundcloud")
                                        DiscordStatusDisplay.ARTIST -> str("discord_status_artist")
                                        DiscordStatusDisplay.SONG -> str("discord_status_song")
                                    },
                                    onClick = { showStatusDialog = true }
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
fun StatusDisplayRadioButton(text: String, mode: DiscordStatusDisplay, selected: DiscordStatusDisplay, onSelect: (DiscordStatusDisplay) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (mode == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

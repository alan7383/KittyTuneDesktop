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
import androidx.compose.ui.graphics.Shape
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import androidx.compose.ui.unit.dp

import com.alananasss.kittytune.data.discord.DiscordRemoteAuth
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

    // The token is written by the QR login on another destination. If this section is still
    // alive when that lands, pick it up straight away instead of waiting for a return trip
    // through navigation to re-read it.
    val authState by DiscordRemoteAuth.state.collectAsState()
    LaunchedEffect(authState) {
        if (authState is DiscordRemoteAuth.State.Success) {
            token = prefs.getDiscordToken()
            isEnabled = prefs.getDiscordRpcEnabled()
        }
    }

    var showStatusDialog by remember { mutableStateOf(false) }

    if (showStatusDialog) {
        EscapableAlertDialog(
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

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsGroup(
            title = str("discord_status_header"),
            // Built for what is actually rendered: a fixed two-entry list left the account row
            // with a bottom-of-group shape and a 2dp gap under it whenever the logout row was
            // absent, because the group sized itself for an item nobody drew.
            items = buildList {
                add { shape: Shape ->
                    SettingsItem(
                        shape = shape,
                        title = if (isLoggedIn) str("discord_connected") else str("discord_not_connected"),
                        // The row is tappable either way now — logging in, or scanning again to
                        // swap accounts — so say which one it is instead of "token present".
                        subtitle = if (isLoggedIn) str("setup_discord_change") else str("discord_connect_desc"),
                        onClick = onNavigateToLogin
                    )
                }
                if (isLoggedIn) {
                    add { shape: Shape ->
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
            }
        )

        // Stacked under the group, not layered over it: these two used to be siblings inside a
        // Box, so the options drew on top of the account rows.
        if (isLoggedIn) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                            subtitle = when (statusDisplay) {
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

@Composable
fun StatusDisplayRadioButton(text: String, mode: DiscordStatusDisplay, selected: DiscordStatusDisplay, onSelect: (DiscordStatusDisplay) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (mode == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

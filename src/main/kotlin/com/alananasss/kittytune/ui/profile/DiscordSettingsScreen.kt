package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.discord.DiscordPresenceStatus
import com.alananasss.kittytune.data.local.DiscordStatusDisplay
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
fun DiscordSettingsScreen(playerViewModel: PlayerViewModel) {
    val prefs = remember { PlayerPreferences() }
    Prefs.flow.collectAsState().value
    val enabled = prefs.getDiscordRpcEnabled()
    val statusDisplay = prefs.getDiscordStatusDisplay()
    val listenAlongEnabled = prefs.getDiscordListenAlongEnabled()
    val status by playerViewModel.discordPresenceStatus.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showStatusDialog by remember { mutableStateOf(false) }
    var logCopied by remember(status) { mutableStateOf(false) }

    if (showStatusDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text(str("pref_discord_status_display")) },
            text = {
                Column {
                    DiscordStatusDisplay.entries.forEach { mode ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                prefs.setDiscordStatusDisplay(mode)
                                showStatusDialog = false
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == statusDisplay, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(str(mode.labelKey()))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showStatusDialog = false }) {
                    Text(str("btn_cancel"))
                }
            },
        )
    }

    SettingsGroup(items = buildList {
        add { shape ->
            SettingsItem(
                shape = shape,
                title = str("discord_enable_rpc"),
                subtitle = when {
                    enabled && status == DiscordPresenceStatus.READY -> str("discord_desktop_ready")
                    enabled -> str("discord_desktop_hint")
                    else -> str("discord_enable_rpc_desc")
                },
                hasSwitch = true,
                switchState = enabled,
                onSwitchChange = prefs::setDiscordRpcEnabled,
                highlightKey = "pref_discord",
            )
        }
        if (enabled) {
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_discord_status_display"),
                    highlightKey = "pref_discord_status_display",
                    subtitle = str(statusDisplay.labelKey()),
                    onClick = { showStatusDialog = true },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("discord_listen_along_title"),
                    highlightKey = "discord_listen_along_title",
                    subtitle = str("discord_listen_along_desc"),
                    hasSwitch = true,
                    switchState = listenAlongEnabled,
                    onSwitchChange = prefs::setDiscordListenAlongEnabled,
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("discord_auto_live_title"),
                    highlightKey = "discord_auto_live_title",
                    subtitle = str("discord_auto_live_desc"),
                    hasSwitch = true,
                    switchState = prefs.getDiscordAutoLiveEnabled(),
                    onSwitchChange = prefs::setDiscordAutoLiveEnabled,
                )
            }
            if (listenAlongEnabled) {
                val liveCode = Together.personalLiveCode
                add { shape ->
                    SettingsItem(
                        shape = shape,
                        title = str("together_copy_link"),
                        highlightKey = "together_copy_link",
                        subtitle = TogetherLinks.inviteUrl(liveCode),
                        onClick = { TogetherLinks.copyInviteLink(liveCode) },
                    )
                }
                add { shape ->
                    SettingsItem(
                        shape = shape,
                        title = str("together_reset_link"),
                        highlightKey = "together_reset_link",
                        subtitle = str("together_reset_link_desc"),
                        onClick = {
                            TogetherLinks.copyInviteLink(Together.regeneratePersonalLiveCode())
                            playerViewModel.updateDiscordPresence()
                        },
                    )
                }
            }
        }
        add { shape ->
            SettingsItem(
                shape = shape,
                title = str(if (logCopied) "sync_diagnostics_copied" else "sync_copy_diagnostics"),
                highlightKey = "sync_copy_diagnostics",
                onClick = {
                    clipboard.setText(AnnotatedString(playerViewModel.discordConnectionLog()))
                    logCopied = true
                },
            )
        }
    })
}

private fun DiscordStatusDisplay.labelKey(): String = when (this) {
    DiscordStatusDisplay.ACTIVITY -> "discord_status_activity"
    DiscordStatusDisplay.SOUNDCLOUD -> "discord_status_soundcloud"
    DiscordStatusDisplay.ARTIST -> "discord_status_artist"
    DiscordStatusDisplay.SONG -> "discord_status_song"
}

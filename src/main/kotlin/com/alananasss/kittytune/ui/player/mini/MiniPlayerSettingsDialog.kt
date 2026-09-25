package com.alananasss.kittytune.ui.player.mini

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.MiniPlayerStyle
import com.alananasss.kittytune.data.local.PlayerPreferences

@Composable
fun MiniPlayerSettingsDialog(
    prefs: PlayerPreferences,
    onDismiss: () -> Unit,
) {
    val prefsSnapshot by Prefs.flow.collectAsState()
    val style = remember(prefsSnapshot) { prefs.getMiniPlayerStyle() }
    val transparentBg = remember(prefsSnapshot) { prefs.getMiniPlayerTransparentBg() }
    val hoverIllumination = remember(prefsSnapshot) { prefs.getMiniPlayerHoverIllumination() }
    val showCover = remember(prefsSnapshot) { prefs.getMiniPlayerShowCover() }
    val showPlayback = remember(prefsSnapshot) { prefs.getMiniPlayerShowPlaybackControls() }
    val showAdditional = remember(prefsSnapshot) { prefs.getMiniPlayerShowAdditionalControls() }
    val controlsOnHover = remember(prefsSnapshot) { prefs.getMiniPlayerControlsOnHover() }
    val hoverEffect = remember(prefsSnapshot) { prefs.getMiniPlayerHoverEffect() }
    val showProgress = remember(prefsSnapshot) { prefs.getMiniPlayerShowProgress() }
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("mini_player_settings_title")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MiniPlayerSwitchRow(
                    title = str("mini_player_style_elongated"),
                    subtitle = str("mini_player_style_elongated_desc"),
                    checked = style == MiniPlayerStyle.ELONGATED,
                    onCheckedChange = { isElongated ->
                        prefs.setMiniPlayerStyle(
                            if (isElongated) MiniPlayerStyle.ELONGATED
                            else MiniPlayerStyle.STANDARD
                        )
                    }
                )
                MiniPlayerSwitchRow(
                    title = str("mini_player_transparent_bg"),
                    subtitle = str("mini_player_transparent_bg_desc"),
                    checked = transparentBg,
                    onCheckedChange = { prefs.setMiniPlayerTransparentBg(it) }
                )
                MiniPlayerSwitchRow(
                    title = str("mini_player_hover_illumination"),
                    subtitle = str("mini_player_hover_illumination_desc"),
                    checked = hoverIllumination,
                    onCheckedChange = { prefs.setMiniPlayerHoverIllumination(it) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.5f)
                )
                MiniPlayerSwitchRow(
                    title = str("mini_player_show_cover"),
                    subtitle = str("mini_player_show_cover_desc"),
                    checked = showCover,
                    onCheckedChange = { prefs.setMiniPlayerShowCover(it) }
                )
                MiniPlayerSwitchRow(
                    title = str("mini_player_show_playback_controls"),
                    subtitle = str("mini_player_show_playback_controls_desc"),
                    checked = showPlayback,
                    onCheckedChange = { prefs.setMiniPlayerShowPlaybackControls(it) }
                )
                MiniPlayerSwitchRow(
                    title = str("mini_player_show_additional_controls"),
                    subtitle = str("mini_player_show_additional_controls_desc"),
                    checked = showAdditional,
                    onCheckedChange = { prefs.setMiniPlayerShowAdditionalControls(it) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.5f)
                )
                MiniPlayerSwitchRow(
                    title = str("mini_player_controls_on_hover"),
                    subtitle = str("mini_player_controls_on_hover_desc"),
                    checked = controlsOnHover,
                    onCheckedChange = { prefs.setMiniPlayerControlsOnHover(it) }
                )
                MiniPlayerSwitchRow(
                    title = str("mini_player_hover_effect"),
                    subtitle = str("mini_player_hover_effect_desc"),
                    checked = hoverEffect,
                    onCheckedChange = { prefs.setMiniPlayerHoverEffect(it) }
                )
                MiniPlayerSwitchRow(
                    title = str("mini_player_show_progress"),
                    subtitle = str("mini_player_show_progress_desc"),
                    checked = showProgress,
                    onCheckedChange = { prefs.setMiniPlayerShowProgress(it) }
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_close")) } }
    )
}

@Composable
fun MiniPlayerSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

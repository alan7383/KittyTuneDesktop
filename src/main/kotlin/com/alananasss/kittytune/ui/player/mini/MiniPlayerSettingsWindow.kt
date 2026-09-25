package com.alananasss.kittytune.ui.player.mini

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.profile.MiniPlayerSettingsList
import com.alananasss.kittytune.ui.theme.KittyTuneTheme

/**
 * The mini player's settings in a window of their own, opened from its right-click menu. A dialog
 * inside the mini player would be clipped to its few dozen dp just as the old menu was.
 */
@Composable
fun MiniPlayerSettingsWindow(onClose: () -> Unit) {
    val prefs = remember { PlayerPreferences() }
    val state = rememberDialogState(
        position = WindowPosition(androidx.compose.ui.Alignment.Center),
        size = DpSize(460.dp, 600.dp),
    )
    DialogWindow(
        onCloseRequest = onClose,
        state = state,
        title = str("mini_player_settings_title"),
        resizable = false,
    ) {
        KittyTuneTheme {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(str("mini_player_settings_title"), style = MaterialTheme.typography.headlineSmall)
                    MiniPlayerSettingsList(prefs, modifier = Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onClose) { Text(str("btn_close")) }
                    }
                }
            }
        }
    }
}

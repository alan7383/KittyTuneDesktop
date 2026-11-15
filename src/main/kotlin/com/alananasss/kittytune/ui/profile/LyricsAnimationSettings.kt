package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.InstrumentalIndicatorStyle
import com.alananasss.kittytune.data.local.PlayerPreferences

/** "Sweep" line animation: line-by-line lyrics fill top to bottom as they are sung (port of Android PR #83). */
@Composable
fun LyricsSweepSettingItem(shape: Shape) {
    val prefs = remember { PlayerPreferences() }
    val enabled by remember { prefs.lyricsSweepEnabledFlow() }.collectAsState(initial = prefs.getLyricsSweepEnabled())
    SettingsItem(
        shape = shape,
        title = str("pref_lyrics_line_animation"),
        icon = Icons.Rounded.GraphicEq,
        subtitle = str("pref_lyrics_line_animation_sub"),
        hasSwitch = true,
        switchState = enabled,
        onSwitchChange = { prefs.setLyricsSweepEnabled(it) },
        highlightKey = "pref_lyrics_line_animation",
    )
}

private fun indicatorLabel(style: InstrumentalIndicatorStyle): String = when (style) {
    InstrumentalIndicatorStyle.NOTE_WAVE -> str("pref_instrumental_note_wave")
    InstrumentalIndicatorStyle.CIRCLE -> str("pref_instrumental_circle")
    InstrumentalIndicatorStyle.DOTS -> str("pref_instrumental_dots")
    InstrumentalIndicatorStyle.OFF -> str("pref_instrumental_off")
}

/** What long instrumental gaps in the lyrics show: the note with its wave, a countdown circle, dots, or nothing. */
@Composable
fun InstrumentalIndicatorSettingItem(shape: Shape) {
    val prefs = remember { PlayerPreferences() }
    val current by remember { prefs.instrumentalIndicatorStyleFlow() }.collectAsState(initial = prefs.getInstrumentalIndicatorStyle())
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(str("pref_instrumental_indicator")) },
            text = {
                Column {
                    listOf(
                        InstrumentalIndicatorStyle.NOTE_WAVE,
                        InstrumentalIndicatorStyle.CIRCLE,
                        InstrumentalIndicatorStyle.DOTS,
                        InstrumentalIndicatorStyle.OFF,
                    ).forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { prefs.setInstrumentalIndicatorStyle(option); showDialog = false }
                                .padding(vertical = 8.dp),
                        ) {
                            RadioButton(selected = option == current, onClick = { prefs.setInstrumentalIndicatorStyle(option); showDialog = false })
                            Text(indicatorLabel(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showDialog = false }) { Text(str("btn_cancel")) }
            },
        )
    }
    SettingsItem(
        shape = shape,
        title = str("pref_instrumental_indicator"),
        icon = Icons.Rounded.MusicNote,
        subtitle = indicatorLabel(current),
        onClick = { showDialog = true },
        highlightKey = "pref_instrumental_indicator",
    )
}

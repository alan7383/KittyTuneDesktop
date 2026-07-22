package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alananasss.kittytune.core.str

@Composable
fun KeyboardShortcutsDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(800.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = str("keyboard_shortcuts_title"),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    // Left Column
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ShortcutRow("space", str("shortcut_play_pause"))
                        ShortcutRow("shift + \u2192", str("shortcut_next_track"))
                        ShortcutRow("shift + L", str("shortcut_repeat"))
                        ShortcutRow("shift + \u2190", str("shortcut_prev_track"))
                        Spacer(Modifier.height(8.dp))
                        ShortcutRow("\u2192", str("shortcut_seek_forward"))
                        ShortcutRow("L", str("shortcut_like"))
                        ShortcutRow("R", str("shortcut_repost"))
                        Spacer(Modifier.height(8.dp))
                        ShortcutRow("S", str("shortcut_search"))
                        Spacer(Modifier.height(8.dp))
                        ShortcutRow("G then L", str("shortcut_nav_likes"))
                        ShortcutRow("G then C", str("shortcut_nav_library"))
                        ShortcutRow("G then H", str("shortcut_nav_history"))
                        Spacer(Modifier.height(8.dp))
                        ShortcutRow("shift + S", str("shortcut_shuffle"))
                    }

                    // Right Column
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ShortcutRow("shift + \u2193", str("shortcut_vol_down"))
                        ShortcutRow("shift + \u2191", str("shortcut_vol_up"))
                        ShortcutRow("M", str("shortcut_mute"))
                        Spacer(Modifier.height(8.dp))
                        ShortcutRow("\u2190", str("shortcut_seek_backward"))
                        ShortcutRow("0...9", str("shortcut_seek_position"))
                        ShortcutRow("P", str("shortcut_nav_now_playing"))
                        Spacer(Modifier.height(8.dp))
                        ShortcutRow("H", str("shortcut_show_shortcuts"))
                        Spacer(Modifier.height(8.dp))
                        ShortcutRow("G then S", str("shortcut_nav_feed"))
                        ShortcutRow("G then P", str("shortcut_nav_profile"))
                        Spacer(Modifier.height(8.dp))
                        ShortcutRow("Q", str("shortcut_show_queue"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutRow(keys: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(0.4f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val keyParts = keys.split(" + ", " then ")
            keyParts.forEachIndexed { index, part ->
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = part,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index < keyParts.lastIndex) {
                    val separator = if (keys.contains(" then ")) " ${str("shortcut_key_then")} " else " + "
                    Text(
                        text = separator,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

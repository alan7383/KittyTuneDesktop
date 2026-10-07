package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.ui.common.ScrollableColumn
import com.alananasss.kittytune.ui.common.SettingsSwitch

/**
 * The lyrics sources in the order they are tried, each one switchable, reordered by dragging. Saved on
 * "Save". Opened from the lyrics settings and from the quick lyrics settings in any lyrics view, which used to
 * offer only a choice between Musixmatch and LrcLib (issue #66).
 */
@Composable
fun LyricsProviderOrderDialog(prefs: PlayerPreferences, onDismiss: () -> Unit) {
    var currentOrder by remember { mutableStateOf(prefs.getLyricsProviderOrder().toMutableList()) }
    var currentEnabled by remember {
        mutableStateOf(PreferredLyricsProvider.entries.associateWith { prefs.getLyricsProviderEnabled(it) })
    }
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("pref_lyrics_order", "Provider Priority Order")) },
        text = {
            ScrollableColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                contentPadding = PaddingValues(end = 12.dp),
            ) {
                sh.calvin.reorderable.ReorderableColumn(
                    list = currentOrder,
                    onSettle = { from, to ->
                        currentOrder = currentOrder.toMutableList().apply { add(to, removeAt(from)) }
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { index, provider, isDragging ->
                    key(provider) {
                        ReorderableItem {
                            val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "providerDrag")
                            val isEnabled = currentEnabled[provider] ?: true
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = elevation,
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .padding(horizontal = 4.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                        Icon(
                                            Icons.Rounded.DragIndicator,
                                            contentDescription = str("action_reorder"),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.weight(1f).alpha(if (isEnabled) 1f else 0.45f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.width(28.dp),
                                        )
                                        Text(
                                            provider.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    SettingsSwitch(
                                        checked = isEnabled,
                                        onCheckedChange = { checked -> currentEnabled = currentEnabled + (provider to checked) },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.setLyricsProviderOrder(currentOrder)
                currentEnabled.forEach { (provider, enabled) -> prefs.setLyricsProviderEnabled(provider, enabled) }
                onDismiss()
            }) {
                Text(str("btn_save", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str("btn_cancel", "Cancel"))
            }
        },
    )
}

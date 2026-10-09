package com.alananasss.kittytune.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.SearchSourceOrder
import com.alananasss.kittytune.ui.home.SearchSource

/**
 * Where the search's sources are moved and hidden: the arrows set the order, the box says whether a source is offered
 * at all. SoundCloud stays, since the search needs one source that is always there.
 */
@Composable
fun SearchSourceOrderDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val order = remember { mutableStateListOf(*SearchSourceOrder.ordered().toTypedArray()) }
    val hidden = remember { mutableStateOf(SearchSourceOrder.hidden()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("search_sources_title"), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                itemsIndexed(order, key = { _, source -> source.name }) { index, source ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = source !in hidden.value || source == SearchSource.SOUNDCLOUD,
                            onCheckedChange = { on ->
                                if (source == SearchSource.SOUNDCLOUD) return@Checkbox
                                hidden.value = if (on) hidden.value - source else hidden.value + source
                            },
                            enabled = source != SearchSource.SOUNDCLOUD,
                        )
                        Text(
                            searchSourceName(source),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) order.swap(index, index - 1)
                            },
                            enabled = index > 0,
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(Icons.Rounded.ArrowUpward, str("search_source_move_up"), modifier = Modifier.size(18.dp)) }
                        IconButton(
                            onClick = {
                                if (index < order.lastIndex) order.swap(index, index + 1)
                            },
                            enabled = index < order.lastIndex,
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(Icons.Rounded.ArrowDownward, str("search_source_move_down"), modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    SearchSourceOrder.save(order.toList(), hidden.value)
                    onSaved()
                    onDismiss()
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(str("btn_save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(str("btn_cancel")) }
        },
    )
}

private fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.swap(a: Int, b: Int) {
    val tmp = this[a]
    this[a] = this[b]
    this[b] = tmp
}

/** Platform names are brands, so they are not translated. */
internal fun searchSourceName(source: SearchSource): String = when (source) {
    SearchSource.SOUNDCLOUD -> "SoundCloud"
    SearchSource.YOUTUBE, SearchSource.YOUTUBE_MUSIC -> "YouTube Music"
    SearchSource.SPOTIFY -> "Spotify"
    SearchSource.APPLE_MUSIC -> "Apple Music"
    SearchSource.YANDEX_MUSIC -> "Yandex Music"
    SearchSource.DEEZER -> "Deezer"
    SearchSource.TIDAL -> "TIDAL"
    SearchSource.QOBUZ -> "Qobuz"
}

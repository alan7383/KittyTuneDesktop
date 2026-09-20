package com.alananasss.kittytune.ui.profile.integrations

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.AudioProviderOrder
import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsScaffold
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ProviderOrderScreen(
    onBackClick: () -> Unit
) {
    val prefs = remember { PlayerPreferences() }
    val currentList = remember { mutableStateListOf<AudioProviderOrderItem>() }

    LaunchedEffect(Unit) {
        currentList.clear()
        currentList.addAll(prefs.getAudioProviderOrder())
    }

    fun persistOrder() {
        prefs.setAudioProviderOrder(currentList.toList())
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentList.indices && toIndex in currentList.indices && fromIndex != toIndex) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            persistOrder()
        }
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val moved = currentList.removeAt(from.index)
            currentList.add(to.index, moved)
            persistOrder()
        }
    )

    SettingsScaffold(
        title = str("provider_order"),
        onBackClick = onBackClick
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = str("provider_order_summary"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(currentList, key = { _, item -> item.name }) { index, item ->
                    ReorderableItem(state = reorderableState, key = item.name) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                        val backgroundColor = if (isDragging) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }

                        val (nameKey, iconRes) = when (item) {
                            AudioProviderOrderItem.QOBUZ -> Pair("audio_provider_qobuz", R.drawable.ic_logo_qobuz)
                            AudioProviderOrderItem.TIDAL -> Pair("audio_provider_tidal", R.drawable.ic_logo_tidal)
                            AudioProviderOrderItem.DEEZER -> Pair("audio_provider_deezer", R.drawable.ic_logo_deezer)
                            AudioProviderOrderItem.YOUTUBE_MUSIC -> Pair("audio_provider_youtube_music", R.drawable.ic_logo_youtube_music)
                            AudioProviderOrderItem.SOUNDCLOUD -> Pair("audio_provider_soundcloud", "drawable/ic_soundcloud.xml")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(backgroundColor)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(32.dp)
                            )
                            Icon(
                                painter = painterResource(iconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = str(nameKey),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            // Quick Move Up/Down buttons for desktop mouse convenience
                            IconButton(
                                onClick = { moveItem(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Move Up",
                                    tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { moveItem(index, index + 1) },
                                enabled = index < currentList.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDownward,
                                    contentDescription = "Move Down",
                                    tint = if (index < currentList.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = "Drag Handle",
                                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(28.dp)
                                    .draggableHandle()
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = {
                    currentList.clear()
                    currentList.addAll(AudioProviderOrder.Default)
                    persistOrder()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(str("provider_order_reset"))
            }
        }
    }
}

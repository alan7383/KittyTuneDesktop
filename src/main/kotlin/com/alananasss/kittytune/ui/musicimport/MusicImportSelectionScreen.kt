package com.alananasss.kittytune.ui.musicimport

import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.stringResource
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.ExternalPlaylist
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold

@Composable
fun MusicImportSelectionScreen(
    platformProviderName: String,
    onBackClick: (() -> Unit)? = null,
    onStartTransfer: () -> Unit,
    viewModel: MusicImportSelectionViewModel = viewModel { MusicImportSelectionViewModel(AppInstance.application) }
) {
    LaunchedEffect(platformProviderName) {
        viewModel.init(platformProviderName)
    }

    val platform = viewModel.platform

    val scrollState = androidx.compose.foundation.rememberScrollState()
    SettingsScaffold(
        title = platform?.let { stringResource(it.labelRes()) }
            ?: stringResource(R.string.music_import_title),
        onBackClick = onBackClick,
        scrollState = scrollState,
        actions = {
            IconButton(
                onClick = {
                    viewModel.logout(onLoggedOut = { onBackClick?.invoke() })
                },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = stringResource(R.string.btn_logout),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(innerPadding).align(Alignment.Center))
            } else {
                com.alananasss.kittytune.ui.common.ScrollableColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState,
                    hideScrollbar = true,
                    contentPadding = innerPadding
                ) {
                    if (viewModel.error != null) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = if (viewModel.retryAfter != null) stringResource(R.string.music_import_load_error) + " (Retry after ${viewModel.retryAfter}s) - ${viewModel.error}" else stringResource(R.string.music_import_load_error) + " - ${viewModel.error}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadExternalContent() },
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Text(stringResource(R.string.btn_retry))
                            }
                        }
                    }

                    if (viewModel.likedTracksCount > 0) {
                        SettingsGroup(
                            title = stringResource(R.string.music_import_likes_header),
                            items = listOf(
                                { shape ->
                                    SettingsItem(
                                        shape = shape,
                                        title = stringResource(R.string.music_import_likes_title),
                                        subtitle = stringResource(R.string.music_import_likes_subtitle, viewModel.likedTracksCount),
                                        hasSwitch = true,
                                        switchState = viewModel.includeLikes,
                                        onSwitchChange = { viewModel.toggleLikes() }
                                    )
                                }
                            )
                        )
                    }

                    SettingsGroup(
                        title = stringResource(R.string.music_import_playlists_header),
                        items = viewModel.playlists.map { playlist ->
                            { shape ->
                                PlaylistSelectionItem(
                                    shape = shape,
                                    playlist = playlist,
                                    selected = playlist.id in viewModel.selectedPlaylistIds,
                                    onToggle = { viewModel.togglePlaylist(playlist.id) }
                                )
                            }
                        }
                    )

                    if (viewModel.playlists.isEmpty() && viewModel.likedTracksCount == 0) {
                        Text(
                            text = stringResource(R.string.music_import_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                    
                    Spacer(Modifier.height(180.dp))
                }

                val totalSelected = viewModel.selectedPlaylistIds.size + (if (viewModel.includeLikes) 1 else 0)
                val hasSelection = totalSelected > 0

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (hasSelection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                text = stringResource(R.string.music_import_selected_count, totalSelected),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (viewModel.playlists.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.toggleSelectAll() },
                                    shapes = ButtonDefaults.shapes(),
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(
                                        text = if (viewModel.isAllPlaylistsSelected) {
                                            stringResource(R.string.music_import_deselect_all)
                                        } else {
                                            stringResource(R.string.music_import_select_all)
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = { viewModel.importSelected(onStartTransfer) },
                            enabled = hasSelection && !viewModel.isImporting,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .height(44.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            if (viewModel.isImporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.music_import_start),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
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
private fun PlaylistSelectionItem(
    shape: androidx.compose.ui.graphics.Shape,
    playlist: ExternalPlaylist,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                if (playlist.imageUrl != null) {
                    AsyncImage(
                        model = playlist.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.music_import_playlist_count,
                        playlist.totalItems ?: 0
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

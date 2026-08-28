@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.local.LibraryFolder
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.main.loadPlaylistTracksForMenu
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun CreatePlaylistDialog(
    isCreating: Boolean = false,
    onDismiss: () -> Unit,
    onCreate: (name: String, isPublic: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
        title = { Text(str("lib_create_playlist_title")) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(str("lib_create_playlist_hint")) },
                    singleLine = true,
                    enabled = !isCreating,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().trackTextInput().focusRequester(focusRequester),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(str("lib_playlist_public"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            str("lib_playlist_public_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        enabled = !isCreating,
                        colors = SwitchDefaults.colors(
                            checkedIconColor = MaterialTheme.colorScheme.primary,
                            uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ),
                        thumbContent = {
                            Icon(
                                if (isPublic) Icons.Rounded.Check else Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, isPublic) },
                shapes = ButtonDefaults.shapes(),
                enabled = name.isNotBlank() && !isCreating,
            ) {
                if (isCreating) {
                    ContainedLoadingIndicator()
                    Spacer(Modifier.width(8.dp))
                }
                Text(str("lib_create"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
                enabled = !isCreating
            ) {
                Text(str("btn_cancel"))
            }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
        title = { Text(str("lib_create_folder_title")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(str("lib_create_folder_hint")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().trackTextInput().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name) },
                shapes = ButtonDefaults.shapes(),
                enabled = name.isNotBlank(),
            ) {
                Text(str("lib_create"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(str("btn_cancel"))
            }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun RenameFolderDialog(
    folder: LibraryFolder,
    onDismiss: () -> Unit,
    onRename: (newName: String) -> Unit,
) {
    var name by remember { mutableStateOf(folder.name) }
    val focusRequester = remember { FocusRequester() }

    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
        title = { Text(str("dialog_rename_folder_title")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(str("dialog_rename_folder_hint")) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().trackTextInput().focusRequester(focusRequester),
            )
        },
        confirmButton = {
            Button(
                onClick = { onRename(name) },
                shapes = ButtonDefaults.shapes(),
                enabled = name.isNotBlank(),
            ) {
                Text(str("btn_save"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(str("btn_cancel"))
            }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun DeleteFolderDialog(
    folder: LibraryFolder,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(str("dialog_delete_folder_title")) },
        text = { Text(str("dialog_delete_folder_msg")) },
        confirmButton = {
            Button(
                onClick = onDelete,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(str("btn_delete"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(str("btn_cancel"))
            }
        },
    )
}

@Composable
fun EmptyFolderView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = str("empty_folder_kaomoji"),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = str("empty_folder_title"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = str("empty_folder_subtitle"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun FolderOptionsMenu(
    folder: LibraryFolder,
    onDismiss: () -> Unit,
    onPlayOrdered: () -> Unit,
    onPlayShuffle: () -> Unit,
    onPlayRecursiveOrdered: () -> Unit,
    onPlayRecursiveShuffle: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(folder.name, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                FolderOptionItem(
                    icon = Icons.Rounded.PlayArrow,
                    label = str("lib_folder_play_ordered"),
                    onClick = { onDismiss(); onPlayOrdered() }
                )
                FolderOptionItem(
                    icon = Icons.Rounded.Shuffle,
                    label = str("lib_folder_play_shuffle"),
                    onClick = { onDismiss(); onPlayShuffle() }
                )
                FolderOptionItem(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    label = str("lib_folder_play_recursive_ordered"),
                    onClick = { onDismiss(); onPlayRecursiveOrdered() }
                )
                FolderOptionItem(
                    icon = Icons.Rounded.Shuffle,
                    label = str("lib_folder_play_recursive_shuffle"),
                    onClick = { onDismiss(); onPlayRecursiveShuffle() }
                )
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                FolderOptionItem(
                    icon = Icons.Rounded.Bookmark,
                    label = if (folder.isPinned) str("menu_unpin_folder") else str("menu_pin_folder"),
                    onClick = { onDismiss(); onTogglePin() }
                )
                FolderOptionItem(
                    icon = Icons.Rounded.Edit,
                    label = str("menu_rename_folder"),
                    onClick = { onDismiss(); onRename() }
                )
                FolderOptionItem(
                    icon = Icons.Rounded.DeleteOutline,
                    label = str("menu_delete_folder"),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { onDismiss(); onDelete() }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(str("btn_cancel"))
            }
        }
    )
}

@Composable
fun MoveToFolderDialog(
    availableFolders: List<LibraryFolder>,
    isInsideFolder: Boolean,
    onDismiss: () -> Unit,
    onMoveToRoot: () -> Unit,
    onMoveToFolder: (Long) -> Unit,
    onCreateNewFolder: () -> Unit,
) {
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(str("menu_move_to_folder"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                FolderOptionItem(
                    icon = Icons.Rounded.CreateNewFolder,
                    label = str("lib_create_folder_title"),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { onDismiss(); onCreateNewFolder() }
                )
                if (isInsideFolder) {
                    FolderOptionItem(
                        icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                        label = str("menu_move_to_library"),
                        onClick = { onDismiss(); onMoveToRoot() }
                    )
                }
                if (availableFolders.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    availableFolders.forEach { targetFolder ->
                        FolderOptionItem(
                            icon = Icons.Rounded.Folder,
                            label = targetFolder.name,
                            onClick = { onDismiss(); onMoveToFolder(targetFolder.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(str("btn_cancel"))
            }
        }
    )
}

@Composable
private fun FolderOptionItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FolderPlaySplitButton(
    folder: LibraryFolder,
    libraryViewModel: LibraryViewModel,
    playerViewModel: com.alananasss.kittytune.ui.player.PlayerViewModel,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        val splitColors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = {
                        libraryViewModel.playFolder(
                            folder.id,
                            playerViewModel,
                            shuffle = false,
                            recursive = false
                        )
                    },
                    colors = splitColors
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = str("lib_folder_play_ordered"),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = isMenuExpanded,
                    onCheckedChange = { isMenuExpanded = it },
                    colors = splitColors
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )

        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = { isMenuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(str("lib_folder_play_ordered")) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {
                    isMenuExpanded = false
                    libraryViewModel.playFolder(
                        folder.id,
                        playerViewModel,
                        shuffle = false,
                        recursive = false
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(str("lib_folder_play_shuffle")) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null
                    )
                },
                onClick = {
                    isMenuExpanded = false
                    libraryViewModel.playFolder(
                        folder.id,
                        playerViewModel,
                        shuffle = true,
                        recursive = false
                    )
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(str("lib_folder_play_recursive_ordered")) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null
                    )
                },
                onClick = {
                    isMenuExpanded = false
                    libraryViewModel.playFolder(
                        folder.id,
                        playerViewModel,
                        shuffle = false,
                        recursive = true
                    )
                }
            )
            DropdownMenuItem(
                text = { Text(str("lib_folder_play_recursive_shuffle")) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null
                    )
                },
                onClick = {
                    isMenuExpanded = false
                    libraryViewModel.playFolder(
                        folder.id,
                        playerViewModel,
                        shuffle = true,
                        recursive = true
                    )
                }
            )
        }
    }
}

data class LibraryPlaylistActionItem(
    val icon: ImageVector,
    val text: String,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

@Composable
fun LibraryPlaylistOptionsDialog(
    playlist: com.alananasss.kittytune.domain.Playlist,
    libraryViewModel: LibraryViewModel,
    playerViewModel: com.alananasss.kittytune.ui.player.PlayerViewModel,
    onDismiss: () -> Unit,
    onShowDetails: (com.alananasss.kittytune.domain.Playlist) -> Unit,
    onMoveToFolder: (String) -> Unit
) {
    val canonicalKey = LibraryItem.getPlaylistCanonicalKey(playlist)
    val isItemPinned = libraryViewModel.displayedItems.find { it.key == canonicalKey }?.isPinned ?: false
    val isInsideFolder = libraryViewModel.currentFolderId != null
    val permalink = playlist.permalinkUrl ?: if (playlist.id > 0) "https://soundcloud.com/playlists/${playlist.id}" else ""
    val likedPlaylistsRepo by com.alananasss.kittytune.data.LikeRepository.likedPlaylists.collectAsState()
    val isPlaylistLiked = remember(playlist.id, likedPlaylistsRepo) {
        com.alananasss.kittytune.data.LikeRepository.isPlaylistLiked(playlist.id)
    }

    val downloadedIds by com.alananasss.kittytune.data.DownloadManager.downloadedIds.collectAsState()
    val storageTrigger by com.alananasss.kittytune.data.DownloadManager.storageTrigger.collectAsState()
    val isLocal = playlist.id < 0
    val isStation = playlist.isTrackStation || playlist.isArtistStation ||
            playlist.permalinkUrl?.contains("station") == true || playlist.urn?.contains("station") == true

    // The library's own copy of the menu, and it waited to draw exactly as the other one did
    // (issue #33). The tiles that act on the track list were gated on having the list, so a playlist
    // whose tracks are not already in hand showed a short menu and a spinner, then reflowed.
    //
    // Same treatment: draw now, wait on the click. Kept as two implementations rather than merged,
    // because the library menu offers pinning, folders and liking that the other one does not, and a
    // shared version would be mostly branches. But the waiting has to behave the same in both, so if
    // one of them changes again, so does the other.
    val menuScope = rememberCoroutineScope()
    val tracksReady = remember(playlist.id) {
        kotlinx.coroutines.CompletableDeferred<List<com.alananasss.kittytune.domain.Track>>()
    }
    LaunchedEffect(playlist.id) {
        val fetched = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.alananasss.kittytune.ui.main.loadPlaylistTracksForMenu(playlist)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
        tracksReady.complete(fetched)
    }
    val loadedTracks by produceState<List<com.alananasss.kittytune.domain.Track>?>(initialValue = null, playlist.id) {
        value = tracksReady.await()
    }
    val tracks = loadedTracks ?: emptyList()
    val isLoadingTracks = loadedTracks == null

    /** True while the answer is unknown, because it is almost always yes. See the note above. */
    val offerTrackActions = isLoadingTracks || tracks.isNotEmpty()

    /** Runs [action] once the list is in, so a tile can be pressed before the fetch has landed. */
    val withTracks: ((List<com.alananasss.kittytune.domain.Track>) -> Unit) -> Unit = { action ->
        val already = loadedTracks
        if (already != null) {
            if (already.isNotEmpty()) action(already)
        } else {
            menuScope.launch {
                val fetched = tracksReady.await()
                if (fetched.isNotEmpty()) action(fetched)
            }
        }
    }

    val isFullyDownloaded = remember(tracks, downloadedIds, storageTrigger) {
        tracks.isNotEmpty() && tracks.all { it.id < 0 || downloadedIds.contains(it.id) }
    }
    val isPlaylistDownloading = com.alananasss.kittytune.data.DownloadManager.isPlaylistDownloading(playlist.id)
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showRemoveDownloadDialog = false },
            title = { Text(str("dialog_remove_download_title")) },
            text = { Text(str("dialog_remove_download_msg")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        com.alananasss.kittytune.data.DownloadManager.removePlaylistDownloads(playlist.id)
                        showRemoveDownloadDialog = false
                        onDismiss()
                    },
                    shapes = ButtonDefaults.shapes()
                ) { Text(str("btn_delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) { Text(str("btn_cancel")) }
            }
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val gridItems = remember(playlist, tracks, isItemPinned, isInsideFolder, primaryColor, errorColor, isPlaylistLiked, isFullyDownloaded, isPlaylistDownloading) {
        mutableListOf<LibraryPlaylistActionItem>().apply {
            if (offerTrackActions) {
                add(LibraryPlaylistActionItem(Icons.Rounded.PlayArrow, str("btn_play")) {
                    withTracks { playerViewModel.playPlaylist(it, 0); onDismiss() }
                })
                add(LibraryPlaylistActionItem(Icons.Rounded.Shuffle, str("btn_shuffle")) {
                    withTracks { playerViewModel.playPlaylist(it.shuffled(), 0); onDismiss() }
                })
                add(LibraryPlaylistActionItem(Icons.AutoMirrored.Rounded.PlaylistPlay, str("menu_play_next")) {
                    withTracks { playerViewModel.insertNext(it); onDismiss() }
                })
                add(LibraryPlaylistActionItem(Icons.AutoMirrored.Rounded.QueueMusic, str("menu_add_queue")) {
                    withTracks { playerViewModel.addToQueue(it); onDismiss() }
                })
                add(LibraryPlaylistActionItem(Icons.Default.Add, str("menu_add_playlist")) {
                    withTracks { onDismiss(); playerViewModel.prepareBulkAdd(it) }
                })
            }
            if (playlist.id > 0 || playlist.urn?.startsWith("soundcloud:system-playlists:") == true) {
                add(
                    LibraryPlaylistActionItem(
                        icon = if (isPlaylistLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        text = if (isPlaylistLiked) str("action_unlike") else str("player_like_action"),
                        tint = if (isPlaylistLiked) primaryColor else null
                    ) {
                        com.alananasss.kittytune.data.LikeRepository.togglePlaylistLike(
                            playlist.id,
                            !isPlaylistLiked,
                            playlist.permalinkUrl ?: "",
                            playlist.urn ?: ""
                        )
                    }
                )
                add(LibraryPlaylistActionItem(Icons.Rounded.Info, str("menu_playlist_details")) {
                    onDismiss()
                    onShowDetails(playlist)
                })
            }
            if (!isInsideFolder) {
                add(
                    LibraryPlaylistActionItem(
                        icon = Icons.Rounded.PushPin,
                        text = if (isItemPinned) str("menu_unpin_playlist") else str("menu_pin_playlist"),
                        tint = if (isItemPinned) primaryColor else null
                    ) {
                        libraryViewModel.togglePinItem(canonicalKey)
                        onDismiss()
                    }
                )
            }
            add(LibraryPlaylistActionItem(Icons.Rounded.Folder, str("menu_move_to_folder")) {
                onDismiss()
                onMoveToFolder(canonicalKey)
            })
            if (isInsideFolder) {
                add(LibraryPlaylistActionItem(Icons.AutoMirrored.Rounded.DriveFileMove, str("menu_move_to_library")) {
                    libraryViewModel.moveItemToFolder(canonicalKey, null)
                    onDismiss()
                })
            }
            playlist.user?.id?.takeIf { it > 0 }?.let { ownerId ->
                add(LibraryPlaylistActionItem(Icons.Default.Person, str("menu_go_artist")) {
                    onDismiss()
                    playerViewModel.navigateToPlaylistId = "profile:$ownerId"
                })
            }
            if (permalink.isNotEmpty()) {
                add(LibraryPlaylistActionItem(Icons.Outlined.Share, str("btn_share")) {
                    playerViewModel.sharePlaylist(playlist)
                    onDismiss()
                })
            }
            if (!isLocal && offerTrackActions && !isStation) {
                val icon = if (isFullyDownloaded) Icons.Default.Delete else Icons.Rounded.Download
                val tint = if (isFullyDownloaded) errorColor else null
                val label = when {
                    isFullyDownloaded -> str("btn_delete")
                    isPlaylistDownloading -> str("btn_cancel")
                    else -> str("btn_download")
                }
                add(LibraryPlaylistActionItem(icon, label, tint) {
                    if (isFullyDownloaded) {
                        showRemoveDownloadDialog = true
                    } else if (!isPlaylistDownloading) {
                        withTracks {
                            com.alananasss.kittytune.data.DownloadManager.downloadPlaylist(playlist, it)
                            onDismiss()
                        }
                    }
                })
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.width(420.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp).padding(horizontal = 4.dp)
                ) {
                    coil3.compose.AsyncImage(
                        model = playlist.fullResArtwork,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title ?: str("generic_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        val subtitleParts = mutableListOf<String>()
                        subtitleParts.add(str("lib_playlists"))
                        playlist.user?.username?.let { subtitleParts.add(it) }
                        val count = if (tracks.isNotEmpty()) tracks.size else (playlist.trackCount ?: 0)
                        if (count > 0) subtitleParts.add(str("playlist_num_tracks", count))
                        Text(
                            text = subtitleParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                // Keeps its height either way, so appearing and disappearing cannot move the grid.
                Row(
                    modifier = Modifier.fillMaxWidth().height(28.dp).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isLoadingTracks) CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                }

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(gridItems) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { item.onClick() }
                                .padding(vertical = 6.dp)
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.text,
                                modifier = Modifier.size(30.dp),
                                tint = item.tint ?: MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                item.text,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = item.tint ?: MaterialTheme.colorScheme.onSurface,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}


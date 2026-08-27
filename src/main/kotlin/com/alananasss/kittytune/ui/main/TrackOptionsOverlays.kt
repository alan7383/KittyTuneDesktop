@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.common.Slider
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.alananasss.kittytune.core.trackTextInput
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Verified
import com.alananasss.kittytune.core.EscapableAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.DownloadManager
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import com.alananasss.kittytune.domain.Comment
import com.alananasss.kittytune.ui.player.CommentSort
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.RepeatMode

private data class MenuOptionItem(
    val icon: ImageVector,
    val text: String,
    val onClick: () -> Unit,
)

/** Spotify brand green, used for catalog badges and verified marks. */
private val SpotifyAccentGreen = androidx.compose.ui.graphics.Color(0xFF1DB954)

/**
 * Desktop replacements for the Android modal bottom sheets: track options menu,
 * add-to-playlist picker, repost dialog, comments sheet and sleep timer. Rendered once at the
 * root of MainScreen; each shows as a centered dialog card.
 */
@Composable
fun TrackOptionsOverlays(viewModel: PlayerViewModel) {
    if (viewModel.showMenuSheet) {
        BackHandler(onBack = { viewModel.showMenuSheet = false })
        Dialog(onDismissRequest = { viewModel.showMenuSheet = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(420.dp),
            ) {
                MenuSheetContent(viewModel)
            }
        }
    }
    if (viewModel.showCommentsSheet) {
        BackHandler(onBack = { viewModel.showCommentsSheet = false })
        Dialog(onDismissRequest = { viewModel.showCommentsSheet = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(540.dp).heightIn(max = 640.dp),
            ) {
                CommentsSheetContent(viewModel)
            }
        }
    }
    if (viewModel.showAddToPlaylistSheet) {
        BackHandler(onBack = { viewModel.showAddToPlaylistSheet = false })
        Dialog(onDismissRequest = { viewModel.showAddToPlaylistSheet = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(420.dp),
            ) {
                AddToPlaylistContent(viewModel)
            }
        }
    }
    if (viewModel.showPlaylistMenuSheet) {
        BackHandler(onBack = { viewModel.showPlaylistMenuSheet = false })
        Dialog(onDismissRequest = { viewModel.showPlaylistMenuSheet = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(420.dp),
            ) {
                PlaylistMenuSheetContent(viewModel)
            }
        }
    }
    if (viewModel.showSelectArtistDialog) {
        SelectArtistDialog(viewModel)
    }
    SleepTimerDialog(viewModel)
    TrackTrimDialog(viewModel)
}

/**
 * Shown when the user opens an artist from a track credited to several
 * Spotify artists (feat / duets): pick which artist profile to open.
 */
@Composable
private fun SelectArtistDialog(viewModel: PlayerViewModel) {
    // The artist list is authoritative: headers open the dialog without a track.
    val track = viewModel.selectedArtistDialogTrack
    val artistsList = viewModel.selectArtistOptions.takeIf { it.isNotEmpty() }
        ?: track?.artists?.takeIf { it.isNotEmpty() }
        ?: listOfNotNull(
            track?.user?.let { u ->
                com.alananasss.kittytune.data.spotify.SpotifyArtistRef(
                    id = u.permalink ?: u.urn?.removePrefix("spotify:artist:") ?: "",
                    name = u.username ?: str("unknown_artist"),
                    avatarUrl = u.avatarUrl,
                    verified = u.verified
                )
            }
        )
    if (artistsList.isEmpty()) return

    EscapableAlertDialog(
        onDismissRequest = { viewModel.dismissSelectArtistDialog() },
        title = { Text(str("select_artist_title")) },
        text = {
            // A plain Column, not a lazy list: there are only ever a handful of credited
            // artists, and ScrollableLazyColumn fills its constraints — which is what made
            // this dialog claim 360dp of height and near-max width for two rows.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .widthIn(min = 240.dp, max = 320.dp)
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                artistsList.forEach { artist ->
                    ArtistPickerRow(artist = artist, viewModel = viewModel)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { viewModel.dismissSelectArtistDialog() }, shapes = ButtonDefaults.shapes()) {
                Text(str("btn_cancel"))
            }
        }
    )
}

/**
 * One row of the artist picker. The artist refs come from track/album payloads, which
 * carry no images, so the avatar is fetched on demand (memoized in the repository) and
 * the silhouette only stays if there is genuinely nothing to show.
 */
@Composable
private fun ArtistPickerRow(
    artist: com.alananasss.kittytune.data.spotify.SpotifyArtistRef,
    viewModel: PlayerViewModel
) {
    val avatar by produceState(artist.avatarUrl, artist.id) {
        if (value.isNullOrBlank() && artist.id.isNotBlank()) {
            value = runCatching {
                com.alananasss.kittytune.data.spotify.SpotifyRepository.getArtistAvatar(artist.id)
            }.getOrNull()
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interaction, indication = null) {
                viewModel.dismissSelectArtistDialog()
                val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(
                    artist.id.ifBlank { artist.uri ?: "" }
                )
                if (cleanId.isNotBlank()) viewModel.navigateToSpotifyArtist(cleanId)
            }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!avatar.isNullOrBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (artist.verified) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.Verified,
                contentDescription = null,
                tint = SpotifyAccentGreen,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun MenuSheetContent(viewModel: PlayerViewModel) {
    val track = viewModel.trackForMenu ?: viewModel.currentTrack ?: return
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val storageTrigger by DownloadManager.storageTrigger.collectAsState()
    val likedTracks by com.alananasss.kittytune.data.LikeRepository.likedTracks.collectAsState()
    val isTrackLiked = remember(track.id, likedTracks) { com.alananasss.kittytune.data.LikeRepository.isTrackLiked(track.id) }
    val isLocalFile = track.id < 0 && track.source != "youtube"
    val isSpotify = viewModel.isSpotifyTrack(track)

    val isReposted = viewModel.isTrackReposted(track.id) || track.userReposted
    if (track.userReposted) {
        com.alananasss.kittytune.data.RepostRepository.syncLocalState(track.id, true)
    }
    var showRepostDialog by remember { mutableStateOf(false) }
    var showDeleteRepostConfirm by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isDownloaded by produceState(initialValue = false, track.id, storageTrigger) {
        val localTrack = DownloadManager.getLocalTrack(track.id)
        value = localTrack?.localAudioPath?.isNotEmpty() == true
    }

    if (showDeleteDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(if (isLocalFile) str("menu_remove_local_q") else str("menu_remove_download_q")) },
            text = { Text(if (isLocalFile) str("menu_remove_local_body") else str("menu_remove_download_body")) },
            confirmButton = {
                TextButton(onClick = { DownloadManager.deleteTrack(track.id); showDeleteDialog = false; viewModel.showMenuSheet = false }) {
                    Text(str("btn_delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showRepostDialog) {
        RepostDialog(
            onDismiss = { showRepostDialog = false },
            onConfirm = { caption -> viewModel.repostTrack(track, caption); showRepostDialog = false; viewModel.showMenuSheet = false }
        )
    }

    if (showDeleteRepostConfirm) {
        EscapableAlertDialog(
            onDismissRequest = { showDeleteRepostConfirm = false },
            title = { Text(str("dialog_repost_delete_title")) },
            text = { Text(str("dialog_repost_delete_msg")) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteRepost(track.id); showDeleteRepostConfirm = false; viewModel.showMenuSheet = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(str("btn_delete")) }
            },
            dismissButton = { TextButton(onClick = { showDeleteRepostConfirm = false }) { Text(str("btn_cancel")) } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp).padding(horizontal = 4.dp)) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = track.title ?: str("untitled_track"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.alananasss.kittytune.ui.common.ArtistLinkText(
                        track = track,
                        onArtistClick = { viewModel.navigateToTrackArtist(it) }
                    )
                    if (track.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        val gridItems = mutableListOf<MenuOptionItem>().apply {
            if (!isLocalFile && !viewModel.isMenuContextFromPlayer) {
                add(
                    MenuOptionItem(
                        if (isTrackLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        if (isTrackLiked) str("action_unlike") else str("player_like_action")
                    ) {
                        viewModel.toggleTrackLike(track)
                    }
                )
            }
            if (viewModel.isMenuContextFromPlayer) {
                add(MenuOptionItem(Icons.Rounded.Shuffle, str("menu_shuffle")) { viewModel.toggleShuffle() })
                add(MenuOptionItem(Icons.Rounded.Repeat, str("menu_repeat")) { viewModel.toggleRepeatMode() })
            }
            if (!viewModel.isMenuContextFromPlayer) {
                add(MenuOptionItem(Icons.AutoMirrored.Rounded.PlaylistPlay, str("menu_play_next")) { viewModel.insertNext(listOf(track)); viewModel.showMenuSheet = false })
                add(MenuOptionItem(Icons.AutoMirrored.Rounded.QueueMusic, str("menu_add_queue")) { viewModel.addToQueue(listOf(track)); viewModel.showMenuSheet = false })
            }
            if (track.source != "youtube" && !isSpotify && !isLocalFile) {
                add(MenuOptionItem(Icons.AutoMirrored.Rounded.Comment, str("menu_comments")) { viewModel.openComments(track) })
                if (isReposted) {
                    add(MenuOptionItem(Icons.Rounded.Repeat, str("menu_reposted")) { showDeleteRepostConfirm = true })
                } else {
                    add(MenuOptionItem(Icons.Rounded.Repeat, str("menu_repost")) { showRepostDialog = true })
                }
            }
            if (track.source != "youtube" && !isSpotify) {
                add(MenuOptionItem(Icons.Rounded.Info, str("menu_details")) { viewModel.openTrackDetails(track) })
            }
            add(MenuOptionItem(Icons.Rounded.Description, str("player_lyrics")) { viewModel.openLyrics(track, forceSheet = true) })
            add(MenuOptionItem(Icons.Default.Add, str("menu_add_playlist")) { viewModel.showMenuSheet = false; viewModel.showAddToPlaylistSheet = true })

            // Catalog tracks carry their album id: jump straight to it.
            val albumId = track.publisherMetadata?.albumId?.takeIf { it.isNotBlank() }
            if (!isLocalFile && albumId != null && (isSpotify || albumId.length == 22)) {
                add(MenuOptionItem(Icons.Rounded.Album, str("menu_go_album")) {
                    viewModel.showMenuSheet = false
                    viewModel.navigateToAlbum(albumId)
                })
            }
            if (track.source != "youtube" && !isLocalFile) {
                add(
                    MenuOptionItem(Icons.Default.Person, str("menu_go_artist")) {
                        if (isSpotify) {
                            viewModel.navigateToTrackArtist(track)
                            viewModel.showMenuSheet = false
                        } else {
                            track.user?.id?.let { viewModel.navigateToArtist(it) }
                        }
                    }
                )
                val isOwnTrack = track.user?.id != null && track.user?.id == viewModel.currentUserId && track.id > 0
                if (isOwnTrack) {
                    add(MenuOptionItem(Icons.Default.Edit, str("menu_edit_track")) {
                        viewModel.showMenuSheet = false
                        viewModel.navigateToEditTrack(track.id)
                    })
                }
            }
            if (!isLocalFile) {
                add(MenuOptionItem(Icons.Rounded.Radio, str("menu_track_radio")) {
                    if (track.source == "youtube") viewModel.startYoutubeRadio(track) else viewModel.startRadioFromTrack(track)
                })
                add(MenuOptionItem(Icons.Outlined.Share, str("btn_share")) { viewModel.shareTrack(track) })
            }
            if (viewModel.menuContextPlaylistId != null && (viewModel.menuContextPlaylistId!! < 0 || viewModel.menuContextPlaylistId == -2L)) {
                add(MenuOptionItem(Icons.Outlined.Delete, str("menu_remove")) { viewModel.removeFromContextPlaylist(viewModel.menuContextPlaylistId!!, track) })
            }
            if (viewModel.isMenuContextFromPlayer) {
                add(MenuOptionItem(Icons.Rounded.Bedtime, str("sleep_timer_title")) { viewModel.showSleepTimerDialog = true })
                // Only for the track that is playing: the editor's whole method is "listen, mark here", so
                // it needs a playhead to mark from (issue #33).
                add(MenuOptionItem(Icons.Rounded.ContentCut, str("trim_title")) {
                    viewModel.showMenuSheet = false
                    viewModel.showTrimDialog = true
                })
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            items(gridItems) { item ->
                val activeColor = MaterialTheme.colorScheme.primary
                val inactiveColor = MaterialTheme.colorScheme.onSurface
                var tint = inactiveColor
                var text = item.text

                if (item.text == str("action_unlike")) tint = activeColor
                if (item.text == str("menu_shuffle") && viewModel.shuffleEnabled) tint = activeColor
                if (item.text == str("menu_reposted")) tint = activeColor
                if (item.text == str("menu_repeat")) {
                    if (viewModel.repeatMode != RepeatMode.NONE) tint = activeColor
                    text = when (viewModel.repeatMode) {
                        RepeatMode.ALL -> str("menu_repeat_all")
                        RepeatMode.ONE -> str("menu_repeat_one")
                        else -> str("menu_repeat")
                    }
                }
                if (item.text == str("sleep_timer_title") && viewModel.isSleepTimerActive) {
                    tint = activeColor
                    text = viewModel.formatSleepTimerRemaining()
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { item.onClick() }.padding(vertical = 6.dp)
                ) {
                    Icon(item.icon, null, modifier = Modifier.size(30.dp), tint = tint)
                    Spacer(Modifier.height(8.dp))
                    Text(text = text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = tint, maxLines = 2)
                }
            }
            if (!isLocalFile) {
                item {
                    val trackId = track.id
                    val isDownloading = DownloadManager.isTrackDownloading(trackId)
                    val downloadProgressVal = downloadProgress[trackId]
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                            if (isDownloaded) showDeleteDialog = true
                            else if (isDownloading) DownloadManager.cancelDownload(trackId)
                            else viewModel.downloadTrack(track)
                        }.padding(vertical = 6.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
                            if (isDownloading) {
                                val animatedProgress by animateFloatAsState(targetValue = (downloadProgressVal ?: 0) / 100f, label = "progress")
                                CircularWavyProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxSize())
                                Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(18.dp))
                            } else {
                                val icon = if (isDownloaded) Icons.Default.Delete else Icons.Rounded.Download
                                val dlTint = if (isDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                Icon(icon, null, modifier = Modifier.fillMaxSize(), tint = dlTint)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val textLabel = if (isDownloaded) str("btn_delete") else if (isDownloading) str("btn_cancel") else str("btn_download")
                        val textColor = if (isDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        Text(textLabel, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistContent(viewModel: PlayerViewModel) {
    val singleTrack = viewModel.trackForMenu
    val bulkTracks = viewModel.tracksToAddInBulk
    if (singleTrack == null && bulkTracks == null) return
    var showCreateInput by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            if (bulkTracks != null) str("add_to_playlist_title_multi", bulkTracks.size) else str("add_to_playlist_title_single"),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (showCreateInput) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(str("lib_create_playlist_hint")) },
                    modifier = Modifier.weight(1f).trackTextInput(),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        if (bulkTracks != null) viewModel.createAndAddTracksToPlaylist(newName, bulkTracks)
                        else if (singleTrack != null) viewModel.createAndAddToPlaylist(newName, singleTrack)
                    }
                }) { Text(str("btn_ok")) }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Surface(
                onClick = { showCreateInput = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        str("add_to_playlist_new"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            itemsIndexed(items = viewModel.userPlaylists) { _, playlist ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        if (bulkTracks != null) viewModel.addTracksToPlaylist(playlist.id, bulkTracks)
                        else if (singleTrack != null) viewModel.addToPlaylist(playlist.id, singleTrack)
                    }.padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = playlist.localCoverPath ?: playlist.artworkUrl.ifEmpty { null },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(playlist.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            str("playlist_num_tracks", playlist.trackCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RepostDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var caption by remember { mutableStateOf("") }
    val maxChars = 140
    EscapableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("dialog_repost_title")) },
        text = {
            Column {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= maxChars) caption = it },
                    placeholder = { Text(str("dialog_repost_caption_hint")) },
                    modifier = Modifier.fillMaxWidth().trackTextInput(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        Text(
                            text = "${caption.length}/$maxChars",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(caption) }) { Text(str("dialog_repost_confirm")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str("btn_cancel")) } }
    )
}

@Composable
private fun SleepTimerDialog(viewModel: PlayerViewModel) {
    if (!viewModel.showSleepTimerDialog) return

    var sliderValue by remember { mutableStateOf(30f) }
    val selectedMinutes = sliderValue.toInt()
    val stopTimeText = remember(selectedMinutes) {
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, selectedMinutes) }
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(cal.time)
    }

    EscapableAlertDialog(
        onDismissRequest = { viewModel.showSleepTimerDialog = false },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        icon = { Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (viewModel.isSleepTimerActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = viewModel.formatSleepTimerRemaining(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { viewModel.cancelSleepTimer(); viewModel.showSleepTimerDialog = false }) {
                                Text(str("sleep_timer_cancel"), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    text = str("sleep_timer_slider_minutes", selectedMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = str("sleep_timer_stop_at", stopTimeText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Slider(value = sliderValue, onValueChange = { sliderValue = it }, valueRange = 5f..120f)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    viewModel.startSleepTimerEndOfTrack()
                    viewModel.showSleepTimerDialog = false
                }) { Text(str("sleep_timer_end_of_track")) }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.startSleepTimer(selectedMinutes * 60_000L)
                viewModel.showSleepTimerDialog = false
            }) { Text(str("btn_ok")) }
        },
        dismissButton = { TextButton(onClick = { viewModel.showSleepTimerDialog = false }) { Text(str("btn_cancel")) } }
    )
}

// ---------------------------------------------------------------------------
// Playlist context menu (right-click on a playlist card) — desktop version of
// the Android PlaylistOptionsSheet.
// ---------------------------------------------------------------------------

@Composable
private fun PlaylistMenuSheetContent(viewModel: PlayerViewModel) {
    val playlist = viewModel.playlistForMenu ?: return
    val isLocal = playlist.id < 0
    val permalink = playlist.permalinkUrl ?: ""
    val isStation = permalink.contains("artist-stations") || permalink.contains("track-stations")

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    val downloadedIds by DownloadManager.downloadedIds.collectAsState()
    val storageTrigger by DownloadManager.storageTrigger.collectAsState()

    // Cards usually carry stub tracks (or none): fetch the full list once.
    val loadedTracks by produceState<List<com.alananasss.kittytune.domain.Track>?>(initialValue = null, playlist.id) {
        value = try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadPlaylistTracksForMenu(playlist) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    val tracks = loadedTracks ?: emptyList()
    val isLoadingTracks = loadedTracks == null

    val isFullyDownloaded = remember(tracks, downloadedIds, storageTrigger) {
        tracks.isNotEmpty() && tracks.all { it.id < 0 || downloadedIds.contains(it.id) }
    }
    val isPlaylistDownloading = DownloadManager.isPlaylistDownloading(playlist.id)
    val shareUrl = if (!isLocal) permalink.ifEmpty { "https://soundcloud.com/playlists/${playlist.id}" } else ""

    if (showRemoveDownloadDialog) {
        EscapableAlertDialog(
            onDismissRequest = { showRemoveDownloadDialog = false },
            title = { Text(str("dialog_remove_download_title")) },
            text = { Text(str("dialog_remove_download_msg")) },
            confirmButton = {
                TextButton(onClick = {
                    DownloadManager.removePlaylistDownloads(playlist.id)
                    showRemoveDownloadDialog = false
                    viewModel.showPlaylistMenuSheet = false
                }) { Text(str("btn_delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRemoveDownloadDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showDetailsSheet) {
        BackHandler(onBack = { showDetailsSheet = false })
        Dialog(onDismissRequest = { showDetailsSheet = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(620.dp).heightIn(max = 680.dp),
            ) {
                com.alananasss.kittytune.ui.library.PlaylistDetailsSheet(
                    playlistId = playlist.id.toString(),
                    onDismiss = { showDetailsSheet = false },
                    onViewAll = { tabIndex ->
                        showDetailsSheet = false
                        viewModel.showPlaylistMenuSheet = false
                        viewModel.navigateToPlaylistId = "playlist_fans/${playlist.id}?tab=$tabIndex"
                    },
                    onNavigate = { id ->
                        showDetailsSheet = false
                        viewModel.showPlaylistMenuSheet = false
                        viewModel.navigateToPlaylistId = id
                    },
                    onMentionClick = { username ->
                        showDetailsSheet = false
                        viewModel.showPlaylistMenuSheet = false
                        viewModel.resolveAndNavigateToArtist(username)
                    },
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp).padding(horizontal = 4.dp)) {
            AsyncImage(
                model = playlist.fullResArtwork,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = playlist.title ?: str("generic_title"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitleParts = mutableListOf<String>()
                playlist.user?.username?.let { subtitleParts.add(it) }
                val count = if (tracks.isNotEmpty()) tracks.size else (playlist.trackCount ?: 0)
                if (count > 0) subtitleParts.add(str("playlist_num_tracks", count))
                Text(
                    text = subtitleParts.joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isLoadingTracks) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularWavyProgressIndicator(modifier = Modifier.size(20.dp), )
            }
        }

        val gridItems = mutableListOf<MenuOptionItem>().apply {
            if (tracks.isNotEmpty()) {
                add(MenuOptionItem(Icons.Rounded.PlayArrow, str("btn_play")) {
                    viewModel.playPlaylist(tracks, 0); viewModel.showPlaylistMenuSheet = false
                })
                add(MenuOptionItem(Icons.Rounded.Shuffle, str("btn_shuffle")) {
                    viewModel.playPlaylist(tracks.shuffled(), 0); viewModel.showPlaylistMenuSheet = false
                })
                add(MenuOptionItem(Icons.AutoMirrored.Rounded.PlaylistPlay, str("menu_play_next")) {
                    viewModel.insertNext(tracks); viewModel.showPlaylistMenuSheet = false
                })
                add(MenuOptionItem(Icons.AutoMirrored.Rounded.QueueMusic, str("menu_add_queue")) {
                    viewModel.addToQueue(tracks); viewModel.showPlaylistMenuSheet = false
                })
                add(MenuOptionItem(Icons.Default.Add, str("menu_add_playlist")) {
                    viewModel.showPlaylistMenuSheet = false
                    viewModel.prepareBulkAdd(tracks)
                })
            }
            if (!isLocal && playlist.id > 0 && !isStation) {
                add(MenuOptionItem(Icons.Rounded.Info, str("menu_playlist_details")) { showDetailsSheet = true })
            }
            playlist.user?.id?.takeIf { it > 0 }?.let { ownerId ->
                add(MenuOptionItem(Icons.Default.Person, str("menu_go_artist")) {
                    viewModel.showPlaylistMenuSheet = false
                    viewModel.navigateToPlaylistId = "profile:$ownerId"
                })
            }
            if (shareUrl.isNotEmpty()) {
                add(MenuOptionItem(Icons.Outlined.Share, str("btn_share")) { viewModel.sharePlaylist(playlist) })
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            items(gridItems) { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { item.onClick() }.padding(vertical = 6.dp)
                ) {
                    Icon(item.icon, null, modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(item.text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                }
            }
            if (!isLocal && tracks.isNotEmpty() && !isStation) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                            if (isFullyDownloaded) {
                                showRemoveDownloadDialog = true
                            } else if (!isPlaylistDownloading) {
                                DownloadManager.downloadPlaylist(playlist, tracks)
                                viewModel.showPlaylistMenuSheet = false
                            }
                        }.padding(vertical = 6.dp)
                    ) {
                        val icon = if (isFullyDownloaded) Icons.Default.Delete else Icons.Rounded.Download
                        val tint = if (isFullyDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        val label = when {
                            isFullyDownloaded -> str("btn_delete")
                            isPlaylistDownloading -> str("btn_cancel")
                            else -> str("btn_download")
                        }
                        Icon(icon, null, modifier = Modifier.size(30.dp), tint = tint)
                        Spacer(Modifier.height(8.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = tint)
                    }
                }
            }
        }
    }
}

/**
 * Fetches the complete track list for the right-clicked playlist: local playlists
 * from SQLite, stations/system playlists/regular playlists from the API — with the
 * same stub-hydration as PlaylistDetailScreen.
 */
internal suspend fun loadPlaylistTracksForMenu(
    playlist: com.alananasss.kittytune.domain.Playlist,
): List<com.alananasss.kittytune.domain.Track> {
    val db = com.alananasss.kittytune.data.local.AppDatabase.downloadDao

    if (playlist.id < 0) {
        val addedAtMap = db.getAddedAtForPlaylist(playlist.id)
        return db.getTracksForPlaylistSync(playlist.id).map { local ->
            com.alananasss.kittytune.domain.Track(
                id = local.id,
                title = local.title,
                artworkUrl = local.localArtworkPath.ifEmpty { local.artworkUrl },
                durationMs = local.duration,
                user = com.alananasss.kittytune.domain.User(0, local.artist, null),
                likedAt = addedAtMap[local.id]?.takeIf { it > 0 },
            )
        }
    }

    val api = com.alananasss.kittytune.data.network.RetrofitClient.create()
    val permalink = playlist.permalinkUrl ?: ""

    // Spotify catalog playlists/albums resolve through the Pathfinder repository.
    if (permalink.contains("spotify") || playlist.urn?.startsWith("spotify:") == true) {
        val cleanId = playlist.permalink?.let { com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(it) }
            ?: com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(playlist.urn ?: permalink)
        if (cleanId.isBlank()) return emptyList()
        val spotifyTracks = if (playlist.isAlbum) {
            com.alananasss.kittytune.data.spotify.SpotifyRepository.getAlbum(cleanId)?.tracks
        } else {
            com.alananasss.kittytune.data.spotify.SpotifyRepository.getPlaylist(cleanId)?.tracks
        }
        return spotifyTracks?.map { it.toTrack() } ?: emptyList()
    }

    val stationId = permalink.substringAfterLast(":").toLongOrNull()
    val playlistObj = when {
        permalink.contains("artist-stations") && stationId != null -> api.getArtistStation(stationId)
        permalink.contains("track-stations") && stationId != null -> api.getTrackStation(stationId)
        playlist.urn?.startsWith("soundcloud:system-playlists") == true -> api.getSystemPlaylist(playlist.urn!!)
        else -> api.getPlaylist(playlist.id)
    }

    val rawTracks = playlistObj.tracks ?: emptyList()
    val incompleteIds = rawTracks.filter { it.title.isNullOrBlank() || it.user == null }.map { it.id }
    if (incompleteIds.isEmpty()) return rawTracks

    val fetchedMap = mutableMapOf<Long, com.alananasss.kittytune.domain.Track>()
    incompleteIds.chunked(50).forEach { batchIds ->
        try {
            api.getTracksByIds(batchIds.joinToString(",")).forEach { fetchedMap[it.id] = it }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return rawTracks.map { track ->
        if (track.title.isNullOrBlank() || track.user == null) fetchedMap[track.id] ?: track else track
    }
}

@Composable
private fun CommentsSheetContent(viewModel: PlayerViewModel) {
    val track = viewModel.selectedTrackForSheet ?: viewModel.trackForMenu ?: viewModel.currentTrack
    var newCommentText by remember { mutableStateOf("") }
    val isPosting = viewModel.isPostingComment

    LaunchedEffect(viewModel.replyingToComment) {
        if (viewModel.replyingToComment != null) {
            val username = viewModel.replyingToComment?.user?.username ?: ""
            if (username.isNotBlank() && !newCommentText.startsWith("@$username")) {
                newCommentText = "@$username "
            }
        }
    }

    val organizedComments = remember(viewModel.commentsList.toList()) {
        val list = mutableListOf<Comment>()
        for (comment in viewModel.commentsList) {
            if (comment.body.trim().startsWith("@") && list.isNotEmpty()) {
                val parentIndex = list.indexOfLast { it.trackTimestamp == comment.trackTimestamp }
                if (parentIndex != -1) {
                    val parent = list[parentIndex]
                    list[parentIndex] = parent.copy(replies = (parent.replies ?: emptyList()) + comment)
                    continue
                }
            }
            list.add(comment)
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                if (track != null) {
                    AsyncImage(
                        model = track.fullResArtwork,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                }
                Column {
                    Text(
                        text = str("menu_comments"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (track != null) {
                        Text(
                            text = track.title ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(onClick = { viewModel.showCommentsSheet = false }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
            }
        }

        Spacer(Modifier.height(12.dp))

        // Sort Filter Header
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = str("menu_comments") + " (${track?.commentCount ?: organizedComments.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (viewModel.isCommentsLoading) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(18.dp))
                }
            }

            var isSortMenuExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { isSortMenuExpanded = true },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = str("sorted_by", str(viewModel.commentSort.labelResId)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }

                DropdownMenu(
                    expanded = isSortMenuExpanded,
                    onDismissRequest = { isSortMenuExpanded = false }
                ) {
                    CommentSort.values().forEach { sortOption ->
                        DropdownMenuItem(
                            text = { Text(str(sortOption.labelResId)) },
                            onClick = {
                                viewModel.onCommentSortChanged(sortOption)
                                isSortMenuExpanded = false
                            },
                            trailingIcon = {
                                if (sortOption == viewModel.commentSort) {
                                    Icon(Icons.Rounded.Check, contentDescription = str("desc_selected"), modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Comments Content List
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (viewModel.isCommentsLoading && viewModel.commentsList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            } else if (viewModel.commentsList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = str("comment_no_comments"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(organizedComments, key = { _, comment -> comment.id }) { index, comment ->
                        if (index >= organizedComments.size - 3 && !viewModel.isCommentsLoading && viewModel.commentNextHref != null) {
                            LaunchedEffect(index) {
                                viewModel.loadComments(refresh = false)
                            }
                        }
                        CommentItemUI(comment, viewModel)
                    }

                    if (viewModel.isCommentsLoading && viewModel.commentsList.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Replying Banner
        if (viewModel.replyingToComment != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                val targetUser = viewModel.replyingToComment?.user?.username ?: str("comment_anonymous")
                Text(
                    text = str("comment_replying_to", targetUser),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).clickable { viewModel.cancelReplying() },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Comment Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newCommentText,
                onValueChange = { newCommentText = it },
                placeholder = { Text(if (viewModel.replyingToComment != null) str("comment_write_reply") else str("add_comment_hint")) },
                modifier = Modifier.weight(1f).trackTextInput(),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                enabled = !isPosting
            )
            IconButton(
                onClick = {
                    if (newCommentText.isNotBlank()) {
                        viewModel.postComment(newCommentText, null)
                        newCommentText = ""
                    }
                },
                enabled = !isPosting && newCommentText.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                if (isPosting) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = str("comment_send_action"),
                        tint = if (newCommentText.isNotBlank()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

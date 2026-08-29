@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.background

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.text.BasicTextField
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.common.ScrollableLazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.ui.library.*
import com.alananasss.kittytune.ui.library.LibraryViewModel
import com.alananasss.kittytune.ui.library.OwnershipFilter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.rounded.*
import com.alananasss.kittytune.ui.player.PlayerViewModel
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.alananasss.kittytune.ui.modifiers.squish

import com.alananasss.kittytune.ui.home.HomeViewModel
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Album

/**
 * Left panel: primary navigation on top, then "Your Library" — search, create,
 * filter chips, four display modes (compact list / list / cover grid / titled grid),
 * a collapsed icon rail, and a full-screen mode (see [LibraryPanel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sidebar(
    navController: NavController,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel? = null,
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Whole sections of the app somebody may never open; Home always stays (issue #33).
    val navPrefs = remember { PlayerPreferences() }
    val hiddenNav by navPrefs.hiddenSidebarNavFlow()
        .collectAsState(initial = navPrefs.getHiddenSidebarNav())

    // How far into a collapse the panel is, read from the width it is actually being laid out at rather
    // than from an animation of this file's own. `MainScreen` puts that width on a spring; a second
    // schedule here could only ever finish too early or too late, which is the "doesn't look smooth, it
    // looks abrupt" half of the report. See [SidebarMorph] for the rest of the reasoning.
    BoxWithConstraints(modifier) {
    val collapse = SidebarMorph.progressFor(
        expanded = libraryViewModel.sidebarWidth.dp,
        actual = maxWidth,
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PANEL_GUTTER.dp),
    ) {

        // --- top card: Home / Explore ------------------------------------------------
        Surface(
            shape = PanelShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Laid out at the panel's real width now, not pinned to one of the two end states. The
            // pinning existed because a label re-wrapped as the panel narrowed; the labels are single
            // non-wrapping lines that leave through [pushedBack], so there is nothing left to re-wrap
            // and the rows can simply follow the edge (issue #33).
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                SidebarNavItem(
                    label = str("nav_home"),
                    selected = currentRoute == "home",
                    iconSelected = Icons.Filled.Home,
                    iconUnselected = Icons.Outlined.Home,
                    collapse = collapse,
                ) {
                    homeViewModel?.clearSearch()
                    if (currentRoute != "home") {
                        navController.navigate("home") {
                            popUpTo("home")
                            launchSingleTop = true
                        }
                    } else {
                        playerViewModel.showLyricsSheet = false
                    }
                }
                if (PlayerPreferences.SIDEBAR_NAV_FEED !in hiddenNav) SidebarNavItem(
                    label = str("nav_feed"),
                    selected = currentRoute == "feed",
                    iconSelected = Icons.Rounded.DynamicFeed,
                    iconUnselected = Icons.Rounded.DynamicFeed,
                    collapse = collapse,
                ) {
                    if (currentRoute != "feed") {
                        navController.navigate("feed") { launchSingleTop = true }
                    } else {
                        playerViewModel.showLyricsSheet = false
                    }
                }
                if (PlayerPreferences.SIDEBAR_NAV_EXPLORE !in hiddenNav) SidebarNavItem(
                    label = str("explorer_title"),
                    selected = currentRoute == "genres",
                    iconSelected = Icons.Filled.Explore,
                    iconUnselected = Icons.Outlined.Explore,
                    collapse = collapse,
                ) {
                    if (currentRoute != "genres") {
                        navController.navigate("genres") { launchSingleTop = true }
                    } else {
                        playerViewModel.showLyricsSheet = false
                    }
                }
                if (PlayerPreferences.SIDEBAR_NAV_RECOGNITION !in hiddenNav) SidebarNavItem(
                    label = str("pref_bottom_menu_fab_recognition"),
                    selected = currentRoute == "recognition",
                    iconSelected = Icons.Rounded.GraphicEq,
                    iconUnselected = Icons.Rounded.GraphicEq,
                    collapse = collapse,
                ) {
                    if (currentRoute != "recognition") {
                        navController.navigate("recognition") { launchSingleTop = true }
                    } else {
                        playerViewModel.showLyricsSheet = false
                    }
                }
                // One click, from anywhere. Sync lived at the bottom of the settings page, which for a
                // feature whose first problem is being discovered at all was the same as hiding it.
                if (PlayerPreferences.SIDEBAR_NAV_SYNC !in hiddenNav) SidebarNavItem(
                    label = str("sync_title"),
                    selected = currentRoute == "sync_settings",
                    iconSelected = Icons.Rounded.Devices,
                    iconUnselected = Icons.Rounded.Devices,
                    collapse = collapse,
                ) {
                    if (currentRoute != "sync_settings") {
                        navController.navigate("sync_settings") { launchSingleTop = true }
                    } else {
                        playerViewModel.showLyricsSheet = false
                    }
                }
            }
        }

        // --- library card ------------------------------------------------------------
        LibraryPanel(
            libraryViewModel = libraryViewModel,
            playerViewModel = playerViewModel,
            fullScreen = false,
            collapse = collapse,
            onImport = { navController.navigate("music_import") },
            onHistory = { navController.navigate("history") },
            onUpload = { navController.navigate("upload") },
            modifier = Modifier.weight(1f),
        )
    }
    }
}

/** Left/right click handling shared by all library entry composables. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.libClicks(onClick: () -> Unit, onRightClick: (() -> Unit)?): Modifier =
    if (onRightClick != null) {
        this
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onRightClick)
            .clickable(onClick = onClick)
    } else {
        this.clickable(onClick = onClick)
    }

/**
 * A single item shown in the library, regardless of display mode: either a
 * pinned collection (icon over a gradient) or a playlist/album/artist artwork.
 */
private data class LibEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String? = null,
    val icon: ImageVector? = null,
    /** An imported image standing in for [icon]. See [LibraryTileIcons]. */
    val iconPath: String? = null,
    val gradient: List<Color>? = null,
    /**
     * A single tonal fill, used instead of [gradient] when set.
     *
     * Material 3 tiles are flat tonal surfaces; a two-stop gradient between a role and its container
     * is what made these look cheap next to the rest of the app (issue #33).
     */
    val flatColor: Color? = null,
    /** Colour of [icon] over the fill. White when the fill is one of the fixed gradients. */
    val iconTint: Color = Color.White,
    val round: Boolean = false,
    val destination: String,
    val playlist: com.alananasss.kittytune.domain.Playlist? = null,
    val folder: com.alananasss.kittytune.data.local.LibraryFolder? = null,
    val track: com.alananasss.kittytune.domain.Track? = null,
    val isPinned: Boolean = false,
)

/**
 * The library card itself. Used at sidebar width inside [Sidebar] and at full
 * content width by MainScreen when [LibraryViewModel.isLibraryFullScreen] is on
 * (it then replaces the center panel but never covers the Now Playing panel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPanel(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    fullScreen: Boolean,
    /** How far into a collapse the panel is: 0 open, 1 a rail. See [SidebarMorph]. */
    collapse: Float = 0f,
    onImport: () -> Unit = {},
    onHistory: () -> Unit = {},
    onUpload: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderForMenu by remember { mutableStateOf<com.alananasss.kittytune.data.local.LibraryFolder?>(null) }
    var folderToRename by remember { mutableStateOf<com.alananasss.kittytune.data.local.LibraryFolder?>(null) }
    var folderToDelete by remember { mutableStateOf<com.alananasss.kittytune.data.local.LibraryFolder?>(null) }
    var playlistForMenu by remember { mutableStateOf<com.alananasss.kittytune.domain.Playlist?>(null) }
    var movingItemKey by remember { mutableStateOf<String?>(null) }
    var playlistForDetails by remember { mutableStateOf<com.alananasss.kittytune.domain.Playlist?>(null) }

    val openEntry: (LibEntry) -> Unit = { entry ->
        if (entry.track != null) {
            val tracks = libraryViewModel.uploadedTracks.toList()
            val idx = tracks.indexOfFirst { it.id == entry.track.id }.coerceAtLeast(0)
            playerViewModel.playPlaylist(tracks, idx)
        } else if (entry.destination.startsWith("folder_")) {
            val fId = entry.destination.removePrefix("folder_").toLongOrNull()
            val folderItem = libraryViewModel.displayedItems.filterIsInstance<LibraryItem.FolderItem>().find { it.folder.id == fId }
            if (folderItem != null) {
                libraryViewModel.navigateToFolder(folderItem.folder)
            }
        } else {
            playerViewModel.navigateToPlaylistId = entry.destination
            if (fullScreen) libraryViewModel.isLibraryFullScreen = false
        }
    }
    // Right-click on a playlist/album/station = Android 3-dot options sheet. On folder = folder menu.
    val rightClickEntry: (LibEntry) -> (() -> Unit)? = { entry ->
        when {
            entry.folder != null -> {
                { folderForMenu = entry.folder }
            }
            entry.playlist != null -> {
                { playlistForMenu = entry.playlist }
            }
            else -> null
        }
    }

    val entries = buildLibraryEntries(libraryViewModel)

    Surface(shape = PanelShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        // One layout, at every width between open and a rail (issue #33).
        //
        // "The collapsed icon and the expanded one are at different heights, because of this, the
        // animation also looks like a curve." / "The icon of favorites, downloaded, etc. again crooked."
        //
        // There used to be two: the full panel and a separate icon rail, cross-faded into each other at
        // nine tenths of the travel. Every attempt to make their geometry agree was a measurement of one
        // fed to the other — the rail pinned to the measured height of the block it stood in for, that
        // measurement taken from a layout which a session starting collapsed never composes, and a
        // constant standing in when it had not been taken. Each fix was correct and the next report was
        // the same, because two layouts *can* disagree, and anything that can disagree eventually does.
        //
        // One layout cannot. The panel narrows, the labels leave by [pushedBack], the search row recedes
        // and hands its place to the two actions a rail needs, and the entries stay exactly where they
        // were because they are the same entries. No measurement, no stand-in constant, no handover, and
        // nothing left for a fade to cover up.
        Column(
            // The panel's real width, never one of the two end states pinned. That pinning existed
            // because labels re-wrapped as the panel narrowed; they are single non-wrapping lines that
            // leave through [pushedBack] now, so there is nothing to re-wrap.
            Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            LibraryHeader(
                libraryViewModel = libraryViewModel,
                playerViewModel = playerViewModel,
                fullScreen = fullScreen,
                collapse = collapse,
                onCreatePlaylist = { showCreatePlaylistDialog = true },
                onCreateFolder = { showCreateFolderDialog = true },
                onImport = onImport,
                onHistory = onHistory,
                onUpload = onUpload,
            )

            // One row, two occupants, handing over inside it. The search field recedes without giving up
            // any of its height — see [receded] — so this row is the same height throughout, which is the
            // whole reason everything below it stays put.
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.receded(collapse)) { LibrarySearchRow(libraryViewModel) }
                if (!fullScreen) RailActions(
                    collapse = collapse,
                    onCreate = { showCreatePlaylistDialog = true },
                    onHistory = onHistory,
                )
            }

            LibraryContent(
                libraryViewModel = libraryViewModel,
                entries = entries,
                fullScreen = fullScreen,
                collapse = collapse,
                onOpen = openEntry,
                onRightClick = rightClickEntry,
            )
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            isCreating = libraryViewModel.isCreatingPlaylist,
            onDismiss = { if (!libraryViewModel.isCreatingPlaylist) showCreatePlaylistDialog = false },
            onCreate = { name, isPublic ->
                libraryViewModel.createPlaylist(name, isPublic) { id ->
                    showCreatePlaylistDialog = false
                    playerViewModel.navigateToPlaylistId = id.toString()
                    if (fullScreen) libraryViewModel.isLibraryFullScreen = false
                }
            },
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name ->
                libraryViewModel.createFolder(name)
                showCreateFolderDialog = false
            }
        )
    }

    folderToRename?.let { folder ->
        RenameFolderDialog(
            folder = folder,
            onDismiss = { folderToRename = null },
            onRename = { newName ->
                libraryViewModel.renameFolder(folder.id, newName)
                folderToRename = null
            }
        )
    }

    folderToDelete?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            onDismiss = { folderToDelete = null },
            onDelete = {
                libraryViewModel.deleteFolder(folder)
                folderToDelete = null
            }
        )
    }

    folderForMenu?.let { folder ->
        FolderOptionsMenu(
            folder = folder,
            onDismiss = { folderForMenu = null },
            onPlayOrdered = {
                libraryViewModel.playFolder(folder.id, playerViewModel, shuffle = false)
            },
            onPlayShuffle = {
                libraryViewModel.playFolder(folder.id, playerViewModel, shuffle = true)
            },
            onPlayRecursiveOrdered = {
                libraryViewModel.playFolder(folder.id, playerViewModel, shuffle = false, recursive = true)
            },
            onPlayRecursiveShuffle = {
                libraryViewModel.playFolder(folder.id, playerViewModel, shuffle = true, recursive = true)
            },
            onTogglePin = {
                libraryViewModel.togglePinFolder(folder.id)
            },
            onRename = {
                folderToRename = folder
            },
            onDelete = {
                folderToDelete = folder
            }
        )
    }

    playlistForMenu?.let { playlist ->
        LibraryPlaylistOptionsDialog(
            playlist = playlist,
            libraryViewModel = libraryViewModel,
            playerViewModel = playerViewModel,
            onDismiss = { playlistForMenu = null },
            onShowDetails = { playlistForDetails = it },
            onMoveToFolder = { key -> movingItemKey = key }
        )
    }

    movingItemKey?.let { itemKey ->
        val availableFolders = libraryViewModel.getAvailableTargetFolders(null)
        MoveToFolderDialog(
            availableFolders = availableFolders,
            isInsideFolder = libraryViewModel.currentFolderId != null,
            onDismiss = { movingItemKey = null },
            onMoveToRoot = {
                libraryViewModel.moveItemToFolder(itemKey, null)
                movingItemKey = null
            },
            onMoveToFolder = { targetFolderId ->
                libraryViewModel.moveItemToFolder(itemKey, targetFolderId)
                movingItemKey = null
            },
            onCreateNewFolder = {
                movingItemKey = null
                showCreateFolderDialog = true
            }
        )
    }

    if (playlistForDetails != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { playlistForDetails = null }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.width(620.dp).heightIn(max = 680.dp),
            ) {
                PlaylistDetailsSheet(
                    playlistId = playlistForDetails!!.id.toString(),
                    onDismiss = { playlistForDetails = null },
                    onViewAll = { tabIndex ->
                        val pid = playlistForDetails!!.id
                        playlistForDetails = null
                        playerViewModel.navigateToPlaylistId = "playlist_fans/$pid?tab=$tabIndex"
                    },
                    onNavigate = { id ->
                        playlistForDetails = null
                        playerViewModel.navigateToPlaylistId = id
                    },
                    onMentionClick = { username ->
                        playlistForDetails = null
                        playerViewModel.resolveAndNavigateToArtist(username)
                    }
                )
            }
        }
    }
}

/**
 * The three fixed library tiles — Liked, Downloads, Local files — as the settings want them
 * (issue #33).
 *
 * Hidden ones are dropped, and one with an imported icon carries its path instead of a vector.
 *
 * With the dynamic theme on, each tile is a flat container role — primary, secondary, tertiary —
 * with its matching `on` colour for the icon. Flat and tonal is what Material 3 does; the earlier
 * gradient from a role to its own container read as cheap next to everything around it, and with
 * three roles drawn from one seed the three tiles came out nearly the same colour. The containers
 * sit further apart in hue and much closer to the surface, so they read as a set (issue #33).
 *
 * With it off they keep the exact purple, green and blue gradients they have always had.
 */
@Composable
private fun rememberFixedLibraryTiles(): List<LibEntry> {
    val prefs = remember { PlayerPreferences() }
    val hidden by prefs.hiddenLibraryTilesFlow()
        .collectAsState(initial = prefs.getHiddenLibraryTiles())
    // Tied to the dynamic theme rather than a switch of its own, which is how it was asked for:
    // the tiles follow the cover while it is on, and fall back to their fixed colours when it is
    // off (issue #33).
    val themed by prefs.dynamicThemeFlow().collectAsState(initial = prefs.getDynamicTheme())

    val likesIcon by prefs.libraryTileIconFlow(PlayerPreferences.LIBRARY_TILE_LIKES)
        .collectAsState(initial = prefs.getLibraryTileIcon(PlayerPreferences.LIBRARY_TILE_LIKES))
    val downloadsIcon by prefs.libraryTileIconFlow(PlayerPreferences.LIBRARY_TILE_DOWNLOADS)
        .collectAsState(initial = prefs.getLibraryTileIcon(PlayerPreferences.LIBRARY_TILE_DOWNLOADS))
    val localIcon by prefs.libraryTileIconFlow(PlayerPreferences.LIBRARY_TILE_LOCAL)
        .collectAsState(initial = prefs.getLibraryTileIcon(PlayerPreferences.LIBRARY_TILE_LOCAL))

    val scheme = MaterialTheme.colorScheme
    return buildList {
        if (PlayerPreferences.LIBRARY_TILE_LIKES !in hidden) {
            add(
                LibEntry(
                    key = "pin_likes",
                    title = str("lib_liked_tracks"),
                    subtitle = str("lib_liked_subtitle"),
                    icon = Icons.Filled.Favorite,
                    iconPath = likesIcon,
                    gradient = if (themed) null else listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)),
                    flatColor = if (themed) scheme.primaryContainer else null,
                    iconTint = if (themed) scheme.onPrimaryContainer else Color.White,
                    destination = "likes",
                    isPinned = true,
                )
            )
        }
        if (PlayerPreferences.LIBRARY_TILE_DOWNLOADS !in hidden) {
            add(
                LibEntry(
                    key = "pin_downloads",
                    title = str("lib_downloads"),
                    subtitle = str("lib_liked_subtitle_local"),
                    icon = Icons.Outlined.DownloadForOffline,
                    iconPath = downloadsIcon,
                    gradient = if (themed) null else listOf(Color(0xFF00C853), Color(0xFF69F0AE)),
                    flatColor = if (themed) scheme.secondaryContainer else null,
                    iconTint = if (themed) scheme.onSecondaryContainer else Color.White,
                    destination = "downloads",
                    isPinned = true,
                )
            )
        }
        if (PlayerPreferences.LIBRARY_TILE_LOCAL !in hidden) {
            add(
                LibEntry(
                    key = "pin_local",
                    title = str("lib_local_media"),
                    subtitle = str("lib_local_media_subtitle"),
                    icon = Icons.Outlined.FolderOpen,
                    iconPath = localIcon,
                    gradient = if (themed) null else listOf(Color(0xFF0091EA), Color(0xFF40C4FF)),
                    flatColor = if (themed) scheme.tertiaryContainer else null,
                    iconTint = if (themed) scheme.onTertiaryContainer else Color.White,
                    destination = "local_files",
                    isPinned = true,
                )
            )
        }
    }
}

@Composable
private fun buildLibraryEntries(libraryViewModel: LibraryViewModel): List<LibEntry> {
    val query = libraryViewModel.searchQuery
    val isInsideFolder = libraryViewModel.currentFolderId != null

    if (libraryViewModel.selectedFilter == str("lib_your_uploads")) {
        val searched = if (query.isBlank()) {
            libraryViewModel.uploadedTracks
        } else {
            libraryViewModel.uploadedTracks.filter {
                it.title?.contains(query, ignoreCase = true) == true ||
                it.user?.username?.contains(query, ignoreCase = true) == true
            }
        }

        val privacyFiltered = when (libraryViewModel.uploadsPrivacyFilter) {
            UploadsPrivacyFilter.ALL -> searched
            UploadsPrivacyFilter.PUBLIC -> searched.filter { it.sharing?.equals("private", ignoreCase = true) != true }
            UploadsPrivacyFilter.PRIVATE -> searched.filter { it.sharing?.equals("private", ignoreCase = true) == true }
        }

        val sortedTracks = when (libraryViewModel.uploadsSortOption) {
            UploadsSortOption.RECENTLY_ADDED -> privacyFiltered
            UploadsSortOption.FIRST_ADDED -> privacyFiltered.reversed()
            UploadsSortOption.TITLE -> privacyFiltered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.orEmpty() })
            UploadsSortOption.ARTIST -> privacyFiltered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.user?.username.orEmpty() })
        }

        return sortedTracks.map { track ->
            LibEntry(
                key = "upload_track_${track.id}",
                title = track.title ?: str("untitled_track"),
                subtitle = track.displayArtist.ifBlank { str("unknown_artist") },
                artworkUrl = track.fullResArtwork,
                destination = "track_${track.id}",
                track = track
            )
        }
    }

    val pinned = if (libraryViewModel.selectedFilter == null && !isInsideFolder) {
        rememberFixedLibraryTiles()
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    } else emptyList()

    val items = libraryViewModel.displayedItems.map { item ->
        val showPin = item.isPinned && !isInsideFolder
        when (item) {
            is LibraryItem.FolderItem -> {
                val folder = item.folder
                val subtitleText = if (item.playlistCount == 0 && item.folderCount == 0) str("lib_folder_empty")
                else "${item.playlistCount} ${str("lib_playlists")}" +
                        (if (item.folderCount > 0) " • ${item.folderCount} ${str("lib_folders")}" else "")
                LibEntry(
                    key = item.key,
                    title = folder.name,
                    subtitle = subtitleText,
                    icon = Icons.Rounded.Folder,
                    gradient = listOf(Color(0xFF5C6BC0), Color(0xFF9FA8DA)),
                    destination = "folder_${folder.id}",
                    folder = folder,
                    isPinned = showPin,
                )
            }
            is LibraryItem.PlaylistItem -> {
                val pl = item.playlist
                val permalink = pl.permalinkUrl
                val isYoutubeShortcut = permalink != null && permalink.startsWith("yt_radio:")
                val isTrackStation = pl.isTrackStation || permalink?.contains("track-stations") == true || pl.urn?.contains("track-stations") == true
                val isArtistStation = pl.isArtistStation || permalink?.contains("artist-stations") == true || pl.urn?.contains("artist-stations") == true

                val dest = when {
                    isYoutubeShortcut -> java.net.URLEncoder.encode(permalink, "UTF-8")
                    isTrackStation -> "station:${pl.numericId}"
                    isArtistStation -> "station_artist:${pl.numericId}"
                    pl.urn?.startsWith("soundcloud:system-playlists:") == true -> "system_playlist:${pl.urn}"
                    pl.id < 0 -> "local_playlist:${pl.id}"
                    else -> pl.id.toString()
                }

                LibEntry(
                    key = "pl_${pl.id}_${pl.permalinkUrl ?: ""}",
                    title = pl.title ?: "",
                    subtitle = listOfNotNull(
                        when {
                            isTrackStation || isArtistStation -> str("lib_stations")
                            pl.isRealAlbum -> str("lib_albums")
                            else -> str("lib_playlists")
                        },
                        pl.user?.username,
                    ).joinToString(" • "),
                    // Deliberately not fullResArtwork: EntryArtwork resolves the real
                    // first-track cover instead of falling back to a stock image.
                    artworkUrl = pl.usableArtwork,
                    destination = dest,
                    playlist = pl,
                    isPinned = showPin,
                )
            }
            is LibraryItem.ArtistItem -> {
                val artist = item.artist
                LibEntry(
                    key = "ar_${artist.id}",
                    title = artist.username,
                    subtitle = str("lib_artists"),
                    artworkUrl = artist.avatarUrl,
                    round = true,
                    destination = "profile:${artist.id}",
                    isPinned = showPin,
                )
            }
        }
    }
    return pinned + items
}

// ---------------------------------------------------------------------------
// Header: collapse affordance + title, create + enlarge buttons
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryHeader(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    fullScreen: Boolean,
    /** How far into a collapse the panel is: 0 open, 1 a rail. See [SidebarMorph]. */
    collapse: Float = 0f,
    onCreatePlaylist: () -> Unit,
    onCreateFolder: () -> Unit,
    onImport: () -> Unit,
    onHistory: () -> Unit,
    onUpload: () -> Unit = {},
) {
    var showCreateMenu by remember { mutableStateOf(false) }

    val headerPrefs = remember { PlayerPreferences() }
    val hiddenButtons by headerPrefs.hiddenLibraryButtonsFlow()
        .collectAsState(initial = headerPrefs.getHiddenLibraryButtons())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Inset so this header's icon sits on the same vertical line as the rail's toggle, the
            // navigation icons above and the artwork of every entry below — the line at the middle of
            // [SidebarMorph.RAIL_WIDTH]. It was 12 dp, which put it 12 dp to the left of where the rail
            // puts it, so it jumped sideways as the panel closed (issue #33). The full-screen library is
            // not a rail and keeps its own inset.
            .padding(
                start = if (fullScreen) 12.dp else SidebarMorph.ICON_INSET - 4.dp,
                end = 8.dp,
                top = 10.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val currentFolder = libraryViewModel.currentFolder
        if (currentFolder != null) {
            IconButton(
                onClick = { libraryViewModel.navigateUp() },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = str("btn_back"),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = currentFolder.name,
                style = if (fullScreen) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FolderPlaySplitButton(
                folder = currentFolder,
                libraryViewModel = libraryViewModel,
                playerViewModel = playerViewModel
            )
            Spacer(Modifier.width(6.dp))
        } else if (fullScreen) {
            Icon(
                Icons.Filled.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = str("nav_library"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
        } else {
            // One control for both directions now that there is one layout: the same icon in the same
            // place, saying which way it will go. The rail used to carry a second button of its own.
            val toggleTip =
                if (libraryViewModel.isSidebarCollapsed) str("lib_open_tooltip")
                else str("lib_collapse_tooltip")
            Tip(toggleTip) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { libraryViewModel.toggleSidebarCollapsed() }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.LibraryMusic,
                        contentDescription = toggleTip,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SidebarMorph.ICON_SIZE),
                    )
                    // Leaves the way the navigation labels above it do, so the whole panel closes with
                    // one gesture instead of the top half receding and the library card cutting.
                    Box(Modifier.pushedBack(collapse).padding(start = 10.dp)) {
                        Text(
                            text = str("nav_library"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }

        // Everything a header holds besides its own icon goes away with the panel (issue #33).
        //
        // At 80 dp there is no room for four icon buttons, and there is no rail layout to hand them to any
        // more — so they recede where they stand and the two a rail actually needs come back beside the
        // search field instead. Receding rather than being dropped keeps this row the height it was, which
        // is what every entry below it is aligned against.
        Row(Modifier.receded(collapse), verticalAlignment = Alignment.CenterVertically) {
        // Extended "+ Créer" with dropdown menu. Outlined rather than filled tonal: next to a row
        // of plain icon buttons the tonal fill made it the loudest thing in the header, which is
        // not what a secondary action should be (issue #33).
        val extendedCreate = fullScreen || libraryViewModel.sidebarWidth >= 340f
        val showCreate = PlayerPreferences.LIBRARY_BUTTON_CREATE !in hiddenButtons
        Box {
            if (showCreate) Tip(str("lib_create_playlist_tooltip")) {
                if (extendedCreate) {
                    OutlinedButton(
                        onClick = { showCreateMenu = true },
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(str("lib_create"))
                    }
                } else {
                    IconButton(
                        onClick = { showCreateMenu = true },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = str("lib_create_playlist_tooltip"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showCreateMenu,
                onDismissRequest = { showCreateMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(str("lib_create_playlist_title")) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                    onClick = {
                        showCreateMenu = false
                        onCreatePlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text(str("lib_create_folder_title")) },
                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                    onClick = {
                        showCreateMenu = false
                        onCreateFolder()
                    }
                )
                DropdownMenuItem(
                    text = { Text(str("upload_screen_title")) },
                    leadingIcon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
                    onClick = {
                        showCreateMenu = false
                        onUpload()
                    }
                )
            }
        }
        if (PlayerPreferences.LIBRARY_BUTTON_HISTORY !in hiddenButtons) {
            Spacer(Modifier.width(4.dp))
            Tip(str("history_title")) {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onHistory,
                    modifier = Modifier.size(32.dp),
                ) {
                    // A plain clock rather than the clock-with-arrow: the arrow read as an undo or a
                    // refresh next to the other header icons (issue #33).
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = str("history_title"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Tip(str("lib_import_playlist")) {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = onImport,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Rounded.ImportExport,
                    contentDescription = str("lib_import_playlist"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        val enlargeTip = if (fullScreen) str("lib_restore_tooltip") else str("lib_enlarge_tooltip")
        Tip(enlargeTip) {
            IconButton(
                shapes = IconButtonDefaults.shapes(),
                onClick = { libraryViewModel.isLibraryFullScreen = !libraryViewModel.isLibraryFullScreen },

                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (fullScreen) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                    contentDescription = enlargeTip,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        }
    }
}

// ---------------------------------------------------------------------------
// Search + sort/view-mode row
// ---------------------------------------------------------------------------

@Composable
private fun LibrarySearchRow(libraryViewModel: LibraryViewModel) {
    var searchActive by remember { mutableStateOf(libraryViewModel.searchQuery.isNotBlank()) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchActive) {
            val focusRequester = remember { FocusRequester() }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).height(32.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (libraryViewModel.searchQuery.isEmpty()) {
                            Text(
                                str("lib_search_hint"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = libraryViewModel.searchQuery,
                            onValueChange = { libraryViewModel.searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .trackTextInput()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                        libraryViewModel.searchQuery = ""
                                        searchActive = false
                                        true
                                    } else false
                                },
                        )
                    }
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = {
                            libraryViewModel.searchQuery = ""
                            searchActive = false
                        },

                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = str("btn_cancel"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            Tip(str("lib_search_tooltip")) {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = { searchActive = true },

                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = str("lib_search_tooltip"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.width(8.dp))
        LibraryCategoryButton(libraryViewModel)
        Spacer(Modifier.width(4.dp))
        SortAndViewMenuButton(libraryViewModel)
    }
}

/**
 * Playlists / Albums / Artists / Stations, as one button instead of a row of five (issue #33).
 *
 * "I think they can be moved below and do it as a filter, you can click and select what you need, by
 * default it is there as 'all' […] because now these filters take up a lot of space, they are spacious,
 * but they are used infrequently and stand out too much from the rest."
 *
 * All three observations hold. They were filled buttons, which in Material's hierarchy is the loudest
 * thing you can draw, sitting above a library where nothing else asks for attention at all; they were a
 * horizontally scrolling row with its own gradient shadows and arrow buttons, for five entries; and they
 * cost a whole row of a panel whose vertical space is the thing in shortest supply. As one button beside
 * the search field they cost nothing until they are used, and the panel loses a row — which the collapsed
 * rail follows on its own, since it is pinned to the measured height of what it replaces.
 *
 * The absence of a filter is a choice with a name here, rather than the state of nothing being pressed:
 * "All" is what a reader looks for, and a menu has room to say it.
 */
@Composable
private fun LibraryCategoryButton(libraryViewModel: LibraryViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val all = str("search_filter_all")
    val categories = buildList {
        add(str("lib_playlists") to Icons.Rounded.QueueMusic)
        add(str("lib_albums") to Icons.Rounded.Album)
        add(str("lib_artists") to Icons.Rounded.Person)
        add(str("lib_stations") to Icons.Rounded.Radio)
        if (libraryViewModel.uploadedTracks.isNotEmpty()) {
            add(str("lib_your_uploads") to Icons.Rounded.CloudUpload)
        }
    }
    val selected = libraryViewModel.selectedFilter

    Box {
        Tip(str("lib_filter_tooltip")) {
            TextButton(
                onClick = { expanded = true },
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    text = selected ?: all,
                    style = MaterialTheme.typography.labelMedium,
                    // Single line and truncated rather than wrapped: this sits in a row that follows the
                    // panel's width, and a label that wraps takes the row's height with it.
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(all) },
                leadingIcon = { CategoryCheck(isSelected = selected == null) },
                onClick = {
                    expanded = false
                    libraryViewModel.selectedFilter = null
                },
            )
            categories.forEach { (label, icon) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        if (selected == label) CategoryCheck(isSelected = true)
                        else Icon(icon, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        libraryViewModel.selectedFilter = label
                    },
                )
            }
        }
    }
}

/** The tick that says which one is live, or the space it would take, so the labels stay in a column. */
@Composable
private fun CategoryCheck(isSelected: Boolean) {
    if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null)
    else Spacer(Modifier.size(24.dp))
}

private fun viewModeIcon(mode: LibraryViewMode): ImageVector = when (mode) {
    LibraryViewMode.COMPACT_LIST -> Icons.Rounded.ViewHeadline
    LibraryViewMode.LIST -> Icons.AutoMirrored.Rounded.ViewList
    LibraryViewMode.COMPACT_GRID -> Icons.Rounded.GridView
    LibraryViewMode.GRID -> Icons.Rounded.ViewModule
}

private fun viewModeLabel(mode: LibraryViewMode): String = when (mode) {
    LibraryViewMode.COMPACT_LIST -> str("lib_view_compact_list")
    LibraryViewMode.LIST -> str("lib_view_list")
    LibraryViewMode.COMPACT_GRID -> str("lib_view_compact_grid")
    LibraryViewMode.GRID -> str("lib_view_grid")
}

@Composable
private fun SortAndViewMenuButton(libraryViewModel: LibraryViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    val shouldShowOwnershipFilter = libraryViewModel.selectedFilter == null ||
            libraryViewModel.selectedFilter == str("lib_playlists") ||
            libraryViewModel.selectedFilter == str("lib_albums")

    val label = if (shouldShowOwnershipFilter && libraryViewModel.ownershipFilter != OwnershipFilter.ALL) {
        when (libraryViewModel.ownershipFilter) {
            OwnershipFilter.CREATED -> str("filter_created")
            OwnershipFilter.LIKED -> str("filter_liked")
            OwnershipFilter.ALL -> str("lib_recents")
        }
    } else str("lib_recents")

    Box {
        Surface(
            modifier = Modifier.clickable { menuOpen = true },
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Icon(viewModeIcon(libraryViewModel.viewMode), contentDescription = str("lib_view_mode"), modifier = Modifier.size(16.dp))
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            if (shouldShowOwnershipFilter) {
                val options = listOf(
                    OwnershipFilter.ALL to str("filter_all"),
                    OwnershipFilter.CREATED to str("filter_created"),
                    OwnershipFilter.LIKED to str("filter_liked"),
                )
                options.forEach { (filter, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        trailingIcon = {
                            if (libraryViewModel.ownershipFilter == filter) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = {
                            libraryViewModel.ownershipFilter = filter
                            menuOpen = false
                        },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }

            DropdownMenuItem(
                text = { Text(str("sort_date_added")) },
                trailingIcon = {
                    Icon(
                        if (libraryViewModel.isSortDescending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { libraryViewModel.isSortDescending = !libraryViewModel.isSortDescending },
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Text(
                str("lib_view_mode"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LibraryViewMode.entries.forEach { mode ->
                    Tip(viewModeLabel(mode)) {
                        FilledIconToggleButton(
                            checked = libraryViewModel.viewMode == mode,
                            onCheckedChange = { libraryViewModel.viewMode = mode },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(viewModeIcon(mode), contentDescription = viewModeLabel(mode), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Content: the four display modes + empty state
// ---------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LibraryContent(
    libraryViewModel: LibraryViewModel,
    entries: List<LibEntry>,
    fullScreen: Boolean,
    /**
     * How far into a collapse the panel is: 0 open, 1 a rail. The two list modes hand their text back
     * over this, so that by the time the rail takes over there is nothing left beside the artwork for
     * the handover to cut (issue #33). See [SidebarMorph].
     */
    collapse: Float = 0f,
    onOpen: (LibEntry) -> Unit,
    onRightClick: (LibEntry) -> (() -> Unit)?,
) {
    if (entries.isEmpty() && libraryViewModel.searchQuery.isNotBlank()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                str("lib_no_results"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                str("lib_no_results_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }

    if (entries.isEmpty() && libraryViewModel.currentFolderId != null) {
        EmptyFolderView(modifier = Modifier.weight(1f))
        return
    }

    androidx.compose.runtime.key(
        libraryViewModel.isSortDescending,
        libraryViewModel.sortOption,
        libraryViewModel.selectedFilter,
        libraryViewModel.ownershipFilter,
        libraryViewModel.currentFolderId
    ) {
        val compactListState = rememberLazyListState()
        val listState = rememberLazyListState()
        val compactGridState = rememberLazyGridState()
        val gridState = rememberLazyGridState()

        when (libraryViewModel.viewMode) {
            LibraryViewMode.COMPACT_LIST -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
                state = compactListState
            ) {
                items(entries, key = { it.key }) { entry ->
                    CompactListRow(entry, collapse = collapse, onRightClick = onRightClick(entry)) { onOpen(entry) }
                }
            }

            LibraryViewMode.LIST -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
                state = listState
            ) {
                items(entries, key = { it.key }) { entry ->
                    LibraryRow(entry, collapse = collapse, onRightClick = onRightClick(entry)) { onOpen(entry) }
                }
            }

            LibraryViewMode.COMPACT_GRID -> ScrollableLazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (fullScreen) 128.dp else 96.dp),
                modifier = Modifier.weight(1f),
                state = compactGridState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries.size, key = { entries[it].key }) { index ->
                    val entry = entries[index]
                    Tip(entry.title) {
                        EntryArtwork(
                            entry = entry,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            iconFraction = 0.4f,
                            cornerRadius = 8.dp,
                            onClick = { onOpen(entry) },
                            onRightClick = onRightClick(entry),
                        )
                    }
                }
            }

            LibraryViewMode.GRID -> ScrollableLazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (fullScreen) 160.dp else 116.dp),
                modifier = Modifier.weight(1f),
                state = gridState,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                items(entries.size, key = { entries[it].key }) { index ->
                    val entry = entries[index]
                    GridCell(entry, onRightClick = onRightClick(entry)) { onOpen(entry) }
                }
            }
        }
    }
}

/**
 * One-line row: title • type (no artwork).
 *
 * @param collapse hands the text back as the panel closes. This mode has no artwork to leave behind, so
 *   what is left at the end is the small leading icon — which is as close as a text-only row gets to the
 *   rail that replaces it (issue #33).
 */
@Composable
private fun CompactListRow(
    entry: LibEntry,
    collapse: Float = 0f,
    onRightClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .libClicks(onClick, onRightClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.isPinned) {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = "Pinned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
        } else {
            entry.icon?.let { icon ->
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Row(
            modifier = Modifier.weight(1f, fill = false).pushedBack(collapse),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "• ${entry.subtitle.substringBefore(" • ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Default row: 48dp artwork + title + subtitle.
 *
 * This is the row the collapse was reported against — "the Favorites folder and so on are positioned
 * lower when collapsed than when expanded" — and it is now the same row on both sides of one. The
 * artwork is 48 dp inside 8 dp of padding inside a list inset by 8 dp, which puts its centre on the rail's
 * centre line and its row at the rail's row height, so neither moves by a pixel. The only difference
 * between the two states is the text, and that leaves through [pushedBack] rather than being dropped
 * (issue #33).
 *
 * @param collapse 0 when the panel is open, 1 when it is a rail.
 */
@Composable
private fun LibraryRow(
    entry: LibEntry,
    collapse: Float = 0f,
    onRightClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    // Only once the title has actually gone. Present in every state so the tree does not change shape
    // part-way through the collapse, exactly as the navigation rows do it.
    Tip(entry.title, enabled = collapse > SidebarMorph.FADE_DONE_AT) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .libClicks(onClick, onRightClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryArtwork(entry, Modifier.size(48.dp), iconFraction = 0.5f)
        // The gap goes inside the part that is leaving, so the row closes up completely instead of
        // keeping 12 dp of nothing beside the artwork.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .pushedBack(collapse)
                .padding(start = 12.dp)
        ) {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}

/** Grid cell with artwork + title + subtitle underneath. */
@Composable
private fun GridCell(entry: LibEntry, onRightClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .libClicks(onClick, onRightClick)
            .padding(6.dp),
    ) {
        EntryArtwork(
            entry,
            Modifier.fillMaxWidth().aspectRatio(1f),
            iconFraction = 0.4f,
            cornerRadius = 8.dp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            entry.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.isPinned) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = "Pinned",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Artwork (or icon-on-gradient for pinned collections), square or round. */
@Composable
private fun EntryArtwork(
    entry: LibEntry,
    modifier: Modifier,
    iconFraction: Float = 0.5f,
    cornerRadius: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
) {
    val shape = if (entry.round) CircleShape else RoundedCornerShape(cornerRadius)
    var boxModifier = modifier.clip(shape)
    if (onClick != null) boxModifier = boxModifier.libClicks(onClick, onRightClick)

    val resolvedCover = com.alananasss.kittytune.ui.common.rememberPlaylistCover(entry.playlist)
    val artwork = entry.artworkUrl ?: resolvedCover

    val tileFill: Modifier? = when {
        entry.flatColor != null -> Modifier.background(entry.flatColor)
        entry.gradient != null -> Modifier.background(Brush.linearGradient(entry.gradient))
        else -> null
    }

    if (tileFill != null && entry.icon != null) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = boxModifier.then(tileFill),
            contentAlignment = Alignment.Center,
        ) {
            // An imported icon is drawn as an image, not tinted: someone who picked their own
            // artwork picked its colours too. It also fills the tile rather than sitting at
            // iconFraction, which is sized for a line-art vector (issue #33).
            val imported = entry.iconPath?.let { path -> remember(path) { java.io.File(path) } }
            if (imported != null && imported.isFile) {
                AsyncImage(
                    model = imported,
                    contentDescription = entry.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    entry.icon,
                    contentDescription = null,
                    tint = entry.iconTint,
                    modifier = Modifier.size(maxWidth * iconFraction),
                )
            }
        }
    } else if (!artwork.isNullOrBlank()) {
        AsyncImage(
            model = artwork,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = boxModifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = boxModifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Collapsed icon rail
// ---------------------------------------------------------------------------






// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

/**
 * The two actions a rail still needs, arriving in the place the search field is leaving.
 *
 * These are the one thing a narrowing panel cannot produce by itself: creating a playlist and opening
 * the history live in the header when there is room for a header, and there is not at 80 dp. So they
 * come forward here — inside the search row's own box, so they cost no height, which is what keeps every
 * entry below at exactly the height it had while the panel was open.
 *
 * Side by side rather than stacked, for the same reason: two 30 dp buttons and the gap between them come
 * to 64 dp, which fits the rail across rather than down. Stacking them is what used to make this block
 * taller than the one it replaced, and everything under it drop by the difference (issue #33).
 *
 * They begin only once the search field has finished fading, so the two are never both legible, and they
 * do not exist at all before then — an invisible button that can still be clicked is worse than no
 * animation.
 */
@Composable
private fun RailActions(collapse: Float, onCreate: () -> Unit, onHistory: () -> Unit) {
    val appearance = SidebarMorph.arrivalOf(collapse)
    if (appearance <= 0f) return

    val railPrefs = remember { PlayerPreferences() }
    val hiddenButtons by railPrefs.hiddenLibraryButtonsFlow()
        .collectAsState(initial = railPrefs.getHiddenLibraryButtons())

    Row(
        modifier = Modifier.graphicsLayer { alpha = appearance },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (PlayerPreferences.LIBRARY_BUTTON_CREATE !in hiddenButtons) {
            Tip(str("lib_create_playlist_tooltip")) {
                IconButton(
                    onClick = onCreate,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = str("lib_create_playlist_tooltip"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (PlayerPreferences.LIBRARY_BUTTON_HISTORY !in hiddenButtons) {
            Tip(str("history_title")) {
                IconButton(
                    onClick = onHistory,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(30.dp),
                ) {
                    // A plain clock rather than the clock-with-arrow: the arrow read as an undo next to
                    // the other icons (issue #33).
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = str("history_title"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * One navigation row, in every state between open and collapsed to a rail.
 *
 * ## One layout, not two
 *
 * This was written twice — `if (compact) { centred icon } else { icon and label }` — and that is the
 * whole of "the text doesn't shrink, it's just removed, and the icons are moved to the centre"
 * (issue #33). Two layouts cannot animate into each other: at best they can be cross-dissolved, which
 * is a cut with a fade over it, and the icon still arrives 12 dp from where it left.
 *
 * There is one row now. The icon is inset by [SidebarMorph.ICON_INSET], which is exactly the inset that
 * puts it on the centre line of the rail, so as the panel closes around it the icon does not move at
 * all — not by a pixel, and not at any point along the way. The label leaves through [pushedBack].
 *
 * `softWrap = false` is load-bearing rather than cosmetic: the row now follows the panel's real width,
 * and a wrapping label re-flows on every frame of a resize. That is what turned "Explorer" into one
 * letter per line, and it is why the previous fix had to pin these rows to a fixed width instead.
 *
 * @param collapse 0 when the panel is open, 1 when it is a rail, and every value in between while it
 *   travels.
 */
@Composable
private fun SidebarNavItem(
    label: String,
    selected: Boolean,
    iconSelected: ImageVector,
    iconUnselected: ImageVector,
    collapse: Float = 0f,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant

    // Present in every state so the tree does not change shape part-way through the animation, but only
    // able to open once the label it would be repeating has actually gone.
    Tip(label, enabled = collapse > 0.6f) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(start = SidebarMorph.ICON_INSET, end = 8.dp)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (selected) iconSelected else iconUnselected,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(SidebarMorph.ICON_SIZE),
            )
            // The gap goes inside the part that is leaving, so the row closes up completely rather
            // than keeping 14 dp of nothing beside the icon.
            Box(Modifier.pushedBack(collapse).padding(start = SidebarMorph.LABEL_GAP)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = color,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


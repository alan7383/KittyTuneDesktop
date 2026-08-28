@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.background

import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.requiredWidth
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
    val collapsed = libraryViewModel.isSidebarCollapsed

    // Whole sections of the app somebody may never open; Home always stays (issue #33).
    val navPrefs = remember { PlayerPreferences() }
    val hiddenNav by navPrefs.hiddenSidebarNavFlow()
        .collectAsState(initial = navPrefs.getHiddenSidebarNav())

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PANEL_GUTTER.dp)) {

        // --- top card: Home / Explore ------------------------------------------------
        Surface(
            shape = PanelShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Pinned to the width these rows were laid out for, and clipped by the card, for the same
            // reason the library card below is (issue #33). Left to the panel's animating width, a
            // label being faded out is re-wrapped narrower on every frame — "Explorer" ends up one
            // letter per line — which looks less like a transition than like a broken layout.
            Column(
                Modifier
                    .requiredWidth(
                        if (collapsed) com.alananasss.kittytune.ui.library.SIDEBAR_COLLAPSED_WIDTH.dp
                        else libraryViewModel.sidebarWidth.dp
                    )
                    .padding(vertical = 8.dp)
            ) {
                SidebarNavItem(
                    label = str("nav_home"),
                    selected = currentRoute == "home",
                    iconSelected = Icons.Filled.Home,
                    iconUnselected = Icons.Outlined.Home,
                    compact = collapsed,
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
                    compact = collapsed,
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
                    compact = collapsed,
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
                    compact = collapsed,
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
                    compact = collapsed,
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
            onImport = { navController.navigate("music_import") },
            onHistory = { navController.navigate("history") },
            onUpload = { navController.navigate("upload") },
            modifier = Modifier.weight(1f),
        )
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
        // Faded rather than swapped (issue #33).
        //
        // "When I minimize and then open the library, all the lines shift down, and the opening
        // doesn't look smooth, it looks abrupt."
        //
        // The panel's *width* was already animated, on a spring, in MainScreen. Its *contents* were
        // not: this was a plain `if`, so the rail and the full column replaced each other in a single
        // frame while the width was still travelling. Every frame of the resize therefore showed one
        // layout at a width belonging to the other, and the swap itself was a cut. Crossfading them
        // costs nothing and gives the eye something continuous to follow while the panel moves.
        //
        // Timed to outlast the width spring rather than to a round number, so the fade is not already
        // over while the edge is still moving, which is the half of "abrupt" that a fade alone would
        // leave behind.
        androidx.compose.animation.Crossfade(
            targetState = libraryViewModel.isSidebarCollapsed && !fullScreen,
            animationSpec = androidx.compose.animation.core.tween(LIBRARY_SWAP_MS),
            label = "libraryCollapse",
        ) { showRail ->
            // Each layout keeps the width it was designed for while the panel travels, and the panel
            // clips whatever no longer fits. Left to the incoming constraint, the outgoing layout is
            // squeezed narrower every frame instead — the header and the filter chips visibly crush
            // together — which is its own kind of lurch on top of the one being fixed. Not applied in
            // full screen, where the panel is deliberately wider than the stored sidebar width.
            val fixedWidth =
                if (fullScreen) Modifier
                else Modifier.requiredWidth(
                    if (showRail) com.alananasss.kittytune.ui.library.SIDEBAR_COLLAPSED_WIDTH.dp
                    else libraryViewModel.sidebarWidth.dp
                )
            if (showRail) {
                androidx.compose.foundation.layout.Box(fixedWidth) {
                CollapsedLibraryRail(
                    entries = entries,
                    onExpand = { libraryViewModel.toggleSidebarCollapsed() },
                    onCreate = { showCreatePlaylistDialog = true },
                    onHistory = onHistory,
                    onOpen = openEntry,
                    onRightClick = rightClickEntry,
                )
                }
            } else {
                Column(fixedWidth.fillMaxHeight()) {
                    LibraryHeader(
                        libraryViewModel = libraryViewModel,
                        playerViewModel = playerViewModel,
                        fullScreen = fullScreen,
                        onCreatePlaylist = { showCreatePlaylistDialog = true },
                        onCreateFolder = { showCreateFolderDialog = true },
                        onImport = onImport,
                        onHistory = onHistory,
                        onUpload = onUpload,
                    )
                    LibraryFilterChips(libraryViewModel)
                    LibrarySearchRow(libraryViewModel)
                    LibraryContent(
                        libraryViewModel = libraryViewModel,
                        entries = entries,
                        fullScreen = fullScreen,
                        onOpen = openEntry,
                        onRightClick = rightClickEntry,
                    )
                }
            }
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
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
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
            Tip(str("lib_collapse_tooltip")) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { libraryViewModel.toggleSidebarCollapsed() }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.LibraryMusic,
                        contentDescription = str("lib_collapse_tooltip"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = str("nav_library"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }

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

// ---------------------------------------------------------------------------
// Filter chips: Playlists / Albums / Artists / Stations
// ---------------------------------------------------------------------------

@Composable
private fun LibraryFilterChips(libraryViewModel: LibraryViewModel) {
    // Each filter carries its icon: narrowed all the way down the labels truncate to a couple of
    // letters, and in a language with long words there was nothing left to recognise them by
    // (issue #33). The main navigation rail already pairs icon and label the same way.
    val filters = remember(libraryViewModel.uploadedTracks.size) {
        buildList {
            add(str("lib_playlists") to Icons.Rounded.QueueMusic)
            add(str("lib_albums") to Icons.Rounded.Album)
            add(str("lib_artists") to Icons.Rounded.Person)
            add(str("lib_stations") to Icons.Rounded.Radio)
            if (libraryViewModel.uploadedTracks.isNotEmpty()) {
                add(str("lib_your_uploads") to Icons.Rounded.CloudUpload)
            }
        }
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.first().scrollDelta.y
                                scope.launch {
                                    scrollState.scrollBy(delta * 50f)
                                }
                            }
                        }
                    }
                }
                .horizontalScroll(scrollState)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filters.forEach { (label, icon) ->
                val selected = libraryViewModel.selectedFilter == label
                Button(
                    onClick = {
                        libraryViewModel.selectedFilter = if (selected) null else label
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Left shadow & arrow
        androidx.compose.animation.AnimatedVisibility(
            visible = scrollState.value > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(48.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clip(CircleShape)
                        .clickable { scope.launch { scrollState.animateScrollBy(-200f) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
        }

        // Right shadow & arrow
        androidx.compose.animation.AnimatedVisibility(
            visible = scrollState.value < scrollState.maxValue,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(48.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )
                    ),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clip(CircleShape)
                        .clickable { scope.launch { scrollState.animateScrollBy(200f) } },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
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
        SortAndViewMenuButton(libraryViewModel)
    }
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
                    CompactListRow(entry, onRightClick = onRightClick(entry)) { onOpen(entry) }
                }
            }

            LibraryViewMode.LIST -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
                state = listState
            ) {
                items(entries, key = { it.key }) { entry ->
                    LibraryRow(entry, onRightClick = onRightClick(entry)) { onOpen(entry) }
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

/** One-line row: title • type (no artwork). */
@Composable
private fun CompactListRow(entry: LibEntry, onRightClick: (() -> Unit)? = null, onClick: () -> Unit) {
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

/** Default row: 48dp artwork + title + subtitle. */
@Composable
private fun LibraryRow(entry: LibEntry, onRightClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .libClicks(onClick, onRightClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryArtwork(entry, Modifier.size(48.dp), iconFraction = 0.5f)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
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

@Composable
private fun CollapsedLibraryRail(
    entries: List<LibEntry>,
    onExpand: () -> Unit,
    onCreate: () -> Unit,
    onHistory: () -> Unit,
    onOpen: (LibEntry) -> Unit,
    onRightClick: (LibEntry) -> (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tip(str("lib_open_tooltip")) {
            IconButton(
                onClick = onExpand,
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ViewSidebar,
                    contentDescription = str("lib_open_tooltip"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Same treatment as the expanded header: no tonal fill, a plain clock, and both hideable
        // (issue #33).
        val railPrefs = remember { PlayerPreferences() }
        val hiddenButtons by railPrefs.hiddenLibraryButtonsFlow()
            .collectAsState(initial = railPrefs.getHiddenLibraryButtons())

        if (PlayerPreferences.LIBRARY_BUTTON_CREATE !in hiddenButtons) {
            Spacer(Modifier.height(4.dp))
            Tip(str("lib_create_playlist_tooltip")) {
                IconButton(
                    onClick = onCreate,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = str("lib_create_playlist_tooltip"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (PlayerPreferences.LIBRARY_BUTTON_HISTORY !in hiddenButtons) {
            Spacer(Modifier.height(4.dp))
            Tip(str("history_title")) {
                IconButton(
                    onClick = onHistory,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = str("history_title"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(entries, key = { it.key }) { entry ->
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Tip(entry.title) {
                        EntryArtwork(
                            entry = entry,
                            modifier = Modifier.size(48.dp),
                            iconFraction = 0.5f,
                            onClick = { onOpen(entry) },
                            onRightClick = onRightClick(entry),
                        )
                    }
                }
            }
        }
    }
}





// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

@Composable
private fun SidebarNavItem(
    label: String,
    selected: Boolean,
    iconSelected: ImageVector,
    iconUnselected: ImageVector,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (selected) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant

    if (compact) {
        Tip(label) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (selected) iconSelected else iconUnselected, contentDescription = label, tint = color)
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (selected) iconSelected else iconUnselected, contentDescription = label, tint = color)
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color,
        )
    }
}

/**
 * How long the library takes to fade between its rail and its full contents.
 *
 * Longer than it feels it should be, on purpose: the panel's width is on a medium-low spring, which
 * takes appreciably more than a typical 200 ms fade to settle. A shorter fade finishes while the edge
 * of the panel is still moving, and the leftover movement reads as the same lurch the fade was added
 * to remove.
 */
private const val LIBRARY_SWAP_MS = 320

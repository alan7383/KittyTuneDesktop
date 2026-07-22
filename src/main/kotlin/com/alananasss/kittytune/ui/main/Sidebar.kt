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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.text.BasicTextField
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.common.ScrollableLazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.ViewList
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
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.library.LibraryItem
import com.alananasss.kittytune.ui.library.LibraryViewMode
import com.alananasss.kittytune.ui.library.LibraryViewModel
import com.alananasss.kittytune.ui.library.OwnershipFilter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.rounded.*
import com.alananasss.kittytune.ui.player.PlayerViewModel
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.alananasss.kittytune.ui.modifiers.squish

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
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val collapsed = libraryViewModel.isSidebarCollapsed

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PANEL_GUTTER.dp)) {

        // --- top card: Home / Explore ------------------------------------------------
        Surface(shape = PanelShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                SidebarNavItem(
                    label = str("nav_home"),
                    selected = currentRoute == "home",
                    iconSelected = Icons.Filled.Home,
                    iconUnselected = Icons.Outlined.Home,
                    compact = collapsed,
                ) {
                    navController.navigate("home") {
                        popUpTo("home")
                        launchSingleTop = true
                    }
                }
                SidebarNavItem(
                    label = str("nav_feed"),
                    selected = currentRoute == "feed",
                    iconSelected = Icons.Rounded.DynamicFeed,
                    iconUnselected = Icons.Rounded.DynamicFeed,
                    compact = collapsed,
                ) {
                    navController.navigate("feed") { launchSingleTop = true }
                }
                SidebarNavItem(
                    label = str("explorer_title"),
                    selected = currentRoute == "genres",
                    iconSelected = Icons.Filled.Explore,
                    iconUnselected = Icons.Outlined.Explore,
                    compact = collapsed,
                ) {
                    navController.navigate("genres") { launchSingleTop = true }
                }
                SidebarNavItem(
                    label = str("pref_bottom_menu_fab_recognition"),
                    selected = currentRoute == "recognition",
                    iconSelected = Icons.Rounded.GraphicEq,
                    iconUnselected = Icons.Rounded.GraphicEq,
                    compact = collapsed,
                ) {
                    navController.navigate("recognition") { launchSingleTop = true }
                }
            }
        }

        // --- library card ------------------------------------------------------------
        LibraryPanel(
            libraryViewModel = libraryViewModel,
            playerViewModel = playerViewModel,
            fullScreen = false,
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
    val gradient: List<Color>? = null,
    val round: Boolean = false,
    val destination: String,
    val playlist: com.alananasss.kittytune.domain.Playlist? = null,
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
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val openEntry: (LibEntry) -> Unit = { entry ->
        playerViewModel.navigateToPlaylistId = entry.destination
        if (fullScreen) libraryViewModel.isLibraryFullScreen = false
    }
    // Right-click on a playlist/album/station = Android 3-dot options sheet.
    val rightClickEntry: (LibEntry) -> (() -> Unit)? = { entry ->
        entry.playlist?.let { pl -> { playerViewModel.showPlaylistOptions(pl) } }
    }

    val entries = buildLibraryEntries(libraryViewModel)

    Surface(shape = PanelShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier) {
        if (libraryViewModel.isSidebarCollapsed && !fullScreen) {
            CollapsedLibraryRail(
                entries = entries,
                onExpand = { libraryViewModel.toggleSidebarCollapsed() },
                onCreate = { showCreateDialog = true },
                onOpen = openEntry,
                onRightClick = rightClickEntry,
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                LibraryHeader(
                    libraryViewModel = libraryViewModel,
                    fullScreen = fullScreen,
                    onCreate = { showCreateDialog = true },
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

    if (showCreateDialog) {
        CreatePlaylistDialog(
            isCreating = libraryViewModel.isCreatingPlaylist,
            onDismiss = { if (!libraryViewModel.isCreatingPlaylist) showCreateDialog = false },
            onCreate = { name, isPublic ->
                libraryViewModel.createPlaylist(name, isPublic) { id ->
                    showCreateDialog = false
                    playerViewModel.navigateToPlaylistId = id.toString()
                    if (fullScreen) libraryViewModel.isLibraryFullScreen = false
                }
            },
        )
    }
}

@Composable
private fun buildLibraryEntries(libraryViewModel: LibraryViewModel): List<LibEntry> {
    val query = libraryViewModel.searchQuery
    val pinned = if (libraryViewModel.selectedFilter == null) {
        listOf(
            LibEntry(
                key = "pin_likes",
                title = str("lib_liked_tracks"),
                subtitle = str("lib_liked_subtitle"),
                icon = Icons.Filled.Favorite,
                gradient = listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)),
                destination = "likes",
            ),
            LibEntry(
                key = "pin_downloads",
                title = str("lib_downloads"),
                subtitle = str("lib_liked_subtitle_local"),
                icon = Icons.Outlined.DownloadForOffline,
                gradient = listOf(Color(0xFF00C853), Color(0xFF69F0AE)),
                destination = "downloads",
            ),
            LibEntry(
                key = "pin_local",
                title = str("lib_local_media"),
                subtitle = str("lib_local_media_subtitle"),
                icon = Icons.Outlined.FolderOpen,
                gradient = listOf(Color(0xFF0091EA), Color(0xFF40C4FF)),
                destination = "local_files",
            ),
        ).filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    } else emptyList()

    val items = libraryViewModel.displayedItems.map { item ->
        when (item) {
            is LibraryItem.PlaylistItem -> {
                val pl = item.playlist
                LibEntry(
                    key = "pl_${pl.id}",
                    title = pl.title ?: "",
                    subtitle = listOfNotNull(
                        if (pl.isAlbum) str("lib_albums") else str("lib_playlists"),
                        pl.user?.username,
                    ).joinToString(" • "),
                    artworkUrl = pl.fullResArtwork,
                    destination = pl.id.toString(),
                    playlist = pl,
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
    fullScreen: Boolean,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (fullScreen) {
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
        }

        Spacer(Modifier.weight(1f))

        // Extended "+ Créer" whenever there is room (wide sidebar or full screen),
        // icon-only otherwise. Expressive shapes morph the corners on press.
        val extendedCreate = fullScreen || libraryViewModel.sidebarWidth >= 340f
        Tip(str("lib_create_playlist_tooltip")) {
            if (extendedCreate) {
                FilledTonalButton(
                    onClick = onCreate,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str("lib_create"))
                }
            } else {
                FilledTonalIconButton(
                    onClick = onCreate,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = str("lib_create_playlist_tooltip"), modifier = Modifier.size(18.dp))
                }
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
    val filters = listOf(
        str("lib_playlists"),
        str("lib_albums"),
        str("lib_artists"),
        str("lib_stations"),
    )
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
            filters.forEach { filter ->
                val selected = libraryViewModel.selectedFilter == filter
                Button(
                    onClick = {
                        libraryViewModel.selectedFilter = if (selected) null else filter
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(filter)
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

    when (libraryViewModel.viewMode) {
        LibraryViewMode.COMPACT_LIST -> LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(entries, key = { it.key }) { entry ->
                CompactListRow(entry, onRightClick = onRightClick(entry)) { onOpen(entry) }
            }
        }

        LibraryViewMode.LIST -> LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(entries, key = { it.key }) { entry ->
                LibraryRow(entry, onRightClick = onRightClick(entry)) { onOpen(entry) }
            }
        }

        LibraryViewMode.COMPACT_GRID -> ScrollableLazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = if (fullScreen) 128.dp else 96.dp),
            modifier = Modifier.weight(1f),
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
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            items(entries.size, key = { entries[it].key }) { index ->
                val entry = entries[index]
                GridCell(entry, onRightClick = onRightClick(entry)) { onOpen(entry) }
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
        entry.icon?.let { icon ->
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
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
        Column {
            Text(
                entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        Text(
            entry.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

    if (entry.gradient != null && entry.icon != null) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = boxModifier.background(Brush.linearGradient(entry.gradient)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(maxWidth * iconFraction),
            )
        }
    } else {
        AsyncImage(
            model = entry.artworkUrl,
            contentDescription = entry.title,
            contentScale = ContentScale.Crop,
            modifier = boxModifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
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
    onOpen: (LibEntry) -> Unit,
    onRightClick: (LibEntry) -> (() -> Unit)?,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tip(str("lib_open_tooltip")) {
            IconButton(onClick = onExpand, ) {
                Icon(
                    Icons.Rounded.ViewSidebar,
                    contentDescription = str("lib_open_tooltip"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Tip(str("lib_create_playlist_tooltip")) {
            FilledTonalIconButton(
                onClick = onCreate,

                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = str("lib_create_playlist_tooltip"), modifier = Modifier.size(20.dp))
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
// Create playlist dialog (synced with SoundCloud when logged in)
// ---------------------------------------------------------------------------

@Composable
private fun CreatePlaylistDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, isPublic: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
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
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
                            // The checked thumb is onPrimary (dark navy in this theme):
                            // tint the icon with primary so it stays visible.
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
            TextButton(onClick = onDismiss, enabled = !isCreating) {
                Text(str("btn_cancel"))
            }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

/** Plain M3 tooltip wrapper used across the library panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

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

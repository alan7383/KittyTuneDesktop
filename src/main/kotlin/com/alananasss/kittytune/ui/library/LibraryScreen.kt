@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import com.alananasss.kittytune.core.trackTextInput
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.PointerButton
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.core.EscapableAlertDialog
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ArtistLinkText
import coil3.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.LibraryFolder
import com.alananasss.kittytune.data.local.LocalArtist
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.SquareCardShimmer
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.ArtistAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onLoginClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onLikedTracksClick: () -> Unit,
    onProfileClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel = viewModel()
) {

    val listState = rememberLazyGridState()
    // collapse fab text when scrolling
    val fabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0
        }
    }

    LaunchedEffect(
        libraryViewModel.selectedFilter,
        libraryViewModel.isSortDescending,
        libraryViewModel.sortOption,
        libraryViewModel.ownershipFilter,
        libraryViewModel.currentFolderId,
        libraryViewModel.isGridLayout,
        libraryViewModel.searchQuery
    ) {
        listState.scrollToItem(0)
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateMenu by remember { mutableStateOf(false) }
    var folderForMenu by remember { mutableStateOf<LibraryFolder?>(null) }
    var folderToRename by remember { mutableStateOf<LibraryFolder?>(null) }
    var folderToDelete by remember { mutableStateOf<LibraryFolder?>(null) }
    var playlistForMenu by remember { mutableStateOf<Playlist?>(null) }
    var movingItemKey by remember { mutableStateOf<String?>(null) }
    var playlistForDetails by remember { mutableStateOf<Playlist?>(null) }
    val scope = rememberCoroutineScope()

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            isCreating = libraryViewModel.isCreatingPlaylist,
            onDismiss = { if (!libraryViewModel.isCreatingPlaylist) showCreatePlaylistDialog = false },
            onCreate = { name, isPublic ->
                libraryViewModel.createPlaylist(name, isPublic) { id ->
                    val navId = if (id < 0) "local_playlist:$id" else id.toString()
                    onPlaylistClick(navId)
                }
                showCreatePlaylistDialog = false
            }
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

        val showLogin = libraryViewModel.userProfile == null && !libraryViewModel.isLoading && !libraryViewModel.isOfflineMode
        val isGuest = libraryViewModel.isGuestUser
    
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(top = 16.dp, bottom = 8.dp)
                ) {
                    // offline banner
                    if (libraryViewModel.isOfflineMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.WifiOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(str("lib_offline_mode"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
    
                    // guest banner
                    if (isGuest && !libraryViewModel.isOfflineMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { onLoginClick() }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(str("lib_guest_mode"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
    
                    SearchBarHeader(
                        query = libraryViewModel.searchQuery,
                        onQueryChange = { libraryViewModel.searchQuery = it },
                        avatarUrl = libraryViewModel.userProfile?.avatarUrl,
                        onProfileClick = onProfileClick,
                        isGuest = isGuest
                    )
    
                    FilterChipsRow(libraryViewModel)

                    val currentFolder = libraryViewModel.currentFolder
                    if (currentFolder != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { libraryViewModel.navigateUp() },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("btn_back"))
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = currentFolder.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = { folderForMenu = currentFolder },
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = str("btn_options"))
                            }

                            Spacer(Modifier.width(6.dp))

                            FolderPlaySplitButton(
                                folder = currentFolder,
                                libraryViewModel = libraryViewModel,
                                playerViewModel = playerViewModel
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!showLogin || isGuest) {
                    val bottomNavHeight = 90.dp
                    val miniPlayerHeight = if (playerViewModel.currentTrack != null) 72.dp else 0.dp
                    val totalBottomPadding = bottomNavHeight + miniPlayerHeight
    
                    Box(modifier = Modifier.padding(bottom = totalBottomPadding)) {
                        ExtendedFloatingActionButton(
                            onClick = { showCreateMenu = true },
                            icon = { Icon(Icons.Rounded.Add, str("lib_create_playlist_title")) },
                            text = { Text(str("lib_create_playlist_title")) },
                            expanded = fabExpanded,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )

                        DropdownMenu(
                            expanded = showCreateMenu,
                            onDismissRequest = { showCreateMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(str("lib_create_playlist_title")) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    showCreateMenu = false
                                    showCreatePlaylistDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(str("lib_create_folder_title")) },
                                leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                                onClick = {
                                    showCreateMenu = false
                                    showCreateFolderDialog = true
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            if (showLogin && !isGuest) {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(str("welcome_title"), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onLoginClick,
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(str("login_soundcloud"))
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(innerPadding)) {
                    SortAndLayoutControls(libraryViewModel)
    
                    if (libraryViewModel.isLoading && libraryViewModel.displayedItems.isEmpty() && libraryViewModel.currentFolderId == null) {
                        LibraryShimmerGrid(isGridLayout = libraryViewModel.isGridLayout)
                    } else if (libraryViewModel.currentFolderId != null && libraryViewModel.displayedItems.isEmpty()) {
                        EmptyFolderView()
                    } else {
                        androidx.compose.runtime.key(
                            libraryViewModel.selectedFilter,
                            libraryViewModel.isSortDescending,
                            libraryViewModel.sortOption,
                            libraryViewModel.ownershipFilter,
                            libraryViewModel.currentFolderId,
                            libraryViewModel.isGridLayout
                        ) {
                            val gridState = rememberLazyGridState()
                            LibraryContentGrid(
                                listState = gridState,
                                viewModel = libraryViewModel,
                                playerViewModel = playerViewModel,
                                onLikedTracksClick = onLikedTracksClick,
                                onPlaylistClick = onPlaylistClick,
                                onArtistClick = { artistId ->
                                    val spotifyId = com.alananasss.kittytune.data.local.PlayerPreferences().getSpotifyArtistIdForStableId(artistId)
                                    if (!spotifyId.isNullOrBlank()) {
                                        val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(spotifyId)
                                        onPlaylistClick("spotify_artist:$clean")
                                    } else {
                                        onPlaylistClick("profile:$artistId")
                                    }
                                },
                                onFolderRightClick = { folderForMenu = it },
                                onPlaylistRightClick = { playlistForMenu = it },
                                isGuest = isGuest
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun LibraryContentGrid(
        listState: LazyGridState,
        viewModel: LibraryViewModel,
        playerViewModel: PlayerViewModel,
        onLikedTracksClick: () -> Unit,
        onPlaylistClick: (String) -> Unit,
        onArtistClick: (Long) -> Unit,
        onFolderRightClick: (LibraryFolder) -> Unit = {},
        onPlaylistRightClick: (Playlist) -> Unit = {},
        isGuest: Boolean
    ) {
        val columns = if (viewModel.isGridLayout) GridCells.Fixed(2) else GridCells.Fixed(1)
        val isSyncing by viewModel.isSyncing.collectAsState()
    
        // grab strings here before using them in logic
        val playlistsFilter = str("lib_playlists")
        val uploadsFilter = str("lib_your_uploads")
        val shouldShowPlaylists = (viewModel.selectedFilter == null || viewModel.selectedFilter == playlistsFilter) && viewModel.currentFolderId == null
    
        LazyVerticalGrid(
            state = listState,
            columns = columns,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isInsideFolder = viewModel.currentFolderId != null

            if (viewModel.selectedFilter == uploadsFilter) {
                val searched = if (viewModel.searchQuery.isBlank()) {
                    viewModel.uploadedTracks
                } else {
                    viewModel.uploadedTracks.filter {
                        it.title?.contains(viewModel.searchQuery, ignoreCase = true) == true ||
                        it.user?.username?.contains(viewModel.searchQuery, ignoreCase = true) == true
                    }
                }

                val privacyFiltered = when (viewModel.uploadsPrivacyFilter) {
                    UploadsPrivacyFilter.ALL -> searched
                    UploadsPrivacyFilter.PUBLIC -> searched.filter {
                        it.sharing?.equals("private", ignoreCase = true) != true
                    }
                    UploadsPrivacyFilter.PRIVATE -> searched.filter {
                        it.sharing?.equals("private", ignoreCase = true) == true
                    }
                }

                val sortedTracks = when (viewModel.uploadsSortOption) {
                    UploadsSortOption.RECENTLY_ADDED -> privacyFiltered
                    UploadsSortOption.FIRST_ADDED -> privacyFiltered.reversed()
                    UploadsSortOption.TITLE -> privacyFiltered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title.orEmpty() })
                    UploadsSortOption.ARTIST -> privacyFiltered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.user?.username.orEmpty() })
                }

                if (sortedTracks.isEmpty()) {
                    item(span = { GridItemSpan(if (viewModel.isGridLayout) 2 else 1) }, key = "empty_uploads") {
                        EmptyUploadsView()
                    }
                } else {
                    items(
                        items = sortedTracks,
                        key = { track -> "upload_track_${track.id}" }
                    ) { track ->
                        val index = sortedTracks.indexOf(track)
                        val trackIndex = if (index >= 0) index else 0
                        Box(modifier = Modifier.animateItem()) {
                            if (viewModel.isGridLayout) {
                                UploadTrackGridCard(
                                    track = track,
                                    onClick = {
                                        playerViewModel.playPlaylist(sortedTracks, trackIndex)
                                    },
                                    onOptionClick = {
                                        playerViewModel.showTrackOptions(track)
                                    },
                                    onArtistClick = { playerViewModel.navigateToTrackArtist(it) }
                                )
                            } else {
                                UploadTrackListCard(
                                    track = track,
                                    onClick = {
                                        playerViewModel.playPlaylist(sortedTracks, trackIndex)
                                    },
                                    onOptionClick = {
                                        playerViewModel.showTrackOptions(track)
                                    },
                                    onArtistClick = { playerViewModel.navigateToTrackArtist(it) }
                                )
                            }
                        }
                    }
                }
                return@LazyVerticalGrid
            }

            if (shouldShowPlaylists) {
                item(span = { GridItemSpan(1) }, key = "liked_tracks") {
                    Box(modifier = Modifier.animateItem()) {
                        val subtitle = if (isGuest) str("lib_liked_subtitle_local")
                        else if(isSyncing) str("lib_liked_subtitle_syncing")
                        else str("lib_liked_subtitle")
                        StaticLibraryCard(
                            title = str("lib_liked_tracks"),
                            subtitle = subtitle,
                            icon = Icons.Rounded.Favorite,
                            isGrid = viewModel.isGridLayout,
                            isPinned = true,
                            onClick = onLikedTracksClick,
                            isLoading = isSyncing
                        )
                    }
                }
    
                item(span = { GridItemSpan(1) }, key = "downloads") {
                    Box(modifier = Modifier.animateItem()) {
                        StaticLibraryCard(
                            title = str("lib_downloads"),
                            subtitle = str("lib_downloads_subtitle"),
                            icon = Icons.Rounded.Folder,
                            isGrid = viewModel.isGridLayout,
                            isPinned = true,
                            onClick = { onPlaylistClick("downloads") },
                            isLoading = false
                        )
                    }
                }
    
                if (viewModel.showLocalMedia) {
                    item(span = { GridItemSpan(1) }, key = "local_media") {
                        Box(modifier = Modifier.animateItem()) {
                            StaticLibraryCard(
                                title = str("lib_local_media"),
                                subtitle = str("lib_local_media_subtitle"),
                                icon = Icons.Default.SdStorage,
                                isGrid = viewModel.isGridLayout,
                                isPinned = true,
                                onClick = { onPlaylistClick("local_files") },
                                isLoading = false
                            )
                        }
                    }
                }
            }
    
            items(
                items = viewModel.displayedItems,
                key = { item -> item.key }
            ) { item ->
                val showPin = item.isPinned && !isInsideFolder
                Box(modifier = Modifier.animateItem()) {
                    when (item) {
                        is LibraryItem.FolderItem -> {
                            val subtitle = if (item.playlistCount == 0 && item.folderCount == 0) str("lib_folder_empty")
                            else "${item.playlistCount} ${str("lib_playlists")}" +
                                    (if (item.folderCount > 0) " • ${item.folderCount} ${str("lib_folders")}" else "")
                            StaticLibraryCard(
                                title = item.folder.name,
                                subtitle = subtitle,
                                icon = Icons.Rounded.Folder,
                                isGrid = viewModel.isGridLayout,
                                isPinned = showPin,
                                onClick = { viewModel.navigateToFolder(item.folder) },
                                onRightClick = { onFolderRightClick(item.folder) },
                                isLoading = false
                            )
                        }
                        is LibraryItem.PlaylistItem -> {
                            val permalink = item.playlist.permalinkUrl
                            val isYoutubeShortcut = permalink != null && permalink.startsWith("yt_radio:")
                            val isTrackStation = item.playlist.isTrackStation || permalink?.contains("track-stations") == true || item.playlist.urn?.contains("track-stations") == true
                            val isArtistStation = item.playlist.isArtistStation || permalink?.contains("artist-stations") == true || item.playlist.urn?.contains("artist-stations") == true
 
                            val navId = if (isYoutubeShortcut) {
                                java.net.URLEncoder.encode(permalink, "UTF-8")
                            } else if (isTrackStation) {
                                "station:${item.playlist.numericId}"
                            } else if (isArtistStation) {
                                "station_artist:${item.playlist.numericId}"
                            } else if (item.playlist.urn?.startsWith("soundcloud:system-playlists:") == true) {
                                "system_playlist:${item.playlist.urn}"
                            } else {
                                if (item.playlist.id < 0) "local_playlist:${item.playlist.id}" else item.playlist.id.toString()
                            }
        
                            DynamicPlaylistCard(
                                playlist = item.playlist,
                                isGrid = viewModel.isGridLayout,
                                isPinned = showPin,
                                onClick = { onPlaylistClick(navId) },
                                onRightClick = { onPlaylistRightClick(item.playlist) }
                            )
                        }
                        is LibraryItem.ArtistItem -> {
                            ArtistLibraryCard(
                                artist = item.artist,
                                isGrid = viewModel.isGridLayout,
                                isPinned = showPin,
                                onClick = { onArtistClick(item.artist.id) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun LibraryShimmerGrid(isGridLayout: Boolean) {
        val columns = if (isGridLayout) GridCells.Fixed(2) else GridCells.Fixed(1)
        LazyVerticalGrid(
            columns = columns,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(10) {
                SquareCardShimmer()
            }
        }
    }
    
    @Composable
    fun SearchBarHeader(
        query: String,
        onQueryChange: (String) -> Unit,
        avatarUrl: String?,
        onProfileClick: () -> Unit,
        isGuest: Boolean
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .trackTextInput()
                .padding(horizontal = 16.dp),
            placeholder = { Text(str("search_library_hint")) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = str("search_library_hint"))
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                    Box(modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(CircleShape)
                        .clickable { onProfileClick() }
                    ) {
                        ArtistAvatar(
                            avatarUrl = if (isGuest) null else avatarUrl,
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                        )
                    }
                }
            },
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            singleLine = true
        )
    }
    
    @Composable
    fun FilterChipsRow(viewModel: LibraryViewModel) {
        val playlistsLabel = str("lib_playlists")
        val albumsLabel = str("lib_albums")
        val artistsLabel = str("lib_artists")
        val stationsLabel = str("lib_stations")
        val uploadsLabel = str("lib_your_uploads")
        val filters = remember(viewModel.uploadedTracks.size) {
            if (viewModel.uploadedTracks.isNotEmpty()) {
                listOf(playlistsLabel, albumsLabel, artistsLabel, stationsLabel, uploadsLabel)
            } else {
                listOf(playlistsLabel, albumsLabel, artistsLabel, stationsLabel)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val selected = viewModel.selectedFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = {
                        viewModel.selectedFilter = if (selected) null else filter
                    },
                    label = { Text(filter) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
    }

    @Composable
    fun SortAndLayoutControls(viewModel: LibraryViewModel) {
        var showUploadsFilterDialog by remember { mutableStateOf(false) }

        if (showUploadsFilterDialog) {
            UploadsFilterDialog(
                viewModel = viewModel,
                onDismiss = { showUploadsFilterDialog = false }
            )
        }

        val playlistsLabel = str("lib_playlists")
        val albumsLabel = str("lib_albums")
        val uploadsLabel = str("lib_your_uploads")
        val isUploads = viewModel.selectedFilter == uploadsLabel
        val shouldShowOwnershipFilter = (viewModel.selectedFilter == null || viewModel.selectedFilter == playlistsLabel || viewModel.selectedFilter == albumsLabel) && viewModel.currentFolderId == null

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isUploads) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        showUploadsFilterDialog = true
                    }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sortText = str(viewModel.uploadsSortOption.stringResKey)
                    Text(text = sortText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = str("uploads_filter_dialog_title"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (shouldShowOwnershipFilter) {
                val filterText = when (viewModel.ownershipFilter) {
                    OwnershipFilter.ALL -> str("filter_all")
                    OwnershipFilter.CREATED -> str("filter_created")
                    OwnershipFilter.LIKED -> str("filter_liked")
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        viewModel.ownershipFilter = when (viewModel.ownershipFilter) {
                            OwnershipFilter.ALL -> OwnershipFilter.CREATED
                            OwnershipFilter.CREATED -> OwnershipFilter.LIKED
                            OwnershipFilter.LIKED -> OwnershipFilter.ALL
                        }
                    }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = filterText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.FilterList,
                        contentDescription = filterText, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { viewModel.isSortDescending = !viewModel.isSortDescending }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = str("sort_date_added"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (viewModel.isSortDescending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                        contentDescription = str("sort_date_added"), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(
                onClick = { viewModel.isGridLayout = !viewModel.isGridLayout },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = if (viewModel.isGridLayout) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                    contentDescription = str("btn_options"), tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun StaticLibraryCard(
        title: String,
        subtitle: String,
        icon: ImageVector,
        isGrid: Boolean,
        isLoading: Boolean,
        isPinned: Boolean = false,
        onClick: () -> Unit,
        onRightClick: (() -> Unit)? = null
    ) {
        val height = if (isGrid) 160.dp else 80.dp
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            interactionSource = interaction,
            colors = CardDefaults.cardColors(
                containerColor = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier
                .height(height)
                .fillMaxWidth()
                .hoverable(interaction)
                .then(
                    if (onRightClick != null) {
                        Modifier.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onRightClick)
                    } else Modifier
                )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isGrid) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPinned) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pinned",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isPinned) {
                                    Icon(
                                        imageVector = Icons.Rounded.PushPin,
                                        contentDescription = "Pinned",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (isLoading) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }
    }
    
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun DynamicPlaylistCard(
        playlist: Playlist,
        isGrid: Boolean,
        isPinned: Boolean = false,
        onClick: () -> Unit,
        onRightClick: (() -> Unit)? = null
    ) {
        // Resolves the first track's cover for playlists that ship none, rather than
        // letting fullResArtwork hand every one of them the same stock photo.
        val art = com.alananasss.kittytune.ui.common.rememberPlaylistCover(playlist)
        val isRadioShortcut = playlist.permalinkUrl?.startsWith("yt_radio:") == true
        val subtitleText = if (isRadioShortcut) {
            str("radio")
        } else {
            str("playlist_num_tracks", playlist.trackCount ?: 0)
        }
    
        val authorText = playlist.user?.username ?: str("me_artist")
        val likesText = if (playlist.likesCount != null && playlist.likesCount > 0) " • ${playlist.likesCount} likes" else ""
        val finalSubtitle = if (isRadioShortcut) "$subtitleText • YouTube" else "$subtitleText • $authorText$likesText"
    
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val hoverBg = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent

        if (isGrid) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(hoverBg)
                    .hoverable(interaction)
                    .then(
                        if (onRightClick != null) {
                            Modifier.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onRightClick)
                        } else Modifier
                    )
                    .clickable(onClick = onClick)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!art.isNullOrBlank()) {
                        AsyncImage(
                            model = art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
    
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.title ?: str("app_name"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playlist.sharing == "private" || playlist.sharing == "secret") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Rounded.Lock,
                            contentDescription = "Private",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = finalSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(hoverBg)
                    .hoverable(interaction)
                    .then(
                        if (onRightClick != null) {
                            Modifier.onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary), onClick = onRightClick)
                        } else Modifier
                    )
                    .clickable(onClick = onClick)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (!art.isNullOrBlank()) {
                        AsyncImage(
                            model = art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = playlist.title ?: str("app_name"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (playlist.sharing == "private" || playlist.sharing == "secret") {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.Lock,
                                contentDescription = "Private",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = finalSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun ArtistLibraryCard(artist: LocalArtist, isGrid: Boolean, isPinned: Boolean = false, onClick: () -> Unit) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val hoverBg = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent

        if (isGrid) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(hoverBg)
                    .hoverable(interaction)
                    .clickable(onClick = onClick)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ArtistAvatar(
                    avatarUrl = artist.avatarUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = artist.username, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(text = str("menu_go_artist"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(hoverBg)
                    .hoverable(interaction)
                    .clickable(onClick = onClick)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtistAvatar(
                    avatarUrl = artist.avatarUrl,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = artist.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(text = str("menu_go_artist") + " • " + str("playlist_num_tracks", artist.trackCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    @Composable
    fun EmptyUploadsView() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = str("empty_uploads_kaomoji"),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = str("empty_uploads_title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = str("empty_uploads_subtitle"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    @Composable
    fun UploadTrackGridCard(
        track: Track,
        onClick: () -> Unit,
        onOptionClick: () -> Unit,
        onArtistClick: ((Track) -> Unit)? = null
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val hoverBg = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
        val isPrivate = track.sharing?.equals("private", ignoreCase = true) == true

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(hoverBg)
                .hoverable(interaction)
                .clickable(onClick = onClick)
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPrivate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Private",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title ?: str("untitled_track"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ArtistLinkText(
                        track = track,
                        onArtistClick = onArtistClick,
                        text = track.displayArtist.ifBlank { str("unknown_artist") },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(
                    onClick = onOptionClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = str("btn_options"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    @Composable
    fun UploadTrackListCard(
        track: Track,
        onClick: () -> Unit,
        onOptionClick: () -> Unit,
        onArtistClick: ((Track) -> Unit)? = null
    ) {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val hoverBg = if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
        val isPrivate = track.sharing?.equals("private", ignoreCase = true) == true

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(hoverBg)
                .hoverable(interaction)
                .clickable(onClick = onClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPrivate) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Private",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: str("untitled_track"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                ArtistLinkText(
                    track = track,
                    onArtistClick = onArtistClick
                )
            }
            IconButton(onClick = onOptionClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = str("btn_options"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    fun UploadsFilterDialog(
        viewModel: LibraryViewModel,
        onDismiss: () -> Unit
    ) {
        var selectedSort by remember { mutableStateOf(viewModel.uploadsSortOption) }
        var selectedPrivacy by remember { mutableStateOf(viewModel.uploadsPrivacyFilter) }

        EscapableAlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = str("uploads_filter_dialog_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Sort section
                    Text(
                        text = str("uploads_sort_section_title"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    UploadsSortOption.entries.forEach { option ->
                        val isSelected = selectedSort == option
                        Surface(
                            onClick = { selectedSort = option },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = str(option.stringResKey),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Privacy section
                    Text(
                        text = str("uploads_filter_section_title"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    UploadsPrivacyFilter.entries.forEach { filter ->
                        val isSelected = selectedPrivacy == filter
                        Surface(
                            onClick = { selectedPrivacy = filter },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = str(filter.stringResKey),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.uploadsSortOption = selectedSort
                        viewModel.uploadsPrivacyFilter = selectedPrivacy
                        onDismiss()
                    }
                ) {
                    Text(str("uploads_filter_save_btn"))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

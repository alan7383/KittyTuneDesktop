@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    package com.alananasss.kittytune.ui.library
    
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
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.GridView
    import androidx.compose.material.icons.filled.SdStorage
    import androidx.compose.material.icons.filled.Search
    import androidx.compose.material.icons.filled.ViewList
    import androidx.compose.material.icons.outlined.ExitToApp
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
    import com.alananasss.kittytune.core.str
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

        import com.alananasss.kittytune.data.DownloadManager
    import com.alananasss.kittytune.data.TokenManager
    import com.alananasss.kittytune.data.local.LocalArtist
    import com.alananasss.kittytune.domain.Playlist
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
    
        // keep an eye on network status

        LaunchedEffect(libraryViewModel.selectedFilter) {
            listState.scrollToItem(0)
        }
    
        // refresh when coming back to the screen
        
    
        var showCreateDialog by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }
        var isCreatingPlaylist by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
    
        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { if (!isCreatingPlaylist) showCreateDialog = false },
                title = { Text(str("lib_create_playlist_title")) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text(str("lib_create_playlist_hint")) },
                        singleLine = true,
                        enabled = !isCreatingPlaylist
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPlaylistName.isNotBlank() && !isCreatingPlaylist) {
                                val playlistName = newPlaylistName
                                isCreatingPlaylist = true
                                scope.launch {
                                    val id = DownloadManager.createUserPlaylist(playlistName)
                                    if (id > 0) {
                                        val api = com.alananasss.kittytune.data.network.RetrofitClient.create()
                                        for (i in 0..15) {
                                            try {
                                                api.getPlaylist(id)
                                                break
                                            } catch (e: Exception) {
                                                kotlinx.coroutines.delay(1000)
                                            }
                                        }
                                    }
                                    isCreatingPlaylist = false
                                    showCreateDialog = false
                                    newPlaylistName = ""
                                    val navId = if (id < 0) "local_playlist:$id" else id.toString()
                                    onPlaylistClick(navId)
                                }
                            }
                        },
                        enabled = !isCreatingPlaylist
                    ) {
                        if (isCreatingPlaylist) {
                            ContainedLoadingIndicator()
                        } else {
                            Text(str("btn_create"))
                        }
                    }
                },
                dismissButton = { 
                    TextButton(
                        onClick = { showCreateDialog = false },
                        enabled = !isCreatingPlaylist
                    ) { 
                        Text(str("btn_cancel")) 
                    } 
                }
            )
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
                }
            },
            floatingActionButton = {
                if (!showLogin || isGuest) {
                    // bump up fab so bottom nav and miniplayer don't cover it
                    val bottomNavHeight = 90.dp
                    val miniPlayerHeight = if (playerViewModel.currentTrack != null) 72.dp else 0.dp
                    val totalBottomPadding = bottomNavHeight + miniPlayerHeight
    
                    ExtendedFloatingActionButton(
                        onClick = { showCreateDialog = true },
                        icon = { Icon(Icons.Rounded.Add, str("lib_create_playlist_title")) },
                        text = { Text(str("lib_create_playlist_title")) },
                        expanded = fabExpanded,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = totalBottomPadding)
                    )
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
                        Button(onClick = onLoginClick) { Text(str("login_soundcloud")) }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(innerPadding)) {
                    SortAndLayoutControls(libraryViewModel)
    
                    if (libraryViewModel.isLoading && libraryViewModel.displayedItems.isEmpty()) {
                        LibraryShimmerGrid(isGridLayout = libraryViewModel.isGridLayout)
                    } else {
                        LibraryContentGrid(
                            listState = listState,
                            viewModel = libraryViewModel,
                            onLikedTracksClick = onLikedTracksClick,
                            onPlaylistClick = onPlaylistClick,
                            onArtistClick = { artistId -> onPlaylistClick("profile:$artistId") },
                            isGuest = isGuest
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun LibraryContentGrid(
        listState: LazyGridState,
        viewModel: LibraryViewModel,
        onLikedTracksClick: () -> Unit,
        onPlaylistClick: (String) -> Unit,
        onArtistClick: (Long) -> Unit,
        isGuest: Boolean
    ) {
        val columns = if (viewModel.isGridLayout) GridCells.Fixed(2) else GridCells.Fixed(1)
        val isSyncing by viewModel.isSyncing.collectAsState()
    
        // grab strings here before using them in logic
        val playlistsFilter = str("lib_playlists")
        val shouldShowPlaylists = viewModel.selectedFilter == null || viewModel.selectedFilter == playlistsFilter
    
        LazyVerticalGrid(
            state = listState,
            columns = columns,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                                onClick = { onPlaylistClick("local_files") },
                                isLoading = false
                            )
                        }
                    }
                }
            }
    
            items(
                items = viewModel.displayedItems,
                key = { item ->
                    when (item) {
                        is LibraryItem.PlaylistItem -> "playlist_${item.playlist.id}_${item.playlist.permalinkUrl ?: ""}"
                        is LibraryItem.ArtistItem -> "artist_${item.artist.id}"
                    }
                }
            ) { item ->
                Box(modifier = Modifier.animateItem()) {
                    when (item) {
                        is LibraryItem.PlaylistItem -> {
                            val permalink = item.playlist.permalinkUrl
                            val isYoutubeShortcut = permalink != null && permalink.startsWith("yt_radio:")
        
                            val navId = if (isYoutubeShortcut) {
                                java.net.URLEncoder.encode(permalink!!, "UTF-8")
                            } else if (item.playlist.urn?.startsWith("soundcloud:system-playlists:") == true) {
                                "system_playlist:${item.playlist.urn}"
                            } else {
                                if (item.playlist.id < 0) "local_playlist:${item.playlist.id}" else item.playlist.id.toString()
                            }
        
                            DynamicPlaylistCard(
                                playlist = item.playlist,
                                isGrid = viewModel.isGridLayout,
                                onClick = { onPlaylistClick(navId) }
                            )
                        }
                        is LibraryItem.ArtistItem -> {
                            ArtistLibraryCard(
                                artist = item.artist,
                                isGrid = viewModel.isGridLayout,
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
                .padding(horizontal = 16.dp),
            placeholder = { Text(str("search_library_hint")) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = str("search_library_hint"))
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
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
    
        val filters = remember(playlistsLabel, albumsLabel, artistsLabel, stationsLabel) {
            listOf(playlistsLabel, albumsLabel, artistsLabel, stationsLabel)
        }
    
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { label ->
                val isSelected = viewModel.selectedFilter == label
                
                val containerColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    label = "chip_container_color"
                )
                val contentColor by androidx.compose.animation.animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "chip_content_color"
                )

                Button(
                    onClick = { 
                        
                        viewModel.selectedFilter = if (isSelected) null else label 
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    )
                ) {
                    Text(label)
                }
            }
        }
    }
    
    @Composable
    fun SortAndLayoutControls(viewModel: LibraryViewModel) {
        val playlistsLabel = str("lib_playlists")
        val albumsLabel = str("lib_albums")
        val shouldShowOwnershipFilter = viewModel.selectedFilter == null || viewModel.selectedFilter == playlistsLabel || viewModel.selectedFilter == albumsLabel

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (shouldShowOwnershipFilter) {
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
            IconButton(onClick = { viewModel.isGridLayout = !viewModel.isGridLayout }) {
                Icon(
                    imageVector = if (viewModel.isGridLayout) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = str("btn_options"), tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    @Composable
    fun StaticLibraryCard(title: String, subtitle: String, icon: ImageVector, isGrid: Boolean, isLoading: Boolean, onClick: () -> Unit) {
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
            modifier = Modifier.height(height).fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isGrid) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // fix applied here
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
    
    @Composable
    fun DynamicPlaylistCard(playlist: Playlist, isGrid: Boolean, onClick: () -> Unit) {
        val art = playlist.fullResArtwork
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
                    .clickable(onClick = onClick)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                )
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
                Text(
                    text = finalSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
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
                    Text(
                        text = finalSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    
    @Composable
    fun ArtistLibraryCard(artist: LocalArtist, isGrid: Boolean, onClick: () -> Unit) {
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
                Text(text = str("menu_go_artist"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(text = str("menu_go_artist") + " • " + str("playlist_num_tracks", artist.trackCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }



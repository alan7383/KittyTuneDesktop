package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.savedstate.read
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.ui.home.HomeViewModel
import com.alananasss.kittytune.ui.library.LibraryViewModel
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.common.CoverViewerOverlay

/**
 * Desktop app shell — Spotify-style three-panel layout in Material 3 Expressive:
 *
 *  ┌────────────┬──────────────────────────────┬───────────────┐
 *  │  Sidebar   │   Content (NavHost + TopBar) │  Now Playing  │
 *  │ (library)  │                              │  (toggleable) │
 *  ├────────────┴──────────────────────────────┴───────────────┤
 *  │                        PlayerBar                          │
 *  └───────────────────────────────────────────────────────────┘
 *
 * Panels are rounded surfaces floating on the window background, separated by
 * small gutters — same structure as the reference, themed with KittyTune colors.
 */

val PanelShape get() = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
const val PANEL_GUTTER = 8

@Composable
fun MainScreen() {
    val playerViewModel: PlayerViewModel = viewModel { PlayerViewModel(AppInstance.application) }
    val homeViewModel: HomeViewModel = viewModel { HomeViewModel(AppInstance.application) }
    val libraryViewModel: LibraryViewModel = viewModel { LibraryViewModel(AppInstance.application) }

    val navController = rememberNavController()
    var showNowPlayingPanel by remember { mutableStateOf(true) }
    var nowPlayingTab by remember { mutableStateOf(NowPlayingTab.TRACK) }


    // Close full-screen lyrics when navigation happens (e.g. sidebar click)
    val backStackEntry by navController.currentBackStackEntryAsState()
    androidx.compose.runtime.LaunchedEffect(backStackEntry) {
        playerViewModel.showLyricsSheet = false
    }

    // Same navigation protocol as the Android MainScreen: PlayerViewModel exposes
    // destination ids ("likes", "profile:<id>", numeric playlist ids, ...) that we
    // translate into NavHost routes.
    androidx.compose.runtime.LaunchedEffect(playerViewModel.navigateToPlaylistId) {
        playerViewModel.navigateToPlaylistId?.let { destinationId ->
            val targetRoute = when {
                destinationId == "history" -> "history"
                destinationId == "upload" -> "upload"
                destinationId == "recognition" -> "recognition"
                destinationId == "recognition_history" -> "recognition_history"
                destinationId.startsWith("edit_track:") -> "edit_track/${destinationId.removePrefix("edit_track:")}"
                destinationId.startsWith("profile:") -> "profile/${destinationId.removePrefix("profile:")}"
                destinationId.startsWith("tag:") -> "tag/${destinationId.removePrefix("tag:")}"
                destinationId.startsWith("track_detail:") -> "track_detail/${destinationId.removePrefix("track_detail:")}"
                destinationId.startsWith("playlist_fans/") -> destinationId
                else -> "playlist_detail/${java.net.URLEncoder.encode(destinationId, "UTF-8")}"
            }

            if (destinationId == "expanded_queue") {
                showNowPlayingPanel = true
                nowPlayingTab = NowPlayingTab.QUEUE
            } else if (!isSameRoute(navController, targetRoute)) {
                playerViewModel.isPlayerExpanded = false
                playerViewModel.showLyricsSheet = false
                navController.navigate(targetRoute)
            }
            playerViewModel.onNavigationHandled()
        }
    }

    var showShortcutsDialog by remember { mutableStateOf(false) }

    // Sync the user's SoundCloud followings into the local DB at app startup.
    // This enables the social proof "liked by" feature in the player to correctly
    // detect followed users among track likers (same as Android KittyTune).
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.alananasss.kittytune.data.DownloadManager.refreshFollowings()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {

        var gPressedTime = 0L
        var lastShortcutTime = 0L
        var lastShortcutKey: androidx.compose.ui.input.key.Key? = null
        com.alananasss.kittytune.core.GlobalShortcutDispatcher.keyEvents.collect { event ->
            if (event.type == KeyEventType.KeyDown) {
                if (com.alananasss.kittytune.core.TextInputTracker.isFocused()) return@collect

                val debounceNow = System.currentTimeMillis()
                if (debounceNow - lastShortcutTime < 250 && event.key == lastShortcutKey) return@collect
                lastShortcutTime = debounceNow
                lastShortcutKey = event.key

                val isShift = event.isShiftPressed
                val isCtrl = event.isCtrlPressed
                val isAlt = event.isAltPressed
                val isMeta = event.isMetaPressed
                val noModifiers = !isShift && !isCtrl && !isAlt && !isMeta

                val now = System.currentTimeMillis()

                if (noModifiers && event.key == Key.G) {
                    gPressedTime = now
                    return@collect
                }

                val isGSequence = (now - gPressedTime) < 1000 // 1 second window
                
                if (isGSequence && noModifiers) {
                    when (event.key) {
                        Key.L -> navController.navigate("playlist_detail/likes")
                        Key.C -> navController.navigate("home") // Actually, navigating to library root? I'll use home for now. Or maybe there is no library route, Sidebar has it.
                        Key.H -> navController.navigate("history")
                        Key.S -> navController.navigate("feed")
                        Key.P -> {
                            val selfId = playerViewModel.currentUserId.takeIf { it != 0L }?.toString()
                            if (selfId != null) navController.navigate("profile/$selfId")
                        }
                    }
                    gPressedTime = 0L
                    return@collect
                }
                
                gPressedTime = 0L

                if (!isCtrl && !isAlt && !isMeta) {
                    val char = event.utf16CodePoint.toChar()
                    val numberSeekFraction = when {
                        event.key == Key.Zero || event.key == Key.NumPad0 || char == '0' || char == 'à' -> 0.0
                        event.key == Key.One || event.key == Key.NumPad1 || char == '1' || char == '&' -> 0.1
                        event.key == Key.Two || event.key == Key.NumPad2 || char == '2' || char == 'é' -> 0.2
                        event.key == Key.Three || event.key == Key.NumPad3 || char == '3' || char == '"' -> 0.3
                        event.key == Key.Four || event.key == Key.NumPad4 || char == '4' || char == '\'' -> 0.4
                        event.key == Key.Five || event.key == Key.NumPad5 || char == '5' || char == '(' -> 0.5
                        event.key == Key.Six || event.key == Key.NumPad6 || char == '6' || char == '-' -> 0.6
                        event.key == Key.Seven || event.key == Key.NumPad7 || char == '7' || char == 'è' -> 0.7
                        event.key == Key.Eight || event.key == Key.NumPad8 || char == '8' || char == '_' -> 0.8
                        event.key == Key.Nine || event.key == Key.NumPad9 || char == '9' || char == 'ç' -> 0.9
                        else -> null
                    }
                    if (numberSeekFraction != null) {
                        val duration = playerViewModel.duration
                        if (duration > 0) {
                            playerViewModel.seekTo((duration * numberSeekFraction).toLong())
                        }
                        return@collect
                    }
                }

                if (isShift && noModifiers.not()) {
                    // Only shift pressed
                    if (isShift && !isCtrl && !isAlt && !isMeta) {
                        when (event.key) {
                            Key.DirectionRight -> playerViewModel.playNext()
                            Key.DirectionLeft -> playerViewModel.smartPrevious()
                            Key.L -> playerViewModel.toggleRepeatMode()
                            Key.S -> playerViewModel.toggleShuffle()
                            Key.DirectionDown -> playerViewModel.volumeDown()
                            Key.DirectionUp -> playerViewModel.volumeUp()
                        }
                    }
                } else if (noModifiers) {
                    when (event.key) {
                        Key.Spacebar -> playerViewModel.togglePlayPause()
                        Key.DirectionRight -> playerViewModel.seekTo((playerViewModel.currentPosition + 5000).coerceAtMost(playerViewModel.duration))
                        Key.DirectionLeft -> playerViewModel.seekTo((playerViewModel.currentPosition - 5000).coerceAtLeast(0))
                        Key.L -> playerViewModel.toggleLike()
                        Key.R -> {
                            playerViewModel.currentTrack?.let { playerViewModel.repostTrack(it, null) }
                        }
                        Key.S -> {
                            navController.navigate("home")
                            homeViewModel.activateSearch()
                        }
                        Key.M -> playerViewModel.toggleMute()
                        Key.P -> {
                            val track = playerViewModel.currentTrack
                            if (track != null) {
                                navController.navigate("track_detail/${track.id}")
                            }
                        }
                        Key.H -> showShortcutsDialog = true
                        Key.Q -> {
                            showNowPlayingPanel = true
                            nowPlayingTab = NowPlayingTab.QUEUE
                        }
                    }
                }
            }
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(PANEL_GUTTER.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f)
        ) {
            // Full-screen library replaces the sidebar + content panels but always
            // stops before the Now Playing panel (rendered after this block).
            if (libraryViewModel.isLibraryFullScreen) {
                LibraryPanel(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    fullScreen = true,
                    onImport = {
                        libraryViewModel.isLibraryFullScreen = false
                        navController.navigate("music_import")
                    },
                    onHistory = {
                        libraryViewModel.isLibraryFullScreen = false
                        navController.navigate("history")
                    },
                    onUpload = {
                        libraryViewModel.isLibraryFullScreen = false
                        navController.navigate("upload")
                    },
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            } else {

            var draggingSidebar by remember { mutableStateOf(false) }
            val targetSidebarWidth =
                if (libraryViewModel.isSidebarCollapsed) com.alananasss.kittytune.ui.library.SIDEBAR_COLLAPSED_WIDTH
                else libraryViewModel.sidebarWidth
            val animatedSidebarWidth by androidx.compose.animation.core.animateDpAsState(
                targetValue = targetSidebarWidth.dp,
                animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                label = "sidebarWidth"
            )
            val sidebarWidth = if (draggingSidebar) targetSidebarWidth.dp else animatedSidebarWidth

            Sidebar(
                navController = navController,
                libraryViewModel = libraryViewModel,
                playerViewModel = playerViewModel,
                homeViewModel = homeViewModel,
                modifier = Modifier.width(sidebarWidth)
            )

            // Resize handle: drag to resize the library, drag far left to snap it
            // into the icon rail. Shows a divider line on hover, like the reference.
            val handleInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val handleHovered by handleInteraction.collectIsHoveredAsState()
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(PANEL_GUTTER.dp)
                    .hoverable(handleInteraction)
                    .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(java.awt.Cursor.E_RESIZE_CURSOR)))
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { deltaPx ->
                            libraryViewModel.sidebarDragBy(with(density) { deltaPx.toDp().value })
                        },
                        onDragStarted = {
                            draggingSidebar = true
                            libraryViewModel.sidebarDragStart()
                        },
                        onDragStopped = {
                            draggingSidebar = false
                            libraryViewModel.sidebarDragEnd()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (handleHovered || draggingSidebar) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = if (draggingSidebar) 0.5f else 0.25f),
                                androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                            )
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = PanelShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                if (playerViewModel.showLyricsSheet) {
                    com.alananasss.kittytune.ui.player.lyrics.LyricsScreen(
                        viewModel = playerViewModel,
                        onClose = { playerViewModel.showLyricsSheet = false }
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        MainTopBar(
                            navController = navController,
                            homeViewModel = homeViewModel,
                            playerViewModel = playerViewModel,
                        )
                        NavHost(
                            navController = navController,
                        startDestination = "home",
                        modifier = Modifier.weight(1f)
                    ) {
                        composable("home") {
                            HomeContent(
                                homeViewModel = homeViewModel,
                                playerViewModel = playerViewModel,
                                navController = navController,
                            )
                        }
                        composable("genres") {
                            com.alananasss.kittytune.ui.home.GenresScreen(
                                onNavigate = { dest -> navController.navigate(dest) }
                            )
                        }
                        composable("feed") {
                            com.alananasss.kittytune.ui.feed.FeedScreen(
                                playerViewModel = playerViewModel,
                                navController = navController,
                            )
                        }
                        composable("profile") {
                            // Own profile (avatar click / sidebar): resolve to the logged-in user.
                            val selfId = playerViewModel.currentUserId.takeIf { it != 0L }?.toString()
                            if (selfId != null) {
                                com.alananasss.kittytune.ui.profile.ProfileScreen(
                                    userId = selfId,
                                    onBackClick = { navController.popBackStack() },
                                    playerViewModel = playerViewModel,
                                    onNavigate = { id ->
                                        when {
                                            id == "history" -> navController.navigate("history")
                                            id == "recognition_history" -> navController.navigate("recognition_history")
                                            id.startsWith("profile:") -> navController.navigate("profile/${id.removePrefix("profile:")}")
                                            id.startsWith("followers:") -> navController.navigate("followers/${id.removePrefix("followers:")}")
                                            id.startsWith("followings:") -> navController.navigate("followings/${id.removePrefix("followings:")}")
                                            id.startsWith("profile_collection:") -> navController.navigate("profile_collection/${id.removePrefix("profile_collection:").replace(':', '/')}")
                                            else -> navController.navigate("playlist_detail/${java.net.URLEncoder.encode(id, "UTF-8")}")
                                        }
                                    }
                                )
                            } else {
                                PlaceholderScreen(com.alananasss.kittytune.core.str("nav_login"))
                            }
                        }
                        composable("settings") {
                            com.alananasss.kittytune.ui.profile.SettingsScreen(
                                navController = navController,
                                onBackClick = null,
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("appearance_settings") {
                            com.alananasss.kittytune.ui.profile.AppearanceSettingsScreen(
                                onNavigateToColors = { navController.navigate("color_palette") },
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("color_palette") { 
                            com.alananasss.kittytune.ui.profile.ColorPaletteScreen(
                                onBackClick = { navController.popBackStack() }
                            ) 
                        }
                        composable("discord_login") {
                            com.alananasss.kittytune.ui.profile.DiscordLoginScreen(
                                onBackClick = { navController.popBackStack() },
                                onLoginSuccess = { navController.popBackStack() },
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("lyrics_settings") {
                            com.alananasss.kittytune.ui.profile.LyricsSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("proxy_settings") {
                            com.alananasss.kittytune.ui.profile.ProxySettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        // Settings sub-pages are now handled within SettingsScreen's Split Pane layout
                        composable("playlist_detail/{playlistId}") { backStackEntry ->
                            val id = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("playlistId") } }.getOrNull()
                            } ?: ""
                            com.alananasss.kittytune.ui.library.PlaylistDetailScreen(
                                playlistId = id,
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { dest ->
                                    when {
                                        dest == "history" -> navController.navigate("history")
                                        dest == "recognition_history" -> navController.navigate("recognition_history")
                                        dest.startsWith("tag:") -> navController.navigate("tag/${dest.removePrefix("tag:")}")
                                        dest.startsWith("profile:") -> navController.navigate("profile/${dest.removePrefix("profile:")}")
                                        dest.startsWith("playlist_fans/") -> navController.navigate(dest)
                                        else -> navController.navigate("playlist_detail/${java.net.URLEncoder.encode(dest, "UTF-8")}")
                                    }
                                },
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("profile/{userId}") { backStackEntry ->
                            val userId = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("userId") } }.getOrNull()
                            } ?: ""
                            com.alananasss.kittytune.ui.profile.ProfileScreen(
                                userId = userId,
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel,
                                onNavigate = { id ->
                                    when {
                                        id == "history" -> navController.navigate("history")
                                        id == "recognition_history" -> navController.navigate("recognition_history")
                                        id.startsWith("profile:") -> navController.navigate("profile/${id.removePrefix("profile:")}")
                                        id.startsWith("followers:") -> navController.navigate("followers/${id.removePrefix("followers:")}")
                                        id.startsWith("followings:") -> navController.navigate("followings/${id.removePrefix("followings:")}")
                                        id.startsWith("profile_collection:") -> navController.navigate("profile_collection/${id.removePrefix("profile_collection:").replace(':', '/')}")
                                        else -> navController.navigate("playlist_detail/${java.net.URLEncoder.encode(id, "UTF-8")}")
                                    }
                                }
                            )
                        }
                        composable("followers/{userId}") { backStackEntry ->
                            val uidStr = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("userId") } }.getOrNull()
                            } ?: ""
                            com.alananasss.kittytune.ui.profile.UserListScreen(
                                userId = uidStr.toLongOrNull() ?: 0L,
                                type = "followers",
                                onBack = { navController.popBackStack() },
                                onUserClick = { uid -> navController.navigate("profile/$uid") }
                            )
                        }
                        composable("followings/{userId}") { backStackEntry ->
                            val uidStr = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("userId") } }.getOrNull()
                            } ?: ""
                            com.alananasss.kittytune.ui.profile.UserListScreen(
                                userId = uidStr.toLongOrNull() ?: 0L,
                                type = "followings",
                                onBack = { navController.popBackStack() },
                                onUserClick = { uid -> navController.navigate("profile/$uid") }
                            )
                        }
                        composable("profile_collection/{userId}/{section}") { backStackEntry ->
                            val userId = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("userId") } }.getOrNull()
                            } ?: ""
                            val section = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("section") } }.getOrNull()
                            } ?: ""
                            com.alananasss.kittytune.ui.profile.ProfileCollectionScreen(
                                userId = userId,
                                section = section,
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel
                            )
                        }

                        composable("tag/{tagName}") { backStackEntry ->
                            val tagName = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("tagName") } }.getOrNull()
                            } ?: ""
                            com.alananasss.kittytune.ui.home.TagScreen(
                                tagName = tagName,
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("track_detail/{trackId}?tab={tabIndex}") { backStackEntry ->
                            val rawTrackId = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("trackId") } }.getOrNull()
                            } ?: ""
                            val cleanTrackId = rawTrackId.substringBefore("?").substringBefore("&")
                            val trackId = cleanTrackId.toLongOrNull() ?: 0L

                            val rawTab = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("tabIndex") } }.getOrNull()
                            }
                            val tabIndex = rawTab?.toIntOrNull()
                                ?: if (rawTrackId.contains("tab=")) rawTrackId.substringAfter("tab=").substringBefore("&").toIntOrNull() ?: 0
                                else 0

                            com.alananasss.kittytune.ui.track.TrackDetailScreen(
                                trackId = trackId,
                                initialTab = tabIndex,
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { id ->
                                    if (id.startsWith("profile:")) navController.navigate("profile/${id.removePrefix("profile:")}")
                                    else navController.navigate("playlist_detail/${java.net.URLEncoder.encode(id, "UTF-8")}")
                                },
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("playlist_fans/{playlistId}?tab={tabIndex}") { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("playlistId") } }.getOrNull()
                            } ?: ""
                            val tabIndex = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("tabIndex") } }.getOrNull()
                            }?.toIntOrNull() ?: 0
                            com.alananasss.kittytune.ui.library.PlaylistFansScreen(
                                playlistId = playlistId,
                                initialTab = tabIndex,
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { id ->
                                    if (id.startsWith("profile:")) navController.navigate("profile/${id.removePrefix("profile:")}")
                                }
                            )
                        }
                        composable("charts") {
                            com.alananasss.kittytune.ui.home.ChartsScreen(
                                onBackClick = { navController.popBackStack() },
                                onPlaylistClick = { playlistId ->
                                    navController.navigate("playlist_detail/$playlistId")
                                },
                                onNavigate = { route ->
                                    when {
                                        route.startsWith("profile:") -> navController.navigate("profile/${route.removePrefix("profile:")}")
                                        route.startsWith("station_artist:") -> navController.navigate("playlist_detail/$route")
                                        else -> navController.navigate(route)
                                    }
                                },
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("new_releases") {
                            com.alananasss.kittytune.ui.home.NewReleasesScreen(
                                onBackClick = { navController.popBackStack() },
                                onPlaylistClick = { playlistId ->
                                    navController.navigate("playlist_detail/$playlistId")
                                },
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("genre_detail/{genreName}/{genreQuery}") { backStackEntry ->
                            val genreName = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("genreName") } }.getOrNull()
                            } ?: ""
                            val genreQuery = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("genreQuery") } }.getOrNull()
                            } ?: ""
                            com.alananasss.kittytune.ui.home.GenreDetailScreen(
                                genreName = java.net.URLDecoder.decode(genreName, "UTF-8"),
                                genreQuery = java.net.URLDecoder.decode(genreQuery, "UTF-8"),
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { dest -> navController.navigate(dest) },
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("genre_playlists/{genreName}/{genreQuery}") { backStackEntry ->
                            val genreName = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("genreName") } }.getOrNull()
                            } ?: ""
                            val genreQuery = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("genreQuery") } }.getOrNull()
                            } ?: ""
                            com.alananasss.kittytune.ui.home.GenrePlaylistsScreen(
                                genreTitle = java.net.URLDecoder.decode(genreName, "UTF-8"),
                                query = java.net.URLDecoder.decode(genreQuery, "UTF-8"),
                                onBackClick = { navController.popBackStack() },
                                onPlaylistClick = { id -> navController.navigate("playlist_detail/$id") }
                            )
                        }
                        composable("recognition") {
                            com.alananasss.kittytune.ui.recognition.RecognitionScreen(
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel,
                                onNavigate = { dest -> navController.navigate(dest) }
                            )
                        }
                        composable("recognition_history") {
                            com.alananasss.kittytune.ui.recognition.RecognitionHistoryScreen(
                                onBackClick = null,
                                onNavigate = { dest -> navController.navigate(dest) },
                                playerViewModel = playerViewModel
                            )
                        }
                        composable("history") {
                            val historyViewModel: com.alananasss.kittytune.ui.history.HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel {
                                com.alananasss.kittytune.ui.history.HistoryViewModel(com.alananasss.kittytune.core.AppInstance.application)
                            }
                            com.alananasss.kittytune.ui.history.HistoryScreen(
                                onBackClick = null,
                                onNavigate = { dest -> navController.navigate(dest) },
                                playerViewModel = playerViewModel,
                                historyViewModel = historyViewModel
                            )
                        }
                        composable("music_import") {
                            com.alananasss.kittytune.ui.musicimport.MusicImportScreen(
                                onBackClick = null,
                                onPlatformSelected = { provider ->
                                    navController.navigate("music_import_selection/$provider")
                                },
                                onAuthRequested = { provider ->
                                    navController.navigate("music_import_auth/$provider")
                                },
                                onLoginClick = { navController.navigate("login") }
                            )
                        }
                        composable("music_import_auth/{provider}") { backStackEntry ->
                            val provider = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("provider") } }.getOrNull()
                            } ?: ""
                            val platform = com.alananasss.kittytune.data.musicimport.MusicApi.fromProviderName(provider)
                            if (platform != null) {
                                com.alananasss.kittytune.ui.musicimport.MusicApiAuthScreen(
                                    platform = platform,
                                    onAuthSuccess = { successProvider ->
                                        navController.navigate("music_import_selection/$successProvider") {
                                            popUpTo("music_import_auth/$provider") { inclusive = true }
                                        }
                                    },
                                    onBackClick = null
                                )
                            }
                        }
                        composable("music_import_selection/{provider}") { backStackEntry ->
                            val provider = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("provider") } }.getOrNull()
                            } ?: ""
                            com.alananasss.kittytune.ui.musicimport.MusicImportSelectionScreen(
                                platformProviderName = provider,
                                onBackClick = null,
                                onStartTransfer = {
                                    navController.navigate("music_import_transfer") {
                                        popUpTo("music_import_selection/$provider") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("music_import_transfer") {
                            com.alananasss.kittytune.ui.musicimport.MusicImportTransferScreen(
                                onBackClick = null,
                                onDone = {
                                    com.alananasss.kittytune.data.DownloadManager.notifyLibraryUpdated()
                                    navController.popBackStack("music_import", inclusive = false)
                                }
                            )
                        }
                        composable("upload") {
                            val uploadViewModel = remember { com.alananasss.kittytune.ui.upload.UploadViewModel() }
                            com.alananasss.kittytune.ui.upload.UploadScreen(
                                viewModel = uploadViewModel,
                                trackIdToEdit = null,
                                onBackClick = { navController.popBackStack() },
                                onNavigateToProfile = {
                                    val selfId = playerViewModel.currentUserId
                                    com.alananasss.kittytune.ui.profile.ProfileViewModel.triggerRefresh(selfId)
                                    libraryViewModel.loadData(forceRefresh = true)
                                    if (selfId > 0L) {
                                        navController.navigate("profile/$selfId") {
                                            popUpTo("home") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                },
                                onLoginClick = { navController.navigate("profile") }
                            )
                        }
                        composable("edit_track/{trackId}") { backStackEntry ->
                            val trackId = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("trackId") } }.getOrNull()
                            }
                            val uploadViewModel = remember { com.alananasss.kittytune.ui.upload.UploadViewModel() }
                            com.alananasss.kittytune.ui.upload.UploadScreen(
                                viewModel = uploadViewModel,
                                trackIdToEdit = trackId,
                                onBackClick = { navController.popBackStack() },
                                onNavigateToProfile = {
                                    val selfId = playerViewModel.currentUserId
                                    com.alananasss.kittytune.ui.profile.ProfileViewModel.triggerRefresh(selfId)
                                    libraryViewModel.loadData(forceRefresh = true)
                                    if (selfId > 0L) {
                                        navController.navigate("profile/$selfId") {
                                            popUpTo("home") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                },
                                onLoginClick = { navController.navigate("profile") }
                            )
                        }
                    }
                }
                }
            }
            } // end if (!isLibraryFullScreen)

            if (showNowPlayingPanel && playerViewModel.currentTrack != null) {
                var draggingRightPanel by remember { mutableStateOf(false) }
                val targetRightPanelWidth = playerViewModel.rightPanelWidth
                val animatedRightPanelWidth by androidx.compose.animation.core.animateDpAsState(
                    targetValue = targetRightPanelWidth.dp,
                    animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                    label = "rightPanelWidth"
                )
                val rightPanelWidth = if (draggingRightPanel) targetRightPanelWidth.dp else animatedRightPanelWidth

                // Resize handle: drag to resize the right panel (NowPlayingPanel / TrackInfoTab)
                val rightHandleInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val rightHandleHovered by rightHandleInteraction.collectIsHoveredAsState()
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(PANEL_GUTTER.dp)
                        .hoverable(rightHandleInteraction)
                        .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor(java.awt.Cursor.W_RESIZE_CURSOR)))
                        .draggable(
                            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                            state = androidx.compose.foundation.gestures.rememberDraggableState { deltaPx ->
                                playerViewModel.rightPanelDragBy(with(density) { deltaPx.toDp().value })
                            },
                            onDragStarted = {
                                draggingRightPanel = true
                                playerViewModel.rightPanelDragStart()
                            },
                            onDragStopped = {
                                draggingRightPanel = false
                                playerViewModel.rightPanelDragEnd()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (rightHandleHovered || draggingRightPanel) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (draggingRightPanel) 0.5f else 0.25f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }

                NowPlayingPanel(
                    playerViewModel = playerViewModel,
                    tab = nowPlayingTab,
                    onTabChange = { nowPlayingTab = it },
                    onClose = { showNowPlayingPanel = false },
                    onOpenFullLyrics = { playerViewModel.showLyricsSheet = true },
                    modifier = Modifier.width(rightPanelWidth)
                )
            }
        }

        PlayerBar(
            playerViewModel = playerViewModel,
            onToggleNowPlaying = { showNowPlayingPanel = !showNowPlayingPanel },
            onOpenQueue = {
                showNowPlayingPanel = true
                nowPlayingTab = NowPlayingTab.QUEUE
            },
            onOpenLyrics = {
                playerViewModel.showLyricsSheet = !playerViewModel.showLyricsSheet
            },
            onOpenTrackInfo = {
                showNowPlayingPanel = true
                nowPlayingTab = NowPlayingTab.TRACK
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PANEL_GUTTER.dp)
        )
    }

    TrackOptionsOverlays(playerViewModel)

    CoverViewerOverlay()

    if (showShortcutsDialog) {
        KeyboardShortcutsDialog(onDismiss = { showShortcutsDialog = false })
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.alananasss.kittytune.data.UpdateManager.checkOnStartup()
    }

    val updateStatus by com.alananasss.kittytune.data.UpdateManager.status.collectAsState()
    val isDialogVisible by com.alananasss.kittytune.data.UpdateManager.isDialogVisible.collectAsState()
    val downloadProgress by com.alananasss.kittytune.data.UpdateManager.downloadProgress.collectAsState()
    val downloadSize by com.alananasss.kittytune.data.UpdateManager.downloadSize.collectAsState()
    val releaseInfo = com.alananasss.kittytune.data.UpdateManager.releaseInfo

    if (isDialogVisible) {
        UpdateDialog(
            release = releaseInfo,
            status = updateStatus,
            progress = downloadProgress,
            totalSize = downloadSize,
            onDismiss = { com.alananasss.kittytune.data.UpdateManager.hideDialog() }
        )
    }
}

enum class NowPlayingTab { TRACK, QUEUE, LYRICS, EFFECTS }

@Composable
fun PlaceholderScreen(name: String) {
    Box(Modifier.fillMaxSize()) {
        androidx.compose.material3.Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp)
        )
    }
}

private fun isSameRoute(navController: androidx.navigation.NavController, targetRoute: String): Boolean {
    val currentEntry = navController.currentBackStackEntry ?: return false
    val pattern = currentEntry.destination.route ?: return false

    return when {
        pattern == "playlist_detail/{playlistId}" -> {
            val rawId = currentEntry.arguments?.let { args ->
                runCatching { args.read { getString("playlistId") } }.getOrNull()
            } ?: ""
            val currentPlaylistId = java.net.URLDecoder.decode(rawId, "UTF-8")
            val targetPlaylistId = if (targetRoute.startsWith("playlist_detail/")) {
                java.net.URLDecoder.decode(targetRoute.removePrefix("playlist_detail/"), "UTF-8")
            } else {
                targetRoute
            }
            currentPlaylistId == targetPlaylistId
        }
        pattern == "profile/{userId}" -> {
            val currentId = currentEntry.arguments?.let { args ->
                runCatching { args.read { getString("userId") } }.getOrNull()
            } ?: ""
            val targetId = targetRoute.removePrefix("profile:").removePrefix("profile/")
            currentId == targetId
        }
        pattern == "followers/{userId}" -> {
            val currentId = currentEntry.arguments?.let { args ->
                runCatching { args.read { getString("userId") } }.getOrNull()
            } ?: ""
            val targetId = targetRoute.removePrefix("followers/")
            currentId == targetId
        }
        pattern == "followings/{userId}" -> {
            val currentId = currentEntry.arguments?.let { args ->
                runCatching { args.read { getString("userId") } }.getOrNull()
            } ?: ""
            val targetId = targetRoute.removePrefix("followings/")
            currentId == targetId
        }
        pattern == "tag/{tagName}" -> {
            val currentTag = currentEntry.arguments?.let { args ->
                runCatching { args.read { getString("tagName") } }.getOrNull()
            } ?: ""
            val targetTag = targetRoute.removePrefix("tag:").removePrefix("tag/")
            currentTag == targetTag
        }
        pattern.startsWith("track_detail/{trackId}") || pattern.startsWith("track_detail") -> {
            val currentTrackId = currentEntry.arguments?.let { args ->
                runCatching { args.read { getString("trackId") } }.getOrNull()
            } ?: ""
            val targetTrackId = targetRoute.removePrefix("track_detail:").removePrefix("track_detail/").substringBefore("?")
            currentTrackId == targetTrackId
        }
        else -> pattern == targetRoute
    }
}

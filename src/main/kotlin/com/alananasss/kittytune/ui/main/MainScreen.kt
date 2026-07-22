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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
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
    var showMainLyricsView by remember { mutableStateOf(false) }

    // Close full-screen lyrics when navigation happens (e.g. sidebar click)
    val backStackEntry by navController.currentBackStackEntryAsState()
    androidx.compose.runtime.LaunchedEffect(backStackEntry) {
        showMainLyricsView = false
    }

    // Same navigation protocol as the Android MainScreen: PlayerViewModel exposes
    // destination ids ("likes", "profile:<id>", numeric playlist ids, ...) that we
    // translate into NavHost routes.
    androidx.compose.runtime.LaunchedEffect(playerViewModel.navigateToPlaylistId) {
        playerViewModel.navigateToPlaylistId?.let { destinationId ->
            playerViewModel.isPlayerExpanded = false
            showMainLyricsView = false
            when {
                destinationId == "expanded_queue" -> {
                    showNowPlayingPanel = true
                    nowPlayingTab = NowPlayingTab.QUEUE
                }
                destinationId == "recognition" -> navController.navigate("recognition")
                destinationId == "recognition_history" -> navController.navigate("recognition_history")
                destinationId.startsWith("profile:") -> navController.navigate("profile/${destinationId.removePrefix("profile:")}")
                destinationId.startsWith("tag:") -> navController.navigate("tag/${destinationId.removePrefix("tag:")}")
                destinationId.startsWith("track_detail:") -> navController.navigate("track_detail/${destinationId.removePrefix("track_detail:")}")
                destinationId.startsWith("playlist_fans/") -> navController.navigate(destinationId)
                else -> navController.navigate("playlist_detail/${java.net.URLEncoder.encode(destinationId, "UTF-8")}")
            }
            playerViewModel.onNavigationHandled()
        }
    }

    var showShortcutsDialog by remember { mutableStateOf(false) }

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
                        Key.H -> navController.navigate("playlist_detail/history")
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

                if (isShift && noModifiers.not()) {
                    // Only shift pressed
                    if (isShift && !isCtrl && !isAlt && !isMeta) {
                        when (event.key) {
                            Key.DirectionRight -> playerViewModel.playNext()
                            Key.DirectionLeft -> playerViewModel.smartPrevious()
                            Key.L -> playerViewModel.toggleRepeatMode()
                            Key.S -> playerViewModel.toggleShuffle()
                            Key.DirectionDown -> com.alananasss.kittytune.data.MusicManager.setVolume((com.alananasss.kittytune.data.MusicManager.getVolume() - 0.1f).coerceIn(0f, 1f))
                            Key.DirectionUp -> com.alananasss.kittytune.data.MusicManager.setVolume((com.alananasss.kittytune.data.MusicManager.getVolume() + 0.1f).coerceIn(0f, 1f))
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
                        Key.M -> com.alananasss.kittytune.data.MusicManager.setVolume(if (com.alananasss.kittytune.data.MusicManager.getVolume() > 0f) 0f else 1f)
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
                        Key.Zero -> playerViewModel.seekTo(0L)
                        Key.One -> playerViewModel.seekTo((playerViewModel.duration * 0.1).toLong())
                        Key.Two -> playerViewModel.seekTo((playerViewModel.duration * 0.2).toLong())
                        Key.Three -> playerViewModel.seekTo((playerViewModel.duration * 0.3).toLong())
                        Key.Four -> playerViewModel.seekTo((playerViewModel.duration * 0.4).toLong())
                        Key.Five -> playerViewModel.seekTo((playerViewModel.duration * 0.5).toLong())
                        Key.Six -> playerViewModel.seekTo((playerViewModel.duration * 0.6).toLong())
                        Key.Seven -> playerViewModel.seekTo((playerViewModel.duration * 0.7).toLong())
                        Key.Eight -> playerViewModel.seekTo((playerViewModel.duration * 0.8).toLong())
                        Key.Nine -> playerViewModel.seekTo((playerViewModel.duration * 0.9).toLong())
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
                if (showMainLyricsView) {
                    com.alananasss.kittytune.ui.player.lyrics.LyricsScreen(
                        viewModel = playerViewModel,
                        onClose = { showMainLyricsView = false }
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
                                            id.startsWith("profile:") -> navController.navigate("profile/${id.removePrefix("profile:")}")
                                            id.startsWith("followers:") -> navController.navigate("followers/${id.removePrefix("followers:")}")
                                            id.startsWith("followings:") -> navController.navigate("followings/${id.removePrefix("followings:")}")
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
                                onBackClick = { navController.popBackStack() },
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
                        composable("drm_explanation") {
                            com.alananasss.kittytune.ui.profile.DrmExplanationScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable("lyrics_settings") {
                            com.alananasss.kittytune.ui.profile.LyricsSettingsScreen(
                                onBackClick = { navController.popBackStack() },
                                playerViewModel = playerViewModel
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
                                        id.startsWith("profile:") -> navController.navigate("profile/${id.removePrefix("profile:")}")
                                        id.startsWith("followers:") -> navController.navigate("followers/${id.removePrefix("followers:")}")
                                        id.startsWith("followings:") -> navController.navigate("followings/${id.removePrefix("followings:")}")
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
                            val trackId = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("trackId") } }.getOrNull()
                            }?.toLongOrNull() ?: 0L
                            val tabIndex = backStackEntry.arguments?.let { args ->
                                runCatching { args.read { getString("tabIndex") } }.getOrNull()
                            }?.toIntOrNull() ?: 0
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
                                onBackClick = { navController.popBackStack() },
                                onNavigate = { dest -> navController.navigate(dest) },
                                playerViewModel = playerViewModel
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
                    onOpenFullLyrics = { showMainLyricsView = true },
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
                showMainLyricsView = !showMainLyricsView
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

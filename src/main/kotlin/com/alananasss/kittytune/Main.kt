@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.alananasss.kittytune

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.theme.withUiScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Tray
import coil3.compose.setSingletonImageLoaderFactory
import com.alananasss.kittytune.core.DesktopBackDispatcher
import com.alananasss.kittytune.core.MAC_STATUS_MENU_ANCHOR
import com.alananasss.kittytune.core.MacStatusMenu
import com.alananasss.kittytune.core.MacStatusMenuItem
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.ui.ImageLoaderFactory
import com.alananasss.kittytune.ui.main.MainScreen
import com.alananasss.kittytune.ui.setup.SetupScreen
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.ui.player.PlayerViewModel

import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed

enum class AppState { ACCOUNT, SETUP, MAIN }

internal fun initialAppState(hasCompletedSetup: Boolean, hasSession: Boolean): AppState = when {
    !hasCompletedSetup -> AppState.SETUP
    hasSession -> AppState.MAIN
    else -> AppState.ACCOUNT
}

@Composable
fun AppRouter(playerViewModel: PlayerViewModel? = null) {
    com.alananasss.kittytune.utils.StartupTimeline.mark("router: first composition")
    val tokenManager = remember { TokenManager }
    val isLoggedIn = !tokenManager.getAccessToken().isNullOrEmpty()
    val isGuestMode = tokenManager.isGuestMode()

    // decided at launch: someone finishing the setup right now is a fresh install, not an updater
    val setupCompletedAtLaunch = remember { PlayerPreferences().getHasCompletedSetup() && (isLoggedIn || isGuestMode) }
    var appState by remember {
        val hasCompletedSetup = PlayerPreferences().getHasCompletedSetup()
        val initialState = initialAppState(hasCompletedSetup, isLoggedIn || isGuestMode)
        mutableStateOf(initialState)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        TokenManager.logoutFlow.collect {
            playerViewModel?.resetAccountProfile()
            com.alananasss.kittytune.ui.setup.SetupLauncher.requested = false
            com.alananasss.kittytune.ui.setup.AccountLauncher.requested = false
            appState = initialAppState(PlayerPreferences().getHasCompletedSetup(), false)
        }
    }

    // Whoever is signed in may have changed on the way out of the account screen, so the profile is read again.
    val accountChosen = {
        playerViewModel?.resetAccountProfile()
        com.alananasss.kittytune.ui.setup.AccountLauncher.requested = false
        appState = AppState.MAIN
    }
    // "Switch account" from inside the app: the same screen, but it can be closed and nobody is signed out.
    if (com.alananasss.kittytune.ui.setup.AccountLauncher.requested && appState == AppState.MAIN) {
        com.alananasss.kittytune.ui.setup.AccountScreen(
            onDone = accountChosen,
            onCancel = { com.alananasss.kittytune.ui.setup.AccountLauncher.requested = false }
        )
        return
    }

    if (com.alananasss.kittytune.ui.setup.SetupLauncher.requested) {
        SetupScreen(
            onSetupComplete = { com.alananasss.kittytune.ui.setup.SetupLauncher.requested = false },
            onCancel = { com.alananasss.kittytune.ui.setup.SetupLauncher.requested = false }
        )
        return
    }

    when (appState) {
        // Setup is shown once. Signing out, or a guest asking to sign in, only asks for the account again.
        AppState.ACCOUNT -> com.alananasss.kittytune.ui.setup.AccountScreen(onDone = accountChosen)
        AppState.SETUP -> SetupScreen(
            onSetupComplete = { appState = AppState.MAIN }
        )
        AppState.MAIN -> {
            com.alananasss.kittytune.utils.StartupTimeline.mark("router: composing main screen")
            if (playerViewModel != null) MainScreen(playerViewModel = playerViewModel) else MainScreen()
            // One-time "new: kitty pet" announcement for people who updated (not fresh installs).
            com.alananasss.kittytune.ui.pet.PetAnnouncementHost(setupCompletedAtLaunch = setupCompletedAtLaunch, playerViewModel = playerViewModel)
        }
    }
}

/** How long the main window stays built after being closed to the tray, so a quick reopen is instant. */
private const val DROP_HIDDEN_WINDOW_AFTER_MS = 20_000L

@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
fun main(args: Array<String>) {
    com.alananasss.kittytune.utils.StartupTimeline.mark("main() entered")
    // Before anything heavy loads: a second launch only has to wake the first one up.
    // Arguments are files the OS asked us to open ("Open with", a double-click on an associated file).
    if (!com.alananasss.kittytune.core.SingleInstance.acquire(args.toList())) return
    // Two costs that do not have to be on the path to the first frame, because neither is on the AWT
    // thread: Skiko's native library (13 MB to map and relocate, needed by the first window and by
    // nothing before it) and the Swing look and feel (Swing's own dialogs, which only exist once
    // somebody opens one). Both are started here and finish long before either is first used.
    Thread({
        runCatching { org.jetbrains.skiko.Library.load() }
        com.alananasss.kittytune.utils.StartupTimeline.mark("skiko native library loaded")
        runCatching { javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName()) }
        com.alananasss.kittytune.utils.StartupTimeline.mark("swing look and feel applied")
    }, "startup-preload").apply { isDaemon = true; start() }
    com.alananasss.kittytune.utils.StartupTimeline.mark("single instance (lock + listener)")
    com.alananasss.kittytune.audio.LinuxAudioIntegration.initialize()
    com.alananasss.kittytune.audio.ai.OnnxHelper.disableNativeTelemetry()
    com.alananasss.kittytune.core.MacWindowIntegration.configureApplication()
    com.alananasss.kittytune.core.LinuxAppIdHelper.apply()
    System.setProperty("sun.java2d.wm.className", com.alananasss.kittytune.core.LinuxAppIdHelper.APP_ID)
    runCatching {
        androidx.compose.ui.platform.registerSkikoComposeImplementation()
    }
    com.alananasss.kittytune.data.MacSoundCloudOAuth.installUriHandler()
    com.alananasss.kittytune.utils.StartupTimeline.mark("platform init")
    AppBootstrap.init()
    com.alananasss.kittytune.utils.StartupTimeline.mark("AppBootstrap.init")
    ProtectedTracksDemo.prepare()
    com.alananasss.kittytune.utils.StartupTimeline.mark("ProtectedTracksDemo.prepare")
    // Transparent windows (pet, mini player, menus) in software on Windows: Direct3D hung on their first frame
    // on some GPU drivers and froze the whole app.
    com.alananasss.kittytune.core.TransparentWindowRendering.install()
    // Opaque floating windows when RTSS is running or a floating window froze the app last time.
    com.alananasss.kittytune.core.FloatingWindowCompat.init()
    com.alananasss.kittytune.utils.StartupTimeline.mark("transparent window compat")

    // ThemeState's snapshot states must exist before the first composition. Otherwise PlayerViewModel can
    // create them from a background thread after the window's snapshot was taken, and that composition
    // fails with "Reading a state that was created after the snapshot was taken".
    com.alananasss.kittytune.ui.theme.ThemeState.previewKeyColor
    com.alananasss.kittytune.utils.StartupTimeline.mark("entering application{}")

    application {
        val playerViewModel = remember { PlayerViewModel(AppInstance.application) }
        com.alananasss.kittytune.utils.StartupTimeline.markOnce("PlayerViewModel created")
        val prefsForTray = remember { PlayerPreferences() }
        val stopOnTaskClear by prefsForTray.stopOnTaskClearFlow().collectAsState(initial = prefsForTray.getStopOnTaskClear())

        // Alternate icon switcher (issue #27): the selected variant drives both the
        // window/taskbar icon and the tray icon, live.
        val appIconVariant by prefsForTray.appIconVariantFlow().collectAsState(initial = prefsForTray.getAppIconVariant())
        val appIcon = androidx.compose.runtime.remember(appIconVariant) {
            com.alananasss.kittytune.core.AppIconRuntime.loadAppPainter(appIconVariant)
        }
        val trayIcon = androidx.compose.runtime.remember(appIconVariant) {
            com.alananasss.kittytune.core.AppIconRuntime.loadTrayPainter(appIconVariant) ?: appIcon
        }
        var isWindowVisible by remember { mutableStateOf(true) }

        // Whether the main window exists at all. Closed to the tray it used to be merely hidden, keeping its
        // whole interface, its GPU surfaces and every screen's state alive: measured, the app held the same
        // half a gigabyte in the tray as with the window open (issue #66). Playback, the mini player and the
        // tray menu live outside the window, so once it has been away for a while it is dropped and built
        // again when it is opened. Its size and position are hoisted below and survive that.
        var isWindowComposed by remember { mutableStateOf(true) }
        LaunchedEffect(isWindowVisible) {
            if (isWindowVisible) {
                com.alananasss.kittytune.core.restoreMemorySizing()
                isWindowComposed = true
            } else {
                kotlinx.coroutines.delay(DROP_HIDDEN_WINDOW_AFTER_MS)
                isWindowComposed = false
                com.alananasss.kittytune.core.releaseMemoryNow()
            }
        }

        val osName = remember { System.getProperty("os.name").lowercase() }
        val isLinux = remember { osName.contains("linux") || osName.contains("nix") }
        val isWindowsHost = remember { osName.contains("win") }
        val isMacHost = remember { osName.contains("mac") }

        fun showMainWindow() {
            isWindowVisible = true
            isWindowComposed = true
            val win = java.awt.Window.getWindows().firstOrNull { it is androidx.compose.ui.awt.ComposeWindow }
                ?: java.awt.Window.getWindows().firstOrNull { it.isVisible && it.name != MAC_STATUS_MENU_ANCHOR }
            win?.let { w ->
                runCatching {
                    w.isVisible = true
                    if (w is java.awt.Frame && (w.extendedState and java.awt.Frame.ICONIFIED) != 0) {
                        w.extendedState = w.extendedState and java.awt.Frame.ICONIFIED.inv()
                    }
                    w.toFront()
                    w.requestFocus()
                    if (isWindowsHost) {
                        val hwnd = com.alananasss.kittytune.data.theme.WindowsFullScreen.handleOf(w)
                        if (hwnd != null) {
                            com.sun.jna.platform.win32.User32.INSTANCE.ShowWindow(hwnd, com.sun.jna.platform.win32.WinUser.SW_RESTORE)
                            com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(hwnd)
                            com.sun.jna.platform.win32.User32.INSTANCE.BringWindowToTop(hwnd)
                        }
                    }
                }
            }
        }

        // Zapret, once: find it if it is running, and add the domains of whichever services are blocked.
        LaunchedEffect(Unit) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val zapret = com.alananasss.kittytune.data.zapret.ZapretManager
                    if (zapret.folder == null) zapret.detect()?.let { zapret.folder = it }
                    zapret.autoConfigureOnce()
                }
            }
        }

        LaunchedEffect(Unit) {
            com.alananasss.kittytune.core.OpenFileRequests.requests.collect { files ->
                playerViewModel.playLocalFiles(files)
            }
        }

        androidx.compose.runtime.DisposableEffect(Unit) {
            com.alananasss.kittytune.core.MainWindowRaiser.handler = { showMainWindow() }
            if (com.alananasss.kittytune.core.MainWindowRaiser.pendingShow) {
                com.alananasss.kittytune.core.MainWindowRaiser.pendingShow = false
                showMainWindow()
            }
            onDispose { com.alananasss.kittytune.core.MainWindowRaiser.handler = null }
        }

        var useSniTray by remember { mutableStateOf(isLinux) }

        androidx.compose.runtime.DisposableEffect(appIconVariant) {
            com.alananasss.kittytune.utils.StartupTimeline.markOnce("icon installer: start")
            if (isMacHost) com.alananasss.kittytune.core.AppIconRuntime.applyMacDockIcon(appIconVariant)
            com.alananasss.kittytune.core.AppIconInstaller.apply(appIconVariant)
            com.alananasss.kittytune.utils.StartupTimeline.markOnce("icon installer: done")
            onDispose { }
        }

        if (isLinux) {
            var activeSniService by remember { mutableStateOf<com.alananasss.kittytune.core.LinuxStatusNotifierService?>(null) }

            androidx.compose.runtime.DisposableEffect(appIconVariant) {
                val iconBaseName = if (appIconVariant == "og") "kittytune" else "kittytune-$appIconVariant"
                val service = com.alananasss.kittytune.core.LinuxStatusNotifierService(
                    iconName = iconBaseName,
                    isMiniPlayerVisible = { playerViewModel.isMiniPlayerVisible },
                    nowPlaying = {
                        val track = playerViewModel.currentTrack ?: return@LinuxStatusNotifierService null
                        val artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
                        com.alananasss.kittytune.ui.tray.TrayNowPlaying(
                            title = track.title,
                            artist = artist,
                            artworkUrl = track.artworkUrl,
                            isPlaying = playerViewModel.isPlaying
                        )
                    },
                    onPlayPause = { playerViewModel.togglePlayPause() },
                    onNext = { playerViewModel.playNext() },
                    onPrevious = { playerViewModel.smartPrevious() },
                    onActivate = { showMainWindow() },
                    onToggleMiniPlayer = { playerViewModel.toggleMiniPlayer() },
                    onExit = {
                        com.alananasss.kittytune.core.AppInstance.isShuttingDown = true
                        exitApplication()
                    },
                    onContextMenu = { x, y ->
                        com.alananasss.kittytune.ui.tray.TrayMenuState.show(x, y)
                    }
                )
                val started = runCatching { service.start() }.getOrDefault(false)
                useSniTray = started
                if (started) activeSniService = service

                onDispose {
                    activeSniService = null
                    service.close()
                }
            }

            androidx.compose.runtime.LaunchedEffect(playerViewModel.currentTrack, playerViewModel.isPlaying, playerViewModel.isMiniPlayerVisible, activeSniService) {
                activeSniService?.notifyMenuUpdated()
            }
        }

        val trayMenuScope = androidx.compose.runtime.rememberCoroutineScope()

        fun openSettings() {
            showMainWindow()
            playerViewModel.navigateToPlaylistId = "settings"
        }

        var showMacAbout by remember { mutableStateOf(false) }
        if (isMacHost) {
            androidx.compose.runtime.DisposableEffect(Unit) {
                val desktop = java.awt.Desktop.getDesktop()
                val preferences = desktop.isSupported(java.awt.Desktop.Action.APP_PREFERENCES)
                val about = desktop.isSupported(java.awt.Desktop.Action.APP_ABOUT)
                if (preferences) desktop.setPreferencesHandler { openSettings() }
                if (about) desktop.setAboutHandler {
                    showMainWindow()
                    showMacAbout = true
                }
                onDispose {
                    if (preferences) desktop.setPreferencesHandler(null)
                    if (about) desktop.setAboutHandler(null)
                }
            }
            val statusIcon = remember(appIconVariant) {
                com.alananasss.kittytune.core.AppIconRuntime.loadMacStatusIcon(appIconVariant)
            }
            if (statusIcon != null) {
                MacStatusMenu(statusIcon, listOf(
                    MacStatusMenuItem(str("menu_show_window")) { showMainWindow() },
                    MacStatusMenuItem(str("profile_menu_settings")) { openSettings() },
                    null,
                    MacStatusMenuItem(str("player_play_pause")) { playerViewModel.togglePlayPause() },
                    MacStatusMenuItem(str("player_previous")) { playerViewModel.smartPrevious() },
                    MacStatusMenuItem(str("player_next")) { playerViewModel.playNext() },
                    null,
                    MacStatusMenuItem(
                        str(if (playerViewModel.isMiniPlayerVisible) "menu_mini_player_hide" else "menu_mini_player_show")
                    ) { playerViewModel.toggleMiniPlayer() },
                    MacStatusMenuItem(
                        str(if (com.alananasss.kittytune.ui.pet.PetWindowState.visible) "pet_desk_hide" else "pet_desk_tray_show")
                    ) { com.alananasss.kittytune.ui.pet.PetWindowState.toggle() },
                    null,
                    MacStatusMenuItem(str("menu_exit")) {
                        AppInstance.isShuttingDown = true
                        exitApplication()
                    }
                ))
            }
        } else if (trayIcon != null && !useSniTray) {
            if (isWindowsHost) {
                // Windows gets a real Win32 menu (see Win32TrayMenu): dark when the app is, rounded on
                // Windows 11, and the only kind the hidden-icons flyout stays open around.
                Tray(
                    icon = trayIcon,
                    tooltip = "KittyTune",
                    onAction = { showMainWindow() },
                )
                androidx.compose.runtime.DisposableEffect(trayIcon) {
                    com.alananasss.kittytune.ui.tray.ModernTrayMenuHook.install(trayMenuScope) { _, _ ->
                        com.alananasss.kittytune.core.Win32TrayMenu.show(
                            trayMenuEntries(
                                playerViewModel = playerViewModel,
                                onShowWindow = { showMainWindow() },
                                onExit = {
                                    com.alananasss.kittytune.core.AppInstance.isShuttingDown = true
                                    exitApplication()
                                },
                            )
                        )
                    }
                    onDispose { com.alananasss.kittytune.ui.tray.ModernTrayMenuHook.uninstall() }
                }
            } else {
                // No AWT PopupMenu (that is the XP-looking system menu) — right-click is hooked to
                // the custom Compose tray menu below, which matches KittyTune's theme.
                Tray(
                    icon = trayIcon,
                    tooltip = "KittyTune",
                    onAction = { showMainWindow() },
                )
                androidx.compose.runtime.DisposableEffect(trayIcon, useSniTray) {
                    com.alananasss.kittytune.ui.tray.ModernTrayMenuHook.install(trayMenuScope)
                    onDispose { com.alananasss.kittytune.ui.tray.ModernTrayMenuHook.uninstall() }
                }
            }
        }

        // Hoisted so the full player can ask for a real full screen rather than an overlay that covers the
        // window: the title bar, the taskbar and everything that reacts near a screen edge stay put
        // otherwise, which is what "pas un pop up mais un vrai écran" is about (issue #33).
        //
        // The placement it came from is remembered, so leaving gives a maximised window back to somebody who
        // had one and a floating window back to somebody who did not.
        val initialMetrics = remember { getScreenMetricsDp(null) }
        val initialW = minOf(1440, initialMetrics.usableBoundsDp.width)
        val initialH = minOf(900, initialMetrics.usableBoundsDp.height)
        val initialSize = remember { DpSize(initialW.dp, initialH.dp) }
        val initialPosition = remember {
            val posX = initialMetrics.usableBoundsDp.x + (initialMetrics.usableBoundsDp.width - initialW) / 2
            val posY = initialMetrics.usableBoundsDp.y + (initialMetrics.usableBoundsDp.height - initialH) / 2
            androidx.compose.ui.window.WindowPosition(posX.dp, posY.dp)
        }

        val windowState = rememberWindowState(
            size = initialSize,
            position = initialPosition,
        )
        var savedPlacement by remember { mutableStateOf(androidx.compose.ui.window.WindowPlacement.Floating) }
        var savedFloatingSize by remember { mutableStateOf(initialSize) }
        var savedFloatingPosition by remember { mutableStateOf<androidx.compose.ui.window.WindowPosition>(initialPosition) }
        var isRestoringFromFullScreen by remember { mutableStateOf(false) }

        var isAppFullScreen by remember { mutableStateOf(false) }

        // Track user's chosen placement and floating dimensions whenever not in lyrics fullscreen
        if (!com.alananasss.kittytune.core.AppWindowState.fullScreen &&
            !isRestoringFromFullScreen &&
            !isAppFullScreen &&
            !com.alananasss.kittytune.data.theme.WindowsFullScreen.isFullScreen
        ) {
            if (windowState.placement != androidx.compose.ui.window.WindowPlacement.Fullscreen) {
                savedPlacement = windowState.placement
            }
            if (windowState.placement == androidx.compose.ui.window.WindowPlacement.Floating) {
                val curW = windowState.size.width.value.toInt()
                val curH = windowState.size.height.value.toInt()
                val curX = (windowState.position as? androidx.compose.ui.window.WindowPosition.Absolute)?.x?.value?.toInt()
                val curY = (windowState.position as? androidx.compose.ui.window.WindowPosition.Absolute)?.y?.value?.toInt()
                val metrics = getScreenMetricsDp(null, curX, curY)
                val isFullScreenDimension = isFullOrMaximizedDimension(curW, curH, metrics)
                if (!isFullScreenDimension && curW >= 400 && curH >= 300) {
                    savedFloatingSize = windowState.size
                    savedFloatingPosition = windowState.position
                }
            }
        }

        // Re-assert placement changes between Compose window state and AppWindowState.fullScreen
        LaunchedEffect(com.alananasss.kittytune.core.AppWindowState.fullScreen) {
            val wanted = com.alananasss.kittytune.core.AppWindowState.fullScreen
            if (wanted && !isAppFullScreen) {
                isAppFullScreen = true
                if (windowState.placement != androidx.compose.ui.window.WindowPlacement.Fullscreen) {
                    savedPlacement = windowState.placement
                }
                if (windowState.placement == androidx.compose.ui.window.WindowPlacement.Floating) {
                    val curW = windowState.size.width.value.toInt()
                    val curH = windowState.size.height.value.toInt()
                    val curX = (windowState.position as? androidx.compose.ui.window.WindowPosition.Absolute)?.x?.value?.toInt()
                    val curY = (windowState.position as? androidx.compose.ui.window.WindowPosition.Absolute)?.y?.value?.toInt()
                    val metrics = getScreenMetricsDp(null, curX, curY)
                    if (!isFullOrMaximizedDimension(curW, curH, metrics) && curW >= 400 && curH >= 300) {
                        savedFloatingSize = windowState.size
                        savedFloatingPosition = windowState.position
                    }
                }
                if (!com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                    windowState.placement = androidx.compose.ui.window.WindowPlacement.Fullscreen
                }
            } else if (!wanted && isAppFullScreen) {
                isAppFullScreen = false
                isRestoringFromFullScreen = true
                try {
                    // On Windows the window never left Compose's placement, and WindowsFullScreen.exit restores it
                    // natively from inside the window (see below). Compose is told only after that, with what the
                    // window already is. Setting it here as well raced the native restore: Compose shrank the
                    // still borderless window to its floating size, or maximized it, before the frame came back,
                    // and the window was seen going small and then big again (issue #66).
                    if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                        kotlinx.coroutines.delay(300)
                        return@LaunchedEffect
                    }
                    val restorePlacement = savedPlacement.takeIf { it != androidx.compose.ui.window.WindowPlacement.Fullscreen }
                        ?: androidx.compose.ui.window.WindowPlacement.Floating
                    if (restorePlacement == androidx.compose.ui.window.WindowPlacement.Floating) {
                        val reqX = (savedFloatingPosition as? androidx.compose.ui.window.WindowPosition.Absolute)?.x?.value?.toInt()
                        val reqY = (savedFloatingPosition as? androidx.compose.ui.window.WindowPosition.Absolute)?.y?.value?.toInt()
                        val metrics = getScreenMetricsDp(null, reqX, reqY)
                        val clamped = clampFloatingBounds(
                            savedFloatingSize.width.value.toInt(),
                            savedFloatingSize.height.value.toInt(),
                            reqX,
                            reqY,
                            metrics.usableBoundsDp
                        )
                        windowState.placement = androidx.compose.ui.window.WindowPlacement.Floating
                        windowState.size = DpSize(clamped.width.dp, clamped.height.dp)
                        windowState.position = androidx.compose.ui.window.WindowPosition(clamped.x.dp, clamped.y.dp)
                    } else if (restorePlacement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                        // Workaround for Compose Multiplatform bug: transitioning directly from Fullscreen to
                        // Maximized fails to reset isFullscreen in ComposeWindow. Sizing through Floating clears it first.
                        windowState.placement = androidx.compose.ui.window.WindowPlacement.Floating
                        kotlinx.coroutines.delay(50)
                        windowState.placement = androidx.compose.ui.window.WindowPlacement.Maximized
                    } else {
                        windowState.placement = restorePlacement
                    }
                    kotlinx.coroutines.delay(400)
                } finally {
                    isRestoringFromFullScreen = false
                }
            }
        }

        CompositionLocalProvider(
            androidx.compose.ui.window.LocalWindowExceptionHandlerFactory provides androidx.compose.ui.window.WindowExceptionHandlerFactory { window ->
                androidx.compose.ui.window.WindowExceptionHandler { throwable ->
                    var curr: Throwable? = throwable
                    var isBenign = false
                    while (curr != null) {
                        val msg = curr.message.orEmpty()
                        if (msg.contains("RootNodeOwner is already disposed", ignoreCase = true) ||
                            (msg.contains("ComposeScene", ignoreCase = true) && msg.contains("disposed", ignoreCase = true)) ||
                            (msg.contains("Owner is already disposed", ignoreCase = true))
                        ) {
                            isBenign = true
                            break
                        }
                        curr = curr.cause
                    }
                    if (!isBenign) {
                        Thread.currentThread().uncaughtExceptionHandler?.uncaughtException(Thread.currentThread(), throwable)
                            ?: throwable.printStackTrace()
                    }
                }
            }
        ) {
        // Keys whose press was taken as a shortcut, so their release is taken too. A focused button clicks on
        // the release of Space or Enter without asking whether it saw the press: after the play button in the
        // full player had been clicked with the mouse it kept the focus, and Space then paused on the press and
        // played again on the release — the pause that "only works every other time".
        val shortcutKeysHeld = remember { mutableSetOf<Key>() }
        if (isWindowComposed) Window(
            visible = isWindowVisible,
            onCloseRequest = {
                    if (stopOnTaskClear) {
                        com.alananasss.kittytune.core.AppInstance.isShuttingDown = true
                        exitApplication()
                    } else {
                        isWindowVisible = false
                    }
                },
                title = "KittyTune",
                icon = appIcon,
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    DesktopBackDispatcher.onBack()
                    true
                } else if (event.type == KeyEventType.KeyDown && !com.alananasss.kittytune.core.TextInputTracker.isFocused()) {
                    val isShift = event.isShiftPressed
                    val isCtrl = event.isCtrlPressed
                    val isAlt = event.isAltPressed
                    val isMeta = event.isMetaPressed
                    val noModifiers = !isShift && !isCtrl && !isAlt && !isMeta
                    val char = event.utf16CodePoint.toChar()

                    if (isCtrl) {
                        when (event.key) {
                            Key.Equals, Key.NumPadAdd -> {
                                val prefs = PlayerPreferences()
                                val newScale = (prefs.getUiScale() + 0.1f).coerceAtMost(1.3f)
                                prefs.setUiScale(newScale)
                                return@Window true
                            }
                            Key.Minus, Key.NumPadSubtract -> {
                                val prefs = PlayerPreferences()
                                val newScale = (prefs.getUiScale() - 0.1f).coerceAtLeast(0.7f)
                                prefs.setUiScale(newScale)
                                return@Window true
                            }
                            else -> if (char == '0' || char == 'à') {
                                PlayerPreferences().setUiScale(1.0f)
                                return@Window true
                            }
                        }
                    }
                    val isNumberKey = when (event.key) {
                        Key.Zero, Key.One, Key.Two, Key.Three, Key.Four,
                        Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
                        Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4,
                        Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9 -> true
                        else -> char in '0'..'9' || char in listOf('à', '&', 'é', '"', '\'', '(', '-', 'è', '_', 'ç')
                    }

                    val isShortcutKey = when {
                        !isCtrl && !isAlt && !isMeta -> when {
                            isNumberKey -> true
                            noModifiers -> when (event.key) {
                                Key.Spacebar, Key.M, Key.L, Key.R, Key.S, Key.P, Key.H, Key.Q,
                                Key.DirectionLeft, Key.DirectionRight, Key.G -> true
                                else -> false
                            }
                            isShift -> when (event.key) {
                                Key.DirectionRight, Key.DirectionLeft, Key.DirectionUp, Key.DirectionDown, Key.L, Key.S -> true
                                else -> false
                            }
                            else -> false
                        }
                        else -> false
                    }

                    if (isShortcutKey) {
                        com.alananasss.kittytune.core.GlobalShortcutDispatcher.dispatch(event)
                        shortcutKeysHeld += event.key
                        true
                    } else {
                        false
                    }
                } else if (event.type == KeyEventType.KeyUp && shortcutKeysHeld.remove(event.key)) {
                    true
                } else {
                    false
                }
            },
            onKeyEvent = { event ->
                if (!com.alananasss.kittytune.core.TextInputTracker.isFocused()) {
                    com.alananasss.kittytune.core.GlobalShortcutDispatcher.dispatch(event)
                } else false
            },
        ) {
            com.alananasss.kittytune.utils.StartupTimeline.markOnce("window content composing")
            // The geometry this window is about to have, read here (in the composition) so the effect
            // below can hand it to AWT before the window is shown.
            val windowDensity = LocalDensity.current
            LaunchedEffect(Unit) {
                // Show the window as soon as the composition is applied instead of waiting for the
                // toolkit's own coroutine, which the frames this window keeps producing leave queued
                // (measured: first frame at 1.8 s, window on screen at 2.9 s, event thread composing
                // the whole time). Size and position are applied first: Compose sets them itself, but
                // after showing, so the window was seen opening small and growing into place. Every
                // placement other than Floating — maximised, full screen — keeps the toolkit's own
                // sequence, which is the one that knows how to ask the window manager for it.
                if (!window.isVisible && isWindowVisible &&
                    windowState.placement == androidx.compose.ui.window.WindowPlacement.Floating
                ) {
                    val targetSize = windowState.size
                    val targetWidth = with(windowDensity) { targetSize.width.roundToPx() }
                    val targetHeight = with(windowDensity) { targetSize.height.roundToPx() }
                    if (targetWidth > 0 && targetHeight > 0) {
                        val targetPosition = windowState.position
                        if (targetPosition is androidx.compose.ui.window.WindowPosition.Absolute) {
                            window.setBounds(
                                with(windowDensity) { targetPosition.x.roundToPx() },
                                with(windowDensity) { targetPosition.y.roundToPx() },
                                targetWidth,
                                targetHeight,
                            )
                        } else {
                            window.setSize(targetWidth, targetHeight)
                        }
                    }
                    window.isVisible = true
                    com.alananasss.kittytune.utils.StartupTimeline.markOnce("window shown on first composition")
                }
                androidx.compose.runtime.withFrameNanos {
                    com.alananasss.kittytune.utils.StartupTimeline.mark("first frame drawn")
                }
                com.alananasss.kittytune.utils.StartupTimeline.traceUntilWindowVisible(window)
            }
            com.alananasss.kittytune.utils.StartupTimeline.markOnce("coil: start")
            setSingletonImageLoaderFactory { ImageLoaderFactory.create() }
            com.alananasss.kittytune.utils.StartupTimeline.markOnce("coil: done")

            androidx.compose.runtime.DisposableEffect(window) {
                val removeMacListeners = com.alananasss.kittytune.core.MacWindowIntegration.install(window) {
                    // A minimized/hidden window must release its native full-screen Space as well
                    // as the lyrics request. Otherwise notch apps keep treating it as full screen.
                    if (com.alananasss.kittytune.core.AppWindowState.fullScreen ||
                        windowState.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen) {
                        com.alananasss.kittytune.core.AppWindowState.fullScreen = false
                        windowState.placement = savedPlacement.takeUnless {
                            it == androidx.compose.ui.window.WindowPlacement.Fullscreen
                        } ?: androidx.compose.ui.window.WindowPlacement.Floating
                        com.alananasss.kittytune.core.MacWindowIntegration.releaseFullScreen(window)
                    }
                }
                onDispose { removeMacListeners() }
            }

            androidx.compose.runtime.DisposableEffect(window) {
                val listener = object : java.awt.event.WindowAdapter() {
                    override fun windowGainedFocus(event: java.awt.event.WindowEvent?) {
                        playerViewModel.refreshSyncedPlaybackOnForeground()
                    }

                    override fun windowOpened(event: java.awt.event.WindowEvent?) {
                        com.alananasss.kittytune.utils.StartupTimeline.markOnce("window opened (first shown)")
                    }
                }
                window.addWindowFocusListener(listener)
                window.addWindowListener(listener)
                if (window.isVisible) {
                    com.alananasss.kittytune.utils.StartupTimeline.markOnce("window opened (first shown, already visible)")
                }
                onDispose {
                    window.removeWindowFocusListener(listener)
                    window.removeWindowListener(listener)
                }
            }

            // Set dark background immediately on the AWT window before composition to prevent white flash on Win32/DirectX resize
            runCatching {
                val darkBg = java.awt.Color(0x13, 0x13, 0x13)
                window.background = darkBg
                (window.contentPane as? javax.swing.JComponent)?.background = darkBg
                window.rootPane?.background = darkBg
            }

            // Enforce a minimum window size responsive to the screen resolution: below this the three-panel layout breaks
            // down and the app can crash (issue #27).
            val currentScreenBounds = window.graphicsConfiguration?.bounds
            window.minimumSize = java.awt.Dimension(
                minOf(960, currentScreenBounds?.width ?: 960),
                minOf(600, currentScreenBounds?.height ?: 600)
            )

            LaunchedEffect(Unit) {
                if (isWindowsHost) {
                    javax.swing.SwingUtilities.invokeLater {
                        runCatching {
                            val hwnd = com.alananasss.kittytune.data.theme.WindowsFullScreen.handleOf(window)
                            if (hwnd != null) {
                                com.sun.jna.platform.win32.User32.INSTANCE.SetForegroundWindow(hwnd)
                                com.sun.jna.platform.win32.User32.INSTANCE.BringWindowToTop(hwnd)
                            }
                        }
                    }
                }
            }

            // Leaving full screen, said to the toolkit as well as to Compose.
            //
            // "Quand je quitte le plein écran je suis encore en plein écran dans l'appli." Compose's placement
            // is a *request* to the window manager, and a window manager is free to take its time or to
            // decline — on a tiling or compositing desktop the frame can stay full screen while Compose
            // believes it is not, and then nothing will ever ask again. AWT's exclusive-full-screen handle is
            // a second lever on the same window, so this releases that too rather than guessing which of the
            // two is holding it (issue #33).
            var wasFullScreenInWindow by remember { mutableStateOf(false) }
            var wasAlwaysOnTop by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(com.alananasss.kittytune.core.AppWindowState.fullScreen) {
                val isFS = com.alananasss.kittytune.core.AppWindowState.fullScreen
                if (isFS) {
                    wasFullScreenInWindow = true
                    wasAlwaysOnTop = window.isAlwaysOnTop
                    if (!isMacHost) runCatching { window.isAlwaysOnTop = true }
                    if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                        javax.swing.SwingUtilities.invokeLater {
                            com.alananasss.kittytune.data.theme.WindowsFullScreen.enter(window)
                        }
                    }
                } else if (wasFullScreenInWindow) {
                    wasFullScreenInWindow = false
                    runCatching { window.isAlwaysOnTop = wasAlwaysOnTop }
                    val gc = window.graphicsConfiguration
                    val reqX = (savedFloatingPosition as? androidx.compose.ui.window.WindowPosition.Absolute)?.x?.value?.toInt()
                    val reqY = (savedFloatingPosition as? androidx.compose.ui.window.WindowPosition.Absolute)?.y?.value?.toInt()
                    val usable = getUsableDesktopBounds(gc, reqX, reqY)
                    val clamped = clampFloatingBounds(
                        savedFloatingSize.width.value.toInt(),
                        savedFloatingSize.height.value.toInt(),
                        reqX,
                        reqY,
                        usable
                    )
                    if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                        javax.swing.SwingUtilities.invokeLater {
                            com.alananasss.kittytune.data.theme.WindowsFullScreen.exit(window, savedPlacement, clamped)
                            // Now that the window is restored, Compose learns where it is. These match the window,
                            // so applying them changes nothing on screen.
                            if (savedPlacement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                                windowState.placement = androidx.compose.ui.window.WindowPlacement.Maximized
                            } else {
                                windowState.placement = androidx.compose.ui.window.WindowPlacement.Floating
                                windowState.size = DpSize(window.width.dp, window.height.dp)
                                windowState.position = androidx.compose.ui.window.WindowPosition(window.x.dp, window.y.dp)
                            }
                        }
                    } else {
                        runCatching {
                            if (savedPlacement != androidx.compose.ui.window.WindowPlacement.Fullscreen) {
                                val device = window.graphicsConfiguration?.device
                                if (device?.fullScreenWindow === window) device.fullScreenWindow = null
                            }
                            if (savedPlacement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                                // Reset isFullscreen in ComposeWindow by transitioning through Floating before Maximized
                                window.placement = androidx.compose.ui.window.WindowPlacement.Floating
                                window.placement = androidx.compose.ui.window.WindowPlacement.Maximized
                                window.extendedState = java.awt.Frame.MAXIMIZED_BOTH
                                window.revalidate()
                                window.repaint()
                            } else if (savedPlacement == androidx.compose.ui.window.WindowPlacement.Fullscreen) {
                                window.placement = androidx.compose.ui.window.WindowPlacement.Fullscreen
                            } else {
                                window.placement = androidx.compose.ui.window.WindowPlacement.Floating
                                window.extendedState = java.awt.Frame.NORMAL
                                javax.swing.SwingUtilities.invokeLater {
                                    runCatching {
                                        window.setBounds(clamped.x, clamped.y, clamped.width, clamped.height)
                                        window.revalidate()
                                        window.repaint()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isMacHost) {
                MenuBar {
                    Menu(str("menu_go")) {
                        Item(str("nav_home"), onClick = { playerViewModel.navigateToPlaylistId = "home" })
                        Item(
                            str("nav_search"),
                            shortcut = androidx.compose.ui.input.key.KeyShortcut(Key.F, meta = true),
                            onClick = { playerViewModel.navigateToPlaylistId = "search" }
                        )
                        Item(str("history_title"), onClick = { playerViewModel.navigateToPlaylistId = "history" })
                    }
                    Menu(str("settings_cat_playback")) {
                        val hasTrack = playerViewModel.uiCurrentTrack != null
                        Item(str("player_play_pause"), enabled = hasTrack, onClick = { playerViewModel.togglePlayPause() })
                        Item(str("player_previous"), enabled = hasTrack, onClick = { playerViewModel.smartPrevious() })
                        Item(str("player_next"), enabled = hasTrack, onClick = { playerViewModel.playNext() })
                        Separator()
                        CheckboxItem(
                            str("menu_shuffle"),
                            checked = playerViewModel.uiShuffleEnabled,
                            onCheckedChange = { playerViewModel.toggleShuffle() }
                        )
                        val repeat = playerViewModel.uiRepeatMode
                        Item(str(when (repeat.name) {
                            "ALL" -> "menu_repeat_all"
                            "ONE" -> "menu_repeat_one"
                            else -> "menu_repeat"
                        }), onClick = { playerViewModel.toggleRepeatMode() })
                        Separator()
                        CheckboxItem(
                            str("shortcut_mute"),
                            checked = playerViewModel.uiVolume <= 0.001f,
                            onCheckedChange = { playerViewModel.toggleMute() }
                        )
                    }
                    Menu(str("menu_window")) {
                        Item(str("player_queue"), onClick = { playerViewModel.navigateToPlaylistId = "expanded_queue" })
                        CheckboxItem(
                            str("player_lyrics"),
                            checked = playerViewModel.showLyricsSheet,
                            enabled = playerViewModel.uiCurrentTrack != null,
                            onCheckedChange = { playerViewModel.showLyricsSheet = it }
                        )
                        Item(
                            str(if (playerViewModel.isMiniPlayerVisible) "menu_mini_player_hide" else "menu_mini_player_show"),
                            onClick = { playerViewModel.toggleMiniPlayer() }
                        )
                        Separator()
                        Item(str("btn_fullscreen"), onClick = {
                            val state = com.alananasss.kittytune.core.AppWindowState
                            state.fullScreen = !state.fullScreen
                        })
                    }
                }
            }

            // Apply the multi-size icons once the native window exists.
            androidx.compose.runtime.LaunchedEffect(appIconVariant) {
                com.alananasss.kittytune.utils.StartupTimeline.markOnce("window icon: start")
                com.alananasss.kittytune.core.AppIconRuntime.apply(appIconVariant, window)
                com.alananasss.kittytune.utils.StartupTimeline.markOnce("window icon: done")
            }

            val prefs = remember { PlayerPreferences() }
            val uiScale by prefs.uiScaleFlow().collectAsState(initial = prefs.getUiScale())
            val currentDensity = LocalDensity.current
            val customDensity = currentDensity.withUiScale(uiScale)

            val windowSeen = com.alananasss.kittytune.core.rememberWindowSeen(window)
            com.alananasss.kittytune.core.TrimMemoryWhileHidden(window)

            CompositionLocalProvider(
                LocalDensity provides customDensity,
                com.alananasss.kittytune.core.LocalWindowSeen provides windowSeen,
            ) {
                KittyTuneTheme {
                    if (showMacAbout) {
                        com.alananasss.kittytune.ui.profile.AboutDialog(
                            onDismiss = { showMacAbout = false },
                            onCreditsClick = { playerViewModel.navigateToPlaylistId = "credits" }
                        )
                    }
                    // Inside the theme, so the title bar and underlying window canvas track
                    // the live palette — the cover-seeded dynamic theme included — instead of
                    // a colour read once at startup (issue #33).
                    ThemedTitleBarEffect(window)
                    // The tray's native menu follows the app's light or dark palette.
                    val menuDark = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    LaunchedEffect(menuDark) { com.alananasss.kittytune.core.Win32TrayMenu.setDark(menuDark) }
                    ThemedWindowBackgroundEffect(window)
                    androidx.compose.foundation.layout.Box(propagateMinConstraints = true) {
                        Surface {
                            if (System.getProperty("kittytune.splashProbe") == "true") {
                                androidx.compose.material3.Text("KittyTune")
                            } else {
                                AppRouter(playerViewModel = playerViewModel)
                            }
                        }
                        // Closing dialogs fade out over the app, drawn above it.
                        com.alananasss.kittytune.core.DialogExitHost()
                    }
                }
            }
        } // End Window
        } // End CompositionLocalProvider

        if (playerViewModel.isMiniPlayerVisible) {
            com.alananasss.kittytune.ui.player.mini.MiniLyricsPlayerWindow(
                viewModel = playerViewModel,
                isAppFullScreen = isAppFullScreen || com.alananasss.kittytune.core.AppWindowState.fullScreen,
                onOpenMainWindow = { showMainWindow() },
            )
        }

        // The Kitty pet: the Android home-screen widget as a floating window, always on top. When it
        // is set to live inside the app instead, MainScreen draws it there and no window is created.
        if (
            com.alananasss.kittytune.ui.pet.PetWindowState.visible &&
            com.alananasss.kittytune.ui.pet.PetWindowState.placement == com.alananasss.kittytune.ui.pet.PetPlacement.DESKTOP
        ) {
            com.alananasss.kittytune.ui.pet.PetDesktopWindow(
                isAppFullScreen = isAppFullScreen || com.alananasss.kittytune.core.AppWindowState.fullScreen,
                onOpenMainWindow = { showMainWindow() },
                onOpenSettings = {
                    showMainWindow()
                    playerViewModel.navigateToPlaylistId = "pet_settings"
                },
                onPlayPause = { playerViewModel.togglePlayPause() },
            )
        }

        // Custom tray context menu — transparent, rounded, themed; lives outside the main window
        // so it can open next to the tray icon on Windows/Linux. macOS uses its native menu.
        if (!isMacHost) com.alananasss.kittytune.ui.tray.ModernTrayMenuHost(
            nowPlaying = playerViewModel.currentTrack?.let { track ->
                com.alananasss.kittytune.ui.tray.TrayNowPlaying(
                    title = track.title,
                    artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() },
                    artworkUrl = track.fullResArtwork,
                    isPlaying = playerViewModel.isPlaying,
                )
            },
            isMiniPlayerVisible = playerViewModel.isMiniPlayerVisible,
            onShowWindow = { showMainWindow() },
            onToggleMiniPlayer = { playerViewModel.toggleMiniPlayer() },
            isPetVisible = com.alananasss.kittytune.ui.pet.PetWindowState.visible,
            onTogglePet = { com.alananasss.kittytune.ui.pet.PetWindowState.toggle() },
            onPlayPause = { playerViewModel.togglePlayPause() },
            onNext = { playerViewModel.playNext() },
            onPrevious = { playerViewModel.smartPrevious() },
            onExit = {
                com.alananasss.kittytune.core.AppInstance.isShuttingDown = true
                exitApplication()
            },
        )
    } // End application
} // End main



/**
 * Keeps native Windows/macOS title bars in step with the palette while retaining system controls.
 */
@Composable
private fun ThemedTitleBarEffect(window: java.awt.Window) {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    val enabled = remember(prefsSnapshot) { PlayerPreferences().getThemedTitleBar() }

    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val caption = scheme.surfaceContainerLow
    val captionText = scheme.onSurface
    val dark = caption.luminance() < 0.5f

    androidx.compose.runtime.LaunchedEffect(enabled, caption, captionText, dark) {
        // The window is realised by the time an effect runs, but a first launch can still race the
        // native peer; one retry is enough and costs nothing when the first attempt worked.
        com.alananasss.kittytune.core.MacWindowIntegration.themeTitleBar(window, enabled)
        repeat(2) { attempt ->
            if (attempt > 0) kotlinx.coroutines.delay(400)
            if (enabled) {
                com.alananasss.kittytune.data.theme.WindowsTitleBar.apply(window, caption, captionText, dark)
            } else {
                com.alananasss.kittytune.data.theme.WindowsTitleBar.reset(window)
            }
        }
    }
}

/**
 * Sets the underlying AWT Window, root panes and child canvas components to match the dynamic theme palette.
 * Uses synchronous DisposableEffect with hierarchy and resize listeners to prevent light/gray flashes during resize.
 */
@Composable
private fun ThemedWindowBackgroundEffect(window: java.awt.Window) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val background = scheme.surfaceContainerLowest

    val r = (background.red * 255f).toInt().coerceIn(0, 255)
    val g = (background.green * 255f).toInt().coerceIn(0, 255)
    val b = (background.blue * 255f).toInt().coerceIn(0, 255)
    val awtBg = remember(r, g, b) { java.awt.Color(r, g, b) }

    androidx.compose.runtime.DisposableEffect(window, awtBg) {
        fun applyBg(comp: java.awt.Component?) {
            if (comp == null || !window.isDisplayable) return
            runCatching {
                comp.background = awtBg
                if (comp is javax.swing.JComponent) {
                    comp.isOpaque = true
                }
            }
            if (comp is java.awt.Container) {
                val children = runCatching { comp.components }.getOrNull() ?: return
                for (child in children) {
                    applyBg(child)
                }
            }
        }

        fun applyAll() {
            if (!window.isDisplayable) return
            runCatching {
                window.background = awtBg
                if (window is javax.swing.JFrame) {
                    window.rootPane?.background = awtBg
                    window.contentPane?.background = awtBg
                    window.layeredPane?.background = awtBg
                }
                applyBg(window)
            }
        }

        applyAll()

        val hierarchyListener = java.awt.event.HierarchyListener {
            applyAll()
        }
        val compListener = object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) {
                applyAll()
            }
        }

        window.addHierarchyListener(hierarchyListener)
        window.addComponentListener(compListener)

        onDispose {
            window.removeHierarchyListener(hierarchyListener)
            window.removeComponentListener(compListener)
        }
    }
}

private fun getScreenDeviceForPosition(x: Int?, y: Int?): java.awt.GraphicsDevice? {
    return runCatching {
        val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        val devices = ge.screenDevices
        if (x != null && y != null) {
            val pt = java.awt.Point(x, y)
            devices.firstOrNull { dev ->
                val b = dev.defaultConfiguration.bounds
                if (b.contains(pt)) return@firstOrNull true
                val sx = dev.defaultConfiguration.defaultTransform.scaleX.toFloat().coerceAtLeast(1f)
                val sy = dev.defaultConfiguration.defaultTransform.scaleY.toFloat().coerceAtLeast(1f)
                val scaledPt = java.awt.Point((x * sx).toInt(), (y * sy).toInt())
                b.contains(scaledPt)
            }
        } else null
    }.getOrNull()
}

internal data class ScreenMetricsDp(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val usableBoundsDp: java.awt.Rectangle,
    val scaleX: Float,
    val scaleY: Float,
)

internal fun getScreenMetricsDp(
    gc: java.awt.GraphicsConfiguration?,
    x: Int? = null,
    y: Int? = null,
): ScreenMetricsDp {
    val config = gc ?: getScreenDeviceForPosition(x, y)?.defaultConfiguration ?: runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    }.getOrNull()

    val scaleX = config?.defaultTransform?.scaleX?.toFloat()?.coerceAtLeast(1.0f) ?: 1.0f
    val scaleY = config?.defaultTransform?.scaleY?.toFloat()?.coerceAtLeast(1.0f) ?: 1.0f

    val screenBounds = config?.bounds ?: java.awt.Rectangle(0, 0, 1920, 1080)
    val insets = config?.let { java.awt.Toolkit.getDefaultToolkit().getScreenInsets(it) } ?: java.awt.Insets(0, 0, 0, 0)

    val usablePixelW = (screenBounds.width - insets.left - insets.right).coerceAtLeast(600)
    val usablePixelH = (screenBounds.height - insets.top - insets.bottom).coerceAtLeast(400)
    val usablePixelX = screenBounds.x + insets.left
    val usablePixelY = screenBounds.y + insets.top

    val screenW_Dp = (screenBounds.width / scaleX).toInt()
    val screenH_Dp = (screenBounds.height / scaleY).toInt()

    val usableW_Dp = (usablePixelW / scaleX).toInt()
    val usableH_Dp = (usablePixelH / scaleY).toInt()
    val usableX_Dp = (usablePixelX / scaleX).toInt()
    val usableY_Dp = (usablePixelY / scaleY).toInt()

    return ScreenMetricsDp(
        screenWidthDp = screenW_Dp,
        screenHeightDp = screenH_Dp,
        usableBoundsDp = java.awt.Rectangle(usableX_Dp, usableY_Dp, usableW_Dp, usableH_Dp),
        scaleX = scaleX,
        scaleY = scaleY,
    )
}

internal fun isFullOrMaximizedDimension(
    curW: Int,
    curH: Int,
    metrics: ScreenMetricsDp,
): Boolean {
    val isNearScreen = curW >= metrics.screenWidthDp - 24 && curH >= metrics.screenHeightDp - 24
    val isNearUsable = curW >= metrics.usableBoundsDp.width - 24 && curH >= metrics.usableBoundsDp.height - 24
    return isNearScreen || isNearUsable
}

private fun getScreenBoundsFor(
    gc: java.awt.GraphicsConfiguration?,
    x: Int? = null,
    y: Int? = null,
): java.awt.Rectangle {
    if (gc != null) return gc.bounds
    val dev = getScreenDeviceForPosition(x, y)
    if (dev != null) return dev.defaultConfiguration.bounds
    return runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    }.getOrNull() ?: java.awt.Rectangle(0, 0, 1920, 1080)
}

private fun getUsableDesktopBounds(
    gc: java.awt.GraphicsConfiguration?,
    x: Int? = null,
    y: Int? = null,
): java.awt.Rectangle {
    val config = gc ?: getScreenDeviceForPosition(x, y)?.defaultConfiguration ?: runCatching {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
    }.getOrNull()
    val bounds = config?.bounds ?: java.awt.Rectangle(0, 0, 1440, 900)
    val insets = config?.let { java.awt.Toolkit.getDefaultToolkit().getScreenInsets(it) } ?: java.awt.Insets(0, 0, 0, 0)
    val usableW = (bounds.width - insets.left - insets.right).coerceAtLeast(600)
    val usableH = (bounds.height - insets.top - insets.bottom).coerceAtLeast(400)
    return java.awt.Rectangle(
        bounds.x + insets.left,
        bounds.y + insets.top,
        usableW,
        usableH,
    )
}

internal fun clampFloatingBounds(
    requestedW: Int,
    requestedH: Int,
    requestedX: Int?,
    requestedY: Int?,
    usable: java.awt.Rectangle,
): java.awt.Rectangle {
    val minW = minOf(960, usable.width)
    val minH = minOf(600, usable.height)
    val w = requestedW.coerceIn(minW, usable.width)
    val h = requestedH.coerceIn(minH, usable.height)

    val usableMaxX = usable.x + usable.width
    val usableMaxY = usable.y + usable.height

    var x = requestedX ?: (usable.x + (usable.width - w) / 2)
    var y = requestedY ?: (usable.y + (usable.height - h) / 2)

    if (x + w > usableMaxX) x = usableMaxX - w
    if (x < usable.x) x = usable.x
    if (y + h > usableMaxY) y = usableMaxY - h
    if (y < usable.y) y = usable.y

    return java.awt.Rectangle(x, y, w, h)
}

/** What the tray menu offers, read from the player at the moment it opens. */
private fun trayMenuEntries(
    playerViewModel: PlayerViewModel,
    onShowWindow: () -> Unit,
    onExit: () -> Unit,
): List<com.alananasss.kittytune.core.Win32TrayMenu.Entry> {
    val entry = com.alananasss.kittytune.core.Win32TrayMenu::Entry
    val separator = com.alananasss.kittytune.core.Win32TrayMenu.Entry.Separator
    val track = playerViewModel.currentTrack
    return buildList {
        if (track != null) {
            val artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
            add(entry(listOfNotNull(track.title, artist.takeIf { it.isNotBlank() }).joinToString(" — "), null, false, false))
            add(entry(if (playerViewModel.isPlaying) str("action_pause") else str("action_play"), { playerViewModel.togglePlayPause() }, false, false))
            add(entry(str("player_next"), { playerViewModel.playNext() }, false, false))
            add(entry(str("player_previous"), { playerViewModel.smartPrevious() }, false, false))
            add(separator)
        }
        add(entry(str("menu_show_window"), onShowWindow, false, false))
        add(entry(str("menu_mini_player_show"), { playerViewModel.toggleMiniPlayer() }, playerViewModel.isMiniPlayerVisible, false))
        add(separator)
        add(entry(str("menu_exit"), onExit, false, false))
    }
}

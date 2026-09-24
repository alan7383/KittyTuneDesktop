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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.Tray
import coil3.compose.setSingletonImageLoaderFactory
import com.alananasss.kittytune.core.DesktopBackDispatcher
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.ui.ImageLoaderFactory
import com.alananasss.kittytune.ui.login.LoginScreen
import com.alananasss.kittytune.ui.login.WelcomeScreen
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

enum class AppState { WELCOME, LOGIN, SETUP, MAIN }

@Composable
fun AppRouter(playerViewModel: PlayerViewModel? = null) {
    val tokenManager = remember { TokenManager }
    val isLoggedIn = !tokenManager.getAccessToken().isNullOrEmpty()
    val isGuestMode = tokenManager.isGuestMode()

    var appState by remember {
        val hasCompletedSetup = PlayerPreferences().getHasCompletedSetup()
        val initialState = if (isLoggedIn || isGuestMode) {
            if (hasCompletedSetup) AppState.MAIN else AppState.SETUP
        } else {
            AppState.WELCOME
        }
        mutableStateOf(initialState)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        TokenManager.logoutFlow.collect {
            appState = AppState.WELCOME
        }
    }

    when (appState) {
        AppState.WELCOME -> WelcomeScreen(
            onLoginClick = { appState = AppState.LOGIN },
            onGuestClick = { 
                tokenManager.setGuestMode(true)
                appState = AppState.SETUP 
            },
            isGuestLoading = false
        )
        AppState.LOGIN -> LoginScreen(
            onLoginSuccess = { appState = AppState.SETUP },
            onBackClick = { appState = AppState.WELCOME }
        )
        AppState.SETUP -> SetupScreen(
            onSetupComplete = { appState = AppState.MAIN }
        )
        AppState.MAIN -> if (playerViewModel != null) MainScreen(playerViewModel = playerViewModel) else MainScreen()
    }
}

@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
fun main() {
    System.setProperty("sun.java2d.wm.className", "kitty-tune")
    runCatching {
        androidx.compose.ui.platform.registerSkikoComposeImplementation()
    }
    AppBootstrap.init()

    application {
        val playerViewModel = remember { PlayerViewModel(AppInstance.application) }
        val prefsForTray = remember { PlayerPreferences() }
        val stopOnTaskClear by prefsForTray.stopOnTaskClearFlow().collectAsState(initial = prefsForTray.getStopOnTaskClear())

        // Alternate icon switcher (issue #27): the selected variant drives both the
        // window/taskbar icon and the tray icon, live.
        val appIconVariant by prefsForTray.appIconVariantFlow().collectAsState(initial = prefsForTray.getAppIconVariant())
        val appIcon = androidx.compose.runtime.remember(appIconVariant) {
            runCatching {
                Thread.currentThread().contextClassLoader?.getResourceAsStream(
                    com.alananasss.kittytune.core.AppIconVariants.resourcePath(appIconVariant)
                )?.use { stream ->
                    androidx.compose.ui.graphics.painter.BitmapPainter(androidx.compose.ui.res.loadImageBitmap(stream))
                }
            }.getOrNull()
        }
        val trayIcon = androidx.compose.runtime.remember(appIconVariant) {
            com.alananasss.kittytune.core.AppIconRuntime.loadTrayPainter(appIconVariant) ?: appIcon
        }
        var isWindowVisible by remember { mutableStateOf(true) }

        fun showMainWindow() {
            isWindowVisible = true
            val win = java.awt.Window.getWindows().firstOrNull { it is androidx.compose.ui.awt.ComposeWindow }
                ?: java.awt.Window.getWindows().firstOrNull { it.isVisible }
            win?.let { w ->
                runCatching {
                    w.isVisible = true
                    if (w is java.awt.Frame && (w.extendedState and java.awt.Frame.ICONIFIED) != 0) {
                        w.extendedState = w.extendedState and java.awt.Frame.ICONIFIED.inv()
                    }
                    w.toFront()
                    w.requestFocus()
                }
            }
        }

        val osName = remember { System.getProperty("os.name").lowercase() }
        val isLinux = remember { osName.contains("linux") || osName.contains("nix") }

        var useSniTray by remember { mutableStateOf(isLinux) }

        if (isLinux) {
            androidx.compose.runtime.DisposableEffect(appIconVariant) {
                runCatching { com.alananasss.kittytune.core.AppIconInstaller.apply(appIconVariant) }
                val iconBaseName = if (appIconVariant == "og") "kittytune" else "kittytune-$appIconVariant"
                val service = com.alananasss.kittytune.core.LinuxStatusNotifierService(
                    iconName = iconBaseName,
                    isMiniPlayerVisible = { playerViewModel.isMiniPlayerVisible },
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
                val started = service.start()
                useSniTray = started

                onDispose {
                    service.close()
                }
            }
        }

        val trayMenuScope = androidx.compose.runtime.rememberCoroutineScope()

        if (trayIcon != null && !useSniTray) {
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

        // Track user's chosen placement and floating dimensions whenever not in fullscreen
        if (windowState.placement != androidx.compose.ui.window.WindowPlacement.Fullscreen &&
            !com.alananasss.kittytune.core.AppWindowState.fullScreen &&
            !isRestoringFromFullScreen &&
            !isAppFullScreen &&
            !com.alananasss.kittytune.data.theme.WindowsFullScreen.isFullScreen
        ) {
            savedPlacement = windowState.placement
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
                savedPlacement = windowState.placement
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
        Window(
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
                        true
                    } else {
                        false
                    }
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
            setSingletonImageLoaderFactory { ImageLoaderFactory.create() }

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

            // Leaving full screen, said to the toolkit as well as to Compose.
            //
            // "Quand je quitte le plein écran je suis encore en plein écran dans l'appli." Compose's placement
            // is a *request* to the window manager, and a window manager is free to take its time or to
            // decline — on a tiling or compositing desktop the frame can stay full screen while Compose
            // believes it is not, and then nothing will ever ask again. AWT's exclusive-full-screen handle is
            // a second lever on the same window, so this releases that too rather than guessing which of the
            // two is holding it (issue #33).
            var wasFullScreenInWindow by remember { mutableStateOf(false) }
            androidx.compose.runtime.LaunchedEffect(com.alananasss.kittytune.core.AppWindowState.fullScreen) {
                val isFS = com.alananasss.kittytune.core.AppWindowState.fullScreen
                if (isFS) {
                    wasFullScreenInWindow = true
                    if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                        javax.swing.SwingUtilities.invokeLater {
                            com.alananasss.kittytune.data.theme.WindowsFullScreen.enter(window)
                        }
                    }
                } else if (wasFullScreenInWindow) {
                    wasFullScreenInWindow = false
                    val gc = window.graphicsConfiguration
                    val scaleX = gc?.defaultTransform?.scaleX?.toFloat()?.coerceAtLeast(1.0f) ?: 1.0f
                    val scaleY = gc?.defaultTransform?.scaleY?.toFloat()?.coerceAtLeast(1.0f) ?: 1.0f
                    val reqX = (savedFloatingPosition as? androidx.compose.ui.window.WindowPosition.Absolute)?.x?.value?.toInt()
                    val reqY = (savedFloatingPosition as? androidx.compose.ui.window.WindowPosition.Absolute)?.y?.value?.toInt()
                    val usable = getUsableDesktopBounds(gc, reqX?.let { (it * scaleX).toInt() }, reqY?.let { (it * scaleY).toInt() })
                    val clampedPixels = clampFloatingBounds(
                        (savedFloatingSize.width.value * scaleX).toInt(),
                        (savedFloatingSize.height.value * scaleY).toInt(),
                        reqX?.let { (it * scaleX).toInt() },
                        reqY?.let { (it * scaleY).toInt() },
                        usable
                    )
                    if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                        javax.swing.SwingUtilities.invokeLater {
                            com.alananasss.kittytune.data.theme.WindowsFullScreen.exit(window, savedPlacement, clampedPixels)
                        }
                    } else {
                        runCatching {
                            val device = window.graphicsConfiguration?.device
                            if (device?.fullScreenWindow === window) device.fullScreenWindow = null
                            if (window is java.awt.Frame) {
                                if (savedPlacement == androidx.compose.ui.window.WindowPlacement.Maximized) {
                                    window.extendedState = java.awt.Frame.MAXIMIZED_BOTH
                                } else {
                                    window.extendedState = java.awt.Frame.NORMAL
                                    javax.swing.SwingUtilities.invokeLater {
                                        runCatching {
                                            window.setBounds(clampedPixels.x, clampedPixels.y, clampedPixels.width, clampedPixels.height)
                                            window.revalidate()
                                            window.repaint()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // The macOS Dock image and the multi-size window icons are outside what
            // Window(icon = …) can set, so they are applied here — from inside the window's
            // own composition, where `window` is guaranteed to exist.
            androidx.compose.runtime.LaunchedEffect(appIconVariant) {
                com.alananasss.kittytune.core.AppIconRuntime.apply(appIconVariant, window)
            }

            val prefs = remember { PlayerPreferences() }
            val uiScale by prefs.uiScaleFlow().collectAsState(initial = prefs.getUiScale())
            val currentDensity = LocalDensity.current
            val customDensity = Density(
                density = currentDensity.density * uiScale,
                fontScale = currentDensity.fontScale * uiScale
            )

            CompositionLocalProvider(LocalDensity provides customDensity) {
                KittyTuneTheme {
                    // Inside the theme, so the title bar and underlying window canvas track
                    // the live palette — the cover-seeded dynamic theme included — instead of
                    // a colour read once at startup (issue #33).
                    ThemedTitleBarEffect(window)
                    ThemedWindowBackgroundEffect(window)
                    Surface { AppRouter(playerViewModel = playerViewModel) }
                }
            }
        } // End Window
        } // End CompositionLocalProvider

        if (playerViewModel.isMiniPlayerVisible) {
            com.alananasss.kittytune.ui.player.mini.MiniLyricsPlayerWindow(viewModel = playerViewModel)
        }

        // Custom tray context menu — transparent, rounded, themed; lives outside the main window
        // so it can open next to the tray icon on any OS.
        com.alananasss.kittytune.ui.tray.ModernTrayMenuHost(
            isMiniPlayerVisible = playerViewModel.isMiniPlayerVisible,
            onShowWindow = { showMainWindow() },
            onToggleMiniPlayer = { playerViewModel.toggleMiniPlayer() },
            onExit = {
                com.alananasss.kittytune.core.AppInstance.isShuttingDown = true
                exitApplication()
            },
        )
    } // End application
} // End main



/**
 * Keeps the Windows title bar in step with the app's palette, or hands it back to the system when
 * the user turns the setting off. A no-op on every other platform — see [WindowsTitleBar].
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

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
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.DesktopBackDispatcher
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.ui.ImageLoaderFactory
import com.alananasss.kittytune.ui.login.LoginScreen
import com.alananasss.kittytune.ui.login.WelcomeScreen
import com.alananasss.kittytune.ui.main.MainScreen
import com.alananasss.kittytune.ui.setup.SetupScreen
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import com.alananasss.kittytune.data.local.PlayerPreferences

import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed

enum class AppState { WELCOME, LOGIN, SETUP, MAIN }

@Composable
fun AppRouter() {
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
        AppState.MAIN -> MainScreen()
    }
}

fun main() {
    System.setProperty("sun.java2d.wm.className", "kitty-tune")
    AppBootstrap.init()

    application {
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
        var isWindowVisible by remember { mutableStateOf(true) }

        if (!stopOnTaskClear && appIcon != null) {
            Tray(
                icon = appIcon,
                tooltip = "KittyTune",
                onAction = { isWindowVisible = true },
                menu = {
                    Item(str("menu_show_window"), onClick = { isWindowVisible = true })
                    Item(str("menu_exit"), onClick = { com.alananasss.kittytune.core.AppInstance.isShuttingDown = true; exitApplication() })
                }
            )
        }

        // Hoisted so the full player can ask for a real full screen rather than an overlay that covers the
        // window: the title bar, the taskbar and everything that reacts near a screen edge stay put
        // otherwise, which is what "pas un pop up mais un vrai écran" is about (issue #33).
        //
        // The placement it came from is remembered, so leaving gives a maximised window back to somebody who
        // had one and a floating window back to somebody who did not.
        val windowState = rememberWindowState(
            size = DpSize(1440.dp, 900.dp),
            position = androidx.compose.ui.window.WindowPosition(androidx.compose.ui.Alignment.Center),
        )
        val placementBeforeFullScreen = remember {
            mutableStateOf(androidx.compose.ui.window.WindowPlacement.Floating)
        }
        // Keyed on the flag *and* on the placement, so it re-asserts rather than firing once. A window manager
        // that declines a placement change — or grants it and then puts the window back itself, which
        // happens — used to leave the two permanently out of step with no way back (issue #33).
        LaunchedEffect(
            com.alananasss.kittytune.core.AppWindowState.fullScreen,
            windowState.placement,
        ) {
            val wanted = com.alananasss.kittytune.core.AppWindowState.fullScreen
            val isFullScreen = windowState.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen
            if (wanted && !isFullScreen) {
                placementBeforeFullScreen.value = windowState.placement
                windowState.placement = androidx.compose.ui.window.WindowPlacement.Fullscreen
            } else if (!wanted && isFullScreen) {
                // Never back to Fullscreen, whatever was stored: that is how a restore turns into a no-op.
                windowState.placement = placementBeforeFullScreen.value
                    .takeIf { it != androidx.compose.ui.window.WindowPlacement.Fullscreen }
                    ?: androidx.compose.ui.window.WindowPlacement.Floating
            }
        }

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

            // Enforce a minimum window size: below this the three-panel layout breaks
            // down and the app can crash (issue #27).
            window.minimumSize = java.awt.Dimension(960, 600)

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
                    // Inside the theme, so the title bar tracks the live palette — the
                    // cover-seeded dynamic theme included — instead of a colour read once at
                    // startup (issue #33).
                    ThemedTitleBarEffect(window)
                    Surface { AppRouter() }
                }
            }
        } // End Window
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

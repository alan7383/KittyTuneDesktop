package com.alananasss.kittytune

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
        val appIcon = androidx.compose.runtime.remember {
            runCatching {
                Thread.currentThread().contextClassLoader?.getResourceAsStream("icons/kittytune.png")?.use { stream ->
                    androidx.compose.ui.graphics.painter.BitmapPainter(androidx.compose.ui.res.loadImageBitmap(stream))
                }
            }.getOrNull()
        }

        val prefsForTray = remember { PlayerPreferences() }
        val stopOnTaskClear by prefsForTray.stopOnTaskClearFlow().collectAsState(initial = prefsForTray.getStopOnTaskClear())
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
            state = rememberWindowState(size = DpSize(1440.dp, 900.dp), position = androidx.compose.ui.window.WindowPosition(androidx.compose.ui.Alignment.Center)),
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
                            Key.Zero, Key.NumPad0 -> {
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
                        else -> false
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

            val prefs = remember { PlayerPreferences() }
            val uiScale by prefs.uiScaleFlow().collectAsState(initial = prefs.getUiScale())
            val currentDensity = LocalDensity.current
            val customDensity = Density(
                density = currentDensity.density * uiScale,
                fontScale = currentDensity.fontScale * uiScale
            )

            CompositionLocalProvider(LocalDensity provides customDensity) {
                KittyTuneTheme {
                    Surface { AppRouter() }
                }
            }
        } // End Window
    } // End application
} // End main



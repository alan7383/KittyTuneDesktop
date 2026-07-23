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
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.setSingletonImageLoaderFactory
import com.alananasss.kittytune.core.DesktopBackDispatcher
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.ui.ImageLoaderFactory
import com.alananasss.kittytune.ui.login.LoginScreen
import com.alananasss.kittytune.ui.login.WelcomeScreen
import com.alananasss.kittytune.ui.main.MainScreen
import com.alananasss.kittytune.ui.theme.KittyTuneTheme

enum class AppState { WELCOME, LOGIN, MAIN }

@Composable
fun AppRouter() {
    val tokenManager = remember { TokenManager }
    val isLoggedIn = !tokenManager.getAccessToken().isNullOrEmpty()
    val isGuestMode = tokenManager.isGuestMode()

    var appState by remember {
        mutableStateOf(if (isLoggedIn || isGuestMode) AppState.MAIN else AppState.WELCOME)
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
                appState = AppState.MAIN 
            },
            isGuestLoading = false
        )
        AppState.LOGIN -> LoginScreen(
            onLoginSuccess = { appState = AppState.MAIN },
            onBackClick = { appState = AppState.WELCOME }
        )
        AppState.MAIN -> MainScreen()
    }
}

fun main() {
    AppBootstrap.init()

    application {
        val appIcon = androidx.compose.runtime.remember {
            runCatching {
                Thread.currentThread().contextClassLoader?.getResourceAsStream("icons/kittytune.png")?.use { stream ->
                    androidx.compose.ui.graphics.painter.BitmapPainter(androidx.compose.ui.res.loadImageBitmap(stream))
                }
            }.getOrNull()
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "KittyTune",
            icon = appIcon,
            state = rememberWindowState(size = DpSize(1440.dp, 900.dp), position = androidx.compose.ui.window.WindowPosition(androidx.compose.ui.Alignment.Center)),
            onKeyEvent = { event ->
                // Esc = Android back (dismiss expanded player, sheets, nav back...)
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    DesktopBackDispatcher.onBack()
                } else {
                    com.alananasss.kittytune.core.GlobalShortcutDispatcher.dispatch(event)
                }
            },
        ) {
            setSingletonImageLoaderFactory { ImageLoaderFactory.create() }

            KittyTuneTheme {
                Surface { AppRouter() }
            }
        }
    }
}



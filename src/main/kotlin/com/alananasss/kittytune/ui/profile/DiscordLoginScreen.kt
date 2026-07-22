package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.player.PlayerViewModel
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val JS_SNIPPET = "(function(){var i=document.createElement('iframe');document.body.appendChild(i);return i.contentWindow.localStorage.token.slice(1,-1);})()"

@Composable
fun DiscordLoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val prefs = remember { PlayerPreferences() }
    val scope = rememberCoroutineScope()
    val jfxPanel = remember { JFXPanel() }

    DisposableEffect(Unit) {
        try {
            Platform.setImplicitExit(false)
        } catch (_: Exception) {}

        Platform.runLater {
            try {
                val webView = WebView()
                val engine = webView.engine
                engine.isJavaScriptEnabled = true
                engine.userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

                val scene = Scene(webView)
                jfxPanel.scene = scene

                var tokenFound = false

                fun checkAndExtractToken(url: String?) {
                    if (url != null && !tokenFound && (url.contains("discord.com/app") || url.contains("discord.com/channels/@me"))) {
                        tokenFound = true
                        Platform.runLater {
                            try {
                                val result = engine.executeScript(JS_SNIPPET) as? String
                                val cleanToken = result?.replace("\"", "")?.trim()
                                if (!cleanToken.isNullOrBlank() && cleanToken.length >= 20 && cleanToken != "null") {
                                    scope.launch(Dispatchers.Main) {
                                        prefs.setDiscordToken(cleanToken)
                                        prefs.setDiscordRpcEnabled(true)
                                        playerViewModel?.updateDiscordPresence()
                                        onLoginSuccess()
                                    }
                                } else {
                                    tokenFound = false
                                }
                            } catch (e: Exception) {
                                tokenFound = false
                            }
                        }
                    }
                }

                engine.locationProperty().addListener { _, _, newUrl ->
                    checkAndExtractToken(newUrl)
                }

                engine.loadWorker.stateProperty().addListener { _, _, newState ->
                    if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                        checkAndExtractToken(engine.location)
                    }
                }

                engine.load("https://discord.com/login")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        onDispose {
            Platform.runLater {
                try {
                    jfxPanel.scene = null
                } catch (_: Exception) {}
            }
        }
    }

    SettingsScaffold(
        title = str("discord_login_title"),
        onBackClick = onBackClick
    ) { innerPadding ->
        SwingPanel(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            factory = { jfxPanel }
        )
    }
}

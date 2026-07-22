@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.AuthFlowManager
import com.alananasss.kittytune.data.PkceHelper
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import java.net.URI
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

private const val TAG = "LoginScreen"

private const val AUTH_BASE_URL = "https://secure.soundcloud.com/"
private const val OFFICIAL_APP_ID = 3152
private const val REDIRECT_URI = "sc://auth"

private const val AUTH_API_BASE = "https://api-auth.soundcloud.com"

private val authHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val tokenManager = remember { TokenManager }
    val pkceVerifier = remember { PkceHelper.generateVerifier() }
    val pkceChallenge = remember(pkceVerifier) { PkceHelper.generateChallenge(pkceVerifier) }
    val deviceId = remember { Config.getOrCreateSoundCloudDeviceId() }
    val authState = remember { java.util.UUID.randomUUID().toString() }
    val authUrl = remember(pkceChallenge, deviceId, authState) {
        buildAuthUrl(
            clientId = Config.OFFICIAL_CLIENT_ID,
            deviceId = deviceId,
            trackingAnonymousId = deviceId,
            codeChallenge = pkceChallenge,
            state = authState,
            isSignup = false
        )
    }

    var isLoading by remember { mutableStateOf(false) }
    var lastHandledCode by remember { mutableStateOf<String?>(null) }
    val authCodeFromIntent by AuthFlowManager.authCode.collectAsState()

    var hasLaunchedBrowser by rememberSaveable { mutableStateOf(false) }

    val os = remember { System.getProperty("os.name").lowercase() }

    DisposableEffect(Unit) {
        val server = try {
            HttpServer.create(InetSocketAddress(0), 0)
        } catch (e: Exception) {
            println("Could not start local server: ${e.message}")
            null
        }

        if (server != null) {
            val port = server.address.port

            if (os.contains("windows")) {
                try {
                    val appDir = com.alananasss.kittytune.core.AppDirs.dataDir
                    if (!appDir.exists()) appDir.mkdirs()

                    val batFile = java.io.File(appDir, "sc_handler.bat")
                    batFile.writeText("@echo off\r\npowershell.exe -WindowStyle Hidden -Command \"Invoke-RestMethod -Uri 'http://localhost:$port/callback' -Method Post -Body '%1'\"")

                    val cmd1 = arrayOf("REG", "ADD", "HKCU\\Software\\Classes\\sc", "/ve", "/d", "URL:sc Protocol", "/f")
                    val cmd2 = arrayOf("REG", "ADD", "HKCU\\Software\\Classes\\sc", "/v", "URL Protocol", "/d", "", "/f")

                    val handlerCmd = "\"${batFile.absolutePath}\" \"%1\""
                    val cmd3 = arrayOf("REG", "ADD", "HKCU\\Software\\Classes\\sc\\shell\\open\\command", "/ve", "/d", handlerCmd, "/f")

                    Runtime.getRuntime().exec(cmd1).waitFor()
                    Runtime.getRuntime().exec(cmd2).waitFor()
                    Runtime.getRuntime().exec(cmd3).waitFor()
                } catch (e: Exception) {
                    println("Failed to register Windows protocol: ${e.message}")
                }
            } else if (os.contains("linux")) {
                try {
                    val appDir = com.alananasss.kittytune.core.AppDirs.dataDir
                    if (!appDir.exists()) appDir.mkdirs()

                    val shellFile = java.io.File(appDir, "sc_handler.sh")
                    shellFile.writeText(buildString {
                        append("#!/bin/bash\n")
                        append("# Only forward the OAuth redirect (contains code= param).\n")
                        append("# SoundCloud's deep-link check sends sc://auth without code=\n")
                        append("# and we intentionally ignore it to avoid a pre-login prompt.\n")
                        append("if [[ \"\$1\" == *\"code=\"* ]]; then\n")
                        append("  curl -s -X POST 'http://localhost:$port/callback' -d \"\$1\"\n")
                        append("fi\n")
                    })
                    shellFile.setExecutable(true)

                    val mimeDir = java.io.File(System.getProperty("user.home"), ".local/share/applications")
                    mimeDir.mkdirs()
                    val targetDesktop = java.io.File(mimeDir, "kittytune-sc.desktop")
                    targetDesktop.writeText(buildString {
                        append("[Desktop Entry]\n")
                        append("Name=KittyTune SC Auth\n")
                        append("Exec=${shellFile.absolutePath} %u\n")
                        append("Type=Application\n")
                        append("NoDisplay=true\n")
                        append("MimeType=x-scheme-handler/sc;\n")
                        append("Terminal=false\n")
                    })

                    Runtime.getRuntime().exec(arrayOf("xdg-mime", "default", "kittytune-sc.desktop", "x-scheme-handler/sc")).waitFor()
                    Runtime.getRuntime().exec(arrayOf("update-desktop-database", mimeDir.absolutePath)).waitFor()
                } catch (e: Exception) {
                    println("Failed to register Linux protocol: ${e.message}")
                }
            }

            server.apply {
                createContext("/callback") { exchange ->
                    val url = when (exchange.requestMethod.uppercase()) {
                        "GET" -> exchange.requestURI.toString()
                        "POST" -> String(exchange.requestBody.readBytes())
                        else -> { exchange.sendResponseHeaders(405, -1); return@createContext }
                    }

                    val code = extractCodeFromUrl(url)
                    val returnedState = extractStateFromUrl(url)
                    if (code != null && returnedState == authState) {
                        AuthFlowManager.setAuthCode(code)
                    } else if (code != null) {
                        println("State mismatch! Expected $authState but got $returnedState")
                    }
                    val response = "Authentification reussie ! Vous pouvez fermer cette page."
                    exchange.sendResponseHeaders(200, response.toByteArray(Charsets.UTF_8).size.toLong())
                    exchange.responseBody.write(response.toByteArray(Charsets.UTF_8))
                    exchange.responseBody.close()
                }
                executor = null
                start()
            }
        }

        onDispose {
            server?.stop(0)
        }
    }

    LaunchedEffect(authUrl) {
        if (!hasLaunchedBrowser) {
            hasLaunchedBrowser = true
            launchSoundCloudAuth(authUrl)
        }
    }

    LaunchedEffect(authCodeFromIntent) {
        val code = authCodeFromIntent
        if (code != null && code != lastHandledCode) {
            lastHandledCode = code
            isLoading = true

            val success = withContext(Dispatchers.IO) {
                exchangeCodeForTokens(
                    code = code,
                    codeVerifier = pkceVerifier,
                    clientId = Config.OFFICIAL_CLIENT_ID,
                    tokenManager = tokenManager
                )
            }

            isLoading = false
            AuthFlowManager.clearAuthCode()

            if (success) {
                SessionManager.harvestStoredSession()
                SessionManager.requestSessionRefresh(force = true)
                onLoginSuccess()
            } else {
                println("Token exchange failed after OAuth callback")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(str("login_title")) },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBackClick,
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = str("btn_cancel")
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularWavyProgressIndicator()
                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Text(
                        text = str("login_auth_in_progress"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = str("login_waiting_browser"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
onClick = { launchSoundCloudAuth(authUrl) }) {
                        Text(str("login_reopen_browser"))
                    }
                }
            }
        }
    }
}

private fun extractCodeFromUrl(url: String): String? {
    try {
        if (!url.contains("code=")) return null
        val uri = URI(url.trim())
        val query = uri.query ?: return url.substringAfter("code=").substringBefore("&")
        val params = query.split("&").map { it.split("=") }
        return params.firstOrNull { it.size == 2 && it[0] == "code" }?.get(1)
    } catch (e: Exception) {
        return url.substringAfter("code=").substringBefore("&")
    }
}

private fun extractStateFromUrl(url: String): String? {
    try {
        if (!url.contains("state=")) return null
        val uri = URI(url.trim())
        val query = uri.query ?: return url.substringAfter("state=").substringBefore("&")
        val params = query.split("&").map { it.split("=") }
        return params.firstOrNull { it.size == 2 && it[0] == "state" }?.get(1)
    } catch (e: Exception) {
        return url.substringAfter("state=").substringBefore("&")
    }
}

private fun buildAuthUrl(
    clientId: String,
    deviceId: String,
    trackingAnonymousId: String,
    codeChallenge: String,
    state: String,
    isSignup: Boolean
): String {
    val startView = if (isSignup) "create_account" else "sign_in"
    val locale = Locale.getDefault().language

    return buildString {
        append(AUTH_BASE_URL).append("web-auth?")
        append("client_id=").append(URLEncoder.encode(clientId, "UTF-8"))
        append("&app_id=").append(OFFICIAL_APP_ID)
        append("&device_id=").append(URLEncoder.encode(deviceId, "UTF-8"))
        append("&start_view=").append(URLEncoder.encode(startView, "UTF-8"))
        append("&redirect_uri=").append(URLEncoder.encode(REDIRECT_URI, "UTF-8"))
        append("&response_type=code")
        append("&code_challenge=").append(URLEncoder.encode(codeChallenge, "UTF-8"))
        append("&code_challenge_method=S256")
        append("&ui_evo=true")
        append("&stand_alone=true")
        append("&tracking=local")
        append("&show_confirmation=true")
        append("&theme=dark")
        append("&locale=").append(URLEncoder.encode(locale, "UTF-8"))
        append("&sc_tracking_anonymous_id=").append(URLEncoder.encode(trackingAnonymousId, "UTF-8"))
        append("&state=").append(URLEncoder.encode(state, "UTF-8"))
    }
}

private fun launchSoundCloudAuth(authUrl: String) {
    com.alananasss.kittytune.core.openUrl(authUrl)
}

private fun exchangeCodeForTokens(
    code: String,
    codeVerifier: String,
    clientId: String,
    tokenManager: TokenManager
): Boolean {
    val tokenUrls = listOf(
        "$AUTH_API_BASE/oauth/token",
        "${Config.BASE_URL.trimEnd('/')}/oauth/token"
    ).distinct()

    for (tokenUrl in tokenUrls) {
        try {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()

            val requestBuilder = Request.Builder()
                .url(tokenUrl)
                .header("User-Agent", Config.USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://soundcloud.com")
                .header("Referer", "https://soundcloud.com/")

            if (clientId == Config.OFFICIAL_CLIENT_ID) {
                requestBuilder.header("Authorization", Config.OFFICIAL_CLIENT_SIGNATURE)
            }

            val request = requestBuilder.post(formBody).build()
            
            val response = authHttpClient.newCall(request).execute()
            try {
                val bodyStr = response.body?.string() ?: ""
                
                if (!response.isSuccessful) {
                    println("Token exchange failed at $tokenUrl: ${response.code} - $bodyStr")
                    continue
                }

                val json = JSONObject(bodyStr)
                val accessToken = json.optString("access_token", "").cleanOAuthValue()
                val refreshToken = json.optString("refresh_token", "").cleanOAuthValue()
                val expiresIn = json.optLong("expires_in", 0L)
                val scope = json.optString("scope", "").cleanOAuthValue()

                if (accessToken.isNullOrEmpty()) {
                    continue
                }

                tokenManager.saveTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = if (expiresIn > 0) expiresIn else null,
                    scope = scope
                )

                return true
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            println("Token exchange error at $tokenUrl: ${e.message}")
        }
    }

    return false
}

private fun String?.cleanOAuthValue(): String? = this
    ?.trim()
    ?.trim('"')
    ?.takeIf { it.isNotBlank() && it != "null" }

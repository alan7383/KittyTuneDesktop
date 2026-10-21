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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularWavyProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.GuestDataSummary
import com.alananasss.kittytune.data.GuestDataTransferManager
import com.alananasss.kittytune.data.PkceHelper
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.net.URLEncoder
import com.alananasss.kittytune.data.SoundCloudOAuthServer
import com.alananasss.kittytune.data.MacSoundCloudOAuth
import androidx.compose.runtime.key
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation

private const val TAG = "LoginScreen"

private const val AUTH_BASE_URL = "https://secure.soundcloud.com/"
private const val OFFICIAL_APP_ID = 3152
private const val REDIRECT_URI = "sc://auth"

private const val AUTH_API_BASE = "https://api-auth.soundcloud.com"

private val baseAuthHttpClient by lazy {
    // Shares ProxyManager's pool and dispatcher; only the timeouts are this screen's own.
    com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient().newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
}

private val authHttpClient: OkHttpClient
    get() = com.alananasss.kittytune.data.network.ProxyManager.configureOkHttpClient(baseAuthHttpClient.newBuilder()).build()

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    var attempt by remember { mutableStateOf(0) }
    key(attempt) {
        LoginAttempt(onLoginSuccess, onBackClick, onRetry = { attempt++ })
    }
}

@Composable
private fun LoginAttempt(onLoginSuccess: () -> Unit, onBackClick: () -> Unit, onRetry: () -> Unit) {
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
    var authCodeFromIntent by remember { mutableStateOf<String?>(null) }

    var hasLaunchedBrowser by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var isTransferring by remember { mutableStateOf(false) }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var guestSummary by remember { mutableStateOf<GuestDataSummary?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var loginError by remember { mutableStateOf<String?>(null) }
    var callbackServer by remember { mutableStateOf<SoundCloudOAuthServer?>(null) }
    var macRegistration by remember { mutableStateOf<AutoCloseable?>(null) }
    var isProtocolReady by remember { mutableStateOf(false) }
    var serverPort by remember { mutableStateOf<Int?>(null) }

    val os = remember { System.getProperty("os.name").lowercase() }

    DisposableEffect(Unit) {
        val server = try {
            SoundCloudOAuthServer(authState) { result ->
                coroutineScope.launch {
                    if (result.error != null) {
                        loginError = str("login_oauth_declined")
                    } else {
                        authCodeFromIntent = requireNotNull(result.code)
                    }
                }
            }.also {
                callbackServer = it
                serverPort = it.port
            }
        } catch (e: Exception) {
            loginError = str("login_oauth_server_error")
            null
        }
        onDispose {
            macRegistration?.close()
            server?.close()
        }
    }

    LaunchedEffect(Unit) {
        delay(5 * 60 * 1000L)
        if (!isLoading && !showTransferDialog) loginError = str("login_oauth_timeout")
    }

    LaunchedEffect(loginError) {
        if (loginError != null) {
            macRegistration?.close()
            callbackServer?.close()
        }
    }

    LaunchedEffect(serverPort) {
        if (serverPort != null && !isProtocolReady) {
            var registration: AutoCloseable? = null
            try {
                withContext(Dispatchers.IO) {
                    try {
                        if (os.contains("windows")) {
                            val appDir = com.alananasss.kittytune.core.AppDirs.dataDir
                            if (!appDir.exists()) appDir.mkdirs()

                            // Write the current port to a side-car file so the VBS always reads
                            // the live port rather than a value baked in at registration time.
                            val portFile = java.io.File(appDir, "sc_oauth_port.txt")
                            portFile.writeText(serverPort.toString())

                            val vbsFile = java.io.File(appDir, "sc_handler.vbs")
                            val portFilePath = portFile.absolutePath
                            val vbsContent = buildString {
                                append("If WScript.Arguments.Count > 0 Then\r\n")
                                append("  url = WScript.Arguments(0)\r\n")
                                append("  If InStr(url, \"code=\") > 0 Or InStr(url, \"error=\") > 0 Then\r\n")
                                append("    Dim fso, port\r\n")
                                append("    Set fso = CreateObject(\"Scripting.FileSystemObject\")\r\n")
                                append("    port = Trim(fso.OpenTextFile(\"$portFilePath\", 1).ReadAll())\r\n")
                                append("    Set req = CreateObject(\"MSXML2.ServerXMLHTTP.6.0\")\r\n")
                                append("    req.open \"POST\", \"http://127.0.0.1:\" & port & \"/callback\", False\r\n")
                                append("    req.setRequestHeader \"Content-Type\", \"text/plain\"\r\n")
                                append("    req.send url\r\n")
                                append("  End If\r\n")
                                append("End If\r\n")
                            }
                            vbsFile.writeText(vbsContent)
                        
                            if (!vbsFile.exists()) {
                                throw Exception("VBS file creation failed at ${vbsFile.absolutePath}")
                            }

                            // Use reg.exe directly to avoid PowerShell backslash-escaping complexity.
                            // The VBS path needs no escaping since reg.exe receives it as a raw argument.
                            val handlerCmd = """wscript.exe "${vbsFile.absolutePath}" "%1""""
                            val regExe = "${System.getenv("SystemRoot") ?: "C:\\Windows"}\\System32\\reg.exe"
                            fun regAdd(key: String, vararg args: String) {
                                val cmd = arrayOf(regExe, "add", key, "/f") + args
                                val proc = Runtime.getRuntime().exec(cmd)
                                val stderr = proc.errorStream.bufferedReader().readText()
                                val exit = proc.waitFor()
                                if (exit != 0) throw Exception("reg add failed (exit $exit): $stderr")
                            }
                            regAdd("HKCU\\Software\\Classes\\sc",
                                "/ve", "/d", "URL:sc Protocol")
                            regAdd("HKCU\\Software\\Classes\\sc",
                                "/v", "URL Protocol", "/d", "")
                            regAdd("HKCU\\Software\\Classes\\sc\\shell\\open\\command",
                                "/ve", "/d", handlerCmd)
                            println("Successfully registered sc:// protocol handler with VBS wrapper")
                        } else if (os.contains("mac")) {
                            registration = MacSoundCloudOAuth.prepare(authState, requireNotNull(serverPort))
                            macRegistration = registration
                        } else if (os.contains("linux")) {
                            val appDir = com.alananasss.kittytune.core.AppDirs.dataDir
                            if (!appDir.exists()) appDir.mkdirs()

                            val shellFile = java.io.File(appDir, "sc_handler.sh")
                            shellFile.writeText(buildString {
                                append("#!/bin/bash\n")
                                append("# Forward success and denial; ignore parameter-free probes.\n")
                                append("# SoundCloud's deep-link check sends sc://auth without code=\n")
                                append("# and we intentionally ignore it to avoid a pre-login prompt.\n")
                                append("if [[ \"\$1\" == *\"code=\"* || \"\$1\" == *\"error=\"* ]]; then\n")
                                append("  curl -s -X POST 'http://127.0.0.1:$serverPort/callback' -d \"\$1\"\n")
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
                        
                            println("Successfully registered sc:// protocol handler for Linux")
                        }
                    } catch (e: Exception) {
                        println("Failed to register protocol handler: ${e.message}")
                        loginError = e.message ?: "Could not register the SoundCloud link handler. Please try again."
                    }
                }
                if (loginError == null) {
                    isProtocolReady = true
                }
                awaitCancellation()
            } finally {
                registration?.close()
            }
        }
    }

    LaunchedEffect(authUrl, isProtocolReady) {
        if (!hasLaunchedBrowser && isProtocolReady) {
            hasLaunchedBrowser = true
            launchSoundCloudAuth(authUrl)
        }
    }

    LaunchedEffect(authCodeFromIntent) {
        val code = authCodeFromIntent
        if (code != null && code != lastHandledCode) {
            lastHandledCode = code
            isLoading = true
            macRegistration?.close()
            callbackServer?.close()
            val wasGuestMode = tokenManager.isGuestMode()

            val success = withContext(Dispatchers.IO) {
                exchangeCodeForTokens(
                    code = code,
                    codeVerifier = pkceVerifier,
                    clientId = Config.OFFICIAL_CLIENT_ID,
                    tokenManager = tokenManager
                )
            }

            isLoading = false
            authCodeFromIntent = null

            if (success) {
                SessionManager.harvestStoredSession()
                SessionManager.requestSessionRefresh(force = true)

                if (wasGuestMode) {
                    val summary = GuestDataTransferManager.getGuestDataSummary()
                    if (summary.hasData) {
                        guestSummary = summary
                        showTransferDialog = true
                    } else {
                        onLoginSuccess()
                    }
                } else {
                    onLoginSuccess()
                }
            } else {
                loginError = str("login_oauth_exchange_error")
            }
        }
    }

    if (showTransferDialog && guestSummary != null) {
        TransferGuestDataDialog(
            summary = guestSummary!!,
            isTransferring = isTransferring,
            progress = transferProgress,
            onTransfer = { transferLikes, transferUserPlaylists, transferLikedPlaylists ->
                isTransferring = true
                coroutineScope.launch {
                    val ok = GuestDataTransferManager.transferData(
                        transferLikes = transferLikes,
                        transferUserPlaylists = transferUserPlaylists,
                        transferLikedPlaylists = transferLikedPlaylists,
                        onProgress = { transferProgress = it }
                    )
                    isTransferring = false
                    showTransferDialog = false
                    val msgKey = if (ok) "transfer_guest_success" else "transfer_guest_error"
                    com.alananasss.kittytune.core.Toaster.show(str(msgKey))
                    onLoginSuccess()
                }
            },
            onDismiss = {
                showTransferDialog = false
                coroutineScope.launch {
                    GuestDataTransferManager.clearGuestData()
                    onLoginSuccess()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(str("login_title")) },
                navigationIcon = {
                    FilledTonalIconButton(shapes = IconButtonDefaults.shapes(), onClick = onBackClick,
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

                if (loginError != null) {
                    Text(requireNotNull(loginError), modifier = Modifier.padding(24.dp))
                    OutlinedButton(onClick = onRetry) { Text(str("login_oauth_retry")) }
                } else if (isLoading) {
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

                    OutlinedButton(shapes = ButtonDefaults.shapes(),
onClick = { if (isProtocolReady) launchSoundCloudAuth(authUrl) }) {
                        Text(str("login_reopen_browser"))
                    }
                }
            }
        }
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
                    println("Token exchange failed at $tokenUrl: ${response.code}")
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

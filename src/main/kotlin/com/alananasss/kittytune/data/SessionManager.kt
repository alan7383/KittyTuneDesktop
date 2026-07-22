package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.data.network.CookieStore
import com.alananasss.kittytune.utils.Config
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Desktop port of the Android SessionManager.
 *
 * The Android version harvested SoundCloud session/DataDome cookies from a
 * headless WebView. On desktop:
 *   - The pure-OkHttp OAuth `refresh_token` grant (the portable core) is kept
 *     verbatim â€” this is the primary token-refresh path.
 *   - The WebView cookie jar is replaced by [CookieStore]; the login flow (an
 *     embedded browser, handled by the login UI) populates it.
 *   - DataDome cookie is persisted to a small JSON file instead of WebView storage.
 *
 * The captcha WebView challenge flow is exposed as StateFlow signals for the UI,
 * which shows an embedded browser when a challenge is required.
 */
object SessionManager {
    private const val REFRESH_INTERVAL = 20 * 60 * 1000L
    private const val SESSION_REFRESH_TIMEOUT_MS = 12_000L
    private const val AUTH_API_BASE_URL = "https://api-auth.soundcloud.com/"

    private val dataDomeFile = File(AppDirs.dataDir, "soundcloud_datadome.txt")

    private data class OAuthTokenResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresInSeconds: Long?,
        @SerializedName("scope") val scope: String?
    )

    private val tokenManager = TokenManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepAliveJob: Job? = null

    private val apiRefreshLock = Any()
    private var pendingApiRefresh: CompletableDeferred<String?>? = null

    private val authClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .cookieJar(CookieStore)
        .build()

    private val gson = Gson()

    private val _isClientIdValid = MutableStateFlow(false)
    val isClientIdValid = _isClientIdValid.asStateFlow()

    private val _sessionReadyEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionReadyEvent = _sessionReadyEvent.asSharedFlow()

    private val _showCaptchaFlow = MutableStateFlow(false)
    val showCaptchaFlow = _showCaptchaFlow.asStateFlow()

    /** URL the login/captcha embedded browser should load, if any. */
    private val _captchaUrlFlow = MutableStateFlow<String?>(null)
    val captchaUrlFlow = _captchaUrlFlow.asStateFlow()

    fun start() {
        startKeepAliveCycle()
    }

    /**
     * Returns a usable access token, harvested from the cookie store first (in case
     * the login browser just wrote one), else from the token store.
     */
    fun harvestStoredSession(): String? {
        // The login browser writes oauth_token/refresh_token cookies into CookieStore.
        val cookieToken = CookieStore.value("soundcloud.com", "oauth_token")
        val cookieRefresh = CookieStore.value("soundcloud.com", "refresh_token")
        if (!cookieToken.isNullOrBlank()) {
            val prev = tokenManager.getAccessToken()
            if (cookieToken != prev) {
                tokenManager.saveTokens(cookieToken, cookieRefresh ?: tokenManager.getRefreshToken())
                _sessionReadyEvent.tryEmit(Unit)
            }
            return cookieToken
        }
        return tokenManager.getAccessToken()
    }

    private fun startKeepAliveCycle() {
        if (keepAliveJob?.isActive == true) return
        keepAliveJob = scope.launch {
            while (true) {
                delay(REFRESH_INTERVAL)
                if (!_showCaptchaFlow.value) {
                    requestSessionRefresh(force = false)
                }
            }
        }
    }

    fun requestSessionRefresh(force: Boolean = false) {
        harvestStoredSession()
        if (tokenManager.isGuestMode()) return
        val shouldRefreshToken = tokenManager.shouldRefreshAccessToken()
        if (!_showCaptchaFlow.value && (force || shouldRefreshToken)) {
            scope.launch {
                withTimeoutOrNull(SESSION_REFRESH_TIMEOUT_MS) {
                    refreshAccessTokenFromApi()
                }
            }
        }
    }

    suspend fun awaitFreshAccessToken(
        staleToken: String? = null,
        force: Boolean = false,
        timeoutMs: Long = SESSION_REFRESH_TIMEOUT_MS
    ): String? {
        if (tokenManager.isGuestMode()) return null

        val cookieToken = harvestStoredSession()
        val currentToken = cookieToken ?: tokenManager.getAccessToken()

        if (!force && !currentToken.isNullOrEmpty() && !tokenManager.shouldRefreshAccessToken()) {
            return currentToken
        }

        if (force && !staleToken.isNullOrEmpty() && !cookieToken.isNullOrEmpty() && cookieToken != staleToken) {
            return cookieToken
        }

        val refreshedByApi = withTimeoutOrNull(timeoutMs) {
            refreshAccessTokenFromApi()
        }

        return refreshedByApi?.takeIf { it.isNotEmpty() }
            ?: harvestStoredSession()
            ?: currentToken
    }

    fun refreshSessionBlocking(
        staleToken: String? = null,
        timeoutMs: Long = SESSION_REFRESH_TIMEOUT_MS
    ): String? = runBlocking {
        awaitFreshAccessToken(staleToken = staleToken, force = true, timeoutMs = timeoutMs)
    }

    private suspend fun refreshAccessTokenFromApi(): String? {
        val refreshToken = tokenManager.getRefreshToken() ?: return null
        if (refreshToken == "ghost_refresh") return null

        var shouldStartRefresh = false
        val refreshDeferred = synchronized(apiRefreshLock) {
            pendingApiRefresh?.takeIf { !it.isCompleted } ?: CompletableDeferred<String?>().also {
                pendingApiRefresh = it
                shouldStartRefresh = true
            }
        }

        if (shouldStartRefresh) {
            scope.launch {
                val refreshedToken = executeRefreshTokenRequest(refreshToken)
                refreshDeferred.complete(refreshedToken)
                synchronized(apiRefreshLock) {
                    if (pendingApiRefresh === refreshDeferred) pendingApiRefresh = null
                }
            }
        }

        return refreshDeferred.await()
    }

    private fun executeRefreshTokenRequest(refreshToken: String): String? {
        return oauthTokenUrls().firstNotNullOfOrNull { tokenUrl ->
            val request = Request.Builder()
                .url(tokenUrl)
                .header("User-Agent", Config.USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://soundcloud.com")
                .header("Referer", "https://soundcloud.com/")
                .header("Authorization", Config.OFFICIAL_CLIENT_SIGNATURE)
                .post(
                    FormBody.Builder()
                        .add("grant_type", "refresh_token")
                        .add("client_id", Config.OFFICIAL_CLIENT_ID)
                        .add("refresh_token", refreshToken)
                        .build()
                )
                .build()

            runCatching {
                authClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null

                    val body = response.body?.string().orEmpty()
                    val tokenResponse = gson.fromJson(body, OAuthTokenResponse::class.java)
                    val accessToken = tokenResponse.accessToken.cleanOAuthValue() ?: return@use null
                    val nextRefreshToken = tokenResponse.refreshToken.cleanOAuthValue() ?: refreshToken

                    tokenManager.saveTokens(
                        accessToken = accessToken,
                        refreshToken = nextRefreshToken,
                        expiresInSeconds = tokenResponse.expiresInSeconds,
                        scope = tokenResponse.scope
                    )
                    _sessionReadyEvent.tryEmit(Unit)
                    accessToken
                }
            }.getOrNull()
        }
    }

    private fun oauthTokenUrls(): List<String> = listOf(
        "${AUTH_API_BASE_URL.trimEnd('/')}/oauth/token",
        "${Config.BASE_URL.trimEnd('/')}/oauth/token"
    ).distinct()

    // --- DataDome ------------------------------------------------------------------------

    fun getStoredDataDomeCookie(): String? = try {
        if (dataDomeFile.exists()) dataDomeFile.readText().takeIf { it.isNotBlank() } else null
    } catch (_: Exception) {
        null
    }

    fun saveDataDomeCookie(cookie: String) {
        val normalized = normalizeDataDomeCookie(cookie) ?: return
        try {
            dataDomeFile.writeText(normalized)
        } catch (_: Exception) {
        }
    }

    fun extractDataDomeCaptchaUrl(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return Regex("\"url\"\\s*:\\s*\"([^\"]+)\"")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("\\/", "/")
            ?.takeIf { it.contains("captcha-delivery.com") }
    }

    /** Show the embedded-browser captcha challenge; resolved by [completeDataDomeChallenge]. */
    private var pendingDataDome: CompletableDeferred<Boolean>? = null

    suspend fun awaitDataDomeChallenge(captchaUrl: String, timeoutMs: Long = 120_000L): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingDataDome = deferred
        _captchaUrlFlow.value = captchaUrl
        _showCaptchaFlow.value = true
        val solved = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: false
        _showCaptchaFlow.value = false
        _captchaUrlFlow.value = null
        return solved
    }

    fun completeDataDomeChallenge(cookie: String?, solved: Boolean) {
        cookie?.let { saveDataDomeCookie(it) }
        _showCaptchaFlow.value = false
        _captchaUrlFlow.value = null
        pendingDataDome?.complete(solved)
        pendingDataDome = null
    }

    fun cancelCaptcha() = completeDataDomeChallenge(null, solved = false)

    fun onClientIdCaptured(newId: String) {
        if (newId != Config.CLIENT_ID) Config.updateClientId(newId)
        _isClientIdValid.value = true
    }

    private fun normalizeDataDomeCookie(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        return cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("datadome=") && it.length > "datadome=".length }
    }

    private fun String?.cleanOAuthValue(): String? = this
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
}


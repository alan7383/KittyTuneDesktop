package com.alananasss.kittytune.data

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
import java.util.concurrent.TimeUnit

/**
 * Desktop port of the Android SessionManager.
 *
 * The Android version harvested SoundCloud session/DataDome cookies from a
 * headless WebView. On desktop:
 *   - The pure-OkHttp OAuth `refresh_token` grant (the portable core) is kept
 *     verbatim - this is the primary token-refresh path.
 *   - The WebView cookie jar is replaced by [CookieStore]; the PKCE login flow
 *     (handled by the login UI) populates it through OkHttp responses.
 *   - The DataDome captcha machinery was dropped entirely, mirroring Android:
 *     PKCE-issued OAuth tokens with the official client_id no longer trip
 *     DataDome's bot detection, and the desktop port never had the captcha
 *     browser UI anyway (the challenge flows had no collectors, so a 403 just
 *     waited out the full 120 s timeout before failing).
 */
object SessionManager {
    private const val REFRESH_INTERVAL = 20 * 60 * 1000L
    private const val SESSION_REFRESH_TIMEOUT_MS = 12_000L
    private const val AUTH_API_BASE_URL = "https://api-auth.soundcloud.com/"

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

    // Shares ProxyManager's pool and dispatcher; the cookie jar is this session's own.
    private val baseAuthClient by lazy {
        com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient().newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cookieJar(CookieStore)
            .build()
    }

    private val authClient: OkHttpClient
        get() = com.alananasss.kittytune.data.network.ProxyManager.configureOkHttpClient(baseAuthClient.newBuilder()).build()

    private val gson = Gson()

    private val _isClientIdValid = MutableStateFlow(false)
    val isClientIdValid = _isClientIdValid.asStateFlow()

    private val _sessionReadyEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionReadyEvent = _sessionReadyEvent.asSharedFlow()

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
                requestSessionRefresh(force = false)
            }
        }
    }

    fun requestSessionRefresh(force: Boolean = false) {
        harvestStoredSession()
        if (tokenManager.isGuestMode()) return
        val shouldRefreshToken = tokenManager.shouldRefreshAccessToken()
        if (force || shouldRefreshToken) {
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

    // --- Client id capture ---------------------------------------------------------------

    fun onClientIdCaptured(newId: String) {
        if (newId != Config.CLIENT_ID) Config.updateClientId(newId)
        _isClientIdValid.value = true
    }

    private fun String?.cleanOAuthValue(): String? = this
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
}


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
 *   - The token lifecycle follows the official app's TokenRefreshCoordinator and
 *     TokenExchangeCoordinator: same client and same grants. Two things differ on
 *     purpose: tokens renew before they expire, and ending a session takes more
 *     proof, because a wrong logout is the worse failure here.
 *   - Tokens live in [TokenManager] only; SoundCloud cookies are never read back
 *     as tokens, they would overwrite rotated ones with stale values.
 *   - The WebView cookie jar is replaced by [CookieStore].
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

    private sealed interface TokenResult {
        data class Issued(val tokens: OAuthTokenResponse) : TokenResult
        data class Refused(val code: Int, val body: String) : TokenResult
        data object Unreachable : TokenResult
    }

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

    /** Login already issued fresh tokens. Reload consumers even when no refresh token was issued. */
    fun onLoginCompleted() {
        _sessionReadyEvent.tryEmit(Unit)
    }

    fun start() {
        startKeepAliveCycle()
        scope.launch { singleFlight { migrateLegacySession() } }
    }

    private fun startKeepAliveCycle() {
        if (keepAliveJob?.isActive == true) return
        keepAliveJob = scope.launch {
            while (true) {
                delay(REFRESH_INTERVAL)
                // Skip the refresh while in guest mode — there's no token to refresh, and the loop
                // was waking every 20 minutes for nothing. The previous code called
                // requestSessionRefresh which returned early in guest mode, but the delay still ran.
                if (tokenManager.isGuestMode()) continue
                requestSessionRefresh(force = false)
            }
        }
    }

    /**
     * Stops the keep-alive loop. Called from [TokenManager.logout] so the loop doesn't keep hitting
     * the token endpoint every 20 minutes after the user has logged out (audit Phase 4 CPU/network
     * gain). The loop restarts on the next [start] / login.
     */
    fun stop() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    fun requestSessionRefresh(force: Boolean = false) {
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

    /**
     * [staleToken] is the token the server just refused, when there is one. The refresh runs in
     * [scope], so a caller's timeout only stops the wait: a rotated token still gets saved.
     */
    suspend fun awaitFreshAccessToken(
        staleToken: String? = null,
        force: Boolean = false,
        timeoutMs: Long = SESSION_REFRESH_TIMEOUT_MS
    ): String? {
        if (tokenManager.isGuestMode()) return null

        val currentToken = tokenManager.getAccessToken()
        if (!force && !currentToken.isNullOrEmpty() && !tokenManager.shouldRefreshAccessToken()) {
            return currentToken
        }

        val refreshedByApi = withTimeoutOrNull(timeoutMs) {
            refreshAccessTokenFromApi(staleToken)
        }

        // Read back rather than falling back to currentToken: the session may have just ended.
        return refreshedByApi?.takeIf { it.isNotEmpty() } ?: tokenManager.getAccessToken()
    }

    fun refreshSessionBlocking(
        staleToken: String? = null,
        timeoutMs: Long = SESSION_REFRESH_TIMEOUT_MS
    ): String? = runBlocking {
        awaitFreshAccessToken(staleToken = staleToken, force = true, timeoutMs = timeoutMs)
    }

    private suspend fun refreshAccessTokenFromApi(staleToken: String? = null): String? =
        singleFlight { refreshSession(staleToken) }

    /**
     * One token operation at a time: refresh tokens rotate, so a second request carrying the same
     * one would be refused. Whatever the operation needs is read inside [block], never before.
     */
    private suspend fun singleFlight(block: () -> String?): String? {
        var shouldStart = false
        val deferred = synchronized(apiRefreshLock) {
            pendingApiRefresh?.takeIf { !it.isCompleted } ?: CompletableDeferred<String?>().also {
                pendingApiRefresh = it
                shouldStart = true
            }
        }

        if (shouldStart) {
            scope.launch {
                try {
                    deferred.complete(block())
                } finally {
                    deferred.complete(null)
                    synchronized(apiRefreshLock) {
                        if (pendingApiRefresh === deferred) pendingApiRefresh = null
                    }
                }
            }
        }

        return deferred.await()
    }

    private fun refreshSession(staleToken: String?): String? {
        val accessToken = tokenManager.getAccessToken()
        // A burst of 401s all carry the same old token: only the first one may refresh.
        if (staleToken != null && accessToken != null && accessToken != staleToken) return accessToken

        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken == "ghost_refresh") return null
        if (refreshToken != null) return redeemRefreshToken(refreshToken)

        // Nothing to redeem. Only a token the server refused can end the session here, and one
        // endpoint refusing it proves little (a stream can 401 for reasons of its own), so ask /me.
        if (accessToken != null && accessToken == staleToken && isRejectedByServer(accessToken)) {
            endSession("access token rejected, no refresh token")
        }
        return null
    }

    private fun redeemRefreshToken(refreshToken: String): String? {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", Config.OFFICIAL_CLIENT_ID)
            .add("refresh_token", refreshToken)
            .build()

        return when (val result = requestTokens(form)) {
            is TokenResult.Issued ->
                // A logout during the request must not bring the session back.
                if (tokenManager.getRefreshToken() == refreshToken) saveIssuedTokens(result.tokens, refreshToken) else null
            is TokenResult.Refused -> {
                println("KittyTune: token refresh refused (HTTP ${result.code})")
                if (tokenManager.getRefreshToken() == refreshToken &&
                    OAuthTokens.isRefreshTokenDead(result.code, result.body)
                ) {
                    endSession("refresh token invalidated")
                }
                null
            }
            TokenResult.Unreachable -> null
        }
    }

    /**
     * Moves a non-expiring token onto the expiring client, like the official app's
     * TokenExchangeCoordinator. Sessions logged in with the legacy client (v1.4.7 to v1.4.9) hold
     * such a token and no refresh token. A failure keeps the token as is; the next launch retries.
     */
    private fun migrateLegacySession(): String? {
        val accessToken = tokenManager.getAccessToken() ?: return null
        if (tokenManager.isGuestMode() || tokenManager.hasExpiringSession()) return null

        val form = FormBody.Builder()
            .add("grant_type", "token_exchange")
            .add("token", accessToken)
            .add("client_id", Config.LEGACY_CLIENT_ID)
            .add("client_secret", Config.LEGACY_CLIENT_SECRET)
            .add("issue_for_client_id", Config.OFFICIAL_CLIENT_ID)
            .build()

        return when (val result = requestTokens(form)) {
            is TokenResult.Issued ->
                if (tokenManager.getAccessToken() == accessToken) saveIssuedTokens(result.tokens, null) else null
            is TokenResult.Refused -> {
                println("KittyTune: token exchange refused (HTTP ${result.code})")
                null
            }
            TokenResult.Unreachable -> null
        }
    }

    /** Like the official app: no Authorization header, api-auth only. The other headers are ours. */
    private fun requestTokens(form: FormBody): TokenResult {
        val request = Request.Builder()
            .url("${AUTH_API_BASE_URL}oauth/token")
            .header("User-Agent", Config.USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")
            .post(form)
            .build()

        return try {
            authClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return TokenResult.Refused(response.code, body)

                val tokens = gson.fromJson(body, OAuthTokenResponse::class.java)
                if (tokens?.accessToken.cleanOAuthValue() == null) TokenResult.Unreachable else TokenResult.Issued(tokens)
            }
        } catch (e: Exception) {
            println("KittyTune: token request failed: ${e.message}")
            TokenResult.Unreachable
        }
    }

    private fun saveIssuedTokens(tokens: OAuthTokenResponse, currentRefreshToken: String?): String? {
        val accessToken = tokens.accessToken.cleanOAuthValue() ?: return null
        tokenManager.saveTokens(
            accessToken = accessToken,
            refreshToken = tokens.refreshToken.cleanOAuthValue() ?: currentRefreshToken,
            expiresInSeconds = tokens.expiresInSeconds,
            scope = tokens.scope
        )
        _sessionReadyEvent.tryEmit(Unit)
        return accessToken
    }

    private fun isRejectedByServer(accessToken: String): Boolean {
        val request = Request.Builder()
            .url("${Config.BASE_URL}me?client_id=${Config.OFFICIAL_CLIENT_ID}")
            .header("User-Agent", Config.USER_AGENT)
            .header("Accept", "application/json")
            .header("Authorization", "OAuth $accessToken")
            .build()

        return runCatching {
            authClient.newCall(request).execute().use { it.code == 401 }
        }.getOrDefault(false)
    }

    /** Sends the user back to the login screen instead of leaving a dead session looking alive. */
    private fun endSession(reason: String) {
        println("KittyTune: SoundCloud session ended: $reason")
        tokenManager.logout()
    }

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


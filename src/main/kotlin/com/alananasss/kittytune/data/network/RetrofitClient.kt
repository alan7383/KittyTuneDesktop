package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.utils.Config
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Desktop port of the Android RetrofitClient.
 * - WebView CookieManager -> [CookieStore] (persistent OkHttp CookieJar) sends cookies automatically.
 * - android.os.Build spoofing -> fixed desktop values in the SoundCloud UA.
 * The 3 interceptors (auth, session-recovery, logging) keep the same behavior.
 */
object RetrofitClient {
    private var okHttpClient: OkHttpClient? = null
    private val tokenManager = TokenManager

    fun create(): SoundCloudApi {
        return Retrofit.Builder()
            .baseUrl(Config.BASE_URL)
            .client(getOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SoundCloudApi::class.java)
    }

    fun getOkHttpClient(): OkHttpClient {
        okHttpClient?.let { return it }

        val authInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            var token = SessionManager.harvestStoredSession() ?: tokenManager.getAccessToken()

            if (!token.isNullOrEmpty() && !tokenManager.isGuestMode() && tokenManager.shouldRefreshAccessToken()) {
                token = SessionManager.refreshSessionBlocking(
                    staleToken = token,
                    timeoutMs = 6_000L
                ) ?: token
            }

            val targetClientId = if (!token.isNullOrEmpty()) Config.OFFICIAL_CLIENT_ID else Config.CLIENT_ID

            val newUrl = originalRequest.url.let { url ->
                if (url.host == "api-mobile.soundcloud.com") {
                    url
                } else {
                    url.newBuilder()
                        .setQueryParameter("client_id", targetClientId)
                        .build()
                }
            }

            val deviceId = Config.getOrCreateSoundCloudDeviceId()
            val buildVersion = "2025.12.10-release"
            // Android Build.VERSION.RELEASE / MODEL are unavailable on desktop â€”
            // use stable values matching a generic device (the API only reads the UA loosely).
            val customUserAgent = "SoundCloud/$buildVersion (Android 10; Android)"

            val requestBuilder = originalRequest.newBuilder()
                .url(newUrl)
                .header("User-Agent", customUserAgent)
                .header("Accept", "application/json")
                .header("App-Version", "330120")
                .header("UDID", deviceId)

            // DataDome cookie (harvested separately) merged in if present.
            SessionManager.getStoredDataDomeCookie()?.let { dd ->
                val existing = originalRequest.header("Cookie")
                val merged = if (existing.isNullOrBlank()) dd else "$existing; $dd"
                requestBuilder.header("Cookie", merged)
            }

            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "OAuth $token")
            } else {
                requestBuilder.removeHeader("Authorization")
            }

            chain.proceed(requestBuilder.build())
        }

        val sessionRecoveryInterceptor = Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (!isAuthFailure(response.code) || tokenManager.isGuestMode()) {
                response
            } else {
                val sentToken = request.header("Authorization")
                    ?.removePrefix("OAuth ")
                    ?.takeIf { it.isNotBlank() }
                val currentToken = tokenManager.getAccessToken()

                if (sentToken.isNullOrEmpty() && currentToken.isNullOrEmpty()) {
                    response
                } else {
                    val refreshedToken = SessionManager.refreshSessionBlocking(
                        staleToken = sentToken ?: currentToken,
                        timeoutMs = 12_000L
                    )

                    if (refreshedToken.isNullOrEmpty() || refreshedToken == sentToken) {
                        if (!sentToken.isNullOrEmpty() && canRetryWithoutAuth(request)) {
                            response.close()
                            val guestUrl = request.url.newBuilder().setQueryParameter("client_id", Config.CLIENT_ID).build()
                            val retryAsGuest = request.newBuilder()
                                .url(guestUrl)
                                .removeHeader("Authorization")
                                .build()
                            chain.proceed(retryAsGuest)
                        } else {
                            response
                        }
                    } else {
                        response.close()
                        val retryRequest = request.newBuilder()
                            .header("Authorization", "OAuth $refreshedToken")
                            .build()
                        chain.proceed(retryRequest)
                    }
                }
            }
        }

        return OkHttpClient.Builder()
            .cookieJar(CookieStore)
            .addInterceptor(authInterceptor)
            .addInterceptor(sessionRecoveryInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
            .also { okHttpClient = it }
    }

    private fun isAuthFailure(code: Int): Boolean = code == 401

    private fun canRetryWithoutAuth(request: Request): Boolean {
        if (request.method != "GET") return false

        val path = request.url.encodedPath
        return path != "/me" &&
            !path.startsWith("/me/") &&
            !path.contains("/track_likes") &&
            !path.contains("/playlist_likes") &&
            !path.contains("/track_reposts") &&
            !path.contains("/conversations")
    }
}


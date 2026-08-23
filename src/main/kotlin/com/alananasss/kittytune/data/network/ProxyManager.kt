package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.utils.Logger
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.util.concurrent.TimeUnit

enum class ProxyProtocol {
    HTTP,
    SOCKS
}

data class ProxyConfig(
    val enabled: Boolean = false,
    val protocol: ProxyProtocol = ProxyProtocol.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val authEnabled: Boolean = false,
    val username: String = "",
    val password: String = ""
) {
    fun isValid(): Boolean = host.isNotBlank() && port in 1..65535
}

data class ProxyProfile(
    val id: String,
    val name: String,
    val config: ProxyConfig
)

sealed class ProxyTestResult {
    data class Success(val pingMs: Long) : ProxyTestResult()
    data class Error(val message: String) : ProxyTestResult()
}

object ProxyManager {
    private const val TAG = "ProxyManager"

    @Volatile
    private var activeJavaProxy: Proxy? = null

    @Volatile
    private var activeProxyAuthenticator: Authenticator? = null

    private val initialDefaultProxySelector: ProxySelector? = ProxySelector.getDefault()

    fun getActiveProxy(): Proxy? = activeJavaProxy

    fun isProxyActive(): Boolean = activeJavaProxy != null

    fun getActiveConfig(): ProxyConfig {
        val prefs = PlayerPreferences()
        val protocolStr = prefs.getProxyType()
        val protocol = if (protocolStr.equals("SOCKS", ignoreCase = true)) ProxyProtocol.SOCKS else ProxyProtocol.HTTP
        return ProxyConfig(
            enabled = prefs.getProxyEnabled(),
            protocol = protocol,
            host = prefs.getProxyHost(),
            port = prefs.getProxyPort(),
            authEnabled = prefs.getProxyAuthEnabled(),
            username = prefs.getProxyUsername(),
            password = prefs.getProxyPassword()
        )
    }

    fun applyConfiguration() {
        val config = getActiveConfig()

        if (config.enabled && config.isValid()) {
            val proxyType = if (config.protocol == ProxyProtocol.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val socketAddress = InetSocketAddress.createUnresolved(config.host, config.port)
            val javaProxy = Proxy(proxyType, socketAddress)
            activeJavaProxy = javaProxy

            if (config.authEnabled && config.username.isNotEmpty()) {
                activeProxyAuthenticator = Authenticator { _, response ->
                    val credential = Credentials.basic(config.username, config.password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            } else {
                activeProxyAuthenticator = null
            }

            // Set system properties for JVM sockets / URLConnections / JavaCV / etc.
            if (config.protocol == ProxyProtocol.SOCKS) {
                System.setProperty("socksProxyHost", config.host)
                System.setProperty("socksProxyPort", config.port.toString())
                System.clearProperty("http.proxyHost")
                System.clearProperty("http.proxyPort")
                System.clearProperty("https.proxyHost")
                System.clearProperty("https.proxyPort")

                if (config.authEnabled && config.username.isNotEmpty()) {
                    java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                        override fun getPasswordAuthentication(): java.net.PasswordAuthentication {
                            if (requestingHost.equals(config.host, ignoreCase = true) || requestingPort == config.port) {
                                return java.net.PasswordAuthentication(config.username, config.password.toCharArray())
                            }
                            return super.getPasswordAuthentication()
                        }
                    })
                } else {
                    java.net.Authenticator.setDefault(null)
                }
            } else {
                System.setProperty("http.proxyHost", config.host)
                System.setProperty("http.proxyPort", config.port.toString())
                System.setProperty("https.proxyHost", config.host)
                System.setProperty("https.proxyPort", config.port.toString())
                System.clearProperty("socksProxyHost")
                System.clearProperty("socksProxyPort")

                if (config.authEnabled && config.username.isNotEmpty()) {
                    java.net.Authenticator.setDefault(object : java.net.Authenticator() {
                        override fun getPasswordAuthentication(): java.net.PasswordAuthentication {
                            if (requestorType == RequestorType.PROXY) {
                                return java.net.PasswordAuthentication(config.username, config.password.toCharArray())
                            }
                            return super.getPasswordAuthentication()
                        }
                    })
                } else {
                    java.net.Authenticator.setDefault(null)
                }
            }

            Logger.i(TAG, "Proxy applied successfully: ${config.protocol} ${config.host}:${config.port} (auth=${config.authEnabled})")
        } else {
            activeJavaProxy = null
            activeProxyAuthenticator = null

            // Restore initial default ProxySelector and Authenticator
            ProxySelector.setDefault(initialDefaultProxySelector)
            java.net.Authenticator.setDefault(null)

            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            System.clearProperty("https.proxyHost")
            System.clearProperty("https.proxyPort")
            System.clearProperty("socksProxyHost")
            System.clearProperty("socksProxyPort")

            Logger.i(TAG, "Proxy disabled / reverted to direct connection.")
        }

        // Reset RetrofitClient singleton to recreate with new proxy settings
        RetrofitClient.resetClient()
    }

    fun configureOkHttpClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val proxy = activeJavaProxy
        if (proxy != null) {
            builder.proxy(proxy)
            activeProxyAuthenticator?.let { builder.proxyAuthenticator(it) }
        }
        return builder
    }

    fun getOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
        return configureOkHttpClient(builder).build()
    }

    suspend fun testProxyConnection(config: ProxyConfig): ProxyTestResult = withContext(Dispatchers.IO) {
        if (!config.isValid()) {
            return@withContext ProxyTestResult.Error("Invalid host or port")
        }

        try {
            val proxyType = if (config.protocol == ProxyProtocol.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val socketAddress = InetSocketAddress.createUnresolved(config.host, config.port)
            val testProxy = Proxy(proxyType, socketAddress)

            val builder = OkHttpClient.Builder()
                .proxy(testProxy)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            if (config.authEnabled && config.username.isNotEmpty()) {
                builder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(config.username, config.password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }

            val testClient = builder.build()

            val testRequest = Request.Builder()
                .url("https://api-v2.soundcloud.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .head()
                .build()

            val startTime = System.currentTimeMillis()
            val response = testClient.newCall(testRequest).execute()
            val latency = System.currentTimeMillis() - startTime
            response.close()

            ProxyTestResult.Success(latency)
        } catch (e: Exception) {
            Logger.w(TAG, "Proxy test failed", e)
            ProxyTestResult.Error(e.localizedMessage ?: e.message ?: "Connection timed out")
        }
    }

    fun parseProxyUri(raw: String): ProxyConfig? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        try {
            // Telegram proxy scheme: tg://socks?server=...&port=... or tg://proxy?server=...
            if (trimmed.startsWith("tg://socks", ignoreCase = true) || trimmed.startsWith("tg://proxy", ignoreCase = true)) {
                val queryIndex = trimmed.indexOf('?')
                if (queryIndex != -1) {
                    val query = trimmed.substring(queryIndex + 1)
                    val params = query.split('&').associate {
                        val parts = it.split('=', limit = 2)
                        parts[0] to (parts.getOrNull(1) ?: "")
                    }
                    val server = params["server"] ?: params["host"] ?: ""
                    val port = params["port"]?.toIntOrNull() ?: 1080
                    val user = params["user"] ?: params["username"] ?: ""
                    val pass = params["pass"] ?: params["password"] ?: ""
                    if (server.isNotBlank()) {
                        return ProxyConfig(
                            enabled = true,
                            protocol = ProxyProtocol.SOCKS,
                            host = server,
                            port = port,
                            authEnabled = user.isNotBlank(),
                            username = user,
                            password = pass
                        )
                    }
                }
            }

            // Standard scheme URI: (socks5://, socks://, http://, https://)
            val schemeRegex = Regex("^(socks5|socks|http|https)://", RegexOption.IGNORE_CASE)
            val hasScheme = schemeRegex.containsMatchIn(trimmed)

            val uriString = if (hasScheme) trimmed else "http://$trimmed"
            val javaUri = URI(uriString)

            val scheme = javaUri.scheme?.lowercase() ?: "http"
            val protocol = if (scheme.startsWith("socks")) ProxyProtocol.SOCKS else ProxyProtocol.HTTP

            val host = javaUri.host ?: ""
            val port = if (javaUri.port != -1) javaUri.port else if (protocol == ProxyProtocol.SOCKS) 1080 else 8080

            var user = ""
            var pass = ""
            val userInfo = javaUri.userInfo
            if (!userInfo.isNullOrBlank()) {
                val parts = userInfo.split(":", limit = 2)
                user = parts.getOrNull(0) ?: ""
                pass = parts.getOrNull(1) ?: ""
            }

            if (host.isNotBlank() && port in 1..65535) {
                return ProxyConfig(
                    enabled = true,
                    protocol = protocol,
                    host = host,
                    port = port,
                    authEnabled = user.isNotBlank(),
                    username = user,
                    password = pass
                )
            }

            // Colon-separated fallback: host:port:user:pass or host:port
            val colonParts = trimmed.split(":")
            if (colonParts.size >= 2) {
                val hostPart = colonParts[0].trim()
                val portPart = colonParts[1].toIntOrNull()
                if (hostPart.isNotBlank() && portPart != null && portPart in 1..65535) {
                    val userPart = colonParts.getOrNull(2)?.trim() ?: ""
                    val passPart = colonParts.getOrNull(3)?.trim() ?: ""
                    return ProxyConfig(
                        enabled = true,
                        protocol = ProxyProtocol.HTTP,
                        host = hostPart,
                        port = portPart,
                        authEnabled = userPart.isNotBlank(),
                        username = userPart,
                        password = passPart
                    )
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed parsing proxy URI: $trimmed", e)
        }

        return null
    }
}

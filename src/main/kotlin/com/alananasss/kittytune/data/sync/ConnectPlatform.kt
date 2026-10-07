package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.core.NamedPrefs

internal object ConnectPlatform {
    fun trace(event: String) {} // Debug transport tracing is enabled only in Android debug builds.
    fun canUseLan() = true
    const val mobile = false
    var autoHeadphones: Boolean
        get() = false
        set(value) {}
    fun observeHeadphones(active: Boolean) {}
    fun canAutoTransfer() = false
    private val prefs by lazy { NamedPrefs("sync_state") }
    var relayUrl: String
        get() = prefs.getString("connect_relay_url", "").orEmpty()
        set(value) = prefs.putString("connect_relay_url", value)
    /** A self-hosting PC joins its relay locally; the public URL is still shared with phones. */
    var localRelayUrl: String
        get() = prefs.getString("connect_relay_local_url", "").orEmpty()
        set(value) = prefs.putString("connect_relay_local_url", value)
    fun relayEndpoint(): String {
        val address = localRelayUrl
        return address.takeIf { runCatching {
            val uri = java.net.URI(it)
            uri.scheme == "ws" && uri.host in listOf("localhost", "127.0.0.1", "::1") &&
                uri.userInfo == null && uri.query == null && uri.fragment == null
        }.getOrDefault(false) } ?: relayUrl
    }
    fun startListener() { if (SyncService.isListenerEnabled && !SyncPeers.isEmpty()) ConnectLanServer.start() }
    fun setActive(value: Boolean) {}
}

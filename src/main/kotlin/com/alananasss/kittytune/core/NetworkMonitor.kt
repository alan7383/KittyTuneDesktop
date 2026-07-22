package com.alananasss.kittytune.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Desktop replacement for Android's ConnectivityManager.NetworkCallback.
 * Polls a TCP connect to well-known hosts; exposes a StateFlow<Boolean>.
 */
object NetworkMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> get() = _isOnline

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (true) {
                _isOnline.value = probe()
                // Check more often while offline so recovery is snappy (like the
                // instant NetworkCallback on Android).
                delay(if (_isOnline.value) 15_000 else 3_000)
            }
        }
    }

    private fun probe(): Boolean {
        val targets = listOf(
            "api-v2.soundcloud.com" to 443,
            "1.1.1.1" to 443,
        )
        for ((host, port) in targets) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), 2_000)
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }
}

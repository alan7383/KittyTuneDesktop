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
 * Whether the app has a working network connection.
 *
 * The probe loop is cancellable and gated on [AppBootstrap.windowVisible]: when the window is hidden
 * (minimised to tray), the probe is skipped, so the app doesn't open TCP sockets to `1.1.1.1:443`
 * every 10–30 seconds while nobody is looking. The previous `while(true) { probe(); delay() }` ran
 * forever, even when the app was idle in the tray (audit Phase 4 CPU/network gain).
 */
object NetworkMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> get() = _isOnline

    private var probeJob: kotlinx.coroutines.Job? = null

    fun start() {
        if (probeJob?.isActive == true) return
        probeJob = scope.launch {
            while (true) {
                // Skip the probe while the window is hidden — nobody is looking at the online state
                // while the app is in the tray. The delay still runs so the loop wakes periodically
                // and picks up a visibility change within 10–30 s.
                if (com.alananasss.kittytune.AppBootstrap.windowVisible) {
                    _isOnline.value = probe()
                }
                delay(if (_isOnline.value) 30_000 else 10_000)
            }
        }
    }

    /** Cancels the probe loop. Currently unused in production, but available for testing. */
    fun stop() {
        probeJob?.cancel()
        probeJob = null
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

package com.alananasss.kittytune.data.theme

import androidx.compose.ui.graphics.Color
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manages detection and real-time live synchronization with the Windows OS Accent Color.
 */
object WindowsThemeManager {
    private val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")

    private val _accentColor = MutableStateFlow<Color?>(null)
    val accentColor: StateFlow<Color?> = _accentColor.asStateFlow()

    private var pollingJob: Job? = null
    
    fun startWatching() {
        if (!isWindows || pollingJob != null) return
        
        // Initial load
        reloadColor()
        
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                reloadColor()
                delay(2000L) // Check every 2 seconds
            }
        }
    }
    
    fun stopWatching() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun reloadColor() {
        if (!isWindows) return
        try {
            val palette = Advapi32Util.registryGetBinaryValue(
                WinReg.HKEY_CURRENT_USER,
                "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Accent",
                "AccentPalette"
            )
            val r = palette[0].toInt() and 0xFF
            val g = palette[1].toInt() and 0xFF
            val b = palette[2].toInt() and 0xFF
            
            val newColor = Color(r, g, b, 0xFF)

            if (_accentColor.value != newColor) {
                _accentColor.value = newColor
            }
        } catch (t: Throwable) {
            // Fallback to ColorizationColor if AccentPalette fails
            try {
                val colorInt = Advapi32Util.registryGetIntValue(
                    WinReg.HKEY_CURRENT_USER,
                    "Software\\Microsoft\\Windows\\DWM",
                    "ColorizationColor"
                )
                val r = (colorInt shr 16) and 0xFF
                val g = (colorInt shr 8) and 0xFF
                val b = colorInt and 0xFF
                val newColor = Color(r, g, b, 0xFF)
                if (_accentColor.value != newColor) _accentColor.value = newColor
            } catch (ex: Throwable) {}
        }
    }
}

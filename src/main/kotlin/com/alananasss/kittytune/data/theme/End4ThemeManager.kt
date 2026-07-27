package com.alananasss.kittytune.data.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Manages detection, color extraction, and real-time live synchronization
 * with end4's Hyprland dotfiles (matugen / quickshell generated Material You colors).
 */
object End4ThemeManager {

    private val userHome: String = System.getProperty("user.home") ?: ""

    // File paths associated with end4 dotfiles
    val colorsFile: File = File(userHome, ".local/state/quickshell/user/generated/colors.json")
    private val quickshellIiDir: File = File(userHome, ".config/quickshell/ii")
    private val dotsHyprlandDir: File = File(userHome, "dots-hyprland")
    private val matugenConfigDir: File = File(userHome, ".config/matugen")

    private val _colorsMap = MutableStateFlow<Map<String, Color>>(emptyMap())
    val colorsMap: StateFlow<Map<String, Color>> = _colorsMap.asStateFlow()

    private var watchingStarted = false
    private var lastModifiedTime: Long = 0L

    /**
     * Checks if end4 dotfiles are installed on this system.
     * True if matugen config AND (quickshell ii OR generated colors OR dots-hyprland repo) exist.
     */
    fun isInstalled(): Boolean {
        if (userHome.isEmpty()) return false
        val hasMatugen = matugenConfigDir.isDirectory
        val hasEnd4Artifacts = quickshellIiDir.isDirectory || colorsFile.exists() || dotsHyprlandDir.isDirectory
        return hasMatugen && hasEnd4Artifacts
    }

    /**
     * Starts watching colors.json for real-time live color updates when matugen generates new colors.
     */
    fun startWatching() {
        if (watchingStarted) return
        watchingStarted = true

        // Initial load
        reloadColors()

        // Background poller for file modification timestamp changes
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (colorsFile.exists()) {
                    val currentModified = colorsFile.lastModified()
                    if (currentModified != lastModifiedTime) {
                        lastModifiedTime = currentModified
                        reloadColors()
                    }
                }
                delay(1000L)
            }
        }
    }

    fun reloadColors() {
        if (!colorsFile.exists()) return
        runCatching {
            val jsonText = colorsFile.readText()
            val json = JSONObject(jsonText)
            val map = mutableMapOf<String, Color>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val hex = json.optString(key, "")
                parseHexColor(hex)?.let { color ->
                    map[key] = color
                }
            }
            if (map.isNotEmpty()) {
                _colorsMap.value = map
            }
        }
    }

    private fun parseHexColor(hex: String): Color? {
        val cleanHex = hex.trim().removePrefix("#")
        if (cleanHex.length != 6 && cleanHex.length != 8) return null
        return runCatching {
            val colorInt = if (cleanHex.length == 6) {
                (0xFF000000 or cleanHex.toLong(16)).toInt()
            } else {
                cleanHex.toLong(16).toInt()
            }
            Color(colorInt)
        }.getOrNull()
    }

    fun getPrimarySeedColor(): Color? {
        return _colorsMap.value["primary"]
    }

    fun buildColorScheme(fallbackScheme: ColorScheme, pureBlack: Boolean = false): ColorScheme {
        val map = _colorsMap.value
        if (map.isEmpty()) return fallbackScheme

        val bg = if (pureBlack) Color.Black else (map["background"] ?: fallbackScheme.background)
        val surf = if (pureBlack) Color.Black else (map["surface"] ?: fallbackScheme.surface)
        val surfLow = if (pureBlack) Color.Black else (map["surface_container_low"] ?: fallbackScheme.surfaceContainerLow)
        val surfCont = if (pureBlack) Color.Black else (map["surface_container"] ?: fallbackScheme.surfaceContainer)
        val surfHigh = if (pureBlack) Color(0xFF121212) else (map["surface_container_high"] ?: fallbackScheme.surfaceContainerHigh)
        val surfHighest = if (pureBlack) Color(0xFF181818) else (map["surface_container_highest"] ?: fallbackScheme.surfaceContainerHighest)

        return fallbackScheme.copy(
            primary = map["primary"] ?: fallbackScheme.primary,
            onPrimary = map["on_primary"] ?: fallbackScheme.onPrimary,
            primaryContainer = map["primary_container"] ?: fallbackScheme.primaryContainer,
            onPrimaryContainer = map["on_primary_container"] ?: fallbackScheme.onPrimaryContainer,

            secondary = map["secondary"] ?: fallbackScheme.secondary,
            onSecondary = map["on_secondary"] ?: fallbackScheme.onSecondary,
            secondaryContainer = map["secondary_container"] ?: fallbackScheme.secondaryContainer,
            onSecondaryContainer = map["on_secondary_container"] ?: fallbackScheme.onSecondaryContainer,

            tertiary = map["tertiary"] ?: fallbackScheme.tertiary,
            onTertiary = map["on_tertiary"] ?: fallbackScheme.onTertiary,
            tertiaryContainer = map["tertiary_container"] ?: fallbackScheme.tertiaryContainer,
            onTertiaryContainer = map["on_tertiary_container"] ?: fallbackScheme.onTertiaryContainer,

            background = bg,
            onBackground = map["on_background"] ?: fallbackScheme.onBackground,

            surface = surf,
            onSurface = map["on_surface"] ?: fallbackScheme.onSurface,
            surfaceVariant = map["surface_variant"] ?: fallbackScheme.surfaceVariant,
            onSurfaceVariant = map["on_surface_variant"] ?: fallbackScheme.onSurfaceVariant,

            surfaceContainerLowest = map["surface_container_lowest"] ?: fallbackScheme.surfaceContainerLowest,
            surfaceContainerLow = surfLow,
            surfaceContainer = surfCont,
            surfaceContainerHigh = surfHigh,
            surfaceContainerHighest = surfHighest,

            outline = map["outline"] ?: fallbackScheme.outline,
            outlineVariant = map["outline_variant"] ?: fallbackScheme.outlineVariant,

            error = map["error"] ?: fallbackScheme.error,
            onError = map["on_error"] ?: fallbackScheme.onError,
            errorContainer = map["error_container"] ?: fallbackScheme.errorContainer,
            onErrorContainer = map["on_error_container"] ?: fallbackScheme.onErrorContainer,

            inverseSurface = map["inverse_surface"] ?: fallbackScheme.inverseSurface,
            inverseOnSurface = map["inverse_on_surface"] ?: fallbackScheme.inverseOnSurface,
            inversePrimary = map["inverse_primary"] ?: fallbackScheme.inversePrimary,
            scrim = map["scrim"] ?: fallbackScheme.scrim
        )
    }
}

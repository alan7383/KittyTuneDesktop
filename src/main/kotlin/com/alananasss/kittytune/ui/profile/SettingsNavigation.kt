package com.alananasss.kittytune.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Where settings are: a category and the sub-pages opened inside it, outermost first. */
internal data class SettingsPlace(val category: SettingsCategory, val pages: List<SettingsSubPage> = emptyList()) {
    val subPage: SettingsSubPage? get() = pages.lastOrNull()
    val depth: Int get() = pages.size
    val titleKeys: List<String> get() = (listOf(category.titleKey) + pages.map { it.titleKey }).distinct()
    fun parent(): SettingsPlace = copy(pages = pages.dropLast(1))
}

/** The last settings pane, remembered independently of the app's navigation stack. */
internal object SettingsNavigation {
    var current by mutableStateOf(SettingsPlace(SettingsCategory.INTERFACE))
        private set

    fun go(place: SettingsPlace) {
        current = place
    }

    fun up() {
        current = current.parent()
    }
}

/** The app screen that preceded a stack of settings pages, when one pop would stay in settings. */
internal fun settingsBackDestination(routes: List<String>): String? {
    val lastAppScreen = routes.indexOfLast { !isSettingsRoute(it) }
    return routes.getOrNull(lastAppScreen)?.takeIf { routes.lastIndex - lastAppScreen > 1 }
}

internal fun isSettingsRoute(route: String?): Boolean = route != null && route in settingsRoutes

private val settingsRoutes = setOf(
    "settings", "sync_settings", "proxy_settings", "color_palette", "provider_order",
    "qobuz_settings", "tidal_settings", "deezer_settings", "music_import",
)

package com.alananasss.kittytune.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.data.local.PlayerPreferences

/**
 * Everything the sidebar can list below Home, keyed by what the preferences store. One catalogue, so the
 * sidebar and the settings page that arranges it always agree on labels and icons.
 */
internal object SidebarDestinations {

    class Destination(
        val key: String,
        val route: String,
        val labelKey: String,
        val iconSelected: ImageVector,
        val iconUnselected: ImageVector = iconSelected,
    )

    val ALL: Map<String, Destination> = listOf(
        Destination(PlayerPreferences.SIDEBAR_NAV_HOME, "home", "nav_home", Icons.Filled.Home, Icons.Outlined.Home),
        Destination(PlayerPreferences.SIDEBAR_NAV_SEARCH, "home", "nav_search", Icons.Filled.Search, Icons.Outlined.Search),
        Destination(PlayerPreferences.SIDEBAR_NAV_FEED, "feed", "nav_feed", Icons.Filled.DynamicFeed, Icons.Outlined.DynamicFeed),
        Destination(PlayerPreferences.SIDEBAR_NAV_EXPLORE, "genres", "explorer_title", Icons.Filled.Explore, Icons.Outlined.Explore),
        Destination(PlayerPreferences.SIDEBAR_NAV_RECOGNITION, "recognition", "pref_bottom_menu_fab_recognition", Icons.Filled.Mic, Icons.Outlined.Mic),
        Destination(PlayerPreferences.SIDEBAR_NAV_SYNC, "sync_settings", "sync_title", Icons.Filled.Devices, Icons.Outlined.Devices),
        Destination(PlayerPreferences.SIDEBAR_NAV_STATS, "listening_stats", "listening_stats_title", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
        Destination(PlayerPreferences.SIDEBAR_NAV_HISTORY, "history", "history_title", Icons.Filled.WatchLater, Icons.Outlined.WatchLater),
        Destination(PlayerPreferences.SIDEBAR_NAV_SETTINGS, "settings", "profile_menu_settings", Icons.Filled.Settings, Icons.Outlined.Settings),
).associateBy { it.key }
}

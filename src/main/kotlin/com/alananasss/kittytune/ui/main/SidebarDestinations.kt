package com.alananasss.kittytune.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
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
        Destination(PlayerPreferences.SIDEBAR_NAV_FEED, "feed", "nav_feed", Icons.Rounded.DynamicFeed),
        Destination(PlayerPreferences.SIDEBAR_NAV_EXPLORE, "genres", "explorer_title", Icons.Filled.Explore, Icons.Outlined.Explore),
        Destination(PlayerPreferences.SIDEBAR_NAV_RECOGNITION, "recognition", "pref_bottom_menu_fab_recognition", Icons.Rounded.GraphicEq),
        Destination(PlayerPreferences.SIDEBAR_NAV_SYNC, "sync_settings", "sync_title", Icons.Rounded.Devices),
        Destination(PlayerPreferences.SIDEBAR_NAV_STATS, "listening_stats", "listening_stats_title", Icons.Rounded.BarChart),
        Destination(PlayerPreferences.SIDEBAR_NAV_HISTORY, "history", "history_title", Icons.Rounded.History),
        Destination(PlayerPreferences.SIDEBAR_NAV_SETTINGS, "settings", "profile_menu_settings", Icons.Filled.Settings, Icons.Outlined.Settings),
    ).associateBy { it.key }
}

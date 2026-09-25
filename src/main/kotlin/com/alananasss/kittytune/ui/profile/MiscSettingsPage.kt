package com.alananasss.kittytune.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.runtime.*
import androidx.navigation.NavController
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.StartDestination
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.player.PlayerViewModel

/** Settings → Other: the language, how the app starts and updates, and Discord. */
@Composable
fun MiscSettingsPage(navController: NavController, playerViewModel: PlayerViewModel) {
    val prefs = remember { PlayerPreferences() }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var startDestination by remember { mutableStateOf(prefs.getStartDestination()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }

    val languages = listOf(
        AppLanguage.SYSTEM to str("theme_system"),
        AppLanguage.ENGLISH to str("lang_english"),
        AppLanguage.RUSSIAN to str("lang_russian"),
        AppLanguage.FRENCH to str("lang_french"),
        AppLanguage.GERMAN to str("lang_german"),
        AppLanguage.HUNGARIAN to str("lang_hungarian"),
        AppLanguage.VIETNAMESE to str("lang_vietnamese"),
    )
    val startDestinations = listOf(
        StartDestination.HOME to str("nav_home"),
        StartDestination.LIBRARY to str("nav_library"),
    )

    if (showLanguageDialog) {
        ChoiceDialog(
            title = str("pref_language"),
            options = languages,
            selected = appLanguage,
            onSelect = {
                prefs.setAppLanguage(it)
                appLanguage = it
                Strings.appLanguage = it.code
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (showStartDialog) {
        ChoiceDialog(
            title = str("pref_start_screen"),
            options = startDestinations,
            selected = startDestination,
            onSelect = {
                startDestination = it
                prefs.setStartDestination(it)
            },
            onDismiss = { showStartDialog = false },
        )
    }

    SettingsGroup(
        title = str("settings_cat_general"),
        items = listOf(
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_language"),
                    subtitle = languages.first { it.first == appLanguage }.second,
                    icon = Icons.Rounded.Translate,
                    onClick = { showLanguageDialog = true },
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_start_screen"),
                    subtitle = startDestinations.first { it.first == startDestination }.second,
                    icon = Icons.Rounded.Home,
                    onClick = { showStartDialog = true },
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_auto_update"),
                    subtitle = str("pref_auto_update_subtitle"),
                    icon = Icons.Rounded.SystemUpdate,
                    hasSwitch = true,
                    switchState = autoUpdate,
                    onSwitchChange = {
                        autoUpdate = it
                        prefs.setAutoUpdateEnabled(it)
                    },
                )
            },
        ),
    )

    MainCategoryTitle(str("pref_discord_title"), Icons.Rounded.Forum)
    DiscordSettingsScreen(
        onBackClick = null,
        onNavigateToLogin = { navController.navigate("discord_login") },
        playerViewModel = playerViewModel,
    )
}

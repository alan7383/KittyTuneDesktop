package com.alananasss.kittytune.ui.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import com.alananasss.kittytune.ui.common.Discord
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.runtime.*
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
fun MiscSettingsPage(playerViewModel: PlayerViewModel) {
    MiscGeneralPage()
    MainCategoryTitle(str("pref_discord_title"), Icons.Rounded.Discord)
    MiscDiscordPage(playerViewModel)
}

/**
 * Other → General: language selection, start screen, and auto-update toggle.
 */
@Composable
fun MiscGeneralPage() {
    val prefs = remember { PlayerPreferences() }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var startDestination by remember { mutableStateOf(prefs.getStartDestination()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var verifiedFilterEnabled by remember { mutableStateOf(prefs.getVerifiedFilterEnabled()) }
    var showCodecOnPlayer by remember { mutableStateOf(prefs.getShowCodecOnPlayer()) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showStartDialog by remember { mutableStateOf(false) }

    val languages = listOf(
        AppLanguage.SYSTEM,
        AppLanguage.ENGLISH,
        AppLanguage.RUSSIAN,
        AppLanguage.FRENCH,
        AppLanguage.GERMAN,
        AppLanguage.HUNGARIAN,
        AppLanguage.VIETNAMESE,
        AppLanguage.TURKISH,
        AppLanguage.BRAZILIAN_PORTUGUESE,
    ).map { it to str(it.labelKey) }
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
                    title = str("onboard_replay"),
                    subtitle = str("onboard_replay_body"),
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = { com.alananasss.kittytune.ui.setup.SetupLauncher.requested = true },
                )
            },
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
                    highlightKey = "pref_auto_update",
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_verified_filter"),
                    subtitle = str("pref_verified_filter_sub"),
                    icon = Icons.Rounded.Verified,
                    hasSwitch = true,
                    switchState = verifiedFilterEnabled,
                    onSwitchChange = {
                        verifiedFilterEnabled = it
                        prefs.setVerifiedFilterEnabled(it)
                    },
                    highlightKey = "pref_verified_filter",
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_show_codec_on_player"),
                    subtitle = str("pref_show_codec_on_player_sub"),
                    icon = Icons.Rounded.GraphicEq,
                    hasSwitch = true,
                    switchState = showCodecOnPlayer,
                    onSwitchChange = {
                        showCodecOnPlayer = it
                        prefs.setShowCodecOnPlayer(it)
                    },
                    highlightKey = "pref_show_codec_on_player",
                )
            },
        ),
    )
}

/**
 * Other → Discord: local Rich Presence settings.
 */
@Composable
fun MiscDiscordPage(playerViewModel: PlayerViewModel) {
    DiscordSettingsScreen(playerViewModel)
}

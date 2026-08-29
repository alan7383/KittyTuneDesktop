package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.launch

import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.ui.graphics.vector.ImageVector
@Composable
fun SettingsScreen(
    navController: NavController,
    onBackClick: (() -> Unit)? = null,
    playerViewModel: PlayerViewModel
) {
    // Restored after the sections have hydrated, not before — see [rememberRestorableScrollState]
    // for why the framework's own saver loses the position on a page built this way (issue #33).
    val scrollState = com.alananasss.kittytune.ui.common.rememberRestorableScrollState()
    SettingsScaffold(
        title = str("settings_title"),
        onBackClick = onBackClick,
        scrollState = scrollState
    ) { innerPadding ->
        com.alananasss.kittytune.ui.common.ScrollableColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            hideScrollbar = true,
            contentPadding = innerPadding
        ) {
            MainCategoryTitle(str("pref_appearance_title"), Icons.Rounded.Palette)
            AppearanceSettingsScreen(onNavigateToColors = { navController.navigate("color_palette") }, onBackClick = null)
            Spacer(Modifier.height(32.dp))
            
            MainCategoryTitle(str("music_import_title"), Icons.Rounded.ImportExport)
            
            SettingsGroup(
                title = str("music_import_title"),
                items = listOf(
                    { shape ->
                        SettingsItem(
                            shape = shape,
                            title = str("music_import_title"),
                            subtitle = str("music_import_settings_subtitle"),
                            icon = Icons.Rounded.ImportExport,
                            onClick = { navController.navigate("music_import") }
                        )
                    }
                )
            )
            
            Spacer(Modifier.height(32.dp))

            MainCategoryTitle(str("pref_audio_title"), Icons.Rounded.GraphicEq)
            AudioSettingsScreen(onBackClick = null, playerViewModel = playerViewModel)
            Spacer(Modifier.height(32.dp))
            
            MainCategoryTitle(str("pref_lyrics_title"), Icons.Rounded.TextSnippet)
            LyricsSettingsScreen(onBackClick = null, playerViewModel = playerViewModel)
            Spacer(Modifier.height(32.dp))
            
            // Mid-page: it was at the very bottom, which for a feature nobody knows exists yet is the same
            // as not shipping it, and it is also not the first thing anyone opens settings for. The row says
            // what is actually happening rather than naming the listener switch, so the state is legible
            // without opening it. The sidebar entry is what makes it reachable in one click.
            MainCategoryTitle(str("sync_title"), Icons.Rounded.Sync)
            SettingsGroup(
                title = str("sync_title"),
                items = listOf(
                    { shape ->
                        val devices = com.alananasss.kittytune.data.sync.SyncPeers.all()
                        SettingsItem(
                            shape = shape,
                            title = str("sync_title"),
                            subtitle = if (devices.isEmpty()) {
                                str("sync_state_not_paired_sub")
                            } else {
                                str("sync_state_in_step")
                            },
                            icon = Icons.Rounded.Devices,
                            onClick = { navController.navigate("sync_settings") }
                        )
                    }
                )
            )
            Spacer(Modifier.height(32.dp))

            MainCategoryTitle(str("pref_discord_title"), Icons.Rounded.Forum)
            DiscordSettingsScreen(onBackClick = null, onNavigateToLogin = { navController.navigate("discord_login") }, playerViewModel = playerViewModel)
            Spacer(Modifier.height(32.dp))

            MainCategoryTitle(str("pref_storage_title"), Icons.Rounded.Storage)
            StorageSettingsScreen()
            Spacer(Modifier.height(32.dp))
            
            MainCategoryTitle(str("pref_local_title"), Icons.Filled.SdStorage)
            LocalMediaSettingsScreen(onBackClick = null)
            Spacer(Modifier.height(32.dp))

            MainCategoryTitle(str("pref_proxy_title"), Icons.Rounded.Dns)
            SettingsGroup(
                title = str("pref_proxy_title"),
                items = listOf(
                    { shape ->
                        val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences() }
                        val isProxyEnabled = prefs.getProxyEnabled()
                        val proxySubtitle = if (isProxyEnabled) {
                            str("proxy_status_enabled", prefs.getProxyType(), prefs.getProxyHost().ifBlank { "127.0.0.1" }, prefs.getProxyPort())
                        } else {
                            str("proxy_status_disabled")
                        }
                        SettingsItem(
                            shape = shape,
                            title = str("proxy_settings_title"),
                            subtitle = proxySubtitle,
                            icon = Icons.Rounded.VpnLock,
                            onClick = { navController.navigate("proxy_settings") }
                        )
                    }
                )
            )
            Spacer(Modifier.height(32.dp))

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun MainCategoryTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

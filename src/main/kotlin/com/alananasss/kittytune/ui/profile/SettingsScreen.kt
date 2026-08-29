package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
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

            // Where the Yandex token goes.
            //
            // Under the import section rather than under a "sources" heading of its own, because that is all it
            // is: a credential the user brings so their catalogue can be read. Nothing here unlocks playback —
            // Yandex serves audio only through a signing scheme lifted out of their own client, which is not
            // something to reproduce, so a Yandex hit always plays from wherever else the song exists
            // (issue #33).
            var showYandexTokenDialog by remember { mutableStateOf(false) }
            val yandexConnected = com.alananasss.kittytune.data.yandex.YandexMusicClient.isConnected
            SettingsGroup(
                title = str("pref_yandex_token"),
                items = listOf(
                    { shape ->
                        SettingsItem(
                            shape = shape,
                            title = str("pref_yandex_token"),
                            subtitle = if (yandexConnected) str("pref_yandex_token_sub")
                            else str("yandex_not_connected"),
                            icon = Icons.Rounded.Key,
                            onClick = { showYandexTokenDialog = true },
                        )
                    }
                )
            )
            if (showYandexTokenDialog) {
                YandexTokenDialog(onDismiss = { showYandexTokenDialog = false })
            }

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

/**
 * Where the Yandex Music token is pasted (issue #33).
 *
 * ## Why a paste box and not a sign-in button
 *
 * "Possibilité de se connecter via le web aussi ?" — and the honest answer is not yet. A browser sign-in needs
 * a registered Yandex application's id and secret, and the only ones lying around belong to other projects.
 * Shipping somebody else's OAuth client is the same objection as shipping Apple's developer token out of their
 * APK, so this asks for the token instead, which is what their own API documentation describes and what every
 * third-party client does.
 *
 * The link goes to that documentation. If a Yandex application is ever registered for KittyTune, filling in
 * `YandexMusicClient.OAUTH_CLIENT_ID` turns this dialog into a sign-in button and nothing else has to change.
 *
 * The field is masked while it is at rest and legible while it is being typed, because a token is a password
 * that people paste and then need to check they pasted correctly.
 */
@Composable
private fun YandexTokenDialog(onDismiss: () -> Unit) {
    val client = com.alananasss.kittytune.data.yandex.YandexMusicClient
    var value by remember { mutableStateOf(client.token.orEmpty()) }
    var reveal by remember { mutableStateOf(false) }

    com.alananasss.kittytune.core.BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(str("pref_yandex_token"), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    str("pref_yandex_token_sub"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.trim() },
                    singleLine = true,
                    label = { Text(str("pref_yandex_token")) },
                    visualTransformation =
                        if (reveal) androidx.compose.ui.text.input.VisualTransformation.None
                        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        runCatching {
                            java.awt.Desktop.getDesktop()
                                .browse(java.net.URI(com.alananasss.kittytune.data.yandex.YandexMusicClient.TOKEN_HELP_URL))
                        }
                    }
                ) { Text(str("pref_yandex_token_get")) }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = {
                            client.token = null
                            value = ""
                            onDismiss()
                        }
                    ) { Text(str("pref_yandex_token_clear")) }
                    TextButton(
                        onClick = {
                            client.token = value
                            onDismiss()
                        }
                    ) { Text(str("btn_save")) }
                }
            }
        }
    }
}

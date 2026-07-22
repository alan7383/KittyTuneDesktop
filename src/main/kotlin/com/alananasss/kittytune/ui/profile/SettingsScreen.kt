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
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    var selectedCategory by remember { mutableStateOf("appearance") }

    Row(modifier = Modifier.fillMaxSize()) {
        // Navigation Sidebar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.width(300.dp).fillMaxHeight()
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp, top = 12.dp, start = 8.dp)) {
                    Text(str("settings_title"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                NavigationDrawerItem(
                    label = { Text(str("pref_appearance_title")) },
                    icon = { Icon(Icons.Rounded.Palette, null) },
                    selected = selectedCategory == "appearance",
                    onClick = { selectedCategory = "appearance" },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text(str("pref_audio_title")) },
                    icon = { Icon(Icons.Rounded.GraphicEq, null) },
                    selected = selectedCategory == "audio",
                    onClick = { selectedCategory = "audio" },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text(str("pref_lyrics_title")) },
                    icon = { Icon(Icons.Rounded.TextSnippet, null) },
                    selected = selectedCategory == "lyrics",
                    onClick = { selectedCategory = "lyrics" },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text(str("pref_discord_title")) },
                    icon = { Icon(Icons.Rounded.Forum, null) },
                    selected = selectedCategory == "discord",
                    onClick = { selectedCategory = "discord" },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text(str("pref_local_title")) },
                    icon = { Icon(Icons.Filled.SdStorage, null) },
                    selected = selectedCategory == "local",
                    onClick = { selectedCategory = "local" },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text(str("pref_updates_title")) },
                    icon = { Icon(Icons.Rounded.SystemUpdate, null) },
                    selected = selectedCategory == "update",
                    onClick = { selectedCategory = "update" },
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        VerticalDivider()

        // Content
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            when (selectedCategory) {
                "appearance" -> AppearanceSettingsScreen(onNavigateToColors = { navController.navigate("color_palette") }, onBackClick = null)
                "audio" -> AudioSettingsScreen(onBackClick = null, onNavigateToDrmExplanation = { navController.navigate("drm_explanation") }, playerViewModel = playerViewModel)
                "lyrics" -> LyricsSettingsScreen(onBackClick = null, playerViewModel = playerViewModel)
                "discord" -> DiscordSettingsScreen(onBackClick = null, onNavigateToLogin = { navController.navigate("discord_login") }, playerViewModel = playerViewModel)
                "local" -> LocalMediaSettingsScreen(onBackClick = null)
                "update" -> AboutUpdateSettingsScreen()
            }
        }
    }
}

@Composable
fun AboutUpdateSettingsScreen() {
    val status by com.alananasss.kittytune.data.UpdateManager.status.collectAsState()
    val progress by com.alananasss.kittytune.data.UpdateManager.downloadProgress.collectAsState()
    val releaseInfo = com.alananasss.kittytune.data.UpdateManager.releaseInfo
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(str("update_about_title"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(str("update_current_version", com.alananasss.kittytune.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyLarge)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (status) {
                    com.alananasss.kittytune.data.UpdateStatus.IDLE -> {
                        Text(str("update_idle_desc"))
                        Button(onClick = { scope.launch { com.alananasss.kittytune.data.UpdateManager.checkForUpdate(isManual = true) } }) {
                            Text(str("update_btn_check"))
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.CHECKING -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(str("update_checking"))
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.AVAILABLE -> {
                        Text(str("update_available", releaseInfo?.tagName ?: ""), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        if (!releaseInfo?.body.isNullOrEmpty()) {
                            Text("${str("update_release_notes")}\n${releaseInfo?.body}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(onClick = { scope.launch { com.alananasss.kittytune.data.UpdateManager.downloadUpdate() } }) {
                            Text(str("update_btn_download"))
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.DOWNLOADING -> {
                        Text(str("update_downloading", (progress * 100).toInt()))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    com.alananasss.kittytune.data.UpdateStatus.READY_TO_INSTALL -> {
                        Text(str("update_ready"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Button(onClick = { com.alananasss.kittytune.data.UpdateManager.installUpdate() }) {
                            Text(str("update_btn_install"))
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.NO_UPDATE -> {
                        Text(str("update_no_update"), color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(onClick = { scope.launch { com.alananasss.kittytune.data.UpdateManager.checkForUpdate(isManual = true) } }) {
                            Text(str("update_btn_recheck"))
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.ERROR -> {
                        Text(str("update_error"), color = MaterialTheme.colorScheme.error)
                        Button(onClick = { scope.launch { com.alananasss.kittytune.data.UpdateManager.checkForUpdate(isManual = true) } }) {
                            Text(str("update_btn_retry"))
                        }
                    }
                }
            }
        }
    }
}

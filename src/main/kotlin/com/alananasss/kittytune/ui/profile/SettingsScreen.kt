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

import com.alananasss.kittytune.ui.common.SettingsScaffold
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.vector.ImageVector
@Composable
fun SettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    SettingsScaffold(
        title = str("settings_title"),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            item { MainCategoryTitle(str("pref_appearance_title"), Icons.Rounded.Palette) }
            item { AppearanceSettingsScreen(onNavigateToColors = { navController.navigate("color_palette") }, onBackClick = null) }
            item { Spacer(Modifier.height(32.dp)) }
            
            item { MainCategoryTitle(str("pref_audio_title"), Icons.Rounded.GraphicEq) }
            item { AudioSettingsScreen(onBackClick = null, playerViewModel = playerViewModel) }
            item { Spacer(Modifier.height(32.dp)) }
            
            item { MainCategoryTitle(str("pref_lyrics_title"), Icons.Rounded.TextSnippet) }
            item { LyricsSettingsScreen(onBackClick = null, playerViewModel = playerViewModel) }
            item { Spacer(Modifier.height(32.dp)) }
            
            item { MainCategoryTitle(str("pref_discord_title"), Icons.Rounded.Forum) }
            item { DiscordSettingsScreen(onBackClick = null, onNavigateToLogin = { navController.navigate("discord_login") }, playerViewModel = playerViewModel) }
            item { Spacer(Modifier.height(32.dp)) }
            
            item { MainCategoryTitle(str("pref_local_title"), Icons.Filled.SdStorage) }
            item { LocalMediaSettingsScreen(onBackClick = null) }
            item { Spacer(Modifier.height(32.dp)) }
            
            item { MainCategoryTitle(str("pref_updates_title"), Icons.Rounded.SystemUpdate) }
            item { AboutUpdateSettingsScreen() }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { scope.launch { com.alananasss.kittytune.data.UpdateManager.checkForUpdate(isManual = true) } }) {
                                Text(str("update_btn_check"))
                            }
                            OutlinedButton(onClick = { com.alananasss.kittytune.data.UpdateManager.testLocalInstall() }) {
                                Text("Test Local Update")
                            }
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.CHECKING -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(str("update_checking"))
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.AVAILABLE -> {
                        Text(str("update_available", releaseInfo?.versionName ?: releaseInfo?.tagName ?: ""), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                    com.alananasss.kittytune.data.UpdateStatus.PAUSED -> {
                        Text(str("update_paused_title"), color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { scope.launch { com.alananasss.kittytune.data.UpdateManager.downloadUpdate() } }) {
                            Text(str("update_btn_resume"))
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.INSTALLING -> {
                        Text(str("update_installing_title"), color = MaterialTheme.colorScheme.primary)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    com.alananasss.kittytune.data.UpdateStatus.WAITING_FOR_AUTH -> {
                        Text(str("update_waiting_auth_title"), color = MaterialTheme.colorScheme.primary)
                        Text(str("update_waiting_auth_step"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    com.alananasss.kittytune.data.UpdateStatus.MULTIPLE_INSTANCES -> {
                        Text(str("update_multi_instance_title"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { com.alananasss.kittytune.data.UpdateManager.killInstancesAndContinue() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(str("update_btn_close_instances"))
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.AUTH_FAILED -> {
                        Text(str("update_auth_failed_title"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(str("update_auth_failed_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Button(onClick = { com.alananasss.kittytune.data.UpdateManager.retryInstall() }) {
                            Text(str("update_btn_retry_auth"))
                        }
                    }
                    com.alananasss.kittytune.data.UpdateStatus.READY_TO_INSTALL -> {
                        Text(str("update_ready"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Button(onClick = { com.alananasss.kittytune.data.UpdateManager.restartApp() }) {
                            Text(str("update_btn_restart"))
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

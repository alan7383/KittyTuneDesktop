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
            }
        }
    }
}

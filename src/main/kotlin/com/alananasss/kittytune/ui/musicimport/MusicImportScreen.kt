package com.alananasss.kittytune.ui.musicimport

import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.stringResource
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.musicimport.MusicApi
import com.alananasss.kittytune.data.musicimport.MusicImportCoordinator
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold

@Composable
fun MusicImportScreen(
    onBackClick: (() -> Unit)? = null,
    onPlatformSelected: (String) -> Unit,
    onAuthRequested: (String) -> Unit,
    onLoginClick: () -> Unit = {},
    viewModel: MusicImportViewModel = viewModel { MusicImportViewModel(AppInstance.application) }
) {
    val isLoggedIn = TokenManager.hasAccessToken() && !TokenManager.isGuestMode()

    LaunchedEffect(Unit) {
        viewModel.refreshConnection()
    }

    val pendingAuth by MusicImportCoordinator.pendingAuth.collectAsState()

    LaunchedEffect(pendingAuth) {
        val auth = pendingAuth
        if (auth != null) {
            val provider = auth.integration?.type
            viewModel.markConnecting(false)
            viewModel.checkPendingAuth()
            if (provider != null) {
                onPlatformSelected(provider)
            }
        }
    }

    val scrollState = androidx.compose.foundation.rememberScrollState()
    SettingsScaffold(
        title = stringResource(R.string.music_import_title),
        onBackClick = onBackClick,
        scrollState = scrollState
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (!isLoggedIn) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.music_import_login_required),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onLoginClick,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.profile_menu_login),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else {
                com.alananasss.kittytune.ui.common.ScrollableColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState,
                    hideScrollbar = true,
                    contentPadding = innerPadding
                ) {
                    Text(
                        text = stringResource(R.string.music_import_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                    
                    SettingsGroup(
                        title = stringResource(R.string.music_import_platforms_header),
                        items = viewModel.platforms.map { platform ->
                            { shape ->
                                val visual = platform.visual()
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(platform.labelRes()),
                                    subtitle = if (viewModel.isConnected(platform)) {
                                        stringResource(R.string.music_import_connected)
                                    } else {
                                        stringResource(R.string.music_import_connect)
                                    },
                                    icon = visual.icon,
                                    trailingText = if (viewModel.isConnected(platform)) {
                                        stringResource(R.string.music_import_manage)
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        if (viewModel.isConnected(platform)) {
                                            onPlatformSelected(platform.providerName)
                                        } else {
                                            onAuthRequested(platform.providerName)
                                        }
                                    }
                                )
                            }
                        }
                    )

                    Text(
                        text = stringResource(R.string.music_import_footnote),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                    
                    Spacer(Modifier.height(180.dp))
                }
            }
        }
    }

}

fun MusicApi.labelRes(): String = when (this) {
    MusicApi.SPOTIFY -> R.string.music_provider_spotify
    MusicApi.APPLE_MUSIC -> R.string.music_provider_apple_music
    MusicApi.YOUTUBE_MUSIC -> R.string.music_provider_youtube_music
    MusicApi.DEEZER -> R.string.music_provider_deezer
    MusicApi.TIDAL -> R.string.music_provider_tidal
    MusicApi.AMAZON_MUSIC -> R.string.music_provider_amazon_music
    MusicApi.BOOMPLAY -> R.string.music_provider_boomplay
}

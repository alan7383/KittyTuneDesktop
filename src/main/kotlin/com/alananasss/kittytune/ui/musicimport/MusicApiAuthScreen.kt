@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.musicimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.stringResource
import com.alananasss.kittytune.data.musicimport.MusicApi
import com.alananasss.kittytune.data.musicimport.MusicImportCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen "waiting for browser" state shown while connecting a music platform,
 * mirroring the SoundCloud OAuth screen but tailored to the selected platform
 * (logo + name), with a "reopen page" button and a back button to cancel.
 */
@Composable
fun MusicApiAuthScreen(
    platform: MusicApi,
    onAuthSuccess: (provider: String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    viewModel: MusicApiAuthViewModel = viewModel { MusicApiAuthViewModel(AppInstance.application) }
) {
    val scope = rememberCoroutineScope()
    val pendingAuth by MusicImportCoordinator.pendingAuth.collectAsState()
    var handledProvider by remember { mutableStateOf<String?>(null) }

    fun launchBrowser() {
        scope.launch {
            withContext(Dispatchers.IO) {
                MusicApiAuthLauncher.launch(platform)
            }
        }
    }

    LaunchedEffect(platform) {
        launchBrowser()
    }

    LaunchedEffect(pendingAuth) {
        val auth = pendingAuth
        if (auth != null) {
            val provider = auth.integration?.type
            if (provider != null && provider != handledProvider) {
                handledProvider = provider
                viewModel.persistAuth(auth)
                onAuthSuccess(provider)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(platform.labelRes()),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        FilledTonalIconButton(
                            onClick = onBackClick,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.btn_cancel)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(
                        R.string.music_import_auth_connecting,
                        stringResource(platform.labelRes())
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.music_import_auth_waiting_browser),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                ContainedLoadingIndicator()
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { launchBrowser() },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.music_import_auth_reopen_browser))
                }
            }
        }
    }
}

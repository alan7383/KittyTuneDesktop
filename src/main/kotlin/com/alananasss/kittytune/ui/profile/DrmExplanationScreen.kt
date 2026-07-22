package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alananasss.kittytune.core.str
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape

@Composable
fun DrmExplanationScreen(
    onBackClick: () -> Unit
) {
    
    val prefs = remember { PlayerPreferences() }
    var downloadDrmEnabled by remember { mutableStateOf(prefs.getDownloadDrmStreamsEnabled()) }

    SettingsScaffold(
        title = str("pref_download_drm"),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsItem(
                        shape = getSettingsShape(1, 0),
                        title = str("pref_download_drm"),
                        subtitle = str("pref_download_drm_sub"),
                        hasSwitch = true,
                        switchState = downloadDrmEnabled,
                        onSwitchChange = { 
                            downloadDrmEnabled = it
                            prefs.setDownloadDrmStreamsEnabled(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .offset(y = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = str("drm_explanation_text"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.4f
                        )
                    }
                }
            }
        }
    }
}

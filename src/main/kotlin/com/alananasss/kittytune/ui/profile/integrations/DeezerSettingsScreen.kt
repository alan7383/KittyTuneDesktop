package com.alananasss.kittytune.ui.profile.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProvider
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality
import com.alananasss.kittytune.audio.providers.deezer.DeezerProxyMode
import com.alananasss.kittytune.audio.providers.deezer.isDeezerCookieConfigured
import com.alananasss.kittytune.audio.providers.deezer.normalizeDeezerCookieInput
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import java.net.URI

@Composable
fun DeezerSettingsScreen(
    onBackClick: () -> Unit
) {
    val prefs = remember { PlayerPreferences() }

    var resolverUrl by remember { mutableStateOf(prefs.getDeezerResolverUrl()) }
    var proxyMode by remember { mutableStateOf(prefs.getDeezerProxyMode()) }
    var proxyUrl by remember { mutableStateOf(prefs.getDeezerProxyUrl()) }
    var audioQuality by remember { mutableStateOf(prefs.getDeezerAudioQuality()) }
    var fastMode by remember { mutableStateOf(prefs.getDeezerFastMode()) }
    var useAccount by remember { mutableStateOf(prefs.getDeezerUseAccount()) }
    var deezerCookie by remember { mutableStateOf(prefs.getDeezerCookie()) }

    var showResolverDialog by remember { mutableStateOf(false) }
    var showProxyModeDialog by remember { mutableStateOf(false) }
    var showCustomProxyDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showCookieDialog by remember { mutableStateOf(false) }

    if (showResolverDialog) {
        var tempUrl by remember { mutableStateOf(resolverUrl) }
        AlertDialog(
            onDismissRequest = { showResolverDialog = false },
            title = { Text(str("deezer_resolver_url")) },
            text = {
                Column {
                    Text(
                        text = str("deezer_resolver_url_helper"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        placeholder = { Text(str("deezer_resolver_url_placeholder")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackTextInput()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = tempUrl.trim().ifBlank { DeezerAudioProvider.DEFAULT_RESOLVER_URL }
                        resolverUrl = cleaned
                        prefs.setDeezerResolverUrl(cleaned)
                        showResolverDialog = false
                    }
                ) {
                    Text(str("btn_ok"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResolverDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    if (showProxyModeDialog) {
        AlertDialog(
            onDismissRequest = { showProxyModeDialog = false },
            title = { Text(str("deezer_proxy_mode")) },
            text = {
                Column {
                    DeezerProxyMode.entries.forEach { mode ->
                        val label = when (mode) {
                            DeezerProxyMode.DIRECT -> str("deezer_proxy_direct")
                            DeezerProxyMode.RENDER -> str("deezer_proxy_render")
                            DeezerProxyMode.CUSTOM -> str("deezer_proxy_custom")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showProxyModeDialog = false
                                    if (mode == DeezerProxyMode.CUSTOM) {
                                        showCustomProxyDialog = true
                                    } else {
                                        proxyMode = mode
                                        prefs.setDeezerProxyMode(mode)
                                        if (mode == DeezerProxyMode.RENDER) {
                                            val renderUrl = DeezerAudioProvider.normalizeProxyUrl(DeezerAudioProvider.RENDER_PROXY_BASE_URL)
                                            proxyUrl = renderUrl
                                            prefs.setDeezerProxyUrl(renderUrl)
                                        } else {
                                            proxyUrl = ""
                                            prefs.setDeezerProxyUrl("")
                                        }
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = proxyMode == mode,
                                onClick = {
                                    showProxyModeDialog = false
                                    if (mode == DeezerProxyMode.CUSTOM) {
                                        showCustomProxyDialog = true
                                    } else {
                                        proxyMode = mode
                                        prefs.setDeezerProxyMode(mode)
                                        if (mode == DeezerProxyMode.RENDER) {
                                            val renderUrl = DeezerAudioProvider.normalizeProxyUrl(DeezerAudioProvider.RENDER_PROXY_BASE_URL)
                                            proxyUrl = renderUrl
                                            prefs.setDeezerProxyUrl(renderUrl)
                                        } else {
                                            proxyUrl = ""
                                            prefs.setDeezerProxyUrl("")
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProxyModeDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    if (showCustomProxyDialog) {
        var tempProxy by remember { mutableStateOf(proxyUrl) }
        AlertDialog(
            onDismissRequest = { showCustomProxyDialog = false },
            title = { Text(str("deezer_proxy_custom_url")) },
            text = {
                Column {
                    Text(
                        text = str("deezer_proxy_url_helper"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempProxy,
                        onValueChange = { tempProxy = it },
                        placeholder = { Text(str("deezer_proxy_url_placeholder")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackTextInput()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = DeezerAudioProvider.normalizeProxyUrl(tempProxy)
                        proxyUrl = cleaned
                        proxyMode = DeezerProxyMode.CUSTOM
                        prefs.setDeezerProxyMode(DeezerProxyMode.CUSTOM)
                        prefs.setDeezerProxyUrl(cleaned)
                        showCustomProxyDialog = false
                    }
                ) {
                    Text(str("btn_ok"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomProxyDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(str("deezer_audio_quality")) },
            text = {
                Column {
                    DeezerAudioQuality.entries.forEach { quality ->
                        val label = when (quality) {
                            DeezerAudioQuality.FLAC -> str("deezer_quality_flac")
                            DeezerAudioQuality.MP3_320 -> str("deezer_quality_mp3_320")
                            DeezerAudioQuality.MP3_128 -> str("deezer_quality_mp3_128")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    audioQuality = quality
                                    prefs.setDeezerAudioQuality(quality)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = audioQuality == quality,
                                onClick = {
                                    audioQuality = quality
                                    prefs.setDeezerAudioQuality(quality)
                                    showQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    if (showCookieDialog) {
        var tempCookie by remember { mutableStateOf(deezerCookie) }
        AlertDialog(
            onDismissRequest = { showCookieDialog = false },
            title = { Text(str("deezer_cookie_title")) },
            text = {
                Column {
                    Text(
                        text = str("deezer_cookie_helper"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tempCookie,
                        onValueChange = { tempCookie = it },
                        placeholder = { Text(str("deezer_cookie_placeholder")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 240.dp)
                            .trackTextInput()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cleaned = normalizeDeezerCookieInput(tempCookie).orEmpty()
                        deezerCookie = cleaned
                        prefs.setDeezerCookie(cleaned)
                        showCookieDialog = false
                    }
                ) {
                    Text(str("btn_ok"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCookieDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    val qualityLabel = when (audioQuality) {
        DeezerAudioQuality.FLAC -> str("deezer_quality_flac")
        DeezerAudioQuality.MP3_320 -> str("deezer_quality_mp3_320")
        DeezerAudioQuality.MP3_128 -> str("deezer_quality_mp3_128")
    }

    val proxyModeLabel = when (proxyMode) {
        DeezerProxyMode.DIRECT -> str("deezer_proxy_direct")
        DeezerProxyMode.RENDER -> str("deezer_proxy_render")
        DeezerProxyMode.CUSTOM -> proxyUrl.ifBlank { str("deezer_proxy_custom") }
    }

    SettingsScaffold(
        title = str("deezer_integration"),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)
        ) {
            item {
                val cookieConfigured = isDeezerCookieConfigured(deezerCookie)
                SettingsGroup(
                    title = str("settings_cat_general"),
                    items = buildList {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("deezer_web_login"),
                                subtitle = if (cookieConfigured) {
                                    str("deezer_cookie_configured")
                                } else {
                                    str("deezer_cookie_not_configured")
                                },
                                icon = Icons.AutoMirrored.Rounded.Login,
                                onClick = {
                                    runCatching {
                                        java.awt.Desktop.getDesktop().browse(URI("https://www.deezer.com"))
                                    }
                                    showCookieDialog = true
                                }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("deezer_cookie_title"),
                                subtitle = if (cookieConfigured) {
                                    str("deezer_cookie_configured")
                                } else {
                                    str("deezer_cookie_not_configured")
                                },
                                icon = Icons.Rounded.Key,
                                onClick = { showCookieDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("deezer_resolver_url"),
                                subtitle = str("deezer_resolver_url_desc", resolverUrl),
                                icon = Icons.Rounded.Link,
                                onClick = { showResolverDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("deezer_proxy_mode"),
                                subtitle = proxyModeLabel,
                                icon = Icons.Rounded.VpnLock,
                                onClick = { showProxyModeDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("deezer_audio_quality"),
                                subtitle = qualityLabel,
                                icon = Icons.Rounded.GraphicEq,
                                onClick = { showQualityDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("deezer_fast_mode"),
                                subtitle = str("deezer_fast_mode_desc"),
                                icon = Icons.Rounded.Speed,
                                hasSwitch = true,
                                switchState = fastMode,
                                onSwitchChange = {
                                    fastMode = it
                                    prefs.setDeezerFastMode(it)
                                }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("deezer_use_account"),
                                subtitle = str("deezer_use_account_desc"),
                                icon = Icons.Rounded.AccountCircle,
                                hasSwitch = true,
                                switchState = useAccount,
                                onSwitchChange = {
                                    useAccount = it
                                    prefs.setDeezerUseAccount(it)
                                }
                            )
                        }
                        if (resolverUrl != DeezerAudioProvider.DEFAULT_RESOLVER_URL) {
                            add { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("deezer_reset_resolver"),
                                    subtitle = DeezerAudioProvider.DEFAULT_RESOLVER_URL,
                                    icon = Icons.Rounded.Restore,
                                    onClick = {
                                        resolverUrl = DeezerAudioProvider.DEFAULT_RESOLVER_URL
                                        prefs.setDeezerResolverUrl(DeezerAudioProvider.DEFAULT_RESOLVER_URL)
                                    }
                                )
                            }
                        }
                        if (proxyMode != DeezerProxyMode.DIRECT) {
                            add { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("deezer_clear_proxy"),
                                    subtitle = str("deezer_proxy_disabled"),
                                    icon = Icons.Rounded.Delete,
                                    onClick = {
                                        proxyMode = DeezerProxyMode.DIRECT
                                        proxyUrl = ""
                                        prefs.setDeezerProxyMode(DeezerProxyMode.DIRECT)
                                        prefs.setDeezerProxyUrl("")
                                    }
                                )
                            }
                        }
                        if (deezerCookie.isNotBlank()) {
                            add { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = str("deezer_clear_cookie"),
                                    subtitle = str("deezer_cookie_configured"),
                                    icon = Icons.Rounded.Delete,
                                    onClick = {
                                        deezerCookie = ""
                                        prefs.setDeezerCookie("")
                                    }
                                )
                            }
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoLabel(text = str("deezer_web_login_desc"))
                    InfoLabel(text = str("deezer_integration_info"))
                }
            }
        }
    }
}

@Composable
private fun InfoLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp, end = 10.dp)
                .size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

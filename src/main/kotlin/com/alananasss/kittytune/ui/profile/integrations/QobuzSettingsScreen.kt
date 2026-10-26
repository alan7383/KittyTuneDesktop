package com.alananasss.kittytune.ui.profile.integrations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider
import com.alananasss.kittytune.core.openUrl
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.ButtonDefaults

@Composable
fun QobuzSettingsScreen(
    onBackClick: () -> Unit
) {
    val prefs = remember { PlayerPreferences() }

    var qobuzCountry by remember { mutableStateOf(prefs.getQobuzCountry()) }
    var qobuzCustomInstances by remember { mutableStateOf(prefs.getQobuzCustomInstances()) }
    var qobuzQuality by remember { mutableStateOf(prefs.getQobuzQuality()) }
    var qobuzEnabled by remember { mutableStateOf(!prefs.isAudioProviderDisabled(com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.QOBUZ)) }
    var qobuzToken by remember { mutableStateOf(prefs.getQobuzUserAuthToken()) }

    var showCountryDialog by remember { mutableStateOf(false) }
    var showInstancesDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var socialProvider by remember { mutableStateOf<String?>(null) }

    // Kept out of the dialog block on purpose: a state declared inside a
    // conditional is forgotten the moment the condition flips, and the login
    // error would vanish with it.
    var accountEmail by remember { mutableStateOf("") }
    var accountPassword by remember { mutableStateOf("") }
    var accountLoading by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<String?>(null) }
    val accountScope = rememberCoroutineScope()

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = str("qobuz_account_login"),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = accountEmail,
                        onValueChange = { accountEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().trackTextInput()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = accountPassword,
                        onValueChange = { accountPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().trackTextInput()
                    )
                    if (accountError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = accountError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            accountError = null
                            socialProvider = "google"
                        }) {
                            Text(str("qobuz_sign_in_google"))
                        }
                        OutlinedButton(onClick = {
                            accountError = null
                            socialProvider = "facebook"
                        }) {
                            Text(str("qobuz_sign_in_facebook"))
                        }
                    }
                    if (socialProvider != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        var pastedToken by remember { mutableStateOf("") }
                        Text(
                            text = str("qobuz_social_token_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            value = pastedToken,
                            onValueChange = { pastedToken = it },
                            label = { Text("Token") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().trackTextInput()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                openUrl(socialSignInUrl(socialProvider!!))
                            }) {
                                Text(str("qobuz_social_open_page"))
                            }
                            TextButton(
                                enabled = pastedToken.isNotBlank(),
                                onClick = {
                                    val token = pastedToken.trim()
                                    val provider = socialProvider
                                    accountScope.launch {
                                        val result = when (provider) {
                                            "google" -> com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
                                                .loginWithGoogle(token)
                                            else -> {
                                                val userId = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
                                                    .fetchFacebookUserId(token)
                                                if (userId == null) null
                                                else com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
                                                    .loginWithFacebook(
                                                        accessToken = token,
                                                        userId = userId,
                                                        expiresAt = 0L,
                                                    )
                                            }
                                        }
                                        if (result != null) {
                                            prefs.setQobuzUserAuthToken(result)
                                            qobuzToken = result
                                            socialProvider = null
                                            pastedToken = ""
                                            showAccountDialog = false
                                        } else {
                                            accountError = str("qobuz_account_login_failed")
                                        }
                                    }
                                }
                            ) {
                                Text(str("qobuz_social_connect"))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(),
                    onClick = {
                        accountLoading = true
                        accountError = null
                        accountScope.launch {
                            val token = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
                                .login(accountEmail, accountPassword)
                            accountLoading = false
                            if (token != null) {
                                prefs.setQobuzUserAuthToken(token)
                                qobuzToken = token
                                showAccountDialog = false
                            } else {
                                accountError = str("qobuz_account_login_failed")
                            }
                        }
                    },
                    enabled = !accountLoading && accountEmail.isNotBlank() && accountPassword.isNotBlank()
                ) {
                    if (accountLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(str("btn_ok"))
                    }
                }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showAccountDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = str("qobuz_account"),
                    textAlign = TextAlign.Center
                )
            },
            text = { Text(str("qobuz_account_desc_connected")) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(),
                    onClick = {
                        prefs.setQobuzUserAuthToken(null)
                        qobuzToken = null
                        showDisconnectDialog = false
                    }
                ) {
                    Text(str("qobuz_account_disconnect"))
                }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showDisconnectDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    if (showCountryDialog) {
        var tempCountry by remember { mutableStateOf(qobuzCountry) }
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = str("qobuz_country"),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = tempCountry,
                        onValueChange = { tempCountry = it.uppercase(Locale.US).take(2) },
                        placeholder = { Text("US") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().trackTextInput()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = str("qobuz_country_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(),
                    onClick = {
                        val finalCountry = tempCountry.trim().uppercase(Locale.US).ifBlank { "US" }
                        qobuzCountry = finalCountry
                        prefs.setQobuzCountry(finalCountry)
                        showCountryDialog = false
                    }
                ) {
                    Text(str("btn_ok"))
                }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showCountryDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    if (showInstancesDialog) {
        var tempInstances by remember { mutableStateOf(qobuzCustomInstances) }
        AlertDialog(
            onDismissRequest = { showInstancesDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = str("qobuz_custom_instances"),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = tempInstances,
                        onValueChange = { tempInstances = it },
                        placeholder = { Text(str("qobuz_custom_instances_placeholder")) },
                        singleLine = false,
                        maxLines = 8,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().trackTextInput()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = str("qobuz_custom_instances_helper"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(),
                    onClick = {
                        val cleaned = tempInstances.trim()
                        qobuzCustomInstances = cleaned
                        prefs.setQobuzCustomInstances(cleaned)
                        showInstancesDialog = false
                    }
                ) {
                    Text(str("btn_ok"))
                }
            },
            dismissButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showInstancesDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = str("qobuz_audio_quality"),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column {
                    listOf(
                        27 to "qobuz_quality_hires_192",
                        7 to "qobuz_quality_hires_96",
                        6 to "qobuz_quality_cd",
                        5 to "qobuz_quality_mp3_320"
                    ).forEach { (code, labelKey) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    qobuzQuality = code
                                    prefs.setQobuzQuality(code)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = qobuzQuality == code,
                                onClick = {
                                    qobuzQuality = code
                                    prefs.setQobuzQuality(code)
                                    showQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = str(labelKey),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { showQualityDialog = false }) {
                    Text(str("btn_cancel"))
                }
            }
        )
    }

    SettingsScaffold(
        title = str("qobuz_integration"),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)
        ) {
            item {
                SettingsGroup(
                    title = str("settings_cat_general"),
                    items = buildList {
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("provider_enabled_title"),
                                subtitle = str("provider_enabled_desc"),
                                icon = Icons.Rounded.PowerSettingsNew,
                                hasSwitch = true,
                                switchState = qobuzEnabled,
                                onSwitchChange = { enabled ->
                                    prefs.setAudioProviderDisabled(com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.QOBUZ, !enabled)
                                    qobuzEnabled = enabled
                                }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("qobuz_country"),
                                subtitle = str("qobuz_country_desc"),
                                icon = Icons.Rounded.Language,
                                onClick = { showCountryDialog = true }
                            )
                        }
                        add { shape ->
                            val instances = qobuzCustomInstances.split("\n").filter { it.isNotBlank() }
                            val desc = if (instances.isEmpty() || qobuzCustomInstances == QobuzAudioProvider.DEFAULT_INSTANCE) {
                                str("qobuz_custom_instances_desc_default")
                            } else {
                                str("qobuz_custom_instances_desc_custom", instances.size)
                            }
                            SettingsItem(
                                shape = shape,
                                title = str("qobuz_custom_instances"),
                                subtitle = desc,
                                icon = Icons.Rounded.Link,
                                onClick = { showInstancesDialog = true }
                            )
                        }
                        add { shape ->
                            val qualityLabel = when (qobuzQuality) {
                                27 -> str("qobuz_quality_hires_192")
                                7 -> str("qobuz_quality_hires_96")
                                6 -> str("qobuz_quality_cd")
                                else -> str("qobuz_quality_mp3_320")
                            }
                            SettingsItem(
                                shape = shape,
                                title = str("qobuz_audio_quality"),
                                subtitle = qualityLabel,
                                icon = Icons.Rounded.GraphicEq,
                                onClick = { showQualityDialog = true }
                            )
                        }
                        add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("qobuz_account"),
                                subtitle = if (qobuzToken != null)
                                    str("qobuz_account_desc_connected")
                                else
                                    str("qobuz_account_desc_disconnected"),
                                icon = Icons.Rounded.Person,
                                onClick = {
                                    if (qobuzToken != null) showDisconnectDialog = true else showAccountDialog = true
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * The provider's own sign-in page. The client ids are the ones the official
 * Qobuz app ships with; Google only accepts a custom-scheme redirect for the
 * app that owns the client id, so a client registered for KittyTune is the way
 * to make that one work.
 */
private fun socialSignInUrl(provider: String): String = when (provider) {
    "google" -> {
        val clientId = "1054464321085-6128809nco2cvu09rj2r8cfacg703d44.apps.googleusercontent.com"
        "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=$clientId" +
            "&redirect_uri=com.googleusercontent.apps.$clientId:/oauth2redirect" +
            "&response_type=id_token" +
            "&scope=openid%20email%20profile" +
            "&nonce=" + java.util.UUID.randomUUID()
    }
    else -> "https://www.facebook.com/v18.0/dialog/oauth" +
        "?client_id=103352876679" +
        "&redirect_uri=fbconnect%3A%2F%2Fcct.com.qobuz.music" +
        "&response_type=token" +
        "&scope=email"
}

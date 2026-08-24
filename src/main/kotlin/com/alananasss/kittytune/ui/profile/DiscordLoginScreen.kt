package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.discord.DiscordRemoteAuth
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.QrCode
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * Discord login by QR code, the same remote-auth handshake the official clients use.
 *
 * This used to embed a JavaFX WebView on discord.com/login and scrape the token out of
 * localStorage. That engine is WebKit 617 with no WebAssembly, and Discord's client needs it,
 * so the page never booted — white, then black. Here nothing renders Discord at all: we do
 * the handshake ourselves and the phone does the authenticating.
 */
@Composable
fun DiscordLoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val prefs = remember { PlayerPreferences() }
    val state by DiscordRemoteAuth.state.collectAsState()

    DisposableEffect(Unit) {
        DiscordRemoteAuth.start()
        onDispose { DiscordRemoteAuth.cancel() }
    }

    LaunchedEffect(state) {
        val current = state
        if (current is DiscordRemoteAuth.State.Success) {
            prefs.setDiscordToken(current.token)
            prefs.setDiscordRpcEnabled(true)
            playerViewModel?.updateDiscordPresence()
            onLoginSuccess()
        }
    }

    SettingsScaffold(title = str("discord_login_title"), onBackClick = onBackClick) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.widthIn(max = 420.dp)
            ) {
                when (val current = state) {
                    is DiscordRemoteAuth.State.AwaitingScan -> {
                        QrPanel(content = current.qrContent)
                        Text(
                            text = str("discord_qr_instructions"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    is DiscordRemoteAuth.State.AwaitingConfirmation -> {
                        AccountPreview(
                            username = current.username,
                            discriminator = current.discriminator,
                            avatarUrl = current.avatarUrl
                        )
                        Text(
                            text = str("discord_qr_confirm_on_phone"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    is DiscordRemoteAuth.State.Expired -> RetryPanel(str("discord_qr_expired"))

                    is DiscordRemoteAuth.State.Failed -> RetryPanel(str("discord_qr_failed"))

                    else -> {
                        CircularWavyProgressIndicator()
                        Text(
                            text = str("discord_qr_connecting"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** The code itself, on a light plate so it scans reliably in dark mode too. */
@Composable
private fun QrPanel(content: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        modifier = Modifier.size(260.dp)
    ) {
        QrCode(
            content = content,
            modifier = Modifier.fillMaxSize().padding(12.dp),
            foreground = Color(0xFF111318),
            background = Color.White
        )
    }
}

@Composable
private fun AccountPreview(username: String, discriminator: String?, avatarUrl: String?) {
    Box(
        modifier = Modifier.size(96.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Text(
        text = if (discriminator.isNullOrBlank()) username else "$username#$discriminator",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun RetryPanel(message: String) {
    Icon(
        Icons.Rounded.QrCode2,
        contentDescription = null,
        modifier = Modifier.size(56.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(4.dp))
    Button(
        onClick = { DiscordRemoteAuth.start() },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(str("discord_qr_retry"))
    }
}

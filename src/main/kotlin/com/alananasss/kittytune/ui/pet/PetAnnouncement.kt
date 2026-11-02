package com.alananasss.kittytune.ui.pet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.str
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown once to people who update to the version with the kitty pet: the cat waves hello (an
 * animation only seen here) and offers to set it up. A bottom sheet on Android, a dialog here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetAnnouncementDialog(onAdopt: () -> Unit, onLater: () -> Unit) {
    val settings = remember { PetConfig.load() }
    BackHandler(onBack = onLater)
    androidx.compose.ui.window.Dialog(onDismissRequest = onLater) {
      Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.widthIn(max = 460.dp),
      ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        str("pet_announce_badge"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PetSprite(
                activity = PetActivity.HELLO,
                settings = settings,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(),
                boopable = true,
                corner = 28
            )
            Spacer(Modifier.height(20.dp))
            Text(
                str("pet_announce_title"),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                str("pet_desk_announce_body"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAdopt,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Rounded.Pets, null)
                Spacer(Modifier.width(8.dp))
                Text(str("pet_announce_adopt"), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onLater, shapes = ButtonDefaults.shapes()) {
                Text(str("pet_announce_later"))
            }
        }
      }
    }
}

/**
 * Shows [PetAnnouncementDialog] once, to people who updated (not fresh installs), and never over the
 * update prompt. "Adopt" opens the pet's settings, where the setup starts.
 */
@Composable
fun PetAnnouncementHost(setupCompletedAtLaunch: Boolean, playerViewModel: com.alananasss.kittytune.ui.player.PlayerViewModel?) {
    var show by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(PetConfig.shouldAnnounce(setupCompletedAtLaunch)) }
    val updateStatus by com.alananasss.kittytune.data.UpdateManager.status.collectAsState()
    val blocked = updateStatus == com.alananasss.kittytune.data.UpdateStatus.AVAILABLE ||
        updateStatus == com.alananasss.kittytune.data.UpdateStatus.DOWNLOADING
    if (!show || blocked) return
    PetAnnouncementDialog(
        onAdopt = {
            PetConfig.markAnnounced()
            show = false
            playerViewModel?.navigateToPlaylistId = "pet_settings"
        },
        onLater = {
            PetConfig.markAnnounced()
            show = false
        },
    )
}

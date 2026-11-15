package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.data.MusicManager

/**
 * The "show codec on player" chip (port of Android PR #83): names what is actually playing, e.g.
 * "OPUS • 160 kbps • 48 kHz", from the stream the decoder opened. Clicking it opens the live audio-path
 * diagnostics. Hidden unless the setting is on, while a track loads, and for a remote device.
 */
@Composable
fun CodecBadge(viewModel: PlayerViewModel, textColor: Color, modifier: Modifier = Modifier) {
    var showDiagnostics by remember { mutableStateOf(false) }
    // Hosted before the early returns: an open sheet outlives the badge hiding while a track loads.
    if (showDiagnostics) {
        AudioPathDiagnosticSheet(visible = true, onDismiss = { showDiagnostics = false }, playerViewModel = viewModel)
    }
    val show by remember { com.alananasss.kittytune.data.local.PlayerPreferences().showCodecOnPlayerFlow() }
        .collectAsState(initial = com.alananasss.kittytune.data.local.PlayerPreferences().getShowCodecOnPlayer())
    if (!show || viewModel.isRemotePlayback) return
    val format by MusicManager.player.currentAudioFormat.collectAsState()
    val text = format?.badgeText().orEmpty()
    if (text.isBlank()) return
    Box(
        modifier = modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable { showDiagnostics = true }
            .background(textColor.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .border(0.5.dp, textColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, fontSize = 10.sp),
            color = textColor.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

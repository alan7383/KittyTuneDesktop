package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.data.MusicManager

/**
 * The "show codec on player" chip (port of Android PR #83): names what is actually playing, e.g.
 * "OPUS • 160 kbps • 48 kHz", from the stream the decoder opened. Clicking it opens the live audio-path
 * diagnostics. Hidden unless the setting is on, while a track loads, and for a remote device.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    FilledTonalButton(
        onClick = { showDiagnostics = true },
        modifier = modifier.height(22.dp).pointerHoverIcon(PointerIcon.Hand),
        shapes = ButtonDefaults.shapes(shape = CircleShape),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = textColor.copy(alpha = 0.08f),
            contentColor = textColor.copy(alpha = 0.8f),
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.alananasss.kittytune.ui.tray

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.FLOATING_MENU_DIVIDER
import com.alananasss.kittytune.ui.common.FLOATING_MENU_ITEM_HEIGHT
import com.alananasss.kittytune.ui.common.FLOATING_MENU_PADDING
import com.alananasss.kittytune.ui.common.FloatingMenuItem
import com.alananasss.kittytune.ui.common.FloatingMenuWindow
import com.alananasss.kittytune.ui.common.pressScale

/**
 * Where and whether the tray's context menu is open. Any tray backend — the AWT icon on Windows and
 * macOS, the SNI item on Linux — reports the pointer here, and [ModernTrayMenuHost] shows the menu.
 */
object TrayMenuState {
    var visible by mutableStateOf(false)
        private set

    /** The pointer, in the screen coordinates AWT reports — the same units Compose places windows in. */
    var x by mutableStateOf(0f)
        private set
    var y by mutableStateOf(0f)
        private set

    fun show(screenX: Int, screenY: Int) {
        x = screenX.toFloat()
        y = screenY.toFloat()
        visible = true
    }

    fun hide() {
        visible = false
    }

    fun toggle(screenX: Int, screenY: Int) {
        if (visible) hide() else show(screenX, screenY)
    }
}

/** What is playing, for the menu's header. Null fields are simply left out. */
data class TrayNowPlaying(
    val title: String?,
    val artist: String?,
    val artworkUrl: String?,
    val isPlaying: Boolean,
)

private val HEADER_HEIGHT = 60.dp

/**
 * The tray's context menu: what is playing with transport controls on top, then the app actions, in
 * the app's own menu style (see [FloatingMenuWindow]).
 */
@Composable
fun ModernTrayMenuHost(
    nowPlaying: TrayNowPlaying?,
    isMiniPlayerVisible: Boolean,
    onShowWindow: () -> Unit,
    onToggleMiniPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExit: () -> Unit,
) {
    if (!TrayMenuState.visible) return

    FloatingMenuWindow(
        anchorXDp = TrayMenuState.x,
        anchorYDp = TrayMenuState.y,
        contentHeight = FLOATING_MENU_PADDING + HEADER_HEIGHT + FLOATING_MENU_DIVIDER + FLOATING_MENU_ITEM_HEIGHT * 3,
        onDismiss = { TrayMenuState.hide() },
    ) {
        NowPlayingHeader(
            nowPlaying = nowPlaying,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        FloatingMenuItem(
            icon = Icons.Rounded.OpenInNew,
            text = str("menu_show_window"),
            onClick = {
                TrayMenuState.hide()
                onShowWindow()
            },
        )
        FloatingMenuItem(
            icon = Icons.Rounded.PictureInPictureAlt,
            text = if (isMiniPlayerVisible) str("menu_mini_player_hide") else str("menu_mini_player_show"),
            onClick = {
                TrayMenuState.hide()
                onToggleMiniPlayer()
            },
        )
        FloatingMenuItem(
            icon = Icons.Rounded.Close,
            text = str("menu_exit"),
            danger = true,
            onClick = {
                TrayMenuState.hide()
                onExit()
            },
        )
    }
}

/** Cover, title and artist, and previous / play-pause / next — the reason most people open a tray menu. */
@Composable
private fun NowPlayingHeader(
    nowPlaying: TrayNowPlaying?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(HEADER_HEIGHT).padding(horizontal = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            val artwork = nowPlaying?.artworkUrl
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = nowPlaying?.title?.takeIf { it.isNotBlank() } ?: "KittyTune",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            nowPlaying?.artist?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val enabled = nowPlaying != null
        TransportButton(Icons.Rounded.SkipPrevious, enabled, onPrevious)
        TransportButton(
            if (nowPlaying?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            enabled,
            onPlayPause,
            emphasized = true,
        )
        TransportButton(Icons.Rounded.SkipNext, enabled, onNext)
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(32.dp)
            .pressScale(interaction, pressedScale = 0.9f)
            .clip(CircleShape)
            .background(if (emphasized) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                emphasized -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

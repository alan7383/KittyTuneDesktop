@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.alananasss.kittytune.ui.tray

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ripple
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.alananasss.kittytune.ui.common.pressScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * Shared visibility/position for the custom tray context menu.
 *
 * The AWT `PopupMenu` Compose's [androidx.compose.ui.window.Tray] installs is drawn entirely by
 * the OS — square corners, system colours, no theme link — which is what made the tray look like
 * Windows XP next to the rest of the app. This state lets any tray backend (Windows/macOS AWT
 * icon, Linux SNI ContextMenu) open one Compose window instead.
 */
object TrayMenuState {
    var visible by mutableStateOf(false)
        private set

    /** Physical pixels (AWT desktop space) → converted to dp when the window opens. */
    var xDp by mutableStateOf(0f)
        private set
    var yDp by mutableStateOf(0f)
        private set

    fun show(physicalX: Int, physicalY: Int) {
        val metrics = com.alananasss.kittytune.getScreenMetricsDp(null, physicalX, physicalY)
        val scaleX = metrics.scaleX.coerceAtLeast(0.1f)
        val scaleY = metrics.scaleY.coerceAtLeast(0.1f)
        val cursorXDp = physicalX / scaleX
        val cursorYDp = physicalY / scaleY

        val usable = metrics.usableBoundsDp
        val width = MENU_WINDOW_WIDTH_DP
        val height = MENU_WINDOW_HEIGHT_DP

        // Tray docks at the bottom on Windows/Linux and the top on macOS — open away from the edge.
        val opensUpward = cursorYDp > usable.y + usable.height / 2f
        var left = cursorXDp - 12f
        var top = if (opensUpward) cursorYDp - height - 4f else cursorYDp + 8f

        val maxLeft = (usable.x + usable.width - width).toFloat().coerceAtLeast(usable.x.toFloat())
        left = left.coerceIn(usable.x.toFloat(), maxLeft)
        val maxTop = (usable.y + usable.height - height).toFloat().coerceAtLeast(usable.y.toFloat())
        top = top.coerceIn(usable.y.toFloat(), maxTop)

        xDp = left
        yDp = top
        visible = true
    }

    fun hide() {
        visible = false
    }

    fun toggle(physicalX: Int, physicalY: Int) {
        if (visible) hide() else show(physicalX, physicalY)
    }
}

/**
 * Content size — the window adds padding around it so the shadow is not clipped. Fixed rather than
 * measured because [TrayMenuState.show] needs the height up front to open upward from a bottom tray.
 */
private const val MENU_CONTENT_WIDTH_DP = 256f
private const val MENU_CONTENT_HEIGHT_DP = 194f
private const val MENU_SHADOW_PAD_DP = 16f
private const val MENU_WINDOW_WIDTH_DP = MENU_CONTENT_WIDTH_DP + MENU_SHADOW_PAD_DP
private const val MENU_WINDOW_HEIGHT_DP = MENU_CONTENT_HEIGHT_DP + MENU_SHADOW_PAD_DP

/** What is playing, for the menu's header. Null fields are simply left out. */
data class TrayNowPlaying(
    val title: String?,
    val artist: String?,
    val artworkUrl: String?,
    val isPlaying: Boolean,
)

/**
 * The tray's context menu: what is playing with transport controls on top, then the app actions.
 *
 * Sized and styled like the app's own menus — a 16 dp surface-container card, 36 dp rows, label-sized
 * text — rather than the oversized grey panel it replaced, which matched neither the app nor the OS.
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

    val windowState = rememberWindowState(
        position = WindowPosition(TrayMenuState.xDp.dp, TrayMenuState.yDp.dp),
        size = DpSize(MENU_WINDOW_WIDTH_DP.dp, MENU_WINDOW_HEIGHT_DP.dp),
    )

    Window(
        onCloseRequest = { TrayMenuState.hide() },
        state = windowState,
        undecorated = true,
        transparent = true,
        focusable = true,
        alwaysOnTop = true,
        resizable = false,
        title = "KittyTune",
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                TrayMenuState.hide()
                true
            } else {
                false
            }
        },
    ) {
        runCatching { window.background = java.awt.Color(0, 0, 0, 0) }

        // Take the foreground on open. Without it Windows may leave focus where it was, and then the
        // menu never loses focus either: a click elsewhere did not close it and Escape did not reach it.
        androidx.compose.runtime.LaunchedEffect(window) {
            window.toFront()
            window.requestFocus()
        }

        // Clicking any other window (or the desktop) closes the menu, like a native tray popup.
        DisposableEffect(window) {
            val listener = object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent?) {
                    TrayMenuState.hide()
                }
            }
            window.addWindowFocusListener(listener)
            onDispose { window.removeWindowFocusListener(listener) }
        }

        KittyTuneTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(MENU_SHADOW_PAD_DP.dp / 2)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize().padding(6.dp)) {
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
                        TrayMenuItem(
                            icon = Icons.Rounded.OpenInNew,
                            text = str("menu_show_window"),
                            onClick = {
                                TrayMenuState.hide()
                                onShowWindow()
                            },
                        )
                        TrayMenuItem(
                            icon = Icons.Rounded.PictureInPictureAlt,
                            text = if (isMiniPlayerVisible) str("menu_mini_player_hide") else str("menu_mini_player_show"),
                            onClick = {
                                TrayMenuState.hide()
                                onToggleMiniPlayer()
                            },
                        )
                        TrayMenuItem(
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
            }
        }
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
        modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 6.dp),
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

@Composable
private fun TrayMenuItem(
    icon: ImageVector,
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val container by animateColorAsState(
        targetValue = when {
            hovered && danger -> MaterialTheme.colorScheme.errorContainer
            hovered -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f)
        },
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "trayMenuItemBg",
    )
    val content = when {
        hovered && danger -> MaterialTheme.colorScheme.onErrorContainer
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .hoverable(interaction)
            // A pressed state, not only a hover one: a menu item that does not react to the press
            // itself feels like it missed the click.
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

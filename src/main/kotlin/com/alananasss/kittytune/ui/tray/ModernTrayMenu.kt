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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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

/** Content size — window adds padding around it so the drop shadow is not clipped. */
private const val MENU_CONTENT_WIDTH_DP = 248f
private const val MENU_CONTENT_HEIGHT_DP = 156f
private const val MENU_SHADOW_PAD_DP = 16f
private const val MENU_WINDOW_WIDTH_DP = MENU_CONTENT_WIDTH_DP + MENU_SHADOW_PAD_DP
private const val MENU_WINDOW_HEIGHT_DP = MENU_CONTENT_HEIGHT_DP + MENU_SHADOW_PAD_DP

/**
 * Floating tray menu: transparent undecorated window, rounded translucent panel, Material 3
 * hover states — the same visual language as the mini player and in-app popups.
 */
@Composable
fun ModernTrayMenuHost(
    isMiniPlayerVisible: Boolean,
    onShowWindow: () -> Unit,
    onToggleMiniPlayer: () -> Unit,
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
        runCatching {
            window.background = java.awt.Color(0, 0, 0, 0)
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
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    ),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
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
                            text = if (isMiniPlayerVisible) {
                                str("menu_mini_player_hide")
                            } else {
                                str("menu_mini_player_show")
                            },
                            onClick = {
                                TrayMenuState.hide()
                                onToggleMiniPlayer()
                            },
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 1.dp,
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

@Composable
private fun TrayMenuItem(
    icon: ImageVector,
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val container by animateColorAsState(
        targetValue = when {
            hovered && danger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
            hovered -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
            else -> Color.Transparent
        },
        label = "trayMenuItemBg",
    )
    val content by animateColorAsState(
        targetValue = when {
            hovered && danger -> MaterialTheme.colorScheme.onErrorContainer
            hovered -> MaterialTheme.colorScheme.onSecondaryContainer
            danger -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        },
        label = "trayMenuItemContent",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (danger) FontWeight.Medium else FontWeight.Normal,
            color = content,
            maxLines = 1,
        )
    }
}

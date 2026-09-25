package com.alananasss.kittytune.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

/**
 * A context menu in a window of its own, placed at a point on screen.
 *
 * Compose's `DropdownMenu` draws inside the window that owns it, so a menu opened from a small window —
 * the tray has none, the mini player is a strip a few dozen dp tall — was cut off at that window's edge
 * and could be neither seen whole nor scrolled. This opens a borderless, transparent window instead, kept
 * on the screen under the point, and closes it on a click anywhere else or on Escape.
 *
 * @param anchorXDp / [anchorYDp] where the pointer was, in screen dp.
 * @param contentHeight fixed so the window can open upward when the point is low on the screen.
 */
@Composable
fun FloatingMenuWindow(
    anchorXDp: Float,
    anchorYDp: Float,
    contentHeight: Dp,
    onDismiss: () -> Unit,
    contentWidth: Dp = 256.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowWidth = contentWidth + SHADOW_PAD * 2
    val windowHeight = contentHeight + SHADOW_PAD * 2
    val position = remember(anchorXDp, anchorYDp) {
        placeOnScreen(anchorXDp, anchorYDp, windowWidth.value, windowHeight.value)
    }
    val windowState = rememberWindowState(
        position = WindowPosition(position.first.dp, position.second.dp),
        size = DpSize(windowWidth, windowHeight),
    )
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    Window(
        onCloseRequest = onDismiss,
        state = windowState,
        undecorated = true,
        transparent = true,
        focusable = true,
        alwaysOnTop = true,
        resizable = false,
        title = "KittyTune",
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        runCatching { window.background = java.awt.Color(0, 0, 0, 0) }

        // Take the foreground so Escape reaches the menu; Windows may otherwise leave focus where it was.
        LaunchedEffect(window) {
            window.toFront()
            window.requestFocus()
        }

        // Close on a click outside, like a native menu. Not on focus loss: on Windows focus bounces
        // through the taskbar while the pointer travels from a tray icon to the menu, which closed the
        // menu the moment it was reached. Where clicks cannot be watched (Linux), focus loss it is.
        if (isWindows) {
            LaunchedEffect(window) { awaitOutsideClick(window) { latestOnDismiss() } }
        } else {
            DisposableEffect(window) {
                val listener = object : WindowAdapter() {
                    override fun windowLostFocus(e: WindowEvent?) = latestOnDismiss()
                }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }
        }

        KittyTuneTheme {
            Box(Modifier.fillMaxSize().padding(SHADOW_PAD)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(Modifier.fillMaxSize().padding(6.dp), content = content)
                }
            }
        }
    }
}

/** One row of a [FloatingMenuWindow]: 36 dp, label-sized, with an eased hover and a ripple on press. */
@Composable
fun FloatingMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val container by animateColorAsState(
        targetValue = when {
            hovered && danger -> MaterialTheme.colorScheme.errorContainer
            hovered -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0f)
        },
        animationSpec = tween(120),
        label = "floatingMenuItemBg",
    )
    val contentColor = when {
        hovered && danger -> MaterialTheme.colorScheme.onErrorContainer
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(FLOATING_MENU_ITEM_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Row height of [FloatingMenuItem], for callers sizing a [FloatingMenuWindow]. */
val FLOATING_MENU_ITEM_HEIGHT = 36.dp

/** Vertical space the menu's own padding and a divider take, for the same purpose. */
val FLOATING_MENU_PADDING = 12.dp
val FLOATING_MENU_DIVIDER = 9.dp

private val SHADOW_PAD = 8.dp

private val isWindows = System.getProperty("os.name").lowercase().contains("win")

/**
 * Top-left corner for a menu of this size at this point: opens down and right, or up / left when that
 * would leave the usable area of the screen the point is on.
 */
private fun placeOnScreen(xDp: Float, yDp: Float, width: Float, height: Float): Pair<Float, Float> {
    val usable = usableScreenAt(xDp.toInt(), yDp.toInt())
    val right = (usable.x + usable.width).toFloat()
    val bottom = (usable.y + usable.height).toFloat()
    val left = if (xDp + width <= right) xDp else xDp - width
    val top = if (yDp + height <= bottom) yDp else yDp - height
    return left.coerceIn(usable.x.toFloat(), (right - width).coerceAtLeast(usable.x.toFloat())) to
        top.coerceIn(usable.y.toFloat(), (bottom - height).coerceAtLeast(usable.y.toFloat()))
}

/**
 * The usable area (screen minus taskbar) of the monitor under a point. AWT window coordinates and
 * screen bounds are in the same logical units Compose uses for window positions, so no scaling here.
 */
private fun usableScreenAt(x: Int, y: Int): java.awt.Rectangle {
    val env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
    val config = env.screenDevices
        .map { it.defaultConfiguration }
        .firstOrNull { it.bounds.contains(x, y) }
        ?: env.defaultScreenDevice.defaultConfiguration
    val bounds = config.bounds
    val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(config)
    return java.awt.Rectangle(
        bounds.x + insets.left,
        bounds.y + insets.top,
        bounds.width - insets.left - insets.right,
        bounds.height - insets.top - insets.bottom,
    )
}

/** Presses in the first moments after opening belong to the click that opened the menu. */
private const val OUTSIDE_CLICK_GRACE_MS = 250L
private const val OUTSIDE_CLICK_POLL_MS = 30L

/**
 * Returns once a mouse button goes down outside [window], calling [onOutside]. Polls the button state
 * — a few cheap calls every 30 ms, and only while a menu is on screen.
 */
private suspend fun awaitOutsideClick(window: java.awt.Window, onOutside: () -> Unit) {
    val user32 = com.sun.jna.platform.win32.User32.INSTANCE
    fun anyButtonDown(): Boolean =
        listOf(0x01, 0x02, 0x04).any { vk -> user32.GetAsyncKeyState(vk).toInt() and 0x8000 != 0 }

    kotlinx.coroutines.delay(OUTSIDE_CLICK_GRACE_MS)
    var wasDown = anyButtonDown()
    while (window.isShowing) {
        kotlinx.coroutines.delay(OUTSIDE_CLICK_POLL_MS)
        val isDown = anyButtonDown()
        if (isDown && !wasDown) {
            val pointer = runCatching { java.awt.MouseInfo.getPointerInfo()?.location }.getOrNull()
            if (pointer != null && !window.bounds.contains(pointer)) {
                onOutside()
                return
            }
        }
        wasDown = isDown
    }
}

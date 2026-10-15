package com.alananasss.kittytune.ui.player.mini

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.profile.MiniPlayerSettingsList
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import androidx.compose.material3.IconButtonDefaults

private const val SETTINGS_WIDTH = 480
private const val SETTINGS_HEIGHT = 620

/** The middle of the usable area of the monitor [window] is on, for a window of this size. */
private fun centredOnScreenOf(window: java.awt.Window?, width: Int, height: Int): WindowPosition {
    val config = window?.graphicsConfiguration ?: return WindowPosition(Alignment.Center)
    val bounds = config.bounds
    val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(config)
    val x = bounds.x + insets.left + (bounds.width - insets.left - insets.right - width) / 2
    val y = bounds.y + insets.top + (bounds.height - insets.top - insets.bottom - height) / 2
    return WindowPosition(x.dp, y.dp)
}

/** Material's emphasized-decelerate curve: arrives quickly and settles. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val ENTER_MS = 260
private const val EXIT_MS = 150

/**
 * The mini player's settings, opened from its right-click menu, in a window of their own — a dialog
 * inside the mini player would be clipped to its few dozen dp just as the old menu was.
 *
 * Drawn as the app's own card rather than an OS dialog, so it can open the way the app's dialogs do:
 * fading in while it grows from 92 %, and reversing on close. The title row drags the window.
 *
 * On Windows the window is restyled as a tool window (no taskbar button, no Alt+Tab entry) *before* it
 * is shown, which avoids the hide/show cycle that froze the settings the last time it was tried
 * (issue #66). Once it is showing, `ToolWindowStyle.raiseToFront` calls `SetForegroundWindow` — the
 * Win32 call `toFront()`/`requestFocus()` don't translate to — so the dialog lands above the mini
 * player's topmost tool window and actually takes clicks (issue #76).
 */
@Composable
fun MiniPlayerSettingsWindow(onClose: () -> Unit, nearWindow: java.awt.Window? = null) {
    val prefs = remember { PlayerPreferences() }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    // On the monitor the mini player is on, not on the main one: opened from a mini player on a second screen,
    // the settings came up on the first (issue #66).
    val state = rememberWindowState(
        position = remember(nearWindow) { centredOnScreenOf(nearWindow, SETTINGS_WIDTH, SETTINGS_HEIGHT) },
        size = DpSize(SETTINGS_WIDTH.dp, SETTINGS_HEIGHT.dp),
    )

    // Starts hidden: the tool-window style and transparent background are set before the first map,
    // so there is no hide/show cycle (which froze the window, issue #66) and no taskbar button flash.
    var windowReady by remember { mutableStateOf(false) }

    // Closing plays the exit first; the window goes once the card has gone.
    LaunchedEffect(shown.currentState, shown.isIdle) {
        if (shown.isIdle && !shown.currentState && !shown.targetState) onClose()
    }
    val requestClose = { shown.targetState = false }

    Window(
        onCloseRequest = requestClose,
        state = state,
        visible = windowReady,
        undecorated = true,
        transparent = true,
        // Above its own mini player: that window is TOPMOST while pinned, so a normal
        // settings window could open underneath it and take clicks meant for it.
        alwaysOnTop = true,
        resizable = false,
        title = str("mini_player_settings_title"),
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                requestClose()
                true
            } else {
                false
            }
        },
    ) {
        // Pre-map setup while the window is still hidden (visible = false above): the tool-window style
        // lands before the first show, so the shell never sees a regular window to put a taskbar button
        // for, and there is no hide/show cycle to freeze the window (issue #66). The mini player uses
        // the same pattern for the same reason.
        DisposableEffect(window) {
            runCatching { window.background = java.awt.Color(0, 0, 0, 0) }
            if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                com.alananasss.kittytune.core.ToolWindowStyle.apply(window)
            }
            windowReady = true
            onDispose { }
        }

        // Once the window is showing, raise it to the foreground above the mini player's topmost tool
        // window. toFront()/requestFocus() alone don't reliably do this on Windows (issue #76);
        // raiseToFront calls SetForegroundWindow, the Win32 function that actually activates a window
        // and reorders it above other topmost windows in the same process. The user just clicked a
        // context-menu item in our process, so we hold the foreground privilege and the call is allowed.
        LaunchedEffect(window) {
            // Wait for the DisposableEffect above to set windowReady = true and the window to become
            // showing. Polling is needed because the Window's visible flag flips in a recomposition
            // that runs after this effect is first scheduled.
            var attempts = 0
            while (!window.isShowing && window.isDisplayable && attempts < 120) {
                kotlinx.coroutines.delay(16)
                attempts++
            }
            if (!window.isShowing) return@LaunchedEffect
            runCatching {
                window.toFront()
                window.requestFocus()
            }
            if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                // A frame's wait lets the window finish mapping before we ask the shell to raise it.
                kotlinx.coroutines.delay(30)
                com.alananasss.kittytune.core.ToolWindowStyle.raiseToFront(window)
                // SetForegroundWindow can be refused on the first try if the shell still considers the
                // just-dismissed context menu the foreground. A second pass after the dust settles
                // usually sticks, since toFront() has already moved focus into our process.
                kotlinx.coroutines.delay(60)
                com.alananasss.kittytune.core.ToolWindowStyle.raiseToFront(window)
            }
        }

        KittyTuneTheme {
            Box(Modifier.fillMaxSize().padding(12.dp)) {
                AnimatedVisibility(
                    visibleState = shown,
                    enter = fadeIn(tween(ENTER_MS / 2)) +
                        scaleIn(tween(ENTER_MS, easing = EmphasizedDecelerate), initialScale = 0.92f),
                    exit = fadeOut(tween(EXIT_MS)) + scaleOut(tween(EXIT_MS), targetScale = 0.96f),
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
                            WindowDraggableArea {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(str("mini_player_settings_title"), style = MaterialTheme.typography.headlineSmall)
                                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = requestClose) {
                                        Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                                    }
                                }
                            }
                            MiniPlayerSettingsList(prefs, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

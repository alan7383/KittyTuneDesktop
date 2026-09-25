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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
 */
@Composable
fun MiniPlayerSettingsWindow(onClose: () -> Unit) {
    val prefs = remember { PlayerPreferences() }
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    val state = rememberWindowState(
        position = WindowPosition(Alignment.Center),
        size = DpSize(480.dp, 620.dp),
    )

    // Closing plays the exit first; the window goes once the card has gone.
    LaunchedEffect(shown.currentState, shown.isIdle) {
        if (shown.isIdle && !shown.currentState && !shown.targetState) onClose()
    }
    val requestClose = { shown.targetState = false }

    Window(
        onCloseRequest = requestClose,
        state = state,
        undecorated = true,
        transparent = true,
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
        runCatching { window.background = java.awt.Color(0, 0, 0, 0) }
        LaunchedEffect(window) { com.alananasss.kittytune.core.ToolWindowStyle.apply(window) }

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
                                    IconButton(onClick = requestClose) {
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

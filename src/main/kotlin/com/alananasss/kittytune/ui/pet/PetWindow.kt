@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.alananasss.kittytune.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.FLOATING_MENU_DIVIDER
import com.alananasss.kittytune.ui.common.FLOATING_MENU_ITEM_HEIGHT
import com.alananasss.kittytune.ui.common.FLOATING_MENU_PADDING
import com.alananasss.kittytune.ui.common.FloatingMenuItem
import com.alananasss.kittytune.ui.common.FloatingMenuWindow
import com.alananasss.kittytune.ui.player.mini.WindowDragHandler
import com.alananasss.kittytune.ui.player.mini.dragsWindow
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import com.alananasss.kittytune.ui.theme.withUiScale
import java.awt.Cursor
import kotlin.math.roundToInt

/** Whether the floating pet window is shown (remembered across launches). */
object PetWindowState {
    var visible by mutableStateOf(PetConfig.prefs.getBoolean(PetConfig.KEY_WINDOW_VISIBLE, false))
        private set

    fun show(value: Boolean = true) {
        visible = value
        PetConfig.prefs.putBoolean(PetConfig.KEY_WINDOW_VISIBLE, value)
        if (value) PetState.refresh(force = true, reroll = false)
    }

    fun toggle() = show(!visible)
}

private const val MIN_W = 120
private const val MIN_H = 110
private const val MAX_W = 560
private const val MAX_H = 520
private const val DEFAULT_W = 230
private const val DEFAULT_H = 210

/**
 * KittyTune's desktop cat: the Android home-screen widget as a small floating window that stays
 * on top of everything, like the mini player. Same layout as the widget (name pill and love meter,
 * the scene, the status bubble), same rules (compact and narrow sizes hide parts), same actions:
 * click the cat for the chosen tap action, click the bubble to open the app while it dances or to
 * play / pause otherwise. Drag it anywhere; right-click for options; resize from the corner.
 */
@Composable
fun PetDesktopWindow(
    isAppFullScreen: Boolean,
    onOpenMainWindow: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val prefs = remember { PlayerPreferences() }
    val saved = remember { PetConfig.windowBounds() }
    val windowState = rememberWindowState(
        size = DpSize((saved?.get(2) ?: DEFAULT_W).dp, (saved?.get(3) ?: DEFAULT_H).dp),
        position = if (saved != null) WindowPosition(saved[0].dp, saved[1].dp) else WindowPosition(Alignment.BottomEnd),
    )
    var isPinned by remember { mutableStateOf(PetConfig.isWindowPinned()) }
    var isDragging by remember { mutableStateOf(false) }
    // Opaque on machines where a transparent window froze the app (RTSS); see FloatingWindowCompat. The
    // transparency of a shown window cannot change, so switching the mode builds the window again.
    val compat = com.alananasss.kittytune.core.FloatingWindowCompat.opaque
    androidx.compose.runtime.key(compat) {
    // Marks the opening on disk: if it freezes the app, the next launch opens it in compatibility mode.
    remember { com.alananasss.kittytune.core.FloatingWindowCompat.opening() }
    // Inside the key: a window built again is set up again before it shows.
    var windowReady by remember { mutableStateOf(false) }

    Window(
        onCloseRequest = { PetWindowState.show(false) },
        state = windowState,
        visible = !isAppFullScreen && windowReady,
        alwaysOnTop = isPinned,
        undecorated = true,
        transparent = !compat,
        resizable = true,
        title = "KittyTune Kitty pet",
    ) {
        // Same pre-map setup as the mini player: a floating tool, not another app window.
        DisposableEffect(window) {
            runCatching { window.type = java.awt.Window.Type.UTILITY }
            // A see-through background makes AWT turn the window translucent: never in the opaque mode.
            if (!compat) runCatching { window.background = java.awt.Color(0, 0, 0, 0) }
            val shownListener = object : java.awt.event.ComponentAdapter() {
                override fun componentShown(e: java.awt.event.ComponentEvent?) {
                    if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                        com.alananasss.kittytune.core.ToolWindowStyle.apply(window)
                    } else {
                        com.alananasss.kittytune.core.LinuxWindowHelper.configureUtilityWindow(window)
                    }
                }
            }
            if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) com.alananasss.kittytune.core.ToolWindowStyle.apply(window)
            window.addComponentListener(shownListener)
            windowReady = true
            onDispose { window.removeComponentListener(shownListener) }
        }

        val density = LocalDensity.current
        val uiScale by prefs.uiScaleFlow().collectAsState(initial = prefs.getUiScale())
        val customDensity = remember(density, uiScale) { density.withUiScale(uiScale) }

        // Opaque mode: the card's 24 dp corners are cut by the window's shape rather than left transparent.
        if (compat) {
            DisposableEffect(window, uiScale) {
                val remove = com.alananasss.kittytune.core.FloatingWindowCompat.keepRoundedShape(window) { 24.0 * uiScale }
                onDispose { remove() }
            }
        }
        // Up and drawing for a few seconds: the opening did not freeze anything.
        LaunchedEffect(window) {
            kotlinx.coroutines.delay(4_000)
            com.alananasss.kittytune.core.FloatingWindowCompat.opened()
        }
        val minWpx = with(density) { MIN_W.dp.roundToPx() }
        val minHpx = with(density) { MIN_H.dp.roundToPx() }
        val maxWpx = with(density) { MAX_W.dp.roundToPx() }
        val maxHpx = with(density) { MAX_H.dp.roundToPx() }

        val saveBounds: () -> Unit = remember(window, density) {
            {
                PetConfig.saveWindowBounds(
                    window.x, window.y,
                    (window.width / density.density).roundToInt().coerceIn(MIN_W, MAX_W),
                    (window.height / density.density).roundToInt().coerceIn(MIN_H, MAX_H),
                )
            }
        }

        LaunchedEffect(window, isAppFullScreen) {
            if (isAppFullScreen) return@LaunchedEffect
            if (com.alananasss.kittytune.data.theme.WindowsFullScreen.isWindows) {
                com.alananasss.kittytune.core.ToolWindowStyle.apply(window)
            } else {
                com.alananasss.kittytune.core.LinuxWindowHelper.configureUtilityWindow(window)
            }
        }

        val dragHandler = remember(window) {
            WindowDragHandler(
                window = window,
                onDragStart = { isDragging = true },
                onDragEnd = {
                    isDragging = false
                    runCatching { windowState.position = WindowPosition(window.x.dp, window.y.dp) }
                    saveBounds()
                },
            )
        }

        DisposableEffect(window) {
            runCatching {
                if (!compat) window.background = java.awt.Color(0, 0, 0, 0)
                window.minimumSize = java.awt.Dimension(minWpx, minHpx)
                window.maximumSize = java.awt.Dimension(maxWpx, maxHpx)
            }
            val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentMoved(e: java.awt.event.ComponentEvent) {
                    if (!isDragging) saveBounds()
                }
                override fun componentResized(e: java.awt.event.ComponentEvent) = saveBounds()
            }
            window.addComponentListener(listener)
            onDispose {
                dragHandler.cleanup()
                window.removeComponentListener(listener)
                saveBounds()
            }
        }

        CompositionLocalProvider(LocalDensity provides customDensity) {
            KittyTuneTheme {
                var menuVisible by remember { mutableStateOf(false) }
                var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .dragsWindow(window, dragHandler)
                        .onPointerEvent(PointerEventType.Press) { event ->
                            if (event.button == PointerButton.Secondary) {
                                val pos = event.changes.first().position
                                menuOffset = with(customDensity) { DpOffset(pos.x.toDp(), pos.y.toDp()) }
                                menuVisible = true
                            }
                        }
                ) {
                    PetWidgetContent(
                        onOpenApp = onOpenMainWindow,
                        onPlayPause = onPlayPause,
                    )

                    if (menuVisible) {
                        FloatingMenuWindow(
                            anchorXDp = window.x + menuOffset.x.value,
                            anchorYDp = window.y + menuOffset.y.value,
                            contentHeight = FLOATING_MENU_PADDING + FLOATING_MENU_DIVIDER + FLOATING_MENU_ITEM_HEIGHT * 4,
                            contentWidth = 240.dp,
                            onDismiss = { menuVisible = false },
                        ) {
                            FloatingMenuItem(
                                icon = Icons.Rounded.OpenInNew,
                                text = str("menu_show_window"),
                                onClick = { menuVisible = false; onOpenMainWindow() },
                            )
                            FloatingMenuItem(
                                icon = Icons.Rounded.PushPin,
                                text = if (isPinned) str("mini_player_unpin") else str("mini_player_pin"),
                                onClick = {
                                    isPinned = !isPinned
                                    PetConfig.setWindowPinned(isPinned)
                                    menuVisible = false
                                },
                            )
                            FloatingMenuItem(
                                icon = Icons.Rounded.Pets,
                                text = str("pet_settings_title"),
                                onClick = { menuVisible = false; onOpenSettings() },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            FloatingMenuItem(
                                icon = Icons.Rounded.Close,
                                text = str("pet_desk_hide"),
                                danger = true,
                                onClick = { menuVisible = false; PetWindowState.show(false) },
                            )
                        }
                    }

                    // Bottom-right corner resize grip
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
                            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() } }
                            .pointerInput(minWpx, maxWpx, minHpx, maxHpx) {
                                detectDragGestures(
                                    onDragEnd = { saveBounds() },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        window.setSize(
                                            (window.width + amount.x.toInt()).coerceIn(minWpx, maxWpx),
                                            (window.height + amount.y.toInt()).coerceIn(minHpx, maxHpx),
                                        )
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
    }
}

/**
 * The widget's own layout (PetWidget.PetContent on Android): a rounded card in the wall colour,
 * the name pill and the love meter on top, the scene, then the status bubble.
 */
@Composable
internal fun PetWidgetContent(onOpenApp: () -> Unit, onPlayPause: () -> Unit) {
    val version by PetConfig.version.collectAsState()
    val settings = remember(version) { PetConfig.load() }
    val state = PetState.snapshot
    // the clock moves the cat along (sad, asleep, the New Year countdown) without any music event
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000L - System.currentTimeMillis() % 1_000L)
            now = System.currentTimeMillis()
        }
    }
    // a change landing between two clock ticks (a boop) is resolved at its own time
    val clock = maxOf(now, state.tick)

    val resolved = PetBrain.resolve(state.playing, state.lastMusicAt, state.boopUntil, clock, state.idle, state.dance, settings)
    // the music calmed down (intro, break): gentle sway until the drop
    val activity = if (resolved.first.isDance && state.quiet) PetActivity.DANCE_SWAY else resolved.first
    val room = resolved.second
    val shownBpm = if (state.liveBpm > 0f) state.liveBpm else state.bpm
    val hearts = PetBrain.hearts(state.playing, state.lastMusicAt, clock, settings)
    val wall = PetLayers.wallColor(room, settings)
    val dark = wall?.let { PetLayers.isDark(it) } ?: false

    val ink = when {
        wall == null -> MaterialTheme.colorScheme.onSecondaryContainer
        dark -> Color(0xFFF1ECFF)
        else -> Color(0xFF4A3448)
    }
    val bg = wall?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val pillBg = if (dark) Color(0x26FFFFFF) else Color(0xB3FFFFFF)
    val heartColor = if (dark) 0xFFFFB3CF.toInt() else 0xFFFF4F8B.toInt()
    val mono = MaterialTheme.typography.labelMedium.copy(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)

    val tapAction: () -> Unit = when (settings.tap) {
        PetTap.BOOP -> ({ PetState.boop() })
        PetTap.OPEN_APP -> onOpenApp
        PetTap.PLAY_PAUSE -> onPlayPause
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
    ) {
        val compact = maxHeight < 150.dp
        val narrow = maxWidth < 140.dp
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            if (settings.showName || (settings.showHearts && !narrow)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (settings.showName) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(pillBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(settings.name, maxLines = 1, style = mono)
                        }
                    }
                    if (settings.showHearts && !narrow) {
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(5) { i ->
                                val icon = remember(i < hearts, heartColor) {
                                    PetComposer.icon(if (i < hearts) "widget_pet_heart_full" else "widget_pet_heart_empty", heartColor)
                                }
                                if (icon != null) {
                                    Canvas(Modifier.padding(horizontal = 1.dp).size(13.dp)) { drawPixels(icon) }
                                }
                            }
                        }
                    }
                }
            }

            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // the scene keeps its 46:32 shape, as large as fits (fitCenter on Android)
                val w = minOf(maxWidth, maxHeight * (46f / 32f))
                PetSprite(
                    activity = activity,
                    settings = settings,
                    modifier = Modifier.width(w),
                    room = room,
                    corner = 0,
                    halfTime = activity.isDance && state.quiet,
                    fallbackBpm = shownBpm,
                    framesKey = if (activity == PetActivity.NY_COUNTDOWN || activity == PetActivity.NY_FIREWORKS) state.tick else null,
                    onClick = tapAction,
                )
            }

            if (!compact && settings.showStatus) {
                Spacer(Modifier.height(4.dp))
                val status = if (activity.isDance) {
                    val song = state.title.ifBlank { Strings.get(activity.status) }
                    if (shownBpm in 30f..400f) "♪ $song · ${shownBpm.roundToInt()} BPM" else "♪ $song"
                } else {
                    "${activity.kaomoji} ${activity.statusText(state.facts)}"
                }
                val midnight = if (activity == PetActivity.NY_EVE) PetNewYear.nextMidnight(clock)?.takeIf { PetNewYear.showLiveClock(it, clock) } else null
                val action = if (activity.isDance) onOpenApp else onPlayPause
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillBg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = action)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activity.isRare) {
                            val sparkle = remember { PetComposer.icon("widget_pet_sparkle", 0xFFFFC83D.toInt()) }
                            if (sparkle != null) {
                                Canvas(Modifier.padding(end = 4.dp).size(14.dp)) { drawPixels(sparkle) }
                            }
                        }
                        val text = if (midnight != null) {
                            // ticks down to the second, like the widget's chronometer
                            Strings.get("pet_ny_chrono", PetNewYear.celebratedYear(clock), countdown(midnight - clock))
                        } else status
                        Text(
                            text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = mono.copy(textAlign = TextAlign.Center),
                        )
                    }
                }
            }
        }
    }
}

/** H:MM:SS (or MM:SS in the last hour), as Android's count-down Chronometer shows it. */
private fun countdown(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

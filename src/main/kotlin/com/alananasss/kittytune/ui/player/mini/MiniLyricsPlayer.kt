@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.alananasss.kittytune.ui.player.mini

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.alananasss.kittytune.core.Prefs
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.MiniPlayerStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import java.awt.Cursor

/**
 * A floating mini-player for lyrics that stays on top of windows.
 *
 * Designed to be placed near the taskbar, top of the screen or anywhere on the desktop.
 * It shows the single line being sung in real time, with smooth word karaoke fill.
 * When a line is longer than the display width, it breaks it into natural readable chunks
 * and smoothly advances to show the next part of the line as playback progresses through it.
 */
@Composable
fun MiniLyricsPlayerWindow(viewModel: PlayerViewModel) {
    val prefs = remember { PlayerPreferences() }
    val miniPlayerStyle by prefs.miniPlayerStyleFlow().collectAsState(initial = prefs.getMiniPlayerStyle())
    val transparentBg by prefs.miniPlayerTransparentBgFlow().collectAsState(initial = prefs.getMiniPlayerTransparentBg())
    val isElongated = miniPlayerStyle == MiniPlayerStyle.ELONGATED

    val minW = if (isElongated) PlayerPreferences.MINI_PLAYER_ELONGATED_MIN_WIDTH else PlayerPreferences.MINI_PLAYER_MIN_WIDTH
    val maxW = if (isElongated) PlayerPreferences.MINI_PLAYER_ELONGATED_MAX_WIDTH else PlayerPreferences.MINI_PLAYER_MAX_WIDTH
    val minH = if (isElongated) PlayerPreferences.MINI_PLAYER_ELONGATED_MIN_HEIGHT else PlayerPreferences.MINI_PLAYER_MIN_HEIGHT
    val maxH = if (isElongated) PlayerPreferences.MINI_PLAYER_ELONGATED_MAX_HEIGHT else PlayerPreferences.MINI_PLAYER_MAX_HEIGHT

    val initialX = remember {
        if (prefs.getMiniPlayerStyle() == MiniPlayerStyle.ELONGATED) prefs.getMiniPlayerElongatedX()
        else prefs.getMiniPlayerX()
    }
    val initialY = remember {
        if (prefs.getMiniPlayerStyle() == MiniPlayerStyle.ELONGATED) prefs.getMiniPlayerElongatedY()
        else prefs.getMiniPlayerY()
    }
    val initialWidth = remember {
        if (prefs.getMiniPlayerStyle() == MiniPlayerStyle.ELONGATED) prefs.getMiniPlayerElongatedWidth()
        else prefs.getMiniPlayerWidth()
    }
    val initialHeight = remember {
        if (prefs.getMiniPlayerStyle() == MiniPlayerStyle.ELONGATED) prefs.getMiniPlayerElongatedHeight()
        else prefs.getMiniPlayerHeight()
    }

    val windowState = rememberWindowState(
        size = DpSize(initialWidth.dp, initialHeight.dp),
        position = if (initialX != null && initialY != null) {
            WindowPosition(initialX.dp, initialY.dp)
        } else {
            WindowPosition(Alignment.BottomCenter)
        }
    )

    var isPinned by remember { mutableStateOf(true) }

    LaunchedEffect(windowState.position, windowState.size, isElongated) {
        val pos = windowState.position
        if (pos.isSpecified) {
            val x = pos.x.value.toInt()
            val y = pos.y.value.toInt()
            val w = windowState.size.width.value.toInt().coerceIn(minW, maxW)
            val h = windowState.size.height.value.toInt().coerceIn(minH, maxH)
            if (isElongated) {
                viewModel.saveMiniPlayerElongatedBounds(x, y, w, h)
            } else {
                viewModel.saveMiniPlayerBounds(x, y, w, h)
            }
        }
    }

    val closeMiniPlayer = {
        val pos = windowState.position
        val curX = if (isElongated) prefs.getMiniPlayerElongatedX() else prefs.getMiniPlayerX()
        val curY = if (isElongated) prefs.getMiniPlayerElongatedY() else prefs.getMiniPlayerY()
        val x = if (pos.isSpecified) pos.x.value.toInt() else (curX ?: 0)
        val y = if (pos.isSpecified) pos.y.value.toInt() else (curY ?: 0)
        val w = windowState.size.width.value.toInt().coerceIn(minW, maxW)
        val h = windowState.size.height.value.toInt().coerceIn(minH, maxH)
        if (isElongated) {
            viewModel.saveMiniPlayerElongatedBounds(x, y, w, h)
        } else {
            viewModel.saveMiniPlayerBounds(x, y, w, h)
        }
        com.alananasss.kittytune.core.Prefs.flush(force = true)
        viewModel.toggleMiniPlayer(false)
    }

    Window(
        onCloseRequest = closeMiniPlayer,
        state = windowState,
        alwaysOnTop = isPinned,
        undecorated = true,
        transparent = true,
        resizable = true,
        title = "KittyTune Mini Player",
    ) {
        val density = LocalDensity.current
        val uiScale by prefs.uiScaleFlow().collectAsState(initial = prefs.getUiScale())
        val customDensity = remember(density, uiScale) {
            Density(
                density = density.density * uiScale,
                fontScale = density.fontScale * uiScale
            )
        }

        // Exact physical pixel limits for window sizing and OS window manager hints
        val minWidthPx = with(density) { minW.dp.roundToPx() }
        val maxWidthPx = with(density) { maxW.dp.roundToPx() }
        val minHeightPx = with(density) { minH.dp.roundToPx() }
        val maxHeightPx = with(density) { maxH.dp.roundToPx() }

        val saveCurrentBounds: () -> Unit = remember(window, isElongated, density, minW, maxW, minH, maxH) {
            {
                val x = (window.x / density.density).toInt()
                val y = (window.y / density.density).toInt()
                val w = (window.width / density.density).toInt().coerceIn(minW, maxW)
                val h = (window.height / density.density).toInt().coerceIn(minH, maxH)
                if (isElongated) {
                    viewModel.saveMiniPlayerElongatedBounds(x, y, w, h)
                } else {
                    viewModel.saveMiniPlayerBounds(x, y, w, h)
                }
            }
        }

        var lastAppliedStyle by remember { mutableStateOf(prefs.getMiniPlayerStyle()) }
        LaunchedEffect(miniPlayerStyle) {
            if (miniPlayerStyle != lastAppliedStyle) {
                val prevStyle = lastAppliedStyle
                lastAppliedStyle = miniPlayerStyle

                val curX = (window.x / density.density).toInt()
                val curY = (window.y / density.density).toInt()
                val curW = (window.width / density.density).toInt()
                val curH = (window.height / density.density).toInt()

                if (prevStyle == MiniPlayerStyle.ELONGATED) {
                    viewModel.saveMiniPlayerElongatedBounds(
                        curX, curY,
                        curW.coerceIn(PlayerPreferences.MINI_PLAYER_ELONGATED_MIN_WIDTH, PlayerPreferences.MINI_PLAYER_ELONGATED_MAX_WIDTH),
                        curH.coerceIn(PlayerPreferences.MINI_PLAYER_ELONGATED_MIN_HEIGHT, PlayerPreferences.MINI_PLAYER_ELONGATED_MAX_HEIGHT)
                    )
                } else {
                    viewModel.saveMiniPlayerBounds(
                        curX, curY,
                        curW.coerceIn(PlayerPreferences.MINI_PLAYER_MIN_WIDTH, PlayerPreferences.MINI_PLAYER_MAX_WIDTH),
                        curH.coerceIn(PlayerPreferences.MINI_PLAYER_MIN_HEIGHT, PlayerPreferences.MINI_PLAYER_MAX_HEIGHT)
                    )
                }

                if (miniPlayerStyle == MiniPlayerStyle.ELONGATED) {
                    val ex = prefs.getMiniPlayerElongatedX()
                    val ey = prefs.getMiniPlayerElongatedY()
                    val ew = prefs.getMiniPlayerElongatedWidth()
                    val eh = prefs.getMiniPlayerElongatedHeight()
                    val newMinW = with(density) { PlayerPreferences.MINI_PLAYER_ELONGATED_MIN_WIDTH.dp.roundToPx() }
                    val newMaxW = with(density) { PlayerPreferences.MINI_PLAYER_ELONGATED_MAX_WIDTH.dp.roundToPx() }
                    val newMinH = with(density) { PlayerPreferences.MINI_PLAYER_ELONGATED_MIN_HEIGHT.dp.roundToPx() }
                    val newMaxH = with(density) { PlayerPreferences.MINI_PLAYER_ELONGATED_MAX_HEIGHT.dp.roundToPx() }
                    runCatching {
                        window.minimumSize = java.awt.Dimension(newMinW, newMinH)
                        window.maximumSize = java.awt.Dimension(newMaxW, newMaxH)
                        window.setSize((ew * density.density).toInt(), (eh * density.density).toInt())
                        if (ex != null && ey != null) {
                            window.setLocation((ex * density.density).toInt(), (ey * density.density).toInt())
                            windowState.position = WindowPosition(ex.dp, ey.dp)
                        }
                    }
                    windowState.size = DpSize(ew.dp, eh.dp)
                } else {
                    val sx = prefs.getMiniPlayerX()
                    val sy = prefs.getMiniPlayerY()
                    val sw = prefs.getMiniPlayerWidth()
                    val sh = prefs.getMiniPlayerHeight()
                    val newMinW = with(density) { PlayerPreferences.MINI_PLAYER_MIN_WIDTH.dp.roundToPx() }
                    val newMaxW = with(density) { PlayerPreferences.MINI_PLAYER_MAX_WIDTH.dp.roundToPx() }
                    val newMinH = with(density) { PlayerPreferences.MINI_PLAYER_MIN_HEIGHT.dp.roundToPx() }
                    val newMaxH = with(density) { PlayerPreferences.MINI_PLAYER_MAX_HEIGHT.dp.roundToPx() }
                    runCatching {
                        window.minimumSize = java.awt.Dimension(newMinW, newMinH)
                        window.maximumSize = java.awt.Dimension(newMaxW, newMaxH)
                        window.setSize((sw * density.density).toInt(), (sh * density.density).toInt())
                        if (sx != null && sy != null) {
                            window.setLocation((sx * density.density).toInt(), (sy * density.density).toInt())
                            windowState.position = WindowPosition(sx.dp, sy.dp)
                        }
                    }
                    windowState.size = DpSize(sw.dp, sh.dp)
                }
            }
        }

        DisposableEffect(window, isElongated) {
            runCatching {
                window.background = java.awt.Color(0, 0, 0, 0)
                window.isAlwaysOnTop = isPinned
                window.minimumSize = java.awt.Dimension(minWidthPx, minHeightPx)
                window.maximumSize = java.awt.Dimension(maxWidthPx, maxHeightPx)
            }
            val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent) {
                    val clampedW = window.width.coerceIn(minWidthPx, maxWidthPx)
                    val clampedH = window.height.coerceIn(minHeightPx, maxHeightPx)
                    if (window.width != clampedW || window.height != clampedH) {
                        window.setSize(clampedW, clampedH)
                    }
                    saveCurrentBounds()
                }
                override fun componentMoved(e: java.awt.event.ComponentEvent) {
                    saveCurrentBounds()
                }
            }
            window.addComponentListener(listener)
            onDispose {
                window.removeComponentListener(listener)
                saveCurrentBounds()
                com.alananasss.kittytune.core.Prefs.flush(force = true)
            }
        }

        CompositionLocalProvider(LocalDensity provides customDensity) {
            KittyTuneTheme {
                val prefsSnapshot by Prefs.flow.collectAsState()
                val showCover = remember(prefsSnapshot) { prefs.getMiniPlayerShowCover() }
                val showPlayback = remember(prefsSnapshot) { prefs.getMiniPlayerShowPlaybackControls() }
                val showAdditional = remember(prefsSnapshot) { prefs.getMiniPlayerShowAdditionalControls() }
                val controlsOnHover = remember(prefsSnapshot) { prefs.getMiniPlayerControlsOnHover() }
                val hoverEffect = remember(prefsSnapshot) { prefs.getMiniPlayerHoverEffect() }
                val showProgress = remember(prefsSnapshot) { prefs.getMiniPlayerShowProgress() }

                val windowInteractionSource = remember { MutableInteractionSource() }
                val isWindowHovered by windowInteractionSource.collectIsHoveredAsState()

                val surfaceAlpha by animateFloatAsState(
                    targetValue = when {
                        transparentBg && !isWindowHovered -> 0.0f
                        transparentBg && isWindowHovered -> 0.35f
                        !hoverEffect || isWindowHovered -> 0.96f
                        else -> 0.82f
                    },
                    animationSpec = tween(200)
                )

                val surfaceColor = if (transparentBg && surfaceAlpha == 0.0f) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = surfaceAlpha)
                }

                val surfaceBorder = when {
                    transparentBg && !isWindowHovered -> null
                    transparentBg && isWindowHovered -> androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    else -> androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                }

                val surfaceShape = if (isElongated) RoundedCornerShape(10.dp) else RoundedCornerShape(22.dp)
                val shadowElevation = if (transparentBg) 0.dp else if (isElongated) 4.dp else 8.dp

                WindowDraggableArea {
                    Surface(
                        shape = surfaceShape,
                        color = surfaceColor,
                        border = surfaceBorder,
                        shadowElevation = shadowElevation,
                        modifier = Modifier
                            .fillMaxSize()
                            .hoverable(windowInteractionSource)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = { saveCurrentBounds() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val currentLoc = window.location
                                        window.setLocation(
                                            currentLoc.x + dragAmount.x.toInt(),
                                            currentLoc.y + dragAmount.y.toInt()
                                        )
                                    }
                                )
                            }
                    ) {
                        // Context menu state: opened by right-click anywhere on the mini player
                        var contextMenuVisible by remember { mutableStateOf(false) }
                        var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onPointerEvent(PointerEventType.Press) { event ->
                                    if (event.button == PointerButton.Secondary) {
                                        val pos = event.changes.first().position
                                        contextMenuOffset = with(density) {
                                            DpOffset(pos.x.toDp(), pos.y.toDp())
                                        }
                                        contextMenuVisible = true
                                    }
                                }
                        ) {
                            if (isElongated) {
                                MiniLyricsElongatedContent(
                                    viewModel = viewModel,
                                    windowHeight = windowState.size.height,
                                    showCover = showCover,
                                    showPlayback = showPlayback,
                                    showAdditional = showAdditional,
                                    controlsOnHover = controlsOnHover,
                                    showProgress = showProgress,
                                    transparentBg = transparentBg,
                                )
                            } else {
                                MiniLyricsContent(
                                    viewModel = viewModel,
                                    windowHeight = windowState.size.height,
                                    showCover = showCover,
                                    showPlayback = showPlayback,
                                    showAdditional = showAdditional,
                                    controlsOnHover = controlsOnHover,
                                    showProgress = showProgress,
                                    transparentBg = transparentBg,
                                )
                            }

                            DropdownMenu(
                                expanded = contextMenuVisible,
                                onDismissRequest = { contextMenuVisible = false },
                                offset = contextMenuOffset,
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isPinned) str("mini_player_unpin") else str("mini_player_pin"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.PushPin,
                                            contentDescription = null,
                                            tint = if (isPinned) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    onClick = {
                                        isPinned = !isPinned
                                        contextMenuVisible = false
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str("mini_player_style_elongated"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.ViewStream,
                                            contentDescription = null,
                                            tint = if (isElongated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (isElongated) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        val newStyle = if (isElongated) MiniPlayerStyle.STANDARD else MiniPlayerStyle.ELONGATED
                                        prefs.setMiniPlayerStyle(newStyle)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str("mini_player_transparent_bg"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Opacity,
                                            contentDescription = null,
                                            tint = if (transparentBg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (transparentBg) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        prefs.setMiniPlayerTransparentBg(!transparentBg)
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str("mini_player_show_cover"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Image,
                                            contentDescription = null,
                                            tint = if (showCover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (showCover) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        prefs.setMiniPlayerShowCover(!showCover)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str("mini_player_show_playback_controls"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayCircle,
                                            contentDescription = null,
                                            tint = if (showPlayback) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (showPlayback) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        prefs.setMiniPlayerShowPlaybackControls(!showPlayback)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str("mini_player_show_additional_controls"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Favorite,
                                            contentDescription = null,
                                            tint = if (showAdditional) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (showAdditional) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        prefs.setMiniPlayerShowAdditionalControls(!showAdditional)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str("mini_player_controls_on_hover"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Visibility,
                                            contentDescription = null,
                                            tint = if (controlsOnHover) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (controlsOnHover) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        prefs.setMiniPlayerControlsOnHover(!controlsOnHover)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str("mini_player_hover_effect"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (hoverEffect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (hoverEffect) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        prefs.setMiniPlayerHoverEffect(!hoverEffect)
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str("mini_player_show_progress"),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.GraphicEq,
                                            contentDescription = null,
                                            tint = if (showProgress) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    trailingIcon = {
                                        if (showProgress) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        prefs.setMiniPlayerShowProgress(!showProgress)
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            str("menu_mini_player_hide"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    onClick = {
                                        contextMenuVisible = false
                                        closeMiniPlayer()
                                    },
                                )
                            }

                            // Right edge resize handle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .width(6.dp)
                                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                                    .pointerInput(minWidthPx, maxWidthPx) {
                                        detectDragGestures(
                                            onDragEnd = { saveCurrentBounds() },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val newW = (window.width + dragAmount.x.toInt()).coerceIn(minWidthPx, maxWidthPx)
                                                window.setSize(newW, window.height)
                                            }
                                        )
                                    }
                            )

                            // Bottom edge resize handle
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.S_RESIZE_CURSOR)))
                                    .pointerInput(minHeightPx, maxHeightPx) {
                                        detectDragGestures(
                                            onDragEnd = { saveCurrentBounds() },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val newH = (window.height + dragAmount.y.toInt()).coerceIn(minHeightPx, maxHeightPx)
                                                window.setSize(window.width, newH)
                                            }
                                        )
                                    }
                            )

                            // Bottom-right corner resize grip
                            var isGripHovered by remember { mutableStateOf(false) }
                            val gripInteraction = remember { MutableInteractionSource() }
                            val isHoveredByState by gripInteraction.collectIsHoveredAsState()
                            val gripActive = isGripHovered || isHoveredByState

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(if (isElongated) 14.dp else 20.dp)
                                    .hoverable(gripInteraction)
                                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
                                    .pointerInput(minWidthPx, maxWidthPx, minHeightPx, maxHeightPx) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                isGripHovered = false
                                                saveCurrentBounds()
                                            },
                                            onDragCancel = {
                                                isGripHovered = false
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                isGripHovered = true
                                                val newW = (window.width + dragAmount.x.toInt()).coerceIn(minWidthPx, maxWidthPx)
                                                val newH = (window.height + dragAmount.y.toInt()).coerceIn(minHeightPx, maxHeightPx)
                                                window.setSize(newW, newH)
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant
                                Canvas(
                                    modifier = Modifier
                                        .size(if (isElongated) 8.dp else 11.dp)
                                        .padding(end = if (isElongated) 2.dp else 4.dp, bottom = if (isElongated) 2.dp else 4.dp)
                                ) {
                                    val strokeAlpha = if (gripActive) 0.85f else 0.35f
                                    val strokeWidth = 1.5.dp.toPx()
                                    val strokeColor = outlineColor.copy(alpha = strokeAlpha)
                                    drawLine(
                                        color = strokeColor,
                                        start = Offset(size.width * 0.35f, size.height),
                                        end = Offset(size.width, size.height * 0.35f),
                                        strokeWidth = strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = strokeColor,
                                        start = Offset(size.width * 0.72f, size.height),
                                        end = Offset(size.width, size.height * 0.72f),
                                        strokeWidth = strokeWidth,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniLyricsContent(
    viewModel: PlayerViewModel,
    windowHeight: Dp = 82.dp,
    showCover: Boolean = true,
    showPlayback: Boolean = true,
    showAdditional: Boolean = true,
    controlsOnHover: Boolean = true,
    showProgress: Boolean = true,
    transparentBg: Boolean = false,
) {
    val track = viewModel.currentTrack
    val isPlaying = viewModel.isPlaying
    val lyrics = viewModel.lyricsLines
    val rawPosition = viewModel.currentPosition
    val smoothPosition = com.alananasss.kittytune.ui.player.lyrics.rememberSmoothPosition(
        positionMs = rawPosition,
        isPlaying = isPlaying,
        speed = 1.0f,
    )
    val adjustedPosition = smoothPosition + viewModel.lyricsOffset

    val activeIndex = remember(adjustedPosition, lyrics) {
        if (lyrics.isEmpty()) -1 else LyricsUtils.activeLineIndex(lyrics, adjustedPosition.toLong())
    }
    val activeLine = lyrics.getOrNull(activeIndex)

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val vPadding = (windowHeight.value * 0.12f).coerceIn(6f, 14f).dp
    val artSize = (windowHeight.value - 38f).coerceIn(36f, 60f).dp

    val controlsVisible = !controlsOnHover || isHovered || track == null

    val textShadow = remember(transparentBg) {
        if (transparentBg) {
            androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 1f),
                blurRadius = 4f
            )
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = vPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail Album Cover or Logo
            if (showCover) {
                Box(
                    modifier = Modifier
                        .size(artSize)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (track?.fullResArtwork != null && track.fullResArtwork.isNotBlank()) {
                        AsyncImage(
                            model = track.fullResArtwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Center Area: The Active Single Lyric Line (with smart chunking for long lines)
            BoxWithConstraints(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                val maxChars = remember(maxWidth) {
                    (maxWidth.value / 9.2f).toInt().coerceIn(18, 90)
                }
                if (track != null && activeLine != null && activeLine.text.isNotBlank()) {
                    MiniLyricDisplay(
                        line = activeLine,
                        positionMs = adjustedPosition,
                        wordSync = viewModel.isWordSyncEnabled,
                        fillEffect = viewModel.isAppleMusicEffectEnabled,
                        maxChunkChars = maxChars,
                        textShadow = textShadow,
                    )
                } else if (track != null) {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = track.title ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.displayArtist.ifBlank { track.user?.username.orEmpty() }.ifBlank { str("mini_player_instrumental") },
                            style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(
                            text = "KittyTune",
                            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = str("mini_player_no_track"),
                            style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action Controls
            if (showAdditional || showPlayback) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(150))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (showAdditional && track != null) {
                                IconButton(
                                    onClick = { viewModel.toggleLike() },
                                    modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        imageVector = if (viewModel.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = if (viewModel.isLiked) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (showPlayback) {
                                IconButton(
                                    onClick = { viewModel.smartPrevious() },
                                    modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.togglePlayPause() },
                                    modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.playNext() },
                                    modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Progress bar along the bottom edge
        if (showProgress && viewModel.duration > 0) {
            val progress = (adjustedPosition / viewModel.duration).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Clear of the 22 dp corners, for the same reason as the elongated bar's.
                    .padding(start = 24.dp, end = 24.dp, bottom = 6.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

/**
 * Compact elongated mini-player layout containing only one line of text.
 * Suitable for placing along taskbars, touchpad panels, or minimal desktop docks.
 */
@Composable
private fun MiniLyricsElongatedContent(
    viewModel: PlayerViewModel,
    windowHeight: Dp = 38.dp,
    showCover: Boolean = true,
    showPlayback: Boolean = true,
    showAdditional: Boolean = true,
    controlsOnHover: Boolean = true,
    showProgress: Boolean = true,
    transparentBg: Boolean = false,
) {
    val track = viewModel.currentTrack
    val isPlaying = viewModel.isPlaying
    val lyrics = viewModel.lyricsLines
    val rawPosition = viewModel.currentPosition
    val smoothPosition = com.alananasss.kittytune.ui.player.lyrics.rememberSmoothPosition(
        positionMs = rawPosition,
        isPlaying = isPlaying,
        speed = 1.0f,
    )
    val adjustedPosition = smoothPosition + viewModel.lyricsOffset

    val activeIndex = remember(adjustedPosition, lyrics) {
        if (lyrics.isEmpty()) -1 else LyricsUtils.activeLineIndex(lyrics, adjustedPosition.toLong())
    }
    val activeLine = lyrics.getOrNull(activeIndex)

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val controlsVisible = !controlsOnHover || isHovered || track == null

    val textShadow = remember(transparentBg) {
        if (transparentBg) {
            androidx.compose.ui.graphics.Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 1f),
                blurRadius = 4f
            )
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Optional miniature album cover or music note icon
            if (showCover) {
                val coverSize = (windowHeight.value - 12f).coerceIn(18f, 28f).dp
                Box(
                    modifier = Modifier
                        .size(coverSize)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (transparentBg) 0.6f else 1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (track?.fullResArtwork != null && track.fullResArtwork.isNotBlank()) {
                        AsyncImage(
                            model = track.fullResArtwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size((coverSize.value * 0.7f).dp)
                        )
                    }
                }
            }

            // Center: Single line text (Lyrics or Title • Artist or KittyTune)
            BoxWithConstraints(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                val maxChars = remember(maxWidth) {
                    (maxWidth.value / 8.5f).toInt().coerceIn(20, 140)
                }

                if (track != null && activeLine != null && activeLine.text.isNotBlank()) {
                    MiniLyricDisplay(
                        line = activeLine,
                        positionMs = adjustedPosition,
                        wordSync = viewModel.isWordSyncEnabled,
                        fillEffect = viewModel.isAppleMusicEffectEnabled,
                        maxChunkChars = maxChars,
                        textShadow = textShadow,
                        compact = true,
                    )
                } else if (track != null) {
                    val trackInfo = remember(track.title, track.displayArtist, track.user?.username) {
                        val artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
                        val title = track.title.orEmpty()
                        if (artist.isNotBlank()) "$title • $artist" else title
                    }
                    Text(
                        text = trackInfo,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            shadow = textShadow,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "KittyTune",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            shadow = textShadow,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Compact Action Controls
            if (showAdditional || showPlayback) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (showAdditional && track != null) {
                            IconButton(
                                onClick = { viewModel.toggleLike() },
                                modifier = Modifier.size(26.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = if (viewModel.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (viewModel.isLiked) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        if (showPlayback) {
                            IconButton(
                                onClick = { viewModel.smartPrevious() },
                                modifier = Modifier.size(26.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipPrevious,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.playNext() },
                                modifier = Modifier.size(26.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                                shapes = IconButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Hairline progress bar along the bottom edge
        if (showProgress && viewModel.duration > 0) {
            val progress = (adjustedPosition / viewModel.duration).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // Inset past the 10 dp corners and lifted off the edge: laid on the border itself,
                    // its ends ran into the rounding and it read as a line sticking out of the bar.
                    .padding(start = 14.dp, end = 14.dp, bottom = 3.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (transparentBg) 0.25f else 0.4f),
            )
        }
    }
}

/**
 * Displays a single lyric line with smooth karaoke fill.
 * If the line exceeds visible length, it partitions the line into natural readable chunks
 * and seamlessly transitions to the next part of the line when the voice progresses through it.
 */
@Composable
private fun MiniLyricDisplay(
    line: LyricLine,
    positionMs: Float,
    wordSync: Boolean,
    fillEffect: Boolean,
    maxChunkChars: Int = 36,
    textShadow: androidx.compose.ui.graphics.Shadow? = null,
    compact: Boolean = false,
) {
    val words = if (wordSync && line.words.isNotEmpty()) line.words else emptyList()
    val fullText = remember(line.text, words) {
        if (words.isNotEmpty()) words.joinToString("") { it.text } else line.text
    }

    // Partition long lines into ~maxChunkChars-character chunks on word boundaries
    val chunks = remember(fullText, words, maxChunkChars) {
        splitIntoChunks(fullText, words, maxChunkChars = maxChunkChars)
    }

    // Determine which chunk is active based on current playback progress
    val activeChunkIndex = remember(chunks, positionMs) {
        resolveActiveChunk(chunks, positionMs)
    }
    val currentChunk = chunks.getOrElse(activeChunkIndex) { chunks.first() }

    AnimatedContent(
        targetState = currentChunk,
        transitionSpec = {
            (slideInVertically { height -> height / 2 } + fadeIn(tween(200)))
                .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(200)))
        },
        label = "miniLyricChunkAnimation"
    ) { chunk ->
        val chunkWords = chunk.words
        val chunkText = chunk.text

        val baseStyle = if (compact) {
            MaterialTheme.typography.bodyMedium.copy(shadow = textShadow)
        } else {
            MaterialTheme.typography.titleMedium.copy(shadow = textShadow)
        }

        if (chunkWords.isEmpty() || !wordSync) {
            Text(
                text = chunkText,
                style = baseStyle.copy(fontWeight = if (compact) FontWeight.SemiBold else FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            val ranges = remember(chunkWords) {
                var start = 0
                chunkWords.map { word ->
                    val range = start to start + word.text.length
                    start += word.text.length
                    range
                }
            }

            val activeColor = MaterialTheme.colorScheme.primary
            val unsungColor = if (textShadow != null) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            }
            val style = baseStyle.copy(fontWeight = if (compact) FontWeight.Bold else FontWeight.ExtraBold)

            if (!fillEffect) {
                val coloured = buildAnnotatedString {
                    for (word in chunkWords) {
                        val reached = positionMs >= word.startTime
                        withStyle(SpanStyle(color = if (reached) activeColor else unsungColor)) {
                            append(word.text)
                        }
                    }
                }
                Text(
                    text = coloured,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val position = if (line.endTime > line.startTime) {
                    positionMs.coerceAtMost(line.endTime.toFloat())
                } else {
                    positionMs
                }

                Box {
                    Text(
                        text = chunkText,
                        style = style,
                        color = unsungColor,
                        maxLines = 1,
                        onTextLayout = { layout = it }
                    )
                    Text(
                        text = chunkText,
                        style = style,
                        color = activeColor,
                        maxLines = 1,
                        modifier = Modifier.drawWithContent {
                            val res = layout ?: return@drawWithContent
                            clipPath(sungPath(res, chunkWords, ranges, chunkText.length, position)) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    )
                }
            }
        }
    }
}

internal data class LyricChunk(
    val text: String,
    val words: List<LyricWord>,
    val startTime: Long,
    val endTime: Long,
)

/**
 * Splits a line into sub-chunks of up to [maxChunkChars] length on word boundaries.
 */
internal fun splitIntoChunks(
    fullText: String,
    words: List<LyricWord>,
    maxChunkChars: Int = 36,
): List<LyricChunk> {
    if (fullText.length <= maxChunkChars || words.isEmpty()) {
        val start = words.firstOrNull()?.startTime ?: 0L
        val end = words.lastOrNull()?.endTime ?: 0L
        return listOf(LyricChunk(fullText, words, start, end))
    }

    val chunks = mutableListOf<LyricChunk>()
    var currentChunkWords = mutableListOf<LyricWord>()
    var currentLength = 0

    for (word in words) {
        val nextLength = currentLength + word.text.length
        if (nextLength > maxChunkChars && currentChunkWords.isNotEmpty()) {
            val chunkText = currentChunkWords.joinToString("") { it.text }
            val start = currentChunkWords.first().startTime
            val end = currentChunkWords.last().endTime
            chunks.add(LyricChunk(chunkText, currentChunkWords, start, end))
            currentChunkWords = mutableListOf()
            currentLength = 0
        }
        currentChunkWords.add(word)
        currentLength += word.text.length
    }

    if (currentChunkWords.isNotEmpty()) {
        val chunkText = currentChunkWords.joinToString("") { it.text }
        val start = currentChunkWords.first().startTime
        val end = currentChunkWords.last().endTime
        chunks.add(LyricChunk(chunkText, currentChunkWords, start, end))
    }

    return if (chunks.isEmpty()) {
        listOf(LyricChunk(fullText, emptyList(), 0L, 0L))
    } else {
        chunks
    }
}

internal fun resolveActiveChunk(chunks: List<LyricChunk>, positionMs: Float): Int {
    if (chunks.size <= 1) return 0
    for (i in chunks.indices) {
        val chunk = chunks[i]
        if (positionMs <= chunk.endTime || i == chunks.lastIndex) {
            return i
        }
    }
    return 0
}

private fun sungPath(
    layout: TextLayoutResult,
    words: List<LyricWord>,
    ranges: List<Pair<Int, Int>>,
    textLength: Int,
    positionMs: Float,
): Path {
    val path = Path()
    val lastIndex = (textLength - 1).coerceAtLeast(0)

    for (i in words.indices) {
        val word = words[i]
        val (from, to) = ranges[i]
        if (from >= to) continue

        if (positionMs >= word.endTime) {
            for (c in from until to) path.addRect(layout.getBoundingBox(c.coerceIn(0, lastIndex)))
            continue
        }
        if (positionMs < word.startTime) continue

        val span = (word.endTime - word.startTime).coerceAtLeast(1L)
        val progress = ((positionMs - word.startTime) / span).coerceIn(0f, 1f)
        val exact = progress * (to - from)
        val whole = exact.toInt()
        for (c in from until from + whole) path.addRect(layout.getBoundingBox(c.coerceIn(0, lastIndex)))

        val partial = from + whole
        if (partial < to) {
            val box = layout.getBoundingBox(partial.coerceIn(0, lastIndex))
            val edge = box.left + (box.right - box.left) * (exact - whole)
            path.addRect(Rect(box.left, box.top, edge, box.bottom))
        }
    }
    return path
}

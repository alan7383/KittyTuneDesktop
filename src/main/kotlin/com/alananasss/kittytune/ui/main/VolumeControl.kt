package com.alananasss.kittytune.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import java.awt.Cursor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Mute button, the two gaps and the percentage: everything in the inline row that is not the track. */
private val INLINE_OVERHEAD = 36.dp + 6.dp + 8.dp + 40.dp

/** Shortest track worth aiming at; with less room the bar uses the hover popup instead. */
private val MIN_TRACK_WIDTH = 96.dp

/** And the widest it grows to on a roomy window. */
private val MAX_TRACK_WIDTH = 180.dp

/** Space either side of the track, so the thumb is never cut off at 0 % or 100 %. */
private val TRACK_INSET = 7.dp

/**
 * The player bar's volume control: an inline track with the level always written beside it, or —
 * when the user prefers it, or the window is too narrow for a usable track — a speaker button that
 * opens a vertical slider on hover.
 *
 * The inline one replaces a stock Material slider whose tall bar thumb and thick track were built
 * for touch: hard to hit precisely in a 64 dp bar, and it never said what level it was at.
 */
@Composable
internal fun VolumeControl(
    volume: Float,
    preferVertical: Boolean,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onVolumeScrolled: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val roomForTrack = maxWidth - INLINE_OVERHEAD
        if (preferVertical || roomForTrack < MIN_TRACK_WIDTH) {
            VolumeHoverControl(volume, onVolumeChange, onVolumeChangeFinished, onVolumeScrolled, onToggleMute)
        } else {
            InlineVolumeControl(
                volume = volume,
                trackWidth = roomForTrack.coerceAtMost(MAX_TRACK_WIDTH),
                onVolumeChange = onVolumeChange,
                onVolumeChangeFinished = onVolumeChangeFinished,
                onVolumeScrolled = onVolumeScrolled,
                onToggleMute = onToggleMute,
            )
        }
    }
}

@Composable
private fun InlineVolumeControl(
    volume: Float,
    trackWidth: Dp,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onVolumeScrolled: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                .volumeWheel({ volume }, onVolumeScrolled)
                .clickable(onClick = onToggleMute),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = volumeIcon(volume),
                contentDescription = "Mute",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        VolumeTrack(
            volume = volume,
            onVolumeChange = onVolumeChange,
            onVolumeChangeFinished = onVolumeChangeFinished,
            modifier = Modifier
                .width(trackWidth)
                .volumeWheel({ volume }, onVolumeScrolled),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = volumePercentLabel(volume),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(40.dp),
        )
    }
}

/**
 * A slim track that thickens and shows its thumb under the pointer. A press anywhere on it jumps
 * there and dragging follows the pointer, even past the ends; the level is saved on release.
 */
@Composable
private fun VolumeTrack(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    var isDragging by remember { mutableStateOf(false) }
    val isActive = isHovered || isDragging

    val trackHeight by animateDpAsState(if (isActive) 6.dp else 4.dp, spring(stiffness = 700f), label = "volumeTrack")
    val thumbRadius by animateDpAsState(if (isActive) 7.dp else 0.dp, spring(stiffness = 700f), label = "volumeThumb")

    val activeColor = MaterialTheme.colorScheme.primary
    // Material's own inactive-track colour: visible on the bar's container, unlike a surface tone.
    val inactiveColor = MaterialTheme.colorScheme.secondaryContainer
    val latestOnChange by rememberUpdatedState(onVolumeChange)
    val latestOnFinished by rememberUpdatedState(onVolumeChangeFinished)

    Canvas(
        modifier = modifier
            .height(28.dp)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .semantics {
                contentDescription = "Volume"
                progressBarRangeInfo = ProgressBarRangeInfo(volume, 0f..1f)
                setProgress { target ->
                    latestOnChange(target.coerceIn(0f, 1f))
                    latestOnFinished()
                    true
                }
            }
            .pointerInput(Unit) {
                fun levelAt(x: Float): Float {
                    val inset = TRACK_INSET.toPx()
                    return ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isDragging = true
                    down.consume()
                    latestOnChange(levelAt(down.position.x))
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.position != change.previousPosition) {
                                change.consume()
                                latestOnChange(levelAt(change.position.x))
                            }
                        }
                    } finally {
                        isDragging = false
                        latestOnFinished()
                    }
                }
            },
    ) {
        val inset = TRACK_INSET.toPx()
        val width = (size.width - 2 * inset).coerceAtLeast(0f)
        val heightPx = trackHeight.toPx()
        val top = (size.height - heightPx) / 2f
        val corner = CornerRadius(heightPx / 2f)
        val filled = width * volume.coerceIn(0f, 1f)

        drawRoundRect(inactiveColor, Offset(inset, top), Size(width, heightPx), corner)
        if (filled > 0f) drawRoundRect(activeColor, Offset(inset, top), Size(filled, heightPx), corner)
        if (thumbRadius > 0.dp) {
            drawCircle(activeColor, thumbRadius.toPx(), Offset(inset + filled, size.height / 2f))
        }
    }
}

private fun volumeIcon(volume: Float): ImageVector = when {
    volume <= 0.001f -> Icons.AutoMirrored.Filled.VolumeOff
    volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
    else -> Icons.AutoMirrored.Filled.VolumeUp
}

private fun volumePercentLabel(volume: Float): String = "${(volume * 100).roundToInt()}%"

/**
 * Scroll wheel over any volume control raises or lowers it, in 5% steps. Wheel deltas are
 * positive downwards, so the sign is inverted to match the direction the user pushed.
 *
 * [currentVolume] and [onVolumeChange] are read through [rememberUpdatedState] because the
 * `pointerInput` block is keyed on Unit and therefore never restarts: capturing them directly
 * froze the level at whatever it was when the control first composed, so scrolling only ever
 * moved one step either side of that stale value.
 */
@Composable
private fun Modifier.volumeWheel(
    currentVolume: () -> Float,
    onVolumeChange: (Float) -> Unit,
): Modifier {
    val volume by androidx.compose.runtime.rememberUpdatedState(currentVolume)
    val onChange by androidx.compose.runtime.rememberUpdatedState(onVolumeChange)
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta == 0f) continue
                onChange((volume() - delta * 0.05f).coerceIn(0f, 1f))
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/**
 * Speaker button that reveals a vertical volume slider on hover, floating above the bar.
 *
 * Hover rather than click, because a click on the speaker already means mute and a control that
 * needed two different clicks to do two things was the confusing part. The panel stays up while
 * the pointer is over either the button or the panel itself, with a short grace period so the
 * gap between them does not dismiss it mid-reach.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalComposeUiApi::class,
)
@Composable
private fun VolumeHoverControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onVolumeScrolled: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    var overButton by remember { mutableStateOf(false) }
    var overPanel by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(overButton, overPanel) {
        if (overButton || overPanel) {
            expanded = true
        } else {
            delay(250)
            expanded = false
        }
    }

    val levelIcon = volumeIcon(volume)

    Box {
        val buttonShape by animateDpAsState(
            targetValue = if (expanded) 14.dp else 20.dp,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
            label = "volumeButtonShape",
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(buttonShape))
                .background(
                    if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    else Color.Transparent
                )
                .onPointerEvent(PointerEventType.Enter) { overButton = true }
                .onPointerEvent(PointerEventType.Exit) { overButton = false }
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                .volumeWheel({ volume }, onVolumeScrolled)
                .clickable(indication = ripple(), interactionSource = remember { MutableInteractionSource() }) {
                    onToggleMute()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                levelIcon,
                contentDescription = "Volume",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        if (expanded) {
            Popup(
                popupPositionProvider = AboveAnchorCentered,
                properties = PopupProperties(focusable = false),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .onPointerEvent(PointerEventType.Enter) { overPanel = true }
                        .onPointerEvent(PointerEventType.Exit) { overPanel = false }
                        .volumeWheel({ volume }, onVolumeScrolled),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = volumePercentLabel(volume),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        val state = remember {
                            SliderState(volume, 0, { onVolumeChangeFinished() }, 0f..1f)
                        }
                        // Follow changes that did not come from this slider — the mute button,
                        // the wheel, a keyboard shortcut — instead of only seeding once.
                        LaunchedEffect(volume) {
                            if (kotlin.math.abs(state.value - volume) > 0.001f) state.value = volume
                        }
                        LaunchedEffect(state) {
                            snapshotFlow { state.value }.collect { onVolumeChange(it) }
                        }
                        VerticalSlider(
                            state = state,
                            // A volume slider fills from the bottom. The default direction puts
                            // the origin at the top, which is what made it read upside down.
                            topToBottom = false,
                            modifier = Modifier.height(150.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Icon(
                            levelIcon,
                            contentDescription = "Mute",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                                .clickable { onToggleMute() },
                        )
                    }
                }
            }
        }
    }
}

/** Places a popup directly above its anchor, horizontally centred and clamped to the window. */
private object AboveAnchorCentered : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        return IntOffset(
            x.coerceIn(0, maxX),
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0),
        )
    }
}

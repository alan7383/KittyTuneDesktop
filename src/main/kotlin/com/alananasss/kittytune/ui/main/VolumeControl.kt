package com.alananasss.kittytune.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.alananasss.kittytune.data.local.PlayerSliderStyle
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    isPlaying: Boolean,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onVolumeScrolled: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    val style = rememberVolumeStyle()
    androidx.compose.runtime.CompositionLocalProvider(LocalWaveMoving provides isPlaying) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val roomForTrack = maxWidth - INLINE_OVERHEAD
        if (preferVertical || roomForTrack < MIN_TRACK_WIDTH) {
            VolumeHoverControl(volume, style, onVolumeChange, onVolumeChangeFinished, onVolumeScrolled, onToggleMute)
        } else {
            InlineVolumeControl(
                volume = volume,
                style = style,
                trackWidth = roomForTrack.coerceAtMost(MAX_TRACK_WIDTH),
                onVolumeChange = onVolumeChange,
                onVolumeChangeFinished = onVolumeChangeFinished,
                onVolumeScrolled = onVolumeScrolled,
                onToggleMute = onToggleMute,
            )
        }
    }
    }
}

/**
 * The volume track alone, in the style the user picked, for a screen that lays out its own speaker and
 * level — the full player, whose colours come from the artwork rather than the theme.
 */
@Composable
internal fun StyledVolumeTrack(
    volume: Float,
    isPlaying: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalWaveMoving provides isPlaying) {
        VolumeTrack(
            volume = volume,
            style = rememberVolumeStyle(),
            onVolumeChange = onVolumeChange,
            onVolumeChangeFinished = onVolumeChangeFinished,
            modifier = modifier,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
        )
    }
}

/** Whether the wavy volume track should be moving: only while music plays, like the seek bar. */
private val LocalWaveMoving = androidx.compose.runtime.compositionLocalOf { false }

@Composable
private fun InlineVolumeControl(
    volume: Float,
    style: PlayerSliderStyle,
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
            style = style,
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
 * The volume track, drawn in the same style as the seek bar the user picked — plain bar, slim, wavy or
 * squiggly — so the two sliders in the player bar read as one family. Horizontal inline, vertical in the
 * popup the bar falls back to on narrow windows; both used to be different stock sliders.
 *
 * A press anywhere jumps there and dragging follows the pointer, even past the ends; the level is saved
 * on release. The wave is still — it is the seek bar's motion, not the volume's.
 */
@Composable
private fun VolumeTrack(
    volume: Float,
    style: PlayerSliderStyle,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    // Material's own inactive-track colour: visible on the bar's container, unlike a surface tone.
    inactiveColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    var isDragging by remember { mutableStateOf(false) }
    val isActive = isHovered || isDragging

    val spec = remember(style) { VolumeTrackSpec.of(style) }
    val thickness by animateDpAsState(
        if (isActive) spec.activeThickness else spec.thickness, spring(stiffness = 700f), label = "volumeTrack",
    )
    // The dot jumps a little when grabbed, as the seek bar's does: bouncy while held, settling on release.
    val thumbGrow by animateFloatAsState(
        when {
            isDragging -> 1.3f
            isActive -> 1f
            else -> 0f
        },
        spring(dampingRatio = 0.45f, stiffness = 500f),
        label = "volumeThumb",
    )
    // The wave moves while music plays and flattens to a line when it stops, in step with the seek bar.
    val isMoving = LocalWaveMoving.current && com.alananasss.kittytune.core.LocalWindowSeen.current && spec.amplitude > 0.dp
    val waveScale by animateFloatAsState(if (LocalWaveMoving.current) 1f else 0f, androidx.compose.animation.core.tween(400), label = "volumeWave")
    val phase = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val waveLengthPx = with(density) { spec.wavelength.toPx() }
    val waveSpeedPx = with(density) { (spec.wavelength / 2f).toPx() }
    androidx.compose.runtime.LaunchedEffect(isMoving, waveLengthPx) {
        while (isMoving) {
            phase.floatValue = com.alananasss.kittytune.ui.player.slider.wavePhasePx(waveSpeedPx, waveLengthPx)
            kotlinx.coroutines.delay(33)
        }
    }

    val latestOnChange by rememberUpdatedState(onVolumeChange)
    val latestOnFinished by rememberUpdatedState(onVolumeChangeFinished)

    Canvas(
        modifier = modifier
            .then(if (vertical) Modifier.width(28.dp) else Modifier.height(28.dp))
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
            .pointerInput(vertical) {
                fun levelAt(position: Offset): Float {
                    val inset = TRACK_INSET.toPx()
                    val raw = if (vertical) {
                        1f - (position.y - inset) / (size.height - 2 * inset).coerceAtLeast(1f)
                    } else {
                        (position.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)
                    }
                    return when {
                        raw <= 0.015f -> 0f
                        raw >= 0.985f -> 1f
                        else -> raw.coerceIn(0f, 1f)
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isDragging = true
                    down.consume()
                    latestOnChange(levelAt(down.position))
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.position != change.previousPosition) {
                                change.consume()
                                latestOnChange(levelAt(change.position))
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
        val length = ((if (vertical) size.height else size.width) - 2 * inset).coerceAtLeast(0f)
        val cross = (if (vertical) size.width else size.height) / 2f
        val stroke = thickness.toPx()
        val filled = length * volume.coerceIn(0f, 1f)

        val barWidth = 4.dp.toPx()
        val gapMargin = 3.dp.toPx()

        if (spec.amplitude == 0.dp) {
            // Straight track (BAR and SLIM)
            val gapScale = if (spec.gapAroundThumb) {
                (filled / (barWidth + gapMargin * 2f)).coerceIn(0f, 1f)
            } else 0f
            val actualGap = if (spec.gapAroundThumb) gapMargin * gapScale else 0f
            val actualThumbHalf = if (spec.gapAroundThumb) (barWidth / 2f) * gapScale else 0f

            val remaining = length - filled
            val inactiveGapScale = if (spec.gapAroundThumb) {
                (remaining / (barWidth + gapMargin * 2f)).coerceIn(0f, 1f)
            } else 0f
            val actualInactiveGap = if (spec.gapAroundThumb) gapMargin * inactiveGapScale else 0f
            val actualInactiveThumbHalf = if (spec.gapAroundThumb) (barWidth / 2f) * inactiveGapScale else 0f

            if (vertical) {
                val bottomY = size.height - inset
                val topY = inset
                val thumbY = bottomY - filled

                // Active track (from bottom up towards thumb)
                if (volume > 0.001f) {
                    val activeTop = (thumbY + actualThumbHalf + actualGap).coerceAtMost(bottomY)
                    val activeHeight = (bottomY - activeTop).coerceAtLeast(0f)
                    if (activeHeight > 0f) {
                        val corner = CornerRadius(minOf(stroke / 2f, activeHeight / 2f))
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(cross - stroke / 2f, activeTop),
                            size = Size(stroke, activeHeight),
                            cornerRadius = corner,
                        )
                    }
                }

                // Inactive track (from thumb up towards top)
                if (volume < 0.999f) {
                    val inactiveBottom = (thumbY - actualInactiveThumbHalf - actualInactiveGap).coerceAtLeast(topY)
                    val inactiveHeight = (inactiveBottom - topY).coerceAtLeast(0f)
                    if (inactiveHeight > 0f) {
                        val corner = CornerRadius(minOf(stroke / 2f, inactiveHeight / 2f))
                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(cross - stroke / 2f, topY),
                            size = Size(stroke, inactiveHeight),
                            cornerRadius = corner,
                        )
                    }
                }
            } else {
                val startX = inset
                val endX = inset + length
                val thumbX = startX + filled

                // Active track (from left up towards thumb)
                if (volume > 0.001f) {
                    val activeRight = (thumbX - actualThumbHalf - actualGap).coerceAtLeast(startX)
                    val activeWidth = (activeRight - startX).coerceAtLeast(0f)
                    if (activeWidth > 0f) {
                        val corner = CornerRadius(minOf(stroke / 2f, activeWidth / 2f))
                        drawRoundRect(
                            color = activeColor,
                            topLeft = Offset(startX, cross - stroke / 2f),
                            size = Size(activeWidth, stroke),
                            cornerRadius = corner,
                        )
                    }
                }

                // Inactive track (from thumb up towards right)
                if (volume < 0.999f) {
                    val inactiveLeft = (thumbX + actualInactiveThumbHalf + actualInactiveGap).coerceAtMost(endX)
                    val inactiveWidth = (endX - inactiveLeft).coerceAtLeast(0f)
                    if (inactiveWidth > 0f) {
                        val corner = CornerRadius(minOf(stroke / 2f, inactiveWidth / 2f))
                        drawRoundRect(
                            color = inactiveColor,
                            topLeft = Offset(inactiveLeft, cross - stroke / 2f),
                            size = Size(inactiveWidth, stroke),
                            cornerRadius = corner,
                        )
                    }
                }
            }
        } else {
            // Wavy / squiggly tracks
            fun at(along: Float, across: Float = 0f): Offset =
                if (vertical) Offset(cross + across, size.height - inset - along)
                else Offset(inset + along, cross + across)

            val activeEnd = if (volume <= 0.001f) 0f else filled
            val inactiveStart = if (volume >= 0.999f) length else filled

            if (inactiveStart < length) {
                drawLine(inactiveColor, at(inactiveStart), at(length), stroke, StrokeCap.Round)
            }
            if (activeEnd > 0f) {
                val amplitude = spec.amplitude.toPx() * waveScale
                val k = (2.0 * Math.PI / spec.wavelength.toPx()).toFloat()
                val shift = phase.floatValue
                val wave = Path()
                var t = 0f
                val start = at(0f)
                wave.moveTo(start.x, start.y)
                while (t < activeEnd) {
                    t = (t + 2f).coerceAtMost(activeEnd)
                    val p = at(t, amplitude * kotlin.math.sin(k * (t - shift)))
                    wave.lineTo(p.x, p.y)
                }
                drawPath(wave, activeColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }

        // Thumb: a bar for the bar style, a dot for the others
        if (spec.gapAroundThumb) {
            val barLength = spec.thumbLength.toPx() * (1f + 0.2f * thumbGrow)
            val thumbPos = if (vertical) {
                Offset(cross, size.height - inset - filled)
            } else {
                Offset(inset + filled, cross)
            }
            val topLeft = if (vertical) {
                Offset(thumbPos.x - barLength / 2f, thumbPos.y - barWidth / 2f)
            } else {
                Offset(thumbPos.x - barWidth / 2f, thumbPos.y - barLength / 2f)
            }
            val barSize = if (vertical) Size(barLength, barWidth) else Size(barWidth, barLength)
            drawRoundRect(activeColor, topLeft, barSize, CornerRadius(barWidth / 2f))
        } else if (thumbGrow > 0f) {
            val dotPos = if (vertical) {
                Offset(cross, size.height - inset - filled)
            } else {
                Offset(inset + filled, cross)
            }
            drawCircle(activeColor, 7.dp.toPx() * thumbGrow.coerceAtLeast(0f), dotPos)
        }
    }
}

/** How each seek-bar style translates to the volume track. */
private data class VolumeTrackSpec(
    val thickness: Dp,
    val activeThickness: Dp,
    val amplitude: Dp,
    val wavelength: Dp,
    val gapAroundThumb: Boolean,
    val thumbLength: Dp,
) {
    companion object {
        fun of(style: PlayerSliderStyle) = when (style) {
            PlayerSliderStyle.BAR -> VolumeTrackSpec(8.dp, 10.dp, 0.dp, 1.dp, gapAroundThumb = true, thumbLength = 20.dp)
            PlayerSliderStyle.SLIM -> VolumeTrackSpec(4.dp, 6.dp, 0.dp, 1.dp, gapAroundThumb = false, thumbLength = 0.dp)
            PlayerSliderStyle.WAVY -> VolumeTrackSpec(4.dp, 5.dp, 2.5.dp, androidx.compose.material3.WavyProgressIndicatorDefaults.LinearDeterminateWavelength, gapAroundThumb = false, thumbLength = 0.dp)
            PlayerSliderStyle.SQUIGGLY -> VolumeTrackSpec(3.dp, 4.dp, 3.dp, 12.dp, gapAroundThumb = false, thumbLength = 0.dp)
        }
    }
}

/** The volume track's own style if one was picked, the seek bar's otherwise; re-read when preferences change. */
@Composable
private fun rememberVolumeStyle(): PlayerSliderStyle {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) {
        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences()
        prefs.getVolumeSliderStyle() ?: prefs.getPlayerSliderStyle()
    }
}

internal fun volumeIcon(volume: Float): ImageVector =
    if (volume <= 0.001f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp

internal fun volumePercentLabel(volume: Float): String {
    if (volume <= 0.001f) return "0%"
    val percent = (volume * 100).roundToInt()
    return "${maxOf(1, percent)}%"
}

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
    style: PlayerSliderStyle,
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
                        VolumeTrack(
                            volume = volume,
                            style = style,
                            onVolumeChange = onVolumeChange,
                            onVolumeChangeFinished = onVolumeChangeFinished,
                            vertical = true,
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

/** A still volume track at 60 %, for the settings' previews. */
@Composable
internal fun VolumeTrackPreview(style: PlayerSliderStyle, vertical: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.runtime.CompositionLocalProvider(LocalWaveMoving provides true) {
        VolumeTrack(volume = 0.6f, style = style, onVolumeChange = {}, onVolumeChangeFinished = {}, modifier = modifier, vertical = vertical)
    }
}

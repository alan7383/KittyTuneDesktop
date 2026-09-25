package com.alananasss.kittytune.ui.player.slider

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.times
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavySliderExpressive(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueCommit: ((Float) -> Unit)? = null,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    isPlaying: Boolean = true,
    isVisible: Boolean = true,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    trackEdgePadding: Dp = thumbRadius,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f,
    waveAmplitudeWhenPlaying: Dp = 4.dp,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
    semanticsLabel: String? = null,
    semanticsProgressStep: Float = 0.01f
) {
    val density = LocalDensity.current
    // Nobody can see the window: stop the wave and the per-frame smoothing, which would otherwise keep
    // the whole window repainting at 60 fps for as long as music plays.
    val isSeen = com.alananasss.kittytune.core.LocalWindowSeen.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }

    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    val latestValue by rememberUpdatedState(value)
    val latestValueRange by rememberUpdatedState(valueRange)
    val normalizedValueState = remember {
        derivedStateOf {
            val v = latestValue()
            val range = latestValueRange
            if (range.endInclusive == range.start) 0f
            else ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        }
    }

    val safeSemanticsStep = semanticsProgressStep.coerceIn(0.005f, 0.25f)
    val semanticNormalizedValueState = remember(safeSemanticsStep) {
        derivedStateOf {
            val norm = normalizedValueState.value
            ((norm / safeSemanticsStep).roundToInt() * safeSemanticsStep).coerceIn(0f, 1f)
        }
    }
    val semanticSliderValueState = remember {
        derivedStateOf {
            val range = latestValueRange
            range.start + semanticNormalizedValueState.value * (range.endInclusive - range.start)
        }
    }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnValueCommit by rememberUpdatedState(onValueCommit)
    var isPointerSeeking by remember { mutableStateOf(false) }
    val isInteracting = isPointerSeeking

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !isInteracting) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude"
    )

    val currentHalfWidth = remember(thumbRadius, strokeWidth) {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val radius = thumbRadius
            val halfStroke = strokeWidth * 0.6f
            radius * (1f - fraction) + halfStroke * fraction
        }
    }

    val dynamicGapSize = remember {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val idleGap = 6.dp
            val draggingGap = currentHalfWidth.value + 1.2.dp
            idleGap + (draggingGap - idleGap) * fraction
        }
    }

    // What is drawn, and how far the wave has travelled. Both move on one 30 fps tick driven by
    // `delay`, which — unlike awaiting frames — does not itself ask for any. The previous version ran
    // Material's wave animation and a per-frame glide, each requesting every frame: the whole window
    // was re-rendered at 60 fps for as long as music played, about a quarter of a CPU core.
    val renderedNormalizedProgress = remember { mutableFloatStateOf(normalizedValueState.value) }
    val wavePhasePx = remember { mutableFloatStateOf(0f) }
    val wavelengthPx = with(density) { wavelength.toPx() }.coerceAtLeast(1f)
    val waveSpeedPx = with(density) { waveSpeed.toPx() }

    // Jumps land at once: a seek, a new track, a drag, or anything while the glide is not running.
    val isGliding = isSeen && isPlaying && enabled && !isInteracting
    LaunchedEffect(isGliding, valueRange) {
        snapshotFlow { normalizedValueState.value }.collect { target ->
            val current = renderedNormalizedProgress.floatValue
            if (!isGliding || target == 0f || abs(current - target) > 0.08f) {
                renderedNormalizedProgress.floatValue = target
            }
        }
    }
    LaunchedEffect(isGliding, wavelengthPx, waveSpeedPx) {
        if (!isGliding) return@LaunchedEffect
        var last = System.nanoTime()
        while (isActive) {
            delay(WAVE_TICK_MS)
            val now = System.nanoTime()
            val seconds = (now - last) / 1_000_000_000f
            last = now
            wavePhasePx.floatValue = (wavePhasePx.floatValue + waveSpeedPx * seconds) % wavelengthPx
            // Position updates arrive about every 250 ms; closing the gap over that span keeps the
            // thumb moving steadily instead of stepping four times a second.
            val target = normalizedValueState.value
            val current = renderedNormalizedProgress.floatValue
            renderedNormalizedProgress.floatValue =
                if (abs(target - current) < 0.0005f) target
                else current + (target - current) * (seconds / 0.25f).coerceAtMost(1f)
        }
    }

    val containerHeight = max(44.dp, max(WavyProgressIndicatorDefaults.LinearContainerHeight, max(thumbRadius * 2, thumbLineHeightWhenInteracting)))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clearAndSetSemantics {
                if (!semanticsLabel.isNullOrBlank()) {
                    contentDescription = semanticsLabel
                }
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = semanticSliderValueState.value,
                    range = valueRange.start..valueRange.endInclusive,
                    steps = 0
                )
                if (enabled) {
                    setProgress { requested ->
                        val coerced = requested.coerceIn(valueRange.start, valueRange.endInclusive)
                        latestOnValueChange(coerced)
                        latestOnValueCommit?.invoke(coerced)
                        latestOnValueChangeFinished?.invoke()
                        true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Spacer(modifier = Modifier.fillMaxWidth().height(containerHeight))

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isVisible) return@Canvas
            val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
            val trackStart = edgePaddingPx
            val trackEnd = size.width - edgePaddingPx
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val thumbY = size.height / 2
            val renderedProgress = renderedNormalizedProgress.floatValue

            fun lerp(start: Float, stop: Float, fraction: Float): Float {
                return start + (stop - start) * fraction
            }

            val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.2f, thumbInteractionFraction)
            val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
            val rawThumbX = trackStart + (trackWidth * renderedProgress)
            val minThumbCenter = (currentWidth / 2f).coerceAtMost(size.width / 2f)
            val maxThumbCenter = (size.width - currentWidth / 2f).coerceAtLeast(minThumbCenter)
            val thumbX = rawThumbX.coerceIn(minThumbCenter, maxThumbCenter)

            // The track: a wave up to the thumb, a gap either side of it, then a flat line with a
            // stop dot at the end. Read here, in drawing, so ticks repaint without recomposing.
            val halfGap = with(density) { dynamicGapSize.value.toPx() } *
                (1.0f + 0.1573f * animatedAmplitude * animatedAmplitude)
            val amplitudePx = with(density) { waveAmplitudeWhenPlaying.toPx() } * animatedAmplitude
            val activeEnd = (thumbX - halfGap).coerceAtLeast(trackStart)
            val inactiveStart = (thumbX + halfGap).coerceAtMost(trackEnd)
            if (activeEnd > trackStart) {
                val wave = androidx.compose.ui.graphics.Path()
                var x = trackStart
                val phase = wavePhasePx.floatValue
                val k = (2.0 * Math.PI / wavelengthPx).toFloat()
                wave.moveTo(x, thumbY + amplitudePx * kotlin.math.sin(k * (x - phase)))
                while (x < activeEnd) {
                    x = (x + WAVE_STEP_PX).coerceAtMost(activeEnd)
                    wave.lineTo(x, thumbY + amplitudePx * kotlin.math.sin(k * (x - phase)))
                }
                drawPath(wave, activeTrackColor, style = stroke)
            }
            if (inactiveStart < trackEnd) {
                drawLine(
                    color = inactiveTrackColor,
                    start = Offset(inactiveStart, thumbY),
                    end = Offset(trackEnd, thumbY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round,
                )
                drawCircle(activeTrackColor, with(density) { 1.5.dp.toPx() }, Offset(trackEnd, thumbY))
            }

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(
                    thumbX - currentWidth / 2f,
                    thumbY - currentHeight / 2f
                ),
                size = Size(currentWidth, currentHeight),
                cornerRadius = CornerRadius(currentWidth / 2f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, valueRange, trackEdgePaddingPx) {
                    if (!enabled) return@pointerInput

                    fun valueForX(rawX: Float): Float {
                        val edgePadding = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
                        val trackStart = edgePadding
                        val trackEnd = size.width - edgePadding
                        val trackWidth = (trackEnd - trackStart).coerceAtLeast(1f)
                        val normalized = ((rawX - trackStart) / trackWidth).coerceIn(0f, 1f)
                        return valueRange.start +
                                normalized * (valueRange.endInclusive - valueRange.start)
                    }

                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPointerSeeking = true
                            down.consume()
                            var latestGestureValue = valueForX(down.position.x)
                            latestOnValueChange(latestGestureValue)

                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()

                                val newDown = event.changes.firstOrNull { it.id != pointerId && it.pressed && !it.previousPressed }
                                if (newDown != null) {
                                    pointerId = newDown.id
                                    newDown.consume()
                                    latestGestureValue = valueForX(newDown.position.x)
                                    latestOnValueChange(latestGestureValue)
                                    continue
                                }

                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull { it.pressed }
                                    ?: break

                                pointerId = change.id
                                if (!change.pressed) {
                                    change.consume()
                                    val remaining = event.changes.firstOrNull { it.pressed }
                                    if (remaining != null) {
                                        pointerId = remaining.id
                                        latestGestureValue = valueForX(remaining.position.x)
                                        latestOnValueChange(latestGestureValue)
                                        continue
                                    } else {
                                        break
                                    }
                                }

                                if (change.position != change.previousPosition) {
                                    change.consume()
                                    latestGestureValue = valueForX(change.position.x)
                                    latestOnValueChange(latestGestureValue)
                                }
                            }

                            latestOnValueCommit?.invoke(latestGestureValue)
                            latestOnValueChangeFinished?.invoke()
                        } finally {
                            isPointerSeeking = false
                        }
                    }
                }
        )
    }
}

/** 30 fps: at this much motion the wave reads as smooth, for half the frames of the display rate. */
private const val WAVE_TICK_MS = 33L

/** Horizontal resolution of the drawn wave. */
private const val WAVE_STEP_PX = 2f

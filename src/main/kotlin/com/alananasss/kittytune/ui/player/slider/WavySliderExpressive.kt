package com.alananasss.kittytune.ui.player.slider

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WavySliderExpressive(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
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
    semanticsProgressStep: Float = 0.01f,
    /** A mix being shown: the thumb is drawn exactly at the value, per frame, and the glow starts at the thumb. */
    mix: MixTransition? = null,
    mixColor: Color = activeTrackColor,
    onValueChangeCancelled: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val trackEdgePaddingPx = with(density) { trackEdgePadding.coerceAtLeast(0.dp).toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }

    val stroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }
    val wave = remember { Path() }

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
    var isInteracting by remember { mutableStateOf(false) }

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

    val dynamicGapSize = remember(currentHalfWidth) {
        derivedStateOf {
            val fraction = thumbInteractionFraction
            val idleGap = 6.dp
            val draggingGap = currentHalfWidth.value + 1.2.dp
            idleGap + (draggingGap - idleGap) * fraction
        }
    }

    val wavelengthPx = with(density) { wavelength.toPx() }.coerceAtLeast(1f)
    val wavePhase = rememberSliderWavePhase(
        moving = isPlaying && enabled && !isInteracting,
        speedPx = with(density) { waveSpeed.toPx() },
        wavelengthPx = wavelengthPx,
    )
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
                        onValueChange(coerced)
                        onValueChangeFinished?.invoke()
                        true
                    }
                }
            }
            .seekSliderGesture(
                enabled = enabled,
                valueRange = valueRange,
                edgePaddingPx = trackEdgePaddingPx,
                onInteracting = { isInteracting = it },
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                onValueChangeCancelled = onValueChangeCancelled,
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isVisible) return@Canvas
            val edgePaddingPx = trackEdgePaddingPx.coerceIn(0f, size.width / 2f)
            val trackStart = edgePaddingPx
            val trackEnd = size.width - edgePaddingPx
            val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
            val thumbY = size.height / 2
            val renderedProgress = normalizedValueState.value

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
            mix?.let { transition ->
                val glow = transition.glowIntensity()
                if (glow > 0.01f) drawMixGlowBand(startX = thumbX, endX = trackEnd, centerY = thumbY, color = mixColor, intensity = glow)
            }
            val activeEnd = (thumbX - halfGap).coerceAtLeast(trackStart)
            val inactiveStart = (thumbX + halfGap).coerceAtMost(trackEnd)
            if (activeEnd > trackStart) {
                wave.rewind()
                var x = trackStart
                val phase = wavePhase.floatValue
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
    }
}

/** Horizontal resolution of the drawn wave. */
private const val WAVE_STEP_PX = 2f

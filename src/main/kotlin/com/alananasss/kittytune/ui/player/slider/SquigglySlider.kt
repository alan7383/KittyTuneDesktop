package com.alananasss.kittytune.ui.player.slider

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
    onValueChangeCancelled: (() -> Unit)? = null,
) {
    val primaryColor = colors.activeTrackColor
    val inactiveColor = colors.inactiveTrackColor

    var isInteracting by remember { mutableStateOf(false) }
    val span = valueRange.endInclusive - valueRange.start
    val progress = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f

    val shouldWave = isPlaying && !isInteracting
    val heightFraction by animateFloatAsState(
        targetValue = if (shouldWave) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (shouldWave) 200 else 150,
            delayMillis = if (shouldWave) 30 else 0,
            easing = LinearEasing,
        ),
        label = "squigglyAmplitude",
    )

    val waveLength = 80f
    val lineAmplitude = 6f
    val transitionLength = 1.5f * waveLength
    val phaseSpeed = 24f
    var phaseOffset by remember { mutableFloatStateOf(0f) }

    // Continuous wave animation loop, paused while nobody can see the window.
    // On the shared ambient clock rather than every frame: this bar is on screen for as long as music plays, and at
    // the display's rate it had the whole window redrawn 240 times a second on a 240 Hz monitor (see AmbientClock).
    val isSeen = com.alananasss.kittytune.core.LocalWindowSeen.current
    val ambientClock = com.alananasss.kittytune.ui.common.LocalAmbientClock.current
    LaunchedEffect(isPlaying, isSeen, ambientClock) {
        if (!isPlaying || !isSeen) return@LaunchedEffect

        var lastFrameTime = com.alananasss.kittytune.ui.common.withAmbientFrameNanos(ambientClock) { it }
        while (isActive) {
            com.alananasss.kittytune.ui.common.withAmbientFrameNanos(ambientClock) { frameTimeNanos ->
                val deltaTime = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                phaseOffset += deltaTime * phaseSpeed
                phaseOffset %= waveLength
                lastFrameTime = frameTimeNanos
            }
        }
    }

    // One path for the life of the slider, refilled each draw: a new one per frame was a native Skia object per frame
    // left for the garbage collector to find.
    val path = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .seekSliderGesture(
                enabled = enabled,
                valueRange = valueRange,
                onInteracting = { isInteracting = it },
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                onValueChangeCancelled = onValueChangeCancelled,
            ),
    ) {
        val strokeWidth = 5.dp.toPx()
        val totalWidth = size.width
        val thumbX = totalWidth * progress
        val centerY = size.height / 2f
        val amplitude = heightFraction * lineAmplitude

        fun amplitudeAt(x: Float): Float =
            amplitude * ((thumbX + transitionLength / 2f - x) / transitionLength).coerceIn(0f, 1f)

        path.rewind()
        val halfWave = waveLength / 2f
        val waveStart = -phaseOffset - halfWave

        path.moveTo(waveStart, centerY)

        var currentX = waveStart
        var waveSign = 1f
        var currentAmp = amplitudeAt(currentX)

        while (currentX < totalWidth) {
            waveSign = -waveSign
            val nextX = currentX + halfWave
            val midX = currentX + halfWave / 2f
            val nextAmp = waveSign * amplitudeAt(nextX)

            path.cubicTo(
                midX,
                centerY + currentAmp,
                midX,
                centerY + nextAmp,
                nextX,
                centerY + nextAmp,
            )

            currentAmp = nextAmp
            currentX = nextX
        }

        val clipTop = lineAmplitude + strokeWidth
        val capRadius = strokeWidth / 2f

        fun drawPathSegment(startX: Float, endX: Float, color: Color) {
            if (endX <= startX) return
            clipRect(
                left = startX,
                top = centerY - clipTop,
                right = endX,
                bottom = centerY + clipTop,
            ) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        // Draw active wavy part
        drawPathSegment(0f, thumbX, primaryColor)

        // Draw inactive part (remaining)
        drawPathSegment(thumbX, totalWidth, inactiveColor)

        fun getWaveY(x: Float): Float {
            val phase = (x - waveStart) / waveLength
            val waveCycle = phase - kotlin.math.floor(phase)
            val waveValue = kotlin.math.cos(waveCycle * 2f * kotlin.math.PI.toFloat())

            return centerY + waveValue * amplitudeAt(x)
        }

        // Draw round cap at start
        drawCircle(
            color = primaryColor,
            radius = capRadius,
            center = Offset(0f, getWaveY(0f)),
        )

        // Draw round cap at end
        val endWaveY = getWaveY(totalWidth)
        clipRect(
            left = totalWidth,
            top = centerY - clipTop,
            right = totalWidth + capRadius,
            bottom = centerY + clipTop,
        ) {
            drawCircle(
                color = inactiveColor,
                radius = capRadius,
                center = Offset(totalWidth, endWaveY),
            )
        }

        drawLine(
            color = primaryColor,
            start = Offset(thumbX, centerY - clipTop),
            end = Offset(thumbX, centerY + clipTop),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

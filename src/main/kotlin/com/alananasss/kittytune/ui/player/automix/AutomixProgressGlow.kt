package com.alananasss.kittytune.ui.player.automix

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.data.MusicManager

/**
 * Smooth, fluid gradient glow integrated directly into the progress slider.
 *
 * Provides beautiful, non-intrusive visual feedback during automix countdowns and active crossfades.
 * - Sits behind the progress slider track (away from the Play/Pause button).
 * - No aggressive flashing or blinking: uses a continuous, silky gradient wave and gentle breathing.
 * - Zero layout shift on any controls or timestamps.
 */
@Composable
fun AutomixProgressGlow(
    modifier: Modifier = Modifier
) {
    val isAutomixing by AutomixManager.isAutomixing.collectAsState()
    val mixBeatsLeft by AutomixManager.mixBeatsLeft.collectAsState()
    val isCrossfading = MusicManager.isCrossfadingOut
    val beats = mixBeatsLeft

    val isActiveTransition = isAutomixing || isCrossfading
    val isCountdown = !isActiveTransition && beats != null && beats > 0
    val isVisible = isActiveTransition || isCountdown

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(600)),
        modifier = modifier
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val tertiary = MaterialTheme.colorScheme.tertiary

        val transition = rememberInfiniteTransition(label = "ProgressGlowTransition")

        // Silky, continuous horizontal gradient sweep (no blinking!)
        val sweepFraction by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ProgressGlowSweep"
        )

        // Gentle, calm ambient breathing (smooth and slow, not a rapid flash)
        val breathingAlpha by transition.animateFloat(
            initialValue = 0.50f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ProgressBreathing"
        )

        Canvas(modifier = Modifier) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            // Gradient colors representing the musical blend
            val gradientColors = listOf(
                primary.copy(alpha = 0.50f),
                tertiary.copy(alpha = 0.85f),
                secondary.copy(alpha = 0.70f),
                primary.copy(alpha = 0.50f)
            )

            val offsetPx = width * sweepFraction
            val sweepBrush = Brush.horizontalGradient(
                colors = gradientColors,
                startX = offsetPx - width,
                endX = offsetPx + width
            )

            // Calculate intensity: increases gracefully as countdown nears 1 beat
            val urgency = if (isCountdown && beats != null) {
                ((16 - beats) / 15f).coerceIn(0f, 1f)
            } else {
                1f
            }

            val totalAlpha = breathingAlpha * (0.55f + 0.45f * urgency)

            // 1. Soft outer ambient glow aura behind the slider
            val outerGlowHeight = 18.dp.toPx().coerceAtMost(height)
            val outerTop = (height - outerGlowHeight) / 2f
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        tertiary.copy(alpha = 0.20f * totalAlpha),
                        primary.copy(alpha = 0.25f * totalAlpha),
                        tertiary.copy(alpha = 0.20f * totalAlpha),
                        Color.Transparent
                    ),
                    startY = outerTop,
                    endY = outerTop + outerGlowHeight
                ),
                topLeft = Offset(0f, outerTop),
                size = Size(width, outerGlowHeight),
                cornerRadius = CornerRadius(outerGlowHeight / 2f, outerGlowHeight / 2f)
            )

            // 2. Focused flowing gradient highlight along the slider track
            val trackGlowHeight = 8.dp.toPx().coerceAtMost(height)
            val trackTop = (height - trackGlowHeight) / 2f
            drawRoundRect(
                brush = sweepBrush,
                topLeft = Offset(0f, trackTop),
                size = Size(width, trackGlowHeight),
                cornerRadius = CornerRadius(trackGlowHeight / 2f, trackGlowHeight / 2f),
                alpha = 0.70f * totalAlpha
            )
        }
    }
}

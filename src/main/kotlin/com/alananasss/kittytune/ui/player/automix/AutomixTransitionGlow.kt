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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.core.stringResource
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.ui.common.Tip

/**
 * Elegant visual transition glow for automix and crossfade built directly into the bottom control panel (PlayerBar).
 *
 * Replaces the disruptive digital countdown badge that previously sat inside the transport controls row,
 * which caused playback buttons to physically shrink and shift.
 *
 * Visual behavior:
 * - Countdown phase: subtle ambient glow pulsing in rhythm with outgoing track BPM, warming in color as mix approaches.
 * - Active crossfade/automix phase: smooth continuous animated gradient sweep across the top edge with soft ambient backlight.
 * - Zero layout shift: completely decoupled from button layout.
 */
@Composable
fun AutomixTransitionGlow(
    modifier: Modifier = Modifier
) {
    val isAutomixing by AutomixManager.isAutomixing.collectAsState()
    val automixDebug by AutomixManager.automixDebugInfo.collectAsState()
    val mixBeatsLeft by AutomixManager.mixBeatsLeft.collectAsState()
    val isCrossfading = MusicManager.isCrossfadingOut
    val beats = mixBeatsLeft

    val isActiveTransition = isAutomixing || isCrossfading
    val isCountdown = !isActiveTransition && beats != null && beats > 0
    val isVisible = isActiveTransition || isCountdown

    // Dynamic tempo-synced beat period (ms) based on outgoing track BPM
    val mixBeatMs = automixDebug?.outBpm?.takeIf { it > 0f }?.let { 60_000f / it } ?: 500f

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(450)),
        exit = fadeOut(animationSpec = tween(550)),
        modifier = modifier
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val tertiary = MaterialTheme.colorScheme.tertiary

        // Pulse animation for countdown synced to track BPM
        val beatTransition = rememberInfiniteTransition(label = "MixBeatTransition")
        val beatPulse by beatTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = mixBeatMs.toInt().coerceIn(200, 1000),
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "MixBeatPulse"
        )

        // Smooth horizontal sweep animation for active crossfade / automix
        val sweepTransition = rememberInfiniteTransition(label = "CrossfadeSweepTransition")
        val sweepFraction by sweepTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "CrossfadeSweepFraction"
        )

        val ambientPulse by sweepTransition.animateFloat(
            initialValue = 0.75f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "CrossfadeAmbientPulse"
        )

        val tooltipText = when {
            isAutomixing -> stringResource(R.string.automixing)
            isCrossfading -> stringResource(R.string.crossfading)
            beats != null && beats > 0 -> stringResource(R.string.automix_mix_in, beats)
            else -> ""
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                if (isActiveTransition) {
                    // Active crossfade / automix: dynamic flowing gradient sweep
                    val gradientColors = listOf(
                        primary.copy(alpha = 0.85f),
                        tertiary.copy(alpha = 0.95f),
                        secondary.copy(alpha = 0.85f),
                        primary.copy(alpha = 0.85f)
                    )
                    val offsetPx = width * sweepFraction
                    val sweepBrush = Brush.horizontalGradient(
                        colors = gradientColors,
                        startX = offsetPx - width,
                        endX = offsetPx + width
                    )

                    // 1. Ambient soft vertical glow falling from the top edge
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                tertiary.copy(alpha = 0.22f * ambientPulse),
                                primary.copy(alpha = 0.08f * ambientPulse),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = (34.dp.toPx()).coerceAtMost(height)
                        ),
                        size = Size(width, (34.dp.toPx()).coerceAtMost(height))
                    )

                    // 2. Glowing top accent line (2.5dp)
                    drawRect(
                        brush = sweepBrush,
                        topLeft = Offset(0f, 0f),
                        size = Size(width, 2.5.dp.toPx())
                    )
                } else if (isCountdown) {
                    // Countdown phase: rhythmic BPM-synced breathing glow
                    val countdownAlpha = beatPulse
                    // Intensity gently increases as countdown reaches final beats
                    val urgency = ((16 - beats) / 15f).coerceIn(0f, 1f)
                    val baseColor = if (urgency > 0.5f) tertiary else primary

                    // 1. Ambient soft vertical glow
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                baseColor.copy(alpha = (0.12f + 0.10f * urgency) * countdownAlpha),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = (26.dp.toPx()).coerceAtMost(height)
                        ),
                        size = Size(width, (26.dp.toPx()).coerceAtMost(height))
                    )

                    // 2. Top accent line with center-weighted intensity
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                baseColor.copy(alpha = countdownAlpha * 0.7f),
                                tertiary.copy(alpha = countdownAlpha * 0.95f),
                                baseColor.copy(alpha = countdownAlpha * 0.7f),
                                Color.Transparent
                            )
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(width, 2.5.dp.toPx())
                    )
                }
            }

            // Top hit area for non-intrusive tooltip on hover (confined to top 8dp so it never touches buttons)
            if (tooltipText.isNotEmpty()) {
                Tip(text = tooltipText) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

package com.alananasss.kittytune.ui.player.slider

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.automix.AutomixManager
import kotlin.math.max
import kotlin.math.pow

/** The countdown starts this many beats before a mix; see the automix loop in PlayerViewModel. */
private const val COUNTDOWN_BEATS = 16

/**
 * A mix, told by the seek bar instead of a label under the track name (issue #66).
 *
 * Counting down to it, a glow gathers at the far end of the bar, a little brighter on every beat, as if the
 * end were pulling the playhead in. When the mix starts the next track takes over the player, so its playhead
 * is back near zero; rather than jump there, the bar starts from the end, flares, and glides home as the mix
 * runs, landing on the real position exactly when the old track has faded out.
 *
 * Driven by the same numbers the audio uses ([AutomixManager.mixProgress] is published by the crossfade loop
 * itself), so what the eye sees and what the ear hears are one event. A plain crossfade reports progress too
 * and gets the same treatment.
 */
@Stable
class MixTransition internal constructor(
    private val progress: State<Float>,
    private val countdown: State<Float>,
    private val beatPulse: State<Float>,
) {
    /** Whether a mix is under way, as opposed to merely counting down to one. */
    val isMixing: Boolean get() = progress.value > 0f

    /**
     * Where the bar should show the playhead: the real [actualFraction], except during a mix, when it travels
     * from the end of the bar back to the real position.
     */
    fun shownFraction(actualFraction: Float): Float {
        val p = progress.value
        if (p <= 0f) return actualFraction
        val eased = FastOutSlowInEasing.transform(p)
        return 1f + (actualFraction - 1f) * eased
    }

    /** How strongly the glow shows, 0..1. */
    internal fun glowIntensity(): Float {
        val p = progress.value
        if (p > 0f) {
            // Flares in over the first tenth, then dims as the bar comes home.
            val rise = (p / 0.1f).coerceAtMost(1f)
            return rise * (1f - p).pow(0.6f)
        }
        return countdown.value * (0.65f + 0.35f * beatPulse.value)
    }
}

@Composable
fun rememberMixTransition(): MixTransition {
    val progress = AutomixManager.mixProgress.collectAsState()
    val beatsLeft by AutomixManager.mixBeatsLeft.collectAsState()
    val debug by AutomixManager.automixDebugInfo.collectAsState()

    // 0 sixteen beats out, 1 on the last one; eased so the change between beats is not a step.
    val countdown = animateFloatAsState(
        targetValue = beatsLeft?.let { 1f - (it - 1).toFloat() / COUNTDOWN_BEATS }?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "mixCountdown",
    )
    val beatMs = debug?.outBpm?.takeIf { it > 0f }?.let { 60_000f / it }?.toInt()?.coerceIn(250, 1000) ?: 500
    val beatPulse = rememberInfiniteTransition(label = "mixBeat").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(beatMs, easing = LinearEasing), RepeatMode.Restart),
        label = "mixBeatPulse",
    )
    return remember(progress, countdown, beatPulse) { MixTransition(progress, countdown, beatPulse) }
}

/**
 * Draws the mix glow behind a seek bar: at the end of the bar while counting down, and at the playhead, with
 * a fading trail back to the end, while the bar glides home.
 *
 * @param shownFraction where the playhead is drawn, 0..1, as returned by [MixTransition.shownFraction].
 */
fun Modifier.mixGlow(mix: MixTransition, color: Color, shownFraction: () -> Float): Modifier = drawBehind {
    val intensity = mix.glowIntensity()
    if (intensity <= 0.01f) return@drawBehind
    val y = size.height / 2f
    val headX = if (mix.isMixing) shownFraction().coerceIn(0f, 1f) * size.width else size.width
    val radius = max(size.height, 22.dp.toPx()) * (0.9f + 0.5f * intensity)

    if (mix.isMixing && headX < size.width) {
        drawLine(
            brush = Brush.horizontalGradient(
                0f to color.copy(alpha = 0.45f * intensity),
                1f to Color.Transparent,
                startX = headX,
                endX = size.width,
            ),
            start = Offset(headX, y),
            end = Offset(size.width, y),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = 0.55f * intensity),
            0.45f to color.copy(alpha = 0.22f * intensity),
            1f to Color.Transparent,
            center = Offset(headX, y),
            radius = radius,
        ),
        radius = radius,
        center = Offset(headX, y),
    )
}

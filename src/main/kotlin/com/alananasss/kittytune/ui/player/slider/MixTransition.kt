package com.alananasss.kittytune.ui.player.slider

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.alananasss.kittytune.audio.automix.AutomixManager
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The countdown starts this many beats before a mix; see the automix loop in PlayerViewModel. */
private const val COUNTDOWN_BEATS = 16

/** How long the bar holds the old track's spot while the next one buffers, before giving up on a mix. */
private const val WAIT_FOR_MIX_NANOS = 8_000_000_000L

/** How long the bar takes to reach the real position when a mix ends before its fade does. */
private const val CATCH_UP_NANOS = 450_000_000L

/** A bar left at or past this fraction, then landing before [LANDED_NEAR_START], slides back instead of jumping. */
private const val JUMPED_FROM_END = 0.85f
private const val LANDED_NEAR_START = 0.15f

/** How long the slide back from the end takes. */
private const val JUMP_BACK_NANOS = 750_000_000L

/** How often an idle bar checks whether a glide has begun; no frames are asked for in between. */
private const val IDLE_CHECK_MS = 64L

/**
 * A mix, told by the seek bar instead of a label under the track name (issue #66).
 *
 * Counting down to it, a glow spreads from the playhead towards the end of the bar, reaching it on the last
 * beat. When the mix starts the next track takes over the player and its playhead is near zero; instead of
 * jumping there, the bar stays where the old track was and glides back as the fade runs, landing on the real
 * position when the old track has faded out.
 *
 * The bar used to jump twice on the way: to the new track's position while it buffered (the fade had not
 * started, so nothing said a mix was on), then to the very end when the fade began. Now the glide starts from
 * wherever the bar was at the track change, and a fade cut short is caught up with instead of snapped to.
 *
 * Driven by the same numbers the audio uses ([AutomixManager.mixProgress] is published by the crossfade loop
 * itself), so what the eye sees and what the ear hears are one event. A plain crossfade gets the same glide.
 */
@Stable
class MixTransition internal constructor(
    private val progress: State<Float>,
    private val countdown: State<Float>,
    private val beatPulse: State<Float>,
    private val automixing: State<Boolean>,
) {
    // Bookkeeping, not state: it is read and written while the bar computes what to draw, and every value
    // here follows from the inputs of that same call, so nothing needs to recompose when it changes.
    private var lastTrackKey: Any? = NO_TRACK
    private var lastShown = 0f
    private var glideFrom: Float? = null
    private var switchedAtNanos = 0L
    private var sawFade = false
    private var catchUpFrom: Float? = null
    private var catchUpAtNanos = 0L
    private var catchUpNanos = CATCH_UP_NANOS
    private var catchUpEasing: Easing = FastOutSlowInEasing

    /** Bumped every frame while the bar glides, so the bar is redrawn between the player's position reports. */
    internal val frameTick = mutableLongStateOf(0L)

    /** Whether the bar is drawn somewhere other than the real position and needs frames to get there. */
    internal val isGliding: Boolean get() = glideFrom != null || catchUpFrom != null

    /**
     * Tells the bar where it is drawn while the reader drags it, when [shownFraction] is not asked: otherwise the
     * next track change glides from wherever the bar was before the drag began, which showed as a short step
     * left, a run back to the end, and then the slide (issue #66).
     */
    fun noteShown(fraction: Float) {
        lastShown = fraction
        catchUpFrom = null
    }

    /** Whether the bar is gliding home after a track change, as opposed to counting down to one. */
    val isMixing: Boolean get() = glideFrom != null || progress.value > 0f

    /** How far the countdown has come, 0 sixteen beats out and 1 on the last beat. */
    internal val countdownFraction: Float get() = countdown.value

    /**
     * Where the bar should show the playhead of the track identified by [trackKey]: the real [actualFraction],
     * except around a mix, when it glides from where the old track was to the new track's position.
     */
    fun shownFraction(actualFraction: Float, trackKey: Any?): Float {
        frameTick.longValue
        val now = System.nanoTime()
        val fade = progress.value
        if (trackKey != lastTrackKey) {
            val mixAround = automixing.value || countdown.value > 0f || fade > 0f
            if (lastTrackKey !== NO_TRACK && mixAround) {
                glideFrom = lastShown
                switchedAtNanos = now
                sawFade = false
                catchUpFrom = null
            } else if (lastTrackKey !== NO_TRACK && lastShown >= JUMPED_FROM_END && actualFraction <= LANDED_NEAR_START) {
                // Dragged or clicked to the very end, which moves on to the next track: the bar slides back from
                // where it was left instead of jumping to the start.
                glideFrom = null
                startCatchUp(now, JUMP_BACK_NANOS, FastOutSlowInEasing)
            } else {
                glideFrom = null
            }
            lastTrackKey = trackKey
        }

        val from = glideFrom
        val shown = when {
            from == null -> catchUp(actualFraction, now)
            fade > 0f -> {
                sawFade = true
                lerp(from, actualFraction, FastOutSlowInEasing.transform(fade))
            }
            // Still buffering the next track: keep the old spot rather than show its position early.
            !sawFade && now - switchedAtNanos < WAIT_FOR_MIX_NANOS -> from
            else -> {
                // The fade is over, or never came. Finish the trip smoothly from wherever the bar is.
                glideFrom = null
                startCatchUp(now, CATCH_UP_NANOS, FastOutSlowInEasing)
                catchUp(actualFraction, now)
            }
        }
        lastShown = shown
        return shown
    }

    private fun startCatchUp(now: Long, nanos: Long, easing: Easing) {
        catchUpFrom = lastShown
        catchUpAtNanos = now
        catchUpNanos = nanos
        catchUpEasing = easing
    }

    private fun catchUp(actualFraction: Float, now: Long): Float {
        val from = catchUpFrom ?: return actualFraction
        val t = (now - catchUpAtNanos).toFloat() / catchUpNanos
        if (t >= 1f) {
            catchUpFrom = null
            return actualFraction
        }
        return lerp(from, actualFraction, catchUpEasing.transform(t))
    }

    /** How strongly the glow shows, 0..1. */
    internal fun glowIntensity(): Float {
        val fade = progress.value
        if (fade > 0f) {
            // Flares in over the first tenth, then dims as the bar comes home.
            val rise = (fade / 0.1f).coerceAtMost(1f)
            return rise * (1f - fade).pow(0.6f)
        }
        if (glideFrom != null) return 0.8f
        return countdown.value * (0.7f + 0.3f * beatPulse.value)
    }

    private companion object {
        val NO_TRACK = Any()
    }
}

@Composable
fun rememberMixTransition(): MixTransition {
    val progress = AutomixManager.mixProgress.collectAsState()
    val automixing = AutomixManager.isAutomixing.collectAsState()
    val beatsLeft by AutomixManager.mixBeatsLeft.collectAsState()
    val debug by AutomixManager.automixDebugInfo.collectAsState()

    // 0 sixteen beats out, 1 on the last one; eased so the change between beats is not a step.
    val countdown = animateFloatAsState(
        targetValue = beatsLeft?.let { 1f - (it - 1).toFloat() / COUNTDOWN_BEATS }?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "mixCountdown",
    )
    val beatMs = debug?.outBpm?.takeIf { it > 0f }?.let { 60_000f / it }?.toInt()?.coerceIn(250, 1000) ?: 500
    // Runs only through the countdown. An infinite transition asked for a frame sixty times a second for as long as the
    // player was open, mixing or not, which is the steady cost the heavy full-screen player did not need.
    val beatPulse = remember { mutableFloatStateOf(1f) }
    val counting = beatsLeft != null
    LaunchedEffect(counting, beatMs) {
        if (!counting) {
            beatPulse.floatValue = 1f
            return@LaunchedEffect
        }
        val startedAt = System.nanoTime()
        while (isActive) {
            withFrameNanos { now ->
                val phase = ((now - startedAt) / 1_000_000L % beatMs).toFloat() / beatMs
                beatPulse.floatValue = 1f - phase
            }
        }
    }
    val mix = remember(progress, countdown, beatPulse, automixing) {
        MixTransition(progress, countdown, beatPulse, automixing)
    }
    // The player reports its position four times a second and the fade publishes its progress as it goes,
    // but neither keeps time while the bar catches up after a mix: those steps
    // were drawn as jumps. Frames are asked for only while such a glide runs.
    LaunchedEffect(mix) {
        while (isActive) {
            if (mix.isGliding) withFrameNanos { mix.frameTick.longValue = it }
            else delay(IDLE_CHECK_MS)
        }
    }
    return mix
}

/**
 * Draws the mix glow behind a seek bar: a soft band that starts at the playhead and spreads towards the end
 * of the bar.
 *
 * While counting down it reaches further on every beat and gets to the end on the last one; while the bar
 * glides home it stretches from the moving playhead to the end and fades with the old track.
 *
 * @param shownFraction where the playhead is drawn, 0..1, as returned by [MixTransition.shownFraction].
 */
fun Modifier.mixGlow(mix: MixTransition, color: Color, shownFraction: () -> Float): Modifier = drawBehind {
    val intensity = mix.glowIntensity()
    if (intensity <= 0.01f) return@drawBehind
    val y = size.height / 2f
    val headX = shownFraction().coerceIn(0f, 1f) * size.width
    val reach = if (mix.isMixing) 1f else FastOutSlowInEasing.transform(mix.countdownFraction)
    // Starts clear of the thumb: from the playhead itself, the widest stroke's round end showed around and
    // behind the dot (issue #66).
    val startX = headX + GLOW_THUMB_CLEARANCE.dp.toPx()
    val endX = startX + (size.width - startX) * reach
    if (endX - startX < 1f) return@drawBehind

    // Three strokes of falling strength and growing width read as one soft band, without a blur pass.
    clipRect(left = startX) {
        for ((width, alpha) in GLOW_LAYERS) {
            drawLine(
                brush = Brush.horizontalGradient(
                    0f to color.copy(alpha = alpha * intensity),
                    0.6f to color.copy(alpha = alpha * intensity * 0.45f),
                    1f to Color.Transparent,
                    startX = startX,
                    endX = endX,
                ),
                start = Offset(startX, y),
                end = Offset(endX, y),
                strokeWidth = width.dp.toPx(),
                cap = StrokeCap.Butt,
            )
        }
    }
}

/** Room left for the thumb between the playhead and where the glow begins. */
private const val GLOW_THUMB_CLEARANCE = 10f

/** Width in dp and peak alpha of each stroke of the glow, widest and faintest first. */
private val GLOW_LAYERS = listOf(18f to 0.10f, 10f to 0.18f, 4f to 0.42f)

package com.alananasss.kittytune.ui.player.cover

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.core.LocalWindowSeen
import com.alananasss.kittytune.media.CROSSFADE_SPAN_END
import com.alananasss.kittytune.media.CROSSFADE_SPAN_START
import com.alananasss.kittytune.media.equalPowerIn
import com.alananasss.kittytune.media.equalPowerOut
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * How far the halo reaches past the sleeve. The caller adds this to the cover's size.
 */
val MIX_HALO_MARGIN: Dp = 72.dp

/**
 * Light that shows how a mix is going.
 *
 * The complaint this answers is that a mix is invisible: the bar swaps to "Mix into …", a badge
 * blinks a count, and then for the length of the fade the two tracks are simply both playing. There
 * is nothing to watch. So the cover is lit by the fade itself — a band of the record's own colours
 * travelling up the sleeve as the outgoing track hands over, and a halo behind it that swells where
 * the two tracks are both audible.
 *
 * Three decisions worth stating:
 *
 * - **The direction is bottom to top**, because that is the direction the request describes: the
 *   track is ending, and the light returns to where it began. A playhead running the other way would
 *   read as the track continuing.
 * - **The swell is driven by the overlap, not by the raw progress.** The engine's two ramps are
 *   equal-power and overlap only across the middle of the fade, so progress on its own would put the
 *   light at full before the fade and full after it with nothing in between — the opposite of what a
 *   handover looks like. What moves is [mixHandover]: how much of both tracks is audible at once.
 * - **The glow follows the overlap, not the level.** The two gain ramps share one span
 *   ([CROSSFADE_SPAN_START] to [CROSSFADE_SPAN_END]), so the handover is the middle of the fade and
 *   nothing else moves. The glow is a readout of that, which is also why it never dips: a light that
 *   sagged at the moment the listener is most attentive would be a bad one.
 *
 * Everything here is drawn, not composed: the 30 fps clock writes a float that is read inside the
 * draw lambda, so a running glow repaints without recomposing the player.
 */
class MixGlow internal constructor(
    /** 0 before the fade, then 0 to 1 across it. Read while drawing, so a fade repaints only the draw. */
    val progress: State<Float>,
    /** Seconds since this was created, at 30 fps. Read while drawing. */
    val seconds: State<Float>,
    /** Outgoing tempo, 0 when unknown, so the pulse can sit on the beat. Captured, not per-frame. */
    val outgoingBpm: Float,
    /** 0 while the mix is far off, 1 as the fade begins. */
    val approach: Float,
    /** Whether anything is happening at all, so an idle player draws none of this. */
    val isLit: Boolean,
)

/**
 * Collects what the glow needs and runs its clock.
 *
 * @param outgoingBpm passed in rather than read from the debug flow, so it is captured once per
 *   track: it changes on a track change, not thirty times a second, and reading it inside the effect
 *   would restart the clock on every emission.
 */
@Composable
fun rememberMixGlow(outgoingBpm: Float): MixGlow {
    val progressState = AutomixManager.mixProgress.collectAsState()
    val beatsLeft by AutomixManager.mixBeatsLeft.collectAsState()
    val progress by progressState

    val isSeen = LocalWindowSeen.current
    val seconds = remember { mutableFloatStateOf(0f) }

    // The clock discipline the fluid background and the wave slider settled on: `delay` asks for
    // nothing, whereas awaiting a frame asks for every one of them, and a full player hidden in the
    // tray would then render at the display rate for as long as a track mixed. The state is
    // remembered outside the effect so the glow resumes where it was instead of restarting.
    LaunchedEffect(isSeen) {
        if (!isSeen) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(TICK_MS)
            val now = System.nanoTime()
            seconds.floatValue += ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
        }
    }

    // The run-up. A glow that only appeared once the tracks were already overlapping gave no warning
    // at all, which was the other half of the complaint. This is the same sixteen-beat window the
    // badge counts down in, so the light and the count arrive together.
    val beats = beatsLeft ?: 0
    val approach = when {
        progress > 0f -> 1f
        beats > 0 -> 1f - (beats - 1).toFloat() / ANTICIPATION_BEATS
        else -> 0f
    }.coerceIn(0f, 1f)

    return MixGlow(
        progress = progressState,
        seconds = seconds,
        outgoingBpm = outgoingBpm,
        approach = approach,
        isLit = progress > 0f || approach > 0f,
    )
}

/**
 * How much of *both* tracks is audible, 0 at either end of the fade and 1 at the handover.
 *
 * This is the number worth lighting a cover by. It is the product of the engine's own two ramps, so
 * it is zero wherever only one track is playing and rises through the overlap — which for this
 * curve is a narrow band in the middle, not the whole fade. Normalising by the peak is measured
 * rather than written down, so changing an edge in the engine cannot leave the glow stuck below one.
 */
internal fun mixHandover(progress: Float): Float {
    if (progress <= 0f || progress >= 1f) return 0f
    val both = equalPowerOut(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, progress) *
        equalPowerIn(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, progress)
    return (both / HANDOVER_PEAK).coerceIn(0f, 1f)
}

/**
 * How bright the glow is, 0 to 1.
 *
 * [approach] is the run-up: it climbs to exactly [FADE_FLOOR], which is where the fade takes over, so
 * there is no step at the moment the two tracks begin to overlap. Through the fade the light sits at
 * that floor and swells with the handover, which is the only part of a mix that a listener can see.
 */
internal fun mixIntensity(progress: Float, approach: Float): Float = when {
    progress <= 0f -> approach * FADE_FLOOR
    else -> FADE_FLOOR + (1f - FADE_FLOOR) * mixHandover(progress)
}

/**
 * The light behind the sleeve.
 *
 * Drawn with a `Screen` blend because light on a dark wall adds rather than covers, which keeps the
 * artwork's own colours reading as emission instead of as a coloured card propped behind the cover.
 * Goes under the sleeve, so only the spill around it is ever seen.
 */
@Composable
fun MixHalo(
    glow: MixGlow,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (!glow.isLit || colors.isEmpty()) return

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val t = glow.seconds.value
            val intensity = mixIntensity(glow.progress.value, glow.approach)
            if (intensity <= 0.001f) return@Canvas

            val breathe = 1f + BREATHE_DEPTH * sin(t * TWO_PI * BREATHE_HZ)
            val centre = Offset(size.width / 2f, size.height / 2f)

            colors.forEachIndexed { index, colour ->
                // Outermost ring widest and faintest, so the halo has no edge to see.
                val spread = (HALO_RING_BASE + index * HALO_RING_STEP) * breathe
                val radius = size.minDimension * spread
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to colour.copy(alpha = intensity * HALO_CORE_ALPHA),
                        0.55f to colour.copy(alpha = intensity * HALO_MID_ALPHA),
                        1f to Color.Transparent,
                        center = centre,
                        radius = radius,
                    ),
                    radius = radius,
                    center = centre,
                    blendMode = BlendMode.Screen,
                )
            }
        }
    }
}

/**
 * The band travelling up the sleeve.
 *
 * Drawn as a run of thin columns rather than one gradient, so each column can take a different
 * colour from the sleeve's palette and a slightly different height. That is what makes it read as
 * light on a moving surface rather than a rectangle sliding past; a single gradient can only be one
 * band of one colour, and this is meant to shimmer with the record's own shades.
 *
 * The caller clips this to the sleeve's rounded corners, so it inherits the cover's shape for free.
 */
@Composable
fun MixSweep(
    glow: MixGlow,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    if (!glow.isLit || colors.isEmpty()) return

    Canvas(modifier) {
        val t = glow.seconds.value
        val p = glow.progress.value
        val intensity = mixIntensity(p, glow.approach)
        if (intensity <= 0.001f) return@Canvas

        // Bottom to top: where the light starts is the end of the track, and where it lands is the
        // beginning. Before the fade there is no progress to travel, so it waits at the bottom.
        val travel = easeInOutSine(p)
        val centreY = size.height * (1f - travel)

        val alpha = intensity * SWEEP_ALPHA * beatPulse(glow, t)
        // Fades the band in and out at the ends of its travel rather than popping it at the edges.
        val travelFade = sin(travel * PI).toFloat().coerceAtLeast(0f)

        for (i in 0 until SWEEP_COLUMNS) {
            val f = i / (SWEEP_COLUMNS - 1f)
            val x0 = size.width * f
            val x1 = size.width * (i + 1f) / SWEEP_COLUMNS
            // A slow wave along the band, so the columns do not line up like a bar chart.
            val ripple = sin(f * TWO_PI * 1.5f + t * RIPPLE_HZ) * size.height * RIPPLE_SPAN
            val y = centreY + ripple
            val half = size.height * BAND_HALF
            val colour = colors[i % colors.size]

            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to colour.copy(alpha = alpha * travelFade),
                    1f to Color.Transparent,
                    startY = y - half,
                    endY = y + half,
                ),
                topLeft = Offset(x0, 0f),
                size = Size(x1 - x0 + 1f, size.height),
                blendMode = BlendMode.Screen,
            )
        }

        // A whole-sleeve lift through the fade, so the moment both tracks are at full is unmistakable
        // even from across the room. Peaks with the handover, like everything else here.
        val lift = intensity * LIFT_ALPHA * mixHandover(p)
        if (lift > 0.001f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to colors.first().copy(alpha = lift),
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.Screen,
            )
        }
    }
}

/**
 * The beat the pulse sits on, when the tempo is known.
 *
 * A glow pulsing at a rate unrelated to the track is decoration; one pulsing on the beat is a tempo
 * readout you can see. Flat for a plain crossfade, where there is no analysed tempo to sit on — and
 * a wrong-tempo pulse would be worse than none.
 */
private fun beatPulse(glow: MixGlow, seconds: Float): Float {
    if (glow.outgoingBpm <= 0f) return 1f
    val phase = (seconds / (60f / glow.outgoingBpm)) * TWO_PI
    return 1f - BEAT_DEPTH * (0.5f - 0.5f * sin(phase))
}

private fun easeInOutSine(x: Float): Float = 0.5f - 0.5f * cos(PI.toFloat() * x.coerceIn(0f, 1f))

/**
 * The highest the handover ever gets, measured off the engine's own curve rather than written down.
 *
 * Scanning keeps the normalisation honest: widen or narrow [CROSSFADE_SPAN_START] and this follows,
 * instead of leaving the glow capped at whatever fraction it used to reach.
 */
private val HANDOVER_PEAK: Float by lazy {
    var peak = 0f
    var p = 0f
    while (p <= 1f) {
        val v = equalPowerOut(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, p) *
            equalPowerIn(CROSSFADE_SPAN_START, CROSSFADE_SPAN_END, p)
        if (v > peak) peak = v
        p += 0.001f
    }
    peak
}

private const val TICK_MS = 33L

/** How many beats before the fade the glow starts to build. Matches the badge's countdown window. */
private const val ANTICIPATION_BEATS = 16f

/**
 * The brightness a fade runs at, and the height the run-up climbs to.
 *
 * One constant, so the hand-over from warning to fade cannot step. It is a floor and not a dip
 * because the glow is showing the handover, not the level: the two are not the same thing here, and
 * the audio's own dip is a separate matter.
 */
private const val FADE_FLOOR = 0.55f

private const val SWEEP_COLUMNS = 28
private const val SWEEP_ALPHA = 0.55f
private const val BAND_HALF = 0.22f
private const val RIPPLE_HZ = 1.7f
private const val RIPPLE_SPAN = 0.014f

private const val HALO_RING_BASE = 0.46f
private const val HALO_RING_STEP = 0.13f
private const val HALO_CORE_ALPHA = 0.30f
private const val HALO_MID_ALPHA = 0.12f

private const val BREATHE_HZ = 0.45f
private const val BREATHE_DEPTH = 0.06f
private const val BEAT_DEPTH = 0.35f
private const val LIFT_ALPHA = 0.10f

private const val TWO_PI = (PI * 2.0).toFloat()

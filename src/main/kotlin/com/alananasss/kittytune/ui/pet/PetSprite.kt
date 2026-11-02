package com.alananasss.kittytune.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Draws a pixel image scaled to fit, centred, with nearest-neighbour filtering (crisp pixels). */
internal fun DrawScope.drawPixels(image: ImageBitmap) {
    val scale = minOf(size.width / image.width, size.height / image.height)
    val w = (image.width * scale).roundToInt()
    val h = (image.height * scale).roundToInt()
    drawImage(
        image,
        dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
        dstSize = IntSize(w, h),
        filterQuality = FilterQuality.None,
    )
}

/** Room shown for an activity in previews (the pet window gets it from [PetBrain.resolve]). */
internal fun previewRoom(shown: PetActivity): String = when (shown) {
    PetActivity.SLEEP -> "sleep"
    PetActivity.SAD -> "rain"
    PetActivity.DOZE, PetActivity.STARGAZE, PetActivity.NOD, PetActivity.SHOOTING_STAR -> "night"
    PetActivity.IVY_SPACE -> "space"
    else -> PetBrain.currentSlot().room
}

/**
 * The pixel cat, drawn in Compose: wall, room, seasonal decor and the eight stacked frame layers,
 * tinted with the given look. [animate] = false shows the first frame only (cheap enough for long
 * lists of option previews).
 *
 * Dances step exactly on each beat the listener hears while music plays (the beat index picks the
 * pose, so the head-left pose lands on the downbeat); [halfTime] steps every other beat (the calm
 * sway of a break). Without a beat grid, dances fall back to [fallbackBpm].
 */
@Composable
fun PetSprite(
    activity: PetActivity,
    settings: PetSettings,
    modifier: Modifier = Modifier,
    room: String? = null,
    boopable: Boolean = false,
    label: String? = null,
    corner: Int = 28,
    animate: Boolean = true,
    onBoop: (() -> Unit)? = null,
    halfTime: Boolean = false,
    fallbackBpm: Float = 0f,
    /** Changes when the frames of a clock-driven activity (New Year) must be rebuilt. */
    framesKey: Any? = null,
    /** Replaces the local boop (the pet window boops the shared state instead). */
    onClick: (() -> Unit)? = null,
) {
    var boopUntil by remember { mutableLongStateOf(0L) }
    var booping by remember { mutableStateOf(false) }
    // booped while dancing: a quick squee with the headphones kept on, like in the pet window
    val shown = if (booping) (if (activity.isDance) PetActivity.BOOP_DANCE else PetActivity.BOOP) else activity
    val roomName = room ?: previewRoom(shown)
    val wall = PetLayers.wallColor(roomName, settings)
    val wallColor = wall?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val dark = wall?.let { PetLayers.isDark(it) } ?: false
    val decor = remember(settings.seasonal, settings.holidays, settings.roomDecor) {
        if (settings.roomDecor) PetBrain.decorFor(settings) else null
    }

    val frameList = remember(shown, framesKey) { PetBrain.framesFor(shown) }
    var frame by remember(shown, framesKey) { mutableIntStateOf(0) }
    val speed by rememberUpdatedState(settings.speed)
    val half by rememberUpdatedState(halfTime)
    val bpm by rememberUpdatedState(fallbackBpm)
    if (animate) {
        LaunchedEffect(shown, framesKey) {
            var beats = 0L
            while (true) {
                if (shown.isDance) {
                    // dancing while music plays: step exactly on each beat the listener hears
                    val next = PetState.nextHeardBeat(minAheadMs = 30)
                    if (next != null) {
                        delay(next.first.coerceAtLeast(0))
                        beats++
                        if (half && beats % 2L != 0L) continue
                        frame = (((next.second % frameList.size) + frameList.size) % frameList.size).toInt()
                    } else {
                        val beatMs = if (bpm > 0f) PetBrain.frameMsForBpm(bpm) else shown.frameMs
                        val ms = beatMs * if (half) 2 else 1
                        delay(ms.toLong())
                        frame = (frame + 1) % frameList.size
                    }
                } else {
                    val ms = if (shown == PetActivity.NY_COUNTDOWN) shown.frameMs.toFloat() else shown.frameMs * speed.factor
                    delay(ms.toLong())
                    frame = (frame + 1) % frameList.size
                }
                if (booping && System.currentTimeMillis() > boopUntil) booping = false
            }
        }
    }
    val frameName = frameList[frame % frameList.size]
    val image = remember(frameName, settings, roomName, decor) {
        PetComposer.scene(
            frameName, settings,
            room = if (settings.roomDecor) roomName else null,
            decor = if (settings.roomDecor && roomName != "space") decor else null,
        )
    }

    Box(
        modifier = modifier
            .aspectRatio(46f / 32f)
            .clip(RoundedCornerShape(corner.dp))
            .background(wallColor)
            .then(
                if (boopable || onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (onClick != null) {
                        onClick()
                    } else {
                        boopUntil = System.currentTimeMillis() + 2_000
                        booping = true
                        onBoop?.invoke()
                    }
                } else Modifier
            )
    ) {
        Canvas(Modifier.fillMaxSize()) { drawPixels(image) }
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (dark) Color(0xFFF1ECFF) else Color(0xFF4A3448),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(if (dark) Color(0x26FFFFFF) else Color(0xB3FFFFFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

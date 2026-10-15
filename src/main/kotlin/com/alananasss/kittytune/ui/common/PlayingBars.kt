package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.LocalWindowSeen

/**
 * Three bars bouncing out of step, the usual "this one is playing" mark; held still at different heights when it
 * is paused. [color] is the bars' colour, since they sit on covers, buttons and plain surfaces alike.
 *
 * The animation only runs while the window is seen AND music is playing — a paused player or a hidden
 * window renders the static PAUSED_BAR_HEIGHTS instead of driving a 60 fps infinite transition
 * (audit Phase 3 CPU gain).
 */
@Composable
fun PlayingBars(isPlaying: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    val isSeen = LocalWindowSeen.current
    val shouldAnimate = isPlaying && isSeen
    val transition = rememberInfiniteTransition(label = "playing_bars")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        BAR_PERIODS_MS.forEachIndexed { i, period ->
            // Only register the animation child when we're actually animating. When shouldAnimate
            // is false the animateFloat still needs to be called (composition can't be conditional
            // inside a forEach), but the transition's clock is stopped when nothing drives it.
            val bounce by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period), RepeatMode.Reverse),
                label = "playing_bar_$i",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(if (shouldAnimate) bounce else PAUSED_BAR_HEIGHTS[i])
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

private val BAR_PERIODS_MS = listOf(420, 560, 360)
private val PAUSED_BAR_HEIGHTS = listOf(0.4f, 0.7f, 0.5f)

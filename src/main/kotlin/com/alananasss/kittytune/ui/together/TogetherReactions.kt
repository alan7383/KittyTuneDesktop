package com.alananasss.kittytune.ui.together

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddReaction
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.LocalWindowSeen
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherReaction
import com.alananasss.kittytune.data.together.TogetherReactionEvent
import com.alananasss.kittytune.ui.common.clearance
import com.alananasss.kittytune.ui.common.rememberPlayerBarOverlap
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt

internal val REACTION_OVERLAY_WIDTH = 104.dp
private val REACTION_OVERLAY_HEIGHT = 296.dp
private const val MAX_FLOATING_REACTIONS = 24
private const val REACTION_RISE_DP_PER_MS = 0.24f

// Different starting speeds and directions keep bursts from stacking on the same path.
private val reactionTrajectories = listOf(
    ReactionTrajectory(horizontal = 0f, durationScale = 1f, riseBias = 0f),
    ReactionTrajectory(horizontal = -1f, durationScale = 0.85f, riseBias = 0.65f),
    ReactionTrajectory(horizontal = 1f, durationScale = 1.08f, riseBias = -0.45f),
    ReactionTrajectory(horizontal = -0.55f, durationScale = 1.18f, riseBias = -0.55f),
    ReactionTrajectory(horizontal = 0.55f, durationScale = 0.78f, riseBias = 0.8f),
)

private val reactionBitmaps by lazy {
    TogetherReaction.entries.associateWith { reaction ->
        val path = "/together/reactions/${reaction.wireId}.png"
        val stream = checkNotNull(TogetherReaction::class.java.getResourceAsStream(path)) { "Missing $path" }
        stream.use { ImageIO.read(it).toComposeImageBitmap() }
    }
}

@Composable
internal fun TogetherReactionButton(
    roomCode: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val activeCode by Together.active.collectAsState()
    val code = activeCode ?: return
    if (roomCode != null && roomCode != code) return
    val connected by Together.isConnected.collectAsState()
    var expanded by remember(code) { mutableStateOf(false) }
    LaunchedEffect(connected) {
        if (!connected) expanded = false
    }
    val label = str("together_reactions")

    Box {
        IconButton(onClick = { expanded = true }, enabled = connected) {
            Icon(
                Icons.Rounded.AddReaction,
                contentDescription = label,
                tint = if (connected) tint else tint.copy(alpha = 0.38f),
            )
        }
        DropdownMenu(expanded = expanded && connected, onDismissRequest = { expanded = false }) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                TogetherReaction.entries.chunked(4).forEach { reactions ->
                    Row {
                        reactions.forEach { reaction ->
                            IconButton(onClick = { Together.sendReaction(code, reaction) }) {
                                ReactionImage(reaction, Modifier.size(28.dp), contentDescription = reaction.emoji)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WindowedTogetherReactionOverlay(modifier: Modifier) {
    val activeCode by Together.active.collectAsState()
    val code = activeCode ?: return
    if (!LocalWindowSeen.current) return

    val barOverlap = rememberPlayerBarOverlap()
    val bottomClearance = barOverlap.clearance()
    Box(modifier.then(barOverlap.modifier).clipToBounds()) {
        TogetherReactionOverlay(
            code = code,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp + bottomClearance),
        )
    }
}

@Composable
internal fun TogetherReactionOverlay(
    code: String,
    modifier: Modifier = Modifier,
    atEnd: Boolean = true,
) {
    if (!LocalWindowSeen.current) return
    val floating = remember(code) { mutableStateListOf<FloatingReaction>() }
    val startsOnRight = atEnd == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    val nicknameStyle = MaterialTheme.typography.labelMedium

    LaunchedEffect(code) {
        var nextId = 0L
        Together.reactions.collect { event ->
            if (event.code != code) return@collect
            if (floating.size == MAX_FLOATING_REACTIONS) floating.removeAt(0)
            floating.add(FloatingReaction(nextId++, event))
        }
    }

    BoxWithConstraints(
        modifier.width(REACTION_OVERLAY_WIDTH).height(REACTION_OVERLAY_HEIGHT).clipToBounds().clearAndSetSemantics {},
    ) {
        val iconSize = minOf(40.dp, (maxWidth - 12.dp).coerceAtLeast(0.dp))
        val nameHeight = with(LocalDensity.current) { nicknameStyle.lineHeight.toDp() } + 8.dp
        val cardHeight = iconSize + nameHeight
        val rise = (maxHeight - cardHeight - 12.dp).coerceIn(0.dp, 228.dp)
        val baseDurationMs = (rise.value / REACTION_RISE_DP_PER_MS).roundToInt().coerceIn(240, 950)
        val spread = ((maxWidth - iconSize) / 2f - 4.dp).coerceAtLeast(0.dp)
        val startX = minOf(10.dp, spread)

        floating.forEach { reaction ->
            key(reaction.id) {
                val trajectory = reactionTrajectories[(reaction.id % reactionTrajectories.size).toInt()]
                val endX = spread * trajectory.horizontal
                val cardWidth = maxWidth - maxOf(startX, spread * abs(trajectory.horizontal)) * 2f
                val progress = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    val durationMs = (baseDurationMs * trajectory.durationScale).roundToInt()
                    progress.animateTo(1f, tween(durationMillis = durationMs, easing = LinearEasing))
                    floating.remove(reaction)
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            val fraction = progress.value
                            val outward = if (startsOnRight) 1f else -1f
                            val spreadProgress = (fraction * 8f).coerceAtMost(1f)
                            translationX = outward * (startX.toPx() * (1f - spreadProgress) + endX.toPx() * spreadProgress)
                            val liftOff = (1f - fraction * 14f).coerceAtLeast(0f)
                            val riseProgress = fraction * (1f + trajectory.riseBias * (1f - fraction))
                            translationY = 14.dp.toPx() * liftOff - rise.toPx() * riseProgress
                            alpha = minOf(fraction * 20f, (1f - fraction) * 5f, 1f)
                            scaleX = 0.8f + minOf(fraction * 3f, 0.2f)
                            scaleY = scaleX
                        }
                        .widthIn(max = cardWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ReactionImage(reaction.event.reaction, Modifier.size(iconSize))
                    Text(
                        text = reaction.event.senderName ?: str("together_invite_default_nickname"),
                        style = nicknameStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionImage(reaction: TogetherReaction, modifier: Modifier, contentDescription: String? = null) {
    Image(
        bitmap = reactionBitmaps.getValue(reaction),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

private data class ReactionTrajectory(val horizontal: Float, val durationScale: Float, val riseBias: Float)
private data class FloatingReaction(val id: Long, val event: TogetherReactionEvent)

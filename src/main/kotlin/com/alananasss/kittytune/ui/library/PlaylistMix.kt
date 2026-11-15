package com.alananasss.kittytune.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.automix.TransitionPlan
import com.alananasss.kittytune.audio.automix.TransitionPreset
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer
import com.alananasss.kittytune.ui.player.mix.CamelotKeyBadge
import com.alananasss.kittytune.ui.player.mix.TransitionPlayheadColor
import com.alananasss.kittytune.ui.player.mix.transitionPresetIcon
import com.alananasss.kittytune.ui.player.mix.transitionPresetTitle

/**
 * What a Mix Mode row shows beside the duration: the analysed tempo and the Camelot badge, coloured by the wheel.
 * [fit] is the harmonic fit against the playing track, kept for colouring the whole row later.
 */
data class MixBadgeUi(
    val bpm: Int,
    val camelot: String?,
    val fit: MixPlaylistAnalyzer.MixKeyFit,
)

/** Width of the Mix column in the track table, so rows and the header stay aligned. */
val MixColumnWidth = 116.dp

/** The Mix column of a track row: "124 BPM" and the Camelot badge. Empty until the track is analysed. */
@Composable
fun MixTrackMeta(badge: MixBadgeUi?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.width(MixColumnWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
    ) {
        if (badge != null && badge.bpm > 0) {
            Text(
                text = str("mix_badge_bpm_only", badge.bpm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        badge?.camelot?.let { CamelotKeyBadge(code = it) }
    }
}

/**
 * The connector between two rows in Mix Mode: a guide line with a plate naming the transition (`Auto ›`,
 * `Blend ›`, `Custom ›`). Clicking it opens the transition editor for that pair.
 */
@Composable
fun MixTransitionConnector(plan: TransitionPlan, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(36.dp).padding(top = 2.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.height(30.dp).clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                if (plan.preset in CONNECTOR_ICON_PRESETS) {
                    Icon(transitionPresetIcon(plan.preset), contentDescription = null, modifier = Modifier.size(13.dp))
                }
                Text(
                    text = transitionPresetTitle(plan.preset) + " ›",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Presets that draw their icon on the connector plate. */
private val CONNECTOR_ICON_PRESETS = setOf(
    TransitionPreset.AUTO,
    TransitionPreset.SLAM,
    TransitionPreset.CUSTOM,
    TransitionPreset.BLEND,
)

/** The Mix Mode switch of a playlist: green with a dot while on. */
@Composable
fun MixModeToggleButton(enabled: Boolean, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
    ) {
        Box {
            Icon(
                Icons.Rounded.Tune,
                contentDescription = str(if (enabled) "mix_mode_disable" else "mix_mode_enable"),
                tint = if (enabled) TransitionPlayheadColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enabled) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(8.dp)
                        .background(TransitionPlayheadColor, CircleShape)
                        .border(1.5.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
                )
            }
        }
    }
}

/** The harmonic smart sort, offered while Mix Mode is on. */
@Composable
fun MixSmartSortButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes(), modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)) {
        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = str("mix_smart_sort"), tint = TransitionPlayheadColor)
    }
}

/**
 * Mix Mode's play control: the CDJ-style round button (a green platter inside a double rim with a soft glow),
 * in place of the plain Play pill.
 */
@Composable
fun MixCdjPlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(52.dp).clip(CircleShape).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(TransitionPlayheadColor.copy(alpha = 0.30f), Color.Transparent),
                        radius = size.maxDimension,
                    ),
                    radius = size.maxDimension,
                )
            }
        )
        Box(Modifier.fillMaxSize().clip(CircleShape).border(1.dp, TransitionPlayheadColor.copy(alpha = 0.45f), CircleShape))
        Box(Modifier.size(44.dp).clip(CircleShape).border(1.5.dp, TransitionPlayheadColor.copy(alpha = 0.85f), CircleShape))
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(TransitionPlayheadColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = str("btn_play"), tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.LyricsDisplayStyle

/**
 * The four ways to set the current line apart, each shown as it looks rather than named (issue #33,
 * round 5: "a preview for each mode").
 *
 * They used to be two checkboxes, Scale and Focus, whose combination was the mode — which left the reader to
 * imagine what "scale + focus" does to a page of lyrics. Every card draws the same five sample lines through
 * [LyricLineStyling.treatmentFor], the function the lyric views themselves use, so a preview cannot drift from
 * the real thing.
 */
@Composable
internal fun LyricsDisplayStylePicker(
    selected: LyricsDisplayStyle,
    onSelect: (LyricsDisplayStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LyricsDisplayStyle.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { style ->
                    StyleCard(style, isSelected = style == selected, onClick = { onSelect(style) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StyleCard(style: LyricsDisplayStyle, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (isSelected) scheme.secondaryContainer else scheme.surfaceContainerHigh, label = "styleCard"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = if (isSelected) BorderStroke(2.dp, scheme.primary) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            StylePreview(style)
            Text(
                text = str(style.labelKey),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) scheme.onSecondaryContainer else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Five short lines around a current one, drawn with the treatment [style] gives each distance. */
@Composable
private fun StylePreview(style: LyricsDisplayStyle) {
    Column(
        modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SAMPLE_WIDTHS.forEachIndexed { i, width ->
            val distance = i - SAMPLE_WIDTHS.size / 2
            val treatment = LyricLineStyling.treatmentFor(style, distance, focusBlur = 1.dp)
            Box(
                Modifier
                    .scale(treatment.scale)
                    .alpha(treatment.alpha)
                    .then(if (treatment.blur > 0.dp) Modifier.blur(treatment.blur) else Modifier)
                    .width(width)
                    .height(if (distance == 0) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

private val LyricsDisplayStyle.labelKey: String
    get() = when (this) {
        LyricsDisplayStyle.STANDARD -> "lyrics_style_standard"
        LyricsDisplayStyle.SCALE -> "lyrics_style_scale"
        LyricsDisplayStyle.FOCUS -> "lyrics_style_focus"
        LyricsDisplayStyle.SCALE_FOCUS -> "lyrics_style_scale_focus"
    }

/** Uneven lengths, so the sample reads as lines of a song rather than as a striped block. */
private val SAMPLE_WIDTHS = listOf(52.dp, 78.dp, 70.dp, 60.dp, 44.dp)
private val PREVIEW_HEIGHT = 72.dp

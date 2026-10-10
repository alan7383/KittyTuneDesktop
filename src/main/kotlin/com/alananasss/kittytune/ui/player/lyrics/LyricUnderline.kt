package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.sp

/**
 * A hover rule under text, drawn by hand.
 *
 * `TextDecoration.Underline` is drawn per font run, and a line routinely spans several runs: the variable UI
 * font has no Cyrillic, Arabic or CJK coverage, so those stretches fall back to a system face with its own
 * underline thickness and position. The result was a rule that looked dashed and stepped (issue #33). One rect
 * per laid-out line, at one thickness, is the same rule whatever the script.
 *
 * Lyric lines themselves show a soft box on hover now; this stays for links (artists, owners, the playing
 * context), where a rule is what a link looks like.
 *
 * @param layout the last layout of the text this sits on, read lazily so a relayout is picked up without
 *   recreating the modifier.
 * @param fontSizeSp the text's font size, which the thickness and the drop below the baseline are derived from.
 */
internal fun Modifier.lyricUnderline(
    layout: () -> TextLayoutResult?,
    visible: Boolean,
    fontSizeSp: Float,
    color: Color,
): Modifier = drawWithContent {
    drawContent()
    if (!visible) return@drawWithContent
    val result = layout() ?: return@drawWithContent
    val thickness = (fontSizeSp * 0.06f).sp.toPx().coerceAtLeast(1f)
    val drop = (fontSizeSp * 0.14f).sp.toPx()
    for (i in 0 until result.lineCount) {
        val left = result.getLineLeft(i)
        val right = result.getLineRight(i)
        if (right - left <= 0f) continue
        drawRect(
            color = color,
            topLeft = Offset(left, result.getLineBaseline(i) + drop),
            size = Size(right - left, thickness),
        )
    }
}

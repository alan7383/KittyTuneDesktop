package com.alananasss.kittytune.ui.player.lyrics.accompanist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.alananasss.kittytune.ui.player.lyrics.BackingVocalRun
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.backingVocalRuns
import com.alananasss.kittytune.ui.player.lyrics.splitTimedLyricParts
import com.alananasss.kittytune.ui.player.lyrics.timedBackingVocalLines
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment

@Composable
internal fun TimedBackingVocalText(
    line: LyricLine,
    currentTimeProvider: () -> Int,
    textStyle: TextStyle,
    activeColor: Color,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    val runs = remember(line, textAlign) {
        val alignment = when (textAlign) {
            TextAlign.Center -> KaraokeAlignment.Unspecified
            TextAlign.End, TextAlign.Right -> KaraokeAlignment.End
            else -> KaraokeAlignment.Start
        }
        val parts = splitTimedLyricParts(line.words, splitBrackets = true)
        val phrases = timedBackingVocalLines(parts.backingPhrases, alignment)
        val end = maxOf(line.endTime, parts.mainWords.maxOfOrNull { it.endTime } ?: 0)
        backingVocalRuns(phrases, end.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
    }
    BackingVocalRows(runs, currentTimeProvider, textStyle, activeColor, modifier)
}

@Composable
internal fun BackingVocalRows(
    runs: List<BackingVocalRun>,
    currentTimeProvider: () -> Int,
    textStyle: TextStyle,
    activeColor: Color,
    modifier: Modifier = Modifier,
    blendMode: BlendMode = BlendMode.SrcOver,
    expandFrom: Alignment.Vertical = Alignment.Top,
) {
    val currentTime by rememberUpdatedState(currentTimeProvider)
    for (run in runs) {
        val visible by remember(run) { derivedStateOf { run.isVisibleAt(currentTime()) } }
        AnimatedVisibility(
            visible = visible,
            modifier = modifier,
            enter = expandVertically(tween(160), expandFrom = expandFrom),
            exit = shrinkVertically(tween(160), shrinkTowards = expandFrom),
        ) {
            KaraokeLineText(
                line = run.line,
                currentTimeProvider = currentTime,
                modifier = Modifier.graphicsLayer {
                    alpha = run.opacityAt(currentTime())
                    this.blendMode = blendMode
                },
                accompanimentLineTextStyle = textStyle,
                activeColor = activeColor,
                blendMode = blendMode,
                backingVocalRun = run,
            )
        }
    }
}

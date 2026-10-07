package com.alananasss.kittytune.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricLineText
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.alananasss.kittytune.ui.player.lyrics.rememberSmoothPosition
import com.alananasss.kittytune.ui.player.lyrics.separateBackingVocals

/**
 * The line being sung, on one row above the transport (issue #66): for when the app is open for something else
 * and the song is still worth following. Words light up one by one where the lyrics are timed that way, backing
 * vocals follow the line in a quieter voice, and a note stands in for a break or for lyrics that are not there yet.
 * Switched in the lyrics settings; not drawn in the full player, which has the whole lyrics.
 */
@Composable
internal fun OneLineLyric(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val lines = vm.lyricsLines
    val position = rememberSmoothPosition(vm.currentPosition, vm.isPlaying, 1f) + vm.lyricsOffset
    val activeIndex = if (lines.isEmpty()) -1 else LyricsUtils.activeLineIndex(lines, position.toLong())
    val active = lines.getOrNull(activeIndex)
    val showsWords = active != null && !active.isInstrumental && active.text.isNotBlank()

    Box(modifier.fillMaxWidth().height(LINE_HEIGHT).clipToBounds(), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = if (showsWords) activeIndex else -1,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                    (fadeOut(tween(140)) + slideOutVertically(tween(200)) { -it / 2 })
            },
            label = "oneLineLyric",
        ) { index ->
            val line = lines.getOrNull(index)
            if (line == null) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp),
                )
            } else {
                SungLine(vm, line, position)
            }
        }
    }
}

@Composable
private fun SungLine(vm: PlayerViewModel, line: LyricLine, position: Float) {
    val scheme = MaterialTheme.colorScheme
    val (main, backing) = remember(line, vm.lyricsSplitBackingVocals) {
        if (vm.lyricsSplitBackingVocals) separateBackingVocals(line) else line to null
    }
    val base = MaterialTheme.typography.labelLarge
    val own = base.copy(fontWeight = FontWeight.SemiBold)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        LyricLineText(
            line = main,
            isActive = true,
            positionMs = position,
            wordSync = vm.isWordSyncEnabled,
            fillEffect = vm.isAppleMusicEffectEnabled,
            activeStyle = own,
            inactiveStyle = own,
            activeColor = scheme.onSurface,
            inactiveColor = scheme.onSurfaceVariant,
            unsungColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = MAX_LINE_WIDTH).let { if (backing == null) it else it.widthIn(max = MAX_MAIN_WIDTH) },
        )
        if (backing != null) {
            LyricLineText(
                line = backing,
                isActive = true,
                positionMs = position,
                wordSync = vm.isWordSyncEnabled,
                fillEffect = vm.isAppleMusicEffectEnabled,
                activeStyle = base.copy(fontWeight = FontWeight.Normal),
                inactiveStyle = base.copy(fontWeight = FontWeight.Normal),
                activeColor = scheme.onSurfaceVariant,
                inactiveColor = scheme.onSurfaceVariant,
                unsungColor = scheme.onSurfaceVariant.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = MAX_BACKING_WIDTH),
            )
        }
    }
}

private val LINE_HEIGHT = 18.dp
private val MAX_LINE_WIDTH = 520.dp
private val MAX_MAIN_WIDTH = 340.dp
private val MAX_BACKING_WIDTH = 170.dp

package com.alananasss.kittytune.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.player.LyricsMode
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricLineText
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.alananasss.kittytune.ui.player.lyrics.rememberSmoothPosition
import com.alananasss.kittytune.ui.player.lyrics.separateBackingVocals
import com.alananasss.kittytune.ui.player.lyrics.withEvenWords

/**
 * The line being sung, in a small card floating above the play button (issue #66): for when the app is open for
 * something else and the song is still worth following. It used to sit inside the player bar and made it taller;
 * the bar keeps its height now and the card hovers just over it. Words light up one by one where the lyrics are
 * timed that way, a note stands in for a break, and backing vocals are left out, as there is no room to set them
 * apart. Only for timed lyrics, switched in the lyrics settings, and not in the full player, which has them all.
 *
 * Placed at the top of the bar and drawn above it: it takes no room in the bar's layout.
 */
@Composable
internal fun FloatingLyricChip(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val lines = vm.lyricsLines
    val isTimed = vm.lyricsMode == LyricsMode.SYNCED && lines.isNotEmpty() && !vm.isLyricsLoading
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { CHIP_GAP.roundToPx() }

    AnimatedVisibility(
        visible = isTimed,
        enter = fadeIn(tween(220)) + scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.9f),
        exit = fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.9f),
        modifier = modifier.layout { measurable, constraints ->
            val chip = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            // Takes no height in the bar and is drawn above its top edge.
            layout(chip.width, 0) { chip.place(0, -chip.height - gapPx) }
        },
    ) {
        val position = rememberSmoothPosition(vm.currentPosition, vm.isPlaying, 1f) + vm.lyricsOffset
        val activeIndex = LyricsUtils.activeLineIndex(lines, position.toLong())
        val active = lines.getOrNull(activeIndex)
        val showsWords = active != null && !active.isInstrumental && active.text.isNotBlank()
        val scheme = MaterialTheme.colorScheme
        // Quiet: one step above the panel, hardly raised and with no outline, so it reads as part of the bar and the
        // words are what stands out. It was the lightest thing on the screen, with a rim and a deep shadow.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = scheme.surfaceContainer.copy(alpha = 0.94f),
            contentColor = scheme.onSurface,
            shadowElevation = 3.dp,
        ) {
            AnimatedContent(
                targetState = if (showsWords) activeIndex else -1,
                transitionSpec = {
                    (fadeIn(tween(200, delayMillis = 50)) + slideInVertically(tween(240)) { it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 2 }) using
                        SizeTransform(clip = false) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) }
                },
                contentAlignment = Alignment.Center,
                label = "floatingLyric",
            ) { index ->
                val line = lines.getOrNull(index)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = if (line == null) 12.dp else 16.dp, vertical = 8.dp),
                ) {
                    if (line == null) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        // A line timed only as a whole gets the words spread across it, so it fills in as smoothly as one
                        // timed word by word; the next line's start is where it ends when it has no end of its own.
                        SungLine(vm, line.withEvenWords(lines.getOrNull(index + 1)?.startTime), position, isEven = line.words.isEmpty())
                    }
                }
            }
        }
    }
}

@Composable
private fun SungLine(vm: PlayerViewModel, line: LyricLine, position: Float, isEven: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val main = remember(line) { separateBackingVocals(line).first }
    val style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    LyricLineText(
        line = main,
        isActive = true,
        positionMs = position,
        // Words spread evenly over a line are only worth showing as a smooth fill: coloured a word at a time on a
        // guess, they would jump.
        wordSync = vm.isWordSyncEnabled || isEven,
        fillEffect = vm.isAppleMusicEffectEnabled || isEven,
        activeStyle = style,
        inactiveStyle = style,
        activeColor = scheme.onSurface,
        inactiveColor = scheme.onSurfaceVariant,
        unsungColor = scheme.onSurfaceVariant.copy(alpha = 0.55f),
        textAlign = TextAlign.Center,
        // As wide as the words, so a short line is a small card instead of one the width of the longest.
        fillWidth = false,
        modifier = Modifier.widthIn(max = MAX_LINE_WIDTH),
    )
}

/** Air between the card and the top of the player bar. */
private val CHIP_GAP = 10.dp
private val MAX_LINE_WIDTH = 460.dp

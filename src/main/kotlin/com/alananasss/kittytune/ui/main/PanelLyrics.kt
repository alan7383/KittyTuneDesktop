package com.alananasss.kittytune.ui.main

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.lyrics.FollowActiveLine
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricLineStyling
import com.alananasss.kittytune.ui.player.lyrics.LyricLineText
import com.alananasss.kittytune.ui.player.lyrics.rememberSmoothPosition
import com.alananasss.kittytune.ui.player.lyrics.FollowPlainLyrics
import com.alananasss.kittytune.ui.player.lyrics.lyricsWheel
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import kotlinx.coroutines.isActive

/**
 * The lyrics as the right-hand panel shows them (issue #33).
 *
 * The panel's copy used to be a plain list that never moved: every line already sung was
 * highlighted rather than only the current one, nothing scrolled, and text with no timings sat
 * still. Anybody who preferred the panel to the full screen had to follow along by hand.
 *
 * It now behaves like the full screen — same follow rule, same auto-scroll for untimed text, same
 * hand-back on a wheel or a drag — at panel size. Used both by the panel's own Lyrics tab and by
 * the lyrics half of the info tab, so the two cannot drift apart.
 */
@Composable
fun PanelLyrics(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val lines = vm.lyricsLines
    when {
        lines.isNotEmpty() -> PanelSyncedLyrics(vm, lines, modifier)
        !vm.rawPlainLyrics.isNullOrBlank() -> PanelPlainLyrics(vm, modifier)
        else -> Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = str("lyrics_no_data"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PanelSyncedLyrics(vm: PlayerViewModel, lines: List<LyricLine>, modifier: Modifier) {
    val listState = rememberLazyListState()
    // Derived rather than read straight from the position, so the lines recompose when the line
    // changes and not on every progress tick.
    val activeIndex by remember {
        derivedStateOf { LyricsUtils.activeLineIndex(vm.lyricsLines, vm.currentPosition + vm.lyricsOffset) }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        // Both callers give this a bounded height, but a caller inside a scrolling column would hand it
        // `Dp.Infinity` — and an infinite anchor becomes `Int.MAX_VALUE` scroll offset and an infinite
        // padding, neither of which fails in a way anyone could diagnose from the symptom.
        val viewportHeight = if (maxHeight.isSpecified && maxHeight.value.isFinite()) maxHeight else 320.dp
        val viewportPx = with(density) { viewportHeight.toPx() }

        // The active line settles about a third of the way down, and it gets there by *scrolling* to that
        // spot rather than by padding a third of the panel away.
        //
        // It used to be padding: a third of the panel's height above the first line and half of it below the
        // last. On a full screen that reads as breathing room; in a panel it was most of the panel, so a song
        // opened on a large empty rectangle with the lyrics below it, and ended on the same rectangle upside
        // down. Anchoring with a scroll offset costs nothing when there is content above the line, and when
        // there is not, the first line simply sits at the top where it belongs (issue #33).
        val anchorPx = (viewportPx * ANCHOR_FRACTION).toInt()

        FollowActiveLine(listState, activeIndex, anchorPx)

        // Interpolated between the player's four-per-second reports, so the word fill in the panel is as
        // smooth as it is on the full screen instead of stepping (issue #33).
        val smoothPosition = rememberSmoothPosition(
            positionMs = vm.currentPosition,
            isPlaying = vm.isPlaying,
            speed = vm.effectsState.speed,
        ) + vm.lyricsOffset

        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                // Enough for the closing lines to rise clear of the bottom edge, and no more. This was half
                // the panel, which left the same empty rectangle at the end of a song that the top inset
                // left at the start.
                bottom = viewportHeight * TAIL_FRACTION,
            ),
        ) {
            items(lines.size) { index ->
                val line = lines[index]
                PanelLyricLine(
                    vm = vm,
                    line = line,
                    // Negative for lines already sung. Before the first line starts there is no current
                    // line, and treating every line as "far away" would shrink the whole panel — so the
                    // distance is zero for all of them until the song reaches the words.
                    distance = if (activeIndex < 0) 0 else index - activeIndex,
                    positionMs = smoothPosition,
                    // The offset shifts the lyrics against the audio, so the position that makes
                    // this line current is its start minus that offset.
                    // Clamped to the track, not merely to zero. A lyric sheet matched from a longer
                    // song carries timestamps past this track's end, and a seek past the end used to
                    // land back at the start (issue #33).
                    onClick = {
                        val target = line.startTime - vm.lyricsOffset
                        val last = (vm.duration - 1).coerceAtLeast(0L)
                        vm.seekTo(target.coerceIn(0L, last))
                    },
                )
            }
        }
    }
}

/**
 * One line, drawn with the shared treatment and the shared renderer.
 *
 * Nothing about the look is decided here any more. The panel used to have its own reading of every setting —
 * it grew the current line where the full screen shrank the others, it never blurred under "focus", and it
 * ignored word-by-word entirely — so the same three switches produced different results depending on which
 * view you happened to be looking at (issue #33). Typography and spacing are still the panel's own, because
 * a side panel is not a full screen; the treatment and the words are not.
 */
@Composable
private fun PanelLyricLine(
    vm: PlayerViewModel,
    line: LyricLine,
    distance: Int,
    positionMs: Float,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isActive = distance == 0

    val treatment = LyricLineStyling.treatmentFor(
        style = vm.lyricsDisplayStyle,
        distance = distance,
        // Softer than the full screen's: this text is a third of the size, and the radius that reads as
        // depth behind a headline turns a panel line into a smudge.
        focusBlur = 1.dp,
    )

    val scale by animateFloatAsState(treatment.scale, tween(260), label = "panelLyricScale")
    val alpha by animateFloatAsState(treatment.alpha, tween(260), label = "panelLyricAlpha")
    val blur by animateDpAsState(treatment.blur, tween(260), label = "panelLyricBlur")

    val base = MaterialTheme.typography.titleMedium
    val activeStyle = base.copy(fontWeight = FontWeight.Bold, lineHeight = base.fontSize * 1.35f)
    val inactiveStyle = activeStyle.copy(fontWeight = FontWeight.SemiBold)

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .graphicsLayer {
                // Scaled about the leading edge rather than the middle, so a shrinking line does not
                // drift inward and back as the song moves past it.
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .alpha(alpha)
            // Only when there is something to blur: the modifier forces the line into its own layer,
            // which is not worth paying for at 0.dp.
            .then(if (blur > 0.dp) Modifier.blur(blur) else Modifier)
    ) {
        LyricLineText(
            line = line,
            isActive = isActive,
            positionMs = positionMs,
            wordSync = vm.isWordSyncEnabled,
            fillEffect = vm.isAppleMusicEffectEnabled,
            activeStyle = activeStyle,
            inactiveStyle = inactiveStyle,
            activeColor = scheme.onSurface,
            inactiveColor = scheme.onSurfaceVariant,
            unsungColor = scheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Start,
        )
        val secondary = line.translation ?: line.romanization
        if (!secondary.isNullOrBlank()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Text with no timings.
 *
 * There is nothing to follow, so where it sits is worked out from the playback position instead — see
 * [FollowPlainLyrics], which is also what the full screen uses, so the two cannot disagree about what
 * a given speed means.
 */
@Composable
private fun PanelPlainLyrics(vm: PlayerViewModel, modifier: Modifier) {
    val text = vm.rawPlainLyrics.orEmpty()
    val lines = remember(text) { text.split("\n") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Wheel and drag always win: the reader is following the words, and having the view creep out
    // from under them would be worse than no auto-scroll at all.
    var lastUserScrollMs by remember { mutableStateOf(0L) }

    FollowPlainLyrics(
        listState = listState,
        enabled = vm.isPlainAutoScrollEnabled,
        speed = vm.effectivePlainAutoScrollSpeed,
        lineCount = lines.size,
        positionMs = { vm.currentPosition },
        isPlaying = { vm.isPlaying },
        playbackSpeed = { vm.effectsState.speed },
        lastManualScrollMs = { lastUserScrollMs },
    )

    LazyColumn(
        modifier
            .fillMaxSize()
            .lyricsWheel(
                listState = listState,
                scope = scope,
                lines = { vm.lyricsWheelLines },
                onManualScroll = { lastUserScrollMs = System.currentTimeMillis() },
            ),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(lines) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            )
        }
    }
}

/**
 * How far down the panel the current line settles, as a fraction of its height.
 *
 * Low enough that several lines still to come stay visible — which is the half worth reading — and high
 * enough that the line just sung is still on screen for context.
 */
private const val ANCHOR_FRACTION = 0.32f

/** How much room the last lines get to rise into, as a fraction of the panel's height. */
private const val TAIL_FRACTION = 0.35f

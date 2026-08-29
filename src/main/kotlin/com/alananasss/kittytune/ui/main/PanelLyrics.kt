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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.Dp
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
fun PanelLyrics(
    vm: PlayerViewModel,
    modifier: Modifier = Modifier,
    /** How much room the words get around themselves. See [PanelLyricsStyle]. */
    style: PanelLyricsStyle = PanelLyricsStyle.Panel,
) {
    val lines = vm.lyricsLines
    when {
        lines.isNotEmpty() -> PanelSyncedLyrics(vm, lines, modifier, style)
        !vm.rawPlainLyrics.isNullOrBlank() -> PanelPlainLyrics(vm, modifier, style)
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
private fun PanelSyncedLyrics(
    vm: PlayerViewModel,
    lines: List<LyricLine>,
    modifier: Modifier,
    style: PanelLyricsStyle,
) {
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
        // The inset counts towards the anchor rather than adding to it. A list applies its scroll offset
        // inside its content padding, so asking for 30% on top of a 36% inset put the current line at two
        // thirds of the way down the screen — "quand on zoome, les lyrics se retrouvent en bas" (issue #33).
        val anchorPx =
            (viewportPx * (style.anchorFraction - style.topInsetFraction).coerceAtLeast(0f)).toInt()

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
                start = style.startPadding,
                end = style.endPadding,
                // Before the first line there is nothing to scroll to, so the list sits where it starts —
                // and a list starts at its top inset. On a full screen that is the difference between the
                // words beginning in the middle of the page and beginning jammed against the ceiling:
                // "quand on est au début, il faut que le lyrics soit au milieu ou un peu vers le haut et pas
                // tout en haut car ça fait vraiment pas beau" (issue #33). Once the song reaches the words
                // the anchored scroll takes over and this costs nothing.
                top = viewportHeight * style.topInsetFraction,
                // Enough for the closing lines to rise clear of the bottom edge, and no more. This was half
                // the panel, which left the same empty rectangle at the end of a song that the top inset
                // left at the start.
                bottom = viewportHeight * style.tailFraction,
            ),
        ) {
            items(lines.size) { index ->
                val line = lines[index]
                PanelLyricLine(
                    vm = vm,
                    line = line,
                    lineSpacing = style.lineSpacing,
                    // Negative for lines already sung. Before the first line starts there is no current
                    // line, and treating every line as "far away" would shrink the whole panel — so the
                    // distance is zero for all of them until the song reaches the words.
                    distance = if (activeIndex < 0) 0 else index - activeIndex,
                    positionMs = smoothPosition,
                    // Shared with the full screen, which had its own copy of this and got it
                    // differently wrong — see [LyricsUtils.seekTargetFor] for what the clamp does
                    // with a duration that is not known yet and with a line past the end.
                    onClick = {
                        LyricsUtils.seekTargetFor(
                            line = line,
                            lyricsOffsetMs = vm.lyricsOffset,
                            durationMs = vm.duration,
                        )?.let(vm::seekTo)
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
    lineSpacing: Dp,
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

    // The reader's own alignment, which this view used to ignore: "ça doit prendre en compte les paramètres
    // lyrics, si on met centré ça met centré" (issue #33). The full screen honoured it and the panel did not,
    // which is the same class of bug as every other one where these two disagreed.
    val textAlign = alignmentOf(vm)
    val columnAlign = when (textAlign) {
        TextAlign.Center -> Alignment.CenterHorizontally
        TextAlign.End -> Alignment.End
        else -> Alignment.Start
    }

    Column(
        horizontalAlignment = columnAlign,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = lineSpacing)
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
            textAlign = textAlign,
        )
        // Gated on the switches, which it was not: a fetched translation stayed in the line, so turning the
        // setting *off* left it on screen until the track changed. "Quand on active la traduction,
        // romanization il faut que ça se fasse direct en updatant l'écran avec anim" — direct in both
        // directions, and animated in both (issue #33).
        val secondary = when {
            vm.isLyricsTranslationEnabled -> line.translation ?: line.romanization
            vm.isRomanizationEnabled -> line.romanization
            else -> null
        }
        // Held past its own disappearance, so the line does not lose its words a frame before it loses the
        // height they were sitting in.
        var lastShown by remember { mutableStateOf("") }
        if (!secondary.isNullOrBlank()) lastShown = secondary
        androidx.compose.animation.AnimatedVisibility(
            visible = !secondary.isNullOrBlank(),
            enter = androidx.compose.animation.fadeIn(tween(SECONDARY_FADE_MS)) +
                androidx.compose.animation.expandVertically(tween(SECONDARY_FADE_MS)),
            exit = androidx.compose.animation.fadeOut(tween(SECONDARY_FADE_MS)) +
                androidx.compose.animation.shrinkVertically(tween(SECONDARY_FADE_MS)),
        ) {
            Text(
                text = lastShown,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
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
private fun PanelPlainLyrics(vm: PlayerViewModel, modifier: Modifier, style: PanelLyricsStyle) {
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
        contentPadding = PaddingValues(
            start = style.startPadding,
            end = style.endPadding,
            top = 12.dp,
            bottom = 24.dp,
        ),
    ) {
        items(lines) { line ->
            // The same type and the same alignment a sung line gets, because "les lyrics qui sont juste en
            // texte tout seul, c'est vraiment moche" was about exactly this: untimed words were set in
            // `bodyMedium` and left-aligned whatever the reader had chosen, so a song without timings looked
            // like a different app from the same song with them. There is no current line to light up — that
            // is what untimed means — but everything else about how they are set can match (issue #33).
            //
            // A blank line in the source stays a blank line: the verse breaks are most of what makes a sheet
            // readable, and they were the one thing the old version did keep.
            if (line.isBlank()) {
                Spacer(Modifier.height(style.lineSpacing * 2))
            } else {
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = alignmentOf(vm),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = style.lineSpacing),
                )
            }
        }
    }
}

/** The reader's chosen alignment, in the one place both halves of this file read it from. */
@Composable
private fun alignmentOf(vm: PlayerViewModel): TextAlign = when (vm.lyricsAlignment) {
    com.alananasss.kittytune.data.local.LyricsAlignment.LEFT -> TextAlign.Start
    com.alananasss.kittytune.data.local.LyricsAlignment.CENTER -> TextAlign.Center
    com.alananasss.kittytune.data.local.LyricsAlignment.RIGHT -> TextAlign.End
}

/** Long enough to read as the line growing a second half, short enough not to lag behind the switch. */
private const val SECONDARY_FADE_MS = 220

/**
 * The room the words get around themselves, which is the whole of what differs between reading them in a
 * side panel and reading them on a full screen (issue #33).
 *
 * A bundle rather than four parameters, because they only ever change together: every one of them is a
 * consequence of how big the type is, and a caller that got three of the four right would look worse than one
 * that took the wrong preset entirely.
 *
 * @param startPadding inset for the text's leading edge.
 * @param endPadding inset for its trailing edge. Deliberately larger than [startPadding] on the full screen:
 *   the list runs to the window edge so the scrollbar can sit against it — "met la barre de slide tout à
 *   droite" — which means the text needs room not to run underneath it.
 * @param topInsetFraction where the first line sits before the song has reached the words, as a fraction of
 *   the viewport. A list with nothing to scroll to sits at its top inset, so this is what puts the opening
 *   line in the middle of the page rather than against the ceiling.
 * @param tailFraction how much room the closing lines get to rise into.
 * @param anchorFraction how far down the current line settles once the song is following. Measured from the
 *   top of the viewport, and [topInsetFraction] counts towards it: a list's scroll offset is applied inside
 *   the content padding, so an inset of 0.36 and an anchor of 0.30 put the line at 0.66 rather than 0.30 —
 *   which is what sent the current line to the bottom of the screen as soon as the type got large enough for
 *   the inset to matter (issue #33).
 * @param lineSpacing air between one line and the next. Part of the preset rather than a setting: it is a
 *   consequence of how big the type is, and the type is what the reader actually adjusts.
 */
data class PanelLyricsStyle(
    val startPadding: Dp,
    val endPadding: Dp,
    val topInsetFraction: Float,
    val tailFraction: Float,
    val anchorFraction: Float,
    val lineSpacing: Dp,
) {
    companion object {
        /**
         * A side panel, where the height is what is in shortest supply.
         *
         * A third of a panel of air above the first line reads as most of the panel being empty, which is why
         * the inset here is a token one and the anchoring does the work instead.
         */
        val Panel = PanelLyricsStyle(
            startPadding = 16.dp,
            endPadding = 16.dp,
            topInsetFraction = 0.03f,
            tailFraction = 0.35f,
            anchorFraction = 0.32f,
            lineSpacing = 6.dp,
        )

        /** A full screen, where the same air is the point of the thing. */
        val FullScreen = PanelLyricsStyle(
            startPadding = 24.dp,
            endPadding = 48.dp,
            topInsetFraction = 0.34f,
            tailFraction = 0.55f,
            // The inset already puts the line a third of the way down, so this asks for nothing on top of it.
            anchorFraction = 0.34f,
            // Overwritten by the caller, which scales it to whatever size the reader has chosen.
            lineSpacing = 14.dp,
        )
    }
}

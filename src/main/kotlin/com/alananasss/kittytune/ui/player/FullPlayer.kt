package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.main.PanelLyrics

/**
 * The player at the size of the window, built from the reference he sent (issue #33).
 *
 * ## Why there was a first attempt and why this is not it
 *
 * The first one was described, not shown: "the cover appears on the left, text appears on the right […]
 * all buttons such as rewind the next track, last track, volume, shuffle, etc. it's under the cover". I
 * built exactly that on the app's own surface colour, with a large filled play button and a cover sized
 * to most of the height, and the verdict was "c'est très très mauvais". Fair — the words describe the
 * arrangement and say nothing about what makes the thing worth looking at.
 *
 * Then he sent a screenshot of Apple Music's, and everything that was wrong is in it:
 *
 *  - **The colour is the record's.** Full-bleed, saturated, with one soft bloom off the top right corner.
 *    Not a panel colour with a cover placed on it — the cover is where the whole screen's colour comes
 *    from, which is why [ThemeState.coverSeedColor] is read directly here rather than going through the
 *    scheme.
 *  - **The cover is small.** About a third of the width, left of centre, with air around it. It is not
 *    trying to be the biggest thing on screen; the words are.
 *  - **The controls are quiet.** A hairline progress bar, times at the ends, small glyphs. No filled
 *    primary button — nothing in that screenshot asks to be pressed, because the screen is for reading.
 *  - **The words are enormous and mostly transparent.** The line being sung is nearly opaque and the rest
 *    are the same white at a third of it, so the page reads as one block of text with a lit line in it.
 *
 * ## One lyrics renderer, told to look different
 *
 * The words are [PanelLyrics] — the renderer the side panel already uses, which follows the song, handles
 * word-by-word and untimed text, and honours every lyrics setting. It reads its colours and its type from
 * the theme, so instead of adding parameters to it (or copying it, which is how the sidebar ended up with
 * two layouts that disagreed for three rounds) this hands it a *local* theme: white on the record's
 * colour, at four times the size. One renderer, one place where the following logic lives, and a caller
 * that says what it should look like.
 */
@Composable
fun FullPlayerScreen(viewModel: PlayerViewModel, onExitFullScreen: () -> Unit) {
    val track = viewModel.currentTrack
    var showText by remember { mutableStateOf(true) }
    var showQuickSettings by remember { mutableStateOf(false) }

    // Nothing to build a screen around. Leaving rather than drawing an empty sleeve on a grey wall: the
    // lyrics screen underneath is still there and is the better thing to be looking at.
    if (track == null) {
        LaunchedEffect(Unit) { onExitFullScreen() }
        return
    }

    if (showQuickSettings) {
        // The same dialog the lyrics screen's own gear opens. Asked for explicitly — "pouvoir aussi avoir
        // les paramètres rapides du lyrics screen" — and it would be needed anyway: the offset controls are
        // the one thing you reach for *while* reading along, which is exactly what this screen is for.
        com.alananasss.kittytune.ui.player.lyrics.QuickLyricsSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showQuickSettings = false },
        )
    }

    // Escape and the mouse's back button leave, through the app's own back stack so this takes precedence
    // over whatever registered before it and gives way to a dialog opened on top. A full-window view whose
    // only exit is a dim glyph in a corner is a trap, and being trapped in a view is the complaint that
    // produced the search-field fix a few commits ago (issue #33).
    com.alananasss.kittytune.core.BackHandler(onBack = onExitFullScreen)

    val palette = rememberFullPlayerPalette()

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.base)
            // The bloom. One soft light off the top right, which is the only thing breaking the flat
            // colour in the reference and the whole reason it does not look like a coloured rectangle.
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(palette.bloom, Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.06f),
                        radius = size.minDimension * 0.9f,
                    )
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                CoverColumn(
                    viewModel = viewModel,
                    palette = palette,
                    showText = showText,
                    onToggleText = { showText = !showText },
                )
            }

            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    expandHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        expandFrom = Alignment.Start,
                    ),
                exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                    shrinkHorizontally(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        shrinkTowards = Alignment.Start,
                    ),
                modifier = Modifier.weight(LYRICS_SHARE).fillMaxHeight(),
            ) {
                LyricsOnCoverColour(viewModel, palette)
            }
        }

        // The two things this screen needs of its own, in the corner and dim: the way out, and the lyrics
        // settings. The reference has no chrome at all, and controls that announce themselves would be the
        // loudest thing on a screen built for reading — but a full-window view with no visible exit is a
        // trap, which is the complaint that produced the search-field fix.
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietButton(
                icon = Icons.Rounded.Tune,
                label = str("pref_lyrics_title"),
                tint = if (viewModel.lyricsOffset != 0L) palette.bright else palette.dim,
                onClick = { showQuickSettings = true },
            )
            QuietButton(
                icon = Icons.Rounded.CloseFullscreen,
                label = str("lyrics_exit_fullscreen"),
                tint = palette.dim,
                onClick = onExitFullScreen,
            )
        }
    }
}

/** The words get more room than the cover, because in the reference they are the subject. */
private const val LYRICS_SHARE = 1.3f

/**
 * The four colours the screen is built from, all of them the record's.
 *
 * Derived rather than taken: a cover's dominant colour is often too light or too washed out to put white
 * text on, so it is pushed towards a deep, saturated version of itself — which is what Apple's own screen
 * does, and why a pale pink cover still gives a screen you can read. The bloom is the same hue lifted, not
 * white, or it would read as a lens flare rather than as light on a wall.
 */
private class FullPlayerPalette(val base: Color, val bloom: Color, val bright: Color, val dim: Color)

@Composable
private fun rememberFullPlayerPalette(): FullPlayerPalette {
    val seedArgb = com.alananasss.kittytune.ui.theme.ThemeState.coverSeedColor
    val fallback = MaterialTheme.colorScheme.primary
    val seed = if (seedArgb != null) Color(seedArgb) else fallback

    // Animated, so a track change travels to the next record's colour instead of cutting to it — the
    // palette elsewhere in the app already does this, and a full screen cutting would be worse.
    val base by animateColorAsState(deepen(seed), tween(COLOUR_TRAVEL_MS), label = "fullPlayerBase")
    val bloom by animateColorAsState(lift(seed), tween(COLOUR_TRAVEL_MS), label = "fullPlayerBloom")

    return FullPlayerPalette(
        base = base,
        bloom = bloom,
        // White rather than a tint: on a saturated ground, tinted text reads as faded rather than as
        // written, and the reference is plainly white at two opacities.
        bright = Color.White.copy(alpha = 0.94f),
        dim = Color.White.copy(alpha = 0.34f),
    )
}

/** Long enough to be a transition and short enough to be over before the next line is sung. */
private const val COLOUR_TRAVEL_MS = 500

/**
 * Towards a dark, saturated version of a colour.
 *
 * Multiplying the channels keeps the hue and drops the luminance, which is what makes a pastel cover
 * produce a deep ground rather than a pale one nothing can be read on.
 */
private fun deepen(color: Color): Color = Color(
    red = color.red * 0.52f,
    green = color.green * 0.52f,
    blue = color.blue * 0.52f,
    alpha = 1f,
)

/** The same hue with some white mixed in, for the bloom. */
private fun lift(color: Color): Color = Color(
    red = color.red * 0.62f + 0.30f,
    green = color.green * 0.62f + 0.30f,
    blue = color.blue * 0.62f + 0.30f,
    alpha = 0.55f,
)

/**
 * The panel's lyrics renderer, handed a theme that says "white, on this record's colour, four times
 * bigger".
 *
 * The alternative was a third copy of the following logic. The sidebar spent three rounds of this issue
 * proving where that ends: two implementations of one thing, disagreeing in ways nobody can fix from the
 * outside. This keeps one renderer and moves the appearance into the caller, where it belongs.
 */
@Composable
private fun LyricsOnCoverColour(viewModel: PlayerViewModel, palette: FullPlayerPalette) {
    val scheme = MaterialTheme.colorScheme.copy(
        onSurface = palette.bright,
        onSurfaceVariant = palette.dim,
        surface = Color.Transparent,
        surfaceContainerLow = Color.Transparent,
    )
    val type = MaterialTheme.typography.let { t ->
        t.copy(
            // What `PanelLyrics` sets a synced line in, and what it sets untimed text in.
            titleMedium = t.displaySmall.copy(fontSize = LYRIC_SIZE, lineHeight = LYRIC_LINE_HEIGHT),
            bodyMedium = t.headlineSmall,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = type, shapes = MaterialTheme.shapes) {
        PanelLyrics(viewModel, Modifier.fillMaxSize())
    }
}

private val LYRIC_SIZE = 34.sp
private val LYRIC_LINE_HEIGHT = 44.sp

/**
 * The cover, and under it the only controls the reference shows.
 *
 * Sized against the room it has: the square takes the smaller of the width it is given and half the
 * height, capped, so it stays about a third of the window and keeps its air instead of growing to fill
 * whatever it is put in. That cap is the difference between this and the first attempt.
 */
@Composable
private fun CoverColumn(
    viewModel: PlayerViewModel,
    palette: FullPlayerPalette,
    showText: Boolean,
    onToggleText: () -> Unit,
) {
    val track = viewModel.currentTrack ?: return

    BoxWithConstraints {
        val side = min(min(maxWidth, maxHeight * 0.56f), COVER_MAX)

        Column(horizontalAlignment = Alignment.Start) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(side)
                    .aspectRatio(1f)
                    // A real shadow, because in the reference the sleeve sits above the wall rather than
                    // being printed on it. It is most of what makes that screen feel like an object.
                    .shadow(28.dp, RoundedCornerShape(14.dp), clip = false)
                    .clip(RoundedCornerShape(14.dp)),
            )

            Spacer(Modifier.height(28.dp))
            Box(Modifier.width(side)) {
                FullPlayerControls(
                    viewModel = viewModel,
                    palette = palette,
                    showText = showText,
                    onToggleText = onToggleText,
                )
            }
        }
    }
}

/**
 * Wide enough to be the subject on a laptop, small enough that a 4K window does not turn it into a poster.
 */
private val COVER_MAX = 420.dp

/**
 * The progress bar and the transport, as quiet as the reference has them.
 *
 * Nothing here is a filled button. That is the single biggest difference from the first attempt, which put
 * a 64 dp primary-coloured play button under the cover: on a screen whose purpose is reading along, the
 * loudest thing must not be a control. Apple's has a hairline bar, the two times at its ends, and five
 * small glyphs — and the remaining time counts down with a minus in front of it, which is worth copying
 * because it answers "how long left" without arithmetic.
 */
@Composable
private fun FullPlayerControls(
    viewModel: PlayerViewModel,
    palette: FullPlayerPalette,
    showText: Boolean,
    onToggleText: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        FullPlayerSeekBar(viewModel, palette)

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietButton(
                icon = Icons.Rounded.MoreHoriz,
                label = str("btn_more"),
                tint = palette.dim,
                onClick = { viewModel.currentTrack?.let { viewModel.showTrackOptions(it, fromPlayer = true) } },
            )

            // The transport keeps the middle of the cover's width whatever sits either side of it, which is
            // why these are weighted spacers rather than an even distribution: in the reference the pause
            // button is centred under the sleeve, not centred between its two neighbours.
            Spacer(Modifier.weight(1f))
            QuietButton(
                icon = Icons.Rounded.SkipPrevious,
                label = str("player_previous"),
                tint = palette.bright,
                size = 26.dp,
                onClick = { viewModel.smartPrevious() },
            )
            Spacer(Modifier.width(14.dp))
            QuietButton(
                icon = if (viewModel.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                label = str("player_play_pause"),
                tint = palette.bright,
                size = 30.dp,
                onClick = { viewModel.togglePlayPause() },
            )
            Spacer(Modifier.width(14.dp))
            QuietButton(
                icon = Icons.Rounded.SkipNext,
                label = str("player_next"),
                tint = palette.bright,
                size = 26.dp,
                onClick = { viewModel.playNext() },
            )
            Spacer(Modifier.weight(1f))

            QuietButton(
                icon = Icons.Rounded.Lyrics,
                label = str("player_lyrics"),
                tint = if (showText) palette.bright else palette.dim,
                onClick = onToggleText,
            )
        }
    }
}

/** One glyph, no container, no ripple worth noticing. */
@Composable
private fun QuietButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(size + 18.dp)) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * A hairline bar with the elapsed time at one end and the time left at the other.
 *
 * Drawn rather than assembled from [androidx.compose.material3.Slider], which on this screen would be
 * wrong in three ways at once: its track is several times too thick, its thumb is a filled pill in the
 * primary colour, and it insists on a 48 dp touch height that would push the transport away from the
 * cover. What the reference has is 4 dp of rounded track and a dot, so that is what this draws.
 *
 * The scrub position is local while a drag is in progress, because the playhead reports four times a
 * second and a bar bound straight to it fights the finger.
 */
@Composable
private fun FullPlayerSeekBar(viewModel: PlayerViewModel, palette: FullPlayerPalette) {
    val duration = viewModel.duration.coerceAtLeast(1L)
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    val playedFraction =
        if (scrubbing) scrubFraction
        else (viewModel.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    val shown = (playedFraction * duration).toLong()

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            scrubbing = true
                            scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            viewModel.seekTo((scrubFraction * duration).toLong())
                            scrubbing = false
                        },
                        onDragCancel = { scrubbing = false },
                        onHorizontalDrag = { change, _ ->
                            scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        },
                    )
                }
                .pointerInput(duration) {
                    detectTapGestures { offset ->
                        viewModel.seekTo(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                    }
                }
                .drawBehind {
                    val track = 4.dp.toPx()
                    val y = size.height / 2f
                    val radius = track / 2f
                    drawRoundRect(
                        color = palette.dim.copy(alpha = 0.22f),
                        topLeft = Offset(0f, y - radius),
                        size = androidx.compose.ui.geometry.Size(size.width, track),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    )
                    val played = size.width * playedFraction
                    if (played > 0f) {
                        drawRoundRect(
                            color = palette.dim.copy(alpha = 0.8f),
                            topLeft = Offset(0f, y - radius),
                            size = androidx.compose.ui.geometry.Size(played, track),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                        )
                    }
                    drawCircle(color = palette.bright, radius = track, center = Offset(played, y))
                }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TimeLabel(com.alananasss.kittytune.utils.makeTimeString(shown), palette)
            // Counting down, with the minus the reference shows: "how much is left" without arithmetic.
            TimeLabel("-" + com.alananasss.kittytune.utils.makeTimeString(duration - shown), palette)
        }
    }
}

@Composable
private fun TimeLabel(text: String, palette: FullPlayerPalette) {
    androidx.compose.material3.Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = palette.dim,
    )
}

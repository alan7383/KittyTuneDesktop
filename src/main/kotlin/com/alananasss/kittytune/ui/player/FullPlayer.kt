package com.alananasss.kittytune.ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
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
import com.alananasss.kittytune.ui.utils.fadingEdge

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
            isFullScreen = true,
            onDismiss = { showQuickSettings = false },
        )
    }

    // Escape and the mouse's back button leave, through the app's own back stack so this takes precedence
    // over whatever registered before it and gives way to a dialog opened on top. A full-window view whose
    // only exit is a dim glyph in a corner is a trap, and being trapped in a view is the complaint that
    // produced the search-field fix a few commits ago (issue #33).
    com.alananasss.kittytune.core.BackHandler(onBack = onExitFullScreen)

    // And the window itself goes full screen, rather than this covering it. Tied to being composed rather
    // than to the flag, so every way out of here — the button, Escape, the mouse, or the track ending and
    // this leaving on its own — gives the window back without any of them having to remember to.
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.alananasss.kittytune.core.AppWindowState.fullScreen = true
        onDispose { com.alananasss.kittytune.core.AppWindowState.fullScreen = false }
    }

    val palette = rememberFullPlayerPalette()
    val drift = rememberMeshDrift()

    // How much of the row the words have, animated rather than switched.
    //
    // `weight` reserves its share whatever the child is doing, so an AnimatedVisibility that shrank its
    // content still held 1.3 shares of the row until it was removed — and then the cover jumped to the middle
    // in one frame: "quand on enlève les lyrics la cover elle va au milieu sans animations". Animating the
    // weight itself is what makes the two halves trade width instead (issue #33).
    val lyricsShare by animateFloatAsState(
        targetValue = if (showText) LYRICS_SHARE else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "lyricsShare",
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(palette.base)
            .drawBehind { drawMesh(palette, drift) }
            // Nothing behind this is reachable while it is up. "Quand on survole la souris en plein écran, les
            // trucs sont sélectionnables derrière le pop up, donc le truc de volume on peut y accéder" — the
            // player bar and the panels are still laid out underneath, and a Box hit-tests every child it
            // covers rather than stopping at the top one.
            //
            // Consumed on the Final pass, which is the only place this works: Initial runs parent-before-child
            // and would swallow this screen's own controls, while by Final anything of ours has had its turn
            // and everything left is on its way to something underneath.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                            .changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        val totalWidth = maxWidth
        val fullLyricsWidth = totalWidth * (LYRICS_SHARE / (1f + LYRICS_SHARE))
        val progress = (lyricsShare / LYRICS_SHARE).coerceIn(0f, 1f)
        val coverStartPadding = 24.dp + 32.dp * progress

        Row(
            // No padding on this Row, and the two halves inset themselves. The lyrics half has to reach the
            // window's own edge so that its scrollbar sits against it — "met la barre de slide tout à droite"
            // — and a Row-level inset would hold it 56 dp short of that (issue #33).
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = coverStartPadding, end = 24.dp, top = 40.dp, bottom = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CoverColumn(
                    viewModel = viewModel,
                    palette = palette,
                    // How much room the cover has to itself, which is what decides how large it gets: the
                    // sleeve grows into the space the words leave rather than sliding across it.
                    roomToItself = 1f - progress,
                    showText = showText,
                    onToggleText = { showText = !showText },
                )
            }

            // Kept out of the row entirely once it has no width, since `weight` refuses zero — and there is
            // nothing left to draw at that point anyway.
            if (lyricsShare > 0.001f) {
                val alpha = if (showText) progress else (progress * 1.4f - 0.4f).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(lyricsShare)
                        .fillMaxHeight()
                        .clipToBounds()
                        .graphicsLayer {
                            this.alpha = alpha
                            this.translationX = (1f - progress) * 40.dp.toPx()
                        },
                ) {
                    Box(
                        Modifier
                            .requiredWidth(fullLyricsWidth)
                            .fillMaxHeight()
                            .padding(vertical = 24.dp)
                    ) {
                        LyricsOnCoverColour(viewModel, palette)
                    }
                }
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
private class FullPlayerPalette(
    val base: Color,
    /** Four soft lights of the record's own hue family, for [drawMesh]. */
    val mesh: List<Color>,
    val bright: Color,
    val dim: Color,
)

/**
 * The background: several huge soft lights of the record's colour, drifting.
 *
 * "Refais aussi le fond car un fond simple comme ça c'est pas ouf, refais-le entièrement, faut que ça claque
 * mais lisible."
 *
 * Three attempts. A flat colour with one bloom in the corner was "pas ouf". Four tints of the single dominant
 * colour, rotated a few degrees apart, was better on a colourful sleeve and produced a flat dark grey
 * rectangle on a nearly black one — which is the screenshot that prompted this, and the reason is that a
 * palette faked from one colour is still one colour.
 *
 * So the lights are the sleeve's own *shades* now — "le fond est animé sur différentes nuances de la cover" —
 * read from the same histogram the theme's seed comes from and keeping the brightness they had there, which is
 * why one corner of this screen can be nearly cream while another is nearly black. See
 * [ArtworkPalette.meshPalette] for what is held back and why. Four of them move; the fifth and darkest is the
 * ground they move over.
 *
 * "Faut que ça claque mais lisible" is the constraint that sets every number here. Claquer is the travel —
 * a light crosses forty per cent of the screen, so a corner that was cream becomes deep red inside half a
 * minute. Lisible is the radius: wide enough that no light has an edge sharp enough to catch a line of text
 * on, because a hard boundary behind a word is what makes a pretty background unreadable.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMesh(
    palette: FullPlayerPalette,
    drift: Float,
) {
    val w = size.width
    val h = size.height
    // Where each light starts, how fast it goes round, and where in its circuit it begins. The rates are
    // deliberately not multiples of each other: shared or harmonic rates make four lights read as one shape
    // pulsing, which is the thing that gives a mesh away as an animation rather than as weather.
    val lights = listOf(
        Blob(baseX = 0.78f, baseY = 0.12f, rate = 1.00f, phase = 0.00f),
        Blob(baseX = 0.20f, baseY = 0.74f, rate = 0.71f, phase = 0.31f),
        Blob(baseX = 0.92f, baseY = 0.66f, rate = 1.29f, phase = 0.63f),
        Blob(baseX = 0.10f, baseY = 0.22f, rate = 0.53f, phase = 0.86f),
    )
    lights.forEachIndexed { index, blob ->
        val angle = ((drift * blob.rate + blob.phase) * 2f * Math.PI).toFloat()
        // Different multipliers on the two axes, so a light travels an ellipse rather than a circle and the
        // whole field never returns to a shape you recognise from a moment ago.
        val x = w * (blob.baseX + WANDER * kotlin.math.cos(angle))
        val y = h * (blob.baseY + WANDER * 0.72f * kotlin.math.sin(angle * 1.3f))
        drawRect(
            Brush.radialGradient(
                colors = listOf(palette.mesh[index % palette.mesh.size], Color.Transparent),
                center = Offset(x, y),
                radius = size.minDimension * BLOB_RADIUS,
            )
        )
    }
}

/** One light in the mesh: where it lives, how fast it circles, and where in that circle it starts. */
private data class Blob(val baseX: Float, val baseY: Float, val rate: Float, val phase: Float)

/**
 * How far a light strays from where it started, as a fraction of the screen.
 *
 * Was a tenth, which is why the background barely moved: "je veux vraiment que ça ressemble à la vidéo, le
 * fond est animé genre un truc de ouf". In the reference the whole character of the screen changes inside
 * twenty seconds — a corner that was cream becomes deep red — and that takes lights crossing a real distance,
 * not shifting by a tenth of one (issue #33).
 */
private const val WANDER = 0.40f

/**
 * How wide a light is, as a fraction of the screen's shorter side.
 *
 * Smaller than it was, because bigger travel needs it: four lights this size at the old radius overlapped
 * everywhere and averaged into one flat colour. Still large enough that no light has an edge sharp enough to
 * catch a line of text on, which is the readability half of "faut que ça claque mais lisible".
 */
private const val BLOB_RADIUS = 0.80f

/**
 * The clock every light circles on, at its own rate.
 *
 * Twenty-six seconds for one turn of the slowest, which is about what the reference does: long enough that
 * you never catch a light moving, short enough that the screen you are looking at is not the screen you
 * looked at a minute ago.
 */
@Composable
private fun rememberMeshDrift(): Float {
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    elapsedSeconds += dt
                }
                lastNanos = now
            }
        }
    }
    return elapsedSeconds / (MESH_CYCLE_MS / 1000f)
}

private const val MESH_CYCLE_MS = 26_000f

@Composable
private fun rememberFullPlayerPalette(): FullPlayerPalette {
    val cover = com.alananasss.kittytune.ui.theme.ThemeState.coverMeshColors
    val fallbackSeed = com.alananasss.kittytune.ui.theme.ThemeState.coverSeedColor
        ?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primary

    // The sleeve's own colours when they have been read, and a spread derived from the one seed until then, so
    // the first frame after opening is never a flat rectangle waiting for a histogram.
    val target = remember(cover, fallbackSeed) {
        if (cover.size >= 2) cover.map { Color(it) } else spreadFrom(fallbackSeed)
    }

    // Animated per light, so a track change is the whole field travelling to the next record rather than
    // cutting to it. Five, because the reference has about that many and a sixth adds nothing you can see.
    val one by animateColorAsState(target[0], tween(COLOUR_TRAVEL_MS), label = "mesh1")
    val two by animateColorAsState(target.getOrElse(1) { target[0] }, tween(COLOUR_TRAVEL_MS), label = "mesh2")
    val three by animateColorAsState(target.getOrElse(2) { target[0] }, tween(COLOUR_TRAVEL_MS), label = "mesh3")
    val four by animateColorAsState(target.getOrElse(3) { target[0] }, tween(COLOUR_TRAVEL_MS), label = "mesh4")
    val five by animateColorAsState(target.getOrElse(4) { target[0] }, tween(COLOUR_TRAVEL_MS), label = "mesh5")

    return FullPlayerPalette(
        // The ground is the darkest of them, which is what the extractor puts last.
        base = five,
        mesh = listOf(one, two, three, four),
        bright = Color.White.copy(alpha = 0.94f),
        dim = Color.White.copy(alpha = 0.34f),
    )
}

/**
 * Five brightnesses of one colour, for the moment before the cover has been read.
 *
 * Mirrors what the extractor does to a monochrome sleeve, so the fallback and the real thing differ in which
 * colours move rather than in how the screen is built.
 */
private fun spreadFrom(seed: Color): List<Color> {
    val hsb = java.awt.Color.RGBtoHSB(
        (seed.red * 255f).toInt(),
        (seed.green * 255f).toInt(),
        (seed.blue * 255f).toInt(),
        null,
    )
    // Around the seed's own brightness rather than on a fixed ramp, so the stand-in is shades of this colour
    // the way the extractor's answer will be shades of that cover.
    val middle = hsb[2].coerceIn(0.30f, 0.52f)
    return listOf(1.38f, 1.12f, 0.90f, 0.70f, 0.52f).map { factor ->
        Color(java.awt.Color.HSBtoRGB(hsb[0], maxOf(hsb[1], 0.30f), (middle * factor).coerceIn(0.14f, 0.72f)))
    }
}

/** Long enough to be a transition and short enough to be over before the next line is sung. */
private const val COLOUR_TRAVEL_MS = 900

@Composable
private fun LyricsOnCoverColour(viewModel: PlayerViewModel, palette: FullPlayerPalette) {
    // Faded at both ends. "Les lyrics à droite il faut le refaire car y'a pas de fond dégradé en sombre en
    // bas" — without it the lines are cut off mid-letter by the bottom of the window, which is the one detail
    // that makes a column of huge type look like a clipped list rather than like text passing through
    // (issue #33). The screen's own lyrics view has had this from the start; the panel renderer had not,
    // because in a panel the list is short enough not to need it.
    val fade = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.10f to Color.Black,
            0.78f to Color.Black,
            1f to Color.Transparent,
        )
    }

    val scheme = MaterialTheme.colorScheme.copy(
        onSurface = palette.bright,
        onSurfaceVariant = palette.dim,
        surface = Color.Transparent,
        surfaceContainerLow = Color.Transparent,
    )
    // The reader's own size, not a constant. "Quand on augmente/baisse la taille des lyrics, que ça fasse
    // vraiment quelque chose" — it did nothing here, because this view set its own 34 sp and ignored the
    // setting the small screen has always honoured (issue #33).
    val size = viewModel.lyricsFullScreenFontSize.coerceIn(LYRIC_MIN, LYRIC_MAX).sp
    val type = MaterialTheme.typography.let { t ->
        t.copy(
            // What `PanelLyrics` sets a line in, whether or not it carries timings.
            titleMedium = t.displaySmall.copy(fontSize = size, lineHeight = size * LINE_HEIGHT_RATIO),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = type, shapes = MaterialTheme.shapes) {
        PanelLyrics(
            vm = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .fadingEdge(fade),
            // Spacing scaled from the size the reader chose, rather than a setting of its own. The knob was
            // there for one round and taken back out: "enlève le line spacing stp en paramètres". It was
            // asked for because lines ran together, and lines ran together because the gap was fixed while
            // the type was not — so tying it to the type is the fix the setting was standing in for
            // (issue #33).
            style = com.alananasss.kittytune.ui.main.PanelLyricsStyle.FullScreen.copy(
                lineSpacing = (size.value * SPACING_PER_SP).dp,
            ),
        )
    }
}

/**
 * What the lyrics font-size setting is multiplied by here, and the range the result is held to.
 *
 * The setting is calibrated against the lyrics screen, which is the width of the window. This column is a
 * little over half that, so using the number raw would wrap every line in two. The scale keeps the control
 * meaningful — turning it up still turns this up — and the bounds stop either end of the slider producing
 * something unreadable.
 */
private const val LYRIC_MIN = 12f
private const val LYRIC_MAX = 100f

/** Leading, as a multiple of the size, so it follows the type instead of being set once for one size. */
private const val LINE_HEIGHT_RATIO = 1.28f

/**
 * Gap between lines, per sp of type.
 *
 * A third of the size, which holds at both ends of the slider: at 16 sp it is five dp and the lines are close
 * without touching, at 64 sp it is twenty-one and they are separate without drifting apart.
 */
private const val SPACING_PER_SP = 0.34f

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
    /** 0 while the words have their full share of the row, 1 once the cover has it to itself. */
    roomToItself: Float,
    showText: Boolean,
    onToggleText: () -> Unit,
) {
    val track = viewModel.currentTrack ?: return

    BoxWithConstraints {
        // The cap rises as the words leave, so the sleeve grows into the space instead of being re-centred in
        // it. Re-centring alone is what read as a jump even once the widths were animating (issue #33).
        val cap = COVER_MAX + (COVER_MAX_ALONE - COVER_MAX) * roomToItself
        val side = min(min(maxWidth, maxHeight * 0.56f), cap)

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

            Spacer(Modifier.height(22.dp))
            Box(Modifier.width(side)) {
                TrackCredit(viewModel = viewModel, palette = palette)
            }
            Spacer(Modifier.height(14.dp))
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
 * And what it may reach once it is the only thing on screen.
 *
 * Not the whole window: a sleeve at 640 dp on a 4K display is a poster, and the screen is still a player.
 */
private val COVER_MAX_ALONE = 560.dp

/**
 * What is playing, and the two things you do to it from here (issue #33).
 *
 * "Le titre, l'artiste, le like et les trois petits points comme t'as mis, mais façon material 3."
 *
 * The reference puts the title and the credit under the sleeve with two small round buttons opposite them,
 * and this had neither — the screen said what the words were and never what the song was. Material 3 rather
 * than Apple's chrome: the buttons are tonal circles rather than grey pills, sized to Material's own 40 dp,
 * and the type is the scheme's title and body rather than a copy of San Francisco's proportions.
 *
 * The heart is filled when the track is liked, which is the app's own convention everywhere else. The three
 * dots stay where they were, in the transport row, because he asked for them there.
 */
@Composable
private fun TrackCredit(viewModel: PlayerViewModel, palette: FullPlayerPalette) {
    val track = viewModel.currentTrack ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                text = track.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = palette.bright,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            androidx.compose.material3.Text(
                // Artist and album on one line, separated by an em dash, which is how the reference reads and
                // is one line instead of two for something nobody needs two lines of.
                text = listOfNotNull(
                    track.user?.username?.takeIf { it.isNotBlank() },
                    track.publisherMetadata?.albumTitle?.takeIf { it.isNotBlank() },
                ).joinToString(" — "),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.dim,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))
        QuietButton(
            icon = if (viewModel.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            label = str("player_like"),
            tint = if (viewModel.isLiked) MaterialTheme.colorScheme.primary else palette.dim,
            size = 22.dp,
            onClick = { viewModel.toggleLike() },
        )
    }
}

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
    androidx.compose.material3.IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(size + 18.dp),
    ) {
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

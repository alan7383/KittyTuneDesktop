package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Lyrics
import com.alananasss.kittytune.ui.player.lyrics.SearchLyricsDialog
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.blur
import com.alananasss.kittytune.data.local.FullPlayerBgStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import com.alananasss.kittytune.ui.player.cover.AnimatedArtwork
import com.alananasss.kittytune.ui.player.cover.CanvasVideo
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.FullPlayerLayout
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

    val toggleLyricsAction: () -> Unit = { showText = !showText }

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

    if (viewModel.isSearchingLyrics) {
        SearchLyricsDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.isSearchingLyrics = false },
        )
    }

    // Escape and the mouse's back button leave, through the app's own back stack so this takes precedence
    // over whatever registered before it and gives way to a dialog opened on top. A full-window view whose
    // only exit is a dim glyph in a corner is a trap, and being trapped in a view is the complaint that
    // produced the search-field fix a few commits ago (issue #33).
    com.alananasss.kittytune.core.BackHandler(onBack = {
        if (viewModel.isSearchingLyrics) {
            viewModel.isSearchingLyrics = false
        } else {
            onExitFullScreen()
        }
    })

    // And the window itself goes full screen, rather than this covering it. Tied to being composed rather
    // than to the flag, so every way out of here — the button, Escape, the mouse, or the track ending and
    // this leaving on its own — gives the window back without any of them having to remember to.
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.alananasss.kittytune.core.AppWindowState.fullScreen = true
        onDispose { com.alananasss.kittytune.core.AppWindowState.fullScreen = false }
    }

    val palette = rememberFullPlayerPalette()
    val drift = rememberMeshDrift()

    // — Screensaver / Focus mode (Point 22) —
    // Auto standby after inactivity in fullscreen displaying large clock, cover, 1 line of lyrics, and session stats.
    val layout = viewModel.fullPlayerLayout
    val screensaverEnabled = viewModel.fullPlayerScreensaverEnabled
    val screensaverTimeoutMs = (viewModel.fullPlayerScreensaverTimeoutSeconds * 1000L).coerceAtLeast(10_000L)
    var lastActivityMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var screensaverActive by remember { mutableStateOf(false) }

    LaunchedEffect(screensaverEnabled, screensaverTimeoutMs) {
        if (!screensaverEnabled) {
            screensaverActive = false
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.delay(1_000L)
            val idle = System.currentTimeMillis() - lastActivityMs
            if (idle >= screensaverTimeoutMs && !screensaverActive) {
                screensaverActive = true
            }
        }
    }

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
            // Intercept pointer events at Initial pass to reset the inactivity timer when screensaver is not active
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        if (!screensaverActive) {
                            lastActivityMs = System.currentTimeMillis()
                        }
                    }
                }
            }
    ) {
        FullPlayerBackground(
            style = viewModel.fullPlayerBgStyle,
            palette = palette,
            drift = { drift.value },
            artworkUrl = track.fullResArtwork,
            animatedVideoUrl = viewModel.currentAnimatedCoverTallUrl ?: viewModel.currentAnimatedCoverUrl,
            fadeUiEnabled = viewModel.playerPrefs.getAnimatedCoversFadeUiEnabled()
        )

        val isPortrait = maxHeight > maxWidth
        val hasLyrics = viewModel.hasLyrics
        val showPortraitLyrics = isPortrait && hasLyrics && showText

        // The single line suits a tall window as well as a wide one, so it is the one layout a portrait window
        // honours; the others become the stacked portrait layout below (issue #33, round 5).
        if (layout == FullPlayerLayout.COVER_AND_LINE) {
            CoverAndLineLayout(viewModel, palette, showText, toggleLyricsAction)
        } else if (isPortrait) {
            AnimatedContent(
                targetState = showPortraitLyrics,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)).togetherWith(fadeOut(animationSpec = tween(220)))
                },
                label = "portraitCoverLyricsCrossfade",
                modifier = Modifier.fillMaxSize()
            ) { isShowingLyrics ->
                if (isShowingLyrics) {
                    // Portrait mode with lyrics: Cover is HIDDEN, lyrics take full width/height
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 44.dp, bottom = 10.dp, start = 24.dp, end = 24.dp)
                        ) {
                            LyricsOnCoverColour(viewModel, palette)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp, top = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                TrackCredit(viewModel = viewModel, palette = palette)
                                Spacer(Modifier.height(10.dp))
                                FullPlayerControls(
                                    viewModel = viewModel,
                                    palette = palette,
                                    showText = showText,
                                    onToggleText = toggleLyricsAction,
                                )
                            }
                        }
                    }
                } else {
                    // Portrait mode with NO lyrics or user toggled lyrics off: Cover is centered
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CoverColumn(
                            viewModel = viewModel,
                            palette = palette,
                            roomToItself = 1f,
                            showText = showText,
                            onToggleText = toggleLyricsAction,
                        )
                    }
                }
            }
        } else if (layout == FullPlayerLayout.LYRICS_CENTRED) {
            CentredLyricsLayout(viewModel, palette, showText, toggleLyricsAction)
        } else {
            CoverBesideLyrics(
                viewModel = viewModel,
                palette = palette,
                lyricsShare = lyricsShare,
                showText = showText,
                onToggleText = toggleLyricsAction,
                lyricsFirst = layout == FullPlayerLayout.LYRICS_LEFT,
                totalWidth = maxWidth,
            )
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
                icon = Icons.Rounded.Search,
                label = str("lyrics_manual_search"),
                tint = if (viewModel.isSearchingLyrics) palette.bright else palette.dim,
                onClick = { viewModel.isSearchingLyrics = true },
            )
            QuietButton(
                icon = Icons.Rounded.Tune,
                label = str("pref_lyrics_title"),
                tint = if (viewModel.lyricsOffset != 0L) palette.bright else palette.dim,
                onClick = { showQuickSettings = true },
            )
            QuietButton(
                icon = Icons.Rounded.DarkMode,
                label = str("screensaver_focus_mode"),
                tint = palette.dim,
                onClick = {
                    lastActivityMs = System.currentTimeMillis()
                    screensaverActive = true
                },
            )
            QuietButton(
                icon = Icons.Rounded.CloseFullscreen,
                label = str("lyrics_exit_fullscreen"),
                tint = palette.dim,
                onClick = onExitFullScreen,
            )
        }

        // Screensaver overlay — fades in over the full player after idle timeout or manual trigger.
        AnimatedVisibility(
            visible = screensaverActive,
            enter = fadeIn(tween(320, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(220, easing = FastOutLinearInEasing)),
            modifier = Modifier.fillMaxSize(),
        ) {
            ScreensaverOverlay(
                viewModel = viewModel,
                palette = palette,
                onWake = {
                    lastActivityMs = System.currentTimeMillis()
                    screensaverActive = false
                },
            )
        }
    }
}

/**
 * The cover and the words side by side, the words on the right or — mirrored — on the left.
 *
 * The words' share of the row is animated rather than switched, so hiding them lets the sleeve grow into the
 * room they leave instead of jumping to the middle.
 */
@Composable
private fun CoverBesideLyrics(
    viewModel: PlayerViewModel,
    palette: FullPlayerPalette,
    lyricsShare: Float,
    showText: Boolean,
    onToggleText: () -> Unit,
    lyricsFirst: Boolean,
    totalWidth: androidx.compose.ui.unit.Dp,
) {
    val fullLyricsWidth = totalWidth * (LYRICS_SHARE / (1f + LYRICS_SHARE))
    val progress = (lyricsShare / LYRICS_SHARE).coerceIn(0f, 1f)
    // The cover's inset on the window's side grows as the words arrive, so the pair sits as one composition.
    val outerInset = 24.dp + 32.dp * progress

    // No padding on this Row, and the two halves inset themselves: the words have to reach the window's own edge
    // so that their scrollbar sits against it — "met la barre de slide tout à droite" (issue #33).
    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        val cover: @Composable RowScope.() -> Unit = {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        start = if (lyricsFirst) 24.dp else outerInset,
                        end = if (lyricsFirst) outerInset else 24.dp,
                        top = 40.dp,
                        bottom = 40.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CoverColumn(
                    viewModel = viewModel,
                    palette = palette,
                    // How much room the cover has to itself decides how large it gets: the sleeve grows into the
                    // space the words leave rather than sliding across it.
                    roomToItself = 1f - progress,
                    showText = showText,
                    onToggleText = onToggleText,
                )
            }
        }
        // Kept out of the row entirely once it has no width, since `weight` refuses zero.
        val words: @Composable RowScope.() -> Unit = {
            if (lyricsShare > 0.001f) {
                val alpha = if (showText) progress else (progress * 1.4f - 0.4f).coerceIn(0f, 1f)
                val slide = if (lyricsFirst) -1f else 1f
                Box(
                    Modifier
                        .weight(lyricsShare)
                        .fillMaxHeight()
                        .clipToBounds()
                        .graphicsLayer {
                            this.alpha = alpha
                            this.translationX = slide * (1f - progress) * 40.dp.toPx()
                        },
                    contentAlignment = if (lyricsFirst) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Box(
                        Modifier
                            .requiredWidth(fullLyricsWidth)
                            .fillMaxHeight()
                            .padding(
                                start = if (lyricsFirst) 40.dp else 16.dp,
                                end = if (lyricsFirst) 16.dp else 40.dp,
                                top = 24.dp,
                                bottom = 24.dp,
                            )
                    ) {
                        LyricsOnCoverColour(viewModel, palette)
                    }
                }
            }
        }
        if (lyricsFirst) {
            words()
            cover()
        } else {
            cover()
            words()
        }
    }
}

/**
 * The words alone in the middle, the way Apple Music's full-screen lyrics are, with the sleeve shrunk into a bar
 * along the bottom beside the credit and the transport. Hiding the words brings the full cover back.
 */
@Composable
private fun CentredLyricsLayout(
    viewModel: PlayerViewModel,
    palette: FullPlayerPalette,
    showText: Boolean,
    onToggleText: () -> Unit,
) {
    val track = viewModel.currentTrack ?: return
    AnimatedContent(
        targetState = showText && viewModel.hasLyrics,
        transitionSpec = { fadeIn(tween(260)).togetherWith(fadeOut(tween(200))) },
        label = "centredLyrics",
        modifier = Modifier.fillMaxSize(),
    ) { showsWords ->
        if (!showsWords) {
            Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
                CoverColumn(viewModel, palette, roomToItself = 1f, showText = showText, onToggleText = onToggleText)
            }
            return@AnimatedContent
        }
        Column(
            Modifier.fillMaxSize().padding(top = 32.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .widthIn(max = CENTRED_LYRICS_MAX_WIDTH)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                LyricsOnCoverColour(viewModel, palette)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.widthIn(max = CENTRED_LYRICS_MAX_WIDTH).fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnimatedArtwork(
                    artworkUrl = track.fullResArtwork,
                    animatedCoverUrl = viewModel.currentAnimatedCoverUrl,
                    isPlaying = viewModel.isPlaying,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(12.dp, RoundedCornerShape(10.dp), clip = false)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Box(Modifier.weight(1f)) { TrackCredit(viewModel = viewModel, palette = palette) }
                Box(Modifier.weight(1.3f)) {
                    FullPlayerControls(viewModel = viewModel, palette = palette, showText = showText, onToggleText = onToggleText)
                }
            }
        }
    }
}

/** Wide enough for a long line at a large size, narrow enough that the eye does not travel across a 4K screen. */
private val CENTRED_LYRICS_MAX_WIDTH = 1100.dp

/**
 * The cover alone with one line under it — the line being sung, replaced as the next one starts. The lyrics
 * button hides the line and leaves the cover.
 */
@Composable
private fun CoverAndLineLayout(
    viewModel: PlayerViewModel,
    palette: FullPlayerPalette,
    showText: Boolean,
    onToggleText: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
        CoverColumn(
            viewModel = viewModel,
            palette = palette,
            roomToItself = 1f,
            showText = showText,
            onToggleText = onToggleText,
            showCurrentLine = showText && viewModel.lyricsLines.isNotEmpty(),
        )
    }
}

/**
 * Screensaver / Focus Mode overlay (Point 22).
 *
 * Appears automatically after inactivity in full screen (or on demand via the moon icon).
 * Shows a large clock with date, the album art, the current lyric line sung (or track info), and
 * the listening session stats (time listened + track plays). Any mouse movement or tap wakes it immediately.
 */
@Composable
private fun androidx.compose.animation.AnimatedVisibilityScope.ScreensaverOverlay(
    viewModel: PlayerViewModel,
    palette: FullPlayerPalette,
    onWake: () -> Unit,
) {
    val track = viewModel.currentTrack ?: return
    val focusRequester = remember { FocusRequester() }
    val activatedAt = remember { System.currentTimeMillis() }
    var initialPosition by remember { mutableStateOf<Offset?>(null) }
    var hasWoken by remember { mutableStateOf(false) }

    fun triggerWake() {
        if (!hasWoken) {
            hasWoken = true
            onWake()
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Throwable) {}
    }

    // Wake on intentional mouse movement, click/tap, or key press.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent {
                if (System.currentTimeMillis() - activatedAt > 400L) {
                    triggerWake()
                    true
                } else {
                    false
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (!hasWoken) {
                        val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                        val elapsed = System.currentTimeMillis() - activatedAt
                        val currentPos = event.changes.firstOrNull()?.position

                        // Grace period: ignore events for 600ms right after activation
                        // (handles button release, cursor settling, and Compose enter events).
                        if (elapsed < 600L) {
                            if (currentPos != null) {
                                initialPosition = currentPos
                            }
                            continue
                        }

                        // Wake on any mouse button click / tap
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed) {
                            event.changes.forEach { it.consume() }
                            triggerWake()
                            break
                        }

                        // Wake on intentional mouse movement (> 20px)
                        if (currentPos != null) {
                            val startPos = initialPosition
                            if (startPos == null) {
                                initialPosition = currentPos
                            } else {
                                val dx = currentPos.x - startPos.x
                                val dy = currentPos.y - startPos.y
                                if (dx * dx + dy * dy > 400f) {
                                    triggerWake()
                                    break
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(horizontal = 32.dp, vertical = 36.dp)
                .animateEnterExit(
                    enter = fadeIn(tween(320, easing = LinearOutSlowInEasing)) +
                            scaleIn(tween(320, easing = LinearOutSlowInEasing), initialScale = 0.94f),
                    exit = fadeOut(tween(200, easing = FastOutLinearInEasing)) +
                            scaleOut(tween(200, easing = FastOutLinearInEasing), targetScale = 0.94f),
                ),
        ) {
            // ── Grande Horloge ────────────────────────────────────────────────────────
            var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    now = java.time.LocalDateTime.now()
                    kotlinx.coroutines.delay(1_000L)
                }
            }
            val timeText = String.format("%02d:%02d", now.hour, now.minute)
            val dateFormatter = remember {
                java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.getDefault())
            }
            val dateText = remember(now.dayOfYear) {
                now.format(dateFormatter).replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                androidx.compose.material3.Text(
                    text = timeText,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 92.sp,
                        letterSpacing = (-2).sp,
                    ),
                    fontWeight = FontWeight.ExtraLight,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                androidx.compose.material3.Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.70f),
                    textAlign = TextAlign.Center,
                )
            }

            // ── Pochette ──────────────────────────────────────────────────────────────
            AnimatedArtwork(
                artworkUrl = track.fullResArtwork,
                animatedCoverUrl = viewModel.currentAnimatedCoverUrl,
                isPlaying = viewModel.isPlaying,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(210.dp)
                    .shadow(32.dp, RoundedCornerShape(22.dp), clip = false)
                    .clip(RoundedCornerShape(22.dp)),
            )

            // ── 1 Ligne de Texte ──────────────────────────────────────────────────────
            val activeLineText by remember {
                derivedStateOf {
                    val lines = viewModel.lyricsLines
                    if (lines.isEmpty()) return@derivedStateOf null
                    val activeIdx = com.alananasss.kittytune.ui.player.lyrics.LyricsUtils.activeLineIndex(
                        lines, viewModel.currentPosition + viewModel.lyricsOffset
                    )
                    lines.getOrNull(activeIdx)?.text?.takeIf { it.isNotBlank() }
                }
            }

            AnimatedContent(
                targetState = activeLineText,
                transitionSpec = {
                    (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 })
                        .togetherWith(fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it / 2 })
                },
                label = "screensaverLyricLine",
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { text ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    if (text != null) {
                        androidx.compose.material3.Text(
                            text = text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 23.sp,
                                lineHeight = 30.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.Text(
                            text = "${track.title.orEmpty()} — ${track.displayArtist.ifBlank { track.user?.username.orEmpty() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        androidx.compose.material3.Text(
                            text = track.title.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(2.dp))
                        androidx.compose.material3.Text(
                            text = track.displayArtist.ifBlank { track.user?.username.orEmpty() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.70f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // ── Statistiques d'écoute de la session ────────────────────────────────────
            val sessionMs = viewModel.sessionTotalListenMs
            val sessionDuration = formatSessionDuration(sessionMs)
            val sessionPlays = viewModel.effectiveSessionPlays

            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Temps d'écoute
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(horizontalAlignment = Alignment.Start) {
                            androidx.compose.material3.Text(
                                text = sessionDuration,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            androidx.compose.material3.Text(
                                text = str("listening_stats_time_listened"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f),
                            )
                        }
                    }

                    // Séparateur vertical
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(Color.White.copy(alpha = 0.16f))
                    )

                    // Nombre de titres écoutés
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(horizontalAlignment = Alignment.Start) {
                            androidx.compose.material3.Text(
                                text = "$sessionPlays",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            androidx.compose.material3.Text(
                                text = str("listening_stats_unique_tracks"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }

            // Indication pour quitter la veille
            androidx.compose.material3.Text(
                text = str("screensaver_tap_to_wake"),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.40f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun formatSessionDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> str("listening_stats_duration_hr_min", hours, minutes)
        minutes > 0 -> str("listening_stats_duration_min", minutes)
        else -> str("listening_stats_duration_sec", seconds)
    }
}

/** How long the full player must be idle (no pointer events) before the screensaver activates. */
private const val SCREENSAVER_IDLE_MS = 60_000L

/**
 * The line being sung, sliding up as the next one takes its place. Two lines tall whatever it says, so the
 * credit under it does not bob as short and long lines alternate.
 */
@Composable
private fun CurrentLyricLine(viewModel: PlayerViewModel, palette: FullPlayerPalette, modifier: Modifier = Modifier) {
    val line by remember {
        androidx.compose.runtime.derivedStateOf {
            val lines = viewModel.lyricsLines
            lines.getOrNull(
                com.alananasss.kittytune.ui.player.lyrics.LyricsUtils.activeLineIndex(
                    lines, viewModel.currentPosition + viewModel.lyricsOffset
                )
            )?.text.orEmpty()
        }
    }
    AnimatedContent(
        targetState = line,
        transitionSpec = {
            (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 2 })
                .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 })
        },
        label = "currentLyricLine",
        modifier = modifier,
    ) { text ->
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = palette.bright,
            minLines = 2,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
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

@Composable
private fun FullPlayerBackground(
    style: FullPlayerBgStyle,
    palette: FullPlayerPalette,
    drift: () -> Float,
    artworkUrl: String?,
    animatedVideoUrl: String? = null,
    fadeUiEnabled: Boolean = false,
) {
    if (fadeUiEnabled && !animatedVideoUrl.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize().background(palette.base)) {
            CanvasVideo(
                canvasUrl = animatedVideoUrl,
                isPlaying = true,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
                    .graphicsLayer { alpha = 0.55f }
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        }
        return
    }

    when (style) {
        FullPlayerBgStyle.APPLE_MUSIC -> {
            FluidArtworkBackground(artworkUrl = artworkUrl, modifier = Modifier.fillMaxSize()) {
                // The lights are the stand-in, not the effect. A sleeve has to be fetched and decoded before
                // there is anything to twist, and the honest alternative — a flat rectangle for a second or
                // two after the screen opens — is the thing three rounds of the old background were spent
                // removing. This is also what is left on a track with no artwork.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(palette.base)
                        .drawBehind { drawMesh(palette, drift()) }
                )
            }
        }
        FullPlayerBgStyle.BLUR -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(palette.base)
            ) {
                if (!artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(70.dp)
                            .graphicsLayer { alpha = 0.55f }
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.40f),
                                    Color.Black.copy(alpha = 0.70f)
                                )
                            )
                        )
                )
            }
        }
        FullPlayerBgStyle.GRADIENT -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                palette.mesh.firstOrNull() ?: palette.base,
                                palette.base
                            ),
                            center = Offset.Unspecified,
                            radius = Float.POSITIVE_INFINITY
                        )
                    )
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(
                                    palette.mesh.getOrElse(0) { palette.base }.copy(alpha = 0.6f),
                                    palette.base
                                )
                            )
                        )
                    }
            )
        }
        FullPlayerBgStyle.PURE_BLACK -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF080808))
            )
        }
    }
}

/**
 * Several huge soft lights of the record's colour, drifting.
 *
 * No longer the background it was written to be: [FluidArtworkBackground] is, and this stands in for the
 * second or so before the sleeve has been decoded — plus for good on a track that has no artwork, or a
 * machine whose driver refuses the shader. Kept rather than deleted because those cases are exactly the ones
 * a flat rectangle would look broken in, and this is a background that needs nothing but four colours.
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
private fun rememberMeshDrift(): androidx.compose.runtime.State<Float> {
    val drift = remember { mutableFloatStateOf(0f) }
    val isSeen = com.alananasss.kittytune.core.LocalWindowSeen.current
    // Returned as state and read while drawing, and paced by `delay`: it used to be read at the top of
    // the screen and advanced by awaiting every frame, so the whole full player recomposed at the display
    // rate for lights that take twenty-six seconds to go round once.
    LaunchedEffect(isSeen) {
        if (!isSeen) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            kotlinx.coroutines.delay(MESH_TICK_MS)
            val now = System.nanoTime()
            val seconds = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now
            drift.floatValue += seconds / (MESH_CYCLE_MS / 1000f)
        }
    }
    return drift
}

/** At this pace nothing visibly moves between two ticks. */
private const val MESH_TICK_MS = 33L

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
    /** The line being sung, between the sleeve and the credit — the single-line layout. */
    showCurrentLine: Boolean = false,
) {
    val track = viewModel.currentTrack ?: return

    BoxWithConstraints {
        // The cap rises as the words leave, and incorporates user cover zoom factor
        val coverScale = viewModel.fullPlayerCoverScale
        val cap = (COVER_MAX + (COVER_MAX_ALONE - COVER_MAX) * roomToItself) * coverScale
        val lineRoom = if (showCurrentLine) CURRENT_LINE_ROOM else 0.dp
        val maxCoverHeight = maxOf(maxHeight - 210.dp - lineRoom, maxHeight * 0.5f)
        val side = min(min(maxWidth, maxCoverHeight), cap)
        val controlsWidth = maxOf(side, min(maxWidth, 400.dp))

        Column(horizontalAlignment = Alignment.Start) {
            // One glow, three layers, in the order they have to stack: the halo behind the sleeve so
            // only its spill shows, the sleeve itself, then the travelling band clipped to the
            // sleeve's corners. The wrapper is exactly the cover's width so the layout below is
            // unaffected; the halo is free to overflow it.
            val automixDebug by viewModel.automixDebugInfo.collectAsState()
            val mixGlow = com.alananasss.kittytune.ui.player.cover.rememberMixGlow(
                outgoingBpm = automixDebug?.outBpm ?: 0f,
            )
            val glowColors = remember(palette.mesh) { palette.mesh }

            Box(Modifier.width(side)) {
                com.alananasss.kittytune.ui.player.cover.MixHalo(
                    glow = mixGlow,
                    colors = glowColors,
                    modifier = Modifier
                        // Centred on the cover, so the spill is even on every side.
                        .align(Alignment.Center)
                        .size(side + com.alananasss.kittytune.ui.player.cover.MIX_HALO_MARGIN * 2),
                )

                AnimatedArtwork(
                    artworkUrl = track.fullResArtwork,
                    animatedCoverUrl = viewModel.currentAnimatedCoverUrl,
                    isPlaying = viewModel.isPlaying,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(side)
                        .aspectRatio(1f)
                        // A real shadow, because in the reference the sleeve sits above the wall rather than
                        // being printed on it. It is most of what makes that screen feel like an object.
                        .shadow(28.dp, RoundedCornerShape(14.dp), clip = false)
                        .clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    if (scrollDelta != 0f) {
                                        val next = (viewModel.fullPlayerCoverScale - scrollDelta * 0.04f).coerceIn(0.6f, 1.4f)
                                        viewModel.updateFullPlayerCoverScale(next)
                                    }
                                }
                            }
                        },
                )

                com.alananasss.kittytune.ui.player.cover.MixSweep(
                    glow = mixGlow,
                    colors = glowColors,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(14.dp)),
                )
            }

            if (showCurrentLine) {
                Spacer(Modifier.height(20.dp))
                CurrentLyricLine(viewModel, palette, Modifier.width(controlsWidth))
            }
            Spacer(Modifier.height(18.dp))
            Box(Modifier.width(controlsWidth)) {
                TrackCredit(viewModel = viewModel, palette = palette)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(controlsWidth)) {
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
private val COVER_MAX = 480.dp

/** Height the single-line layout takes from the cover for its two lines of text and their gap. */
private val CURRENT_LINE_ROOM = 90.dp

/**
 * And what it may reach once it is the only thing on screen.
 *
 * Not the whole window: a sleeve at 640 dp on a 4K display is a poster, and the screen is still a player.
 */
private val COVER_MAX_ALONE = 620.dp

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
                    track.displayArtist.takeIf { it.isNotBlank() } ?: track.user?.username?.takeIf { it.isNotBlank() },
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
        if (viewModel.isYourMixActive) {
            Spacer(Modifier.width(6.dp))
            QuietButton(
                icon = Icons.Outlined.HeartBroken,
                label = str("mix_dislike"),
                tint = palette.dim,
                size = 22.dp,
                onClick = { viewModel.dislikeCurrentTrackInMix() },
            )
        }
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
        com.alananasss.kittytune.ui.player.automix.AutomixDebugOverlay(
            currentPositionMs = { viewModel.currentPosition },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
        )

        FullPlayerSeekBar(viewModel, palette)

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(6.dp))
        FullPlayerVolumeBar(viewModel = viewModel, palette = palette)
    }
}

/**
 * The full player's volume: the same styled track as the player bar's — plain, slim, wavy or squiggly, with
 * the dot that jumps when grabbed and the wave that moves only while music plays — in the artwork's colours.
 * The wheel works anywhere on the row.
 */
@Composable
private fun FullPlayerVolumeBar(
    viewModel: PlayerViewModel,
    palette: FullPlayerPalette,
) {
    val volume = viewModel.volume.coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val scrollDelta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (scrollDelta != 0f) {
                            viewModel.updateVolume((viewModel.volume - scrollDelta * 0.05f).coerceIn(0f, 1f))
                            viewModel.persistVolumeSoon()
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QuietButton(
            icon = com.alananasss.kittytune.ui.main.volumeIcon(volume),
            label = str("volume_title"),
            tint = palette.dim,
            size = 18.dp,
            onClick = { viewModel.toggleMute() }
        )
        com.alananasss.kittytune.ui.main.StyledVolumeTrack(
            volume = volume,
            isPlaying = viewModel.isPlaying,
            activeColor = palette.bright,
            inactiveColor = palette.dim.copy(alpha = 0.25f),
            onVolumeChange = { viewModel.updateVolume(it) },
            onVolumeChangeFinished = { viewModel.persistVolume() },
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.Text(
            text = com.alananasss.kittytune.ui.main.volumePercentLabel(volume),
            style = MaterialTheme.typography.labelSmall,
            color = palette.dim,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
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

        val showRemaining = rememberFullPlayerShowRemaining()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeLabel(com.alananasss.kittytune.utils.makeTimeString(shown), palette)
            com.alananasss.kittytune.ui.player.automix.AutomixBadge(textColor = palette.bright)
            // Counting down or total duration, switchable by clicking and synced with settings.
            TimeLabel(
                text = if (showRemaining) "-" + com.alananasss.kittytune.utils.makeTimeString((duration - shown).coerceAtLeast(0L)) else com.alananasss.kittytune.utils.makeTimeString(duration),
                palette = palette,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        com.alananasss.kittytune.data.local.PlayerPreferences().setShowRemainingTime(!showRemaining)
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun TimeLabel(text: String, palette: FullPlayerPalette, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = palette.dim,
        modifier = modifier,
    )
}

/**
 * Reactive read of the "show remaining time" setting; recomposes when the pref changes.
 */
@Composable
private fun rememberFullPlayerShowRemaining(): Boolean {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) {
        com.alananasss.kittytune.data.local.PlayerPreferences().getShowRemainingTime()
    }
}

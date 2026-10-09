@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
    package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton

import androidx.compose.material3.ButtonDefaults
    
    import androidx.compose.animation.*
    import androidx.compose.animation.core.animateFloatAsState
    import androidx.compose.animation.core.tween
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.hoverable
    import androidx.compose.foundation.interaction.MutableInteractionSource
    import androidx.compose.foundation.interaction.collectIsHoveredAsState
    import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.min
    import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
    import androidx.compose.foundation.lazy.LazyRow
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.foundation.lazy.rememberLazyListState
    import androidx.compose.foundation.gestures.scrollBy
    import androidx.compose.ui.input.pointer.PointerEventType
    import androidx.compose.ui.input.pointer.pointerInput
    import androidx.compose.ui.platform.LocalDensity
    import androidx.compose.material.icons.rounded.Check
    import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
    import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.KeyboardActions
    import androidx.compose.foundation.text.KeyboardOptions
    import androidx.compose.foundation.verticalScroll
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.Close
    import androidx.compose.material.icons.rounded.Notes
    import androidx.compose.material.icons.rounded.FilterCenterFocus
    import androidx.compose.material.icons.rounded.Add
    import androidx.compose.material.icons.rounded.ArrowDropDown
    import androidx.compose.material.icons.rounded.ContentCopy
    import androidx.compose.material.icons.rounded.Remove
    import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.OpenInFull
    import androidx.compose.material.icons.rounded.Settings
    import androidx.compose.material.icons.rounded.Timer
    import androidx.compose.material.icons.rounded.Tune
    import com.alananasss.kittytune.core.EscapableAlertDialog
    import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
    import androidx.compose.runtime.*
    import kotlinx.coroutines.isActive
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.alpha
    import androidx.compose.ui.draw.blur
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.draw.drawWithContent
    import androidx.compose.ui.draw.drawBehind
    import androidx.compose.ui.draw.scale
    import androidx.compose.ui.graphics.BlendMode
    import androidx.compose.ui.graphics.Brush
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.CompositingStrategy
    import androidx.compose.ui.graphics.graphicsLayer
    import androidx.compose.ui.graphics.drawscope.clipPath
    import androidx.compose.ui.platform.LocalClipboardManager
    import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
    import com.alananasss.kittytune.core.openUrl
    import com.alananasss.kittytune.core.str
    import com.alananasss.kittytune.core.trackTextInput
    import androidx.compose.ui.text.AnnotatedString
    import androidx.compose.ui.text.buildAnnotatedString
    import androidx.compose.ui.text.withStyle
    import androidx.compose.ui.text.SpanStyle
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.input.ImeAction
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.text.style.TextDecoration
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.compose.ui.zIndex
    import com.alananasss.kittytune.data.local.LyricsAlignment
    import com.alananasss.kittytune.data.local.LyricsDisplayStyle

    import com.alananasss.kittytune.data.network.LrcLibResponse
    import androidx.compose.ui.window.DialogProperties
    import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignRight
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.escapeDismisses
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.ui.common.Slider
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GraphicEq
import kotlin.math.roundToInt
    import com.alananasss.kittytune.ui.player.LyricsMode
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.utils.makeTimeString
    import com.alananasss.kittytune.ui.utils.fadingEdge
    import androidx.compose.ui.input.pointer.pointerInput
    import androidx.compose.foundation.gestures.detectTapGestures
    import androidx.compose.foundation.gestures.scrollBy
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.isActive
    import kotlinx.coroutines.launch
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun LyricsScreen(
        viewModel: PlayerViewModel,
        onClose: () -> Unit
    ) {
        val isSearching = viewModel.isSearchingLyrics
        val currentTrack = viewModel.currentTrack
        var showQuickSettingsDialog by remember { mutableStateOf(false) }
        var showUploadYamlDialog by remember { mutableStateOf(false) }

        val hasSynced = viewModel.lyricsLines.any { it.startTime > 0 }
        val hasPlain = !viewModel.rawPlainLyrics.isNullOrBlank()

        if (showQuickSettingsDialog) {
            QuickLyricsSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showQuickSettingsDialog = false }
            )
        }
        
        if (showUploadYamlDialog) {
            UploadYamlDialog(
                viewModel = viewModel,
                onDismiss = { showUploadYamlDialog = false }
            )
        }

        BackHandler {
            if (viewModel.isSearchingLyrics) {
                viewModel.isSearchingLyrics = false
            } else {
                onClose()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow)) {

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    if (!isSearching) {
                        CenterAlignedTopAppBar(
                            title = {
                                if (currentTrack != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        AsyncImage(
                                            model = currentTrack.fullResArtwork,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(horizontalAlignment = Alignment.Start) {
                                            Text(
                                                text = currentTrack.title ?: "",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                ArtistLinkText(
                                                    track = currentTrack,
                                                    onArtistClick = { viewModel.navigateToTrackArtist(it) },
                                                    text = currentTrack.displayArtist.ifBlank { currentTrack.user?.username.orEmpty() },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    hoverColor = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (currentTrack.user?.verified == true) {
                                                    Spacer(Modifier.width(3.dp))
                                                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        str("player_lyrics"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                                    Icon(Icons.Rounded.Close, str("btn_close"), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            },
                            actions = {
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showQuickSettingsDialog = true }) {
                                    Icon(Icons.Rounded.Tune, str("pref_lyrics_title"), tint = MaterialTheme.colorScheme.onSurface)
                                }
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.isSearchingLyrics = true }) {
                                    Icon(Icons.Rounded.Search, str("lyrics_manual_search"), tint = MaterialTheme.colorScheme.onSurface)
                                }
                                // Raises the player over the whole window. This screen is one of three
                                // columns, so the big view cannot live in it — it is an overlay, and this is
                                // the way in (issue #33).
                                IconButton(
                                    shapes = IconButtonDefaults.shapes(),
                                    onClick = { viewModel.isLyricsFullScreen = true },
                                ) {
                                    Icon(
                                        Icons.Rounded.OpenInFull,
                                        str("lyrics_fullscreen"),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                        )
                    }
                }
            ) { innerPadding ->
                BoxWithConstraints(modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)) {

                    if (isSearching) {
                        SearchLyricsView(
                            viewModel = viewModel,
                            onCloseSearch = { viewModel.isSearchingLyrics = false }
                        )
                    } else {
                        if (viewModel.lyricsLines.isEmpty() && viewModel.rawPlainLyrics.isNullOrBlank()) {
                            if (viewModel.isLyricsLoading) {
                                SearchingLyricsState(viewModel)
                            } else {
                                EmptyLyricsState(onManualSearch = { viewModel.isSearchingLyrics = true })
                            }
                        } else {
                            AnimatedContent(
                                targetState = viewModel.lyricsMode,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.95f) togetherWith
                                            fadeOut(animationSpec = tween(300))
                                },
                                label = "LyricsModeTransition",
                                modifier = Modifier.fillMaxSize()
                            ) { mode ->
                                when (mode) {
                                    LyricsMode.SYNCED -> {
                                        when (viewModel.lyricsUiStyle) {
                                            com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED -> {
                                                LyricsEnhanced(
                                                    viewModel = viewModel,
                                                    textColorOverride = null
                                                )
                                            }
                                            com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC -> {
                                                SyncedLyricsView(viewModel)
                                            }
                                        }
                                    }
                                    LyricsMode.PLAIN -> {
                                        PlainLyricsView(viewModel)
                                    }
                                }
                            }

                            if (hasSynced && hasPlain) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 16.dp)
                                        .zIndex(10f)
                                ) {
                                    LyricsModeSelector(
                                        currentMode = viewModel.lyricsMode,
                                        onModeSelected = { viewModel.lyricsMode = it },
                                        hasSynced = hasSynced,
                                        hasPlain = hasPlain
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun LyricsModeSelector(
        currentMode: LyricsMode,
        onModeSelected: (LyricsMode) -> Unit,
        hasSynced: Boolean,
        hasPlain: Boolean,
        modifier: Modifier = Modifier
    ) {
        // Themed rather than hard-coded black and white: the hover effect came from Material and so
        // already followed the palette, which is exactly why the buttons under it looked wrong
        // (issue #33).
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CircleShape,
            modifier = modifier.height(38.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasSynced) {
                    LyricsModeChip(
                        text = str("lyrics_mode_synced"),
                        isSelected = currentMode == LyricsMode.SYNCED,
                        onClick = { onModeSelected(LyricsMode.SYNCED) }
                    )
                }
    
                if (hasPlain) {
                    LyricsModeChip(
                        text = str("lyrics_mode_plain"),
                        isSelected = currentMode == LyricsMode.PLAIN,
                        onClick = { onModeSelected(LyricsMode.PLAIN) },
                        enabled = hasPlain
                    )
                }
            }
        }
    }
    
    @Composable
    fun LyricsModeChip(
        text: String,
        isSelected: Boolean,
        onClick: () -> Unit,
        enabled: Boolean = true
    ) {
        val scheme = MaterialTheme.colorScheme
        val backgroundColor by animateColorAsState(
            targetValue = if (isSelected) scheme.primary else Color.Transparent,
            animationSpec = tween(300),
            label = "bgColor"
        )
        val textColor by animateColorAsState(
            targetValue = when {
                isSelected -> scheme.onPrimary
                enabled -> scheme.onSurfaceVariant
                else -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            animationSpec = tween(300),
            label = "textColor"
        )
    
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
    
    @Composable
    fun SyncedLyricsView(viewModel: PlayerViewModel) {
        val currentPosition = viewModel.currentPosition
        val adjustedPosition = currentPosition + viewModel.lyricsOffset
        val lyrics = viewModel.lyricsLines
        // Built on the line being sung, not on line one: the placement below runs after the first frame, and
        // that frame — the top of the song, mid-fade — was the jolt at every opening (issue #33, round 5).
        val listState = key(viewModel.currentTrack?.id) {
            rememberLazyListState(
                initialFirstVisibleItemIndex = LyricsUtils.activeLineIndex(lyrics, adjustedPosition).coerceAtLeast(0)
            )
        }
        val fontSize = viewModel.lyricsFontSize
        val alignment = when(viewModel.lyricsAlignment) {
            LyricsAlignment.LEFT -> TextAlign.Left
            LyricsAlignment.CENTER -> TextAlign.Center
            LyricsAlignment.RIGHT -> TextAlign.Right
        }

        // Interpolated between the player's four-per-second reports so the word fill moves per frame.
        // Shared with the panel, which needs exactly the same thing for exactly the same reason
        // (issue #33) — see [rememberSmoothPosition] for why the estimate is bounded.
        val smoothDrawPosition = rememberSmoothPosition(
            positionMs = currentPosition,
            isPlaying = viewModel.isPlaying,
            speed = viewModel.effectsState.speed,
        )
    
        val fadeBrush = remember {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.15f to Color.Black,
                0.85f to Color.Black,
                1f to Color.Transparent
            )
        }
    
        val activeIndex = remember(adjustedPosition, lyrics) {
            LyricsUtils.activeLineIndex(lyrics, adjustedPosition)
        }
    
        // Reading along by hand wins for a while; the panel's copy of the lyrics follows the same
        // rule, which is why this lives in one place (issue #33).
        val readingByHand = FollowActiveLine(listState, activeIndex, centred = true, contentKey = lyrics.size to lyrics.firstOrNull()?.startTime)
        val focusIndex = rememberFocusLine(listState, activeIndex, readingByHand)
    
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            val halfHeight = screenHeight / 2
            // Little above the first line: the lyrics start at the top and come down to the middle as the song
            // goes, instead of an empty half of the screen. The bottom keeps half, so the last line reaches the middle.
            val topPadding = 24.dp
    
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = topPadding, bottom = halfHeight),
                modifier = Modifier
                    .fillMaxSize()
                    .revealWhenPlaced(listState, activeIndex, contentKey = lyrics.size to lyrics.firstOrNull()?.startTime)
                    .fadingEdge(fadeBrush),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == activeIndex
    
                    // Three ways to set the current line apart, asked for with screenshots of another
                    // player (issue #33). The decision is shared with the panel now — see
                    // [LyricLineStyling] — because it was made twice and the two copies disagreed about
                    // what the same setting does. Scale falls away with distance rather than in one step,
                    // which is what the request's sketch of "lower / more lower" actually describes.
                    val treatment = LyricLineStyling.treatmentFor(
                        style = viewModel.lyricsDisplayStyle,
                        // Zero for every line until the song reaches the words: with no current line there
                        // is nothing to measure distance from, and shrinking everything would be wrong.
                        distance = if (focusIndex < 0) 0 else index - focusIndex,
                        // Not while reading by hand: the lines being scrolled through are the ones the reader
                        // wants to read, and they were the ones under the heaviest blur (issue #66).
                        blurEnabled = viewModel.lyricsLineBlurEnabled && !readingByHand,
                    )

                    val scale by animateFloatAsState(treatment.scale, tween(400), label = "scale")
                    val alpha by animateFloatAsState(treatment.alpha, tween(400), label = "alpha")
                    val blurRadius by androidx.compose.animation.core.animateDpAsState(
                        treatment.blur, tween(400), label = "blur"
                    )
    
                    val lineInteractionSource = remember { MutableInteractionSource() }
                    val isHovered by lineInteractionSource.collectIsHoveredAsState()
    
    
                    val hzAlignment = when(alignment) {
                        TextAlign.Left -> Alignment.Start
                        TextAlign.Center -> Alignment.CenterHorizontally
                        TextAlign.Right -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }

                    // For duet lines, override alignment per singer (normal style, no bubbles)
                    val isDuetActive = viewModel.isDuetActiveForTrack(viewModel.currentTrack)
                    val effectiveSinger = if (isDuetActive) {
                        line.singer?.takeIf { it != LyricSinger.DEFAULT }
                            ?: when (line.agent?.trim()?.lowercase()) {
                                "v2", "singer2", "2" -> LyricSinger.SINGER_2
                                "v1", "singer1", "1" -> LyricSinger.SINGER_1
                                "both", "group", "all", "v1000", "v2000", "3", "v3" -> LyricSinger.BOTH
                                else -> LyricSinger.DEFAULT
                            }
                    } else {
                        LyricSinger.DEFAULT
                    }

                    val lineTextAlign = when (effectiveSinger) {
                        LyricSinger.SINGER_1 -> TextAlign.Start
                        LyricSinger.SINGER_2 -> TextAlign.End
                        LyricSinger.BOTH -> TextAlign.Center
                        else -> alignment
                    }
                    val lineHzAlignment = when (effectiveSinger) {
                        LyricSinger.SINGER_1 -> Alignment.Start
                        LyricSinger.SINGER_2 -> Alignment.End
                        LyricSinger.BOTH -> Alignment.CenterHorizontally
                        else -> hzAlignment
                    }

                    // --- COLUMN GLOBALE DE LA LIGNE ---
                    Column(
                        horizontalAlignment = lineHzAlignment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .hoverable(lineInteractionSource)
                            // Duet lines are constrained to ~72% width and pushed to their side
                            .padding(
                                start = if (effectiveSinger == LyricSinger.SINGER_2) 100.dp else 24.dp,
                                end = if (effectiveSinger == LyricSinger.SINGER_1) 100.dp else 24.dp
                            )
                            .lyricHoverHighlight(isHovered, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                            // Grown from the side the line is aligned to. Scaled from its centre, a full-width
                            // line grew past both edges by more than its padding, and the second singer's
                            // right-aligned words ran off the screen (issue #66).
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                    pivotFractionX = when (lineHzAlignment) {
                                        Alignment.Start -> 0f
                                        Alignment.End -> 1f
                                        else -> 0.5f
                                    },
                                    pivotFractionY = 0.5f,
                                )
                            }
                            .alpha(alpha)
                            // Only when there is something to blur: the modifier forces the line
                            // into its own layer, which is not worth paying for at 0.dp.
                            .then(
                                if (blurRadius > 0.dp) {
                                    Modifier.blur(blurRadius)
                                } else Modifier
                            )
                            .clickable(interactionSource = lineInteractionSource, indication = null) {
                                // Same sum as the panel's, in one place, and allowed to answer
                                // "nowhere" — see [LyricsUtils.seekTargetFor]. This copy also used to
                                // forget the offset that its own highlight applies.
                                LyricsUtils.seekTargetFor(
                                    line = line,
                                    lyricsOffsetMs = viewModel.lyricsOffsetAtLyricTime(line.startTime),
                                    durationMs = viewModel.duration,
                                )?.let(viewModel::seekTo)
                            }
                    ) {
                        // One renderer for both views. This block existed twice — here and in the
                        // panel — and only this copy ever drew the words, so "highlight word by word"
                        // did nothing at all for anyone reading in the panel (issue #33).
                        val lyricsFontFamily = com.alananasss.kittytune.ui.theme.rememberLyricsFontFamily(viewModel.lyricsFont)
                        val lineFont = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.4).sp,
                            fontFamily = lyricsFontFamily
                        )
                        LyricLineText(
                            line = line,
                            isActive = isActive,
                            // Interpolated, so the fill moves per frame rather than per report.
                            positionMs = smoothDrawPosition + viewModel.lyricsOffset,
                            wordSync = viewModel.isWordSyncEnabled,
                            fillEffect = viewModel.isAppleMusicEffectEnabled,
                            activeStyle = lineFont.copy(fontWeight = FontWeight.ExtraBold),
                            inactiveStyle = lineFont.copy(fontWeight = FontWeight.Bold),
                            activeColor = MaterialTheme.colorScheme.onSurface,
                            inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unsungColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = lineTextAlign,
                        )

                        AnimatedVisibility(
                            visible = viewModel.isRomanizationEnabled && !line.romanization.isNullOrBlank(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Text(
                                text = line.romanization ?: "",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = (fontSize * 0.85f).sp,
                                    lineHeight = (fontSize * 1.2f).sp
                                ),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.9f else 0.4f),
                                textAlign = lineTextAlign,
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = viewModel.isLyricsTranslationEnabled && !line.translation.isNullOrBlank(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Text(
                                text = line.translation ?: "",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSize * 0.70f).sp,
                                    lineHeight = (fontSize * 1.0f).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                textAlign = lineTextAlign,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
    
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                AnimatedContent(
                    targetState = viewModel.showLyricsOffsetControls,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically { height -> height } + fadeIn())
                                .togetherWith(fadeOut(animationSpec = tween(100)))
                        } else {
                            (fadeIn(animationSpec = tween(100, delayMillis = 150)))
                                .togetherWith(slideOutVertically { height -> height } + fadeOut())
                        }
                    },
                    contentAlignment = Alignment.BottomCenter,
                    label = "controls_anim"
                ) { showControls ->
                    if (showControls) {
                        LyricsOffsetControls(
                            offset = viewModel.lyricsOffset,
                            onAdjust = { viewModel.adjustLyricsOffset(it) },
                            onReset = { viewModel.resetLyricsOffset() },
                            onClose = { viewModel.showLyricsOffsetControls = false },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    } else {
                        WrongLyricsButton(onClick = { viewModel.isSearchingLyrics = true })
                    }
                }
            }
        }
    }
    
    @Composable
    fun PlainLyricsView(viewModel: PlayerViewModel) {
        val text = viewModel.rawPlainLyrics ?: str("lyrics_no_data")
        val clipboardManager = LocalClipboardManager.current
        val density = androidx.compose.ui.platform.LocalDensity.current
    
        val fontSize = viewModel.lyricsFontSize
        val alignment = when(viewModel.lyricsAlignment) {
            LyricsAlignment.LEFT -> TextAlign.Left
            LyricsAlignment.CENTER -> TextAlign.Center
            LyricsAlignment.RIGHT -> TextAlign.Right
        }
    
        val lines = remember(text) { text.split("\n") }
    
        val fadeBrush = remember {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.15f to Color.Black,
                0.85f to Color.Black,
                1f to Color.Transparent
            )
        }

        val listState = key(viewModel.currentTrack?.id) { rememberLazyListState() }
        val scrollScope = androidx.compose.runtime.rememberCoroutineScope()
        // Wheel and drag always win: the reader is following the words, and having the view creep
        // out from under them would be worse than no auto-scroll at all. Any manual scroll parks
        // the automatic one for five seconds, and the way back is animated rather than a snap.
        var lastUserScrollMs by remember { mutableStateOf(0L) }

        // Where the text sits is a function of the playback position, not a running total — so it
        // resumes where it was after a restart, keeps its place while nobody is looking, and is
        // already correct on the first frame after this screen opens (issue #33).
        FollowPlainLyrics(
            listState = listState,
            enabled = viewModel.isPlainAutoScrollEnabled,
            speed = viewModel.effectivePlainAutoScrollSpeed,
            lineCount = lines.size,
            positionMs = { viewModel.currentPosition },
            isPlaying = { viewModel.isPlaying },
            playbackSpeed = { viewModel.effectsState.speed },
            lastManualScrollMs = { lastUserScrollMs },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .revealWhenPlaced(listState, activeIndex = -1, contentKey = lines)
                    .fadingEdge(fadeBrush)
                    .lyricsWheel(
                        listState = listState,
                        scope = scrollScope,
                        lines = { viewModel.lyricsWheelLines },
                        onManualScroll = { lastUserScrollMs = System.currentTimeMillis() },
                    ),
                contentPadding = PaddingValues(top = 70.dp, bottom = 180.dp, start = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.4).sp
                        ),
                        // The synced view draws its lines in onSurface, which picks up the cover's
                        // tint; this was pure white, so the two modes did not match (issue #33).
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = alignment,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
    
            FloatingActionButton(onClick = {
                    clipboardManager.setText(AnnotatedString(text))
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(48.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, str("lyrics_copy_text"), modifier = Modifier.size(20.dp))
            }
        }
    }
    
    @Composable
    fun SearchingLyricsState(viewModel: PlayerViewModel) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LyricsSearchingIndicator(com.alananasss.kittytune.ui.theme.rememberLyricsFontFamily(viewModel.lyricsFont))
        }
    }

    @Composable
    fun EmptyLyricsState(onManualSearch: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                str("lyrics_no_data"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(24.dp))
    
            Button(
                onClick = onManualSearch,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = str("lyrics_manual_search"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    
    @Composable
    fun WrongLyricsButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
        Box(modifier = modifier) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    str("lyrics_wrong"),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
    
    /**
     * The lyrics timing panel: how far the words are shifted against the song, nudged in tenths of a second.
     *
     * A title with a way out, the shift large in the middle between the two nudges, and the reset under it — lit
     * only when there is something to reset (issue #33, round 5). It had no close button although it was handed
     * one, and a reset written in English whatever the language.
     */
    @Composable
    fun LyricsOffsetControls(
        offset: Long,
        onAdjust: (Long) -> Unit,
        onReset: () -> Unit,
        onClose: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val scheme = MaterialTheme.colorScheme
        Surface(
            modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            // Themed like the rest of the screen: it floats over the lyrics, so a raised container rather than a
            // black scrim that ignored the palette (issue #33).
            color = scheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = str("lyrics_sync"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = str("btn_close"), tint = scheme.onSurfaceVariant)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RepeatingIconButton(onClick = { onAdjust(-OFFSET_STEP_MS) }, icon = Icons.Rounded.Remove, tint = scheme.onSurface)
                    val shiftColor by androidx.compose.animation.animateColorAsState(
                        if (offset == 0L) scheme.onSurfaceVariant else scheme.primary, label = "lyricsOffsetColor"
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%s%.1f s", if (offset > 0) "+" else "", offset / 1000.0),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = shiftColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    RepeatingIconButton(onClick = { onAdjust(OFFSET_STEP_MS) }, icon = Icons.Rounded.Add, tint = scheme.onSurface)
                }

                TextButton(shapes = ButtonDefaults.shapes(),
                    onClick = onReset,
                    enabled = offset != 0L,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, end = 12.dp),
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str("btn_reset"), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    /**
     * A round button that fires once on press and keeps firing while held.
     *
     * The press used to start the repeat and cancel it on the very next line, without waiting for the release:
     * holding never repeated, and a click whose coroutine had not been dispatched yet did nothing at all. The
     * first step now runs on the press itself and the repeat lives until the finger lifts.
     */
    @Composable
    fun RepeatingIconButton(
        onClick: () -> Unit,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        tint: Color,
        modifier: Modifier = Modifier
    ) {
        val currentOnClick by rememberUpdatedState(onClick)
        val scope = rememberCoroutineScope()
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = modifier
                .size(48.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            currentOnClick()
                            val repeat = scope.launch {
                                delay(REPEAT_START_DELAY_MS)
                                while (isActive) {
                                    currentOnClick()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                            tryAwaitRelease()
                            repeat.cancel()
                        }
                    )
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint)
            }
        }
    }

@Composable
fun UploadYamlDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.width(460.dp).padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = str("dialog_upload_yaml_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, str("btn_close"))
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = str("dialog_upload_yaml_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    text = "Documentation: https://lrclib.net/lyricsfile",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        openUrl("https://lrclib.net/lyricsfile")
                    }
                )

                Spacer(Modifier.height(24.dp))

                androidx.compose.material3.Button(
                    onClick = {
                        val file = com.alananasss.kittytune.core.NativeFileDialog.openFile(
                            str("btn_upload_yaml"),
                            com.alananasss.kittytune.core.NativeFileDialog.FileType("Lyrics", listOf(com.alananasss.kittytune.core.NativeFileDialog.ANY_FILE)),
                        )
                        if (file != null && file.exists()) {
                            viewModel.loadCustomLyrics(file.readText())
                            onDismiss()
                        }
                    },
                    shapes = androidx.compose.material3.ButtonDefaults.shapes(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("btn_upload_yaml"), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/**
 * The hover highlight behind a lyric line: a soft rounded box, a little wider than the line, that fades in
 * and out. The same look as the karaoke view's hover. It replaces a rule drawn under the text, the last of
 * the old hover styles left in the lyrics views (issue #66).
 *
 * Drawn behind the line's own layer, so the line's blur and scale do not smear it.
 */
@Composable
internal fun Modifier.lyricHoverHighlight(hovered: Boolean, color: Color): Modifier {
    val shown by animateFloatAsState(if (hovered) 1f else 0f, tween(HOVER_FADE_MS), label = "lyricHover")
    return drawBehind {
        if (shown <= 0f) return@drawBehind
        val bleed = HOVER_BLEED.toPx()
        drawRoundRect(
            color = color.copy(alpha = color.alpha * shown),
            topLeft = androidx.compose.ui.geometry.Offset(-bleed, 0f),
            size = androidx.compose.ui.geometry.Size(size.width + bleed * 2, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(HOVER_CORNER.toPx()),
        )
    }
}

private const val HOVER_FADE_MS = 150
private val HOVER_BLEED = 8.dp
private val HOVER_CORNER = 14.dp



private const val OFFSET_STEP_MS = 100L
private const val REPEAT_START_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 100L

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
    import androidx.compose.material.icons.rounded.FormatSize
    import androidx.compose.material.icons.rounded.CenterFocusStrong
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
                                                    text = currentTrack.user?.username ?: "",
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
                            EmptyLyricsState(onManualSearch = { viewModel.isSearchingLyrics = true })
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
        val listState = rememberLazyListState()
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
        FollowActiveLine(listState, activeIndex)
    
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            val halfHeight = screenHeight / 2
            val topPadding = halfHeight - 50.dp
    
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = topPadding, bottom = halfHeight),
                modifier = Modifier
                    .fillMaxSize()
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
                        distance = if (activeIndex < 0) 0 else index - activeIndex,
                        blurEnabled = viewModel.lyricsLineBlurEnabled,
                    )

                    val scale by animateFloatAsState(treatment.scale, tween(400), label = "scale")
                    val alpha by animateFloatAsState(treatment.alpha, tween(400), label = "alpha")
                    val blurRadius by androidx.compose.animation.core.animateDpAsState(
                        treatment.blur, tween(400), label = "blur"
                    )
    
                    val lineInteractionSource = remember { MutableInteractionSource() }
                    val isHovered by lineInteractionSource.collectIsHoveredAsState()
    
                    // The hover rule is drawn by [lyricUnderline] rather than set as a
                    // TextDecoration: Skia underlines each font run separately, so a line that
                    // falls back out of the variable font (Cyrillic, Arabic, CJK…) came out as a
                    // broken dashed rule at mismatched thicknesses (issue #33).
                    var hoverLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    
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
                            .scale(scale)
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
                                    lyricsOffsetMs = viewModel.lyricsOffset,
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
                        val ruleColor =
                            if (isActive) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant

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
                            // The hover rule is drawn rather than set as a TextDecoration: Skia underlines
                            // each font run separately, so a line that falls back out of the variable font
                            // (Cyrillic, Arabic, CJK…) came out as a broken dashed rule.
                            textModifier = Modifier.lyricUnderline(
                                { hoverLayout },
                                isHovered,
                                fontSize,
                                ruleColor,
                            ),
                            onTextLayout = { hoverLayout = it },
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

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
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
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SearchLyricsView(
        viewModel: PlayerViewModel,
        onCloseSearch: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val focusManager = LocalFocusManager.current
        var query by remember(viewModel.currentTrack?.id, viewModel.manualSearchQuery) {
            mutableStateOf(viewModel.manualSearchQuery)
        }

        LaunchedEffect(viewModel.currentTrack?.id) {
            if (viewModel.unifiedLyricSearchResults.isEmpty() && query.isNotBlank()) {
                viewModel.searchLyricsManual(query, viewModel.manualSearchProvider)
            }
        }

        // Themed rather than hard-coded black (issue #33): this view covers the whole player,
        // so a flat black panel clashed with both the light theme and the cover-seeded palette.
        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                IconButton(onClick = onCloseSearch, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        Icons.Rounded.Close,
                        str("btn_close"),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .trackTextInput()
                        .escapeDismisses(onCloseSearch),
                    placeholder = {
                        Text(
                            str("lyrics_search_hint"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.searchLyricsManual(query, viewModel.manualSearchProvider)
                        focusManager.clearFocus()
                    })
                )
                IconButton(
                    onClick = {
                        viewModel.searchLyricsManual(query, viewModel.manualSearchProvider)
                        focusManager.clearFocus()
                    },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        str("search_hint"),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // --- SÉLECTEUR DE FOURNISSEUR (MENU DÉROULANT) ---
            val allProviders = remember {
                val userOrder = viewModel.playerPrefs.getLyricsProviderOrder()
                val all = (userOrder + com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider.entries).distinct()
                val (enabled, disabled) = all.partition { viewModel.playerPrefs.getLyricsProviderEnabled(it) }
                enabled + disabled
            }
            val currentProvider = allProviders.firstOrNull { it.name.equals(viewModel.manualSearchProvider, ignoreCase = true) }
            var providerExpanded by remember { mutableStateOf(false) }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedButton(
                    onClick = { providerExpanded = true },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = currentProvider?.displayName ?: "Auto",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                ) {
                    allProviders.forEach { provider ->
                        val isActive = viewModel.manualSearchProvider.equals(provider.name, ignoreCase = true)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = provider.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                providerExpanded = false
                                viewModel.searchLyricsManual(query, provider.name)
                            },
                            leadingIcon = if (isActive) ({
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }) else null
                        )
                    }
                }
            }

            if (viewModel.isLyricsLoading) {
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val searchResults = remember(viewModel.unifiedLyricSearchResults.toList()) {
                viewModel.unifiedLyricSearchResults.toList()
            }

            if (searchResults.isEmpty() && !viewModel.isLyricsLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = str("no_results"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = searchResults, key = { it.id + it.provider }) { result ->
                        Card(
                            onClick = { viewModel.selectUnifiedLyricResult(result) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(result.artistName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!result.albumName.isNullOrEmpty()) {
                                        Text(result.albumName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    if (result.durationSec > 0.0) {
                                        Text(
                                            makeTimeString((result.durationSec * 1000).toLong()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (result.hasLineSync) {
                                            Icon(
                                                Icons.Rounded.Timer,
                                                str("lyrics_badge_line_sync"),
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (result.hasWordSync) {
                                            Icon(
                                                Icons.Rounded.Verified,
                                                str("lyrics_badge_word_sync"),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SearchLyricsDialog(
        viewModel: PlayerViewModel,
        onDismiss: () -> Unit
    ) {
        BackHandler(onBack = onDismiss)
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .escapeDismisses(onDismiss)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.escapeDismisses(onDismiss)
                ) {
                    val panelWidth = min(720.dp, maxWidth * 0.92f)
                    val panelHeight = min(680.dp, maxHeight * 0.88f)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .width(panelWidth)
                            .height(panelHeight)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {} // Stop click from bubbling to dismiss backdrop
                            )
                            .escapeDismisses(onDismiss)
                    ) {
                        SearchLyricsView(
                            viewModel = viewModel,
                            onCloseSearch = onDismiss,
                            modifier = Modifier.escapeDismisses(onDismiss)
                        )
                    }
                }
            }
        }
    }



    @Composable
    fun LyricsOffsetControls(
        offset: Long,
        onAdjust: (Long) -> Unit,
        onReset: () -> Unit,
        onClose: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(), // Padding is managed by the parent
            shape = RoundedCornerShape(24.dp),
            // Themed like the rest of the screen. This panel floats over the lyrics, so it uses a raised
            // container rather than a black scrim that ignored the palette (issue #33).
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = str("lyrics_sync"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
    
                    // Proper formatting: +0.1s, -0.5s, 0.0s
                    val seconds = offset / 1000.0
                    val sign = if (offset > 0) "+" else ""
                    val color = if (offset == 0L) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
    
                    Text(
                        text = String.format(java.util.Locale.US, "%s%.1fs", sign, seconds),
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp) // Small visual alignment
                    )
                }
    
                Spacer(Modifier.height(16.dp))
    
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // MINUS BUTTON (Active repetition)
                    RepeatingIconButton(onClick = { onAdjust(-100L) }, // -0.1s
                        icon = Icons.Rounded.Remove,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
    
                    // RESET BUTTON (Simple click is enough)
                    TextButton(onClick = onReset) {
                        Text(
                            "RESET",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
    
                    // PLUS BUTTON (Active repetition)
                    RepeatingIconButton(onClick = { onAdjust(100L) }, // +0.1s
                        icon = Icons.Rounded.Add,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    
    @Composable
    fun RepeatingIconButton(onClick: () -> Unit,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        tint: Color,
        modifier: Modifier = Modifier
    ) {
        val currentOnClick by rememberUpdatedState(onClick)
        val scope = rememberCoroutineScope()
    
        // We use Surface instead of FilledIconButton to have total control over touch events
        Surface(
            shape = CircleShape, // Round shape like an IconButton
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = modifier
                .size(48.dp) // Standard button size
                .clip(CircleShape) // Important for visual effect and touch
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            // Start coroutine for repetition
                            val job = scope.launch {
                                // 1. Immediate click on touch
                                currentOnClick()
    
                                // 2. Delay before starting repetition (e.g., 400ms)
                                delay(400)
    
                                // 3. Repetition loop while finger is pressed
                                while (isActive) {
                                    currentOnClick()
                                    delay(100) // Repetition speed (0.1s)
                                }
                            }
    
                            // Cancel loop as soon as it's released
                            job.cancel()
                        }
                    )
                }
        ) {
            // Center icon in the Surface
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint)
            }
        }
    }

/**
 * How large the lyrics quick-settings panel is allowed to get.
 *
 * A cap rather than a size: the panel takes 94% of the window up to these, so it grows on a big screen
 * without ever running past the edges of a small one. Wide because the left column carries four-up button
 * groups whose labels have to fit on one line, tall because the alternative to scrolling inside itself was
 * being cut off by the bottom of the window.
 */
private val QUICK_SETTINGS_MAX_WIDTH = 1080.dp
private val QUICK_SETTINGS_MAX_HEIGHT = 940.dp

@Composable
fun QuickLyricsSettingsDialog(
    viewModel: PlayerViewModel,
    isFullScreen: Boolean = false,
    isSidebar: Boolean = false,
    onDismiss: () -> Unit
) {
    val prefs = remember { PlayerPreferences() }
    val defaultFontSize = when {
        isFullScreen -> 42f
        isSidebar -> 22f
        else -> 42f
    }
    val fontSize = when {
        isFullScreen -> viewModel.lyricsFullScreenFontSize
        isSidebar -> viewModel.lyricsSidebarFontSize
        else -> viewModel.lyricsFontSize
    }
    val updateFontSize: (Float) -> Unit = {
        when {
            isFullScreen -> viewModel.updateLyricsFullScreenFontSize(it)
            isSidebar -> viewModel.updateLyricsSidebarFontSize(it)
            else -> viewModel.updateLyricsFontSize(it)
        }
    }
    val uiStyle = when {
        isFullScreen -> viewModel.lyricsFullScreenUiStyle
        isSidebar -> viewModel.lyricsSidebarUiStyle
        else -> viewModel.lyricsUiStyle
    }
    val updateUiStyle: (com.alananasss.kittytune.data.local.LyricsUiStyle) -> Unit = {
        when {
            isFullScreen -> viewModel.updateLyricsFullScreenUiStyle(it)
            isSidebar -> viewModel.updateLyricsSidebarUiStyle(it)
            else -> viewModel.updateLyricsUiStyle(it)
        }
    }
    val alignment = when {
        isFullScreen -> viewModel.lyricsFullScreenAlignment
        isSidebar -> viewModel.lyricsSidebarAlignment
        else -> viewModel.lyricsAlignment
    }
    val updateAlignment: (LyricsAlignment) -> Unit = {
        when {
            isFullScreen -> viewModel.updateLyricsFullScreenAlignment(it)
            isSidebar -> viewModel.updateLyricsSidebarAlignment(it)
            else -> viewModel.updateLyricsAlignment(it)
        }
    }
    val displayStyle = when {
        isFullScreen -> viewModel.lyricsFullScreenDisplayStyle
        isSidebar -> viewModel.lyricsSidebarDisplayStyle
        else -> viewModel.lyricsDisplayStyle
    }
    val updateDisplayStyle: (LyricsDisplayStyle) -> Unit = {
        when {
            isFullScreen -> viewModel.updateLyricsFullScreenDisplayStyle(it)
            isSidebar -> viewModel.updateLyricsSidebarDisplayStyle(it)
            else -> viewModel.updateLyricsDisplayStyle(it)
        }
    }
    val lineSpacing = when {
        isFullScreen -> viewModel.lyricsFullScreenLineSpacing
        isSidebar -> viewModel.lyricsSidebarLineSpacing
        else -> viewModel.lyricsLineSpacing
    }
    val updateLineSpacing: (Float) -> Unit = {
        when {
            isFullScreen -> viewModel.updateLyricsFullScreenLineSpacing(it)
            isSidebar -> viewModel.updateLyricsSidebarLineSpacing(it)
            else -> viewModel.updateLyricsLineSpacing(it)
        }
    }
    val horizontalMargin = when {
        isFullScreen -> viewModel.lyricsFullScreenHorizontalMargin
        isSidebar -> viewModel.lyricsSidebarHorizontalMargin
        else -> viewModel.lyricsHorizontalMargin
    }
    val updateHorizontalMargin: (Float) -> Unit = {
        when {
            isFullScreen -> viewModel.updateLyricsFullScreenHorizontalMargin(it)
            isSidebar -> viewModel.updateLyricsSidebarHorizontalMargin(it)
            else -> viewModel.updateLyricsHorizontalMargin(it)
        }
    }
    val verticalOffset = when {
        isFullScreen -> viewModel.lyricsFullScreenVerticalOffset
        isSidebar -> viewModel.lyricsSidebarVerticalOffset
        else -> viewModel.lyricsVerticalOffset
    }
    val updateVerticalOffset: (Float) -> Unit = {
        when {
            isFullScreen -> viewModel.updateLyricsFullScreenVerticalOffset(it)
            isSidebar -> viewModel.updateLyricsSidebarVerticalOffset(it)
            else -> viewModel.updateLyricsVerticalOffset(it)
        }
    }
    val activeScale = when {
        isFullScreen -> viewModel.lyricsFullScreenActiveScale
        isSidebar -> viewModel.lyricsSidebarActiveScale
        else -> viewModel.lyricsActiveScale
    }
    val updateActiveScale: (Float) -> Unit = {
        when {
            isFullScreen -> viewModel.updateLyricsFullScreenActiveScale(it)
            isSidebar -> viewModel.updateLyricsSidebarActiveScale(it)
            else -> viewModel.updateLyricsActiveScale(it)
        }
    }
    val lineBlurEnabled = when {
        isFullScreen -> viewModel.lyricsFullScreenLineBlurEnabled
        isSidebar -> viewModel.lyricsSidebarLineBlurEnabled
        else -> viewModel.lyricsLineBlurEnabled
    }
    val updateLineBlurEnabled: (Boolean) -> Unit = {
        when {
            isFullScreen -> viewModel.updateLyricsFullScreenLineBlurEnabled(it)
            isSidebar -> viewModel.updateLyricsSidebarLineBlurEnabled(it)
            else -> viewModel.updateLyricsLineBlurEnabled(it)
        }
    }
    var preferLocal by remember { mutableStateOf(prefs.getLyricsPreferLocal()) }
    val currentOffsetMs = viewModel.lyricsOffset
    val currentOffsetSec = currentOffsetMs / 1000f

    var enableTranslation by remember { mutableStateOf(prefs.getLyricsTranslationEnabled()) }
    var targetLang by remember { mutableStateOf(prefs.getLyricsTranslationLang()) }
    var showLangDialog by remember { mutableStateOf(false) }

    if (showLangDialog) {
        val systemLangCode = java.util.Locale.getDefault().language
        val allLanguages = remember {
            val locales = java.util.Locale.getISOLanguages()
                .map { code ->
                    val loc = java.util.Locale(code)
                    code to loc.getDisplayLanguage(loc).replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }
                }
                .filter { it.second.isNotBlank() && it.first.length == 2 }
                .distinctBy { it.first }
                .sortedBy { it.second }

            val list = mutableListOf<Pair<String, String>>()
            val systemLoc = locales.find { it.first == systemLangCode }
            if (systemLoc != null) {
                list.add(systemLoc.first to "${systemLoc.second} (${str("theme_system")})")
            }
            list.addAll(locales.filter { it.first != systemLangCode })
            list
        }

        EscapableAlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(str("pref_lyrics_translation_lang")) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(allLanguages) { (code, name) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { 
                                targetLang = code
                                showLangDialog = false 
                                viewModel.setLyricsTranslationLanguage(code)
                            }.padding(vertical = 12.dp), 
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (targetLang == code), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLangDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Sized against the window rather than fixed at 860 dp. Two columns of cards inside 860 left each
        // segment of a four-up button group about forty dp of text, which is why "Gradient" was breaking
        // across two lines in the middle of the word; and a panel taller than the window was simply cut off
        // at the bottom edge instead of scrolling inside itself. `min` also copes with an unbounded
        // measurement, where the fractions come back infinite and the caps are what is left.
        BoxWithConstraints {
            val panelWidth = min(QUICK_SETTINGS_MAX_WIDTH, maxWidth * 0.94f)
            val panelHeight = min(QUICK_SETTINGS_MAX_HEIGHT, maxHeight * 0.94f)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier.width(panelWidth).heightIn(max = panelHeight).padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()) 
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = str("pref_lyrics_title"),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, str("btn_close"))
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // --- LAYOUT EN 2 COLONNES ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                    
                        // ==========================================
                        // COLONNE GAUCHE (Visuel & Synchro)
                        // ==========================================
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isFullScreen) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = str("full_player_bg_style"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            // Beside the heading rather than beside the Apple Music button,
                                            // because four buttons split this card between them and none of
                                            // them has room for a dot. Only while that style is the one in
                                            // use, since the aside is about that style.
                                            if (viewModel.fullPlayerBgStyle ==
                                                com.alananasss.kittytune.data.local.FullPlayerBgStyle.APPLE_MUSIC
                                            ) {
                                                com.alananasss.kittytune.ui.common.FunFactDot(
                                                    str("full_player_bg_apple_music_fact")
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        ExpressiveConnectedButtonGroup(
                                            fillMaxWidth = true,
                                            // Four segments across one column of the panel: at the button's
                                            // own 24 dp either side there is no room for "Apple Music" on a
                                            // single line.
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                            options = com.alananasss.kittytune.data.local.FullPlayerBgStyle.entries,
                                            selectedOption = viewModel.fullPlayerBgStyle,
                                            onOptionSelected = { viewModel.updateFullPlayerBgStyle(it) },
                                            labelProvider = { style ->
                                                val text = when (style) {
                                                    com.alananasss.kittytune.data.local.FullPlayerBgStyle.APPLE_MUSIC -> str("full_player_bg_apple_music")
                                                    com.alananasss.kittytune.data.local.FullPlayerBgStyle.BLUR -> str("full_player_bg_blur")
                                                    com.alananasss.kittytune.data.local.FullPlayerBgStyle.GRADIENT -> str("full_player_bg_gradient")
                                                    com.alananasss.kittytune.data.local.FullPlayerBgStyle.PURE_BLACK -> str("full_player_bg_pure_black")
                                                }
                                                Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                            }
                                        )

                                        Spacer(Modifier.height(14.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = str("full_player_cover_zoom"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${(viewModel.fullPlayerCoverScale * 100).toInt()}%",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.updateFullPlayerCoverScale(viewModel.fullPlayerCoverScale - 0.05f) }) {
                                                Icon(Icons.Rounded.Remove, null)
                                            }
                                            Slider(
                                                value = viewModel.fullPlayerCoverScale,
                                                onValueChange = { viewModel.updateFullPlayerCoverScale(it) },
                                                valueRange = 0.6f..1.4f,
                                                steps = 15,
                                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                            )
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.updateFullPlayerCoverScale(viewModel.fullPlayerCoverScale + 0.05f) }) {
                                                Icon(Icons.Rounded.Add, null)
                                            }
                                        }
                                    }
                                }
                            }

                            // UI STYLE
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = str("pref_lyrics_ui_style", "Style des paroles"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    ExpressiveConnectedButtonGroup(
                                        fillMaxWidth = true,
                                        options = com.alananasss.kittytune.data.local.LyricsUiStyle.entries,
                                        selectedOption = uiStyle,
                                        onOptionSelected = { updateUiStyle(it) },
                                        labelProvider = { style ->
                                            val text = when (style) {
                                                com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED -> str("pref_lyrics_ui_style_enhanced", "Apple Music")
                                                com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC -> str("pref_lyrics_ui_style_classic", "Classique")
                                            }
                                            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                        }
                                    )
                                    if (uiStyle != com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC) {
                                        Spacer(Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = when {
                                                        isFullScreen -> str("pref_lyrics_fullscreen_line_blur_title", "Flou des lignes en plein écran")
                                                        isSidebar -> str("pref_lyrics_sidebar_line_blur_title", "Lignes inactives floutées (Panneau latéral)")
                                                        else -> str("pref_lyrics_central_line_blur_title", str("pref_lyrics_line_blur_title", "Lignes inactives floutées (Mode central)"))
                                                    },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = str("pref_lyrics_line_blur_desc", "Blur inactive lyric lines progressively"),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Switch(
                                                checked = lineBlurEnabled,
                                                onCheckedChange = { updateLineBlurEnabled(it) }
                                            )
                                        }
                                    }
                                }
                            }

                            // POLICE DES PAROLES
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = str("pref_lyrics_font_title", "Police des paroles"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    val fonts = listOf(
                                        com.alananasss.kittytune.data.local.LyricsFont.APPLE to str("pref_lyrics_font_apple_short", "Apple"),
                                        com.alananasss.kittytune.data.local.LyricsFont.APP_DEFAULT to str("pref_lyrics_font_app_default_short", "Défaut")
                                    )
                                    ExpressiveConnectedButtonGroup(
                                        fillMaxWidth = true,
                                        options = fonts,
                                        selectedOption = fonts.firstOrNull { it.first == viewModel.lyricsFont } ?: fonts.first(),
                                        onOptionSelected = { viewModel.updateLyricsFont(it.first) },
                                        labelProvider = { (_, label) ->
                                            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                        }
                                    )
                                }
                            }

                            // 0. FOURNISSEUR
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = str("pref_lyrics_provider_title"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    ExpressiveConnectedButtonGroup(
                                        fillMaxWidth = true,
                                        options = listOf(com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY, com.alananasss.kittytune.ui.player.LyricsProvider.OPEN_SOURCE),
                                        selectedOption = viewModel.lyricsProvider,
                                        onOptionSelected = { viewModel.updateLyricsProvider(it) },
                                        labelProvider = { prov ->
                                            val text = when (prov) {
                                                com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY -> "Musixmatch"
                                                com.alananasss.kittytune.ui.player.LyricsProvider.OPEN_SOURCE -> "LrcLib"
                                            }
                                            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                        }
                                    )
                                }
                            }

                            // 1. SYNCHRONISATION (SYNC OFFSET)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = str("lyrics_sync"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        val sign = if (currentOffsetMs > 0) "+" else ""
                                        val formattedOffset = String.format("%.2fs", currentOffsetSec)
                                        Text(
                                            text = "$sign$formattedOffset",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (currentOffsetMs != 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = { viewModel.adjustLyricsOffset(-1000L) },
                                            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("-1s", style = MaterialTheme.typography.labelMedium) }

                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = { viewModel.adjustLyricsOffset(-100L) },
                                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("-.1s", style = MaterialTheme.typography.labelMedium) }

                                        ToggleButton(
                                            checked = viewModel.lyricsOffset == 0L,
                                            onCheckedChange = { viewModel.resetLyricsOffset() },
                                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                            modifier = Modifier.weight(1f)
                                        ) { 
                                            Text("0s", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) 
                                        }

                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = { viewModel.adjustLyricsOffset(100L) },
                                            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("+.1s", style = MaterialTheme.typography.labelMedium) }

                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = { viewModel.adjustLyricsOffset(1000L) },
                                            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                            modifier = Modifier.weight(1f)
                                        ) { Text("+1s", style = MaterialTheme.typography.labelMedium) }
                                    }
                                }
                            }

                            // 2. TAILLE DU TEXTE
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = when {
                                                isFullScreen -> str("pref_lyrics_fullscreen_size", "Size (full screen)")
                                                isSidebar -> str("pref_lyrics_size_sidebar", "Size (side panel)")
                                                else -> str("pref_lyrics_size_central", str("pref_lyrics_size", "Size (central mode)"))
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${fontSize.roundToInt()} sp",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            IconButton(
                                                onClick = { updateFontSize(defaultFontSize) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Rounded.RestartAlt, str("pref_lyrics_reset"), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateFontSize((fontSize - 2f).coerceAtLeast(12f)) }) {
                                            Icon(Icons.Rounded.Remove, null)
                                        }
                                        Slider(
                                            value = fontSize,
                                            onValueChange = { updateFontSize(it) },
                                            valueRange = 12f..100f,
                                            steps = 43,
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                        )
                                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateFontSize((fontSize + 2f).coerceAtMost(100f)) }) {
                                            Icon(Icons.Rounded.Add, null)
                                        }
                                    }
                                }
                            }

                            if (uiStyle == com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED) {
                                // 2b. INTERVALLE ENTRE LIGNES (LINE SPACING)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isFullScreen) str("pref_lyrics_fullscreen_line_spacing_title", "Line Spacing (Fullscreen)") else str("pref_lyrics_line_spacing_title", "Line Spacing"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${lineSpacing.roundToInt()} dp",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { updateLineSpacing(0f) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Rounded.RestartAlt, str("pref_lyrics_reset"), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        val minSpacing = 0f
                                        val maxSpacing = if (isFullScreen) 64f else 48f
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateLineSpacing((lineSpacing - 2f).coerceAtLeast(minSpacing)) }) {
                                                Icon(Icons.Rounded.Remove, null)
                                            }
                                            Slider(
                                                value = lineSpacing,
                                                onValueChange = { updateLineSpacing(it) },
                                                valueRange = minSpacing..maxSpacing,
                                                steps = ((maxSpacing - minSpacing) / 2f).toInt() - 1,
                                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                            )
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateLineSpacing((lineSpacing + 2f).coerceAtMost(maxSpacing)) }) {
                                                Icon(Icons.Rounded.Add, null)
                                            }
                                        }
                                    }
                                }

                                // 2c. ZOOMS DE LIGNE ACTIVE (TEXT SCALING)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = str("pref_lyrics_active_scale_title", "Active Line Text Scaling"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${(activeScale * 100).roundToInt()}%",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { updateActiveScale(1.00f) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Rounded.RestartAlt, str("pref_lyrics_reset"), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        Text(
                                            text = str("pref_lyrics_active_scale_desc", "Magnify active singing line"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateActiveScale((activeScale - 0.05f).coerceAtLeast(1.00f)) }) {
                                                Icon(Icons.Rounded.Remove, null)
                                            }
                                            Slider(
                                                value = activeScale,
                                                onValueChange = { updateActiveScale(it) },
                                                valueRange = 1.00f..1.30f,
                                                steps = 5,
                                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                            )
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateActiveScale((activeScale + 0.05f).coerceAtMost(1.30f)) }) {
                                                Icon(Icons.Rounded.Add, null)
                                            }
                                        }
                                    }
                                }

                                // 2d. MARGES HORIZONTALES & VERTICALES
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        // Horizontal Margin
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isFullScreen) str("pref_lyrics_fullscreen_horizontal_margin_title", "Horizontal Margins") else str("pref_lyrics_horizontal_margin_title", "Horizontal Margins"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${horizontalMargin.roundToInt()} dp",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { updateHorizontalMargin(0f) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Rounded.RestartAlt, str("pref_lyrics_reset"), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        val maxMargin = if (isFullScreen) 160f else 64f
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateHorizontalMargin((horizontalMargin - 4f).coerceAtLeast(0f)) }) {
                                                Icon(Icons.Rounded.Remove, null)
                                            }
                                            Slider(
                                                value = horizontalMargin,
                                                onValueChange = { updateHorizontalMargin(it) },
                                                valueRange = 0f..maxMargin,
                                                steps = (maxMargin / 8f).toInt() - 1,
                                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                            )
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateHorizontalMargin((horizontalMargin + 4f).coerceAtMost(maxMargin)) }) {
                                                Icon(Icons.Rounded.Add, null)
                                            }
                                        }

                                        Spacer(Modifier.height(10.dp))

                                        // Vertical Position
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isFullScreen) str("pref_lyrics_fullscreen_vertical_offset_title", "Vertical Position") else str("pref_lyrics_vertical_offset_title", "Vertical Position"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${(verticalOffset * 100).roundToInt()}%",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { updateVerticalOffset(0.38f) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Rounded.RestartAlt, str("pref_lyrics_reset"), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateVerticalOffset((verticalOffset - 0.02f).coerceAtLeast(0.20f)) }) {
                                                Icon(Icons.Rounded.Remove, null)
                                            }
                                            Slider(
                                                value = verticalOffset,
                                                onValueChange = { updateVerticalOffset(it) },
                                                valueRange = 0.20f..0.60f,
                                                steps = 19,
                                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                            )
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { updateVerticalOffset((verticalOffset + 0.02f).coerceAtMost(0.60f)) }) {
                                                Icon(Icons.Rounded.Add, null)
                                            }
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.resetLyricsTypography(isFullScreen) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = str("pref_lyrics_reset_typography", "Rétablir les tailles par défaut"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2b. VITESSE DE DÉFILEMENT (texte non synchronisé)
                            // Here as well as in the full settings: this is the screen you are on when
                            // you notice the speed is wrong (issue #33).
                            if (viewModel.isPlainAutoScrollEnabled) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = str("pref_lyrics_autoscroll_speed"),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = autoScrollSpeedLabel(viewModel.effectivePlainAutoScrollSpeed),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))

                                        // One slider for both, because they are one question asked of
                                        // different scopes: the switch below decides whether the number
                                        // being dragged belongs to this song or to every song (issue #33).
                                        val perTrack = viewModel.trackAutoScrollSpeed != null
                                        val speed = viewModel.effectivePlainAutoScrollSpeed
                                        val setSpeed: (Float) -> Unit = { value ->
                                            if (perTrack) viewModel.setTrackAutoScrollSpeed(value)
                                            else viewModel.updatePlainAutoScrollSpeed(value)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                                setSpeed(speed - 0.25f)
                                            }) { Icon(Icons.Rounded.Remove, null) }
                                            Slider(
                                                value = speed,
                                                onValueChange = setSpeed,
                                                valueRange = 0.25f..4f,
                                                steps = 14,
                                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                            )
                                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                                setSpeed(speed + 0.25f)
                                            }) { Icon(Icons.Rounded.Add, null) }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = str("pref_lyrics_speed_this_track"),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    text = str("pref_lyrics_speed_this_track_sub"),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Switch(
                                                checked = perTrack,
                                                onCheckedChange = { on ->
                                                    // Turning it on starts from whatever is on screen, so
                                                    // the number does not jump when the scope changes.
                                                    if (on) viewModel.setTrackAutoScrollSpeed(speed)
                                                    else viewModel.clearTrackAutoScrollSpeed()
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            // 2c. PAS DE LA MOLETTE
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = str("pref_lyrics_wheel_step"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = str(
                                                "pref_lyrics_wheel_step_value",
                                                wheelLinesLabel(viewModel.lyricsWheelLines),
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = str("pref_lyrics_wheel_step_sub"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                            viewModel.updateLyricsWheelLines(viewModel.lyricsWheelLines - 0.5f)
                                        }) { Icon(Icons.Rounded.Remove, null) }
                                        Slider(
                                            value = viewModel.lyricsWheelLines,
                                            onValueChange = { viewModel.updateLyricsWheelLines(it) },
                                            valueRange = PlayerPreferences.LYRICS_WHEEL_LINES_MIN..PlayerPreferences.LYRICS_WHEEL_LINES_MAX,
                                            steps = 21,
                                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                        )
                                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                                            viewModel.updateLyricsWheelLines(viewModel.lyricsWheelLines + 0.5f)
                                        }) { Icon(Icons.Rounded.Add, null) }
                                    }
                                }
                            }

                            // 3. ALIGNEMENT
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = str("pref_lyrics_align"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    ExpressiveConnectedButtonGroup(
                                        fillMaxWidth = true,
                                        options = listOf(LyricsAlignment.LEFT, LyricsAlignment.CENTER, LyricsAlignment.RIGHT),
                                        selectedOption = alignment,
                                        onOptionSelected = { updateAlignment(it) },
                                        iconProvider = { align ->
                                            val icon = when (align) {
                                                LyricsAlignment.LEFT -> Icons.Rounded.FormatAlignLeft
                                                LyricsAlignment.CENTER -> Icons.Rounded.FormatAlignCenter
                                                LyricsAlignment.RIGHT -> Icons.Rounded.FormatAlignRight
                                            }
                                            Icon(icon, null, modifier = Modifier.size(16.dp))
                                        },
                                        labelProvider = { align ->
                                            val text = when (align) {
                                                LyricsAlignment.LEFT -> str("align_left")
                                                LyricsAlignment.CENTER -> str("align_center_simple")
                                                LyricsAlignment.RIGHT -> str("align_right")
                                            }
                                            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                                        }
                                    )
                                }
                            }

                            // 3b. STYLE D'AFFICHAGE DE LA LIGNE COURANTE (Uniquement pour le mode classique)
                            if (uiStyle == com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = str("pref_lyrics_display_style"),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        val hasScale = displayStyle == LyricsDisplayStyle.SCALE || displayStyle == LyricsDisplayStyle.SCALE_FOCUS
                                        val hasFocus = displayStyle == LyricsDisplayStyle.FOCUS || displayStyle == LyricsDisplayStyle.SCALE_FOCUS

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                                        ) {
                                            ToggleButton(
                                                checked = hasScale,
                                                onCheckedChange = { nextScale ->
                                                    val next = when {
                                                        nextScale && hasFocus -> LyricsDisplayStyle.SCALE_FOCUS
                                                        nextScale -> LyricsDisplayStyle.SCALE
                                                        hasFocus -> LyricsDisplayStyle.FOCUS
                                                        else -> LyricsDisplayStyle.STANDARD
                                                    }
                                                    updateDisplayStyle(next)
                                                },
                                                modifier = Modifier.weight(1f),
                                                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Rounded.FormatSize, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        str("lyrics_style_scale"),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }

                                            ToggleButton(
                                                checked = hasFocus,
                                                onCheckedChange = { nextFocus ->
                                                    val next = when {
                                                        hasScale && nextFocus -> LyricsDisplayStyle.SCALE_FOCUS
                                                        hasScale -> LyricsDisplayStyle.SCALE
                                                        nextFocus -> LyricsDisplayStyle.FOCUS
                                                        else -> LyricsDisplayStyle.STANDARD
                                                    }
                                                    updateDisplayStyle(next)
                                                },
                                                modifier = Modifier.weight(1f),
                                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Rounded.CenterFocusStrong, null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        str("lyrics_style_focus"),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ==========================================
                        // COLONNE DROITE (Toggles & Recherche)
                        // ==========================================
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 4. TOGGLES (Fichiers locaux, Karaoké, Effet Apple, etc.)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                
                                    // Fichiers locaux
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(str("pref_lyrics_local"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text(str("pref_lyrics_local_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = preferLocal, onCheckedChange = { preferLocal = it; prefs.setLyricsPreferLocal(it) })
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    // Synchro Mot par mot
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(str("pref_lyrics_word_sync"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text(str("pref_lyrics_word_sync_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = viewModel.isWordSyncEnabled, onCheckedChange = { viewModel.toggleWordSync(it) })
                                    }

                                    // Effet Apple Music (Uniquement si Word Sync activé)
                                    AnimatedVisibility(visible = viewModel.isWordSyncEnabled) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                                    Text(str("pref_lyrics_apple_effect"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                    Text(str("pref_lyrics_apple_effect_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Switch(checked = viewModel.isAppleMusicEffectEnabled, onCheckedChange = { viewModel.toggleAppleMusicEffect(it) })
                                            }
                                        }
                                    }

                                    // Mode Duo
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(str("pref_lyrics_duet_title"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text(str("pref_lyrics_duet_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = viewModel.isDuetViewEnabled, onCheckedChange = { viewModel.toggleDuetView(it) })
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    // Prononciation (Romaji)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(str("pref_lyrics_romanization"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text(str("pref_lyrics_romanization_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = viewModel.isRomanizationEnabled, onCheckedChange = { viewModel.toggleRomanization(it) })
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    // Traduction
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(str("pref_lyrics_translation_title"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text(str("pref_lyrics_translation_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = enableTranslation, onCheckedChange = { 
                                            enableTranslation = it
                                            viewModel.toggleLyricsTranslation(it)
                                        })
                                    }

                                    AnimatedVisibility(visible = enableTranslation) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { showLangDialog = true }.padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(str("pref_lyrics_translation_lang"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(targetLang.uppercase(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                Icon(Icons.Rounded.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }



                            // Bouton RECHERCHE MANUELLE en bas de la colonne de droite
                            Button(
                                onClick = {
                                    onDismiss()
                                    viewModel.isSearchingLyrics = true
                                },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer, 
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(str("lyrics_manual_search"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
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
                        val dialog = java.awt.FileDialog(null as java.awt.Frame?, str("btn_upload_yaml"), java.awt.FileDialog.LOAD)
                        dialog.isVisible = true
                        if (dialog.directory != null && dialog.file != null) {
                            val file = java.io.File(dialog.directory, dialog.file)
                            if (file.exists()) {
                                viewModel.loadCustomLyrics(file.readText())
                                onDismiss()
                            }
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
 * Draws the hover rule under a lyric line by hand.
 *
 * `TextDecoration.Underline` is drawn per font run, and a lyric line routinely spans several
 * runs: the variable UI font has no Cyrillic, Arabic or CJK coverage, so those stretches fall
 * back to a system face with its own underline thickness and position. The result was a rule
 * that looked dashed and stepped — reported for Russian lyrics in issue #33. One rect per
 * laid-out line, at one thickness, is the same rule whatever the script.
 *
 * @param layout the last layout of the text this sits on, read lazily so a relayout is picked
 *   up without recreating the modifier.
 * @param fontSizeSp the line's font size, which the thickness and the drop below the baseline
 *   are both derived from, so the rule scales with the lyrics font-size setting.
 */
/**
 * The hover rule under a lyric line.
 *
 * Internal rather than private because the panel renderer draws the same lines and needs the same affordance:
 * it was relying on `clickable`'s default indication instead, which on a 34 sp line at full-screen width is a
 * ripple the width of the screen — "un gros truc en surbrillance moche" (issue #33).
 */
internal fun Modifier.lyricUnderline(
    layout: () -> androidx.compose.ui.text.TextLayoutResult?,
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
            topLeft = androidx.compose.ui.geometry.Offset(left, result.getLineBaseline(i) + drop),
            size = androidx.compose.ui.geometry.Size(right - left, thickness),
        )
    }
}



/** "1.5×" — one decimal only when there is one, matching the label in the full settings. */
/** "3" rather than "3.0", and "2.5" when it is not whole. */
private fun wheelLinesLabel(lines: Float): String {
    val rounded = kotlin.math.round(lines * 2f) / 2f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
}

private fun autoScrollSpeedLabel(speed: Float): String {
    val rounded = kotlin.math.round(speed * 100f) / 100f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    return "$text×"
}



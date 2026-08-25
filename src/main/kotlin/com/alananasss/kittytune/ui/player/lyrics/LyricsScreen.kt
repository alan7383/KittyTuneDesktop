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
    import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.foundation.lazy.rememberLazyListState
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.KeyboardActions
    import androidx.compose.foundation.text.KeyboardOptions
    import androidx.compose.foundation.verticalScroll
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.Close
    import androidx.compose.material.icons.rounded.Add
    import androidx.compose.material.icons.rounded.ArrowDropDown
    import androidx.compose.material.icons.rounded.ContentCopy
    import androidx.compose.material.icons.rounded.Remove
    import androidx.compose.material.icons.rounded.Search
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

    import com.alananasss.kittytune.data.network.LrcLibResponse
    import androidx.compose.ui.window.DialogProperties
    import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignRight
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.core.BackHandler
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

        val hasSynced = viewModel.lyricsLines.any { it.endTime > 0 }
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
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                ArtistLinkText(
                                                    track = currentTrack,
                                                    onArtistClick = { viewModel.navigateToTrackArtist(it) },
                                                    text = currentTrack.user?.username ?: "",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.7f),
                                                    hoverColor = Color.White,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (currentTrack.user?.verified == true) {
                                                    Spacer(Modifier.width(3.dp))
                                                    Icon(Icons.Rounded.Verified, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text(
                                        str("player_lyrics"),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                                    Icon(Icons.Rounded.Close, str("btn_close"), tint = Color.White)
                                }
                            },
                            actions = {
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showUploadYamlDialog = true }) {
                                    Icon(Icons.Rounded.Add, str("btn_upload_yaml"), tint = Color.White)
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showQuickSettingsDialog = true }) {
                                    val tint = if (viewModel.lyricsOffset != 0L) MaterialTheme.colorScheme.primary else Color.White
                                    Icon(Icons.Rounded.Settings, str("pref_lyrics_title"), tint = tint)
                                }
                                IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.isSearchingLyrics = true }) {
                                    Icon(Icons.Rounded.Search, str("lyrics_manual_search"), tint = Color.White)
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
                                        SyncedLyricsView(viewModel)
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
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = CircleShape,
            modifier = modifier.height(38.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
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
        val backgroundColor by animateColorAsState(
            targetValue = if (isSelected) Color.White else Color.Transparent,
            animationSpec = tween(300),
            label = "bgColor"
        )
        val textColor by animateColorAsState(
            targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = if (enabled) 0.7f else 0.3f),
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

        // --- NOUVEAU : Moteur d'interpolation 144Hz / 244Hz ---
        val isPlaying = viewModel.isPlaying
        val speed = viewModel.effectsState.speed
        
        // Cette variable n'est lue QUE par la carte graphique (drawWithContent)
        var smoothDrawPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
        
        LaunchedEffect(currentPosition, isPlaying, speed) {
            if (isPlaying) {
                val startTime = System.currentTimeMillis()
                val startPos = currentPosition.toFloat()
                while (isActive) {
                    withFrameMillis {
                        // Interpolated to the millisecond between the player's 250 ms position
                        // reports, but never further ahead than one report plus some slack: the
                        // player stops reporting while buffering, seeking or scrubbing, and an
                        // unbounded extrapolation then ran the word highlight off to the end of the
                        // line and snapped it back once the real position arrived — the "one line
                        // goes straight to the very end and jumps around" report in issue #33.
                        val elapsed =
                            (System.currentTimeMillis() - startTime).coerceAtMost(MAX_EXTRAPOLATION_MS)
                        smoothDrawPosition = startPos + (elapsed * speed)
                    }
                }
            } else {
                smoothDrawPosition = currentPosition.toFloat()
            }
        }
        // ------------------------------------------------------
    
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
    
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0 && !listState.isScrollInProgress) {
                listState.animateScrollToItem(index = activeIndex)
            }
        }
    
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
    
                    val targetScale = 1.0f
                    val targetAlpha = if (isActive) 1.0f else (if (index < activeIndex) 0.45f else 0.70f)
                    val targetBlur = 0.dp
    
                    val scale by animateFloatAsState(targetScale, tween(400), label = "scale")
                    val alpha by animateFloatAsState(targetAlpha, tween(400), label = "alpha")
    
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

                    // --- COLUMN GLOBALE DE LA LIGNE ---
                    Column(
                        horizontalAlignment = hzAlignment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .hoverable(lineInteractionSource)
                            .padding(horizontal = 24.dp)
                            .scale(scale)
                            .alpha(alpha)
                            .clickable(interactionSource = lineInteractionSource, indication = null) { viewModel.seekTo(line.startTime) }
                    ) {
                        val isWordSyncEnabled = viewModel.isWordSyncEnabled
                        val isAppleEffect = viewModel.isAppleMusicEffectEnabled
                        val displayWords = if (isWordSyncEnabled) line.words else emptyList()

                        if (isActive && displayWords.isNotEmpty()) {
                            if (isAppleEffect) {
                                var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                val reconstructedText = remember(displayWords) { displayWords.joinToString("") { it.text } }
                                val wordRanges = remember(displayWords) {
                                    val ranges = mutableListOf<Pair<Int, Int>>()
                                    var currentLen = 0
                                    for (w in displayWords) {
                                        ranges.add(currentLen to currentLen + w.text.length)
                                        currentLen += w.text.length
                                    }
                                    ranges
                                }

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = reconstructedText,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp, lineHeight = (fontSize * 1.4).sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        textAlign = alignment,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .lyricUnderline({ hoverLayout }, isHovered, fontSize, MaterialTheme.colorScheme.onSurface),
                                        onTextLayout = { textLayoutResult = it; hoverLayout = it }
                                    )
                                    Text(
                                        text = reconstructedText,
                                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp, lineHeight = (fontSize * 1.4).sp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = alignment,
                                        modifier = Modifier.fillMaxWidth().drawWithContent {
                                            // Clamped to this line's own end so it can only ever
                                            // fill up, never overfill and then bounce.
                                            val rawPos = smoothDrawPosition + viewModel.lyricsOffset
                                            val currentPos =
                                                if (line.endTime > line.startTime) {
                                                    rawPos.coerceAtMost(line.endTime.toFloat())
                                                } else {
                                                    rawPos
                                                }
                                            val layout = textLayoutResult ?: return@drawWithContent
                                            val path = androidx.compose.ui.graphics.Path()
                                            val safeTextLength = (reconstructedText.length - 1).coerceAtLeast(0)
                                            for (i in displayWords.indices) {
                                                val w = displayWords[i]
                                                val range = wordRanges[i]
                                                if (range.first >= range.second) continue
                                                if (currentPos >= w.endTime) {
                                                    for (c in range.first until range.second) path.addRect(layout.getBoundingBox(c.coerceIn(0, safeTextLength)))
                                                } else if (currentPos >= w.startTime) {
                                                    val progress = ((currentPos - w.startTime).toFloat() / (w.endTime - w.startTime).coerceAtLeast(1L)).coerceIn(0f, 1f)
                                                    val exactProgressChars = progress * (range.second - range.first)
                                                    val fullySungChars = exactProgressChars.toInt()
                                                    val charFraction = exactProgressChars - fullySungChars
                                                    for (c in range.first until range.first + fullySungChars) path.addRect(layout.getBoundingBox(c.coerceIn(0, safeTextLength)))
                                                    val partialCharIdx = range.first + fullySungChars
                                                    if (partialCharIdx < range.second) {
                                                        val cBbox = layout.getBoundingBox(partialCharIdx.coerceIn(0, safeTextLength))
                                                        val cX = cBbox.left + (cBbox.right - cBbox.left) * charFraction
                                                        path.addRect(androidx.compose.ui.geometry.Rect(cBbox.left, cBbox.top, cX, cBbox.bottom))
                                                    }
                                                }
                                            }
                                            clipPath(path) { this@drawWithContent.drawContent() }
                                        }
                                    )
                                }
                            } else {
                                val reconstructedText = buildAnnotatedString {
                                    displayWords.forEach { word ->
                                        val isWordActive = (viewModel.currentPosition + viewModel.lyricsOffset) >= word.startTime
                                        val wordColor = if (isWordActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        withStyle(SpanStyle(color = wordColor)) { append(word.text) }
                                    }
                                }
                                Text(
                                    text = reconstructedText,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = fontSize.sp, lineHeight = (fontSize * 1.4).sp),
                                    textAlign = alignment,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lyricUnderline({ hoverLayout }, isHovered, fontSize, MaterialTheme.colorScheme.onSurface),
                                    onTextLayout = { hoverLayout = it }
                                )
                            }
                        } else {
                            val textColor = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f)
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold, fontSize = fontSize.sp, lineHeight = (fontSize * 1.4).sp),
                                color = textColor,
                                textAlign = alignment,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .lyricUnderline({ hoverLayout }, isHovered, fontSize, textColor),
                                onTextLayout = { hoverLayout = it }
                            )
                        }

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
                                textAlign = alignment,
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
                                textAlign = alignment,
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
                            onReset = { viewModel.lyricsOffset = 0L },
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
        // Wheel and drag always win: the reader is following the words, and having the view creep
        // out from under them would be worse than no auto-scroll at all. Any manual scroll parks
        // the automatic one for a moment and it then picks up from wherever they left off.
        var lastUserScrollMs by remember { mutableStateOf(0L) }

        LaunchedEffect(viewModel.isPlainAutoScrollEnabled, viewModel.plainAutoScrollSpeed, text) {
            if (!viewModel.isPlainAutoScrollEnabled) return@LaunchedEffect
            var lastFrameNs = withFrameNanos { it }
            while (isActive) {
                val nowNs = withFrameNanos { it }
                val elapsedSec = (nowNs - lastFrameNs) / 1_000_000_000f
                lastFrameNs = nowNs
                if (System.currentTimeMillis() - lastUserScrollMs < PLAIN_AUTOSCROLL_PAUSE_MS) continue
                if (!listState.canScrollForward) continue
                val step = PLAIN_AUTOSCROLL_BASE_DP_PER_SEC * viewModel.plainAutoScrollSpeed * elapsedSec
                if (step > 0f) {
                    listState.scrollBy(with(density) { step.dp.toPx() })
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdge(fadeBrush)
                    // Observed on the Initial pass and never consumed, so the list still scrolls
                    // exactly as it did before — this only notes that the reader took over.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Scroll ||
                                    event.type == androidx.compose.ui.input.pointer.PointerEventType.Press
                                ) {
                                    lastUserScrollMs = System.currentTimeMillis()
                                }
                            }
                        }
                    },
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
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = alignment,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
    
            FloatingActionButton(onClick = {
                    clipboardManager.setText(AnnotatedString(text))
                },
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
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
                color = Color.White.copy(0.7f),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(24.dp))
    
            Button(onClick = onManualSearch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
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
                color = Color.Black.copy(alpha = 0.4f),
                contentColor = Color.White.copy(alpha = 0.8f)
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
        onCloseSearch: () -> Unit
    ) {
        val focusManager = LocalFocusManager.current
        var query by remember { mutableStateOf(viewModel.manualSearchQuery) }

        // Themed rather than hard-coded black (issue #33): this view covers the whole player,
        // so a flat black panel clashed with both the light theme and the cover-seeded palette.
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                IconButton(onClick = onCloseSearch) {
                    Icon(
                        Icons.Rounded.Close,
                        str("btn_close"),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f).trackTextInput(),
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
                IconButton(onClick = {
                    viewModel.searchLyricsManual(query, viewModel.manualSearchProvider)
                    focusManager.clearFocus()
                }) {
                    Icon(
                        Icons.Rounded.Search,
                        str("search_hint"),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // --- SÉLECTEUR DE FOURNISSEUR ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        ProviderChip(
                            label = "Musixmatch",
                            selected = viewModel.manualSearchProvider == "MUSIXMATCH",
                            onClick = { viewModel.searchLyricsManual(query, "MUSIXMATCH") }
                        )
                        ProviderChip(
                            label = "LrcLib",
                            selected = viewModel.manualSearchProvider == "LRCLIB",
                            onClick = { viewModel.searchLyricsManual(query, "LRCLIB") }
                        )
                        ProviderChip(
                            label = "Genius",
                            selected = viewModel.manualSearchProvider == "GENIUS",
                            onClick = { viewModel.searchLyricsManual(query, "GENIUS") }
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

    /** One provider pill in the manual-search selector. */
    @Composable
    private fun ProviderChip(label: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
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
            color = Color.Black.copy(alpha = 0.8f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
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
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
    
                    // Proper formatting: +0.1s, -0.5s, 0.0s
                    val seconds = offset / 1000.0
                    val sign = if (offset > 0) "+" else ""
                    val color = if (offset == 0L) Color.White.copy(0.7f) else MaterialTheme.colorScheme.primary
    
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
                        tint = Color.White
                    )
    
                    // RESET BUTTON (Simple click is enough)
                    TextButton(onClick = onReset) {
                        Text("RESET", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                    }
    
                    // PLUS BUTTON (Active repetition)
                    RepeatingIconButton(onClick = { onAdjust(100L) }, // +0.1s
                        icon = Icons.Rounded.Add,
                        tint = Color.White
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
            color = Color.White.copy(0.1f), // Background color (translucent gray)
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

@Composable
fun QuickLyricsSettingsDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val prefs = remember { PlayerPreferences() }
    val fontSize = viewModel.lyricsFontSize
    val alignment = viewModel.lyricsAlignment
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
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.width(860.dp).padding(8.dp)
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
                                        onCheckedChange = { viewModel.lyricsOffset = 0L },
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
                                        text = str("pref_lyrics_size"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${fontSize.roundToInt()} sp",
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
                                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.updateLyricsFontSize((fontSize - 2f).coerceAtLeast(12f)) }) {
                                        Icon(Icons.Rounded.Remove, null)
                                    }
                                    Slider(
                                        value = fontSize,
                                        onValueChange = { viewModel.updateLyricsFontSize(it) },
                                        valueRange = 12f..100f,
                                        steps = 43,
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                    )
                                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.updateLyricsFontSize((fontSize + 2f).coerceAtMost(100f)) }) {
                                        Icon(Icons.Rounded.Add, null)
                                    }
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
                                    options = listOf(LyricsAlignment.LEFT, LyricsAlignment.CENTER, LyricsAlignment.RIGHT),
                                    selectedOption = alignment,
                                    onOptionSelected = { viewModel.updateLyricsAlignment(it) },
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

@Composable
fun <T> ExpressiveConnectedButtonGroup(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelProvider: @Composable (T) -> Unit,
    iconProvider: (@Composable (T) -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = selectedOption == option,
                onCheckedChange = { onOptionSelected(option) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (iconProvider != null) {
                        iconProvider(option)
                        Spacer(Modifier.width(8.dp))
                    }
                    labelProvider(option)
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
private fun Modifier.lyricUnderline(
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

/**
 * Auto-scroll rate for lyrics with no timings, at speed 1×, in dp per second. Slow on purpose:
 * the slider spans 0.25× to 4×, so the default has to sit where a comfortable reading pace is,
 * not at the top of the range.
 */
private const val PLAIN_AUTOSCROLL_BASE_DP_PER_SEC = 18f

/** How long a manual scroll holds the automatic one off. */
private const val PLAIN_AUTOSCROLL_PAUSE_MS = 2_500L

/**
 * How far the word-sync highlight may run ahead of the player's last reported position.
 *
 * The player reports every 250 ms, so the highlight has to extrapolate to stay smooth between
 * reports. It must not extrapolate indefinitely: reporting stops outright while buffering, seeking
 * or scrubbing, and without a ceiling the highlight sprinted to the end of the line and jumped back
 * when the real position returned (issue #33). One report plus slack keeps normal playback smooth
 * and turns a stall into a pause rather than a bolt.
 */
private const val MAX_EXTRAPOLATION_MS = 400L

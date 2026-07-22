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
    import androidx.compose.material.icons.rounded.ContentCopy
    import androidx.compose.material.icons.rounded.Remove
    import androidx.compose.material.icons.rounded.Search
    import androidx.compose.material.icons.rounded.Timer
    import androidx.compose.material.icons.rounded.Tune
    import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
    import androidx.compose.runtime.*
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
    import androidx.compose.ui.platform.LocalClipboardManager
    import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
    import com.alananasss.kittytune.core.str
    import androidx.compose.ui.text.AnnotatedString
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
    import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignRight
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlin.math.roundToInt
    import com.alananasss.kittytune.ui.player.LyricsMode
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.utils.makeTimeString
    import com.alananasss.kittytune.ui.utils.fadingEdge
    import androidx.compose.ui.input.pointer.pointerInput
    import androidx.compose.foundation.gestures.detectTapGestures
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

        val hasSynced = viewModel.lyricsLines.any { it.endTime > 0 }
        val hasPlain = !viewModel.rawPlainLyrics.isNullOrBlank()

        if (showQuickSettingsDialog) {
            QuickLyricsSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showQuickSettingsDialog = false }
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
                                            Text(
                                                text = currentTrack.user?.username ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
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
    
        val fadeBrush = remember {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.15f to Color.Black,
                0.85f to Color.Black,
                1f to Color.Transparent
            )
        }
    
        val activeIndex = remember(adjustedPosition, lyrics) {
            lyrics.indexOfFirst { adjustedPosition >= it.startTime && adjustedPosition < it.endTime }
                .takeIf { it != -1 }
                ?: lyrics.indexOfLast { adjustedPosition >= it.startTime }
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

                    val textDecoration = if (isHovered) {
                        TextDecoration.Underline
                    } else {
                        TextDecoration.None
                    }

                    val textColor = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)

                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * 1.4).sp,
                            textDecoration = textDecoration
                        ),
                        color = textColor,
                        textAlign = alignment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .hoverable(lineInteractionSource)
                            .padding(horizontal = 24.dp)
                            .scale(scale)
                            .alpha(alpha)
                            .blur(targetBlur)
                            .clickable(
                                interactionSource = lineInteractionSource,
                                indication = null
                            ) { viewModel.seekTo(line.startTime) }
                    )
                }
            }
    
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp) // Fixed bottom margin
            ) {
                AnimatedContent(
                    targetState = viewModel.showLyricsOffsetControls,
                    transitionSpec = {
                        if (targetState) {
                            // Opening: The panel slides from bottom to top
                            (slideInVertically { height -> height } + fadeIn())
                                .togetherWith(fadeOut(animationSpec = tween(100))) // Button disappears quickly
                        } else {
                            // Closing: The panel slides down
                            (fadeIn(animationSpec = tween(100, delayMillis = 150))) // Button reappears with a small delay
                                .togetherWith(slideOutVertically { height -> height } + fadeOut())
                        }
                    },
                    contentAlignment = Alignment.BottomCenter, // <--- CRUCIAL: Keeps everything stuck at the bottom
                    label = "controls_anim"
                ) { showControls ->
                    if (showControls) {
                        // Remove vertical padding here so it's managed by the transition
                        // to avoid visual "jumping"
                        LyricsOffsetControls(
                            offset = viewModel.lyricsOffset,
                            onAdjust = { viewModel.adjustLyricsOffset(it) },
                            onReset = { viewModel.lyricsOffset = 0L },
                            onClose = { viewModel.showLyricsOffsetControls = false },
                            // Override modifier to adjust padding specifically here
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    } else {
                        WrongLyricsButton(onClick = { viewModel.isSearchingLyrics = true }
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun PlainLyricsView(viewModel: PlayerViewModel) {
        val text = viewModel.rawPlainLyrics ?: str("lyrics_no_data")
        val clipboardManager = LocalClipboardManager.current
    
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
    
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdge(fadeBrush),
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
    
        Column(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Rounded.Close, str("btn_close"), tint = Color.White)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(str("lyrics_search_hint"), color = Color.White.copy(0.5f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(0.5f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        viewModel.searchLyricsManual(query)
                        focusManager.clearFocus()
                    })
                )
                IconButton(onClick = {
                    viewModel.searchLyricsManual(query)
                    focusManager.clearFocus()
                }) {
                    Icon(Icons.Rounded.Search, str("search_hint"), tint = Color.White)
                }
            }

            if (viewModel.isLyricsLoading) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
            }

            val searchResults = remember(viewModel.lyricSearchResults.toList()) {
                viewModel.lyricSearchResults.toList()
            }
    
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = searchResults, key = { it.id }) { result ->
                    Card(
                        onClick = { viewModel.selectLyricResult(result) },
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(result.artistName, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.7f))
                                if (!result.albumName.isNullOrEmpty()) {
                                    Text(result.albumName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f), maxLines = 1)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(makeTimeString((result.duration * 1000).toLong()), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                                if (!result.syncedLyrics.isNullOrEmpty()) {
                                    Icon(Icons.Rounded.Timer, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
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
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = str("pref_lyrics_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, str("btn_close"))
                    }
                }

                Spacer(Modifier.height(16.dp))



                // --- 1. SYNCHRONISATION (SYNC OFFSET) ---
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
                            ) { Text("-1.0s", style = MaterialTheme.typography.labelMedium) }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = { viewModel.adjustLyricsOffset(-100L) },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                modifier = Modifier.weight(1f)
                            ) { Text("-0.1s", style = MaterialTheme.typography.labelMedium) }

                            ToggleButton(
                                checked = viewModel.lyricsOffset == 0L,
                                onCheckedChange = { viewModel.lyricsOffset = 0L },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                modifier = Modifier.weight(1.2f)
                            ) { Text(str("pref_lyrics_reset"), style = MaterialTheme.typography.labelMedium) }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = { viewModel.adjustLyricsOffset(100L) },
                                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                                modifier = Modifier.weight(1f)
                            ) { Text("+0.1s", style = MaterialTheme.typography.labelMedium) }

                            ToggleButton(
                                checked = false,
                                onCheckedChange = { viewModel.adjustLyricsOffset(1000L) },
                                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                modifier = Modifier.weight(1f)
                            ) { Text("+1.0s", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // --- 2. TAILLE DU TEXTE (FONT SIZE) ---
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
                                valueRange = 12f..48f,
                                steps = 17,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                            )
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.updateLyricsFontSize((fontSize + 2f).coerceAtMost(48f)) }) {
                                Icon(Icons.Rounded.Add, null)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // --- 3. ALIGNEMENT (ALIGNEMENT) ---
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
                                Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // --- 4. OPTIONS DE RECHERCHE ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(str("pref_lyrics_local"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(str("pref_lyrics_local_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = preferLocal,
                                onCheckedChange = {
                                    preferLocal = it
                                    prefs.setLyricsPreferLocal(it)
                                }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(str("pref_lyrics_precise"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(str("pref_lyrics_precise_sub"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = viewModel.isPreciseLyricsSearchEnabled,
                                onCheckedChange = { viewModel.togglePreciseLyricsSearch(it) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // --- 5. RECHERCHE MANUELLE ---
                Button(
                    onClick = {
                        onDismiss()
                        viewModel.isSearchingLyrics = true
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("lyrics_manual_search"), style = MaterialTheme.typography.labelLarge)
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

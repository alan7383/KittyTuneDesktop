package com.alananasss.kittytune.ui.player.mini

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.alananasss.kittytune.ui.theme.KittyTuneTheme
import java.awt.Cursor

/**
 * A floating mini-player for lyrics that stays on top of windows.
 *
 * Designed to be placed near the taskbar, top of the screen or anywhere on the desktop.
 * It shows the single line being sung in real time, with smooth word karaoke fill.
 * When a line is longer than the display width, it breaks it into natural readable chunks
 * and smoothly advances to show the next part of the line as playback progresses through it.
 */
@Composable
fun MiniLyricsPlayerWindow(viewModel: PlayerViewModel) {
    val prefs = remember { PlayerPreferences() }
    val initialX = remember { prefs.getMiniPlayerX() }
    val initialY = remember { prefs.getMiniPlayerY() }
    val initialWidth = remember { prefs.getMiniPlayerWidth() }

    val windowState = rememberWindowState(
        size = DpSize(initialWidth.dp, 82.dp),
        position = if (initialX != null && initialY != null) {
            WindowPosition(initialX.dp, initialY.dp)
        } else {
            WindowPosition(Alignment.BottomCenter)
        }
    )

    var isPinned by remember { mutableStateOf(true) }

    LaunchedEffect(windowState.position, windowState.size) {
        val pos = windowState.position
        if (pos.isSpecified) {
            val x = pos.x.value.toInt()
            val y = pos.y.value.toInt()
            val w = windowState.size.width.value.toInt()
            viewModel.saveMiniPlayerBounds(x, y, w)
        }
    }

    Window(
        onCloseRequest = { viewModel.toggleMiniPlayer(false) },
        state = windowState,
        alwaysOnTop = isPinned,
        undecorated = true,
        transparent = true,
        resizable = true,
        title = "KittyTune Mini Player",
    ) {
        LaunchedEffect(window) {
            runCatching {
                window.background = java.awt.Color(0, 0, 0, 0)
                window.isAlwaysOnTop = isPinned
            }
        }

        KittyTuneTheme {
            WindowDraggableArea {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val currentLoc = window.location
                                window.setLocation(
                                    currentLoc.x + dragAmount.x.toInt(),
                                    currentLoc.y + dragAmount.y.toInt()
                                )
                            }
                        }
                ) {
                    MiniLyricsContent(
                        viewModel = viewModel,
                        isPinned = isPinned,
                        onTogglePin = { isPinned = !isPinned },
                        onClose = { viewModel.toggleMiniPlayer(false) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniLyricsContent(
    viewModel: PlayerViewModel,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onClose: () -> Unit,
) {
    val track = viewModel.currentTrack
    val isPlaying = viewModel.isPlaying
    val lyrics = viewModel.lyricsLines
    val rawPosition = viewModel.currentPosition
    val smoothPosition = com.alananasss.kittytune.ui.player.lyrics.rememberSmoothPosition(
        positionMs = rawPosition,
        isPlaying = isPlaying,
        speed = 1.0f,
    )
    val adjustedPosition = smoothPosition + viewModel.lyricsOffset

    val activeIndex = remember(adjustedPosition, lyrics) {
        if (lyrics.isEmpty()) -1 else LyricsUtils.activeLineIndex(lyrics, adjustedPosition.toLong())
    }
    val activeLine = lyrics.getOrNull(activeIndex)

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .hoverable(interactionSource)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail Album Cover or Logo
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (track?.fullResArtwork != null && track.fullResArtwork.isNotBlank()) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Center Area: The Active Single Lyric Line (with smart chunking for long lines)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (track != null && activeLine != null && activeLine.text.isNotBlank()) {
                MiniLyricDisplay(
                    line = activeLine,
                    positionMs = adjustedPosition,
                    wordSync = viewModel.isWordSyncEnabled,
                    fillEffect = viewModel.isAppleMusicEffectEnabled,
                )
            } else if (track != null) {
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = track.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.user?.username ?: str("mini_player_instrumental"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "KittyTune",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = str("mini_player_no_track"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Action Controls (Playback controls & pin/close)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AnimatedVisibility(
                visible = isHovered || track == null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.smartPrevious() },
                        modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(36.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier.size(32.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))
                }
            }

            // Pin / Always-on-top toggle
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = "Pin on top",
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp).pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Displays a single lyric line with smooth karaoke fill.
 * If the line exceeds visible length, it partitions the line into natural readable chunks
 * and seamlessly transitions to the next part of the line when the voice progresses through it.
 */
@Composable
private fun MiniLyricDisplay(
    line: LyricLine,
    positionMs: Float,
    wordSync: Boolean,
    fillEffect: Boolean,
) {
    val words = if (wordSync && line.words.isNotEmpty()) line.words else emptyList()
    val fullText = remember(line.text, words) {
        if (words.isNotEmpty()) words.joinToString("") { it.text } else line.text
    }

    // Partition long lines into ~36-character chunks on word boundaries
    val chunks = remember(fullText, words) {
        splitIntoChunks(fullText, words, maxChunkChars = 36)
    }

    // Determine which chunk is active based on current playback progress
    val activeChunkIndex = remember(chunks, positionMs) {
        resolveActiveChunk(chunks, positionMs)
    }
    val currentChunk = chunks.getOrElse(activeChunkIndex) { chunks.first() }

    AnimatedContent(
        targetState = currentChunk,
        transitionSpec = {
            (slideInVertically { height -> height / 2 } + fadeIn(tween(200)))
                .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(200)))
        },
        label = "miniLyricChunkAnimation"
    ) { chunk ->
        val chunkWords = chunk.words
        val chunkText = chunk.text

        if (chunkWords.isEmpty() || !wordSync) {
            Text(
                text = chunkText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            val ranges = remember(chunkWords) {
                var start = 0
                chunkWords.map { word ->
                    val range = start to start + word.text.length
                    start += word.text.length
                    range
                }
            }

            val activeColor = MaterialTheme.colorScheme.primary
            val unsungColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)

            if (!fillEffect) {
                val coloured = buildAnnotatedString {
                    for (word in chunkWords) {
                        val reached = positionMs >= word.startTime
                        withStyle(SpanStyle(color = if (reached) activeColor else unsungColor)) {
                            append(word.text)
                        }
                    }
                }
                Text(
                    text = coloured,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val position = if (line.endTime > line.startTime) {
                    positionMs.coerceAtMost(line.endTime.toFloat())
                } else {
                    positionMs
                }

                Box {
                    Text(
                        text = chunkText,
                        style = style,
                        color = unsungColor,
                        maxLines = 1,
                        onTextLayout = { layout = it }
                    )
                    Text(
                        text = chunkText,
                        style = style,
                        color = activeColor,
                        maxLines = 1,
                        modifier = Modifier.drawWithContent {
                            val res = layout ?: return@drawWithContent
                            clipPath(sungPath(res, chunkWords, ranges, chunkText.length, position)) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    )
                }
            }
        }
    }
}

internal data class LyricChunk(
    val text: String,
    val words: List<LyricWord>,
    val startTime: Long,
    val endTime: Long,
)

/**
 * Splits a line into sub-chunks of up to [maxChunkChars] length on word boundaries.
 */
internal fun splitIntoChunks(
    fullText: String,
    words: List<LyricWord>,
    maxChunkChars: Int = 36,
): List<LyricChunk> {
    if (fullText.length <= maxChunkChars || words.isEmpty()) {
        val start = words.firstOrNull()?.startTime ?: 0L
        val end = words.lastOrNull()?.endTime ?: 0L
        return listOf(LyricChunk(fullText, words, start, end))
    }

    val chunks = mutableListOf<LyricChunk>()
    var currentChunkWords = mutableListOf<LyricWord>()
    var currentLength = 0

    for (word in words) {
        val nextLength = currentLength + word.text.length
        if (nextLength > maxChunkChars && currentChunkWords.isNotEmpty()) {
            val chunkText = currentChunkWords.joinToString("") { it.text }
            val start = currentChunkWords.first().startTime
            val end = currentChunkWords.last().endTime
            chunks.add(LyricChunk(chunkText, currentChunkWords, start, end))
            currentChunkWords = mutableListOf()
            currentLength = 0
        }
        currentChunkWords.add(word)
        currentLength += word.text.length
    }

    if (currentChunkWords.isNotEmpty()) {
        val chunkText = currentChunkWords.joinToString("") { it.text }
        val start = currentChunkWords.first().startTime
        val end = currentChunkWords.last().endTime
        chunks.add(LyricChunk(chunkText, currentChunkWords, start, end))
    }

    return if (chunks.isEmpty()) {
        listOf(LyricChunk(fullText, emptyList(), 0L, 0L))
    } else {
        chunks
    }
}

internal fun resolveActiveChunk(chunks: List<LyricChunk>, positionMs: Float): Int {
    if (chunks.size <= 1) return 0
    for (i in chunks.indices) {
        val chunk = chunks[i]
        if (positionMs <= chunk.endTime || i == chunks.lastIndex) {
            return i
        }
    }
    return 0
}

private fun sungPath(
    layout: TextLayoutResult,
    words: List<LyricWord>,
    ranges: List<Pair<Int, Int>>,
    textLength: Int,
    positionMs: Float,
): Path {
    val path = Path()
    val lastIndex = (textLength - 1).coerceAtLeast(0)

    for (i in words.indices) {
        val word = words[i]
        val (from, to) = ranges[i]
        if (from >= to) continue

        if (positionMs >= word.endTime) {
            for (c in from until to) path.addRect(layout.getBoundingBox(c.coerceIn(0, lastIndex)))
            continue
        }
        if (positionMs < word.startTime) continue

        val span = (word.endTime - word.startTime).coerceAtLeast(1L)
        val progress = ((positionMs - word.startTime) / span).coerceIn(0f, 1f)
        val exact = progress * (to - from)
        val whole = exact.toInt()
        for (c in from until from + whole) path.addRect(layout.getBoundingBox(c.coerceIn(0, lastIndex)))

        val partial = from + whole
        if (partial < to) {
            val box = layout.getBoundingBox(partial.coerceIn(0, lastIndex))
            val edge = box.left + (box.right - box.left) * (exact - whole)
            path.addRect(Rect(box.left, box.top, edge, box.bottom))
        }
    }
    return path
}

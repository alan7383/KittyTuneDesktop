/*
 * KittyTune Desktop (2026)
 * ArchiveTune Enhanced Lyrics Engine (Apple Music - Accompanist Parity)
 */

package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.lyrics.accompanist.KaraokeLyricsView
import com.alananasss.kittytune.ui.theme.rememberLyricsFontFamily
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val LRC_LEAD_MS = 300L
private const val WORD_SYNC_LEAD_MS = 0L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private const val MANUAL_SCROLL_DEBOUNCE_MS = 50L
private const val LYRIC_FOCUS_ANCHOR_RATIO = 0.42f
private const val LYRIC_LINE_SYNC_TOP_ANCHOR_RATIO = 0.35f
private const val LYRIC_FOCUS_TOP_GUARD_RATIO = 0.18f
private const val LYRIC_FOCUS_BOTTOM_GUARD_RATIO = 0.24f
private const val LYRIC_FOCUS_MIN_SCROLL_PX = 6
private const val LYRIC_FOCUS_ANIMATED_DISTANCE = 12
private const val SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS = 80L
private const val SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS = 180L
private const val SMOOTH_PLAYBACK_DRIFT_CORRECTION = 0.55f
private const val LYRIC_FOCUS_SCROLL_DURATION_MS = 520
private const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1

@Composable
fun LyricsEnhanced(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,
    fontSizeOverride: Float? = null,
    isFullScreen: Boolean = false
) {
    val lines = viewModel.lyricsLines
    val isSynced = lines.any { it.startTime > 0 }
    val isWordSyncedFormat = isSynced && viewModel.isWordSyncEnabled && lines.any { it.words.isNotEmpty() }
    val isDuetActive = viewModel.isDuetViewEnabled

    val lyricsLineBlurPreference = viewModel.lyricsLineBlurEnabled
    val lyricsLineBlur = lyricsLineBlurOverride ?: lyricsLineBlurPreference
    val lyricsTextSize = fontSizeOverride ?: viewModel.lyricsFontSize
    val lyricsLineSpacing = viewModel.lyricsLineSpacing

    val textColor = textColorOverride ?: MaterialTheme.colorScheme.onSurface

    val showTranslations = viewModel.isLyricsTranslationEnabled && lines.any { it.translation != null }
    val showPhonetics = viewModel.isRomanizationEnabled && lines.any { it.romanization != null }

    val baseLayoutDirection = LocalLayoutDirection.current
    val lyricsLayoutDirection = remember(lines, baseLayoutDirection) {
        val firstLine = lines.firstOrNull { it.text.isNotBlank() }
        if (firstLine != null && isRtlText(firstLine.text)) {
            LayoutDirection.Rtl
        } else {
            baseLayoutDirection
        }
    }

    val lyricsSessionKey = remember(viewModel.currentTrack?.id, lines.size) {
        (viewModel.currentTrack?.id ?: "") to lines.map { it.startTime }
    }

    val userAlignment = if (isFullScreen) viewModel.lyricsFullScreenAlignment else viewModel.lyricsAlignment

    val syncedLyrics = remember(lines.toList(), isWordSyncedFormat, isDuetActive, userAlignment) {
        buildSyncedLyrics(lines, isWordSyncedFormat, isDuetActive, userAlignment)
    }

    val leadMs = if (isWordSyncedFormat) WORD_SYNC_LEAD_MS else LRC_LEAD_MS
    val playbackPositionMs = remember(viewModel.currentTrack?.id) {
        mutableLongStateOf(MusicManager.player.currentPosition.coerceAtLeast(0L))
    }
    var isManualScrolling by remember { mutableStateOf(false) }
    var isUserTouching by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }
    val listState = key(lyricsSessionKey) { rememberLazyListState() }

    LaunchedEffect(lyricsSessionKey) {
        playbackPositionMs.longValue = MusicManager.player.currentPosition.coerceAtLeast(0L)
        isManualScrolling = false
        isUserTouching = false
        lastManualScrollTime = 0L
    }

    // High-precision frame loop tracking smooth playback position with PLL drift correction
    LaunchedEffect(viewModel.currentTrack?.id, lyricsSessionKey, viewModel.effectsState.speed) {
        var wasSliderActive = false
        var anchorPlayerPositionMs = MusicManager.player.currentPosition.coerceAtLeast(0L)
        var anchorFrameNanos = 0L
        while (isActive) {
            val isSliderActive = viewModel.isScrubbing
            if (isSliderActive && !wasSliderActive) {
                isManualScrolling = false
            }
            wasSliderActive = isSliderActive

            val rawPosition = (if (isSliderActive) viewModel.currentPosition else MusicManager.player.currentPosition).coerceAtLeast(0L)
            val isPlaying = MusicManager.player.isPlaying

            if (isSliderActive || !isPlaying) {
                anchorPlayerPositionMs = rawPosition
                anchorFrameNanos = 0L
                if (playbackPositionMs.longValue != rawPosition) {
                    playbackPositionMs.longValue = rawPosition
                }
                delay(100L)
            } else {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                if (anchorFrameNanos == 0L) {
                    anchorFrameNanos = frameNanos
                    anchorPlayerPositionMs = rawPosition
                }

                val currentSpeed = viewModel.effectsState.speed
                val elapsedMs = ((frameNanos - anchorFrameNanos) / 1_000_000f) * currentSpeed
                val projectedPosition = anchorPlayerPositionMs + elapsedMs.roundToLong()
                val driftMs = rawPosition - projectedPosition
                val nextPosition = when {
                    driftMs > SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS || driftMs < -SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS -> {
                        anchorPlayerPositionMs = rawPosition
                        anchorFrameNanos = frameNanos
                        rawPosition
                    }
                    driftMs != 0L -> {
                        projectedPosition + (driftMs * SMOOTH_PLAYBACK_DRIFT_CORRECTION).roundToLong()
                    }
                    else -> projectedPosition
                }.coerceAtLeast(0L)

                if (playbackPositionMs.longValue != nextPosition) {
                    playbackPositionMs.longValue = nextPosition
                }
            }
        }
    }

    val playbackSyncPosition: () -> Int = remember {
        {
            (playbackPositionMs.longValue + viewModel.lyricsOffset + leadMs)
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
        }
    }

    val lineFocusPosition: () -> Int = remember(syncedLyrics) {
        {
            syncedLyrics.positionForStableLineFocus(playbackSyncPosition())
        }
    }

    val nestedScrollConnection = remember {
        var lastUserScrollEventMs = 0L
        object : NestedScrollConnection {
            private fun markManualScroll() {
                val now = System.currentTimeMillis()
                if (now - lastUserScrollEventMs >= MANUAL_SCROLL_DEBOUNCE_MS) {
                    isManualScrolling = true
                    lastManualScrollTime = now
                    lastUserScrollEventMs = now
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    markManualScroll()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (isManualScrolling) {
                    lastManualScrollTime = System.currentTimeMillis()
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(isManualScrolling, isUserTouching, lastManualScrollTime) {
        if (isManualScrolling) {
            // Keep timeout from elapsing while user is touching screen or list is actively flinging
            while (isUserTouching || listState.isScrollInProgress) {
                lastManualScrollTime = System.currentTimeMillis()
                delay(100L)
            }
            while (isActive) {
                val elapsed = System.currentTimeMillis() - lastManualScrollTime
                val remaining = MANUAL_SCROLL_TIMEOUT_MS - elapsed
                if (remaining <= 0) break
                delay(remaining.coerceAtLeast(10L))
                if (isUserTouching || listState.isScrollInProgress) {
                    lastManualScrollTime = System.currentTimeMillis()
                }
            }
            if (!isUserTouching && !listState.isScrollInProgress) {
                isManualScrolling = false
            }
        }
    }

    // Auto-scroll keeping active lyric in center focus
    LaunchedEffect(lyricsSessionKey, syncedLyrics, isSynced) {
        if (!isSynced || syncedLyrics.lines.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.viewportEndOffset > listState.layoutInfo.viewportStartOffset
        }.first { it }

        var forceNextScroll = true
        snapshotFlow {
            if (isManualScrolling) {
                null
            } else {
                syncedLyrics
                    .getCurrentFirstHighlightLineIndexByTime(lineFocusPosition())
                    .takeIf { index -> index in syncedLyrics.lines.indices }
            }
        }.distinctUntilChanged()
            .collectLatest { index ->
                if (index == null) {
                    forceNextScroll = true
                    return@collectLatest
                }

                listState.scrollLyricIntoFocus(
                    index = index,
                    animateToNearbyItem = true,
                    force = forceNextScroll,
                    alignByItemCenter = isWordSyncedFormat,
                )
                forceNextScroll = false
            }
    }

    val lyricsFontFamily = rememberLyricsFontFamily(viewModel.lyricsFont)
    val normalTextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = lyricsTextSize.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = lyricsFontFamily
    )
    val accompanimentTextStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = (lyricsTextSize * 0.82f).sp,
        fontFamily = lyricsFontFamily
    )
    val phoneticTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = (lyricsTextSize * 0.55f).sp,
        fontWeight = FontWeight.Normal,
        fontFamily = lyricsFontFamily
    )

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize().padding(bottom = 12.dp)
    ) {
        when {
            lines.isEmpty() && viewModel.rawPlainLyrics.isNullOrBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = str("lyrics_no_data", "No lyrics available"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            !isSynced -> {
                // Plain lyrics fallback with smooth auto-scroll
                PlainLyricsView(viewModel)
            }

            else -> {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val anyPressed = event.changes.any { it.pressed }
                                    if (anyPressed) {
                                        isUserTouching = true
                                        isManualScrolling = true
                                        lastManualScrollTime = System.currentTimeMillis()
                                    } else if (isUserTouching) {
                                        isUserTouching = false
                                        lastManualScrollTime = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                        .nestedScroll(nestedScrollConnection)
                ) {
                    val lyricsViewportOffset = remember(maxHeight) { maxHeight * 0.38f }

                    CompositionLocalProvider(LocalLayoutDirection provides lyricsLayoutDirection) {
                        key(lyricsSessionKey, syncedLyrics, showTranslations, showPhonetics, isDuetActive, userAlignment, lyricsFontFamily) {
                            KaraokeLyricsView(
                                listState = listState,
                                lyrics = syncedLyrics,
                                currentPosition = playbackSyncPosition,
                                onLineClicked = { line ->
                                    if (line.start >= 0) {
                                        isManualScrolling = false
                                        isUserTouching = false
                                        MusicManager.player.seekTo(line.start.toLong())
                                    }
                                },
                                onLinePressed = {},
                                textColor = textColor,
                                normalLineTextStyle = normalTextStyle,
                                accompanimentLineTextStyle = accompanimentTextStyle,
                                phoneticTextStyle = phoneticTextStyle,
                                blendMode = BlendMode.SrcOver,
                                useBlurEffect = lyricsLineBlur,
                                showTranslation = showTranslations,
                                showPhonetic = showPhonetics,
                                offset = lyricsViewportOffset,
                                keepAliveZone = 72.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun LazyListState.scrollLyricIntoFocus(
    index: Int,
    animateToNearbyItem: Boolean,
    force: Boolean,
    alignByItemCenter: Boolean
) {
    val itemCount = layoutInfo.totalItemsCount
    if (itemCount == 0) return

    val targetIndex = index.coerceIn(0, itemCount - 1)
    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportHeight = viewportEnd - viewportStart
    if (viewportHeight <= 0) return

    val anchorRatio = if (alignByItemCenter) {
        LYRIC_FOCUS_ANCHOR_RATIO
    } else {
        LYRIC_LINE_SYNC_TOP_ANCHOR_RATIO
    }
    val targetFocusPoint = viewportStart + (viewportHeight * anchorRatio).roundToInt()

    var itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    if (itemInfo == null) {
        val distance = abs(targetIndex - firstVisibleItemIndex)
        if (animateToNearbyItem && distance <= LYRIC_FOCUS_ANIMATED_DISTANCE) {
            animateScrollToItem(
                index = targetIndex,
                scrollOffset = -targetFocusPoint
            )
        } else {
            val preJumpIndex = if (targetIndex > firstVisibleItemIndex) {
                (targetIndex - 2).coerceAtLeast(0)
            } else {
                (targetIndex + 2).coerceAtMost(itemCount - 1)
            }
            scrollToItem(preJumpIndex, -targetFocusPoint)
            withFrameNanos { }
            animateScrollToItem(
                index = targetIndex,
                scrollOffset = -targetFocusPoint
            )
        }
        withFrameNanos { }
        itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    }

    itemInfo ?: return

    val itemFocusPoint = if (alignByItemCenter) {
        itemInfo.offset + itemInfo.size / 2
    } else {
        itemInfo.offset
    }
    val topGuard = viewportStart + (viewportHeight * LYRIC_FOCUS_TOP_GUARD_RATIO).roundToInt()
    val bottomGuard = viewportEnd - (viewportHeight * LYRIC_FOCUS_BOTTOM_GUARD_RATIO).roundToInt()
    if (!force && itemFocusPoint in topGuard..bottomGuard) return

    val scrollDelta = itemFocusPoint - targetFocusPoint
    if (abs(scrollDelta) > LYRIC_FOCUS_MIN_SCROLL_PX) {
        animateScrollBy(
            value = scrollDelta.toFloat(),
            animationSpec = tween(
                durationMillis = LYRIC_FOCUS_SCROLL_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        )
    }
}

private fun SyncedLyrics.positionForStableLineFocus(time: Int): Int {
    if (lines.isEmpty()) return time
    val index = findLastStartedLineIndex(time)
    if (index < 0) return time

    val line = lines[index]
    if (time < line.end) return time

    return (line.end - 1).coerceAtLeast(line.start)
}

private fun SyncedLyrics.findLastStartedLineIndex(time: Int): Int {
    var low = 0
    var high = lines.lastIndex
    var result = -1

    while (low <= high) {
        val mid = low + (high - low) / 2
        if (lines[mid].start <= time) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return result
}

fun buildSyncedLyrics(
    entries: List<LyricLine>,
    isWordSynced: Boolean,
    isDuetEnabled: Boolean = false,
    userAlignment: LyricsAlignment = LyricsAlignment.LEFT
): SyncedLyrics {
    if (entries.isEmpty()) return SyncedLyrics(emptyList())
    val lines = mutableListOf<ISyncedLine>()

    entries.forEachIndexed { index, entry ->
        if (entry.startTime < 0L) return@forEachIndexed
        if (entry.isInstrumental) return@forEachIndexed
        val cleanText = LyricsUtils.decodeHtmlEntities(entry.text)
        if (cleanText.isBlank() && entry.words.isEmpty()) return@forEachIndexed
        val cleanTranslation = entry.translation?.let { LyricsUtils.decodeHtmlEntities(it) }

        val effectiveSinger = if (isDuetEnabled) {
            entry.singer?.takeIf { it != LyricSinger.DEFAULT }
                ?: when (entry.agent?.trim()?.lowercase()) {
                    "v2", "singer2", "2" -> LyricSinger.SINGER_2
                    "v1", "singer1", "1" -> LyricSinger.SINGER_1
                    "both", "group", "all", "v1000", "v2000", "3", "v3" -> LyricSinger.BOTH
                    else -> LyricSinger.DEFAULT
                }
        } else {
            LyricSinger.DEFAULT
        }

        val alignment = if (isDuetEnabled && effectiveSinger != LyricSinger.DEFAULT) {
            when (effectiveSinger) {
                LyricSinger.SINGER_2 -> KaraokeAlignment.End
                LyricSinger.SINGER_1 -> KaraokeAlignment.Start
                LyricSinger.BOTH -> KaraokeAlignment.Unspecified
                else -> when (userAlignment) {
                    LyricsAlignment.RIGHT -> KaraokeAlignment.End
                    LyricsAlignment.CENTER -> KaraokeAlignment.Unspecified
                    LyricsAlignment.LEFT -> KaraokeAlignment.Start
                }
            }
        } else {
            when (userAlignment) {
                LyricsAlignment.RIGHT -> KaraokeAlignment.End
                LyricsAlignment.CENTER -> KaraokeAlignment.Unspecified
                LyricsAlignment.LEFT -> KaraokeAlignment.Start
            }
        }

        val hasWords = isWordSynced && entry.words.isNotEmpty()
        if (hasWords) {
            val mainWords = entry.words.filter { !it.isBackground }
            val bgWords = entry.words.filter { it.isBackground }

            val wordsForMain = if (mainWords.isNotEmpty()) mainWords else entry.words
            val formattedMainContents = formatLyricWordContents(cleanText, wordsForMain)
            val mainSyllables = wordsForMain.mapIndexed { wIdx, w ->
                val start = w.startTime.toInt()
                val end = w.endTime.toInt().coerceAtLeast(start + MIN_KARAOKE_SYLLABLE_DURATION_MS)
                KaraokeSyllable(
                    content = formattedMainContents.getOrElse(wIdx) { LyricsUtils.decodeHtmlEntities(w.text) },
                    start = start,
                    end = end,
                    phonetic = null
                )
            }

            val lineStart = entry.startTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val lineEnd = if (entry.endTime > entry.startTime) {
                entry.endTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            } else {
                mainSyllables.maxOfOrNull { it.end } ?: (lineStart + 4000)
            }
            if (lineEnd <= lineStart) return@forEachIndexed

            val accompanimentLines = if (mainWords.isNotEmpty() && bgWords.isNotEmpty()) {
                val formattedBgContents = formatLyricWordContents("", bgWords)
                val bgSyllables = bgWords.mapIndexed { bgIdx, w ->
                    val start = w.startTime.toInt()
                    val end = w.endTime.toInt().coerceAtLeast(start + MIN_KARAOKE_SYLLABLE_DURATION_MS)
                    KaraokeSyllable(
                        content = formattedBgContents.getOrElse(bgIdx) { LyricsUtils.decodeHtmlEntities(w.text) },
                        start = start,
                        end = end,
                        phonetic = null
                    )
                }
                val bgStart = bgSyllables.minOfOrNull { it.start } ?: lineStart
                val bgEnd = bgSyllables.maxOfOrNull { it.end } ?: lineEnd
                if (bgEnd > bgStart) {
                    listOf(
                        KaraokeLine.AccompanimentKaraokeLine(
                            syllables = bgSyllables,
                            translation = null,
                            alignment = alignment,
                            start = bgStart,
                            end = bgEnd,
                            phonetic = null
                        )
                    )
                } else null
            } else null

            lines.add(
                KaraokeLine.MainKaraokeLine(
                    syllables = mainSyllables,
                    translation = cleanTranslation,
                    alignment = alignment,
                    start = lineStart,
                    end = lineEnd,
                    phonetic = entry.romanization,
                    accompanimentLines = accompanimentLines
                )
            )
        } else {
            val nextEntry = entries.getOrNull(index + 1)
            val lineEnd = if (entry.endTime > entry.startTime) {
                entry.endTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            } else if (nextEntry != null && nextEntry.startTime > entry.startTime) {
                val gap = nextEntry.startTime - entry.startTime
                if (gap > 3000L) {
                    minOf((nextEntry.startTime - 1L).toInt(), (entry.startTime + 4000L).toInt())
                        .coerceAtLeast(entry.startTime.toInt() + 1)
                } else {
                    (nextEntry.startTime - 1L).coerceAtLeast(entry.startTime + 1L).toInt()
                }
            } else {
                (entry.startTime + 4000L).toInt()
            }

            if (!entry.romanization.isNullOrBlank() || alignment != KaraokeAlignment.Start) {
                val syllables = buildWrappingKaraokeSyllables(
                    content = cleanText,
                    romanizedText = entry.romanization.orEmpty(),
                    start = entry.startTime.toInt(),
                    end = lineEnd
                )
                lines.add(
                    KaraokeLine.MainKaraokeLine(
                        syllables = syllables,
                        translation = cleanTranslation,
                        alignment = alignment,
                        start = entry.startTime.toInt(),
                        end = lineEnd,
                        phonetic = entry.romanization?.takeIf { it.isNotBlank() }
                    )
                )
            } else {
                lines.add(
                    SyncedLine(
                        content = cleanText,
                        translation = cleanTranslation,
                        start = entry.startTime.toInt(),
                        end = lineEnd
                    )
                )
            }
        }
    }

    return SyncedLyrics(lines = lines)
}

private fun buildWrappingKaraokeSyllables(
    content: String,
    romanizedText: String,
    start: Int,
    end: Int
): List<KaraokeSyllable> {
    val contentUnits = content.toLyricsWrappingUnits().ifEmpty { listOf(content) }
    val phoneticWords = romanizedText.split(Regex("\\s+")).filter(String::isNotEmpty)
    val phoneticAnchorIndices = contentUnits.indices.filter { index ->
        contentUnits[index].any(Char::isLetterOrDigit)
    }
    val phoneticsByUnit = MutableList<String?>(contentUnits.size) { null }

    if (phoneticAnchorIndices.isNotEmpty()) {
        phoneticWords.forEachIndexed { wordIndex, word ->
            val anchorIndex = wordIndex * phoneticAnchorIndices.size / phoneticWords.size
            val unitIndex = phoneticAnchorIndices[anchorIndex]
            phoneticsByUnit[unitIndex] = listOfNotNull(phoneticsByUnit[unitIndex], word).joinToString(" ")
        }
    }

    val duration = (end - start).coerceAtLeast(contentUnits.size)
    return contentUnits.mapIndexed { index, unit ->
        val unitStart = start + (duration.toLong() * index / contentUnits.size).toInt()
        val unitEnd = start + (duration.toLong() * (index + 1) / contentUnits.size).toInt()
        KaraokeSyllable(
            content = unit,
            start = unitStart,
            end = unitEnd.coerceAtLeast(unitStart + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phoneticsByUnit[index]
        )
    }
}

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
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToLong

private const val LRC_LEAD_MS = 300L
private const val WORD_SYNC_LEAD_MS = 0L
private const val SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS = 80L
private const val SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS = 180L
private const val SMOOTH_PLAYBACK_DRIFT_CORRECTION = 0.55f
private const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1

@Composable
fun LyricsEnhanced(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,
    fontSizeOverride: Float? = null,
    lineSpacingOverride: Dp? = null,
    isFullScreen: Boolean = false,
    isSidebar: Boolean = false
) {
    val lines = viewModel.lyricsLines
    val isSynced = lines.any { it.startTime > 0 }
    val isWordSyncedFormat = isSynced && viewModel.isWordSyncEnabled && lines.any { it.words.isNotEmpty() }
    val isDuetActive = viewModel.isDuetActiveForTrack(viewModel.currentTrack)

    val lyricsLineBlur = lyricsLineBlurOverride ?: when {
        isFullScreen -> viewModel.lyricsFullScreenLineBlurEnabled
        isSidebar -> viewModel.lyricsSidebarLineBlurEnabled
        else -> viewModel.lyricsLineBlurEnabled
    }
    val lyricsTextSize = fontSizeOverride ?: if (isFullScreen) viewModel.lyricsFullScreenFontSize else viewModel.lyricsFontSize
    val defaultSpacing = if (isFullScreen) (lyricsTextSize * 0.34f) else (lyricsTextSize * 0.22f)
    val lyricsLineSpacingDp = when {
        lineSpacingOverride != null -> lineSpacingOverride
        isFullScreen && viewModel.lyricsFullScreenLineSpacing > 0f -> viewModel.lyricsFullScreenLineSpacing.dp
        !isFullScreen && viewModel.lyricsLineSpacing > 0f -> viewModel.lyricsLineSpacing.dp
        else -> defaultSpacing.dp
    }
    val lyricsHorizontalMargin = if (isFullScreen) viewModel.lyricsFullScreenHorizontalMargin else viewModel.lyricsHorizontalMargin
    val lyricsHorizontalMarginDp = if (lyricsHorizontalMargin > 0f) lyricsHorizontalMargin.dp else (if (isFullScreen) 0.dp else 16.dp)
    val lyricsVerticalOffsetFraction = if (isFullScreen) viewModel.lyricsFullScreenVerticalOffset else viewModel.lyricsVerticalOffset
    val configuredScale = if (isFullScreen) viewModel.lyricsFullScreenActiveScale else viewModel.lyricsActiveScale
    val lyricsActiveScale = if (isFullScreen && configuredScale <= 1.01f) 1.08f else configuredScale

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

    val userAlignment = when {
        isFullScreen -> viewModel.lyricsFullScreenAlignment
        isSidebar -> viewModel.lyricsSidebarAlignment
        else -> viewModel.lyricsAlignment
    }

    val syncedLyrics = remember(lines.toList(), isWordSyncedFormat, isDuetActive, userAlignment) {
        buildSyncedLyrics(lines, isWordSyncedFormat, isDuetActive, userAlignment)
    }

    val leadMs = if (isWordSyncedFormat) WORD_SYNC_LEAD_MS else LRC_LEAD_MS
    val playbackPositionMs = remember {
        mutableLongStateOf(MusicManager.player.currentPosition.coerceAtLeast(0L))
    }
    val listState = remember(lyricsSessionKey) { LazyListState() }

    LaunchedEffect(viewModel.currentTrack?.id, lyricsSessionKey) {
        playbackPositionMs.longValue = MusicManager.player.currentPosition.coerceAtLeast(0L)
    }

    // High-precision frame loop tracking smooth playback position with PLL drift correction
    LaunchedEffect(viewModel.currentTrack?.id, lyricsSessionKey, viewModel.effectsState.speed) {
        var anchorPlayerPositionMs = MusicManager.player.currentPosition.coerceAtLeast(0L)
        var anchorFrameNanos = 0L
        while (isActive) {
            val isSliderActive = viewModel.isScrubbing
            val isPlaying = MusicManager.player.isPlaying

            val rawPosition = if (isSliderActive || !isPlaying) {
                viewModel.currentPosition.coerceAtLeast(0L)
            } else {
                val enginePos = MusicManager.player.currentPosition.coerceAtLeast(0L)
                val modelPos = viewModel.currentPosition.coerceAtLeast(0L)
                if (abs(enginePos - modelPos) > 1500L) {
                    modelPos
                } else {
                    enginePos
                }
            }

            if (isSliderActive || !isPlaying) {
                anchorPlayerPositionMs = rawPosition
                anchorFrameNanos = 0L
                if (playbackPositionMs.longValue != rawPosition) {
                    playbackPositionMs.longValue = rawPosition
                }
                delay(if (isSliderActive) 16L else 50L)
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

    val playbackSyncPosition: () -> Int = {
        (playbackPositionMs.longValue + viewModel.lyricsOffset + leadMs)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
    }

    val lyricsFontFamily = rememberLyricsFontFamily(viewModel.lyricsFont)
    val normalTextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = lyricsTextSize.sp,
        lineHeight = (lyricsTextSize * 1.30f).sp,
        fontWeight = FontWeight.Bold,
        fontFamily = lyricsFontFamily
    )
    val accompanimentTextStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = (lyricsTextSize * 0.82f).sp,
        lineHeight = (lyricsTextSize * 0.82f * 1.30f).sp,
        fontFamily = lyricsFontFamily
    )
    val phoneticTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = (lyricsTextSize * 0.55f).sp,
        lineHeight = (lyricsTextSize * 0.55f * 1.30f).sp,
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
                    modifier = Modifier.fillMaxSize()
                ) {
                    val lyricsViewportOffset = remember(maxHeight, lyricsVerticalOffsetFraction) { maxHeight * lyricsVerticalOffsetFraction }

                    CompositionLocalProvider(LocalLayoutDirection provides lyricsLayoutDirection) {
                        key(
                            lyricsSessionKey,
                            syncedLyrics,
                            showTranslations,
                            showPhonetics,
                            isDuetActive,
                            userAlignment,
                            lyricsFontFamily,
                            lyricsTextSize,
                            lyricsLineBlur,
                            lyricsLineSpacingDp,
                            lyricsHorizontalMarginDp,
                            lyricsVerticalOffsetFraction,
                            lyricsActiveScale
                        ) {
                            KaraokeLyricsView(
                                listState = listState,
                                lyrics = syncedLyrics,
                                userAlignment = userAlignment,
                                currentPosition = playbackSyncPosition,
                                onLineClicked = { line ->
                                    val target = line.start.toLong() - viewModel.lyricsOffset
                                    if (viewModel.duration > 0L && target >= viewModel.duration) return@KaraokeLyricsView
                                    val finalTarget = target.coerceAtLeast(0L)
                                    playbackPositionMs.longValue = finalTarget
                                    viewModel.seekTo(finalTarget)
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
                                isScrubbing = viewModel.isScrubbing,
                                lrcBounceEnabled = viewModel.lyricsLrcBounceEnabled,
                                bounceFactor = viewModel.lyricsBounceFactor,
                                lineSpacing = lyricsLineSpacingDp,
                                horizontalMargin = lyricsHorizontalMarginDp,
                                activeScale = lyricsActiveScale,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
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

            if (!entry.romanization.isNullOrBlank()) {
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
            } else if (isDuetEnabled && effectiveSinger == LyricSinger.SINGER_2) {
                val syllables = listOf(
                    KaraokeSyllable(
                        content = cleanText,
                        start = entry.startTime.toInt(),
                        end = lineEnd,
                        phonetic = null
                    )
                )
                lines.add(
                    KaraokeLine.MainKaraokeLine(
                        syllables = syllables,
                        translation = cleanTranslation,
                        alignment = KaraokeAlignment.End,
                        start = entry.startTime.toInt(),
                        end = lineEnd
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

    return contentUnits.mapIndexed { index, unit ->
        KaraokeSyllable(
            content = unit,
            start = start,
            end = end,
            phonetic = phoneticsByUnit[index]
        )
    }
}

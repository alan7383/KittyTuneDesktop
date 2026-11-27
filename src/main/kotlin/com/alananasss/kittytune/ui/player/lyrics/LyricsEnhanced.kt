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

/** Where the side panel puts the sung line, as a fraction of its height from the top. */
private const val SIDEBAR_VERTICAL_OFFSET = 0.36f
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
    isSidebar: Boolean = false,
    contentPosition: com.alananasss.kittytune.data.local.LyricsAlignment = com.alananasss.kittytune.data.local.LyricsAlignment.LEFT,
) {
    val lines = viewModel.lyricsLines
    val isSynced = lines.any { it.startTime > 0 }
    val isWordSyncedFormat = isSynced && viewModel.wordSyncEnabledFor(isFullScreen, isSidebar) && lines.any { it.words.isNotEmpty() }
    val isDuetActive = viewModel.isDuetActiveForTrack(viewModel.uiCurrentTrack, isFullScreen, isSidebar)

    val lyricsLineBlur = lyricsLineBlurOverride ?: viewModel.lineBlurFor(isFullScreen, isSidebar)
    val lyricsTextSize = fontSizeOverride ?: viewModel.fontSizeFor(isFullScreen, isSidebar)
    val defaultSpacing = if (isFullScreen) (lyricsTextSize * 0.34f) else (lyricsTextSize * 0.22f)
    val modeLineSpacing = viewModel.lineSpacingFor(isFullScreen, isSidebar)
    val lyricsLineSpacingDp = when {
        lineSpacingOverride != null -> lineSpacingOverride
        modeLineSpacing > 0f -> modeLineSpacing.dp
        else -> defaultSpacing.dp
    }
    val lyricsHorizontalMargin = viewModel.horizontalMarginFor(isFullScreen, isSidebar)
    val lyricsHorizontalMarginDp = if (lyricsHorizontalMargin > 0f) lyricsHorizontalMargin.dp else (if (isFullScreen) 0.dp else 16.dp)
    val lyricsVerticalOffsetFraction = viewModel.verticalOffsetFor(isFullScreen, isSidebar)
    val configuredScale = viewModel.activeScaleFor(isFullScreen, isSidebar)
    val lyricsActiveScale = if (isFullScreen && configuredScale <= 1.01f) 1.08f else configuredScale

    val textColor = textColorOverride ?: MaterialTheme.colorScheme.onSurface

    val showTranslations = viewModel.translationEnabledFor(isFullScreen, isSidebar) && lines.any { it.translation != null }
    val showPhonetics = viewModel.romanizationEnabledFor(isFullScreen, isSidebar) && lines.any { it.romanization != null }

    val baseLayoutDirection = LocalLayoutDirection.current
    val lyricsLayoutDirection = remember(lines, baseLayoutDirection) {
        val firstLine = lines.firstOrNull { it.text.isNotBlank() }
        if (firstLine != null && isRtlText(firstLine.text)) {
            LayoutDirection.Rtl
        } else {
            baseLayoutDirection
        }
    }

    val lyricsSessionKey = remember(viewModel.uiCurrentTrack?.id, lines.size) {
        (viewModel.uiCurrentTrack?.id ?: "") to lines.map { it.startTime }
    }

    val userAlignment = viewModel.alignmentFor(isFullScreen, isSidebar)

    val splitBacking = viewModel.splitBackingVocalsFor(isFullScreen, isSidebar)
    // Sweep and word-by-word reveal are two looks for the same line-timed lines, and the reveal turns every
    // line into syllables, which the sweep would never reach. When the Sweep animation is chosen it wins.
    val sweepSelected = viewModel.sweepEnabledFor(isFullScreen, isSidebar) && !isWordSyncedFormat
    val revealWords = viewModel.revealWordsFor(isFullScreen, isSidebar) && !sweepSelected
    val syncedLyrics = remember(lines.toList(), isWordSyncedFormat, isDuetActive, userAlignment, splitBacking, revealWords) {
        buildSyncedLyrics(lines, isWordSyncedFormat, isDuetActive, userAlignment, splitBacking, revealWords)
    }

    val leadMs = if (isWordSyncedFormat) WORD_SYNC_LEAD_MS else LRC_LEAD_MS
    val isRemote = viewModel.isRemotePlayback
    val playbackPositionMs = remember(lyricsSessionKey, isRemote) {
        mutableLongStateOf(if (isRemote) viewModel.remotePositionForLyrics() else MusicManager.player.currentPosition.coerceAtLeast(0L))
    }
    val listState = remember(lyricsSessionKey) { LazyListState() }

    // High-precision frame loop tracking smooth playback position with PLL drift correction.
    // The playback position for the words moves on the shared ambient pulse, not every frame: it is what kept the full
    // player redrawing at the display's rate — 240 times a second on a 240 Hz monitor — for as long as a song played
    // (see AmbientClock). Line changes and scrolls the reader starts are animations of their own and keep the full rate.
    val ambientClock = com.alananasss.kittytune.ui.common.LocalAmbientClock.current
    LaunchedEffect(lyricsSessionKey, isRemote, viewModel.effectsState.speed, ambientClock) {
        if (isRemote) {
            while (isActive) {
                playbackPositionMs.longValue = viewModel.remotePositionForLyrics()
                if (viewModel.uiIsPlaying) {
                    com.alananasss.kittytune.ui.common.withAmbientFrameNanos(ambientClock) { }
                } else {
                    delay(50L)
                }
            }
            return@LaunchedEffect
        }
        var anchorPlayerPositionMs = MusicManager.player.currentPosition.coerceAtLeast(0L)
        var anchorFrameNanos = 0L
        while (isActive) {
            val isSliderActive = viewModel.isScrubbing
            val isPlaying = MusicManager.player.isPlaying

            val rawPosition = if (isSliderActive || !isPlaying) {
                viewModel.uiCurrentPosition.coerceAtLeast(0L)
            } else {
                val enginePos = MusicManager.player.currentPosition.coerceAtLeast(0L)
                val modelPos = viewModel.uiCurrentPosition.coerceAtLeast(0L)
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
                val frameNanos = com.alananasss.kittytune.ui.common.withAmbientFrameNanos(ambientClock) { frameTimeNanos -> frameTimeNanos }
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

    val lyricsFontFamily = rememberLyricsFontFamily(viewModel.lyricsFontFor(isFullScreen, isSidebar))
    val lrcBounceEnabled = viewModel.lrcBounceEnabledFor(isFullScreen, isSidebar)
    val bounceFactor = viewModel.bounceFactorFor(isFullScreen, isSidebar)
    val glowFactor = viewModel.glowFactorFor(isFullScreen, isSidebar)
    val fillTransitionWidth = viewModel.fillTransitionWidthFor(isFullScreen, isSidebar)
    val interludeIndicatorStyle = viewModel.instrumentalIndicatorStyleFor(isFullScreen, isSidebar)
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
                PlainLyricsView(
                    viewModel,
                    isFullScreen = isFullScreen,
                    isSidebar = isSidebar,
                    contentPosition = contentPosition,
                )
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
                            contentPosition,
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
                                contentPosition = contentPosition,
                                currentPosition = playbackSyncPosition,
                                onLineClicked = { line ->
                                    val target = line.start.toLong() - viewModel.lyricsOffsetAtLyricTime(line.start.toLong())
                                    if (viewModel.uiDuration > 0L && target >= viewModel.uiDuration) return@KaraokeLyricsView
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
                                startsAtTop = false,
                                keepAliveZone = 72.dp,
                                isScrubbing = viewModel.isScrubbing,
                                lrcBounceEnabled = lrcBounceEnabled,
                                bounceFactor = bounceFactor,
                                lineSpacing = lyricsLineSpacingDp,
                                // The dots between verses at the size of the panel, not of a window.
                                breathingDotsDefaults = if (isSidebar) {
                                    com.alananasss.kittytune.ui.player.lyrics.accompanist.KaraokeBreathingDotsDefaults(size = 8.dp, margin = 6.dp)
                                } else {
                                    com.alananasss.kittytune.ui.player.lyrics.accompanist.KaraokeBreathingDotsDefaults()
                                },
                                horizontalMargin = lyricsHorizontalMarginDp,
                                activeScale = lyricsActiveScale,
                                lineSweepEnabled = sweepSelected,
                                interludeIndicatorStyle = interludeIndicatorStyle,
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
    userAlignment: LyricsAlignment = LyricsAlignment.LEFT,
    /** Backing vocals in round brackets go on a line of their own; see [splitBackingVocals]. */
    splitBacking: Boolean = false,
    /** Line-timed lines light up word by word as they start; see [revealSyllables]. */
    revealWords: Boolean = false,
): SyncedLyrics {
    if (entries.isEmpty()) return SyncedLyrics(emptyList())
    val lines = mutableListOf<ISyncedLine>()
    val hasDuetLayout = isDuetEnabled && hasDuetSecondSinger(entries)

    entries.forEachIndexed { index, entry ->
        if (entry.startTime < 0L) return@forEachIndexed
        if (entry.isInstrumental) return@forEachIndexed
        val cleanText = LyricsUtils.decodeHtmlEntities(entry.text).normalizeLyricCommas()
        if (cleanText.isBlank() && entry.words.isEmpty()) return@forEachIndexed
        val cleanTranslation = entry.translation?.let { LyricsUtils.decodeHtmlEntities(it) }?.takeUnless { LyricsUtils.isDuplicateTranslation(cleanText, it) }

        val effectiveSinger = entry.effectiveDuetSinger(hasDuetLayout)

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

        val hasWords = isWordSynced && entry.words.any { it.text.isNotBlank() }
        if (hasWords) {
            val parts = splitTimedLyricParts(entry.words, splitBacking)
            val mainText = if (parts.hasBracketedVocals) splitBackingVocals(cleanText).first else cleanText

            val wordsForMain = parts.mainWords
            val formattedMainContents = formatLyricWordContents(mainText, wordsForMain)
            val mainSyllables = wordsForMain.mapIndexedNotNull { wIdx, w ->
                val content = formattedMainContents[wIdx].takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
                val start = w.startTime.toInt()
                val end = w.endTime.toInt().coerceAtLeast(start + MIN_KARAOKE_SYLLABLE_DURATION_MS)
                KaraokeSyllable(
                    content = content,
                    start = start,
                    end = end,
                    phonetic = null
                )
            }

            val lineStart = entry.startTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            // Never before the last word ends: a line whose end time falls short of its words (the source's own
            // rounding, or the next line's start) stopped being the sung line while "за ужин" was still being
            // sung, and the view moved on before the fill got there (issue #66).
            val declaredEnd = if (entry.endTime > entry.startTime) {
                entry.endTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            } else {
                lineStart + 4000
            }
            val lineEnd = maxOf(declaredEnd, mainSyllables.maxOfOrNull { it.end } ?: 0)
            if (lineEnd <= lineStart) return@forEachIndexed

            val accompanimentLines = timedBackingVocalLines(parts.backingPhrases, alignment).takeIf { it.isNotEmpty() }

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
            } else {
                val lineStart = entry.startTime.toInt()
                val isRightDuet = isDuetEnabled && effectiveSinger == LyricSinger.SINGER_2
                val (mainText, backing) = if (splitBacking) splitBackingVocals(cleanText) else cleanText to null
                if (!isRightDuet && backing == null && !revealWords) {
                    lines.add(
                        SyncedLine(
                            content = cleanText,
                            translation = cleanTranslation,
                            start = lineStart,
                            end = lineEnd
                        )
                    )
                } else {
                    val lineAlignment = if (isRightDuet) KaraokeAlignment.End else alignment
                    val syllables = when {
                        revealWords -> revealSyllables(mainText, lineStart, lineEnd)
                        // Word by word, so the line can wrap. As one syllable holding the whole line it could not
                        // break anywhere, and a long right-aligned line ran off the left edge (issue #66).
                        isRightDuet && backing == null -> buildWrappingKaraokeSyllables(
                            content = cleanText,
                            romanizedText = entry.romanization ?: "",
                            start = lineStart,
                            end = lineEnd,
                        )
                        else -> instantSyllables(mainText, lineStart, lineEnd)
                    }
                    // Sung over the same line, so it shows while the line does, smaller and under it.
                    val backingLine = backing?.let { text ->
                        val backingStart = if (revealWords) {
                            (lineStart + revealDurationMs(mainText)).coerceAtMost(lineEnd - 1)
                        } else {
                            lineStart
                        }
                        KaraokeLine.AccompanimentKaraokeLine(
                            syllables = if (revealWords) revealSyllables(text, backingStart, lineEnd)
                            else instantSyllables(text, lineStart, lineEnd),
                            translation = null,
                            alignment = lineAlignment,
                            start = lineStart,
                            end = lineEnd,
                            phonetic = null,
                        )
                    }
                    lines.add(
                        KaraokeLine.MainKaraokeLine(
                            syllables = syllables,
                            translation = cleanTranslation,
                            alignment = lineAlignment,
                            start = lineStart,
                            end = lineEnd,
                            phonetic = null,
                            accompanimentLines = backingLine?.let(::listOf),
                        )
                    )
                }
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

    val syllableEnd = (start + 1).coerceAtMost(end)
    return contentUnits.mapIndexed { index, unit ->
        KaraokeSyllable(
            content = unit,
            start = start,
            end = syllableEnd,
            phonetic = phoneticsByUnit[index]
        )
    }
}

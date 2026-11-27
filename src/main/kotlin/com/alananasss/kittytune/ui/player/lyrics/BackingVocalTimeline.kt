package com.alananasss.kittytune.ui.player.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine.AccompanimentKaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable

private const val PREVIEW_MS = 280
private const val FADE_IN_MS = 110
private const val FADE_OUT_MS = 240
private const val PHRASE_GAP_MS = 650
private const val UNSUNG_OPACITY = 0.4f

internal data class TimedLyricParts(
    val mainWords: List<LyricWord>,
    val backingPhrases: List<List<LyricWord>>,
    val hasBracketedVocals: Boolean = false,
)

internal fun splitTimedLyricParts(words: List<LyricWord>, splitBrackets: Boolean): TimedLyricParts {
    val marked = words.filter { it.isBackground }
    if (marked.isEmpty()) {
        if (!splitBrackets) return TimedLyricParts(words, emptyList())
        val (main, backing) = splitBackingWords(words)
        return TimedLyricParts(main, backing, hasBracketedVocals = backing.isNotEmpty())
    }

    val main = words.filterNot { it.isBackground }
    if (main.none { it.text.isNotBlank() }) return TimedLyricParts(words, emptyList())
    val phrases = marked.groupBy { it.backgroundPhrase }.flatMap { (id, wordsInPhrase) ->
        // Older lyrics only identify the individual sung words. Their timestamps still determine the sequence.
        if (id == null) wordsInPhrase.map(::listOf) else listOf(wordsInPhrase)
    }
    return TimedLyricParts(main, phrases)
}

internal fun timedBackingVocalLines(
    phrases: List<List<LyricWord>>,
    alignment: KaraokeAlignment,
): List<AccompanimentKaraokeLine> = phrases.flatMap { phrase ->
    phrase.splitAtVocalPauses(LyricWord::startTime, LyricWord::endTime).mapNotNull { words ->
        val contents = formatLyricWordContents("", words)
        val syllables = words.mapIndexedNotNull { index, word ->
            val content = contents[index].takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val start = word.startTime.coerceIn(0L, Int.MAX_VALUE.toLong() - 1).toInt()
            val end = word.endTime.coerceIn(start.toLong() + 1, Int.MAX_VALUE.toLong()).toInt()
            KaraokeSyllable(content = content, start = start, end = end, phonetic = null)
        }
        if (syllables.isEmpty()) return@mapNotNull null
        AccompanimentKaraokeLine(
            syllables = syllables,
            translation = null,
            alignment = alignment,
            start = syllables.first().start,
            end = syllables.maxOf { it.end },
            phonetic = null,
        )
    }
}

/** One backing row that grows as its phrases are sung and fades as a unit. */
internal class BackingVocalRun(
    val line: AccompanimentKaraokeLine,
    private val previewTimes: Map<KaraokeSyllable, Int>,
    private val holdUntil: Int,
) {
    fun isVisibleAt(time: Int): Boolean =
        time >= line.start - PREVIEW_MS && time.toLong() < holdUntil.toLong() + FADE_OUT_MS

    fun opacityAt(time: Int): Float = 1f - progressSince(time, holdUntil, FADE_OUT_MS)

    fun syllableOpacityAt(syllable: KaraokeSyllable, time: Int): Float {
        val preview = previewTimes.getValue(syllable)
        val fadeIn = progressSince(time, preview, FADE_IN_MS)
        val lightUp = progressSince(time, syllable.start, (syllable.end - syllable.start).coerceIn(1, FADE_IN_MS))
        return fadeIn * (UNSUNG_OPACITY + (1f - UNSUNG_OPACITY) * lightUp)
    }
}

internal fun backingVocalRuns(
    phrases: List<AccompanimentKaraokeLine>,
    mainEnd: Int,
): List<BackingVocalRun> {
    val runs = phrases.splitAtVocalPauses({ it.start.toLong() }, { it.end.toLong() })
    return runs.mapIndexed { index, run ->
        val previewTimes = mutableMapOf<KaraokeSyllable, Int>()
        val syllables = run.flatMapIndexed { phraseIndex, phrase ->
            phrase.syllables.mapIndexed { wordIndex, word ->
                val display = if (phraseIndex < run.lastIndex && wordIndex == phrase.syllables.lastIndex) {
                    word.copy(content = word.content.trimEnd() + " ")
                } else word
                previewTimes[display] = phrase.start - PREVIEW_MS
                display
            }
        }
        val end = run.maxOf { it.end }
        val holdUntil = if (run.size > 1 && index == runs.lastIndex) maxOf(mainEnd, end) else end
        BackingVocalRun(run.first().copy(syllables = syllables, end = end), previewTimes, holdUntil)
    }
}

private fun progressSince(time: Int, start: Int, duration: Int): Float =
    ((time.toLong() - start) / duration.toFloat()).coerceIn(0f, 1f)

private fun <T> List<T>.splitAtVocalPauses(
    startTime: (T) -> Long,
    endTime: (T) -> Long,
): List<List<T>> {
    val groups = mutableListOf<MutableList<T>>()
    var lastEnd = 0L
    for (item in sortedBy(startTime)) {
        if (groups.isEmpty() || startTime(item) - lastEnd >= PHRASE_GAP_MS) {
            groups.add(mutableListOf(item))
            lastEnd = endTime(item)
        } else {
            groups.last().add(item)
            lastEnd = maxOf(lastEnd, endTime(item))
        }
    }
    return groups
}

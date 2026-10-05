package com.alananasss.kittytune.ui.player.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable

/**
 * Pieces for building karaoke lines out of lyrics that are timed by the line only.
 *
 * Such a line has one start and one end. Anything finer, a backing vocal set apart or words lighting up one by
 * one, is made here from the text.
 */

/**
 * The line's backing vocals, written in round brackets, taken out of it: "I'm falling (falling down)" is sung
 * as "I'm falling" with "falling down" behind it. Returns the line as it is, and null, when there is nothing in
 * brackets or nothing outside them, since a line that is all backing vocal is still the line.
 */
internal fun splitBackingVocals(text: String): Pair<String, String?> {
    val backing = BACKING_VOCAL.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
    if (backing.isEmpty()) return text to null
    val main = text.replace(BACKING_VOCAL, " ").replace(SPACES, " ").trim()
    if (main.none { it.isLetterOrDigit() }) return text to null
    return main to backing.joinToString(" ")
}

/** The words of [text], all lit the moment the line starts: a line-timed line that is drawn as karaoke. */
internal fun instantSyllables(text: String, start: Int, end: Int): List<KaraokeSyllable> =
    text.toLyricsWrappingUnits().ifEmpty { listOf(text) }.map { word ->
        KaraokeSyllable(content = word, start = start, end = (start + 1).coerceAtMost(end), phonetic = null)
    }

/**
 * The words of [text] lighting up one after another from [start], quickly, the way a word-timed line does but
 * over a fraction of a second, so the line arrives instead of snapping to white.
 */
internal fun revealSyllables(text: String, start: Int, end: Int): List<KaraokeSyllable> {
    val words = text.toLyricsWrappingUnits().ifEmpty { listOf(text) }
    val step = revealStepMs(words.size)
    return words.mapIndexed { index, word ->
        val wordStart = (start + index * step).coerceAtMost(end - 1).coerceAtLeast(start)
        // Each word takes two steps to fill, so the next one has already begun: a wave, not a ticker.
        val wordEnd = (wordStart + step * 2).coerceAtMost(end).coerceAtLeast(wordStart + 1)
        KaraokeSyllable(content = word, start = wordStart, end = wordEnd, phonetic = null)
    }
}

/** How long [revealSyllables] takes to light [text] completely, for whatever follows it. */
internal fun revealDurationMs(text: String): Int {
    val count = text.toLyricsWrappingUnits().size.coerceAtLeast(1)
    return revealStepMs(count) * (count + 1)
}

private fun revealStepMs(wordCount: Int): Int =
    (REVEAL_TOTAL_MS / wordCount.coerceAtLeast(1)).coerceIn(REVEAL_MIN_STEP_MS, REVEAL_MAX_STEP_MS)

private val BACKING_VOCAL = Regex("""\(([^()]*)\)""")
private val SPACES = Regex("""\s{2,}""")

/** The whole line lights within about this long, however many words it has. */
private const val REVEAL_TOTAL_MS = 700
private const val REVEAL_MIN_STEP_MS = 30
private const val REVEAL_MAX_STEP_MS = 90

package com.alananasss.kittytune.ui.player.lyrics

import com.mpatric.mp3agic.Mp3File
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.regex.Pattern

data class LyricWord(
    val text: String,
    val startTime: Long,
    val endTime: Long
)

data class LyricLine(
    val text: String,
    val startTime: Long,
    val endTime: Long,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null
)

object LyricsUtils {

    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
    
    private val ENHANCED_WORD_PATTERN = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")

    /**
     * Which line is the current one at [positionMs]: the last one that has started.
     *
     * Deliberately not "the first line whose interval contains the position". Word-synced results
     * carry each line's real start and end, so their intervals leave gaps over instrumental breaks
     * and occasionally overlap each other; containment then picked an earlier line than the one
     * actually being sung and the view jumped backwards (issue #33). Last-started is monotone in
     * [positionMs] by construction, which is the property that matters here.
     *
     * @return the line index, or -1 before the first line starts.
     */
    fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int =
        lines.indexOfLast { positionMs >= it.startTime }

    /**
     * Where a click on [line] should move the playhead, or null when it should move nothing.
     *
     * ## Why this is shared, and why it can answer "nowhere"
     *
     * Both lyrics views had their own copy of this arithmetic and the two disagreed: the panel
     * subtracted [lyricsOffsetMs] and the full screen did not, so with a non-zero offset clicking the
     * line the full screen was highlighting jumped somewhere else. Same sum, one place.
     *
     * The clamp is the interesting half. It used to be `coerceIn(0, duration - 1)`, which turns two
     * different situations into a wrong answer:
     *
     *  - **A duration that is not known yet.** A track's duration is nullable in the API and the engine reports
     *    nothing until the stream opens, so `duration` is legitimately 0 for a while. `duration - 1`
     *    coerced up to 0 then made the upper bound *zero*, and every click on every line seeked to the
     *    start of the track. That is the report — "when you click on the text, playback starts from the
     *    very beginning" — reintroduced by the clamp that was meant to fix it.
     *  - **A line that starts after this track ends.** A sheet matched from a longer song carries
     *    timestamps past the end. Clamping those to `duration - 1` seeks to the final millisecond,
     *    where the decoder immediately sees EOF: the track "finishes", and the queue moves on or
     *    repeat-one starts it again from the beginning. Also the report, by a longer route.
     *
     * So an unknown duration clamps nothing, and a line past the end returns null — the caller does
     * not seek and playback simply continues, which is what was asked for.
     *
     * @param lyricsOffsetMs the offset shifting the lyrics against the audio. The position that makes
     *   [line] current is its start minus that offset.
     * @param durationMs this track's length, or 0 while it is still unknown.
     */
    fun seekTargetFor(line: LyricLine, lyricsOffsetMs: Long, durationMs: Long): Long? {
        val target = line.startTime - lyricsOffsetMs
        if (durationMs > 0L && target >= durationMs) return null
        return target.coerceAtLeast(0L)
    }

    fun parseLyricsContent(content: String, totalDurationMs: Long): List<LyricLine> {
        return if (content.trim().startsWith("version:")) {
            parseLyricsFile(content, totalDurationMs)
        } else {
            parseLrc(content, totalDurationMs)
        }
    }

    private fun parseLyricsFile(yamlContent: String, totalDurationMs: Long): List<LyricLine> {
        val parsedLines = mutableListOf<LyricLine>()
        try {
            val yaml = Yaml()
            val data = yaml.load<Map<String, Any>>(yamlContent)
            val linesData = data["lines"] as? List<Map<String, Any>> ?: return emptyList()

            for (lineMap in linesData) {
                val text = lineMap["text"] as? String ?: continue
                val startMs = (lineMap["start_ms"] as? Number)?.toLong() ?: continue
                val endMs = (lineMap["end_ms"] as? Number)?.toLong() ?: totalDurationMs

                val words = mutableListOf<LyricWord>()
                val wordsData = lineMap["words"] as? List<Map<String, Any>>
                if (wordsData != null) {
                    for (wordMap in wordsData) {
                        val wordText = wordMap["text"] as? String ?: continue
                        val wordStartMs = (wordMap["start_ms"] as? Number)?.toLong() ?: continue
                        val wordEndMs = (wordMap["end_ms"] as? Number)?.toLong() ?: endMs
                        words.add(LyricWord(wordText, wordStartMs, wordEndMs))
                    }
                }
                parsedLines.add(LyricLine(text, startMs, endMs, words))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return parsedLines
    }

    fun parseLrc(lrcContent: String, totalDurationMs: Long): List<LyricLine> {
        val lines = lrcContent.split("\n")
        val parsedLines = mutableListOf<ParsedLineTemp>()

        for (line in lines) {
            val matcher = LRC_PATTERN.matcher(line.trim())
            if (matcher.matches()) {
                val min = matcher.group(1)?.toLong() ?: 0
                val sec = matcher.group(2)?.toLong() ?: 0
                val msStr = matcher.group(3) ?: "00"
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()

                val rawText = matcher.group(4)?.trim() ?: ""
                val startTime = (min * 60 * 1000) + (sec * 1000) + ms
                
                val words = mutableListOf<LyricWord>()
                var cleanText = rawText
                if (rawText.contains("<")) {
                    val wordMatcher = ENHANCED_WORD_PATTERN.matcher(rawText)
                    val extractedWords = mutableListOf<LyricWord>()
                    while (wordMatcher.find()) {
                        val wMin = wordMatcher.group(1)?.toLong() ?: 0
                        val wSec = wordMatcher.group(2)?.toLong() ?: 0
                        val wMsStr = wordMatcher.group(3) ?: "00"
                        val wMs = if (wMsStr.length == 2) wMsStr.toLong() * 10 else wMsStr.toLong()
                        val wText = wordMatcher.group(4) ?: ""
                        
                        val wTime = (wMin * 60 * 1000) + (wSec * 1000) + wMs
                        extractedWords.add(LyricWord(wText, wTime, 0L))
                    }
                    if (extractedWords.isNotEmpty()) {
                        cleanText = extractedWords.joinToString("") { it.text }.trim()
                        for (i in extractedWords.indices) {
                            val current = extractedWords[i]
                            val nextTime = if (i < extractedWords.size - 1) extractedWords[i+1].startTime else 0L
                            words.add(current.copy(endTime = nextTime))
                        }
                    }
                }

                if (cleanText.isNotEmpty()) {
                    parsedLines.add(ParsedLineTemp(cleanText, startTime, words))
                }
            }
        }

        if (parsedLines.isEmpty()) return emptyList()

        return parsedLines.mapIndexed { index, current ->
            val nextTime = if (index < parsedLines.size - 1) {
                parsedLines[index + 1].startTime
            } else {
                totalDurationMs
            }
            
            val updatedWords = current.words.map { word ->
                if (word.endTime == 0L) word.copy(endTime = nextTime) else word
            }
            
            LyricLine(current.text, current.startTime, nextTime, updatedWords)
        }
    }

    private data class ParsedLineTemp(val text: String, val startTime: Long, val words: List<LyricWord> = emptyList())

    fun extractLocalLyrics(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val mp3file = Mp3File(filePath)
            if (mp3file.hasId3v2Tag()) {
                val tag = mp3file.id3v2Tag
                tag.lyrics
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

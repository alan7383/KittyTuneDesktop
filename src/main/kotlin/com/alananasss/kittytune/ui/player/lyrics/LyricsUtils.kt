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

// basic holder for a timed line
data class LyricLine(
    val text: String,
    val startTime: Long,
    val endTime: Long,
    val words: List<LyricWord> = emptyList()
)

object LyricsUtils {

    // standard lrc regex: [mm:ss.xx] lyrics
    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
    
    // enhanced LRC word regex: <mm:ss.xx> word
    private val ENHANCED_WORD_PATTERN = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")

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
                // handle 2 digit vs 3 digit milliseconds
                val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()

                val rawText = matcher.group(4)?.trim() ?: ""
                val startTime = (min * 60 * 1000) + (sec * 1000) + ms
                
                // parse enhanced LRC words
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
                        // Calculate end times for words
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

        // calculate end times based on the next line
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

    // --- local extraction ---
    fun extractLocalLyrics(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val mp3file = Mp3File(filePath)
            if (mp3file.hasId3v2Tag()) {
                val tag = mp3file.id3v2Tag
                // mp3agic handles the uslt tag magic
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

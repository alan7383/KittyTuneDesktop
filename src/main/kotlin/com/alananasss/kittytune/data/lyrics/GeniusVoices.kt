package com.alananasss.kittytune.data.lyrics

import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricSinger

/**
 * Who sings which line, read from Genius.
 *
 * Genius heads every part of a song with who sings it: "[Куплет 1: 9mice]", "[Припев: Kai Angel & 9mice]".
 * Synced lyrics mostly carry nothing of the kind, so a song by two artists showed as one voice, and the few
 * sources that do mark voices mark them per take rather than per person: the same artist ended up on both
 * sides, and a song with one singer was drawn as a duet (issue #66). Matched against Genius, each line gets
 * the person who sings it, and a song Genius credits to one voice throughout is not a duet at all.
 */
object GeniusVoices {

    /** One headed part of a Genius page: who sings it (empty when the header does not say) and its lines. */
    data class Section(val singers: List<String>, val lines: List<String>)

    /**
     * The voice for each of [lines], in order, or null when Genius says nothing usable about who sings.
     *
     * Two named voices give a duet: the first artist the track credits (else the first to sing) on the left,
     * the second on the right, both together in the middle. One named voice throughout gives no duet at all:
     * every line [LyricSinger.DEFAULT]. A line with no match on the page takes the voice of the part the
     * matching had reached.
     *
     * @param creditedArtists the track's artists in their credited order, for which voice goes left.
     */
    fun voicesFor(lines: List<LyricLine>, sections: List<Section>, creditedArtists: List<String>): List<LyricSinger>? {
        val named = sections.filter { it.singers.isNotEmpty() }
        if (named.isEmpty() || lines.isEmpty()) return null
        val people = mainSingers(named, creditedArtists)
        if (people.size < 2) return List(lines.size) { LyricSinger.DEFAULT }
        val first = people[0]
        val second = people[1]

        val pageLines = sections.flatMap { section -> section.lines.map { it to section } }
        var cursor = 0
        var lastSection: Section? = null
        val voices = lines.map { line ->
            if (line.isInstrumental || line.text.isBlank()) return@map LyricSinger.DEFAULT
            val match = (cursor until minOf(pageLines.size, cursor + MATCH_WINDOW))
                .maxByOrNull { LyricsMatcher.similarity(line.text, pageLines[it].first) }
                ?.takeIf { LyricsMatcher.similarity(line.text, pageLines[it].first) >= MATCH_THRESHOLD }
            val section = if (match != null) {
                cursor = match + 1
                pageLines[match].second
            } else {
                lastSection ?: pageLines.getOrNull(cursor)?.second
            }
            lastSection = section
            voiceOf(section, first, second)
        }
        return voices.takeIf { it.any { v -> v == LyricSinger.SINGER_1 } && it.any { v -> v == LyricSinger.SINGER_2 } }
            ?: voices.takeIf { it.none { v -> v == LyricSinger.SINGER_2 || v == LyricSinger.BOTH } }?.map { LyricSinger.DEFAULT }
    }

    /** The headed parts of a Genius page, in order. Lines before the first header belong to an unnamed part. */
    fun parseSections(page: String): List<Section> {
        val sections = mutableListOf<Section>()
        var singers = emptyList<String>()
        var lines = mutableListOf<String>()
        for (raw in page.lines()) {
            val text = raw.trim()
            val header = HEADER.matchEntire(text)
            if (header != null) {
                if (lines.isNotEmpty()) sections += Section(singers, lines)
                lines = mutableListOf()
                singers = header.groupValues[1].takeIf { it.isNotBlank() }?.let(::splitNames).orEmpty()
                continue
            }
            if (text.isNotEmpty()) lines += text
        }
        if (lines.isNotEmpty()) sections += Section(singers, lines)
        return sections
    }

    private fun voiceOf(section: Section?, first: String, second: String): LyricSinger {
        val singers = section?.singers?.map(::key).orEmpty()
        val hasFirst = key(first) in singers
        val hasSecond = key(second) in singers
        return when {
            hasFirst && hasSecond -> LyricSinger.BOTH
            hasSecond -> LyricSinger.SINGER_2
            hasFirst -> LyricSinger.SINGER_1
            // Someone else, named: a guest voice in an intro or outro, in the middle.
            singers.isNotEmpty() -> LyricSinger.BOTH
            else -> LyricSinger.DEFAULT
        }
    }

    /**
     * The two people the song is by: the track's credited artists that the page names, in their credited order
     * (so the same one is on the left for the whole song), topped up with whoever sings the most lines. Anyone
     * else is a guest, a few words in an intro or outro, and is drawn in the middle.
     */
    private fun mainSingers(named: List<Section>, credited: List<String>): List<String> {
        val lineCount = mutableMapOf<String, Int>()
        val spelling = mutableMapOf<String, String>()
        for (section in named) for (name in section.singers) {
            spelling.putIfAbsent(key(name), name)
            lineCount[key(name)] = (lineCount[key(name)] ?: 0) + section.lines.size
        }
        val creditedKeys = credited.map(::key).distinct().filter { it in lineCount }
        val byLines = lineCount.entries.sortedByDescending { it.value }.map { it.key }
        return (creditedKeys + byLines).distinct().take(2).map { spelling.getValue(it) }
    }

    fun splitNames(credit: String): List<String> =
        credit.split(NAME_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    private fun key(name: String): String = LyricsMatcher.normalize(name).replace(" ", "")

    /** "[Part: names]" or "[Part]", the names after the colon. */
    private val HEADER = Regex("""^\[[^\]:]*(?::\s*([^\]]*))?]$""")

    private val NAME_SEPARATOR = Regex("""\s*(?:&|,|\s+x\s+|\s+и\s+|\s+and\s+|\s*\+\s*|\s+feat\.?\s+|\s+ft\.?\s+|\s+with\s+)\s*""", RegexOption.IGNORE_CASE)

    /** How far ahead on the page a line is looked for, so a repeated chorus matches its own repeat. */
    private const val MATCH_WINDOW = 12

    private const val MATCH_THRESHOLD = 0.5f
}

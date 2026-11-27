package com.alananasss.kittytune.data.discord

internal data class ParsedDiscordTrack(
    val title: String,
    val artist: String?,
)

private val zeroWidthRegex = Regex("[\u200B-\u200D\uFEFF]")
private val parenBlockRegex = Regex("\\([^()]*\\)")
private val bracketOrParenRegex = Regex("\\([^()]*\\)|\\[[^\\[\\]]*]")
private val multiSpaceRegex = Regex("\\s{2,}")
private val dashSeparatorRegex = Regex("\\s[-\\u2010-\\u2015\\u2212]\\s")
private val versionLabelRegex = Regex(
    "\\b(?:slow(?:ed)?|sped[\\s-]*up|speed[\\s-]*up|nightcore|daycore|reverb|remix|mix|edit|version|remaster(?:ed)?|live|instrumental|acoustic|demo|cover|extended|radio|original|bass[\\s-]*boosted|8d)\\b",
    RegexOption.IGNORE_CASE,
)
private val featurePrefixRegex = Regex(
    "^(?:(?:ft|feat|featuring)\\.?\\s|(?:w/(?!o\\b)|with/)\\s*|with\\.\\s+)",
    RegexOption.IGNORE_CASE,
)
private val withoutRegex = Regex("^w/o\\b", RegexOption.IGNORE_CASE)
private val trailingProducerRegex = Regex("\\s\\[prod(?:uced)?\\.?\\s[^()\\[\\]]+]$", RegexOption.IGNORE_CASE)
private val producerRegex = Regex("\\sprod(?:uced)?\\.?\\s+", RegexOption.IGNORE_CASE)
private val featureInTitleRegex = Regex(
    "\\s[( \\[]?(?:(?:ft|feat|featuring)\\.?\\s+|(?:w/(?!o\\b)|with/)\\s*|with\\.\\s+)",
    RegexOption.IGNORE_CASE,
)

/**
 * Cleans up SoundCloud-style track titles ("Artist - Song (feat. X) [prod. Y]") into
 * separate title and artist strings while preserving version tags like "(Slowed)" or "(Remix)".
 */
internal fun parseDiscordTrackTitle(rawTitle: String?, uploader: String?): ParsedDiscordTrack {
    var normalized = rawTitle?.replace(zeroWidthRegex, "")?.trim().orEmpty()
    if (normalized.isEmpty()) {
        return ParsedDiscordTrack(title = rawTitle?.trim().orEmpty(), artist = uploader)
    }

    var previous: String
    do {
        previous = normalized
        normalized = normalized.replace(parenBlockRegex) { match ->
            val inner = match.value.substring(1, match.value.length - 1).trim()
            if (versionLabelRegex.containsMatchIn(inner) ||
                featurePrefixRegex.containsMatchIn(inner) ||
                withoutRegex.containsMatchIn(inner)
            ) {
                match.value
            } else {
                ""
            }
        }.replace(multiSpaceRegex, " ").trim()
    } while (normalized != previous)

    if (normalized.isEmpty()) {
        return ParsedDiscordTrack(title = rawTitle?.trim().orEmpty(), artist = uploader)
    }

    val sepMatch = dashSeparatorRegex.find(normalized)
    val sepIndex = sepMatch?.range?.first ?: -1
    var artist: String? = if (sepIndex > 0) normalized.substring(0, sepIndex).trim() else null
    var song = if (sepIndex > 0 && sepMatch != null) {
        normalized.substring(sepMatch.range.last + 1).trim()
    } else {
        normalized
    }
    if (song.isEmpty()) {
        return ParsedDiscordTrack(title = rawTitle?.trim().orEmpty(), artist = uploader)
    }

    if (sepIndex > 0 && !artist.isNullOrEmpty()) {
        val collaborators = artist.split('+').map { it.trim() }
        if (collaborators.size > 1 && collaborators.all { it.isNotEmpty() }) {
            artist = "${collaborators.first()} feat. ${collaborators.drop(1).joinToString(", ")}"
        }
    }

    song = song.replace(trailingProducerRegex, "").trim()

    producerRegex.find(song)?.let { prod ->
        val tail = song.substring(prod.range.last + 1)
        val nextFeature = featureInTitleRegex.find(tail)
        val credit = if (nextFeature != null) tail.substring(0, nextFeature.range.first) else tail
        val versions = bracketOrParenRegex.findAll(credit)
            .map { it.value }
            .filter { versionLabelRegex.containsMatchIn(it) }
            .toList()
        if (credit.trim().isNotEmpty()) {
            val head = song.substring(0, prod.range.first).trim()
            val prefix = (listOf(head) + versions).filter { it.isNotEmpty() }.joinToString(" ")
            song = prefix + if (nextFeature != null) tail.substring(nextFeature.range.first) else ""
        }
    }

    featureInTitleRegex.find(song)?.let { feat ->
        val credit = song.substring(feat.range.last + 1).trim()
        val opener = feat.value.trimStart().firstOrNull()
        val closer = when (opener) {
            '(' -> ')'
            '[' -> ']'
            else -> null
        }
        var featureText = credit
        var suffix = ""
        if (closer != null && opener != null) {
            var depth = 1
            var closingIndex = -1
            for (i in credit.indices) {
                when (credit[i]) {
                    opener -> depth++
                    closer -> depth--
                }
                if (depth == 0) {
                    closingIndex = i
                    break
                }
            }
            if (closingIndex < 0) {
                return ParsedDiscordTrack(title = song, artist = artist ?: uploader)
            }
            featureText = credit.substring(0, closingIndex).trim()
            suffix = credit.substring(closingIndex + 1).trim()
        }

        val versions = mutableListOf<String>()
        val featuredArtist = featureText.replace(bracketOrParenRegex) { match ->
            if (versionLabelRegex.containsMatchIn(match.value)) {
                versions += match.value
                ""
            } else {
                match.value
            }
        }.trim()

        val baseTitle = song.substring(0, feat.range.first).trim()
        val mainArtist = artist ?: uploader
        if (baseTitle.isNotEmpty() && featuredArtist.isNotEmpty() && !mainArtist.isNullOrBlank() &&
            hasBalancedBrackets(featuredArtist)
        ) {
            song = (listOf(baseTitle) + versions + listOf(suffix)).filter { it.isNotEmpty() }.joinToString(" ")
            val separator = if (" feat. " in mainArtist) ", " else " feat. "
            artist = "$mainArtist$separator$featuredArtist"
        }
    }

    return ParsedDiscordTrack(title = song, artist = artist ?: uploader)
}

private fun hasBalancedBrackets(text: String): Boolean {
    val stack = ArrayDeque<Char>()
    for (ch in text) {
        when (ch) {
            '(' -> stack.addLast(')')
            '[' -> stack.addLast(']')
            ')', ']' -> if (stack.removeLastOrNull() != ch) return false
        }
    }
    return stack.isEmpty()
}

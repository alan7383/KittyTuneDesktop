package com.alananasss.kittytune.domain

private const val SPACE = """[\s\p{Z}]"""
private val confirmedSeparator = Regex("""$SPACE+-$SPACE*|$SPACE*-$SPACE+|$SPACE*[–—]$SPACE*""")
private val inferredSeparator = Regex("""$SPACE+[-–—]$SPACE+""")
private val accountSeparator = Regex("""(?i)\s*(?:&|,|\+|\s+x\s+|\s+and\s+)\s*""")
private val nameSpaces = Regex("""$SPACE+""")
private val soundCloudUrl = Regex("""(?i)^https?://(?:[a-z0-9-]+\.)*soundcloud\.com(?:[/?#]|$)""")
private val versionOrCredit = Regex(
    """(?ix)
        (?:feat(?:uring)?|ft|prod(?:uced)?(?:\s+by)?)\.?\s+.+
        |
        (?:\d{4}\s+)?
        (?:
           remaster(?:ed)? | remix | live | (?:original|extended|radio|club|album)?\s*(?:mix|edit)
           | instrumental | acoustic | demo | clean | explicit | sped\s+up | slowed(?:\s*\+\s*reverb)?
           | (?:official\s+)?(?:audio|video)
           | (?:vol(?:ume)?\.?|pt\.?|part|episode|disc|chapter)\s*[\divxlcdm]+
        )
        (?:\s+version|\s+\d{4}|\s+(?:at|from)\s+.+)?
    """.trimIndent(),
)

internal fun usesSoundCloudMetadata(urn: String?, url: String?): Boolean =
    (urn.isNullOrBlank() || urn.startsWith("soundcloud:", ignoreCase = true)) &&
        (url.isNullOrBlank() || url.startsWith("soundcloud:", ignoreCase = true) || soundCloudUrl.containsMatchIn(url))

private fun sameArtistName(first: String, second: String): Boolean =
    first.trim().replace(nameSpaces, " ").equals(second.trim().replace(nameSpaces, " "), ignoreCase = true)

/** A matching credit lets us remove the prefix even for artist names that contain a dash. */
internal fun withoutArtistPrefix(title: String?, vararg artists: String?): String? {
    if (title.isNullOrBlank() || artists.all { it.isNullOrBlank() }) return title
    var remainder: String? = null
    for (separator in confirmedSeparator.findAll(title)) {
        val prefix = title.substring(0, separator.range.first)
        if (artists.any { !it.isNullOrBlank() && sameArtistName(prefix, it) }) {
            val suffix = title.substring(separator.range.last + 1).trim()
            if (suffix.isNotEmpty()) remainder = suffix
        }
    }
    return remainder ?: title
}

internal data class SoundCloudTitleCredit(val artist: String, val title: String)

internal fun soundCloudTitleCredit(
    title: String?,
    uploader: String?,
    creditedArtist: String?,
    hasStructuredCredits: Boolean,
): SoundCloudTitleCredit? {
    if (title.isNullOrBlank()) return null
    val knownArtist = creditedArtist ?: uploader
    val knownTitle = withoutArtistPrefix(title, knownArtist)
    if (knownArtist != null && knownTitle != null && knownTitle != title) {
        return SoundCloudTitleCredit(knownArtist, knownTitle)
    }

    // A publisher credit equal to the account name can be the reuploader's default credit.
    if (hasStructuredCredits || (creditedArtist != null && (uploader == null || !sameArtistName(creditedArtist, uploader)))) {
        return null
    }

    val separator = inferredSeparator.find(title) ?: return null
    val artist = title.substring(0, separator.range.first).trim()
    val track = title.substring(separator.range.last + 1).trim()
    val unbracketed = track.removeSurrounding("(", ")").removeSurrounding("[", "]").trim()
    if (artist.none { it.isLetterOrDigit() } || track.none { it.isLetterOrDigit() } || versionOrCredit.matches(unbracketed)) {
        return null
    }
    // Keep the account's spelling for solo tracks uploaded from a shared artist profile.
    val accountArtist = uploader?.split(accountSeparator)?.map { it.trim() }
        ?.firstOrNull { sameArtistName(it, artist) }
    return SoundCloudTitleCredit(accountArtist ?: artist, track)
}

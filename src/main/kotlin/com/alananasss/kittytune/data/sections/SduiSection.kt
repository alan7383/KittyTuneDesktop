package com.alananasss.kittytune.data.sections

/**
 * One shelf of a server-driven browse screen, reduced to what a desktop list needs (issue #33).
 *
 * SoundCloud's own layout language has sixteen-odd section kinds, versioned, and they add to it whenever they
 * ship a new shelf. Mirroring that vocabulary here would mean tracking their releases forever, so this keeps the
 * three distinctions that change how something is *drawn* and throws the rest away: is it a group of shelves, and
 * if it is a shelf, are its tiles wide, square or in a row.
 *
 * @see SectionsParser
 */
sealed interface SduiSection {

    /** A titled group of shelves — how the category page bundles trending, playlists and albums. */
    data class Group(val title: String?, val sections: List<SduiSection>) : SduiSection

    data class Shelf(val title: String?, val items: List<SduiItem>, val style: Style) : SduiSection

    /** How wide the tiles are, which is the only thing their sixteen kinds actually disagree about. */
    enum class Style { LIST, CAROUSEL, GRID }
}

/**
 * One tile.
 *
 * @param query what to send as `q` when this is opened. For a category tile this is the whole point — it is how
 *   the grid navigates into a page — and for content it is the urn.
 * @param kind what pressing it should do. Inferred from the urn rather than declared, because their items carry
 *   the type in three different places depending on which shelf they came from.
 */
data class SduiItem(
    val urn: String?,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val query: String?,
    val kind: Kind,
) {
    enum class Kind { TRACK, PLAYLIST, USER, CATEGORY }

    /** Stable enough for a list key, and unique within one screen. */
    val key: String get() = urn ?: "$kind:$title"
}

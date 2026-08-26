package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.str

/**
 * Our own names for SoundCloud's home selections.
 *
 * `mixed-selections` returns editorial titles that are English whatever `Accept-Language` says — I
 * checked the same account with `en-US` and `fr-FR` and got byte-identical titles back — so half the
 * home page stayed in English however the app was set (issue #33). The titles are ours to write, and
 * the selections carry stable urns to key them off, which is safer than matching their English text.
 *
 * An unmapped selection keeps whatever SoundCloud called it: a new one appearing in English is
 * better than it disappearing, and it tells us there is a name to add.
 */
object SoundCloudSelectionLabels {

    /** Matched on a fragment of the urn, because several of them end in the listener's own id. */
    private val TITLES: List<Pair<String, String>> = listOf(
        "personalized-tracks" to "sc_selection_personalized_tracks",
        "your-moods" to "sc_selection_your_moods",
        "artist-stations" to "home_discover_stations",
        "buzzing" to "sc_selection_buzzing",
        "friends-of-friends" to "home_section_new_crew",
        "trending-by-genre" to "sc_selection_trending_by_genre",
        "made-for-you" to "sc_selection_made_for_you",
        "personalised-curated-global" to "sc_selection_curated",
        "personalized-albums" to "home_albums_for_you",
        "liked-by" to "home_liked_by_section_title",
    )

    private val SUBTITLES: Map<String, String> = mapOf(
        "friends-of-friends" to "home_section_new_crew_sub",
        "liked-by" to "home_liked_by_section_subtitle",
    )

    private fun keyFor(urn: String?, table: List<Pair<String, String>>): String? {
        val id = urn ?: return null
        return table.firstOrNull { (fragment, _) -> id.contains(fragment, ignoreCase = true) }?.second
    }

    /** @return our name for this selection, or [fallback] when we have not named it. */
    fun title(urn: String?, fallback: String?): String? =
        keyFor(urn, TITLES)?.let { str(it) } ?: fallback

    /**
     * @return our subtitle for this selection. Null both when we have no subtitle for it and when
     *   we have renamed the title: an English subtitle under a translated title reads worse than no
     *   subtitle at all.
     */
    fun subtitle(urn: String?, fallback: String?): String? {
        val id = urn ?: return fallback
        SUBTITLES.entries.firstOrNull { id.contains(it.key, ignoreCase = true) }?.let { return str(it.value) }
        return if (keyFor(id, TITLES) != null) null else fallback
    }
}

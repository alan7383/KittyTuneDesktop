package com.alananasss.kittytune.data.yandex

import com.alananasss.kittytune.data.catalog.CatalogSong
import com.alananasss.kittytune.data.catalog.CatalogSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads one track out of a Yandex Music search response (issue #33).
 *
 * Their shape differs from Apple's in three ways that matter, all of them handled here rather than at the call
 * site: the artist is a list, the album is a list, and the cover is a template with `%%` where the size goes.
 *
 * Produces a [CatalogSong], so the results list, the resolver and the "cannot be played directly" guarantee
 * are the same ones Apple Music already goes through.
 */
object YandexTrack {

    /**
     * @return the track, or null when it is missing what would be needed to find it on a source that streams,
     *   or when Yandex has marked it unavailable — an entry nothing can be done with is worse than one fewer
     *   row.
     */
    fun from(entry: JsonObject): CatalogSong? {
        // `available` false is a track Yandex itself will not serve: rights lapsed, region, taken down. It is
        // still findable elsewhere, so it is kept — the whole point of reading this catalogue is that the song
        // exists somewhere. Only a missing title or artist disqualifies an entry.
        val title = entry.string("title") ?: return null
        val artist = entry.names("artists").firstOrNull() ?: return null
        return CatalogSong(
            source = CatalogSource.YANDEX_MUSIC,
            // Their ids are sometimes numbers and sometimes strings, and a track id can be composite
            // ("10994777:1193829"). Taken as text, since nothing here does arithmetic with it.
            id = entry.string("id") ?: entry.string("realId") ?: return null,
            title = title,
            // Every credited artist, joined, because a feature that is only in the second name is exactly the
            // sort of thing that makes a title unfindable — and the matcher is happier with more of the credit.
            artist = entry.names("artists").joinToString(", "),
            album = entry.titles("albums").firstOrNull(),
            durationMs = entry.string("durationMs")?.toLongOrNull() ?: 0L,
            artworkUrl = entry.string("coverUri")?.let { cover(it) },
            releaseDate = entry.releaseDate(),
        )
    }

    /**
     * Their cover URIs end in `%%`, which the caller is expected to replace with a size, and carry no scheme.
     * 400 is what a search row needs; asking for 1000 on every row of a scrolling list is a lot of bytes for
     * something 48 dp wide.
     */
    private fun cover(uri: String): String = "https://" + uri.replace("%%", "400x400")

    /** The album's release year, when the album carries one — their tracks do not. */
    private fun JsonObject.releaseDate(): String? =
        (this["albums"] as? JsonArray)?.firstOrNull()?.let { it as? JsonObject }?.string("year")

    private fun JsonObject.names(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("name") }

    private fun JsonObject.titles(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.string("title") }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
}

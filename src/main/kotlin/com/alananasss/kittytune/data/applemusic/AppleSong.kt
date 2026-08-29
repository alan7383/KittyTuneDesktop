package com.alananasss.kittytune.data.applemusic

import com.alananasss.kittytune.data.catalog.CatalogSong
import com.alananasss.kittytune.data.catalog.CatalogSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads one entry of an Apple Music catalogue response.
 *
 * Produces a [CatalogSong] rather than a type of its own: Apple and Yandex are the same shape of thing — a
 * catalogue we can read and cannot stream from — so they share one type, one results list and one resolver.
 * Writing the second one as a copy of the first with a different word in it is the mistake this issue has
 * already cost three rounds of elsewhere (issue #33).
 */
object AppleSong {

    /**
     * @return the song, or null when the entry is missing the two fields that make it findable anywhere else.
     *   A result nothing can be done with is worse than one fewer result.
     */
    fun from(entry: JsonObject): CatalogSong? {
        val attributes = entry["attributes"] as? JsonObject ?: return null
        val title = attributes.string("name") ?: return null
        val artist = attributes.string("artistName") ?: return null
        return CatalogSong(
            source = CatalogSource.APPLE_MUSIC,
            id = entry.string("id") ?: return null,
            title = title,
            artist = artist,
            album = attributes.string("albumName"),
            durationMs = attributes.string("durationInMillis")?.toLongOrNull() ?: 0L,
            artworkUrl = (attributes["artwork"] as? JsonObject)?.let { artwork(it) },
            releaseDate = attributes.string("releaseDate"),
        )
    }

    /**
     * Apple gives a template with `{w}` and `{h}` in it rather than a URL, and expects the caller to choose.
     * 600 is what a row of covers needs on a high-density display without asking for the 3000 px original
     * every time a search scrolls.
     */
    private fun artwork(artwork: JsonObject): String? =
        artwork.string("url")?.replace("{w}", "600")?.replace("{h}", "600")

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
}

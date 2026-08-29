package com.alananasss.kittytune.data.applemusic

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One song as the Apple Music catalogue describes it.
 *
 * Deliberately not a [com.alananasss.kittytune.domain.Track]: a Track is something the player can be
 * handed, and nothing here can be played. Keeping the two types apart is what stops an Apple result
 * reaching the queue by accident — the only way one becomes a Track is through [AppleMusicFallback],
 * which finds the same song somewhere that streams (issue #33).
 */
data class AppleSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val artworkUrl: String?,
    val releaseDate: String?,
) {
    companion object {

        /**
         * @return the song, or null when the entry is missing the two fields that make it findable
         *   anywhere else. A result nothing can be done with is worse than one fewer result.
         */
        fun from(entry: JsonObject): AppleSong? {
            val attributes = entry["attributes"] as? JsonObject ?: return null
            val title = attributes.string("name") ?: return null
            val artist = attributes.string("artistName") ?: return null
            return AppleSong(
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
         * Apple gives a template with `{w}` and `{h}` in it rather than a URL, and expects the caller to
         * choose. 600 is what a row of covers needs on a high-density display without asking for the
         * 3000 px original every time a search scrolls.
         */
        private fun artwork(artwork: JsonObject): String? =
            artwork.string("url")?.replace("{w}", "600")?.replace("{h}", "600")

        private fun JsonObject.string(key: String): String? =
            runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}

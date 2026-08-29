package com.alananasss.kittytune.data.sections

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Reads SoundCloud's server-driven browse screens (issue #33).
 *
 * ## What this is and how it was found
 *
 * "Regarde dans le code source SoundCloud mais pouvoir mettre ça pour toutes les catégories qu'a l'app Android
 * […] tu mets tout exactement comme Android mais adapté desktop. Ça doit avoir le même endpoint que SoundCloud
 * mobile."
 *
 * It does, and the endpoint is not a list of genres — it is a whole screen. From their decompiled client
 * (`DefaultSectionsService`, `SectionsRequestBuildersKt`):
 *
 * ```
 * GET /search/query?q=<query>&layout=soundcloud:layouts:search_default&version=v10&limit=30&device_time=…
 * GET /search/query?q=<category urn>&layout=soundcloud:layouts:category_page&version=v10&device_time=…
 * ```
 *
 * on the **mobile** host with `ApiMode.PRIVATE`, i.e. the listener's own OAuth token —
 * `api-mobile.soundcloud.com` answers 401 to an anonymous client id, which is how that was confirmed.
 *
 * The first layout is the "Ambiances" grid he screenshotted. The second is a category's own page, with the
 * trending / playlists / albums / profiles sections. Both come back as the same envelope:
 *
 * ```
 * { "sections": [ { "data": { … } }, … ], "entities": { "<urn>": { … } }, "_links": { "next": { "href": … } } }
 * ```
 *
 * ## Why it is parsed loosely on purpose
 *
 * The section kinds their client knows about are `banner`, `caption_carousel`, `carousel`, `container`,
 * `content_wall`, `correction`, `divider`, `gallery`, `grid`, `headline`, `horizontal_menu`, `image_banner`,
 * `page_header`, `pair`, `pills`, `placeholder` and more — a whole layout language, versioned `v8`/`v9`/`v10`,
 * that they extend whenever they ship a new shelf. A parser that insisted on knowing all of them would break the
 * browse screen the next time SoundCloud added one.
 *
 * So this recognises the handful that carry content, turns them into [SduiSection], and *drops* everything else
 * without complaint. An unrecognised shelf costs one shelf; a strict parser would cost the screen.
 */
object SectionsParser {

    /** A parsed screen: the shelves that were understood, and where to ask for more. */
    data class Screen(val sections: List<SduiSection>, val nextHref: String?) {
        val isEmpty: Boolean get() = sections.isEmpty()
    }

    fun parse(root: JsonObject?): Screen {
        if (root == null) return Screen(emptyList(), null)
        val entities = root.obj("entities") ?: JsonObject()
        val sections = root.array("sections").orEmpty()
            .mapNotNull { section(it, entities) }
        return Screen(sections, root.obj("_links")?.obj("next")?.string("href"))
    }

    private fun section(element: JsonElement, entities: JsonObject): SduiSection? {
        val data = element.asObjectOrNull()?.obj("data") ?: return null
        val title = data.string("title") ?: data.obj("header")?.string("title")

        // A container nests further sections, which is how the category page groups its shelves. Flattened,
        // because a desktop list of shelves has no use for the nesting their phone layout needs.
        data.array("sections")?.let { nested ->
            val inner = nested.mapNotNull { section(it, entities) }
            return if (inner.isEmpty()) null else SduiSection.Group(title, inner)
        }

        val items = (data.array("items") ?: data.array("results") ?: data.array("pills"))
            .orEmpty()
            .mapNotNull { item(it, entities) }
        if (items.isEmpty()) return null

        return SduiSection.Shelf(
            title = title,
            items = items,
            // A gallery or a grid is square artwork in rows; a carousel is a horizontal strip; everything else
            // reads fine as a list. The distinction is only about how wide the tiles are.
            style = when (data.string("kind") ?: data.string("type")) {
                "grid", "gallery", "content_wall" -> SduiSection.Style.GRID
                "carousel", "caption_carousel" -> SduiSection.Style.CAROUSEL
                else -> SduiSection.Style.LIST
            },
        )
    }

    /**
     * One tile.
     *
     * Their items either carry the content inline or point at the `entities` map by urn — both shapes appear in
     * the same response, so both are followed. An item that resolves to neither is skipped rather than shown as
     * a blank tile.
     */
    private fun item(element: JsonElement, entities: JsonObject): SduiItem? {
        val obj = element.asObjectOrNull() ?: return null
        val data = obj.obj("data") ?: obj

        val urn = data.string("urn") ?: data.string("raw_urn") ?: data.string("track_urn")
            ?: data.string("playlist_urn")
        val resolved = urn?.let { entities.obj(it) } ?: data

        val title = resolved.string("title")
            ?: resolved.string("username")
            ?: data.string("title")
            ?: data.string("text")
            ?: return null

        return SduiItem(
            urn = urn,
            title = title,
            subtitle = resolved.obj("user")?.string("username") ?: resolved.string("subtitle"),
            artworkUrl = resolved.string("artwork_url")
                ?: resolved.string("avatar_url")
                ?: data.imageUrl(),
            // Where pressing it goes. A category tile carries a query rather than an urn, which is what makes
            // the grid navigable at all: the next request is this string in `q`.
            query = data.obj("action_link")?.string("href")
                ?: data.string("query")
                ?: urn,
            kind = when {
                resolved.has("track_format") || urn?.contains(":tracks:") == true -> SduiItem.Kind.TRACK
                urn?.contains(":playlists:") == true || urn?.contains("system-playlists") == true ->
                    SduiItem.Kind.PLAYLIST
                urn?.contains(":users:") == true -> SduiItem.Kind.USER
                else -> SduiItem.Kind.CATEGORY
            },
        )
    }

    /** Their tiles carry six sizes and two themes of the same picture; any of them is a picture. */
    private fun JsonObject.imageUrl(): String? =
        string("image_medium_dark")
            ?: string("image_medium_light")
            ?: string("image_large_dark")
            ?: string("image_large_light")
            ?: string("image_landscape")
            ?: string("main_image")

    // ---- the small amount of Gson plumbing this needs ------------------------------------------

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(key: String): List<JsonElement>? =
        (get(key)?.takeIf { it.isJsonArray }?.asJsonArray as JsonArray?)?.toList()

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
}

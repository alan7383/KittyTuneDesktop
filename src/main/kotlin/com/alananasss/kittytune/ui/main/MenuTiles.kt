package com.alananasss.kittytune.ui.main

import com.alananasss.kittytune.data.local.PlayerPreferences

/**
 * The tiles an options menu can offer, and the arrangement the reader has given them (issue #33).
 *
 * "I would also add to the menu where to shuffle and so on, as in gboard, that you can move the
 * tiles, if you need one thing in the first place, then just move it, convenient and practical. You
 * can also add hiding unnecessary buttons to hide, and enabling them in the settings."
 *
 * The catalogue exists because the menu itself cannot answer "what tiles are there?": which ones it
 * builds depends on the track — a local file has no comments, a station has no details, a track
 * outside a playlist has nothing to be removed from. A settings screen listing what can be hidden
 * needs the whole set, so the set is written down here and the menu draws its ids from it.
 */
internal object MenuTiles {

    /** An id, and the string key that names it wherever it has to be named without being drawn. */
    data class Tile(val id: String, val labelKey: String)

    // Ids are never shown and never translated: they are what gets stored, so they have to outlive
    // any relabelling. The built-in order of these lists is also the default order of the menu.
    val TRACK = listOf(
        Tile("like", "player_like_action"),
        Tile("shuffle", "menu_shuffle"),
        Tile("repeat", "menu_repeat"),
        Tile("play_next", "menu_play_next"),
        Tile("add_queue", "menu_add_queue"),
        Tile("comments", "menu_comments"),
        Tile("repost", "menu_repost"),
        Tile("details", "menu_details"),
        Tile("lyrics", "player_lyrics"),
        Tile("add_playlist", "menu_add_playlist"),
        Tile("go_album", "menu_go_album"),
        Tile("go_artist", "menu_go_artist"),
        Tile("edit_track", "menu_edit_track"),
        Tile("track_radio", "menu_track_radio"),
        Tile("share", "btn_share"),
        Tile("remove_from_playlist", "menu_remove"),
        Tile("sleep_timer", "sleep_timer_title"),
        Tile("trim", "trim_title"),
        Tile("download", "btn_download"),
    )

    val PLAYLIST = listOf(
        Tile("play", "btn_play"),
        Tile("shuffle", "btn_shuffle"),
        Tile("play_next", "menu_play_next"),
        Tile("add_queue", "menu_add_queue"),
        Tile("add_playlist", "menu_add_playlist"),
        Tile("details", "menu_playlist_details"),
        Tile("go_artist", "menu_go_artist"),
        Tile("share", "btn_share"),
        Tile("download", "btn_download"),
    )

    fun catalogue(menu: String): List<Tile> =
        if (menu == PlayerPreferences.MENU_PLAYLIST) PLAYLIST else TRACK

    /**
     * Applies an arrangement to the tiles a menu actually has this time.
     *
     * @param present the tiles the menu built for this track, in their built-in order.
     * @param order the ids that were moved, in the order they were moved into. Ids absent from it
     *   keep their built-in position *after* the arranged ones, so a tile shipped in a later version
     *   turns up at the end rather than not at all.
     * @param hidden ids to drop entirely.
     */
    fun <T> arrange(
        present: List<T>,
        order: List<String>,
        hidden: Set<String>,
        idOf: (T) -> String,
    ): List<T> {
        val visible = present.filterNot { idOf(it) in hidden }
        if (order.isEmpty()) return visible
        val rank = order.withIndex().associate { (i, id) -> id to i }
        // Unranked tiles sort by where they already were, after every ranked one — so they keep the
        // built-in sequence among themselves instead of being shuffled by the sort.
        return visible
            .withIndex()
            .sortedBy { (position, tile) -> rank[idOf(tile)] ?: (order.size + position) }
            .map { it.value }
    }

    /**
     * The arrangement to store after a drag, expressed over the whole catalogue rather than over the
     * tiles that happened to be on screen.
     *
     * A menu shows a subset, so writing that subset back would be read later as "these come first and
     * everything else after", quietly reordering tiles the reader never touched. Splicing the move
     * into the full catalogue keeps the rest where it was.
     */
    fun moved(menu: String, stored: List<String>, fromId: String, toId: String): List<String> {
        val full = (stored + catalogue(menu).map { it.id }).distinct().toMutableList()
        val from = full.indexOf(fromId)
        val to = full.indexOf(toId)
        if (from < 0 || to < 0 || from == to) return stored
        full.add(to, full.removeAt(from))
        return full
    }
}

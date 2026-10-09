package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.ui.home.SearchSource

/**
 * Which sources the search offers, and in what order (round 3 of the tester's list, 39).
 *
 * SoundCloud comes first, then the catalogues in the order people use them, and YouTube Music stands for YouTube as
 * well: a plain YouTube search was a second copy of the same results. The reader can move each source and hide the
 * ones they never use; both are kept here, in the order they were set.
 */
object SearchSourceOrder {

    private const val KEY_ORDER = "search_source_order"
    private const val KEY_HIDDEN = "search_source_hidden"

    /** The sources in the search menu, in their default order. YouTube is folded into YouTube Music. */
    val defaults: List<SearchSource> = listOf(
        SearchSource.SOUNDCLOUD,
        SearchSource.SPOTIFY,
        SearchSource.APPLE_MUSIC,
        SearchSource.YANDEX_MUSIC,
        SearchSource.DEEZER,
        SearchSource.TIDAL,
        SearchSource.QOBUZ,
        SearchSource.YOUTUBE_MUSIC,
    )

    /** Every source the menu can show, in the order the reader gave them, with ones not yet placed at the end. */
    fun ordered(): List<SearchSource> {
        val saved = Prefs.getString(KEY_ORDER, null)?.split(',')
            ?.mapNotNull { name -> runCatching { SearchSource.valueOf(name) }.getOrNull() }
            .orEmpty()
        return (saved + defaults).distinct().filter { it in defaults }
    }

    fun hidden(): Set<SearchSource> = Prefs.getString(KEY_HIDDEN, null)?.split(',')
        ?.mapNotNull { name -> runCatching { SearchSource.valueOf(name) }.getOrNull() }
        ?.toSet()
        .orEmpty()

    /** What the menu shows: the order, minus what is hidden. Never empty: SoundCloud cannot be hidden. */
    fun visible(): List<SearchSource> {
        val hiddenNow = hidden()
        return ordered().filter { it !in hiddenNow || it == SearchSource.SOUNDCLOUD }
    }

    fun save(order: List<SearchSource>, hiddenSources: Set<SearchSource>) {
        Prefs.putString(KEY_ORDER, order.joinToString(",") { it.name })
        Prefs.putString(KEY_HIDDEN, hiddenSources.filter { it != SearchSource.SOUNDCLOUD }.joinToString(",") { it.name })
    }

    /** The source a search should use: the one asked for, or SoundCloud when that one has been hidden. */
    fun fallbackFor(source: SearchSource): SearchSource = if (source in visible()) source else SearchSource.SOUNDCLOUD
}

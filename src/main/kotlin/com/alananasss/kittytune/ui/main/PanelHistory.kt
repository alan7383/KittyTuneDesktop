package com.alananasss.kittytune.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Back and forward inside the right-hand panel, on the mouse's side buttons.
 *
 * With the pointer over the panel, back returns to the tab it was on before (and, on the info tab, to the half it had
 * open: comments or lyrics), forward undoes that. Anywhere else the buttons keep walking the pages, as before. When
 * the panel has nowhere to go back to, the press falls through to the pages, so the buttons are never dead over it.
 */
internal object PanelHistory {

    /** Where the panel is: its tab, and which half of the info tab is open (null until the info tab has said). */
    data class Place(val tab: NowPlayingTab, val lyricsHalf: Boolean? = null)

    private val back = ArrayDeque<Place>()
    private val forward = ArrayDeque<Place>()

    var present: Place = Place(NowPlayingTab.TRACK)
        private set

    /** Bumped when history moves the panel, so the tab row and the info tab put themselves where [present] says. */
    var movedTick by mutableIntStateOf(0)
        private set

    /** Whether the pointer is over the panel. Written by the panel, read when a side button is pressed. */
    @Volatile
    var pointerInside = false

    val canGoBack: Boolean get() = back.isNotEmpty()
    val canGoForward: Boolean get() = forward.isNotEmpty()

    fun noteTab(tab: NowPlayingTab) {
        if (tab == present.tab) return
        record(Place(tab, present.lyricsHalf.takeIf { tab == NowPlayingTab.TRACK }))
    }

    fun noteHalf(lyricsHalf: Boolean) {
        if (present.lyricsHalf == lyricsHalf) return
        // The first word from the info tab says where it started; it is not a change.
        if (present.lyricsHalf == null) present = present.copy(lyricsHalf = lyricsHalf) else record(present.copy(lyricsHalf = lyricsHalf))
    }

    fun goBack(): Boolean {
        val previous = back.removeLastOrNull() ?: return false
        forward.addLast(present)
        move(previous)
        return true
    }

    fun goForward(): Boolean {
        val next = forward.removeLastOrNull() ?: return false
        back.addLast(present)
        move(next)
        return true
    }

    private fun record(place: Place) {
        back.addLast(present)
        if (back.size > MAX_ENTRIES) back.removeFirst()
        forward.clear()
        present = place
    }

    private fun move(place: Place) {
        present = place
        movedTick++
    }

    private const val MAX_ENTRIES = 32
}

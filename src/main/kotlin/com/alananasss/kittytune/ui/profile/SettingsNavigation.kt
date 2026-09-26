package com.alananasss.kittytune.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

/** Where settings are: a category and the sub-pages opened inside it, outermost first. */
internal data class SettingsPlace(val category: SettingsCategory, val pages: List<SettingsSubPage> = emptyList()) {
    val subPage: SettingsSubPage? get() = pages.lastOrNull()
    val depth: Int get() = pages.size
    fun parent(): SettingsPlace = copy(pages = pages.dropLast(1))
}

/**
 * The settings' own history, kept for the whole session.
 *
 * It lives outside the screen so that leaving settings and coming back lands where you were, and so the app's
 * back and forward buttons (and the mouse's side buttons) walk through categories and sub-pages before they
 * leave settings — Interface → Themes → back returns to Interface instead of to whatever was open before.
 */
internal object SettingsNavigation {
    private val places = mutableStateListOf(SettingsPlace(SettingsCategory.INTERFACE))
    private var index by mutableIntStateOf(0)

    val current: SettingsPlace get() = places[index]
    val canGoBack: Boolean get() = index > 0
    val canGoForward: Boolean get() = index < places.lastIndex

    /** Goes to [place] as a new step: whatever was ahead is dropped, like a browser. */
    fun go(place: SettingsPlace) {
        if (place == current) return
        while (places.lastIndex > index) places.removeAt(places.lastIndex)
        places += place
        index = places.lastIndex
        if (places.size > MAX_STEPS) {
            places.removeAt(0)
            index--
        }
    }

    /** The in-page back arrow: one level up, reusing the history step when that is where it leads. */
    fun up() {
        val parent = current.parent()
        if (canGoBack && places[index - 1] == parent) index-- else go(parent)
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        index--
        return true
    }

    fun forward(): Boolean {
        if (!canGoForward) return false
        index++
        return true
    }

    private const val MAX_STEPS = 50
}

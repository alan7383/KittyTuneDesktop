package com.alananasss.kittytune.core

/**
 * Lets something outside the window's composition bring it to the front — the Linux media widget's
 * "Raise", for one — without that code knowing how the window is hidden or shown. Main.kt installs
 * the handler for as long as the window exists.
 */
object MainWindowRaiser {
    @Volatile
    var handler: (() -> Unit)? = null

    fun raise() {
        handler?.invoke()
    }
}

package com.alananasss.kittytune.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The one thing about the window that the rest of the app needs to be able to ask for (issue #33).
 *
 * "Faut pas que ce soit un pop up mais un vrai écran, car quand on passe notre souris dessus ils voient."
 *
 * The full player was an overlay drawn inside the window, which is a different thing from a full screen
 * however much of the window it covers: the title bar stays, the taskbar stays, and anything that reacts to
 * the pointer near an edge still reacts. What he is asking for is the window itself going full screen.
 *
 * A global rather than a parameter threaded down, because the composable that wants it is nine levels below
 * the one that owns the window, and every level in between would carry a flag it has no interest in. Kept to
 * exactly one property so it cannot grow into a second place where window state lives.
 */
object AppWindowState {

    /** True while something on screen wants the window to fill the display. */
    var fullScreen by mutableStateOf(false)
}

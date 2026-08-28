package com.alananasss.kittytune.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Back and forward, on the side buttons of the mouse (issue #33).
 *
 * "I think you need to add a back and forward button, using the side buttons of the mouse: mouse
 * button 4 is back, mouse button 5 is forward."
 *
 * Back is the navigation library's own. Forward is not: Navigation Compose keeps a back stack and
 * nothing else, so once an entry is popped it is gone. This holds the routes it popped, and offers
 * them back in the order a browser would.
 *
 * What clears that forward list is the part worth getting right. Any navigation the reader makes
 * themselves invalidates it — you cannot go forward to a page you have just navigated away from
 * towards somewhere else — so it is cleared whenever the route changes for a reason that is not one
 * of these two buttons. That is what stops the forward button taking you somewhere you never asked
 * to go back from.
 */
/**
 * The back/forward pair, over whatever can actually navigate.
 *
 * Takes functions rather than a `NavHostController` so the part with the reasoning in it — when a
 * forward entry is offered and when it is thrown away — can be tested without a navigation host.
 */
class HistoryNavigator internal constructor(
    private val currentRoute: () -> String?,
    private val hasPrevious: () -> Boolean,
    private val pop: () -> Boolean,
    private val go: (String) -> Unit,
) {

    /** Routes popped by [back], most recent last. */
    internal val forward = mutableStateListOf<String>()

    /** Set around our own navigations, so the observer can tell them from the reader's. */
    internal var moving = false

    val canGoBack: Boolean get() = hasPrevious()

    val canGoForward: Boolean get() = forward.isNotEmpty()

    /** How many entries are waiting, for tests and for anything that wants to show the trail. */
    val forwardSize: Int get() = forward.size

    fun back() {
        if (!hasPrevious()) return
        val leaving = currentRoute() ?: return
        moving = true
        if (pop()) {
            forward.add(leaving)
            // Bounded: a session's worth of dead ends is not history anybody wants offered back.
            if (forward.size > MAX_FORWARD) forward.removeAt(0)
        } else {
            moving = false
        }
    }

    fun forward() {
        val route = forward.removeLastOrNull() ?: return
        moving = true
        go(route)
    }

    /**
     * Called when the route changed. A change we did not cause is the reader going somewhere new,
     * which invalidates everything we were holding: you cannot go forward to a page you have just
     * navigated away from towards somewhere else.
     */
    internal fun onRouteChanged() {
        if (moving) moving = false else forward.clear()
    }

    private companion object {
        const val MAX_FORWARD = 32
    }
}

/**
 * @return a navigator whose forward list follows [navController], cleared by any navigation the
 *   reader makes for themselves.
 */
@Composable
fun rememberHistoryNavigator(navController: NavHostController): HistoryNavigator {
    val navigator = remember(navController) {
        HistoryNavigator(
            currentRoute = { navController.currentBackStackEntry?.destination?.route },
            hasPrevious = { navController.previousBackStackEntry != null },
            pop = { navController.popBackStack() },
            go = { navController.navigate(it) },
        )
    }
    val entry by navController.currentBackStackEntryAsState()
    var lastRoute by remember(navController) { mutableStateOf<String?>(null) }

    LaunchedEffect(entry) {
        val route = entry?.destination?.route
        if (route != lastRoute) {
            lastRoute = route
            navigator.onRouteChanged()
        }
    }
    return navigator
}

/**
 * Routes the mouse's side buttons to [navigator].
 *
 * ## What the platform actually sends
 *
 * Measured rather than assumed, because none of it was what the names suggest. On X11 the two side
 * buttons arrive as [PointerButton] index 5 and 6, not as [PointerButton.Back] and
 * [PointerButton.Forward]; their event type is [PointerEventType.Unknown], not `Press`; and each
 * physical click produces *two* identical events with `pressed` false on both, so there is nothing in
 * an individual event that says whether it is the press or the release.
 *
 * Hence: both the named buttons and the observed indices are accepted, so this keeps working if
 * another platform numbers them the way the names imply; `Unknown` is accepted alongside `Press` for
 * the same reason; and a click is ignored if the same button already acted a few milliseconds ago,
 * which collapses the pair without swallowing a deliberate second click.
 *
 * Consumed only for those two buttons. Every other press passes through untouched, which is what lets
 * this sit on the root of the window.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.mouseHistoryButtons(navigator: HistoryNavigator): Modifier =
    this.pointerInput(navigator) {
        var lastIndex = -1
        var lastAtMs = 0L
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press && event.type != PointerEventType.Unknown) continue
                val button = event.button ?: continue
                val isBack = button == PointerButton.Back || button == BACK_ON_X11
                val isForward = button == PointerButton.Forward || button == FORWARD_ON_X11
                if (!isBack && !isForward) continue

                val index = if (isBack) 0 else 1
                val now = System.currentTimeMillis()
                event.changes.forEach { it.consume() }
                if (index == lastIndex && now - lastAtMs < PAIRED_EVENT_MS) continue
                lastIndex = index
                lastAtMs = now

                if (isBack) navigator.back() else navigator.forward()
            }
        }
    }

/** What X11 calls buttons 8 and 9, measured on a real event stream. */
private val BACK_ON_X11 = PointerButton(5)
private val FORWARD_ON_X11 = PointerButton(6)

/**
 * How close together two events for the same button must be to count as one click.
 *
 * The pair arrives within a frame of itself, so this only has to be longer than that, and short
 * enough that somebody clicking back twice in a hurry still goes back twice.
 */
private const val PAIRED_EVENT_MS = 60L

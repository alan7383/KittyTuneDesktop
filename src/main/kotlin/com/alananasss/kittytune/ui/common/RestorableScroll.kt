package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A [ScrollState] that comes back where it was, even when the content it scrolls needs a few frames to
 * reach its full height (issue #33).
 *
 * ## Why `rememberScrollState` is not enough
 *
 * "When you go through the settings and click somewhere, and then go back, you start from the beginning,
 * not from where you left off."
 *
 * `rememberScrollState` is already a `rememberSaveable`, and navigation already keeps a saveable registry
 * per back stack entry, so the offset *is* saved and restored. It is then thrown away by the framework,
 * one frame later, for a reason worth writing down. From `ScrollState`:
 *
 * ```
 * internal set(newMax) {
 *     _maxValueState.intValue = newMax
 *     Snapshot.withoutReadObservation {
 *         if (value > newMax) { value = newMax }
 *     }
 * }
 * ```
 *
 * `maxValue` starts at `Int.MAX_VALUE` and is replaced with the real content height on the first layout.
 * A settings page is built out of a dozen sections that each read a preference through a flow with a
 * placeholder initial value, so on that first layout it is a fraction of its eventual height — and the
 * restored offset, being larger than a height nobody has measured yet, is clamped down to it and lost.
 * The further down the page you were, the closer to the top you come back, which is why it reads as
 * "from the beginning".
 *
 * ## What this does instead
 *
 * It keeps the offset itself, and waits for the page to be tall enough to hold it before applying it.
 * The state is seeded with the remembered value so a page that is immediately tall enough never moves at
 * all; when it is not, the scroll happens as soon as the content has grown, within a short window after
 * which a genuinely shorter page is accepted as shorter.
 *
 * A scroll by hand during that window wins: the reader has said where they want to be, and arriving
 * somewhere else half a second later would be worse than not restoring at all.
 */
@Composable
fun rememberRestorableScrollState(): ScrollState {
    var remembered by rememberSaveable { mutableIntStateOf(0) }

    // A plain `remember`: this owns its persistence through `remembered`, and letting the framework's own
    // saver run as well would mean two answers to the same question.
    val state = remember { ScrollState(remembered) }

    LaunchedEffect(state) {
        val target = remembered
        if (target > 0) {
            withTimeoutOrNull(RESTORE_WINDOW_MS) {
                snapshotFlow { state.maxValue }
                    // Not `Int.MAX_VALUE`, which is the value before anything has been laid out and would
                    // satisfy any target while measuring nothing.
                    .filter { it != Int.MAX_VALUE && it >= target }
                    .first()
                if (state.value < target) state.scrollTo(target)
            }
        }
        // Only now: writing the offset down while it is still 0 would record the position the restore was
        // about to undo.
        snapshotFlow { state.value }.collect { remembered = it }
    }

    return state
}

/**
 * How long to keep waiting for the content to grow into the remembered offset.
 *
 * Long enough for a page whose sections each hydrate from disk or from the network, short enough that a
 * page which really did get shorter — a list with fewer items than last time — settles at its own top
 * rather than sitting under a pending scroll.
 */
private const val RESTORE_WINDOW_MS = 2_000L

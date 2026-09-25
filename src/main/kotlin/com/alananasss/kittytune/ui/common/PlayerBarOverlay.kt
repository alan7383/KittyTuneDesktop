package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where the floating player bar is, on screen, while it floats over the content.
 *
 * In the floating style the bar is drawn on top of the panels rather than below them, so the content runs
 * under it and shows around and through it. Scrolling containers read this to leave room at their end, so
 * the last row can still be scrolled clear of the bar.
 */
@Stable
class PlayerBarOverlay {
    /** The bar's bounds in screen pixels, or null when it is not floating. */
    var bounds by mutableStateOf<Rect?>(null)
}

val LocalPlayerBarOverlay = staticCompositionLocalOf<PlayerBarOverlay?> { null }

/** How far the floating bar covers the element this is attached to, from its bottom edge up. */
@Stable
class PlayerBarOverlap internal constructor(private val overlay: PlayerBarOverlay?) {
    private var ownBounds by mutableStateOf<Rect?>(null)

    val modifier: Modifier = if (overlay == null) Modifier else Modifier.onGloballyPositioned { coordinates ->
        val topLeft = coordinates.positionOnScreen()
        ownBounds = Rect(topLeft.x, topLeft.y, topLeft.x + coordinates.size.width, topLeft.y + coordinates.size.height)
    }

    /** Covered height in pixels; zero when the bar is elsewhere or not floating. */
    val overlapPx: Float
        get() {
            val bar = overlay?.bounds ?: return 0f
            val own = ownBounds ?: return 0f
            val sideBySide = own.right <= bar.left || own.left >= bar.right
            if (sideBySide) return 0f
            return (own.bottom - bar.top).coerceIn(0f, own.height)
        }
}

@Composable
fun rememberPlayerBarOverlap(): PlayerBarOverlap {
    val overlay = LocalPlayerBarOverlay.current
    return remember(overlay) { PlayerBarOverlap(overlay) }
}

/** The covered height, with a little breathing room above the bar, as a Dp for padding. */
@Composable
fun PlayerBarOverlap.clearance(): Dp {
    val px = overlapPx
    if (px <= 0f) return 0.dp
    return with(LocalDensity.current) { px.toDp() } + 12.dp
}

/** [this] with [extra] added to its bottom. */
@Composable
fun PaddingValues.plusBottom(extra: Dp): PaddingValues {
    if (extra == 0.dp) return this
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + extra,
    )
}

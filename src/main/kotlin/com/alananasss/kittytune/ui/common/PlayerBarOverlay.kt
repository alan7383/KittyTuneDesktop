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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
    /** The bar's bounds in window pixels, or null when it is not floating. */
    var bounds by mutableStateOf<Rect?>(null)
}

val LocalPlayerBarOverlay = staticCompositionLocalOf<PlayerBarOverlay?> { null }

/** How far the floating bar covers the element this is attached to, from its bottom edge up. */
@Stable
class PlayerBarOverlap internal constructor(private val overlay: PlayerBarOverlay?) {
    private var ownBounds by mutableStateOf<Rect?>(null)

    val modifier: Modifier = if (overlay == null) Modifier else Modifier.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            ownBounds = coordinates.boundsInWindow()
        }
    }

    /** Covered height in pixels; zero when the bar is elsewhere or not floating. */
    val overlapPx: Float
        get() {
            val bar = overlay?.bounds ?: return 0f
            val own = ownBounds ?: return 0f
            if (bar.isEmpty || own.isEmpty) return 0f
            if (bar.left.isNaN() || bar.right.isNaN() || bar.top.isNaN() || bar.bottom.isNaN()) return 0f
            if (own.left.isNaN() || own.right.isNaN() || own.top.isNaN() || own.bottom.isNaN()) return 0f
            val sideBySide = own.right <= bar.left || own.left >= bar.right
            if (sideBySide) return 0f
            val overlap = (own.bottom - bar.top).coerceIn(0f, own.height)
            return if (overlap.isNaN() || overlap < 0f) 0f else overlap
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
    val target = if (px.isNaN() || px <= 0f) 0.dp else with(LocalDensity.current) { px.toDp() } + 16.dp
    val safeTarget = if (target.value.isNaN() || target.value < 0f) 0.dp else target
    // Eased, so whatever sits above the bar (the sidebar's profile row, a list's end) glides to its new place.
    val animated by androidx.compose.animation.core.animateDpAsState(
        targetValue = safeTarget,
        animationSpec = androidx.compose.animation.core.tween(280),
        label = "barClearance"
    )
    return if (animated.value.isNaN() || animated.value < 0f) 0.dp else animated
}

/** [this] with [extra] added to its bottom. */
@Composable
fun PaddingValues.plusBottom(extra: Dp): PaddingValues {
    val safeExtra = if (extra.value.isNaN() || extra.value <= 0f) 0.dp else extra
    if (safeExtra == 0.dp) return this
    val direction = LocalLayoutDirection.current
    // Some callers hand in custom PaddingValues whose sides are unspecified or go below zero mid-animation
    // (the sidebar's collapse); the stock constructor rejects both, so each side is made safe.
    fun Dp.safe(): Dp = if (value.isNaN() || value < 0f) 0.dp else this
    return PaddingValues(
        start = calculateStartPadding(direction).safe(),
        top = calculateTopPadding().safe(),
        end = calculateEndPadding(direction).safe(),
        bottom = calculateBottomPadding().safe() + safeExtra.safe(),
    )
}

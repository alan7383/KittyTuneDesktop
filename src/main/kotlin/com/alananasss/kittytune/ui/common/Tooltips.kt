package com.alananasss.kittytune.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * A plain Material tooltip, positioned so it cannot leave the window.
 *
 * Wherever an icon has to stand in for a label — a collapsed sidebar, a panel narrow enough to drop
 * its tab text — this is what says which is which on hover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tip(text: String, enabled: Boolean = true, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = rememberEdgeSafeTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        // A row that only sometimes needs a tooltip — a sidebar label is repeated by the tooltip while
        // the panel is open and replaced by it once collapsed — turns this off rather than dropping the
        // wrapper. Dropping it would change the shape of the tree mid-animation (issue #33).
        enableUserInput = enabled,
        content = content,
    )
}

/**
 * The plain tooltip position, kept inside the window.
 *
 * Material centres a tooltip on its anchor and does not clamp it. The library panel's icons sit
 * against the left edge, so a label wider than its icon — every label, once the panel is collapsed —
 * started at a negative x and was cut off by the window, which is the unreadable
 * "usic Recognition" in issue #33.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberEdgeSafeTooltipPositionProvider(): PopupPositionProvider {
    val delegate = TooltipDefaults.rememberPlainTooltipPositionProvider()
    return remember(delegate) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val wanted =
                    delegate.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                return IntOffset(wanted.x.coerceIn(0, maxX), wanted.y)
            }
        }
    }
}

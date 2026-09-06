package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider


/**
 * When `true`, [Tip] renders its content directly without creating a [TooltipBox].
 *
 * `TooltipBox` creates a popup window (a separate Compose scene on Desktop). If many of these are
 * composed and decomposed in rapid succession — which is what happens when the sidebar's collapse
 * animation crosses the thresholds that flip `enabled` on and off — the underlying Skiko
 * `RootNodeOwner` can be disposed while a previous scene still references it, producing the
 * "RootNodeOwner is already disposed" crash.
 *
 * The sidebar's hover-expand provides `true` here for the duration of the transition so that no
 * tooltip popup is ever created while the width is in flight.
 */
val LocalSuppressTooltips = compositionLocalOf { false }

/**
 * A plain Material tooltip, positioned so it cannot leave the window.
 *
 * Wherever an icon has to stand in for a label — a collapsed sidebar, a panel narrow enough to drop
 * its tab text — this is what says which is which on hover.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tip(text: String, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled || text.isBlank() || LocalSuppressTooltips.current) {
        content()
        return
    }
    TooltipBox(
        positionProvider = rememberEdgeSafeTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
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

/**
 * A small dot beside a label that gives up an aside on hover.
 *
 * For the thing that is worth saying about an option and is not worth saying in the option's name. A
 * subtitle would push every other row down for a sentence nobody needs twice; a dot costs a dot.
 *
 * The dot drawn is [DOT_DIAMETER] and the thing you have to hit is [DOT_TARGET] — a five-pixel hover target
 * is a dot nobody ever reads.
 */
@Composable
fun FunFactDot(
    fact: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Tip(fact) {
        Box(modifier.size(DOT_TARGET), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(DOT_DIAMETER)
                    .background(tint.copy(alpha = 0.55f), CircleShape)
            )
        }
    }
}

private val DOT_DIAMETER = 5.dp
private val DOT_TARGET = 16.dp

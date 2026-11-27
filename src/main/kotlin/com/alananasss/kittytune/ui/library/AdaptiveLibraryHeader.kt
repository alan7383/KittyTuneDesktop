package com.alananasss.kittytune.ui.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val HEADER_ROW_HEIGHT = 40.dp

/**
 * Reserve actions at their natural size before constraining the translated title.
 * Only the placed actions can open a menu.
 * While the sidebar folds, keep the arrangement it has at [expandedWidth] and release any
 * second row as the actions fade.
 */
@Composable
internal fun AdaptiveLibraryHeader(
    modifier: Modifier = Modifier,
    expandedWidth: Dp? = null,
    actionsVisibility: Float = 1f,
    title: @Composable (measuring: Boolean) -> Unit,
    actions: @Composable (extendedCreate: Boolean, measuring: Boolean) -> Unit,
) {
    SubcomposeLayout(modifier.clipToBounds()) { constraints ->
        val width = constraints.maxWidth
        val arrangementWidth = expandedWidth?.roundToPx() ?: width
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val preferredTitle = subcompose("preferredTitle") { Row { title(true) } }
            .single().measure(loose)
        val extended = subcompose("extendedActions") { Row { actions(true, true) } }
            .single().measure(loose)
        val useExtended = preferredTitle.width + extended.width <= arrangementWidth
        val buttons = subcompose("actions") {
            Row(Modifier.heightIn(min = HEADER_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                actions(useExtended, false)
            }
        }.single().measure(loose)
        val minTitleWidth = 48.dp.roundToPx()
        val stacked = buttons.width + minTitleWidth > arrangementWidth
        val rightAligned = (width - buttons.width).coerceAtLeast(0)
        // Keep the icon's space clear as the narrowing header clips the departing actions.
        val actionsLeft = if (stacked) rightAligned else maxOf(rightAligned, minOf(width, minTitleWidth))
        val titleWidth = if (stacked) width else actionsLeft
        val label = subcompose("title") { Row(Modifier.clipToBounds()) { title(false) } }.single().measure(
            Constraints(maxWidth = titleWidth, maxHeight = constraints.maxHeight)
        )
        val visibleActionHeight = (buttons.height * actionsVisibility).roundToInt()
        val rowHeight = maxOf(label.height, HEADER_ROW_HEIGHT.roundToPx())
        val height = if (stacked) rowHeight + visibleActionHeight else maxOf(rowHeight, visibleActionHeight)
        layout(width, constraints.constrainHeight(height)) {
            val titleRowHeight = if (stacked) rowHeight else height
            label.placeRelative(0, (titleRowHeight - label.height) / 2)
            buttons.placeRelative(
                actionsLeft,
                if (stacked) rowHeight else (height - buttons.height).coerceAtLeast(0) / 2,
            )
        }
    }
}

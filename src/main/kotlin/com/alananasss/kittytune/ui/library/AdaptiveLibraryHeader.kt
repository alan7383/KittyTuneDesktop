package com.alananasss.kittytune.ui.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/**
 * Reserve actions at their natural size before constraining the translated title.
 * Measurement compositions must suppress popups: only the placed actions own the visible menu.
 */
@Composable
internal fun AdaptiveLibraryHeader(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    actions: @Composable (extendedCreate: Boolean, measuring: Boolean) -> Unit,
) {
    SubcomposeLayout(modifier) { constraints ->
        val loose = Constraints(maxHeight = constraints.maxHeight)
        val preferredTitle = subcompose("preferredTitle") { Row { title() } }
            .single().measure(loose)
        val extended = subcompose("extendedActions") { Row(Modifier.heightIn(min = 40.dp)) { actions(true, true) } }
            .single().measure(loose)
        val useExtended = preferredTitle.width + extended.width <= constraints.maxWidth
        val buttons = subcompose("actions") {
            Row(Modifier.heightIn(min = 40.dp)) { actions(useExtended, false) }
        }.single().measure(loose)
        // At very narrow widths keep the back/title row above the actions rather than overlapping it.
        val stacked = buttons.width + 48.dp.roundToPx() > constraints.maxWidth
        val titleWidth = if (stacked) constraints.maxWidth else constraints.maxWidth - buttons.width
        val label = subcompose("title") { Row { title() } }.single().measure(
            Constraints(maxWidth = titleWidth.coerceAtLeast(0), maxHeight = constraints.maxHeight)
        )
        val height = if (stacked) label.height + buttons.height else maxOf(label.height, buttons.height)
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            label.placeRelative(0, if (stacked) 0 else (height - label.height) / 2)
            buttons.placeRelative(
                (constraints.maxWidth - buttons.width).coerceAtLeast(0),
                if (stacked) label.height else (height - buttons.height) / 2,
            )
        }
    }
}

package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** How long a [MorphingGrid] takes to move its cells into a new column count. */
private const val MORPH_MS = 380

/**
 * Cells laid out [columns] to a row, which glide to their new places when the column count changes.
 *
 * Choosing between a Row and a Column by width did two things wrong at the moment the width crossed the line
 * (issue #66). The cells jumped from one arrangement to the other, where a card that no longer fits should
 * stretch out and the one beside it should slide below. And because a Row and a Column are different places
 * in the composition, every cell was thrown away and built again, so the bar charts inside grew in from zero
 * each time the window was resized past the threshold — an entrance animation playing on a resize.
 *
 * Here the cells stay where they are in the composition whatever the column count, so their state survives,
 * and each cell's rectangle is interpolated between the old arrangement and the new one. Cells in a row share
 * the row's height, the way `Modifier.height(IntrinsicSize.Max)` with `fillMaxHeight()` did.
 *
 * @param weights relative widths of the slots in a row, by position; equal when null or of the wrong size.
 */
@Composable
internal fun MorphingGrid(
    columns: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    weights: List<Float>? = null,
    content: @Composable () -> Unit,
) {
    var fromColumns by remember { mutableIntStateOf(columns) }
    var toColumns by remember { mutableIntStateOf(columns) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(columns) {
        if (columns == toColumns) return@LaunchedEffect
        fromColumns = toColumns
        toColumns = columns
        progress.snapTo(0f)
        progress.animateTo(1f, tween(MORPH_MS, easing = FastOutSlowInEasing))
    }

    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = spacing.roundToPx()
        val from = gridCells(measurables, fromColumns.coerceAtLeast(1), width, gap, weights)
        val to = gridCells(measurables, toColumns.coerceAtLeast(1), width, gap, weights)
        val t = progress.value
        val cells = from.cells.zip(to.cells) { a, b -> a.lerp(b, t) }
        val placeables = measurables.zip(cells) { measurable, cell ->
            measurable.measure(Constraints.fixed(cell.width.coerceAtLeast(0), cell.height.coerceAtLeast(0)))
        }
        val height = lerp(from.height, to.height, t).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            placeables.zip(cells) { placeable, cell -> placeable.place(cell.x, cell.y) }
        }
    }
}

private class Cell(val x: Int, val y: Int, val width: Int, val height: Int) {
    fun lerp(other: Cell, t: Float) =
        Cell(lerp(x, other.x, t), lerp(y, other.y, t), lerp(width, other.width, t), lerp(height, other.height, t))
}

private class GridPlan(val cells: List<Cell>, val height: Int)

private fun lerp(start: Int, stop: Int, t: Float): Int = (start + (stop - start) * t).roundToInt()

/** Where every cell goes with [columns] slots to a row: slot widths from [weights], row heights from the tallest cell. */
private fun gridCells(
    measurables: List<IntrinsicMeasurable>,
    columns: Int,
    width: Int,
    gap: Int,
    weights: List<Float>?,
): GridPlan {
    val slotWeights = weights?.takeIf { it.size == columns } ?: List(columns) { 1f }
    val free = (width - gap * (columns - 1)).coerceAtLeast(0)
    val totalWeight = slotWeights.sum()
    val slotWidths = slotWeights.map { (free * it / totalWeight).roundToInt() }
    val slotX = slotWidths.runningFold(0) { x, slot -> x + slot + gap }

    val cells = ArrayList<Cell>(measurables.size)
    var y = 0
    measurables.chunked(columns).forEach { row ->
        val rowHeight = row.withIndex().maxOf { (slot, cell) -> cell.maxIntrinsicHeight(slotWidths[slot]) }
        row.indices.forEach { slot -> cells += Cell(slotX[slot], y, slotWidths[slot], rowHeight) }
        y += rowHeight + gap
    }
    return GridPlan(cells, (y - gap).coerceAtLeast(0))
}

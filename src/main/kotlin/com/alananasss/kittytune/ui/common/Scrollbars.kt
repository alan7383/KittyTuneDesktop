package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ScrollableLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit
) {
    val barOverlap = rememberPlayerBarOverlap()
    val clearance = barOverlap.clearance()
    Box(modifier = modifier.then(barOverlap.modifier)) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = contentPadding.plusBottom(clearance),
            reverseLayout = reverseLayout,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content
        )
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 4.dp, bottom = 4.dp + clearance, start = 2.dp, end = 2.dp),
            adapter = rememberScrollbarAdapter(scrollState = state)
        )
    }
}

@Composable
fun ScrollableLazyVerticalGrid(
    columns: androidx.compose.foundation.lazy.grid.GridCells,
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.grid.LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit
) {
    val barOverlap = rememberPlayerBarOverlap()
    val clearance = barOverlap.clearance()
    Box(modifier = modifier.then(barOverlap.modifier)) {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = contentPadding.plusBottom(clearance),
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = content
        )
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 4.dp, bottom = 4.dp + clearance, start = 2.dp, end = 2.dp),
            adapter = rememberScrollbarAdapter(scrollState = state)
        )
    }
}

@Composable
fun ScrollableColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    hideScrollbar: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val barOverlap = rememberPlayerBarOverlap()
    val clearance = barOverlap.clearance()
    Box(modifier = modifier.then(barOverlap.modifier)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(state).padding(contentPadding.plusBottom(clearance)),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content
        )
        if (!hideScrollbar) {
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 4.dp, bottom = 4.dp + clearance, start = 2.dp, end = 2.dp),
                adapter = rememberScrollbarAdapter(scrollState = state)
            )
        }
    }
}

/**
 * Enables smooth horizontal mouse swipe / drag scrolling across horizontal containers (issue #56).
 *
 * Holding the cursor down on any item and dragging left-to-right or right-to-left scrolls the container.
 * Quick mouse gestures trigger a physics-based friction fling.
 * If the user clicks without exceeding pointer slop, child clicks are preserved intact.
 */
fun Modifier.horizontalMouseSwipe(
    state: ScrollableState,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this
    val scope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }
    var flingJob by remember { mutableStateOf<Job?>(null) }

    this.pointerInput(state) {
        detectHorizontalDragGestures(
            onDragStart = { offset ->
                flingJob?.cancel()
                velocityTracker.resetTracking()
                velocityTracker.addPosition(System.currentTimeMillis(), offset)
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity().x
                if (abs(velocity) > 100f) {
                    flingJob = scope.launch {
                        var currentVelocity = -velocity
                        val friction = 0.92f
                        while (abs(currentVelocity) > 15f) {
                            state.scrollBy(currentVelocity * 0.016f)
                            currentVelocity *= friction
                            delay(16)
                        }
                    }
                }
            },
            onDragCancel = {
                flingJob?.cancel()
            },
            onHorizontalDrag = { change, dragAmount ->
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
                scope.launch {
                    state.scrollBy(-dragAmount)
                }
            }
        )
    }
}

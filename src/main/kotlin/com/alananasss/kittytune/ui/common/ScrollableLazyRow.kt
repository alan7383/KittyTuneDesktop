package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A horizontal row you can actually move with a mouse (issue #33).
 *
 * A `LazyRow` is built for a thumb. On a desktop there is nothing to swipe with: the row simply sat there
 * with its remaining items off-screen and no way to reach them — the top tracks and top artists on the
 * statistics screen were unreachable past whatever the window happened to fit.
 *
 * Two ways in, because people reach for both:
 *
 * - **Arrows**, at each end, shown only while there is something in that direction. Behind a gradient that
 *   fades the row out beneath them, so the button sits on a soft edge instead of on top of a half-cut card.
 * - **The wheel**, translated from vertical to horizontal. Consumed only when the row can actually move that
 *   way; at either end the event is left alone so the page underneath keeps scrolling and the row does not
 *   become a dead patch in the middle of it.
 *
 * The arrow half already existed, written inline and twice over in the home screen. This is that pattern
 * extracted, with the wheel added, so a third caller does not mean a third copy.
 */
@Composable
fun ScrollableLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    /** What the edges fade into. Should match whatever the row is drawn on. */
    fadeColor: Color = MaterialTheme.colorScheme.background,
    /** How far one press of an arrow travels. Three cards is a screenful on most window widths. */
    itemsPerJump: Int = 3,
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val canScrollBackward by remember { derivedStateOf { state.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { state.canScrollForward } }

    val alphaLeft by animateFloatAsState(if (canScrollBackward) 1f else 0f, label = "rowArrowLeft")
    val alphaRight by animateFloatAsState(if (canScrollForward) 1f else 0f, label = "rowArrowRight")

    Box(modifier) {
        LazyRow(
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = horizontalArrangement,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val notches = event.changes.fold(0f) { sum, c -> sum + c.scrollDelta.y }
                            if (notches == 0f) continue
                            // Down means right. Only ours if the row can go that way — otherwise the page
                            // behind it should keep scrolling as usual.
                            val wanted = notches > 0f
                            if (wanted && !state.canScrollForward) continue
                            if (!wanted && !state.canScrollBackward) continue
                            event.changes.forEach { it.consume() }
                            val px = with(density) { WHEEL_STEP.toPx() } * notches
                            scope.launch { state.scrollBy(px) }
                        }
                    }
                },
            content = content,
        )

        if (alphaLeft > 0f) {
            EdgeArrow(
                alpha = alphaLeft,
                alignment = Alignment.CenterStart,
                fadeColor = fadeColor,
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                onClick = {
                    scope.launch {
                        state.animateScrollToItem((state.firstVisibleItemIndex - itemsPerJump).coerceAtLeast(0))
                    }
                },
            )
        }
        if (alphaRight > 0f) {
            EdgeArrow(
                alpha = alphaRight,
                alignment = Alignment.CenterEnd,
                fadeColor = fadeColor,
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                onClick = {
                    scope.launch {
                        state.animateScrollToItem(state.firstVisibleItemIndex + itemsPerJump)
                    }
                },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.EdgeArrow(
    alpha: Float,
    alignment: Alignment,
    fadeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val toTransparent = alignment == Alignment.CenterStart
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(ARROW_LANE)
            .align(alignment)
            .graphicsLayer { this.alpha = alpha }
            .background(
                Brush.horizontalGradient(
                    colors = if (toTransparent) listOf(fadeColor, Color.Transparent)
                    else listOf(Color.Transparent, fadeColor)
                )
            ),
        contentAlignment = if (toTransparent) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(icon, contentDescription = null)
        }
    }
}

/** How far one wheel notch moves the row. About a card, so the gesture feels like paging by hand. */
private val WHEEL_STEP = 90.dp

/** Width of the faded lane an arrow sits in. Wide enough to soften the cut-off card behind it. */
private val ARROW_LANE = 72.dp

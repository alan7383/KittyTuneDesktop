    package com.alananasss.kittytune.ui.utils
    
    import androidx.compose.animation.core.FastOutSlowInEasing
    import androidx.compose.animation.core.animateFloatAsState
    import androidx.compose.animation.core.tween
    import androidx.compose.foundation.gestures.ScrollableState
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.derivedStateOf
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.remember
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.drawWithContent
    import androidx.compose.ui.graphics.BlendMode
    import androidx.compose.ui.graphics.Brush
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.CompositingStrategy
    import androidx.compose.ui.graphics.graphicsLayer
    import androidx.compose.ui.unit.Dp
    import androidx.compose.ui.unit.dp
    
    fun Modifier.fadingEdge(brush: Brush) = this
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.DstIn)
        }

    fun Modifier.smoothFadingEdge(
        top: androidx.compose.ui.unit.Dp? = null,
        bottom: androidx.compose.ui.unit.Dp? = null,
    ) = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            if (top != null) {
                val topPx = top.toPx()
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Transparent,
                                    0.3f to Color.Black.copy(alpha = 0.15f),
                                    0.5f to Color.Black.copy(alpha = 0.4f),
                                    0.7f to Color.Black.copy(alpha = 0.7f),
                                    0.85f to Color.Black.copy(alpha = 0.9f),
                                    1.0f to Color.Black,
                                ),
                            startY = 0f,
                            endY = topPx,
                        ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottom != null) {
                val bottomPx = bottom.toPx()
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color.Black,
                                    0.15f to Color.Black.copy(alpha = 0.9f),
                                    0.3f to Color.Black.copy(alpha = 0.7f),
                                    0.5f to Color.Black.copy(alpha = 0.4f),
                                    0.7f to Color.Black.copy(alpha = 0.15f),
                                    1.0f to Color.Transparent,
                                ),
                            startY = size.height - bottomPx,
                            endY = size.height,
                        ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

fun Modifier.smoothFadingEdge(vertical: androidx.compose.ui.unit.Dp) =
    smoothFadingEdge(
        top = vertical,
        bottom = vertical,
    )

/**
 * Fades the left/start and right/end edges of a horizontally scrollable container when content extends beyond them.
 *
 * As the user scrolls, the edge in that direction smoothly dissolves towards transparency, preventing harsh cut-offs.
 * When scrolled to an edge, the fade in that direction disappears so items at the edge are fully visible.
 */
@androidx.compose.runtime.Composable
fun Modifier.fadedHorizontalEdges(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    fadeLength: androidx.compose.ui.unit.Dp = 36.dp,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val backwardAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (canScrollBackward) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "fadeHorizontalBackwardAlpha",
    )
    val forwardAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (canScrollForward) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "fadeHorizontalForwardAlpha",
    )

    return this
        .graphicsLayer {
            compositingStrategy = if (backwardAlpha > 0f || forwardAlpha > 0f) {
                CompositingStrategy.Offscreen
            } else {
                CompositingStrategy.Auto
            }
        }
        .drawWithContent {
            drawContent()
            if (size.width <= 0f || size.height <= 0f) return@drawWithContent
            if (backwardAlpha <= 0f && forwardAlpha <= 0f) return@drawWithContent

            val fadePx = fadeLength.toPx().coerceAtMost(size.width / 4)
            if (fadePx <= 0f) return@drawWithContent
            val stopFraction = (fadePx / size.width).coerceIn(0.001f, 0.499f)

            if (backwardAlpha > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 1f - backwardAlpha),
                            (stopFraction * 0.35f) to Color.Black.copy(alpha = 1f - backwardAlpha * 0.75f),
                            (stopFraction * 0.70f) to Color.Black.copy(alpha = 1f - backwardAlpha * 0.30f),
                            stopFraction to Color.Black,
                            1.0f to Color.Black,
                        ),
                        startX = 0f,
                        endX = size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (forwardAlpha > 0f) {
                val startFraction = 1f - stopFraction
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black,
                            startFraction to Color.Black,
                            (startFraction + stopFraction * 0.30f) to Color.Black.copy(alpha = 1f - forwardAlpha * 0.30f),
                            (startFraction + stopFraction * 0.65f) to Color.Black.copy(alpha = 1f - forwardAlpha * 0.75f),
                            1.0f to Color.Black.copy(alpha = 1f - forwardAlpha),
                        ),
                        startX = 0f,
                        endX = size.width,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

/**
 * Fades the horizontal edges of any scrollable container implementing [androidx.compose.foundation.gestures.ScrollableState].
 */
@androidx.compose.runtime.Composable
fun Modifier.fadedHorizontalEdges(
    state: androidx.compose.foundation.gestures.ScrollableState,
    fadeLength: androidx.compose.ui.unit.Dp = 36.dp,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val canScrollBackward by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { state.canScrollBackward } }
    val canScrollForward by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { state.canScrollForward } }
    return fadedHorizontalEdges(
        canScrollBackward = canScrollBackward,
        canScrollForward = canScrollForward,
        fadeLength = fadeLength,
        enabled = enabled,
    )
}

/**
 * Fades the top and bottom edges of a vertically scrollable container when content extends beyond them.
 *
 * When the user scrolls down, the top edge smoothly dissolves items towards transparency as they scroll past.
 * When more content is available below, the bottom edge smoothly dissolves items before reaching the container boundary.
 */
@androidx.compose.runtime.Composable
fun Modifier.fadedVerticalEdges(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    fadeLength: androidx.compose.ui.unit.Dp = 28.dp,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val topAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (canScrollBackward) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "fadeVerticalTopAlpha",
    )
    val bottomAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (canScrollForward) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "fadeVerticalBottomAlpha",
    )

    return this
        .graphicsLayer {
            compositingStrategy = if (topAlpha > 0f || bottomAlpha > 0f) {
                CompositingStrategy.Offscreen
            } else {
                CompositingStrategy.Auto
            }
        }
        .drawWithContent {
            drawContent()
            if (size.width <= 0f || size.height <= 0f) return@drawWithContent
            if (topAlpha <= 0f && bottomAlpha <= 0f) return@drawWithContent

            val fadePx = fadeLength.toPx().coerceAtMost(size.height / 4)
            if (fadePx <= 0f) return@drawWithContent
            val stopFraction = (fadePx / size.height).coerceIn(0.001f, 0.499f)

            if (topAlpha > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 1f - topAlpha),
                            (stopFraction * 0.35f) to Color.Black.copy(alpha = 1f - topAlpha * 0.75f),
                            (stopFraction * 0.70f) to Color.Black.copy(alpha = 1f - topAlpha * 0.30f),
                            stopFraction to Color.Black,
                            1.0f to Color.Black,
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (bottomAlpha > 0f) {
                val startFraction = 1f - stopFraction
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black,
                            startFraction to Color.Black,
                            (startFraction + stopFraction * 0.30f) to Color.Black.copy(alpha = 1f - bottomAlpha * 0.30f),
                            (startFraction + stopFraction * 0.65f) to Color.Black.copy(alpha = 1f - bottomAlpha * 0.75f),
                            1.0f to Color.Black.copy(alpha = 1f - bottomAlpha),
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
}

/**
 * Fades the vertical edges of any scrollable container implementing [androidx.compose.foundation.gestures.ScrollableState].
 */
@androidx.compose.runtime.Composable
fun Modifier.fadedVerticalEdges(
    state: androidx.compose.foundation.gestures.ScrollableState,
    fadeLength: androidx.compose.ui.unit.Dp = 28.dp,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val canScrollBackward by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { state.canScrollBackward } }
    val canScrollForward by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { state.canScrollForward } }
    return fadedVerticalEdges(
        canScrollBackward = canScrollBackward,
        canScrollForward = canScrollForward,
        fadeLength = fadeLength,
        enabled = enabled,
    )
}




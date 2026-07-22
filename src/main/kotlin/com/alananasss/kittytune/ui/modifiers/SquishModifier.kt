package com.alananasss.kittytune.ui.modifiers

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * M3 Expressive "Squish" effect for chips and buttons.
 */
@Composable
fun Modifier.squish(interactionSource: InteractionSource, squishAmount: Float = 0.85f): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) squishAmount else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 500f
        )
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

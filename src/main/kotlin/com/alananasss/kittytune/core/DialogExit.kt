package com.alananasss.kittytune.core

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntSize

/**
 * Lets dialogs leave with an animation instead of vanishing.
 *
 * A dialog is shown by `if (show) Dialog(...)`, so the moment it is closed it is gone from composition and
 * has nothing left to animate with, whichever way it was closed: a button, Esc or a click outside. Rather
 * than rework every caller, each dialog keeps its last frame in a graphics layer while it is open, and when
 * it leaves, [DialogExitHost] at the root of the window fades and shrinks that frame where it stood.
 */
internal object DialogExit {

    class Ghost(val layer: GraphicsLayer, val topLeft: Offset, val size: IntSize, val context: GraphicsContext)

    internal val ghosts = mutableStateListOf<Ghost>()

    /** Records the dialog it is applied to, and hands its last frame over when the dialog goes away. */
    @Composable
    fun recorder(): Modifier {
        // The window's own context, not the dialog's: the layer has to outlive the dialog.
        val context = LocalGraphicsContext.current
        val state = remember { Recording(context.createGraphicsLayer()) }
        DisposableEffect(state) {
            onDispose {
                val size = state.size
                if (state.hasDrawn && size != null && size.width > 0 && size.height > 0) {
                    // A window without a host never clears its ghosts; keep only the newest few.
                    while (ghosts.size >= MAX_GHOSTS) ghosts.removeAt(0).let { it.context.releaseGraphicsLayer(it.layer) }
                    ghosts += Ghost(state.layer, state.topLeft, size, context)
                } else {
                    context.releaseGraphicsLayer(state.layer)
                }
            }
        }
        return Modifier
            .onGloballyPositioned {
                state.topLeft = it.positionInWindow()
                state.size = it.size
            }
            .drawWithContent {
                state.layer.record { this@drawWithContent.drawContent() }
                drawLayer(state.layer)
                state.hasDrawn = true
            }
    }

    private class Recording(val layer: GraphicsLayer) {
        var topLeft = Offset.Zero
        var size: IntSize? = null
        var hasDrawn = false
    }
}

/** Draws the dialogs that are on their way out. Belongs above everything else in the window. */
@Composable
fun DialogExitHost() {
    DialogExit.ghosts.toList().forEach { ghost ->
        key(ghost) { DialogGhost(ghost) }
    }
}

@Composable
private fun DialogGhost(ghost: DialogExit.Ghost) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(ghost) {
        progress.animateTo(0f, tween(DIALOG_EXIT_MS, easing = FastOutLinearInEasing))
        DialogExit.ghosts.remove(ghost)
        ghost.context.releaseGraphicsLayer(ghost.layer)
    }
    Canvas(Modifier.fillMaxSize()) {
        val visible = progress.value
        ghost.layer.alpha = visible
        val shrink = DIALOG_EXIT_SCALE + (1f - DIALOG_EXIT_SCALE) * visible
        translate(ghost.topLeft.x, ghost.topLeft.y) {
            scale(shrink, pivot = Offset(ghost.size.width / 2f, ghost.size.height / 2f)) {
                drawLayer(ghost.layer)
            }
        }
    }
}

private const val DIALOG_EXIT_MS = 160
private const val DIALOG_EXIT_SCALE = 0.92f
private const val MAX_GHOSTS = 4

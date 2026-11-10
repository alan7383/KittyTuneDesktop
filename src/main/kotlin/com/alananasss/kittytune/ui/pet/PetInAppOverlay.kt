@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.alananasss.kittytune.ui.pet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import java.awt.Cursor
import kotlin.math.roundToInt

/** The card the cat is drawn in, inside the app: a little smaller than the floating window. */
private val CARD_WIDTH = 190.dp
private val CARD_HEIGHT = 175.dp

/** Breathing room between the cat and the window's edges, so it never sits flush against them. */
private val EDGE = 12.dp

private val MENU_WIDTH = 224.dp
private val MENU_ROW_HEIGHT = 44.dp
private val MENU_PADDING = 6.dp
private val MENU_GAP = 8.dp

/** The menu's height, counted with its divider, so it can be held inside the window. */
private val MENU_HEIGHT = MENU_PADDING * 2 + MENU_ROW_HEIGHT * 3 + 8.dp

/**
 * The Kitty pet, when it lives inside KittyTune's own window instead of a floating one.
 *
 * This is what "keep the cat in the window" means: the same widget (name pill, love meter, scene and
 * status bubble) drawn over the app's panels, but held by the window's edges. Dragging it only moves
 * it within the window; it is never allowed outside, and it never takes focus over another app the
 * way the mini player does. Its place is kept as a fraction of the room it has, so resizing the
 * window keeps it in the same corner.
 */
@Composable
fun PetInAppOverlay(
    onOpenSettings: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!PetWindowState.visible || PetWindowState.placement != PetPlacement.IN_APP) return
    PetInAppContent(onOpenSettings = onOpenSettings, onPlayPause = onPlayPause, modifier = modifier)
}

/**
 * The cat itself, without the visibility check: [initialFraction] lets a preview or a render test
 * place it somewhere without reading (or writing) the stored position.
 */
@Composable
internal fun PetInAppContent(
    onOpenSettings: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    initialFraction: Pair<Float, Float>? = null,
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize().padding(EDGE)) {
        // A window smaller than the card still gets the cat: it shrinks rather than overflowing.
        val cardW = if (maxWidth < CARD_WIDTH) maxWidth else CARD_WIDTH
        val cardH = if (maxHeight < CARD_HEIGHT) maxHeight else CARD_HEIGHT
        val freeW = (maxWidth - cardW).coerceAtLeast(0.dp)
        val freeH = (maxHeight - cardH).coerceAtLeast(0.dp)

        val freeWpx = with(density) { freeW.toPx() }
        val freeHpx = with(density) { freeH.toPx() }
        val overlayWpx = with(density) { maxWidth.toPx() }
        val overlayHpx = with(density) { maxHeight.toPx() }
        val cardWpx = with(density) { cardW.roundToPx() }
        val cardHpx = with(density) { cardH.roundToPx() }

        val saved = remember { initialFraction ?: PetConfig.inAppPosition() }
        var fx by remember { mutableStateOf(saved.first) }
        var fy by remember { mutableStateOf(saved.second) }
        var menuOpen by remember { mutableStateOf(false) }

        val xPx = PetInAppGeometry.offsetPx(fx, freeWpx)
        val yPx = PetInAppGeometry.offsetPx(fy, freeHpx)

        // Clicking anywhere else closes the menu without also clicking what is under it.
        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { menuOpen = false }
            )
        }

        Box(
            Modifier
                .offset { IntOffset(xPx, yPx) }
                .size(cardW, cardH)
                .shadow(18.dp, RoundedCornerShape(24.dp))
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.button == PointerButton.Secondary) {
                        event.changes.forEach { it.consume() }
                        menuOpen = true
                    }
                }
                .pointerInput(freeWpx, freeHpx) {
                    detectDragGestures(
                        onDragEnd = { PetConfig.saveInAppPosition(fx, fy) },
                        onDragCancel = { PetConfig.saveInAppPosition(fx, fy) },
                    ) { change, drag ->
                        change.consume()
                        fx = PetInAppGeometry.dragFraction(fx, drag.x, freeWpx)
                        fy = PetInAppGeometry.dragFraction(fy, drag.y, freeHpx)
                    }
                }
        ) {
            // The app is already open here, so its "open the app" tap action has nothing to do.
            PetWidgetContent(onOpenApp = {}, onPlayPause = onPlayPause)
        }

        if (menuOpen) {
            PetInWindowMenu(
                cardX = xPx,
                cardY = yPx,
                cardW = cardWpx,
                cardH = cardHpx,
                overlayW = overlayWpx,
                overlayH = overlayHpx,
                onDismiss = { menuOpen = false },
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

/**
 * The cat's right-click menu, drawn inside the app's window rather than in a popup window of its
 * own: the whole point of this placement is that nothing of the pet ever floats outside it.
 */
@Composable
private fun PetInWindowMenu(
    cardX: Int,
    cardY: Int,
    cardW: Int,
    cardH: Int,
    overlayW: Float,
    overlayH: Float,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val density = LocalDensity.current
    val menuWpx = with(density) { MENU_WIDTH.roundToPx() }
    val menuHpx = with(density) { MENU_HEIGHT.roundToPx() }
    val gapPx = with(density) { MENU_GAP.roundToPx() }

    // Beside the cat when there is room, on its other side otherwise, and always inside the window.
    val rightRoom = overlayW - (cardX + cardW)
    val beside = if (rightRoom >= menuWpx + gapPx) cardX + cardW + gapPx else cardX - menuWpx - gapPx
    val left = beside.coerceIn(0, (overlayW - menuWpx).toInt().coerceAtLeast(0))
    val top = cardY.coerceIn(0, (overlayH - menuHpx).toInt().coerceAtLeast(0))

    Surface(
        modifier = Modifier
            .offset { IntOffset(left, top) }
            .width(MENU_WIDTH),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(vertical = MENU_PADDING)) {
            PetInWindowMenuItem(
                icon = Icons.Rounded.DesktopWindows,
                text = str("pet_desk_show"),
                onClick = {
                    onDismiss()
                    PetWindowState.moveTo(PetPlacement.DESKTOP)
                },
            )
            PetInWindowMenuItem(
                icon = Icons.Rounded.Pets,
                text = str("pet_settings_title"),
                onClick = { onDismiss(); onOpenSettings() },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            PetInWindowMenuItem(
                icon = Icons.Rounded.Close,
                text = str("pet_desk_hide"),
                danger = true,
                onClick = { onDismiss(); PetWindowState.show(false) },
            )
        }
    }
}

@Composable
private fun PetInWindowMenuItem(
    icon: ImageVector,
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MENU_ROW_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/**
 * The arithmetic of keeping the cat inside the window, kept out of the composition so it can be
 * checked without a screen: the cat's place is a fraction of the room left to move in.
 */
internal object PetInAppGeometry {

    /** The cat's left/top in px for its fraction of the free room. */
    fun offsetPx(fraction: Float, freePx: Float): Int {
        if (freePx <= 0f) return 0
        return (fraction.coerceIn(0f, 1f) * freePx).roundToInt()
    }

    /** The new fraction after dragging by [dragPx], held inside the window. */
    fun dragFraction(fraction: Float, dragPx: Float, freePx: Float): Float {
        if (freePx <= 0f) return fraction.coerceIn(0f, 1f)
        return (fraction + dragPx / freePx).coerceIn(0f, 1f)
    }
}

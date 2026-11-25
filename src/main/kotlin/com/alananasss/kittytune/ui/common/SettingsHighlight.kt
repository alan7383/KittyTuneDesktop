package com.alananasss.kittytune.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

internal class SettingsHighlight(val key: String, val finish: (SettingsHighlight) -> Unit) {
    var isCentering = false
        private set

    suspend fun center(
        target: LayoutCoordinates,
        viewport: SettingsScrollViewport?,
        requester: BringIntoViewRequester,
    ) {
        try {
            if (viewport?.center(target) == true) return
            isCentering = true
            requester.bringIntoView()
        } catch (_: CancellationException) {
            // A scroll request can stop at the end of the list. The row should still pulse there.
            currentCoroutineContext().ensureActive()
        } finally {
            isCentering = false
        }
    }
}

internal val LocalSettingsHighlight = compositionLocalOf<SettingsHighlight?> { null }
internal val LocalSettingsScrollViewport = compositionLocalOf<SettingsScrollViewport?> { null }

internal class SettingsScrollViewport(
    private val state: ScrollableState,
    private val barOverlap: PlayerBarOverlap,
    private val reverseLayout: Boolean,
) {
    private var coordinates by mutableStateOf<LayoutCoordinates?>(null)
    val isPlaced: Boolean get() = coordinates?.isAttached == true
    val modifier = Modifier.onGloballyPositioned { coordinates = it }

    suspend fun center(target: LayoutCoordinates): Boolean {
        val viewport = coordinates?.takeIf { it.isAttached } ?: return false
        if (!target.isAttached || target.findRootCoordinates() !== viewport.findRootCoordinates()) return false

        val visibleHeight = viewport.size.height - barOverlap.overlapPx
        if (visibleHeight < target.size.height || visibleHeight <= 0f) return false
        val targetCenter = viewport.localPositionOf(target, Offset(0f, target.size.height / 2f)).y
        val distance = targetCenter - visibleHeight / 2f
        if (!distance.isFinite()) return false
        if (abs(distance) > 0.5f) {
            state.animateScrollBy(if (reverseLayout) -distance else distance, tween(260))
        }
        return true
    }
}

@Composable
internal fun rememberSettingsScrollViewport(
    state: ScrollableState,
    barOverlap: PlayerBarOverlap,
    reverseLayout: Boolean = false,
): SettingsScrollViewport? {
    val highlighting = LocalSettingsHighlight.current != null
    return remember(state, barOverlap, reverseLayout, highlighting) {
        if (highlighting) SettingsScrollViewport(state, barOverlap, reverseLayout) else null
    }
}

/** Provides the same highlight request to the target and its scrollable ancestors. */
@Composable
internal fun SettingsHighlightScope(request: SettingsHighlight?, content: @Composable () -> Unit) {
    val defaultSpec = LocalBringIntoViewSpec.current
    val scrollSpec = remember(request, defaultSpec) {
        if (request == null) defaultSpec else object : BringIntoViewSpec by defaultSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (request.isCentering && size <= containerSize) {
                    offset - (containerSize - size) / 2f
                } else {
                    defaultSpec.calculateScrollDistance(offset, size, containerSize)
                }
        }
    }
    CompositionLocalProvider(
        LocalSettingsHighlight provides request,
        LocalBringIntoViewSpec provides scrollSpec,
        content = content,
    )
}

/** Centers the search destination in its scroll pane after layout, then pulses it. */
fun Modifier.settingHighlight(highlightKey: String?, shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    if (highlightKey == null) return this
    return composed {
        val request = LocalSettingsHighlight.current?.takeIf { it.key == highlightKey } ?: return@composed this
        val viewport = LocalSettingsScrollViewport.current
        val requester = remember { BringIntoViewRequester() }
        val pulse = remember(request) { Animatable(0f) }
        val color = MaterialTheme.colorScheme.primary
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val ready = coordinates?.isAttached == true && viewport?.isPlaced != false

        LaunchedEffect(request, ready) {
            if (ready) {
                withFrameNanos { }
                val target = coordinates?.takeIf { it.isAttached } ?: return@LaunchedEffect
                request.center(target, viewport, requester)
                repeat(2) {
                    pulse.animateTo(0.26f, tween(200))
                    pulse.animateTo(0f, tween(200))
                }
                pulse.animateTo(0.26f, tween(200))
                delay(900)
                pulse.animateTo(0f, tween(400))
                request.finish(request)
            }
        }

        this.bringIntoViewRequester(requester)
            .onGloballyPositioned { coordinates = it }
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (pulse.value > 0f) drawRect(color.copy(alpha = pulse.value))
            }
    }
}

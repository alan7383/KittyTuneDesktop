@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package com.alananasss.kittytune.ui.profile.recap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.GlobalShortcutDispatcher
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.ListeningRecap
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.imageio.ImageIO

/** The keys the story takes over from the player while it is open. */
private val STORY_KEYS = setOf(Key.Spacebar, Key.DirectionLeft, Key.DirectionRight)

/** How long a slide stays before the story moves on by itself. */
private const val SLIDE_MS = 7_000

/**
 * A recap as a story: full-page slides that advance by themselves, with a segmented progress bar on
 * top. Click left/right of the page or press ← / → to navigate, Space to pause, Esc to close. The
 * last slide sums it up and can be shared as an image.
 */
@Composable
fun RecapStoryScreen(
    recapId: String,
    onClose: () -> Unit,
    onTrackClick: (ReportTrack) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    val viewModel: RecapStoryViewModel = viewModel { RecapStoryViewModel() }
    LaunchedEffect(recapId) { viewModel.load(recapId) }
    val recap = viewModel.recap

    // Esc goes through the app's back stack, like every other screen.
    BackHandler(onBack = onClose)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            recap != null -> Story(recap, onClose, onTrackClick, onArtistClick)
            viewModel.isMissing -> Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(str("recap_missing"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onClose, shapes = ButtonDefaults.shapes()) { Text(str("btn_close")) }
            }
            else -> ContainedLoadingIndicator(Modifier.align(Alignment.Center).size(72.dp))
        }
    }
}

@Composable
private fun Story(
    recap: ListeningRecap,
    onClose: () -> Unit,
    onTrackClick: (ReportTrack) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val slides = remember(recap) { slidesFor(recap) }
    var index by rememberSaveable(recap.period.id) { mutableIntStateOf(0) }
    if (index > slides.lastIndex) index = slides.lastIndex
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shareLayer = rememberGraphicsLayer()
    val shareTitle = str("recap_share_text", recap.period.title())

    val slide = slides[index]
    val isLast = index == slides.lastIndex

    fun go(to: Int) { index = to.coerceIn(0, slides.lastIndex) }

    var clockIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(index, paused) {
        if (clockIndex != index) { progress.snapTo(0f); clockIndex = index }
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * SLIDE_MS).toInt()
        progress.animateTo(1f, tween(remaining.coerceAtLeast(1), easing = LinearEasing))
        if (index < slides.lastIndex) index++
    }

    // Space and the arrows are window-wide player shortcuts (play/pause, seek ±5 s), taken before any
    // screen sees the key — so a key handler here never ran, and Space started the music. While the
    // story is open it takes them first, as the sleep screen does; Shift+arrows still change track.
    val onKey by rememberUpdatedState<(Key) -> Unit> { key ->
        when (key) {
            Key.DirectionLeft -> go(index - 1)
            Key.DirectionRight -> go(index + 1)
            Key.Spacebar -> paused = !paused
            else -> Unit
        }
    }
    DisposableEffect(Unit) {
        val previous = GlobalShortcutDispatcher.interceptor
        GlobalShortcutDispatcher.interceptor = { event ->
            val plain = !event.isShiftPressed && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed
            if (plain && event.key in STORY_KEYS) {
                if (event.type == KeyEventType.KeyDown) onKey(event.key)
                true
            } else {
                previous?.invoke(event) ?: false
            }
        }
        onDispose { GlobalShortcutDispatcher.interceptor = previous }
    }

    val palette = slide.palette(scheme, recap)
    val container by animateColorAsState(palette.container, tween(500), label = "slideContainer")
    val content by animateColorAsState(palette.onContainer, tween(500), label = "slideContent")
    val accent by animateColorAsState(palette.accent, tween(500), label = "slideAccent")
    val onAccent by animateColorAsState(palette.onAccent, tween(500), label = "slideOnAccent")

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(container)
            .pointerInput(slides.size) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val press = event.changes.firstOrNull { it.pressed && it.changedToDown() }
                        if (press != null) {
                            val x = press.position.x
                            if (x < size.width * 0.3f) go(index - 1) else if (x > size.width * 0.7f) go(index + 1)
                        }
                    }
                }
            }
    ) {
        MorphingBackdrop(slide.polygon(recap), accent.copy(alpha = 0.18f))

        CompositionLocalProvider(LocalContentColor provides content, LocalRecapOnAccent provides onAccent) {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
                StoryTopBar(recap, slides.size, index, { progress.value }, paused, { paused = !paused }, onClose)
                AnimatedContent(
                    targetState = index,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                    label = "recapSlide",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { shown ->
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val viewport = maxHeight
                        Box(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .widthIn(max = 720.dp)
                                    .fillMaxWidth()
                                    .then(
                                        if (slides[shown] == RecapSlide.SUMMARY) {
                                            Modifier.drawWithContent {
                                                shareLayer.record { this@drawWithContent.drawContent() }
                                                drawLayer(shareLayer)
                                            }
                                        } else Modifier
                                    ),
                            ) {
                                SlideContent(slides[shown], recap, accent, onTrackClick, onArtistClick)
                            }
                        }
                    }
                }
                if (isLast) {
                    StoryActions(
                        container = container,
                        onShare = {
                            scope.launch {
                                val bitmap = runCatching { shareLayer.toImageBitmap() }.getOrNull()
                                shareRecap(bitmap, recap, shareTitle)
                            }
                        },
                        onReplay = { go(0) },
                    )
                } else Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun androidx.compose.ui.input.pointer.PointerInputChange.changedToDown(): Boolean =
    pressed && !previousPressed

@Composable
private fun StoryTopBar(
    recap: ListeningRecap,
    count: Int,
    index: Int,
    progress: () -> Float,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onClose: () -> Unit,
) {
    val content = LocalContentColor.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { i ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(content.copy(alpha = 0.25f))
                ) {
                    val fill = when { i < index -> 1f; i == index -> progress(); else -> 0f }
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clip(CircleShape).background(content))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(recap.period.polygon().toShape()).background(content.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(recap.period.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(recap.period.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(recap.period.kindLabel(), style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.75f), maxLines = 1)
            }
            val buttonColors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = content.copy(alpha = 0.14f),
                contentColor = content,
            )
            FilledTonalIconButton(onClick = onTogglePause, shapes = IconButtonDefaults.shapes(), colors = buttonColors) {
                Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = str(if (paused) "recap_resume" else "recap_pause"))
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(onClick = onClose, shapes = IconButtonDefaults.shapes(), colors = buttonColors) {
                Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
            }
        }
    }
}

@Composable
private fun StoryActions(container: Color, onShare: () -> Unit, onReplay: () -> Unit) {
    val content = LocalContentColor.current
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing, Alignment.CenterHorizontally),
    ) {
        FilledTonalButton(
            onClick = onReplay,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = content.copy(alpha = 0.14f), contentColor = content),
        ) {
            Icon(Icons.Rounded.Replay, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(str("recap_replay"))
        }
        Button(
            onClick = onShare,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = container),
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(str("recap_share"))
        }
    }
}

/**
 * A big expressive shape behind the slides, turning slowly and morphing into the next slide's shape.
 *
 * Drawn as a path rather than clipped through `toShape()`, so the morph is a real one — the shape used
 * to jump from one to the other in a frame. The progress runs on an easing that never passes 1: a
 * bouncy spring overshot it, and a [Morph] read beyond its end extrapolates into a distorted blob.
 * It is sized from the frame's shorter side and kept whole inside it, turning included.
 */
@Composable
private fun MorphingBackdrop(target: RoundedPolygon, color: Color) {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val morphProgress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        // Interrupted halfway (slides flicked quickly): start from whichever end it is closer to,
        // rather than snapping back to the old shape.
        from = if (morphProgress.value >= 0.5f) to else from
        to = target
        morphProgress.snapTo(0f)
        morphProgress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val morph = remember(from, to) { Morph(from, to) }
    // On the shared ambient pulse (see AmbientClock): a forty second turn needs none of the display's frames,
    // and no frame at all while the window is hidden.
    val angle = com.alananasss.kittytune.ui.common.rememberAmbientLoop(
        periodMs = 40_000,
        from = 0f,
        to = 360f,
    )
    val path = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        // Whole at every angle: a shape turning about its centre stays inside a circle of radius
        // ~0.71 × its side, so a side of 0.7 × the frame's short edge, centred that far in from the
        // right, never reaches an edge.
        val side = minOf(size.width, size.height) * 0.7f
        val left = size.width - side * 1.25f
        val top = (size.height - side) / 2f
        path.reset()
        var first = true
        morph.forEachCubic(morphProgress.value.coerceIn(0f, 1f)) { c ->
            if (first) {
                path.moveTo(c.anchor0X, c.anchor0Y)
                first = false
            }
            path.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
        }
        path.close()
        translate(left, top) {
            rotate(angle.value, pivot = Offset(side / 2f, side / 2f)) {
                scale(side, side, pivot = Offset.Zero) {
                    drawPath(path, color)
                }
            }
        }
    }
}

/**
 * Writes the summary to the user's temp dir, opens it in the OS image viewer, and copies the path to
 * the clipboard. Desktop has no SEND intent; opening + clipboard is the common denominator.
 */
private suspend fun shareRecap(bitmap: ImageBitmap?, recap: ListeningRecap, caption: String) {
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(System.getProperty("java.io.tmpdir"), "kittytune-recaps").apply { mkdirs() }
            val file = File(dir, "recap-${recap.period.id}.png")
            if (bitmap != null) {
                val awt = bitmap.toAwtImage()
                file.outputStream().use { out -> ImageIO.write(awt, "png", out) }
            }
            // Open in the default image viewer so the user can save or drag it anywhere.
            if (Desktop.isDesktopSupported() && file.exists()) {
                runCatching { Desktop.getDesktop().open(file) }
            }
            // And drop the path on the clipboard so a chat/mail paste just works.
            runCatching {
                val transfer = object : Transferable {
                    override fun getTransferDataFlavors() = arrayOf(DataFlavor.stringFlavor)
                    override fun isDataFlavorSupported(flavor: DataFlavor?) = flavor == DataFlavor.stringFlavor
                    override fun getTransferData(flavor: DataFlavor?): Any = "$caption\n${file.absolutePath}"
                }
                Toolkit.getDefaultToolkit().systemClipboard.setContents(transfer, null)
            }
        }
    }
}

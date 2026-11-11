@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.LocalAmbientClock
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.common.withAmbientFrameNanos
import kotlin.math.cos
import kotlin.math.sin

/** Each step wears its own Material shape; the hero morphs from one to the next as the setup advances. */
internal val SetupStep.polygon: RoundedPolygon
    get() = when (this) {
        SetupStep.WELCOME -> MaterialShapes.Cookie12Sided
        SetupStep.APPEARANCE -> MaterialShapes.Flower
        SetupStep.ACCOUNT -> MaterialShapes.Clover4Leaf
        SetupStep.DESKTOP -> MaterialShapes.Cookie4Sided
        SetupStep.LIBRARY -> MaterialShapes.Gem
        SetupStep.DISCORD -> MaterialShapes.Cookie6Sided
        SetupStep.PROTECTED -> MaterialShapes.Clover8Leaf
        SetupStep.READY -> MaterialShapes.SoftBurst
    }

/** The step's mark. SoundCloud and Discord keep their own logos, which Material's icon set does not have. */
@Composable
internal fun StepIcon(step: SetupStep, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    when (step) {
        SetupStep.ACCOUNT -> Icon(painterResource("icons/soundcloud.svg"), null, modifier.size(size, size * 0.625f), tint = tint)
        SetupStep.DISCORD -> Icon(painterResource("icons/discord.svg"), null, modifier.size(size), tint = tint)
        else -> Icon(step.icon, null, modifier.size(size), tint = tint)
    }
}

private fun Morph.writeTo(path: Path, progress: Float) {
    path.rewind()
    var first = true
    forEachCubic(progress) { cubic ->
        if (first) {
            path.moveTo(cubic.anchor0X, cubic.anchor0Y)
            first = false
        }
        path.cubicTo(cubic.control0X, cubic.control0Y, cubic.control1X, cubic.control1Y, cubic.anchor1X, cubic.anchor1Y)
    }
    path.close()
}

/** Material's polygons are normalised to the unit square, so the path is scaled up to the box here. */
private fun DrawScope.drawUnitPath(path: Path, color: Color, degrees: Float) {
    val side = size.minDimension
    translate((size.width - side) / 2f, (size.height - side) / 2f) {
        rotate(degrees, pivot = Offset(side / 2f, side / 2f)) {
            scale(side, side, pivot = Offset.Zero) { drawPath(path, color) }
        }
    }
}

/**
 * A filled Material shape that morphs, with a spring, whenever [polygon] changes.
 *
 * Only the draw phase follows the animation: the morph and the rotation are read inside the draw lambda, so a
 * frame of it costs one path and no recomposition.
 */
@Composable
internal fun MorphingShape(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    rotation: () -> Float = { 0f },
    content: @Composable BoxScope.() -> Unit = {},
) {
    var from by remember { mutableStateOf(polygon) }
    var to by remember { mutableStateOf(polygon) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(polygon) {
        if (polygon !== to) {
            from = to
            to = polygon
            progress.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 170f))
        }
    }
    val morph = remember(from, to) { Morph(from, to) }
    val path = remember { Path() }
    Box(
        modifier.drawBehind {
            morph.writeTo(path, progress.value)
            drawUnitPath(path, color, rotation())
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A slow, endless turn on the window's ambient clock, so it never asks for more frames than the app's other decorations. */
@Composable
private fun rememberDrift(degreesPerSecond: Float): FloatState {
    val clock = LocalAmbientClock.current
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(clock) {
        val start = withAmbientFrameNanos(clock) { it }
        while (true) {
            withAmbientFrameNanos(clock) { angle.floatValue = ((it - start) / 1e9f * degreesPerSecond) % 360f }
        }
    }
    return angle
}

private fun GraphicsLayerScope.orbit(degrees: Float, radius: Float) {
    val radians = Math.toRadians(degrees.toDouble())
    translationX = (cos(radians) * radius).toFloat()
    translationY = (sin(radians) * radius).toFloat()
}

/**
 * The left pane on a wide window: one large shape that morphs and turns whenever [polygon] changes, two smaller
 * ones that orbit it, and whatever the screen wants to say underneath.
 *
 * [phase] is how far along the screen is; each increment gives the shapes a push round.
 */
@Composable
internal fun HeroPane(
    polygon: RoundedPolygon,
    phase: Int,
    modifier: Modifier = Modifier,
    icon: @Composable (size: Dp) -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val steps = SetupStep.entries
    val drift = rememberDrift(4f)
    val kick by animateFloatAsState(phase * 50f, spring(dampingRatio = 0.5f, stiffness = 90f), label = "heroKick")
    val orbit by animateFloatAsState(phase * 64f - 48f, spring(dampingRatio = 0.7f, stiffness = 60f), label = "heroOrbit")
    Surface(modifier, shape = RoundedCornerShape(40.dp), color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource("icons/kittytune.svg"), null, Modifier.size(32.dp))
                Text("KittyTune", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val main = minOf(maxWidth * 0.8f, maxHeight * 0.62f, 240.dp)
                Box(Modifier.size(main), contentAlignment = Alignment.Center) {
                    MorphingShape(
                        steps[(phase + 3) % steps.size].polygon, cs.tertiary,
                        Modifier.size(main * 0.28f).graphicsLayer { orbit(orbit, main.toPx() * 0.56f) },
                        rotation = { -drift.floatValue * 2f - kick },
                    )
                    MorphingShape(polygon, cs.primary, Modifier.fillMaxSize(), rotation = { drift.floatValue + kick }) { icon(main * 0.4f) }
                    MorphingShape(
                        steps[(phase + 5) % steps.size].polygon, cs.secondary,
                        Modifier.size(main * 0.2f).graphicsLayer { orbit(orbit + 155f, main.toPx() * 0.58f) },
                        rotation = { drift.floatValue * 3f + kick },
                    )
                }
            }
            footer()
        }
    }
}

/** The setup's hero: the current step's shape and mark, with the step count and progress underneath. */
@Composable
internal fun SetupHero(step: SetupStep, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val steps = SetupStep.entries
    val index = step.ordinal
    val progress by animateFloatAsState((index + 1f) / steps.size, spring(dampingRatio = 1f, stiffness = 120f), label = "heroProgress")
    HeroPane(step.polygon, index, modifier, icon = { size ->
        AnimatedContent(step, transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.5f, stiffness = 380f), initialScale = 0.3f) + fadeIn(tween(120))) togetherWith
                (scaleOut(tween(120), targetScale = 0.3f) + fadeOut(tween(90))) using SizeTransform(clip = false)
        }, label = "heroIcon") { shown -> StepIcon(shown, size, cs.onPrimary) }
    }) {
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(index, transitionSpec = {
                val way = if (targetState > initialState) 1 else -1
                (slideInVertically(spring(dampingRatio = 0.7f, stiffness = 400f)) { it * way } + fadeIn(tween(160))) togetherWith
                    (slideOutVertically(spring(dampingRatio = 1f, stiffness = 400f)) { -it * way } + fadeOut(tween(120))) using SizeTransform(clip = false)
            }, label = "heroCount") { shown ->
                Text("${shown + 1}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            }
            Text(" / ${steps.size}", Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.titleLarge,
                color = cs.onPrimaryContainer.copy(alpha = 0.6f))
        }
        AnimatedContent(step, transitionSpec = {
            val way = if (targetState > initialState) 1 else -1
            (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it / 2 * way } + fadeIn(tween(160, 40))) togetherWith
                (slideOutVertically(tween(120)) { -it / 2 * way } + fadeOut(tween(90))) using SizeTransform(clip = false)
        }, label = "heroLabel") { shown ->
            Text(str("onboard_" + shown.key + "_label"), style = MaterialTheme.typography.titleMedium, maxLines = 2)
        }
        Spacer(Modifier.height(18.dp))
        LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
            color = cs.primary, trackColor = cs.onPrimaryContainer.copy(alpha = 0.18f))
    }
}

/** The steps as a row of dots: the current one stretches into a pill, and any of them can be clicked to jump there. */
@Composable
internal fun StepIndicator(index: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        SetupStep.entries.forEachIndexed { i, step ->
            val current = i == index
            val width by animateDpAsState(if (current) 36.dp else 10.dp, spring(dampingRatio = 0.55f, stiffness = 500f), label = "stepWidth")
            val color by animateColorAsState(
                when { current -> cs.primary; i < index -> cs.secondary; else -> cs.outlineVariant }, label = "stepColor")
            Tip(str("onboard_" + step.key + "_label")) {
                Box(
                    Modifier.height(32.dp).clip(CircleShape)
                        .selectable(selected = current, role = Role.Tab, onClick = { onSelect(i) })
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(width, 10.dp).background(color, CircleShape)) }
            }
        }
    }
}

/**
 * Brings [content] in from the side the setup is moving towards, [order] steps after the page's first element.
 *
 * The wait is itself an animation rather than a `delay`, so the whole entrance runs on the frame clock and stays
 * in step with the frames actually drawn.
 */
@Composable
internal fun Reveal(order: Int, direction: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (order > 0) Animatable(0f).animateTo(1f, tween(order * 40, easing = LinearEasing))
        shown.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 420f))
    }
    Box(modifier.graphicsLayer {
        val value = shown.value
        alpha = value.coerceIn(0f, 1f)
        translationX = (1f - value) * 48.dp.toPx() * direction
    }) { content() }
}

/** An icon on a Material shape rather than a circle. */
@Composable
internal fun ShapeBadge(
    polygon: RoundedPolygon,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.size(size).background(container, polygon.toShape()), contentAlignment = Alignment.Center, content = content)
}

/** A large clickable surface whose corners open on hover and tighten under the pointer, as expressive buttons do. */
@Composable
internal fun ExpressiveTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val corner by animateDpAsState(
        when { pressed -> 14.dp; hovered -> 40.dp; else -> 28.dp }, spring(dampingRatio = 0.6f, stiffness = 600f), label = "tileCorner")
    val zoom by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "tileZoom")
    Surface(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = zoom; scaleY = zoom },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = contentColor,
        interactionSource = interaction,
        content = content,
    )
}

/**
 * One colour choice: a disc of the palette's three accents that turns into a scalloped cookie when it is selected.
 */
@Composable
internal fun PaletteSwatch(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    primary: Color,
    modifier: Modifier = Modifier,
    secondary: Color = primary,
    tertiary: Color = primary,
    center: @Composable () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val path = remember { Path() }
    val shape by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 300f), label = "swatchShape")
    val turn by animateFloatAsState(if (selected) 40f else 0f, spring(dampingRatio = 0.6f, stiffness = 200f), label = "swatchTurn")
    val zoom by animateFloatAsState(
        when { pressed -> 0.8f; selected -> 1f; else -> 0.86f }, spring(dampingRatio = 0.5f, stiffness = 500f), label = "swatchZoom")
    val corner by animateDpAsState(if (selected) 30.dp else 20.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "swatchCorner")
    val container by animateColorAsState(if (selected) cs.secondaryContainer else cs.surfaceContainerHigh, label = "swatchContainer")
    Column(modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(corner)).background(container)
                .selectable(selected = selected, interactionSource = interaction, indication = ripple(), role = Role.RadioButton, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(50.dp).graphicsLayer { scaleX = zoom; scaleY = zoom }.drawBehind {
                    morph.writeTo(path, shape)
                    val side = size.minDimension
                    // The outline turns; the colours inside it stay level.
                    rotate(turn) {
                        scale(side, side, pivot = Offset.Zero) {
                            clipPath(path) {
                                rotate(-turn, pivot = Offset(0.5f, 0.5f)) {
                                    drawRect(primary, Offset(-0.5f, -0.5f), Size(2f, 1f))
                                    drawRect(secondary, Offset(-0.5f, 0.5f), Size(1f, 1f))
                                    drawRect(tertiary, Offset(0.5f, 0.5f), Size(1f, 1f))
                                }
                            }
                        }
                    }
                },
                contentAlignment = Alignment.Center,
            ) { center() }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
            color = if (selected) cs.primary else cs.onSurfaceVariant)
    }
}

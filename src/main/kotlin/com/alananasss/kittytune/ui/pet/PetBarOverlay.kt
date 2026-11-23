@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.alananasss.kittytune.ui.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import kotlinx.coroutines.delay
import java.awt.Cursor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Where the player bar is in the window, reported by the bar itself as it is laid out. */
object PetBarAnchor {
    var bounds by mutableStateOf<Rect?>(null)
        private set

    fun report(coordinates: LayoutCoordinates) {
        val next = if (coordinates.isAttached) coordinates.boundsInWindow().takeIf { it.width > 0f && it.height > 0f } else null
        if (next != bounds) bounds = next
    }
}

/** One screen pixel of the cat's 46×32 art. Small enough to stay out of the way, big enough to read. */
private val PIXEL = 2.dp

/** The cat's feet sink this far into the bar, so it sits on it rather than floating over it. */
private val SINK = 3.dp

/** Room kept from the bar's ends (rounded corners, the cover on the left). */
private val END_MARGIN = 28.dp

/** Walking speed, in dp per second. */
private const val WALK_DP_PER_S = 34f

/** After the music stops: this long awake (wandering, sitting), then asleep. */
private const val AWAKE_AFTER_MUSIC_MS = 75_000L

private const val CELEBRATE_MS = 4_500L

/**
 * The cat's frames for one activity, cropped to the box all of them share: the art is drawn on a
 * 46×32 canvas with a room around it, but here there is no room, only the cat on the bar, so its feet
 * must sit on the bottom edge and nothing invisible may catch clicks.
 */
internal class CroppedFrames(val images: List<ImageBitmap>, val width: Int, val height: Int)

internal object PetBarFrames {
    private val cache = object : LinkedHashMap<Any, CroppedFrames>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, CroppedFrames>?) = size > 24
    }

    @Synchronized
    fun of(frames: List<String>, settings: PetSettings): CroppedFrames {
        val key = frames to settings
        cache[key]?.let { return it }
        val w = PetSprites.W
        val h = PetSprites.H
        val pixels = frames.map { PetComposer.cat(it, settings) }
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (p in pixels) for (y in 0 until h) for (x in 0 until w) {
            if (p[y * w + x] ushr 24 != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) { minX = 0; minY = 0; maxX = w - 1; maxY = h - 1 }
        val cw = maxX - minX + 1
        val ch = maxY - minY + 1
        val images = pixels.map { p ->
            val out = IntArray(cw * ch)
            for (y in 0 until ch) System.arraycopy(p, (y + minY) * w + minX, out, y * cw, cw)
            PetComposer.toImage(out, cw, ch)
        }
        return CroppedFrames(images, cw, ch).also { cache[key] = it }
    }
}

/**
 * The Kitty pet on the player bar: a small cat, no card around it, standing on the bar's top edge.
 * It dances while music plays, wanders and sits for a moment once it stops, then falls asleep. Its
 * daily streak ([PetStreak]) decides the rest: sad while a missed day waits to be restored, gone
 * (a faint paw print left behind) when it has gone too long without music.
 *
 * Click it for its tap action; right-click (or click the paw print) for its streak and options.
 */
@Composable
fun PetBarOverlay(
    onOpenSettings: () -> Unit,
    onPlayPause: () -> Unit,
) {
    if (!PetWindowState.visible || PetWindowState.placement != PetPlacement.BAR) return
    val bar = PetBarAnchor.bounds ?: return
    PetBarContent(bar, onOpenSettings, onPlayPause)
}

/** The cat on [bar], without the visibility check; [forcedActivity] pins a pose for previews. */
@Composable
internal fun PetBarContent(
    bar: Rect,
    onOpenSettings: () -> Unit,
    onPlayPause: () -> Unit,
    forcedActivity: PetActivity? = null,
    forcedStreak: PetStreak.Status? = null,
    initialMenuOpen: Boolean = false,
) {

    val density = LocalDensity.current
    val version by PetConfig.version.collectAsState()
    val settings = remember(version) { PetConfig.load() }
    val state = PetState.snapshot
    val streak = forcedStreak ?: PetStreak.status

    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var menuOpen by remember { mutableStateOf(initialMenuOpen) }
    var hovered by remember { mutableStateOf(false) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    // Fed for the day: a short "yum" and the new count, once, when it happens.
    var celebrateUntil by remember { mutableLongStateOf(0L) }
    var lastSeen by remember { mutableStateOf<PetStreak.Status?>(null) }
    LaunchedEffect(streak) {
        val before = lastSeen
        if (before != null && before.mood != PetStreak.Mood.FED && streak.mood == PetStreak.Mood.FED) {
            celebrateUntil = System.currentTimeMillis() + CELEBRATE_MS
        }
        lastSeen = streak
    }

    val gone = streak.mood == PetStreak.Mood.GONE
    val clock = maxOf(now, state.tick)
    val booped = clock < state.boopUntil
    val celebrating = clock < celebrateUntil
    val awake = state.playing || clock - state.lastMusicAt < AWAKE_AFTER_MUSIC_MS

    // Walking only happens awake, without music, when nothing else is going on.
    var walking by remember { mutableStateOf(false) }
    var facingLeft by remember { mutableStateOf(false) }
    val mayWander = awake && !state.playing && !booped && !celebrating && !menuOpen && !gone

    val activity = forcedActivity ?: when {
        booped -> if (state.playing) PetActivity.BOOP_DANCE else PetActivity.BOOP
        celebrating -> PetActivity.EAT
        state.playing -> if (state.quiet) PetActivity.DANCE_SWAY else state.dance
        walking -> PetActivity.IDLE
        !awake -> PetActivity.SLEEP
        streak.mood == PetStreak.Mood.RESTORABLE -> PetActivity.SAD_LOAF
        else -> PetActivity.LOAF
    }

    val frames = remember(activity, settings) { PetBarFrames.of(PetBrain.framesFor(activity), settings) }
    var frame by remember(activity) { mutableIntStateOf(0) }
    val latestFrames by rememberUpdatedState(frames)
    val fallbackBpm by rememberUpdatedState(if (state.liveBpm > 0f) state.liveBpm else state.bpm)
    LaunchedEffect(activity) {
        while (true) {
            val count = latestFrames.images.size
            if (activity.isDance) {
                val next = PetState.nextHeardBeat(minAheadMs = 30)
                if (next != null) {
                    delay(next.first.coerceAtLeast(0))
                    frame = (((next.second % count) + count) % count).toInt()
                    continue
                }
                delay(if (fallbackBpm > 0f) PetBrain.frameMsForBpm(fallbackBpm).toLong() else activity.frameMs.toLong())
            } else {
                delay((activity.frameMs * settings.speed.factor).toLong())
            }
            frame = (frame + 1) % count
        }
    }

    val catW = PIXEL * frames.width
    val catH = PIXEL * frames.height
    val catWpx = with(density) { catW.toPx() }
    val marginPx = with(density) { END_MARGIN.toPx() }
    val travelPx = (bar.width - marginPx * 2 - catWpx).coerceAtLeast(0f)

    // Where along the bar it is, as a share of the room it has: a resized window keeps its spot.
    val position = remember { Animatable(PetBarPlace.fraction) }
    LaunchedEffect(mayWander, travelPx) {
        if (!mayWander || travelPx <= 0f) {
            walking = false
            return@LaunchedEffect
        }
        while (true) {
            delay(Random.nextLong(3_000L, 9_000L))
            val from = position.value
            val to = (from + Random.nextFloat() * 0.5f - 0.25f).coerceIn(0f, 1f)
            val distanceDp = with(density) { (abs(to - from) * travelPx).toDp().value }
            if (distanceDp < 12f) continue
            facingLeft = to < from
            walking = true
            position.animateTo(to, tween((distanceDp / WALK_DP_PER_S * 1000f).toInt(), easing = LinearEasing))
            PetBarPlace.fraction = to
            walking = false
        }
    }

    // A small hop on each step while it walks.
    val hop = remember { Animatable(0f) }
    LaunchedEffect(walking) {
        if (!walking) { hop.snapTo(0f); return@LaunchedEffect }
        while (true) {
            hop.animateTo(1f, tween(140))
            hop.animateTo(0f, tween(140))
        }
    }

    val tapAction: () -> Unit = when (settings.tap) {
        PetTap.PLAY_PAUSE -> onPlayPause
        else -> ({ PetState.boop() })
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menuOpen = false }
            )
        }

        val sinkPx = with(density) { SINK.toPx() }
        val catHpx = with(density) { catH.toPx() }
        val x = bar.left - origin.x + marginPx + position.value * travelPx
        val y = bar.top - origin.y - catHpx + sinkPx

        if (gone) {
            // It left: a faint paw print where it used to sit, which explains itself when clicked.
            val pawPx = with(density) { 18.dp.toPx() }
            Icon(
                Icons.Rounded.Pets,
                contentDescription = settings.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (hovered) 0.7f else 0.35f),
                modifier = Modifier
                    .offset { IntOffset((x + catWpx / 2 - pawPx / 2).roundToInt(), (bar.top - origin.y - pawPx - 2).roundToInt()) }
                    .size(18.dp)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { menuOpen = !menuOpen }
            )
        } else {
            Canvas(
                Modifier
                    .offset { IntOffset(x.roundToInt(), (y - hop.value * sinkPx).roundToInt()) }
                    .size(catW, catH)
                    .graphicsLayer { scaleX = if (facingLeft && walking) -1f else 1f }
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                    .onPointerEvent(PointerEventType.Enter) { hovered = true }
                    .onPointerEvent(PointerEventType.Exit) { hovered = false }
                    .onPointerEvent(PointerEventType.Press) { event ->
                        if (event.button == PointerButton.Secondary) {
                            event.changes.forEach { it.consume() }
                            menuOpen = true
                        }
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tapAction() }
            ) {
                drawPixels(frames.images[frame % frames.images.size])
            }
        }

        // The streak, only while the pointer is on the cat (or it just ate): never a permanent badge.
        val badgeShown = (hovered || celebrating) && !menuOpen && streak.days > 0 && !gone
        AnimatedVisibility(
            visible = badgeShown,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.offset {
                IntOffset((x + catWpx / 2).roundToInt() - 26.dp.roundToPx(), (y - 30.dp.toPx()).roundToInt())
            },
        ) {
            StreakChip(streak.days)
        }

        if (menuOpen) {
            val cardW = 260.dp
            val cardWpx = with(density) { cardW.toPx() }
            val left = (x + catWpx / 2 - cardWpx / 2).coerceIn(8f, (bar.right - origin.x - cardWpx - 8f).coerceAtLeast(8f))
            val bottom = (if (gone) bar.top - origin.y - with(density) { 24.dp.toPx() } else y) - with(density) { 8.dp.toPx() }
            PetBarCard(
                settings = settings,
                streak = streak,
                modifier = Modifier
                    .layoutAbove(leftPx = left, bottomPx = bottom)
                    .width(cardW),
                onDismiss = { menuOpen = false },
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

/** Places a node at [leftPx] with its bottom edge on [bottomPx]: a card opening upwards from the cat. */
private fun Modifier.layoutAbove(leftPx: Float, bottomPx: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        placeable.place(leftPx.roundToInt(), (bottomPx - placeable.height).roundToInt().coerceAtLeast(0))
    }
}

/** Remembered for the session only: where the cat stopped last. */
private object PetBarPlace {
    var fraction = 0.12f
}

@Composable
private fun StreakChip(days: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            days.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** The cat's card: its streak, today's feeding, a restore when one is due, and its options. */
@Composable
private fun PetBarCard(
    settings: PetSettings,
    streak: PetStreak.Status,
    modifier: Modifier,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val goalMin = (PetStreak.DAILY_GOAL_MS / 60_000L).toInt()
    val todayMin = (streak.todayMs / 60_000L).toInt().coerceAtMost(goalMin)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (streak.mood == PetStreak.Mood.GONE) Icons.Rounded.Pets else Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(settings.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (streak.mood) {
                            PetStreak.Mood.GONE -> str("pet_streak_gone_title")
                            PetStreak.Mood.NEW -> str("pet_streak_none")
                            PetStreak.Mood.RESTORABLE -> str("pet_streak_on_hold", streak.days)
                            else -> str("pet_streak_days", streak.days)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                when (streak.mood) {
                    PetStreak.Mood.FED -> str("pet_streak_fed")
                    PetStreak.Mood.GONE -> str("pet_streak_gone_body", settings.name, goalMin)
                    PetStreak.Mood.RESTORABLE -> str("pet_streak_restore_body", settings.name)
                    else -> str("pet_streak_goal", goalMin)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { streak.goalProgress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Text(
                    str("pet_streak_today", todayMin, goalMin),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (streak.mood == PetStreak.Mood.RESTORABLE) {
                FilledTonalButton(
                    onClick = { if (PetStreak.restore()) onDismiss() },
                    enabled = streak.canRestore,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("pet_streak_restore"))
                }
                Text(
                    if (streak.restoresLeft > 0) str("pet_streak_restores_left", streak.restoresLeft) else str("pet_streak_no_restores"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Column {
                CardAction(Icons.Rounded.Pets, str("pet_settings_title"), Modifier.fillMaxWidth()) { onDismiss(); onOpenSettings() }
                CardAction(Icons.Rounded.Close, str("pet_desk_hide"), Modifier.fillMaxWidth(), danger = true) { onDismiss(); PetWindowState.show(false) }
            }
        }
    }
}

@Composable
private fun CardAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
    }
}

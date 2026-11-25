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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import java.awt.Cursor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Where the player bar is in the window, reported by the bar itself as it is laid out. */
object PetBarAnchor {
    var bounds by mutableStateOf<Rect?>(null)
        private set

    /** The play button, which the cat walks up to and sits by when the music stops. */
    var playButton by mutableStateOf<Rect?>(null)
        private set

    fun report(coordinates: LayoutCoordinates) {
        val next = if (coordinates.isAttached) coordinates.boundsInWindow().takeIf { it.width > 0f && it.height > 0f } else null
        if (next != bounds) bounds = next
    }

    fun reportPlayButton(coordinates: LayoutCoordinates) {
        val next = if (coordinates.isAttached) coordinates.boundsInWindow().takeIf { it.width > 0f && it.height > 0f } else null
        if (next != playButton) playButton = next
    }
}

/** One screen pixel of the cat's 46×32 art. Small enough to stay out of the way, big enough to read. */
private val PIXEL = 2.dp

/** The cat's feet sink this far into the bar, so it sits on it rather than floating over it. */
private val SINK = 3.dp

/** Room kept from the bar's ends (rounded corners, the cover on the left). */
private val END_MARGIN = 28.dp

/** Walking speeds, in dp per second, for a small cat on mini paws: an amble, a walk, a trot back to its music. */
private const val STROLL_DP_PER_S = 18f
private const val WALK_DP_PER_S = 28f
private const val RUN_DP_PER_S = 62f

/** Poses that look the way it goes (drawn looking left): they turn round with it. */
internal val PetActivity.facesSideways: Boolean
    get() = this == PetActivity.WALK


/** After the music stops: this long awake (wandering, sitting), then asleep. */
private const val AWAKE_AFTER_MUSIC_MS = 75_000L

private const val CELEBRATE_MS = 4_500L

/**
 * The cat's frames, cropped to one box shared by every pose: the art is drawn on a 46×32 canvas with a room
 * around it, but on the bar there is no room, only the cat, so its feet must sit on the bottom edge. The box is
 * the same for all poses (the full width, down to the floor row), so going from one pose to the next never shifts
 * the cat by a pixel or two — what used to happen when each pose was cropped to its own size.
 */
internal class CroppedFrames(val images: List<ImageBitmap>, val width: Int, val height: Int)

internal object PetBarFrames {
    /** The shared box: the canvas' width, and its rows down to the floor the cat stands on. */
    const val BOX_W = 46
    const val BOX_H = 28

    private val cache = object : LinkedHashMap<Any, CroppedFrames>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, CroppedFrames>?) = size > 24
    }

    @Synchronized
    fun of(frames: List<String>, settings: PetSettings): CroppedFrames {
        val key = frames to settings
        cache[key]?.let { return it }
        val w = PetSprites.W
        val pixels = frames.map { PetComposer.cat(it, settings) }
        val minX = 0
        val minY = 0
        val cw = BOX_W.coerceAtMost(w)
        val ch = BOX_H.coerceAtMost(PetSprites.H)
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
    player: PetPlayerLink? = null,
) {
    if (!PetWindowState.visible || PetWindowState.placement != PetPlacement.BAR) return
    val bar = PetBarAnchor.bounds ?: return
    PetBarContent(bar, onOpenSettings, onPlayPause, player = player)
}

/** What the cat on the bar may read from the player and ask it to do; null in previews. */
class PetPlayerLink(
    val track: () -> com.alananasss.kittytune.domain.Track?,
    val positionMs: () -> Long,
    val durationMs: () -> Long,
    val liked: () -> Boolean,
    val repeatOn: () -> Boolean,
    val play: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val repeatOne: () -> Unit,
)

/** The cat on [bar], without the visibility check; [forcedActivity] pins a pose for previews. */
@Composable
internal fun PetBarContent(
    bar: Rect,
    onOpenSettings: () -> Unit,
    onPlayPause: () -> Unit,
    forcedActivity: PetActivity? = null,
    forcedStreak: PetStreak.Status? = null,
    initialMenuOpen: Boolean = false,
    player: PetPlayerLink? = null,
    forcedHint: PetHint? = null,
) {
    val density = LocalDensity.current
    val version by PetConfig.version.collectAsState()
    val settings = remember(version) { PetConfig.load() }
    val state = PetState.snapshot
    val streak = forcedStreak ?: PetStreak.status

    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var menuOpen by remember { mutableStateOf(initialMenuOpen) }
    var hovered by remember { mutableStateOf(false) }

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
    val box = PetBarFrames.BOX_W
    val catW = PIXEL * box
    val catH = PIXEL * PetBarFrames.BOX_H
    val catWpx = with(density) { catW.toPx() }
    val marginPx = with(density) { END_MARGIN.toPx() }
    val travelPx = (bar.width - marginPx * 2 - catWpx).coerceAtLeast(1f)
    fun fractionFor(windowX: Float) = ((windowX - bar.left - marginPx - catWpx / 2f) / travelPx).coerceIn(0f, 1f)

    // Its corner: the right part of the bar, away from the cover and the song's name.
    val zone = remember(bar.width) {
        val minRoom = with(density) { 140.dp.toPx() } / travelPx
        (PetBarDirector.DEFAULT_ZONE.start.coerceAtMost(1f - minRoom)).coerceAtLeast(0.5f)..PetBarDirector.DEFAULT_ZONE.endInclusive
    }
    // The spot just right of the play button, where it sits and waits for the music.
    val buttonSpot = PetBarAnchor.playButton?.let { fractionFor(it.right + with(density) { 34.dp.toPx() }) }

    val position = remember { Animatable(PetBarPlace.fraction) }
    val director = remember { PetBarDirector() }
    var scene by remember { mutableStateOf(PetBarScene(PetActivity.STAND, position.value)) }

    // What it knows about the song: how often it was played through, and skipped.
    val trackId = player?.track?.invoke()?.id ?: state.title.hashCode().toLong().takeIf { state.title.isNotEmpty() }
    var habit by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(trackId) {
        habit = 0 to 0
        val id = player?.track?.invoke()?.id ?: return@LaunchedEffect
        habit = withContext(Dispatchers.IO) {
            runCatching { com.alananasss.kittytune.data.ListeningStatsRepository.trackHabit(id) }.getOrDefault(0 to 0)
        }
    }

    // The listener's actions, as they happen.
    LaunchedEffect(Unit) {
        PetEvents.events.collect { director.onEvent(it, System.currentTimeMillis(), player?.track?.invoke()?.id) }
    }

    // The brain ticks four times a second: cheap, and quick enough to answer a press of the play button.
    val latestZone by rememberUpdatedState(zone)
    val latestButton by rememberUpdatedState(buttonSpot)
    val latestHabit by rememberUpdatedState(habit)
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val s = PetState.snapshot
            val track = player?.track?.invoke()
            val (plays, skips) = latestHabit
            val beat = s.liveBpm.takeIf { it > 0f } ?: s.bpm
            scene = director.update(
                PetBarInput(
                    now = now,
                    playing = s.playing,
                    hasTrack = track != null || s.title.isNotEmpty(),
                    trackId = track?.id,
                    favourite = (player?.liked?.invoke() == true) || plays >= 4,
                    plays = plays,
                    skips = skips,
                    positionMs = player?.positionMs?.invoke() ?: 0L,
                    durationMs = player?.durationMs?.invoke() ?: 0L,
                    repeatOn = player?.repeatOn?.invoke() == true,
                    mood = PetMood.classify(track?.genre, track?.tagList, track?.title ?: s.title, beat, PetState.keyIsMinor),
                    quiet = s.quiet,
                    sinceMusicMs = if (s.playing) 0L else (now - s.lastMusicAt).coerceAtLeast(0L),
                    restorable = PetStreak.status.mood == PetStreak.Mood.RESTORABLE,
                    zone = latestZone,
                    button = latestButton,
                    at = position.value,
                )
            )
            delay(250L)
        }
    }

    // Walking where the scene wants it, at its pace; the walk itself is drawn from the walking frames.
    var walking by remember { mutableStateOf(false) }
    var facingRight by remember { mutableStateOf(false) }
    val target = scene.target
    val pace = scene.pace
    LaunchedEffect(target, pace, travelPx) {
        val from = position.value
        val distanceDp = with(density) { (abs(target - from) * travelPx).toDp().value }
        if (distanceDp < 3f || forcedActivity != null) {
            walking = false
            return@LaunchedEffect
        }
        facingRight = target > from
        walking = true
        val speed = when (pace) {
            PetPace.RUN -> RUN_DP_PER_S
            PetPace.STROLL -> STROLL_DP_PER_S
            PetPace.WALK -> WALK_DP_PER_S
        }
        try {
            position.animateTo(target, tween((distanceDp / speed * 1000f).toInt().coerceAtLeast(120), easing = LinearEasing))
        } finally {
            walking = false
            PetBarPlace.fraction = position.value
        }
    }

    val clock = maxOf(System.currentTimeMillis(), state.tick)
    val booped = clock < state.boopUntil
    val celebrating = clock < celebrateUntil
    val activity = forcedActivity ?: when {
        booped -> if (state.playing) PetActivity.BOOP_DANCE else PetActivity.BOOP
        celebrating -> PetActivity.EAT
        walking -> PetActivity.WALK
        else -> scene.activity
    }
    val hint = forcedHint ?: scene.hint.takeIf { !menuOpen && !gone && !walking }
    // Poses drawn facing left (on its feet) turn to where it last went; sitting poses face the room.
    val mirrored = activity.facesSideways && facingRight

    val frames = remember(activity, settings) { PetBarFrames.of(PetBrain.framesFor(activity), settings) }
    var frame by remember(activity) { mutableIntStateOf(0) }
    val latestFrames by rememberUpdatedState(frames)
    val fallbackBpm by rememberUpdatedState(if (state.liveBpm > 0f) state.liveBpm else state.bpm)
    val paceFactor = when {
        activity != PetActivity.WALK -> 1f
        pace == PetPace.RUN -> 0.6f
        pace == PetPace.STROLL -> 1.3f
        else -> 1f
    }
    LaunchedEffect(activity, paceFactor) {
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
                delay((activity.frameMs * settings.speed.factor * paceFactor).toLong())
            }
            frame = (frame + 1) % count
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
        // Moved a whole art pixel at a time, like its frames, so it never shimmers between two pixels.
        val artPx = with(density) { PIXEL.toPx() }
        val x = (bar.left - origin.x + marginPx + ((position.value * travelPx) / artPx).roundToInt() * artPx)
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
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(catW, catH),
            ) {
                // Pixel art stays pixel art: no turning, stretching or squashing, only whole frames (the trot is
                // drawn), flipped to face the way it goes.
                Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = if (mirrored) -1f else 1f }) {
                    drawPixels(frames.images[frame % frames.images.size])
                }
                // Only the cat itself takes the pointer, not the room around it in the frame.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(PIXEL * 30, PIXEL * 22)
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
                )
            }
        }

        // A suggestion over its head: a click does it.
        AnimatedVisibility(
            visible = hint != null && !gone,
            enter = fadeIn() + scaleIn(initialScale = 0.6f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.7f, transformOrigin = TransformOrigin(0.5f, 1f)),
            modifier = Modifier.layoutAbove(
                centerXPx = x + catWpx / 2,
                bottomPx = y + with(density) { 10.dp.toPx() },
                maxRightPx = bar.right - origin.x - 8f,
            ),
        ) {
            var shown by remember { mutableStateOf(PetHint.PLAY) }
            if (hint != null) shown = hint
            HintBubble(shown) {
                val id = player?.track?.invoke()?.id
                director.onHintDone(shown, if (shown == PetHint.PLAY) null else id)
                when (shown) {
                    PetHint.PLAY -> player?.play?.invoke() ?: onPlayPause()
                    PetHint.REPEAT -> player?.repeatOne?.invoke()
                    PetHint.NEXT -> player?.next?.invoke()
                    PetHint.PREVIOUS -> player?.previous?.invoke()
                }
            }
        }

        // The streak, only while the pointer is on the cat (or it just ate): never a permanent badge.
        val badgeShown = (hovered || celebrating) && !menuOpen && streak.days > 0 && !gone && hint == null
        AnimatedVisibility(
            visible = badgeShown,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.offset {
                IntOffset((x + catWpx / 2).roundToInt() - 26.dp.roundToPx(), (y + 6.dp.toPx()).roundToInt())
            },
        ) {
            StreakChip(streak.days)
        }

        if (menuOpen) {
            val cardW = 260.dp
            val cardWpx = with(density) { cardW.toPx() }
            val left = (x + catWpx / 2 - cardWpx / 2).coerceIn(8f, (bar.right - origin.x - cardWpx - 8f).coerceAtLeast(8f))
            val bottom = (if (gone) bar.top - origin.y - with(density) { 24.dp.toPx() } else y + with(density) { 10.dp.toPx() }) - with(density) { 8.dp.toPx() }
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

/** The hint bubble: the cat's suggestion as a small tonal pill with a tail, an icon and a few words. */
@Composable
private fun HintBubble(hint: PetHint, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (icon, text) = when (hint) {
        PetHint.PLAY -> Icons.Rounded.PlayArrow to str("pet_hint_play")
        PetHint.REPEAT -> Icons.Rounded.RepeatOne to str("pet_hint_repeat")
        PetHint.NEXT -> Icons.Rounded.SkipNext to str("pet_hint_next")
        PetHint.PREVIOUS -> Icons.Rounded.SkipPrevious to str("pet_hint_previous")
    }
    // A gentle bob, so a waiting suggestion is noticed without shouting.
    val bob = rememberInfiniteTransition(label = "hintBob")
    val dy by bob.animateFloat(0f, -3f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { translationY = dy * density }) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(50),
            color = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
        ) {
            Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        // The bubble's tail, pointing down at the cat.
        Canvas(Modifier.size(12.dp, 6.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2f, size.height); close()
            }
            drawPath(path, scheme.primaryContainer)
        }
    }
}

/** Places a node centred on [centerXPx] with its bottom edge on [bottomPx], kept inside [maxRightPx]. */
private fun Modifier.layoutAbove(centerXPx: Float, bottomPx: Float, maxRightPx: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(placeable.width, placeable.height) {
        val left = (centerXPx - placeable.width / 2f).coerceIn(8f, (maxRightPx - placeable.width).coerceAtLeast(8f))
        placeable.place(left.roundToInt(), (bottomPx - placeable.height).roundToInt().coerceAtLeast(0))
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
    var fraction = PetBarDirector.HOME
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

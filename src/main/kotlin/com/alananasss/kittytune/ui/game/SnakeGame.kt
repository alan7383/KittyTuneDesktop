@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import com.alananasss.kittytune.ui.common.Tip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.data.playbackKey
import com.alananasss.kittytune.data.wave.MyWave
import com.alananasss.kittytune.data.wave.WaveMode
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.pet.PetConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class SnakeGameMode { PLAYLIST, WAVE }

internal data class SnakeGameRequest(
    val title: String,
    val mode: SnakeGameMode,
    val tracks: List<Track> = emptyList(),
    val playbackContext: PlaybackContext? = null,
)

/** Shared launch point (like LikeAllPrompt): ask anywhere, one host shows the game. */
internal object SnakeGameLauncher {
    private const val KEY_BEST = "best_score"
    private val prefs by lazy { NamedPrefs("snake_game") }

    var pending by mutableStateOf<SnakeGameRequest?>(null)
        private set

    internal var keyHandler: ((KeyEvent) -> Boolean)? = null

    fun handleKeyEvent(event: KeyEvent): Boolean {
        return keyHandler?.invoke(event) ?: false
    }

    fun launch(request: SnakeGameRequest) {
        if (request.mode == SnakeGameMode.PLAYLIST && request.tracks.isEmpty()) return
        pending = request
    }

    fun dismiss() {
        pending = null
        keyHandler = null
    }

    fun bestScore(): Int = prefs.getInt(KEY_BEST, 0)

    fun saveBestScore(score: Int) = prefs.putInt(KEY_BEST, score)
}

// ─── Game model ─────────────────────────────────────────────────────────────────────────

internal enum class SnakePhase { IDLE, RUNNING, PAUSED, OVER, WIN }

internal enum class SnakeDirection { UP, DOWN, LEFT, RIGHT }

internal data class SnakeCell(val x: Int, val y: Int)

internal data class SnakeFood(val track: Track, val cell: SnakeCell)

/**
 * The snake itself. Pure state, no UI: the screen ticks it and reads the state back.
 */
internal class SnakeEngine(
    val cols: Int,
    val rows: Int,
    tracks: List<Track>,
) {
    var phase by mutableStateOf(SnakePhase.IDLE)
    var snake by mutableStateOf(listOf<SnakeCell>())
    var direction by mutableStateOf(SnakeDirection.RIGHT)
    var foods by mutableStateOf(listOf<SnakeFood>())
    var score by mutableStateOf(0)
    var speedMs by mutableStateOf(START_SPEED_MS)
    var lastEaten by mutableStateOf<Track?>(null)

    private val turns = ArrayDeque<SnakeDirection>()
    // The menu is eaten in list order: the track that is already playing is not on the board,
    // so the first bite is the NEXT track of the list, then the one after it, and so on.
    private val pool = tracks
    private var poolIndex = 0

    init {
        placeSnake()
        spawnInitialFoods()
    }

    val trackCount: Int get() = pool.size

    private fun placeSnake() {
        val startY = rows / 2
        val startX = cols / 2
        snake = listOf(
            SnakeCell(startX + 1, startY),
            SnakeCell(startX, startY),
            SnakeCell(startX - 1, startY),
        )
    }

    private fun spawnInitialFoods() {
        foods = emptyList()
        repeat(min(FOODS_ON_BOARD, pool.size)) { spawnFood()?.let { foods = foods + it } }
    }

    /** A fresh run on the same menu: the board is restocked in list order, the snake goes home. */
    fun reset() {
        turns.clear()
        poolIndex = 0
        direction = SnakeDirection.RIGHT
        score = 0
        speedMs = START_SPEED_MS
        lastEaten = null
        placeSnake()
        spawnInitialFoods()
        phase = SnakePhase.IDLE
    }

    /** Any input or tap starts (or resumes) the run. */
    fun ensureRunning() {
        if (phase == SnakePhase.IDLE || phase == SnakePhase.PAUSED) phase = SnakePhase.RUNNING
    }

    fun pause() {
        if (phase == SnakePhase.RUNNING) phase = SnakePhase.PAUSED
    }

    /** Queues a turn; a direct reverse and a no-op are dropped so the snake cannot bite itself on the spot. */
    fun turn(dir: SnakeDirection) {
        val last = turns.lastOrNull() ?: direction
        if (dir == last || dir == opposite(last)) return
        if (turns.size < 2) turns.add(dir)
    }

    fun step() {
        if (phase != SnakePhase.RUNNING) return
        if (turns.isNotEmpty()) direction = turns.removeFirst()

        val head = snake.first()
        val newHead = when (direction) {
            SnakeDirection.UP -> SnakeCell(head.x, head.y - 1)
            SnakeDirection.DOWN -> SnakeCell(head.x, head.y + 1)
            SnakeDirection.LEFT -> SnakeCell(head.x - 1, head.y)
            SnakeDirection.RIGHT -> SnakeCell(head.x + 1, head.y)
        }

        if (newHead.x !in 0 until cols || newHead.y !in 0 until rows) {
            phase = SnakePhase.OVER
            return
        }

        val food = foods.firstOrNull { it.cell == newHead }
        if (food == null) {
            val body = snake.dropLast(1) // the tail cell is vacated this tick, it is safe to enter
            if (body.any { it == newHead }) {
                phase = SnakePhase.OVER
                return
            }
            snake = listOf(newHead) + body
        } else {
            if (snake.any { it == newHead }) {
                phase = SnakePhase.OVER
                return
            }
            snake = listOf(newHead) + snake
            foods = foods - food
            score++
            speedMs = (speedMs - SPEED_STEP_MS).coerceAtLeast(MIN_SPEED_MS)
            lastEaten = food.track
            spawnFood()?.let { foods = foods + it }
            if (foods.isEmpty()) phase = SnakePhase.WIN
        }
    }

    private fun spawnFood(): SnakeFood? {
        if (poolIndex >= pool.size) return null
        val occupied = snake.toHashSet() + foods.map { it.cell }
        val free = mutableListOf<SnakeCell>()
        for (x in 0 until cols) for (y in 0 until rows) {
            val cell = SnakeCell(x, y)
            if (cell !in occupied) free.add(cell)
        }
        if (free.isEmpty()) return null
        val track = pool[poolIndex++]
        return SnakeFood(track, free.random())
    }

    private fun opposite(dir: SnakeDirection): SnakeDirection = when (dir) {
        SnakeDirection.UP -> SnakeDirection.DOWN
        SnakeDirection.DOWN -> SnakeDirection.UP
        SnakeDirection.LEFT -> SnakeDirection.RIGHT
        SnakeDirection.RIGHT -> SnakeDirection.LEFT
    }

    private companion object {
        const val FOODS_ON_BOARD = 1
        /** Very slow at first, ramps up with every swallowed track. */
        const val START_SPEED_MS = 460f
        const val SPEED_STEP_MS = 8f
        const val MIN_SPEED_MS = 110f
    }
}

// ─── Host ───────────────────────────────────────────────────────────────────────────────

/** Put once near the top of the window; shows the game whenever [SnakeGameLauncher.launch] has been called. */
@Composable
internal fun SnakeGameHost(playerViewModel: PlayerViewModel) {
    val request = SnakeGameLauncher.pending ?: return
    BackHandler(onBack = { SnakeGameLauncher.dismiss() })
    Dialog(
        onDismissRequest = { SnakeGameLauncher.dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        SnakeGameScreen(
            request = request,
            onPlayTrack = { tracks, index, startMs, queueStarted ->
                if (!queueStarted || !playerViewModel.playTrackInQueue(tracks[index], startMs)) {
                    playerViewModel.playPlaylist(
                        tracks = tracks,
                        startIndex = index,
                        context = request.playbackContext,
                        maintainPlayerState = true,
                        respectShuffle = false,
                        startAtPositionMs = startMs,
                    )
                }
            },
            onOpenPlayer = {
                SnakeGameLauncher.dismiss()
                playerViewModel.isPlayerExpanded = true
            },
            onClose = SnakeGameLauncher::dismiss,
        )
    }
}

// ─── Screen ─────────────────────────────────────────────────────────────────────────────

private const val COLS = 15
private const val ROWS = 21

/** Wide enough for the board with the score, the song and the controls beside it. */
private val SIDE_BY_SIDE_WIDTH = 720.dp

@Composable
internal fun SnakeGameScreen(
    request: SnakeGameRequest,
    onPlayTrack: (tracks: List<Track>, index: Int, startMs: Long, queueStarted: Boolean) -> Unit,
    onOpenPlayer: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isWave = request.mode == SnakeGameMode.WAVE

    // The wave gathers its own menu: fresh finds, one batch per run.
    var waveReloadKey by remember { mutableStateOf(0) }
    var waveTracks by remember { mutableStateOf<List<Track>?>(null) }
    if (isWave) {
        LaunchedEffect(waveReloadKey) {
            waveTracks = try {
                MyWave.nextBatch(WaveMode.DISCOVER, exclude = emptySet())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    val menu = when (request.mode) {
        SnakeGameMode.PLAYLIST -> request.tracks
        SnakeGameMode.WAVE -> waveTracks
    }
    val sourceTracks = remember(menu) {
        menu?.let { BlockManager.filterBlocked(it).distinctBy(Track::playbackKey) }
    }

    var best by remember { mutableStateOf(SnakeGameLauncher.bestScore()) }

    Surface(
        modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth(0.94f).heightIn(max = 880.dp).fillMaxHeight(0.94f),
        shape = RoundedCornerShape(32.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                GameBadge()
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        str(if (isWave) "game_wave_title" else "game_eat_playlist"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        request.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Tip(str("btn_close")) {
                    FilledTonalIconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            when {
                isWave && waveTracks == null -> GameMessage(str("game_loading"), loading = true)
                sourceTracks.isNullOrEmpty() -> GameMessage(str("game_empty"))
                else -> SnakeGameBoardArea(
                    sourceTracks = sourceTracks,
                    onPlayTrack = onPlayTrack,
                    onOpenPlayer = onOpenPlayer,
                    isWave = isWave,
                    best = best,
                    onBest = {
                        if (it > best) {
                            best = it
                            SnakeGameLauncher.saveBestScore(it)
                        }
                    },
                    onRestartWave = {
                        waveReloadKey++
                        waveTracks = null
                    },
                    onClose = onClose,
                )
            }
        }
    }
}

/** Which way a key steers, whatever the keyboard: arrows, WASD (QWERTY), ZQSD (AZERTY) and the number pad. */
internal fun steeringFor(key: Key, char: Char? = null): SnakeDirection? {
    val lower = char?.lowercaseChar()
    return when {
        key == Key.DirectionUp || key == Key.NumPad8 || key == Key.W || key == Key.Z || lower == 'z' || lower == 'w' -> SnakeDirection.UP
        key == Key.DirectionDown || key == Key.NumPad2 || key == Key.NumPad5 || key == Key.S || lower == 's' -> SnakeDirection.DOWN
        key == Key.DirectionLeft || key == Key.NumPad4 || key == Key.A || key == Key.Q || lower == 'q' || lower == 'a' -> SnakeDirection.LEFT
        key == Key.DirectionRight || key == Key.NumPad6 || key == Key.D || lower == 'd' -> SnakeDirection.RIGHT
        else -> null
    }
}

internal fun steeringFor(event: KeyEvent): SnakeDirection? {
    val char = runCatching { event.utf16CodePoint.toChar() }.getOrNull()
    return steeringFor(event.key, char)
}

@Composable
private fun SnakeGameBoardArea(
    sourceTracks: List<Track>,
    onPlayTrack: (List<Track>, Int, Long, Boolean) -> Unit,
    onOpenPlayer: () -> Unit,
    isWave: Boolean,
    best: Int,
    onBest: (Int) -> Unit,
    onRestartWave: () -> Unit,
    onClose: () -> Unit,
) {
    // The snake wears the pet cat's coat: fur for the body, its eyes and markings for the face.
    val pet = remember { PetConfig.load() }

    val engine = remember(sourceTracks) {
        SnakeEngine(COLS, ROWS, sourceTracks.drop(1).ifEmpty { sourceTracks })
    }

    // Start and steering share the same entry point. Pausing the game keeps the selected song.
    val requestedTrack = if (engine.phase == SnakePhase.IDLE) null else engine.lastEaten ?: sourceTracks.first()
    var playingTrack by remember(engine) { mutableStateOf<Track?>(null) }
    LaunchedEffect(engine, requestedTrack) {
        val track = requestedTrack ?: return@LaunchedEffect
        val startMs = TrackHighlights.startMs(track)
        ensureActive()
        onPlayTrack(sourceTracks, sourceTracks.indexOf(track), startMs, playingTrack != null)
        playingTrack = track
    }
    val bannerTrack = playingTrack.takeIf { requestedTrack != null }

    val restart: () -> Unit = {
        if (isWave) onRestartWave() // the wave loads a fresh batch; the board re-enters on its own
        engine.reset()
    }
    val togglePause: () -> Unit = {
        when (engine.phase) {
            SnakePhase.RUNNING -> engine.pause()
            SnakePhase.IDLE, SnakePhase.PAUSED -> engine.ensureRunning()
            SnakePhase.OVER, SnakePhase.WIN -> restart()
        }
    }

    val onKey: (KeyEvent) -> Boolean = { event ->
        val direction = steeringFor(event)
        val char = runCatching { event.utf16CodePoint.toChar().lowercaseChar() }.getOrNull()
        val isPauseKey = event.key == Key.Spacebar || event.key == Key.P || char == 'p'
        val isEnterKey = event.key == Key.Enter || event.key == Key.NumPadEnter

        when {
            direction != null -> {
                if (event.type == KeyEventType.KeyDown) {
                    if (engine.phase == SnakePhase.OVER || engine.phase == SnakePhase.WIN) {
                        restart()
                    }
                    engine.turn(direction)
                    engine.ensureRunning()
                }
                true
            }
            isPauseKey -> {
                if (event.type == KeyEventType.KeyUp) togglePause()
                true
            }
            isEnterKey -> {
                if (event.type == KeyEventType.KeyUp && engine.phase != SnakePhase.RUNNING) togglePause()
                true
            }
            event.key == Key.Escape -> {
                if (event.type == KeyEventType.KeyUp) onClose()
                true
            }
            else -> false
        }
    }

    val currentOnKey by rememberUpdatedState(onKey)
    DisposableEffect(engine) {
        SnakeGameLauncher.keyHandler = { currentOnKey(it) }
        onDispose {
            if (SnakeGameLauncher.keyHandler != null) {
                SnakeGameLauncher.keyHandler = null
            }
        }
    }

    // The ticker: slower at first, frantic once the menu is half eaten.
    LaunchedEffect(engine, engine.phase, engine.speedMs) {
        while (engine.phase == SnakePhase.RUNNING) {
            delay(engine.speedMs.toLong())
            engine.step()
        }
    }

    LaunchedEffect(engine.score) {
        if (engine.score > best) onBest(engine.score)
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }
    LaunchedEffect(engine.phase) {
        if (engine.phase == SnakePhase.RUNNING) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent(onKey),
    ) {
        val board: @Composable (Modifier) -> Unit = { modifier ->
            SnakeBoard(
                engine = engine,
                pet = pet,
                isWave = isWave,
                best = best,
                onRequestFocus = { runCatching { focusRequester.requestFocus() } },
                onStart = {
                    engine.ensureRunning()
                    runCatching { focusRequester.requestFocus() }
                },
                onRestart = restart,
                onClose = onClose,
                modifier = modifier,
            )
        }
        if (maxWidth >= SIDE_BY_SIDE_WIDTH) {
            Row(Modifier.fillMaxSize()) {
                board(Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier.width(300.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ScoreCards(engine.score, best, engine.trackCount)
                    NowPlayingCard(bannerTrack, onOpenPlayer)
                    Spacer(Modifier.weight(1f))
                    ControlsCard()
                    PauseButton(engine.phase, onClick = togglePause, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreCards(engine.score, best, engine.trackCount, modifier = Modifier.weight(1f), compact = true)
                    Spacer(Modifier.width(10.dp))
                    PauseButton(engine.phase, onClick = togglePause)
                }
                NowPlayingCard(bannerTrack, onOpenPlayer)
                board(Modifier.weight(1f).fillMaxWidth())
                Text(
                    str("game_hint"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The board itself: the grid, the covers to eat, the snake, and what to do when it is not running. */
@Composable
private fun SnakeBoard(
    engine: SnakeEngine,
    pet: com.alananasss.kittytune.ui.pet.PetSettings,
    isWave: Boolean,
    best: Int,
    onRequestFocus: () -> Unit = {},
    onStart: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    var dragAccum by remember { mutableStateOf(Offset.Zero) }
    val gridColor = scheme.outlineVariant.copy(alpha = 0.45f)
    val glowColor = scheme.tertiary

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cell = min(constraints.maxWidth.toFloat() / COLS, constraints.maxHeight.toFloat() / ROWS)
        val cellDp = with(density) { cell.toDp() }
        val boardW = with(density) { (cell * COLS).toDp() }
        val boardH = with(density) { (cell * ROWS).toDp() }

        Box(
            modifier = Modifier
                .size(boardW, boardH)
                .clip(RoundedCornerShape(28.dp))
                .background(scheme.surfaceContainerLowest)
                .pointerInput(engine) {
                    detectDragGestures(
                        onDragStart = {
                            dragAccum = Offset.Zero
                            onRequestFocus()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccum += dragAmount
                            val threshold = cell * 0.85f
                            if (abs(dragAccum.x) > threshold || abs(dragAccum.y) > threshold) {
                                val dir = if (abs(dragAccum.x) > abs(dragAccum.y)) {
                                    if (dragAccum.x > 0) SnakeDirection.RIGHT else SnakeDirection.LEFT
                                } else {
                                    if (dragAccum.y > 0) SnakeDirection.DOWN else SnakeDirection.UP
                                }
                                engine.turn(dir)
                                engine.ensureRunning()
                                dragAccum = Offset.Zero
                            }
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawGrid(cell, gridColor)
                drawFoodsGlow(engine.foods, cell, glowColor)
                drawSnake(
                    engine.snake, cell,
                    fur = Color(pet.palette.fur),
                    shade = Color(pet.palette.shade),
                    mark = Color(pet.palette.mark),
                    eyeColor = Color(pet.eyeColor),
                )
            }

            engine.foods.forEach { food ->
                val appeared = remember(food.track.id, food.cell) { Animatable(0.4f) }
                LaunchedEffect(food.track.id, food.cell) {
                    appeared.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
                }
                AsyncImage(
                    model = trackImageModel(food.track),
                    contentDescription = food.track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset { IntOffset((food.cell.x * cell).roundToInt(), (food.cell.y * cell).roundToInt()) }
                        .size(cellDp)
                        .padding(2.5.dp)
                        .graphicsLayer {
                            scaleX = appeared.value
                            scaleY = appeared.value
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceContainerHighest),
                )
            }

            val overlayAlpha by animateFloatAsState(
                targetValue = if (engine.phase == SnakePhase.RUNNING) 0f else 1f,
                label = "overlay",
            )
            if (overlayAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .background(scheme.scrim.copy(alpha = 0.45f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (engine.phase) {
                        SnakePhase.IDLE -> GameOverlayCard(
                            title = str(if (isWave) "game_wave_idle" else "game_idle"),
                            body = str("game_eat_hint"),
                            primary = { BigPlayButton(onStart) },
                        )
                        SnakePhase.PAUSED -> GameOverlayCard(
                            title = str("game_paused"),
                            primary = { BigPlayButton(onStart) },
                        )
                        SnakePhase.OVER -> GameOverlayCard(
                            title = str("game_over"),
                            body = str("game_score", engine.score) + "  ·  " + str("game_best", best),
                            icon = Icons.Rounded.SentimentDissatisfied,
                            primary = { RestartButtons(onRestart, onClose) },
                        )
                        SnakePhase.WIN -> GameOverlayCard(
                            title = str(if (isWave) "game_wave_finished" else "game_finished_title"),
                            body = str("game_finished_text", engine.score),
                            icon = Icons.Rounded.EmojiEvents,
                            primary = { RestartButtons(onRestart, onClose) },
                        )
                        SnakePhase.RUNNING -> Unit
                    }
                }
            }
        }
    }
}

// ─── Pieces ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GameBadge() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(MaterialShapes.Cookie9Sided.toShape())
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
    }
}

/** Songs eaten (the score), the best run, and how far through the menu this run is. */
@Composable
private fun ScoreCards(score: Int, best: Int, total: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val scoreScale = remember { Animatable(1f) }
    LaunchedEffect(score) {
        if (score > 0) {
            scoreScale.snapTo(1.25f)
            scoreScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(24.dp), color = scheme.primaryContainer, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.MusicNote, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(str("game_score_label"), style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
                    }
                    Text(
                        "$score",
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scoreScale.value
                            scaleY = scoreScale.value
                        },
                    )
                }
            }
            Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, null, tint = scheme.tertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(str("game_best_label"), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                    Text(
                        "$best",
                        style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (total > 0 && !compact) {
            Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "${score.coerceAtMost(total)} " + str("game_of_total", total),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearWavyProgressIndicator(
                        progress = { (score.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** The song the last cover played; a click opens the player on it. */
@Composable
private fun NowPlayingCard(track: Track?, onOpenPlayer: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = track != null,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
    ) {
        FilledTonalButton(
            onClick = onOpenPlayer,
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(10.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AsyncImage(
                model = trackImageModel(track),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(scheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(str("game_now_playing"), style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    track?.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track?.displayArtist.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

/** The keys, as keycaps: arrows or WASD / ZQSD to steer, Space or P to pause. */
@Composable
private fun ControlsCard() {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Keyboard, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(str("game_controls"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Keycap(icon = Icons.Rounded.KeyboardArrowUp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Keycap(icon = Icons.Rounded.KeyboardArrowLeft)
                        Keycap(icon = Icons.Rounded.KeyboardArrowDown)
                        Keycap(icon = Icons.Rounded.KeyboardArrowRight)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(str("game_key_steer"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("WASD · ZQSD", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Keycap(label = str("game_key_space"), wide = true)
                Spacer(Modifier.width(6.dp))
                Keycap(label = "P")
                Spacer(Modifier.width(14.dp))
                Text(str("game_key_pause"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Keycap(label: String? = null, icon: ImageVector? = null, wide: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = scheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.height(32.dp).widthIn(min = if (wide) 72.dp else 32.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
            if (icon != null) Icon(icon, null, modifier = Modifier.size(18.dp))
            if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PauseButton(phase: SnakePhase, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val running = phase == SnakePhase.RUNNING
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier.height(52.dp),
    ) {
        Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(str(if (running) "action_pause" else "btn_play"))
    }
}

@Composable
private fun GameOverlayCard(
    title: String,
    body: String? = null,
    icon: ImageVector? = null,
    primary: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(32.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 320.dp)) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.size(64.dp).clip(MaterialShapes.SoftBurst.toShape()).background(scheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = scheme.onTertiaryContainer, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(14.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (body != null) {
                Spacer(Modifier.height(6.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(18.dp))
            primary()
        }
    }
}

@Composable
private fun BigPlayButton(onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(80.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = str("btn_play"), modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun RestartButtons(onRestart: () -> Unit, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = onRestart, shapes = ButtonDefaults.shapes(), modifier = Modifier.height(52.dp)) {
            Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(str("game_restart"))
        }
        TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) { Text(str("btn_close")) }
    }
}

@Composable
private fun GameMessage(text: String, loading: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        if (loading) {
            ContainedLoadingIndicator(Modifier.size(64.dp))
        } else {
            Box(
                modifier = Modifier.size(72.dp).clip(MaterialShapes.Cookie12Sided.toShape()).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.SportsEsports, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ─── Canvas drawing ─────────────────────────────────────────────────────────────────────


private fun DrawScope.drawGrid(cell: Float, color: Color) {
    for (x in 1 until COLS) {
        for (y in 1 until ROWS) {
            drawCircle(
                color = color,
                radius = cell * 0.045f,
                center = Offset(x * cell, y * cell),
            )
        }
    }
}

private fun DrawScope.drawFoodsGlow(foods: List<SnakeFood>, cell: Float, color: Color) {
    foods.forEach { food ->
        drawCircle(
            color = color.copy(alpha = 0.26f),
            radius = cell * 0.72f,
            center = Offset(
                food.cell.x * cell + cell / 2f,
                food.cell.y * cell + cell / 2f,
            ),
        )
    }
}

private fun DrawScope.drawSnake(
    snake: List<SnakeCell>,
    cell: Float,
    fur: Color,
    shade: Color,
    mark: Color,
    eyeColor: Color,
) {
    val ink = Color(0xFF101015)
    val inset = cell * 0.06f
    val size = cell - inset * 2
    val radius = CornerRadius(cell * 0.22f, cell * 0.22f)
    snake.forEachIndexed { index, segment ->
        val color = if (index == 0) {
            fur
        } else {
            fur.copy(alpha = (0.92f - index * 0.02f).coerceAtLeast(0.45f))
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(segment.x * cell + inset, segment.y * cell + inset),
            size = Size(size, size),
            cornerRadius = radius,
        )
    }

    // The head is the pet cat's face: its fur, its eye colour, its markings for the nose.
    val head = snake.firstOrNull() ?: return
    val cx = head.x * cell + cell / 2f
    val cy = head.y * cell + cell / 2f
    val earSize = cell * 0.2f
    val leftEar = listOf(
        Offset(cx - cell * 0.26f, cy - cell * 0.12f),
        Offset(cx - cell * 0.3f, cy - cell * 0.12f - earSize),
        Offset(cx - cell * 0.06f, cy - cell * 0.26f),
    )
    val rightEar = listOf(
        Offset(cx + cell * 0.26f, cy - cell * 0.12f),
        Offset(cx + cell * 0.3f, cy - cell * 0.12f - earSize),
        Offset(cx + cell * 0.06f, cy - cell * 0.26f),
    )
    listOf(leftEar, rightEar).forEach { ear ->
        drawTriangle(ear[0], ear[1], ear[2], fur)
        // Inner ear tinted with the coat's shade colour.
        val c = centroid(ear)
        val inner = ear.map { p -> Offset(p.x * 0.45f + c.x * 0.55f, p.y * 0.45f + c.y * 0.55f) }
        drawTriangle(inner[0], inner[1], inner[2], shade)
    }
    // Eyes in the pet's eye colour, with a dark socket and a catchlight.
    val eyeRadius = cell * 0.07f
    listOf(
        Offset(cx - cell * 0.16f, cy + cell * 0.02f),
        Offset(cx + cell * 0.16f, cy + cell * 0.02f),
    ).forEach { center ->
        drawCircle(ink, eyeRadius * 1.45f, center)
        drawCircle(eyeColor, eyeRadius, center)
        drawCircle(
            Color.White.copy(alpha = 0.85f), eyeRadius * 0.32f,
            Offset(center.x - eyeRadius * 0.3f, center.y - eyeRadius * 0.35f),
        )
    }
    // Nose in the coat's marking colour.
    drawCircle(mark, eyeRadius * 0.62f, Offset(cx, cy + cell * 0.18f))
}

private fun centroid(points: List<Offset>): Offset = Offset(
    points.sumOf { it.x.toDouble() }.toFloat() / points.size,
    points.sumOf { it.y.toDouble() }.toFloat() / points.size,
)

private fun DrawScope.drawTriangle(a: Offset, b: Offset, c: Offset, color: Color) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        close()
    }
    drawPath(path, color)
}

private fun trackImageModel(track: Track?): String = track?.thumbnailUrl.orEmpty()

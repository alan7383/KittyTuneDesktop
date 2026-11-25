@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.Tip
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

internal const val MERGE_BEST = "merge_best"

internal data class MergeTile(val id: Long, val value: Int, val x: Int, val y: Int, val merged: Boolean = false, val fresh: Boolean = false)

/** A tile sliding into the one it merges with; drawn for the slide, then gone. */
internal data class MergeGhost(val id: Long, val value: Int, val x: Int, val y: Int)

/** 2048 on a 4×4 grid: slide, equal tiles merge, a 2 (sometimes a 4) drops in after every move. */
internal class MergeEngine(private val random: Random = Random.Default) {
    var tiles by mutableStateOf(listOf<MergeTile>())
        private set
    var ghosts by mutableStateOf(listOf<MergeGhost>())
        private set
    var score by mutableIntStateOf(0)
        private set
    var over by mutableStateOf(false)
        private set
    var reached2048 by mutableStateOf(false)
        private set
    var moves by mutableIntStateOf(0)
        private set
    private var undo: Triple<List<MergeTile>, Int, Boolean>? = null
    val canUndo: Boolean get() = undo != null
    private var nextId = 1L

    init {
        reset()
    }

    fun reset() {
        tiles = emptyList()
        ghosts = emptyList()
        score = 0
        over = false
        reached2048 = false
        undo = null
        moves = 0
        spawn()
        spawn()
    }

    /** dx/dy is the direction of the slide. Returns false when nothing moved. */
    fun move(dx: Int, dy: Int): Boolean {
        if (over) return false
        val before = tiles
        val grid = Array(SIZE) { arrayOfNulls<MergeTile>(SIZE) }
        before.forEach { grid[it.y][it.x] = it.copy(merged = false, fresh = false) }
        val out = mutableListOf<MergeTile>()
        val ghostsOut = mutableListOf<MergeGhost>()
        var gained = 0
        var moved = false
        for (line in 0 until SIZE) {
            // Cells of this line, from the edge the tiles slide toward.
            val cells = (0 until SIZE).map { k ->
                val step = if (dx > 0 || dy > 0) SIZE - 1 - k else k
                if (dx != 0) step to line else line to step
            }
            val row = cells.mapNotNull { (x, y) -> grid[y][x] }
            var target = 0
            var i = 0
            while (i < row.size) {
                val (tx, ty) = cells[target]
                val a = row[i]
                val b = row.getOrNull(i + 1)
                if (b != null && b.value == a.value) {
                    val value = a.value * 2
                    gained += value
                    out += MergeTile(nextId++, value, tx, ty, merged = true)
                    ghostsOut += MergeGhost(a.id, a.value, tx, ty)
                    ghostsOut += MergeGhost(b.id, b.value, tx, ty)
                    moved = true
                    if (value >= 2048) reached2048 = true
                    i += 2
                } else {
                    if (a.x != tx || a.y != ty) moved = true
                    out += a.copy(x = tx, y = ty)
                    i++
                }
                target++
            }
        }
        if (!moved) return false
        undo = Triple(before, score, over)
        tiles = out
        ghosts = ghostsOut
        score += gained
        moves++
        spawn()
        over = !canMove()
        return true
    }

    fun undo() {
        val u = undo ?: return
        tiles = u.first.map { it.copy(merged = false, fresh = false) }
        ghosts = emptyList()
        score = u.second
        over = u.third
        undo = null
    }

    private fun spawn() {
        val taken = tiles.map { it.x to it.y }.toHashSet()
        val free = (0 until SIZE * SIZE).map { it % SIZE to it / SIZE }.filter { it !in taken }
        if (free.isEmpty()) return
        val (x, y) = free[random.nextInt(free.size)]
        tiles = tiles + MergeTile(nextId++, if (random.nextFloat() < 0.1f) 4 else 2, x, y, fresh = true)
    }

    private fun canMove(): Boolean {
        if (tiles.size < SIZE * SIZE) return true
        val g = Array(SIZE) { IntArray(SIZE) }
        tiles.forEach { g[it.y][it.x] = it.value }
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            if (x + 1 < SIZE && g[y][x] == g[y][x + 1]) return true
            if (y + 1 < SIZE && g[y][x] == g[y + 1][x]) return true
        }
        return false
    }

    internal fun setTilesForTest(values: List<Triple<Int, Int, Int>>) {
        tiles = values.map { (v, x, y) -> MergeTile(nextId++, v, x, y) }
        over = false
    }

    companion object {
        const val SIZE = 4
    }
}

@Composable
internal fun MergeGame() {
    val engine = remember { MergeEngine() }
    var best by remember { mutableIntStateOf(GameRecords.best(MERGE_BEST)) }
    var keepGoing by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    TrackGamePlaytime(GameId.MERGE, active = !engine.over)

    fun slide(dx: Int, dy: Int) {
        GamePlaytime.touch()
        if (engine.move(dx, dy) && engine.score > best) {
            best = engine.score
        }
        if (engine.over) GameRecords.offer(MERGE_BEST, engine.score)
    }
    LaunchedEffect(engine.score) { if (engine.score > 0 && engine.score % 64 == 0) GameRecords.offer(MERGE_BEST, engine.score) }

    GameKeyHandler { event ->
        val dir = steeringFor(event) ?: return@GameKeyHandler false
        if (event.type == KeyEventType.KeyDown) when (dir) {
            SnakeDirection.UP -> slide(0, -1)
            SnakeDirection.DOWN -> slide(0, 1)
            SnakeDirection.LEFT -> slide(-1, 0)
            SnakeDirection.RIGHT -> slide(1, 0)
        }
        true
    }

    val restart = {
        GameRecords.offer(MERGE_BEST, engine.score)
        keepGoing = false
        engine.reset()
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameStat(str("arcade_score"), "${engine.score}", Icons.Rounded.Star, Modifier.weight(1.2f), emphasized = true, compact = true)
            GameStat(str("game_best_label"), "${max(best, engine.score)}", Icons.Rounded.EmojiEvents, Modifier.weight(1f), compact = true)
            Tip(str("merge_undo")) {
                FilledTonalIconButton(onClick = { engine.undo() }, enabled = engine.canUndo, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = str("merge_undo"))
                }
            }
            NewGameButton(restart)
        }
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val boardSize = minOf(maxWidth, maxHeight)
            val gap = boardSize * 0.025f
            val cell = (boardSize - gap * (MergeEngine.SIZE + 1)) / MergeEngine.SIZE
            var drag by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier
                    .size(boardSize)
                    .clip(RoundedCornerShape(32.dp))
                    .background(scheme.surfaceContainerHighest)
                    .pointerInput(engine) {
                        detectDragGestures(
                            onDragStart = { drag = Offset.Zero },
                            onDrag = { change, amount ->
                                change.consume()
                                drag += amount
                            },
                            onDragEnd = {
                                val threshold = 24.dp.toPx()
                                if (abs(drag.x) > threshold || abs(drag.y) > threshold) {
                                    if (abs(drag.x) > abs(drag.y)) slide(if (drag.x > 0) 1 else -1, 0) else slide(0, if (drag.y > 0) 1 else -1)
                                }
                            },
                        )
                    },
            ) {
                for (y in 0 until MergeEngine.SIZE) for (x in 0 until MergeEngine.SIZE) {
                    Box(
                        Modifier
                            .offset(gap + (cell + gap) * x, gap + (cell + gap) * y)
                            .size(cell)
                            .clip(RoundedCornerShape(cell * 0.22f))
                            .background(scheme.surfaceContainer),
                    )
                }
                // One keyed loop for both: a tile that merges keeps its slot (and its position) while it slides in.
                val shown = engine.ghosts.map { MergeTile(it.id, it.value, it.x, it.y) to true } + engine.tiles.map { it to false }
                shown.forEach { (tile, ghost) -> key(tile.id) { TileView(tile, cell, gap, ghost = ghost, moves = engine.moves) } }

                val won = engine.reached2048 && !keepGoing
                BoardScrim(visible = engine.over || won) {
                    if (won && !engine.over) {
                        GameEndCard(
                            title = str("merge_won"),
                            body = str("arcade_score_is", engine.score),
                            icon = Icons.Rounded.EmojiEvents,
                            onRestart = restart,
                        ) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { keepGoing = true }, shapes = ButtonDefaults.shapes()) { Text(str("merge_keep_going")) }
                        }
                    } else {
                        GameEndCard(
                            title = str(if (engine.score >= best && engine.score > 0) "arcade_new_record" else "merge_over"),
                            body = str("arcade_score_is", engine.score) + "  ·  " + str("game_best", max(best, engine.score)),
                            icon = Icons.Rounded.Tag,
                            onRestart = restart,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(str("merge_hint"), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

private data class TileLook(val bg: Color, val fg: Color)

@Composable
private fun tileLook(value: Int): TileLook {
    val s = MaterialTheme.colorScheme
    return when (value) {
        2 -> TileLook(s.secondaryContainer, s.onSecondaryContainer)
        4 -> TileLook(s.tertiaryContainer, s.onTertiaryContainer)
        8 -> TileLook(s.primaryContainer, s.onPrimaryContainer)
        16 -> TileLook(lerp(s.primaryContainer, s.primary, 0.55f), s.onPrimary)
        32 -> TileLook(s.primary, s.onPrimary)
        64 -> TileLook(s.tertiary, s.onTertiary)
        128 -> TileLook(lerp(s.tertiary, s.error, 0.5f), s.onError)
        256 -> TileLook(s.error, s.onError)
        512 -> TileLook(lerp(s.error, Color(0xFFE6A700), 0.5f), Color.White)
        1024 -> TileLook(Color(0xFFE6A700), Color.White)
        else -> TileLook(Color(0xFFFFC400), Color(0xFF3A2A00))
    }
}

@Composable
private fun TileView(tile: MergeTile, cell: Dp, gap: Dp, ghost: Boolean = false, moves: Int = 0) {
    val density = LocalDensity.current
    val target = with(density) { Offset((gap + (cell + gap) * tile.x).toPx(), (gap + (cell + gap) * tile.y).toPx()) }
    val pos = remember { Animatable(target, Offset.VectorConverter) }
    LaunchedEffect(target) { pos.animateTo(target, tween(110)) }
    val scale = remember { Animatable(if (tile.fresh || tile.merged) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (tile.merged) {
            // Appears once the two halves have slid together, with a little overshoot.
            scale.animateTo(1f, keyframes {
                durationMillis = 280
                0f at 0
                0.6f at 100
                1.16f at 190
                1f at 280
            })
        } else if (tile.fresh) {
            scale.animateTo(1f, tween(180, delayMillis = 110, easing = androidx.compose.animation.core.EaseOutBack))
        }
    }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(ghost, moves) {
        if (ghost) fade.animateTo(0f, tween(40, delayMillis = 100)) else fade.snapTo(1f)
    }
    if (ghost && fade.value <= 0f) return
    val look = tileLook(tile.value)
    val digits = tile.value.toString().length
    Box(
        Modifier
            .offset { IntOffset(pos.value.x.roundToInt(), pos.value.y.roundToInt()) }
            .size(cell)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(cell * 0.22f))
            .background(look.bg),
        contentAlignment = Alignment.Center,
    ) {
        val size = with(density) { (cell * if (digits <= 2) 0.42f else if (digits == 3) 0.34f else 0.27f).toSp() }
        Text("${tile.value}", color = look.fg, fontWeight = FontWeight.Black, fontSize = size, modifier = Modifier.padding(4.dp))
    }
}

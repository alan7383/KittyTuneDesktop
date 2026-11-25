@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.core.str
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

internal const val BLOCKS_BEST = "blocks_best"

private const val N = BlocksEngine.SIZE

/** The seven jewel colours of the pieces, nudged toward the theme so they sit in it. */
@Composable
internal fun blockPalette(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminanceApprox() < 0.5f
    val hues = floatArrayOf(352f, 24f, 46f, 140f, 188f, 222f, 274f)
    return remember(scheme.primary, dark) {
        hues.map { h ->
            val base = Color.hsv(h, if (dark) 0.72f else 0.78f, if (dark) 0.97f else 0.92f)
            lerp(base, scheme.primary, 0.05f)
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private class Particle(val x: Float, val y: Float, val vx: Float, val vy: Float, val color: Color, val size: Float, val spin: Float)

private class ClearFx(val move: BlocksMove, val startMs: Long, val particles: List<Particle>)

@Composable
internal fun BlocksGame() {
    val engine = remember { BlocksEngine() }
    var best by remember { mutableIntStateOf(GameRecords.best(BLOCKS_BEST)) }
    var bestAtStart by remember { mutableIntStateOf(best) }
    var celebratedBest by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val palette = blockPalette()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    TrackGamePlaytime(GameId.BLOCKS, active = !engine.over)

    // ─── Effect clock: runs only while something is animating ─────────────────────────
    var now by remember { mutableLongStateOf(System.nanoTime() / 1_000_000L) }
    var fxUntil by remember { mutableLongStateOf(0L) }
    LaunchedEffect(fxUntil) {
        runGameFrames {
            now = System.nanoTime() / 1_000_000L
            now < fxUntil
        }
    }
    fun animateFor(ms: Long) {
        val t = System.nanoTime() / 1_000_000L
        now = t
        fxUntil = max(fxUntil, t + ms)
    }

    // ─── Drag state ─────────────────────────────────────────────────────────────────────
    var dragSlot by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val dragScale = remember { Animatable(0.55f) }
    val returning = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var returningSlot by remember { mutableIntStateOf(-1) }
    val placedAt = remember { mutableMapOf<Int, Long>() }
    var clearFx by remember { mutableStateOf<ClearFx?>(null) }
    var shake by remember { mutableFloatStateOf(0f) }
    var trayShownAt by remember { mutableLongStateOf(0L) }
    var overAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(engine.trayRev) {
        trayShownAt = System.nanoTime() / 1_000_000L
        animateFor(600)
    }
    LaunchedEffect(engine.over) {
        if (engine.over) {
            overAt = System.nanoTime() / 1_000_000L
            animateFor(1_400)
            best = GameRecords.offer(BLOCKS_BEST, engine.score)
        }
    }

    val shownScore by animateIntAsState(engine.score, tween(450), label = "score")
    val restart: () -> Unit = {
        best = GameRecords.offer(BLOCKS_BEST, engine.score)
        bestAtStart = best
        celebratedBest = false
        clearFx = null
        placedAt.clear()
        engine.reset()
    }

    Column(Modifier.fillMaxSize()) {
        // ─── Header: score, best, combo ─────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameStat(str("arcade_score"), "$shownScore", Icons.Rounded.Star, Modifier.weight(1.3f), emphasized = true, compact = true)
            GameStat(str("game_best_label"), "${max(best, engine.score)}", Icons.Rounded.EmojiEvents, Modifier.weight(1f), compact = true)
            ComboChip(engine.combo)
            NewGameButton(restart)
        }
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val wPx = constraints.maxWidth.toFloat()
            val hPx = constraints.maxHeight.toFloat()
            // Board on top, the three pieces under it, as the genre always lays them out.
            val boardSize = min(wPx * 0.96f, hPx / 1.38f)
            val cell = boardSize / N
            val boardLeft = (wPx - boardSize) / 2f
            val boardTop = cell * 0.2f
            val trayTop = boardTop + boardSize + cell * 0.45f
            val trayHeight = (hPx - trayTop).coerceAtLeast(cell * 2f)
            val trayCell = cell * 0.5f
            val slotWidth = boardSize / 3f
            fun slotRect(i: Int) = Rect(boardLeft + slotWidth * i, trayTop, boardLeft + slotWidth * (i + 1), trayTop + trayHeight)
            fun slotCenter(i: Int) = slotRect(i).center

            /** Where the dragged piece's top-left lands on the board, as a cell, for a pointer at [p]. */
            fun snap(piece: BlockPiece, p: Offset): Pair<Int, Int> {
                val center = p - Offset(0f, cell * 0.9f)
                val fx = (center.x - boardLeft) / cell - piece.width / 2f
                val fy = (center.y - boardTop) / cell - piece.height / 2f
                return fx.roundToInt() to fy.roundToInt()
            }

            fun drop() {
                val slot = dragSlot
                val piece = engine.tray.getOrNull(slot)
                dragSlot = -1
                if (piece == null) return
                val (x, y) = snap(piece, dragPos)
                val move = if (engine.canPlace(piece, x, y)) engine.place(slot, x, y) else null
                if (move != null) {
                    val t = System.nanoTime() / 1_000_000L
                    move.placed.forEach { placedAt[it] = t }
                    if (move.lines > 0) {
                        val rnd = Random(move.id)
                        val particles = move.clearedCells.flatMap { (idx, color) ->
                            val cx = boardLeft + (idx % N + 0.5f) * cell
                            val cy = boardTop + (idx / N + 0.5f) * cell
                            List(3) {
                                val a = rnd.nextFloat() * 2f * PI.toFloat()
                                val speed = cell * (2.5f + rnd.nextFloat() * 4f)
                                Particle(cx, cy, cos(a) * speed, sin(a) * speed - cell * 3f, palette[(color - 1).coerceIn(0, palette.lastIndex)], cell * (0.12f + rnd.nextFloat() * 0.14f), rnd.nextFloat() * 720f - 360f)
                            }
                        }
                        clearFx = ClearFx(move, t, particles)
                        shake = if (move.lines >= 2) cell * 0.08f * min(move.lines, 4) else 0f
                        animateFor(1_100)
                    } else {
                        animateFor(260)
                    }
                    if (!celebratedBest && bestAtStart > 0 && engine.score > bestAtStart) celebratedBest = true
                } else {
                    // Back to its slot, with a spring.
                    returningSlot = slot
                    scope.launch {
                        returning.snapTo(dragPos)
                        launch { dragScale.animateTo(0.55f, spring(dampingRatio = 0.7f, stiffness = 500f)) }
                        returning.animateTo(slotCenter(slot), spring(dampingRatio = 0.75f, stiffness = 420f))
                        returningSlot = -1
                    }
                }
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(engine, boardSize, wPx, hPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            if (engine.over) return@awaitEachGesture
                            val slot = (0..2).firstOrNull { slotRect(it).contains(down.position) } ?: return@awaitEachGesture
                            if (engine.tray.getOrNull(slot) == null) return@awaitEachGesture
                            GamePlaytime.touch()
                            dragSlot = slot
                            dragPos = down.position
                            scope.launch { dragScale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 700f)) }
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                dragPos = change.position
                                change.consume()
                            }
                            drop()
                        }
                    },
            ) {
                val t = now
                // Board shake after a big clear.
                val fx = clearFx
                val sinceClear = if (fx != null) (t - fx.startMs).toFloat() else Float.MAX_VALUE
                val shakeNow = if (sinceClear < 380f) shake * (1f - sinceClear / 380f) * sin(sinceClear / 22f) else 0f

                translate(left = shakeNow) {
                    drawBoardFrame(boardLeft, boardTop, boardSize, cell, scheme.surfaceContainerLowest, scheme.surfaceContainerHigh, engine.combo, t)

                    // Highlight what the dragged piece would complete, in its own colour.
                    val dragged = engine.tray.getOrNull(dragSlot)
                    var ghost: Pair<Int, Int>? = null
                    var clearRows = emptyList<Int>()
                    var clearCols = emptyList<Int>()
                    if (dragged != null) {
                        val (sx, sy) = snap(dragged, dragPos)
                        if (engine.canPlace(dragged, sx, sy)) {
                            ghost = sx to sy
                            val (r, c) = engine.wouldClear(dragged, sx, sy)
                            clearRows = r
                            clearCols = c
                        }
                    }
                    val draggedColor = dragged?.let { palette[it.color - 1] }

                    @Suppress("UNUSED_EXPRESSION") engine.rev
                    val overProgress = if (engine.over) ((t - overAt) / 900f).coerceIn(0f, 1f) else 0f
                    for (i in 0 until N * N) {
                        val v = engine.board[i]
                        val x = i % N
                        val y = i / N
                        val inClear = y in clearRows || x in clearCols
                        if (v == 0 && !inClear) continue
                        var color = if (inClear && draggedColor != null) draggedColor else palette[(v - 1).coerceIn(0, palette.lastIndex)]
                        // Game over: the board greys out from the bottom up.
                        if (engine.over) {
                            val local = (overProgress * (N + 2) - (N - 1 - y)).coerceIn(0f, 1f)
                            color = lerp(color, scheme.outline, local * 0.85f)
                        }
                        val pop = placedAt[i]?.let { ((t - it) / 220f).coerceIn(0f, 1f) } ?: 1f
                        val scale = if (pop < 1f) 0.82f + 0.18f * springish(pop) else 1f
                        drawBlockTile(Offset(boardLeft + x * cell, boardTop + y * cell), cell, color, scale = scale, glow = inClear && v != 0)
                    }
                    if (dragged != null && ghost != null) {
                        val (gx, gy) = ghost
                        dragged.cells.forEach { (cx, cy) ->
                            drawBlockTile(Offset(boardLeft + (gx + cx) * cell, boardTop + (gy + cy) * cell), cell, palette[dragged.color - 1], alpha = 0.38f)
                        }
                    }

                    // Cleared cells: shrink and spin away, line by line from the middle out.
                    if (fx != null && sinceClear < 1_100f) {
                        val rows = fx.move.clearedRows
                        val cols = fx.move.clearedCols
                        fx.move.clearedCells.forEach { (idx, color) ->
                            val x = idx % N
                            val y = idx / N
                            val along = if (y in rows) kotlin.math.abs(x - 3.5f) else kotlin.math.abs(y - 3.5f)
                            val local = ((sinceClear - along * 28f) / 320f).coerceIn(0f, 1f)
                            if (local >= 1f) return@forEach
                            val c = palette[(color - 1).coerceIn(0, palette.lastIndex)]
                            val center = Offset(boardLeft + (x + 0.5f) * cell, boardTop + (y + 0.5f) * cell)
                            rotate(local * 90f, center) {
                                drawBlockTile(Offset(center.x - cell / 2, center.y - cell / 2), cell, lerp(c, Color.White, 0.5f * (1f - local)), scale = 1f + 0.15f * local - local, alpha = 1f - local * 0.6f)
                            }
                        }
                        // A white flash along each cleared line.
                        val flash = (1f - sinceClear / 260f).coerceIn(0f, 1f)
                        if (flash > 0f) {
                            rows.forEach { r -> drawRoundRect(Color.White.copy(alpha = 0.55f * flash), Offset(boardLeft, boardTop + r * cell), Size(boardSize, cell), CornerRadius(cell * 0.2f)) }
                            cols.forEach { c -> drawRoundRect(Color.White.copy(alpha = 0.55f * flash), Offset(boardLeft + c * cell, boardTop), Size(cell, boardSize), CornerRadius(cell * 0.2f)) }
                        }
                    }
                }

                // Particles fly over everything, falling.
                if (fx != null && sinceClear < 1_100f) {
                    val s = sinceClear / 1000f
                    val g = cell * 14f
                    fx.particles.forEach { p ->
                        val life = (1f - s / 1.1f).coerceIn(0f, 1f)
                        if (life <= 0f) return@forEach
                        val px = p.x + p.vx * s
                        val py = p.y + p.vy * s + 0.5f * g * s * s
                        rotate(p.spin * s, Offset(px, py)) {
                            drawRoundRect(p.color.copy(alpha = life), Offset(px - p.size / 2, py - p.size / 2), Size(p.size, p.size), CornerRadius(p.size * 0.25f))
                        }
                    }
                }

                // ─── Tray ───────────────────────────────────────────────────────────────
                val trayAppear = ((t - trayShownAt) / 420f).coerceIn(0f, 1f)
                engine.tray.forEachIndexed { i, piece ->
                    if (piece == null || i == dragSlot || i == returningSlot) return@forEachIndexed
                    val local = ((trayAppear * 1.5f) - i * 0.25f).coerceIn(0f, 1f)
                    val sc = springish(local)
                    val fits = engine.fitsAnywhere(piece)
                    val center = slotCenter(i)
                    drawPiece(piece, center, trayCell * sc, palette[piece.color - 1], alpha = if (fits) 1f else 0.32f)
                }
                // The piece in hand, or flying back.
                val dragged = engine.tray.getOrNull(dragSlot)
                if (dragged != null) {
                    val size = trayCell + (cell - trayCell) * ((dragScale.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    drawPiece(dragged, dragPos - Offset(0f, cell * 0.9f), size * 0.96f, palette[dragged.color - 1], shadow = true)
                }
                val back = engine.tray.getOrNull(returningSlot)
                if (back != null) {
                    val size = trayCell + (cell - trayCell) * ((dragScale.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
                    drawPiece(back, returning.value - Offset(0f, cell * 0.9f * ((dragScale.value - 0.55f) / 0.45f).coerceIn(0f, 1f)), size, palette[back.color - 1])
                }
            }

            // ─── Words over the board ───────────────────────────────────────────────────
            val boardCenterDp = with(density) { Offset(boardLeft + boardSize / 2, boardTop + boardSize / 2) }
            engine.lastMove?.let { move ->
                if (move.points > 0) {
                    val anchor = move.placed.fold(Offset.Zero) { acc, i -> acc + Offset(boardLeft + (i % N + 0.5f) * cell, boardTop + (i / N + 0.5f) * cell) } / move.placed.size.toFloat()
                    FloatingPoints(move.id, "+${move.points}", anchor)
                }
                if (move.lines >= 2 || move.combo >= 2 || move.allClear) {
                    PraiseBurst(move, boardCenterDp)
                }
            }
            if (celebratedBest) NewBestBanner(Offset(boardLeft + boardSize / 2, boardTop + cell * 0.6f))

            if (engine.over) {
                val overShown = ((now - overAt) / 1_000f) >= 1f
                Box(
                    Modifier
                        .offset { IntOffset(boardLeft.roundToInt(), boardTop.roundToInt()) }
                        .width(with(density) { boardSize.toDp() })
                        .height(with(density) { boardSize.toDp() }),
                ) {
                    BoardScrim(visible = overShown) {
                        val record = engine.score >= best && engine.score > 0
                        GameEndCard(
                            title = str(if (record) "arcade_new_record" else "blocks_no_space"),
                            body = str("arcade_score_is", engine.score) + "  ·  " + str("game_best", max(best, engine.score)),
                            icon = if (record) Icons.Rounded.EmojiEvents else Icons.Rounded.Widgets,
                            onRestart = restart,
                        )
                    }
                }
            }
        }
    }
}

/** 0..1 with a small overshoot, for pops without an Animatable per cell. */
private fun springish(t: Float): Float {
    if (t >= 1f) return 1f
    val c = 1.70158f * 1.3f
    val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

@Composable
private fun ComboChip(combo: Int) {
    val scheme = MaterialTheme.colorScheme
    val pop = remember { Animatable(0f) }
    LaunchedEffect(combo) {
        if (combo >= 2) {
            pop.snapTo(1.35f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        } else {
            pop.animateTo(0f, tween(180))
        }
    }
    if (pop.value <= 0.01f) return
    Surface(
        shape = RoundedCornerShape(50),
        color = scheme.tertiaryContainer,
        contentColor = scheme.onTertiaryContainer,
        modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Rounded.LocalFireDepartment, null)
            Spacer(Modifier.width(4.dp))
            Text(str("blocks_combo", combo.coerceAtLeast(2)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun FloatingPoints(id: Long, text: String, anchor: Offset) {
    val progress = remember(id) { Animatable(0f) }
    LaunchedEffect(id) { progress.animateTo(1f, tween(900)) }
    if (progress.value >= 1f) return
    val p = progress.value
    val density = LocalDensity.current
    Box(Modifier.offset { IntOffset((anchor.x - with(density) { 40.dp.toPx() }).roundToInt(), (anchor.y - with(density) { 18.dp.toPx() } - p * with(density) { 46.dp.toPx() }).roundToInt()) }.width(80.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            modifier = Modifier.graphicsLayer {
                alpha = if (p < 0.7f) 1f else (1f - (p - 0.7f) / 0.3f)
                val s = 0.6f + 0.4f * springish((p * 4f).coerceAtMost(1f))
                scaleX = s
                scaleY = s
            },
        )
    }
}

/** "Great!", the combo, "All clear!": a big word in the middle of the board. */
@Composable
private fun PraiseBurst(move: BlocksMove, center: Offset) {
    val scheme = MaterialTheme.colorScheme
    val progress = remember(move.id) { Animatable(0f) }
    LaunchedEffect(move.id) { progress.animateTo(1f, tween(1_300)) }
    if (progress.value >= 1f) return
    val p = progress.value
    val tier = when {
        move.allClear -> 5
        move.lines >= 4 || move.combo >= 6 -> 4
        move.lines == 3 || move.combo >= 4 -> 3
        move.lines == 2 || move.combo >= 3 -> 2
        else -> 1
    }
    val word = if (move.allClear) str("blocks_all_clear") else str("blocks_praise_$tier")
    val density = LocalDensity.current
    val width = 360.dp
    Box(
        Modifier.offset { IntOffset((center.x - with(density) { width.toPx() } / 2).roundToInt(), (center.y - with(density) { 60.dp.toPx() }).roundToInt()) }.width(width),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val s = 0.4f + 0.6f * springish((p * 3.2f).coerceAtMost(1f))
                scaleX = s
                scaleY = s
                alpha = if (p < 0.75f) 1f else 1f - (p - 0.75f) / 0.25f
                translationY = -p * 30f
            },
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = scheme.primary, contentColor = scheme.onPrimary, shadowElevation = 6.dp) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(8.dp))
                    Text(word, fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
            }
            if (move.combo >= 2) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(50), color = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer) {
                    Text(str("blocks_combo", move.combo), Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun NewBestBanner(anchor: Offset) {
    val scheme = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2_200)) }
    if (progress.value >= 1f) return
    val p = progress.value
    val density = LocalDensity.current
    Box(Modifier.offset { IntOffset((anchor.x - with(density) { 120.dp.toPx() }).roundToInt(), anchor.y.roundToInt()) }.width(240.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50),
            color = scheme.tertiary,
            contentColor = scheme.onTertiary,
            modifier = Modifier.graphicsLayer {
                val s = springish((p * 5f).coerceAtMost(1f))
                scaleX = s
                scaleY = s
                alpha = if (p < 0.85f) 1f else 1f - (p - 0.85f) / 0.15f
            },
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Rounded.EmojiEvents, null)
                Spacer(Modifier.width(6.dp))
                Text(str("arcade_new_record"), fontWeight = FontWeight.Black)
            }
        }
    }
}

// ─── Drawing ────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBoardFrame(left: Float, top: Float, size: Float, cell: Float, bg: Color, slot: Color, combo: Int, now: Long) {
    val pad = cell * 0.16f
    drawRoundRect(bg, Offset(left - pad, top - pad), Size(size + pad * 2, size + pad * 2), CornerRadius(cell * 0.5f))
    // A rainbow rim while a streak runs, a little brighter with every combo.
    if (combo >= 3) {
        val colors = listOf(Color(0xFFFF6B8B), Color(0xFFFFB86B), Color(0xFFFFE36B), Color(0xFF7BE495), Color(0xFF6BC8FF), Color(0xFFB08BFF), Color(0xFFFF6B8B))
        drawRoundRect(
            Brush.sweepGradient(colors, Offset(left + size / 2, top + size / 2)),
            Offset(left - pad, top - pad), Size(size + pad * 2, size + pad * 2), CornerRadius(cell * 0.5f),
            style = Stroke(width = cell * (0.06f + 0.015f * min(combo, 8))),
            alpha = 0.9f,
        )
    }
    val inset = cell * 0.06f
    for (y in 0 until N) for (x in 0 until N) {
        drawRoundRect(slot, Offset(left + x * cell + inset, top + y * cell + inset), Size(cell - inset * 2, cell - inset * 2), CornerRadius(cell * 0.18f))
    }
}

/** One jelly block: a light-to-dark body, a lighter inner face and a gloss in the corner. */
internal fun DrawScope.drawBlockTile(topLeft: Offset, cell: Float, color: Color, scale: Float = 1f, alpha: Float = 1f, glow: Boolean = false) {
    val s = cell * scale
    val o = topLeft + Offset((cell - s) / 2f, (cell - s) / 2f)
    val inset = s * 0.05f
    val size = s - inset * 2
    val origin = o + Offset(inset, inset)
    val r = CornerRadius(size * 0.2f)
    if (glow) drawRoundRect(Color.White.copy(alpha = 0.35f * alpha), origin - Offset(inset, inset), Size(size + inset * 2, size + inset * 2), CornerRadius(size * 0.26f))
    drawRoundRect(
        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.22f), lerp(color, Color.Black, 0.22f)), origin.y, origin.y + size),
        origin, Size(size, size), r, alpha = alpha,
    )
    val face = size * 0.16f
    drawRoundRect(
        Brush.verticalGradient(listOf(lerp(color, Color.White, 0.12f), color), origin.y + face, origin.y + size - face),
        origin + Offset(face, face * 0.8f), Size(size - face * 2, size - face * 2), CornerRadius(size * 0.12f), alpha = alpha,
    )
    drawRoundRect(Color.White.copy(alpha = 0.45f * alpha), origin + Offset(size * 0.14f, size * 0.1f), Size(size * 0.26f, size * 0.1f), CornerRadius(size * 0.05f))
}

private fun DrawScope.drawPiece(piece: BlockPiece, center: Offset, cell: Float, color: Color, alpha: Float = 1f, shadow: Boolean = false) {
    val left = center.x - piece.width * cell / 2f
    val top = center.y - piece.height * cell / 2f
    if (shadow) {
        piece.cells.forEach { (x, y) ->
            drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(left + x * cell + cell * 0.12f, top + y * cell + cell * 0.18f), Size(cell * 0.9f, cell * 0.9f), CornerRadius(cell * 0.2f))
        }
    }
    piece.cells.forEach { (x, y) -> drawBlockTile(Offset(left + x * cell, top + y * cell), cell, color, alpha = alpha) }
}

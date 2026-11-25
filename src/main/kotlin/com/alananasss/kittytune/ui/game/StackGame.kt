@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.core.LocalWindowSeen
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.Tip
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val STACK_BEST = "stack_best"

private const val DAS = 0.16f
private const val ARR = 0.045f

/** The guideline colours of the seven pieces (cyan I, yellow O, purple T, green S, red Z, blue J, orange L). */
@Composable
private fun stackPalette(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.red + scheme.surface.green + scheme.surface.blue < 1.5f
    return remember(scheme.primary, dark) {
        floatArrayOf(188f, 48f, 282f, 130f, 354f, 222f, 28f).map { h ->
            lerp(Color.hsv(h, if (dark) 0.72f else 0.8f, if (dark) 0.97f else 0.92f), scheme.primary, 0.04f)
        }
    }
}

/** What the player is holding down, for auto-repeat (DAS / ARR) and soft drop. */
private class Held {
    var left = false
    var right = false
    var down = false
    var dir = 0
    var das = 0f
    var arr = 0f
    val pressed = mutableSetOf<Key>()
}

@Composable
internal fun StackGame() {
    val engine = remember { StackEngine() }
    var best by remember { mutableIntStateOf(GameRecords.best(STACK_BEST)) }
    var paused by remember { mutableStateOf(false) }
    val held = remember { Held() }
    val scheme = MaterialTheme.colorScheme
    val palette = stackPalette()
    val seen = LocalWindowSeen.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // A hidden window pauses the game: nobody plays what they cannot see.
    LaunchedEffect(seen) { if (!seen && !engine.over) paused = true }
    TrackGamePlaytime(GameId.STACK, active = !engine.over && !paused)

    LaunchedEffect(engine.over) {
        if (engine.over) best = GameRecords.offer(STACK_BEST, engine.score)
    }
    val running = !engine.over && !paused
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        runGameFrames { dt ->
            now = System.currentTimeMillis()
            // Auto-repeat: one step on press, then after DAS one step every ARR.
            if (held.dir != 0) {
                held.das += dt
                if (held.das >= DAS) {
                    held.arr += dt
                    while (held.arr >= ARR) {
                        held.arr -= ARR
                        if (!engine.move(held.dir)) break
                    }
                }
            }
            engine.tick(dt, held.down)
            !engine.over && !paused
        }
        // Let the last flash finish.
        val until = System.currentTimeMillis() + 400
        runGameFrames {
            now = System.currentTimeMillis()
            now < until
        }
    }

    val restart = {
        best = GameRecords.offer(STACK_BEST, engine.score)
        paused = false
        engine.reset()
    }

    fun press(key: Key, char: Char?, event: KeyEvent): Boolean {
        val lower = char?.lowercaseChar()
        val isLeft = key == Key.DirectionLeft || key == Key.NumPad4
        val isRight = key == Key.DirectionRight || key == Key.NumPad6
        val isDown = key == Key.DirectionDown || key == Key.NumPad2
        val isCw = key == Key.DirectionUp || key == Key.X || key == Key.NumPad8 || lower == 'x'
        val isCcw = key == Key.Z || key == Key.W || lower == 'z' || lower == 'w' || key == Key.CtrlLeft || key == Key.CtrlRight
        val isDrop = key == Key.Spacebar || key == Key.NumPad5
        val isHold = key == Key.C || lower == 'c' || key == Key.ShiftLeft || key == Key.ShiftRight
        val isPause = key == Key.P || lower == 'p' || key == Key.F1
        val isRestart = key == Key.Enter || key == Key.NumPadEnter
        if (!(isLeft || isRight || isDown || isCw || isCcw || isDrop || isHold || isPause || isRestart)) return false

        if (event.type == KeyEventType.KeyUp) {
            held.pressed -= key
            if (isLeft) { held.left = false; held.dir = if (held.right) 1 else 0; held.das = 0f; held.arr = 0f }
            if (isRight) { held.right = false; held.dir = if (held.left) -1 else 0; held.das = 0f; held.arr = 0f }
            if (isDown) held.down = false
            return true
        }
        if (event.type != KeyEventType.KeyDown) return true
        if (key in held.pressed) return true // the system's own key repeat: ours does that job
        held.pressed += key
        GamePlaytime.touch()
        if (isPause) { if (!engine.over) paused = !paused; return true }
        if (isRestart) { if (engine.over) restart() else if (paused) paused = false; return true }
        if (paused || engine.over) return true
        when {
            isLeft -> { held.left = true; held.dir = -1; held.das = 0f; held.arr = 0f; engine.move(-1) }
            isRight -> { held.right = true; held.dir = 1; held.das = 0f; held.arr = 0f; engine.move(1) }
            isDown -> { held.down = true; engine.softDrop() }
            isCw -> engine.rotate(1)
            isCcw -> engine.rotate(-1)
            isDrop -> engine.hardDrop()
            isHold -> engine.holdPiece()
        }
        return true
    }

    GameKeyHandler { event ->
        val char = runCatching { event.utf16CodePoint.toChar() }.getOrNull()
        @Suppress("UNUSED_VARIABLE") val mods = event.isCtrlPressed || event.isShiftPressed
        press(event.key, char, event)
    }

    val shownScore by animateIntAsState(engine.score, tween(300), label = "score")

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val side = if (maxWidth > 760.dp) 190.dp else 140.dp
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                // ─── Left: hold and numbers ───────────────────────────────────────────────
                Column(Modifier.width(side).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PiecePanel(str("stack_hold"), listOfNotNull(engine.hold), palette, dimmed = engine.holdUsed, slots = 1)
                    GameStat(str("arcade_score"), "$shownScore", Icons.Rounded.Star, Modifier.fillMaxWidth(), emphasized = true, compact = true)
                    GameStat(str("bricks_level"), "${engine.level}", Icons.Rounded.Layers, Modifier.fillMaxWidth(), compact = true)
                    GameStat(str("stack_lines"), "${engine.lines}", Icons.Rounded.ViewModule, Modifier.fillMaxWidth(), compact = true)
                    GameStat(str("game_best_label"), "${max(best, engine.score)}", Icons.Rounded.EmojiEvents, Modifier.fillMaxWidth(), compact = true)
                }
                // ─── The well ─────────────────────────────────────────────────────────────
                BoxWithConstraints(Modifier.fillMaxHeight().weight(1f, fill = false), contentAlignment = Alignment.TopCenter) {
                    val visibleRows = StackEngine.H - StackEngine.HIDDEN
                    val density = LocalDensity.current
                    val cellPx = with(density) { min(maxHeight.toPx() / visibleRows, (maxWidth.toPx()) / StackEngine.W) }
                    val wellW = with(density) { (cellPx * StackEngine.W).toDp() }
                    val wellH = with(density) { (cellPx * visibleRows).toDp() }
                    Box(Modifier.size(wellW, wellH)) {
                        Well(engine, palette, cellPx, now)
                        StackPopup(engine.lastClear, Offset(cellPx * StackEngine.W / 2f, cellPx * visibleRows * 0.38f))
                        BoardScrim(visible = paused && !engine.over) {
                            Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh) {
                                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(str("game_paused"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { paused = false }, shapes = ButtonDefaults.shapes()) {
                                        Icon(Icons.Rounded.PlayArrow, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(str("btn_play"))
                                    }
                                }
                            }
                        }
                        BoardScrim(visible = engine.over) {
                            val record = engine.score >= best && engine.score > 0
                            GameEndCard(
                                title = str(if (record) "arcade_new_record" else "stack_over"),
                                body = str("arcade_score_is", engine.score) + "\n" + str("stack_lines_count", engine.lines),
                                icon = if (record) Icons.Rounded.EmojiEvents else Icons.Rounded.ViewModule,
                                onRestart = restart,
                            )
                        }
                    }
                }
                // ─── Right: next pieces ───────────────────────────────────────────────────
                Column(Modifier.width(side).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PiecePanel(str("stack_next"), engine.queue, palette, slots = StackEngine.QUEUE)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Buttons for those playing with the mouse; the keyboard does the same, faster.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            ControlButton(Icons.Rounded.SwapHoriz, str("stack_hold")) { engine.holdPiece() }
            ControlButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, str("game_left")) { engine.move(-1) }
            ControlButton(Icons.Rounded.Rotate90DegreesCcw, str("stack_rotate_left")) { engine.rotate(-1) }
            ControlButton(Icons.Rounded.Rotate90DegreesCw, str("stack_rotate_right")) { engine.rotate(1) }
            ControlButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, str("game_right")) { engine.move(1) }
            ControlButton(Icons.Rounded.KeyboardArrowDown, str("stack_soft_drop")) { engine.softDrop() }
            ControlButton(Icons.Rounded.VerticalAlignBottom, str("stack_hard_drop"), primary = true) { engine.hardDrop() }
            Spacer(Modifier.width(8.dp))
            ControlButton(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, str(if (paused) "btn_play" else "action_pause")) { if (!engine.over) paused = !paused }
            NewGameButton(restart)
        }
        Spacer(Modifier.height(8.dp))
        Text(str("stack_hint"), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, primary: Boolean = false, onClick: () -> Unit) {
    Tip(label) {
        FilledTonalIconButton(
            onClick = { GamePlaytime.touch(); onClick() },
            shapes = IconButtonDefaults.shapes(),
            colors = if (primary) IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) else IconButtonDefaults.filledTonalIconButtonColors(),
            // Not focusable: Space must keep dropping pieces, not press the last button clicked.
            modifier = Modifier.size(48.dp).focusProperties { canFocus = false },
        ) { Icon(icon, contentDescription = label) }
    }
}

@Composable
private fun Well(engine: StackEngine, palette: List<Color>, cell: Float, now: Long) {
    val scheme = MaterialTheme.colorScheme
    val hidden = StackEngine.HIDDEN
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION") engine.rev
        val clear = engine.lastClear
        val sinceClear = clear?.let { (now - it.at).toFloat() } ?: Float.MAX_VALUE
        // A short jolt for four lines at once.
        val jolt = if (clear != null && clear.rows.size >= 4 && sinceClear < 300f) cell * 0.12f * (1f - sinceClear / 300f) * sin(sinceClear / 18f) else 0f
        translate(top = jolt) {
            drawRoundRect(scheme.surfaceContainerLowest, Offset.Zero, size, CornerRadius(cell * 0.4f))
            for (y in 0 until StackEngine.H - hidden) for (x in 0 until StackEngine.W) {
                drawRoundRect(scheme.surfaceContainer, Offset(x * cell + cell * 0.08f, y * cell + cell * 0.08f), Size(cell * 0.84f, cell * 0.84f), CornerRadius(cell * 0.18f))
            }
            for (y in hidden until StackEngine.H) for (x in 0 until StackEngine.W) {
                val v = engine.board[y * StackEngine.W + x]
                if (v == 0) continue
                val color = if (engine.over) lerp(palette[v - 1], scheme.outline, 0.7f) else palette[v - 1]
                drawBlockTile(Offset(x * cell, (y - hidden) * cell), cell, color)
            }
            // Hard drop: a streak from where the piece was to where it landed.
            engine.lastDrop?.let { (from, rows, at) ->
                val p = ((now - at) / 180f).coerceIn(0f, 1f)
                if (p < 1f && rows > 0) {
                    val color = palette[from.type.color - 1]
                    from.cells().map { it.first }.distinct().forEach { x ->
                        val top = from.cells().filter { it.first == x }.minOf { it.second } - hidden
                        val bottom = top + rows
                        drawRect(
                            Brush.verticalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.35f * (1f - p))), top * cell, bottom * cell),
                            Offset(x * cell + cell * 0.15f, top * cell), Size(cell * 0.7f, (bottom - top) * cell),
                        )
                    }
                }
            }
            val piece = engine.current
            if (piece != null && !engine.over) {
                val color = palette[piece.type.color - 1]
                engine.ghost()?.let { g ->
                    if (g.y != piece.y) g.cells().forEach { (x, y) ->
                        if (y < hidden) return@forEach
                        val o = Offset(x * cell + cell * 0.08f, (y - hidden) * cell + cell * 0.08f)
                        drawRoundRect(color.copy(alpha = 0.14f), o, Size(cell * 0.84f, cell * 0.84f), CornerRadius(cell * 0.18f))
                        drawRoundRect(color.copy(alpha = 0.6f), o, Size(cell * 0.84f, cell * 0.84f), CornerRadius(cell * 0.18f), style = Stroke(cell * 0.06f))
                    }
                }
                piece.cells().forEach { (x, y) -> if (y >= hidden) drawBlockTile(Offset(x * cell, (y - hidden) * cell), cell, color) }
            }
            // Cleared rows flash white and thin out.
            if (clear != null && sinceClear < 320f) {
                val p = sinceClear / 320f
                clear.rows.forEach { r ->
                    val h = cell * (1f - p)
                    drawRoundRect(Color.White.copy(alpha = 0.7f * (1f - p)), Offset(0f, (r - hidden) * cell + (cell - h) / 2f), Size(size.width, h), CornerRadius(cell * 0.3f))
                }
            }
        }
    }
}

@Composable
private fun PiecePanel(title: String, pieces: List<Tetromino>, palette: List<Color>, dimmed: Boolean = false, slots: Int) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height((if (slots == 1) 64 else 64 + (slots - 1) * 48).dp)) {
                val first = 64.dp.toPx()
                val rest = 48.dp.toPx()
                pieces.take(slots).forEachIndexed { i, t ->
                    val slotH = if (i == 0) first else rest
                    val top = if (i == 0) 0f else first + (i - 1) * rest
                    val cell = min(slotH / 2.6f, size.width / 5f) * if (i == 0) 1f else 0.8f
                    drawMiniPiece(t, Offset(size.width / 2f, top + slotH / 2f), cell, palette[t.color - 1], if (dimmed) 0.35f else 1f)
                }
            }
        }
    }
}

private fun DrawScope.drawMiniPiece(t: Tetromino, center: Offset, cell: Float, color: Color, alpha: Float) {
    val cells = t.cellsAt(0)
    val minX = cells.minOf { it.first }
    val maxX = cells.maxOf { it.first }
    val minY = cells.minOf { it.second }
    val maxY = cells.maxOf { it.second }
    val w = (maxX - minX + 1) * cell
    val h = (maxY - minY + 1) * cell
    cells.forEach { (x, y) ->
        drawBlockTile(Offset(center.x - w / 2 + (x - minX) * cell, center.y - h / 2 + (y - minY) * cell), cell, color, alpha = alpha)
    }
}

/** "Triple", "4 lines!", "T-spin", "Back-to-back", combo, points: a word over the well after a clear. */
@Composable
private fun StackPopup(clear: StackClear?, anchor: Offset) {
    clear ?: return
    if (clear.points <= 0) return
    val scheme = MaterialTheme.colorScheme
    val progress = remember(clear.id) { Animatable(0f) }
    LaunchedEffect(clear.id) { progress.animateTo(1f, tween(1_200)) }
    if (progress.value >= 1f) return
    val p = progress.value
    val title = when {
        clear.perfect -> str("blocks_all_clear")
        clear.tSpin -> str("stack_tspin", clear.rows.size)
        else -> str("stack_clear_${clear.rows.size.coerceIn(1, 4)}")
    }
    val density = LocalDensity.current
    val width = 280.dp
    Box(
        Modifier.offset { IntOffset((anchor.x - with(density) { width.toPx() } / 2).roundToInt(), anchor.y.roundToInt()) }.width(width),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val s = 0.5f + 0.5f * min(1f, p * 5f)
                scaleX = s
                scaleY = s
                alpha = if (p < 0.75f) 1f else 1f - (p - 0.75f) / 0.25f
                translationY = -p * 40f
            },
        ) {
            val big = clear.rows.size >= 4 || clear.tSpin || clear.perfect
            Surface(shape = RoundedCornerShape(24.dp), color = if (big) scheme.primary else scheme.secondaryContainer, contentColor = if (big) scheme.onPrimary else scheme.onSecondaryContainer, shadowElevation = 4.dp) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (big) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(title, fontWeight = FontWeight.Black, fontSize = if (big) 24.sp else 18.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            val extras = buildList {
                if (clear.backToBack) add(str("stack_b2b"))
                if (clear.combo >= 1) add(str("blocks_combo", clear.combo + 1))
                add("+${clear.points}")
            }
            Text(extras.joinToString("  ·  "), color = scheme.onSurface, fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
    }
}

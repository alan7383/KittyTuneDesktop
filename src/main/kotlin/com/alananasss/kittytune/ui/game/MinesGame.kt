@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.Tip
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal enum class MinesLevel(val cols: Int, val rows: Int, val mines: Int, val labelKey: String) {
    EASY(9, 9, 10, "mines_easy"),
    MEDIUM(16, 16, 40, "mines_medium"),
    HARD(30, 16, 99, "mines_hard"),
}

internal fun minesBestKey(level: MinesLevel) = "mines_best_${level.name.lowercase()}"

internal enum class MinesState { READY, PLAYING, WON, LOST }

/** Minesweeper: the first click is always safe and opens an area, numbers chord, flags count down. */
internal class MinesEngine(val level: MinesLevel, private val random: Random = Random.Default) {
    val cols = level.cols
    val rows = level.rows
    val count = cols * rows
    val mines = BooleanArray(count)
    val revealed = BooleanArray(count)
    val flagged = BooleanArray(count)
    val adjacent = IntArray(count)
    /** When each cell opened, in ms after [revealEpoch]-relative wave timing, for the ripple. */
    val revealDelay = IntArray(count)
    var state by mutableStateOf(MinesState.READY)
        private set
    var rev by mutableIntStateOf(0)
        private set
    var flags by mutableIntStateOf(0)
        private set
    var exploded = -1
        private set
    var startedAt = 0L
        private set
    var finishedAt = 0L
        private set

    fun neighbours(i: Int): List<Int> {
        val x = i % cols
        val y = i / cols
        val out = ArrayList<Int>(8)
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until cols && ny in 0 until rows) out += ny * cols + nx
        }
        return out
    }

    private fun layMines(safe: Int) {
        val forbidden = neighbours(safe).toHashSet() + safe
        val pool = (0 until count).filter { it !in forbidden }.shuffled(random)
        pool.take(level.mines).forEach { mines[it] = true }
        for (i in 0 until count) adjacent[i] = neighbours(i).count { mines[it] }
    }

    /** Opens [i]; returns the cells opened (empty if nothing changed). */
    fun reveal(i: Int, now: Long = System.currentTimeMillis()): List<Int> {
        if (state == MinesState.WON || state == MinesState.LOST) return emptyList()
        if (flagged[i] || revealed[i]) return emptyList()
        if (state == MinesState.READY) {
            layMines(i)
            state = MinesState.PLAYING
            startedAt = now
        }
        if (mines[i]) {
            revealed[i] = true
            exploded = i
            lose(now)
            return listOf(i)
        }
        val opened = flood(i)
        checkWin(now)
        rev++
        return opened
    }

    /** On an open number whose flags are all placed, opens every other neighbour. */
    fun chord(i: Int, now: Long = System.currentTimeMillis()): List<Int> {
        if (state != MinesState.PLAYING || !revealed[i] || adjacent[i] == 0) return emptyList()
        val around = neighbours(i)
        if (around.count { flagged[it] } != adjacent[i]) return emptyList()
        val opened = mutableListOf<Int>()
        for (n in around) {
            if (flagged[n] || revealed[n]) continue
            if (mines[n]) {
                revealed[n] = true
                exploded = n
                lose(now)
                return opened + n
            }
            opened += flood(n, origin = i)
        }
        checkWin(now)
        rev++
        return opened
    }

    fun toggleFlag(i: Int): Boolean {
        if (state == MinesState.WON || state == MinesState.LOST || revealed[i]) return false
        flagged[i] = !flagged[i]
        flags += if (flagged[i]) 1 else -1
        rev++
        return true
    }

    val minesLeft: Int get() = level.mines - flags

    fun elapsedSeconds(now: Long = System.currentTimeMillis()): Int = when (state) {
        MinesState.READY -> 0
        MinesState.PLAYING -> ((now - startedAt) / 1000).toInt()
        else -> ((finishedAt - startedAt) / 1000).toInt()
    }

    private fun flood(start: Int, origin: Int = start): List<Int> {
        val ox = origin % cols
        val oy = origin / cols
        val out = mutableListOf<Int>()
        val queue = ArrayDeque<Int>()
        queue += start
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (revealed[c] || flagged[c] || mines[c]) continue
            revealed[c] = true
            revealDelay[c] = (hypot((c % cols - ox).toFloat(), (c / cols - oy).toFloat()) * 22f).toInt()
            out += c
            if (adjacent[c] == 0) neighbours(c).forEach { if (!revealed[it]) queue += it }
        }
        return out
    }

    private fun checkWin(now: Long) {
        if (state != MinesState.PLAYING) return
        if ((0 until count).all { mines[it] || revealed[it] }) {
            state = MinesState.WON
            finishedAt = now
            // Flag what is left, the way a finished board looks.
            for (i in 0 until count) if (mines[i] && !flagged[i]) { flagged[i] = true; flags++ }
        }
    }

    private fun lose(now: Long) {
        state = MinesState.LOST
        finishedAt = now
        val ex = exploded % cols
        val ey = exploded / cols
        for (i in 0 until count) if (mines[i]) {
            revealDelay[i] = 200 + (hypot((i % cols - ex).toFloat(), (i / cols - ey).toFloat()) * 45f).toInt()
        }
        rev++
    }
}

@Composable
internal fun MinesGame() {
    var level by remember { mutableStateOf(MinesLevel.EASY) }
    var engine by remember(level) { mutableStateOf(MinesEngine(level)) }
    var best by remember(level) { mutableIntStateOf(GameRecords.best(minesBestKey(level))) }
    var flagMode by remember { mutableStateOf(false) }
    var hover by remember(engine) { mutableIntStateOf(-1) }
    val scheme = MaterialTheme.colorScheme

    TrackGamePlaytime(GameId.MINES, active = engine.state == MinesState.PLAYING)

    // Frame clock for the ripples, the flags popping and the mines going off.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fxUntil by remember { mutableLongStateOf(0L) }
    var waveAt by remember(engine) { mutableLongStateOf(0L) }
    val flagAt = remember(engine) { mutableMapOf<Int, Long>() }
    LaunchedEffect(fxUntil) {
        runGameFrames {
            now = System.currentTimeMillis()
            now < fxUntil
        }
    }
    fun animate(ms: Long) {
        now = System.currentTimeMillis()
        fxUntil = max(fxUntil, now + ms)
    }

    var seconds by remember(engine) { mutableIntStateOf(0) }
    LaunchedEffect(engine, engine.state) {
        while (engine.state == MinesState.PLAYING) {
            seconds = engine.elapsedSeconds()
            delay(250)
        }
        seconds = engine.elapsedSeconds()
        if (engine.state == MinesState.WON) best = GameRecords.offer(minesBestKey(level), max(1, seconds), lowerIsBetter = true)
    }

    val restart = { engine = MinesEngine(level) }

    fun act(i: Int, secondary: Boolean) {
        GamePlaytime.touch()
        val t = System.currentTimeMillis()
        val flag = secondary || (flagMode && !engine.revealed[i])
        when {
            flag -> if (engine.toggleFlag(i)) { flagAt[i] = t; animate(260) }
            engine.revealed[i] -> if (engine.chord(i, t).isNotEmpty()) { waveAt = t; animate(900) }
            else -> {
                val opened = engine.reveal(i, t)
                if (opened.isNotEmpty()) {
                    waveAt = t
                    val longest = opened.maxOf { engine.revealDelay[it] }
                    animate(longest + 400L + if (engine.state == MinesState.LOST) 3_000L else 0L)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ExpressiveConnectedButtonGroup(
                options = MinesLevel.entries,
                selectedOption = level,
                onOptionSelected = { level = it },
                labelProvider = { Text(str(it.labelKey)) },
            )
            Spacer(Modifier.weight(1f))
            GameStat(str("mines_left"), "${engine.minesLeft}", Icons.Rounded.Flag, Modifier.width(120.dp), emphasized = true, compact = true)
            GameStat(str("mines_time"), formatClock(seconds), Icons.Rounded.Timer, Modifier.width(120.dp), compact = true)
            GameStat(str("game_best_label"), if (best > 0) formatClock(best) else "—", Icons.Rounded.EmojiEvents, Modifier.width(120.dp), compact = true)
            Tip(str("mines_flag_mode")) {
                FilledTonalIconToggleButton(
                    checked = flagMode,
                    onCheckedChange = { flagMode = it },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(52.dp),
                ) { Icon(Icons.Rounded.Flag, contentDescription = str("mines_flag_mode")) }
            }
            NewGameButton(restart)
        }
        Spacer(Modifier.height(14.dp))

        val measurer = rememberTextMeasurer()
        val numberColors = listOf(scheme.primary, Color(0xFF3FA34D), scheme.error, scheme.tertiary, Color(0xFFB0521E), Color(0xFF168C8C), scheme.onSurface, scheme.outline)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val e = engine
            val cell = min(constraints.maxWidth.toFloat() / e.cols, constraints.maxHeight.toFloat() / e.rows)
            val density = LocalDensity.current
            val boardW = with(density) { (cell * e.cols).toDp() }
            val boardH = with(density) { (cell * e.rows).toDp() }
            val digitStyle = TextStyle(fontSize = with(density) { (cell * 0.56f).toSp() }, fontWeight = FontWeight.Black)

            Box(Modifier.size(boardW, boardH)) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(e, cell) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pos = event.changes.firstOrNull()?.position ?: continue
                                    val cx = (pos.x / cell).toInt()
                                    val cy = (pos.y / cell).toInt()
                                    val idx = if (cx in 0 until e.cols && cy in 0 until e.rows) cy * e.cols + cx else -1
                                    when (event.type) {
                                        PointerEventType.Move, PointerEventType.Enter -> hover = idx
                                        PointerEventType.Exit -> hover = -1
                                        PointerEventType.Press -> if (idx >= 0) {
                                            act(idx, secondary = event.buttons.isSecondaryPressed)
                                            event.changes.forEach { it.consume() }
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        },
                ) {
                    @Suppress("UNUSED_EXPRESSION") e.rev
                    val t = now
                    val since = (t - waveAt).toInt()
                    val gap = cell * 0.06f
                    val r = CornerRadius(cell * 0.24f)
                    val finished = e.state == MinesState.LOST || e.state == MinesState.WON
                    // Neighbours of the hovered number light up, to read a chord before clicking it.
                    val hoverRing = if (hover >= 0 && e.revealed[hover] && e.adjacent[hover] > 0) e.neighbours(hover).toHashSet() else emptySet()
                    for (i in 0 until e.count) {
                        val x = i % e.cols
                        val y = i / e.cols
                        val o = Offset(x * cell + gap, y * cell + gap)
                        val s = Size(cell - gap * 2, cell - gap * 2)
                        val openProgress = if (e.revealed[i]) ((since - e.revealDelay[i]) / 160f).coerceIn(0f, 1f).let { if (waveAt == 0L) 1f else it } else 0f
                        val mineShown = e.mines[i] && e.state == MinesState.LOST && (i == e.exploded || since >= e.revealDelay[i])
                        if (e.revealed[i] && openProgress >= 1f || mineShown) {
                            val bg = when {
                                i == e.exploded -> scheme.errorContainer
                                mineShown -> lerp(scheme.surfaceContainerHigh, scheme.errorContainer, 0.45f)
                                else -> scheme.surfaceContainerLow
                            }
                            drawRoundRect(bg, o, s, r)
                            if (mineShown) {
                                if (e.flagged[i]) drawFlag(o, s, scheme.error, 1f) else drawMine(o + Offset(s.width / 2, s.height / 2), s.width * 0.3f, if (i == e.exploded) scheme.onErrorContainer else scheme.onSurface)
                            } else if (e.adjacent[i] > 0) {
                                val layout = measurer.measure("${e.adjacent[i]}", digitStyle.copy(color = numberColors[e.adjacent[i] - 1]))
                                drawText(layout, topLeft = Offset(o.x + (s.width - layout.size.width) / 2f, o.y + (s.height - layout.size.height) / 2f))
                            }
                        } else {
                            // A raised, closed tile; it sinks and fades as the ripple reaches it.
                            val hovered = i == hover && !finished
                            val base = when {
                                hovered -> lerp(scheme.primaryContainer, scheme.surfaceContainerHighest, 0.35f)
                                i in hoverRing && !e.flagged[i] -> lerp(scheme.surfaceContainerHighest, scheme.primaryContainer, 0.35f)
                                else -> scheme.surfaceContainerHighest
                            }
                            if (openProgress > 0f) drawRoundRect(scheme.surfaceContainerLow, o, s, r)
                            val shrink = openProgress * s.width * 0.5f
                            val tile = Size(s.width - shrink, s.height - shrink)
                            val to = o + Offset(shrink / 2, shrink / 2)
                            drawRoundRect(lerp(base, scheme.surfaceContainerLow, openProgress), to, tile, r, alpha = 1f - openProgress * 0.5f)
                            drawRoundRect(Color.White.copy(alpha = 0.07f * (1f - openProgress)), to + Offset(tile.width * 0.12f, tile.height * 0.08f), Size(tile.width * 0.76f, tile.height * 0.14f), CornerRadius(tile.height * 0.07f))
                            if (e.flagged[i]) {
                                val pop = flagAt[i]?.let { ((t - it) / 220f).coerceIn(0f, 1f) } ?: 1f
                                val wrong = e.state == MinesState.LOST && !e.mines[i]
                                drawFlag(o, s, scheme.error, 0.6f + 0.4f * bounce(pop))
                                if (wrong) {
                                    drawLine(scheme.onSurface, o + Offset(s.width * 0.2f, s.height * 0.2f), o + Offset(s.width * 0.8f, s.height * 0.8f), cell * 0.07f, StrokeCap.Round)
                                    drawLine(scheme.onSurface, o + Offset(s.width * 0.8f, s.height * 0.2f), o + Offset(s.width * 0.2f, s.height * 0.8f), cell * 0.07f, StrokeCap.Round)
                                }
                            }
                        }
                    }
                }
                val endShown = e.state == MinesState.WON || (e.state == MinesState.LOST && now >= fxUntil - 1_500)
                BoardScrim(visible = endShown) {
                    if (e.state == MinesState.WON) {
                        GameEndCard(
                            title = str(if (seconds <= best || best == 0) "arcade_new_record" else "mines_won"),
                            body = str("mines_won_time", formatClock(seconds)),
                            icon = Icons.Rounded.EmojiEvents,
                            onRestart = restart,
                        )
                    } else {
                        GameEndCard(title = str("mines_lost"), body = str("mines_lost_body"), icon = Icons.Rounded.SentimentDissatisfied, onRestart = restart)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            str("mines_hint"),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private fun bounce(t: Float): Float {
    if (t >= 1f) return 1f
    val c = 2.2f
    val x = t - 1f
    return 1f + (c + 1f) * x * x * x + c * x * x
}

private fun DrawScope.drawFlag(o: Offset, s: Size, color: Color, scale: Float) {
    val cx = o.x + s.width / 2
    val cy = o.y + s.height / 2
    val h = s.height * 0.56f * scale
    val poleX = cx - s.width * 0.12f * scale
    val top = cy - h / 2
    drawLine(color.copy(alpha = 0.9f), Offset(poleX, top), Offset(poleX, top + h), s.width * 0.07f, StrokeCap.Round)
    val path = Path().apply {
        moveTo(poleX, top)
        lineTo(poleX + s.width * 0.36f * scale, top + h * 0.24f)
        lineTo(poleX, top + h * 0.5f)
        close()
    }
    drawPath(path, color)
    drawLine(color.copy(alpha = 0.9f), Offset(poleX - s.width * 0.14f * scale, top + h), Offset(poleX + s.width * 0.14f * scale, top + h), s.width * 0.07f, StrokeCap.Round)
}

private fun DrawScope.drawMine(c: Offset, radius: Float, color: Color) {
    for (k in 0 until 8) {
        val a = k * Math.PI.toFloat() / 4f
        drawLine(color, c, c + Offset(kotlin.math.cos(a) * radius * 1.45f, kotlin.math.sin(a) * radius * 1.45f), radius * 0.28f, StrokeCap.Round)
    }
    drawCircle(color, radius, c)
    drawCircle(Color.White.copy(alpha = 0.55f), radius * 0.28f, c + Offset(-radius * 0.35f, -radius * 0.35f))
}

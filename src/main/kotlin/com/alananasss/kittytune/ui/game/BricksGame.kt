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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.Tip
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal const val BRICKS_BEST = "bricks_best"

internal enum class BricksPhase { AIM, SHOOTING, OVER }

internal class Ball(var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f) {
    var flying = false
    var done = false
}

internal class BrickBurst(val x: Float, val y: Float, val hpColor: Float, val at: Long)

/**
 * Balls against bricks: aim, a volley leaves one ball after another, every brick shows how many hits it still takes.
 * After each volley the wall comes down one row and a new row, a little tougher, appears on top; "+1" rings add a
 * ball. The wall reaching the bottom ends the run. Units are cells: the field is [COLS] wide.
 */
internal class BricksEngine(private val random: Random = Random.Default) {
    val hp = IntArray(COLS * ROWS)
    val pickup = BooleanArray(COLS * ROWS)
    val hitAt = LongArray(COLS * ROWS)
    var rev by mutableIntStateOf(0)
        private set
    var phase by mutableStateOf(BricksPhase.AIM)
        private set
    var turn by mutableIntStateOf(1)
        private set
    var ballCount by mutableIntStateOf(1)
        private set
    var launchX by mutableStateOf(COLS / 2f)
        private set
    var nextLaunchX: Float? = null
        private set
    val balls = mutableListOf<Ball>()
    val bursts = mutableListOf<BrickBurst>()
    private var toFire = 0
    private var fireTimer = 0f
    private var aimVx = 0f
    private var aimVy = 0f
    private var pendingBalls = 0
    var volleyTime = 0f
        private set
    var collected by mutableIntStateOf(0)
        private set

    init {
        addRow()
    }

    fun reset() {
        hp.fill(0)
        pickup.fill(false)
        balls.clear()
        bursts.clear()
        turn = 1
        ballCount = 1
        launchX = COLS / 2f
        pendingBalls = 0
        collected = 0
        phase = BricksPhase.AIM
        addRow()
        rev++
    }

    /** Fires the volley toward ([tx], [ty]); false if the aim points down or a volley is running. */
    fun shoot(tx: Float, ty: Float): Boolean {
        if (phase != BricksPhase.AIM) return false
        val dir = clampAim(tx - launchX, ty - LAUNCH_Y) ?: return false
        aimVx = dir.first * SPEED
        aimVy = dir.second * SPEED
        balls.clear()
        repeat(ballCount) { balls += Ball(launchX, LAUNCH_Y) }
        toFire = ballCount
        fireTimer = 0f
        nextLaunchX = null
        volleyTime = 0f
        phase = BricksPhase.SHOOTING
        rev++
        return true
    }

    /** Calls the balls in at once: they land where the volley started. */
    fun recall() {
        if (phase != BricksPhase.SHOOTING) return
        balls.forEach { it.done = true; it.flying = false }
        toFire = 0
        endVolley()
    }

    fun update(dtIn: Float, fast: Boolean, now: Long) {
        if (phase != BricksPhase.SHOOTING) return
        volleyTime += dtIn
        // Long volleys speed up by themselves, the fast-forward doubles that.
        val boost = (1f + max(0f, volleyTime - 6f) * 0.25f).coerceAtMost(3f) * if (fast) 2f else 1f
        var remaining = dtIn * boost
        while (remaining > 0f) {
            val dt = min(remaining, STEP)
            remaining -= dt
            if (toFire > 0) {
                fireTimer -= dt
                if (fireTimer <= 0f) {
                    val ball = balls[ballCount - toFire]
                    ball.vx = aimVx
                    ball.vy = aimVy
                    ball.flying = true
                    toFire--
                    fireTimer = FIRE_INTERVAL
                }
            }
            for (b in balls) if (b.flying) stepBall(b, dt, now)
        }
        if (toFire == 0 && balls.all { it.done }) endVolley()
        rev++
    }

    private fun stepBall(b: Ball, dt: Float, now: Long) {
        b.x += b.vx * dt
        b.y += b.vy * dt
        if (b.x < RADIUS) { b.x = RADIUS; b.vx = abs(b.vx) }
        if (b.x > COLS - RADIUS) { b.x = COLS - RADIUS; b.vx = -abs(b.vx) }
        if (b.y < RADIUS) { b.y = RADIUS; b.vy = abs(b.vy) }
        if (b.y >= LAUNCH_Y && b.vy > 0f) {
            b.y = LAUNCH_Y
            b.flying = false
            b.done = true
            if (nextLaunchX == null) nextLaunchX = b.x.coerceIn(RADIUS * 2, COLS - RADIUS * 2)
            return
        }
        val cx = b.x.toInt()
        val cy = b.y.toInt()
        for (gy in cy - 1..cy + 1) for (gx in cx - 1..cx + 1) {
            if (gx !in 0 until COLS || gy !in 0 until ROWS) continue
            val i = gy * COLS + gx
            if (pickup[i]) {
                val dx = b.x - (gx + 0.5f)
                val dy = b.y - (gy + 0.5f)
                if (dx * dx + dy * dy < (PICKUP_RADIUS + RADIUS) * (PICKUP_RADIUS + RADIUS)) {
                    pickup[i] = false
                    pendingBalls++
                    collected++
                    bursts += BrickBurst(gx + 0.5f, gy + 0.5f, -1f, now)
                }
                continue
            }
            if (hp[i] <= 0) continue
            val left = gx + GAP
            val right = gx + 1 - GAP
            val top = gy + GAP
            val bottom = gy + 1 - GAP
            val px = b.x.coerceIn(left, right)
            val py = b.y.coerceIn(top, bottom)
            var nx = b.x - px
            var ny = b.y - py
            val d2 = nx * nx + ny * ny
            if (d2 >= RADIUS * RADIUS) continue
            var d = sqrt(d2)
            if (d < 1e-4f) {
                // Centre inside the brick: push out along the shallowest side.
                val l = b.x - left; val r = right - b.x; val t = b.y - top; val bo = bottom - b.y
                val m = minOf(l, r, t, bo)
                nx = when (m) { l -> -1f; r -> 1f; else -> 0f }
                ny = when (m) { t -> -1f; bo -> 1f; else -> 0f }
                d = 0f
            } else {
                nx /= d
                ny /= d
            }
            b.x += nx * (RADIUS - d)
            b.y += ny * (RADIUS - d)
            val dot = b.vx * nx + b.vy * ny
            if (dot < 0f) {
                b.vx -= 2f * dot * nx
                b.vy -= 2f * dot * ny
            }
            keepAngle(b)
            hp[i]--
            hitAt[i] = now
            if (hp[i] <= 0) bursts += BrickBurst(gx + 0.5f, gy + 0.5f, colorIndex(i), now)
        }
    }

    /** A ball that skims almost flat would bounce side to side for ever: tilt it a little. */
    private fun keepAngle(b: Ball) {
        val minVy = SPEED * 0.14f
        if (abs(b.vy) < minVy) {
            b.vy = if (b.vy < 0f) -minVy else minVy
            val vx = sqrt(max(0f, SPEED * SPEED - b.vy * b.vy))
            b.vx = if (b.vx < 0f) -vx else vx
        }
    }

    private fun endVolley() {
        launchX = nextLaunchX ?: launchX
        balls.clear()
        ballCount += pendingBalls
        pendingBalls = 0
        // The wall steps down; a brick on the last row means the end.
        for (y in ROWS - 1 downTo 1) for (x in 0 until COLS) {
            hp[y * COLS + x] = hp[(y - 1) * COLS + x]
            pickup[y * COLS + x] = pickup[(y - 1) * COLS + x]
            hitAt[y * COLS + x] = 0L
        }
        for (x in 0 until COLS) { hp[x] = 0; pickup[x] = false }
        // Rings that reached the bottom are collected for free.
        for (x in 0 until COLS) {
            val i = (ROWS - 1) * COLS + x
            if (pickup[i]) { pickup[i] = false; ballCount++ }
        }
        turn++
        addRow()
        phase = if ((0 until COLS).any { hp[(ROWS - 1) * COLS + it] > 0 }) BricksPhase.OVER else BricksPhase.AIM
        rev++
    }

    private fun addRow() {
        val slots = (0 until COLS).shuffled(random)
        val bricks = 2 + random.nextInt(if (turn < 5) 3 else 4)
        slots.take(bricks).forEach { x ->
            hp[x] = if (random.nextFloat() < 0.22f && turn > 3) turn * 2 else turn
        }
        // One "+1" ring in a free slot every row.
        slots.drop(bricks).firstOrNull()?.let { pickup[it] = true }
    }

    /** 0..1 of a brick's toughness against the current turn, for its colour. */
    fun colorIndex(i: Int): Float = (hp[i].toFloat() / (turn * 2f)).coerceIn(0f, 1f)

    companion object {
        const val COLS = 7
        const val ROWS = 10
        const val RADIUS = 0.13f
        const val GAP = 0.05f
        const val PICKUP_RADIUS = 0.2f
        const val SPEED = 15f
        const val STEP = 1f / 240f
        const val FIRE_INTERVAL = 0.065f
        val LAUNCH_Y = ROWS + 0.75f
        val HEIGHT = ROWS + 1f

        /** The aim as a unit vector, kept a little above flat; null if it points down. */
        fun clampAim(dx: Float, dy: Float): Pair<Float, Float>? {
            if (dy > -0.05f) return null
            var a = atan2(dy, dx) // negative, between -PI and 0
            val minA = -PI.toFloat() + 0.14f
            val maxA = -0.14f
            a = a.coerceIn(minA, maxA)
            return cos(a) to sin(a)
        }
    }
}

@Composable
internal fun BricksGame() {
    val engine = remember { BricksEngine() }
    var best by remember { mutableIntStateOf(GameRecords.best(BRICKS_BEST)) }
    var aim by remember { mutableStateOf<Offset?>(null) }
    var fast by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fxUntil by remember { mutableLongStateOf(0L) }
    val scheme = MaterialTheme.colorScheme

    TrackGamePlaytime(GameId.BRICKS, active = engine.phase != BricksPhase.OVER)

    LaunchedEffect(engine.phase, fxUntil) {
        runGameFrames { dt ->
            now = System.currentTimeMillis()
            engine.update(dt, fast, now)
            engine.phase == BricksPhase.SHOOTING || now < fxUntil
        }
    }
    LaunchedEffect(engine.phase) {
        if (engine.phase != BricksPhase.SHOOTING) fxUntil = System.currentTimeMillis() + 600
        if (engine.phase == BricksPhase.OVER) best = GameRecords.offer(BRICKS_BEST, engine.turn - 1)
    }
    val restart = {
        best = GameRecords.offer(BRICKS_BEST, engine.turn - 1)
        engine.reset()
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GameStat(str("bricks_level"), "${engine.turn}", Icons.Rounded.Layers, Modifier.weight(1f), emphasized = true, compact = true)
            GameStat(str("bricks_balls"), "${engine.ballCount}", Icons.Rounded.SportsBaseball, Modifier.weight(1f), compact = true)
            GameStat(str("game_best_label"), "${max(best, engine.turn - 1)}", Icons.Rounded.EmojiEvents, Modifier.weight(1f), compact = true)
            Tip(str("bricks_fast")) {
                FilledTonalIconToggleButton(checked = fast, onCheckedChange = { fast = it }, shapes = IconButtonDefaults.toggleableShapes(), modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.FastForward, contentDescription = str("bricks_fast"))
                }
            }
            Tip(str("bricks_recall")) {
                FilledTonalIconButton(onClick = { engine.recall() }, enabled = engine.phase == BricksPhase.SHOOTING, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Rounded.KeyboardDoubleArrowDown, contentDescription = str("bricks_recall"))
                }
            }
            NewGameButton(restart)
        }
        Spacer(Modifier.height(12.dp))

        val measurer = rememberTextMeasurer()
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val unit = min(constraints.maxWidth / BricksEngine.COLS.toFloat(), constraints.maxHeight / BricksEngine.HEIGHT)
            val density = LocalDensity.current
            val w = with(density) { (unit * BricksEngine.COLS).toDp() }
            val h = with(density) { (unit * BricksEngine.HEIGHT).toDp() }
            val numberStyle = TextStyle(fontSize = with(density) { (unit * 0.34f).toSp() }, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            val low = scheme.primary
            val mid = scheme.tertiary
            val high = scheme.error

            Box(Modifier.size(w, h).clip(RoundedCornerShape(14.dp))) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(engine, unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pos = event.changes.firstOrNull()?.position ?: continue
                                    val world = Offset(pos.x / unit, pos.y / unit)
                                    when (event.type) {
                                        PointerEventType.Move, PointerEventType.Enter, PointerEventType.Press -> aim = world
                                        PointerEventType.Exit -> aim = null
                                        PointerEventType.Release -> {
                                            GamePlaytime.touch()
                                            if (engine.shoot(world.x, world.y)) aim = null
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        },
                ) {
                    @Suppress("UNUSED_EXPRESSION") engine.rev
                    val t = now
                    drawRect(Brush.verticalGradient(listOf(scheme.surfaceContainerLowest, scheme.surfaceContainer)))
                    // The danger line: where the wall must not arrive.
                    val dangerY = (BricksEngine.ROWS - 1) * unit
                    drawLine(scheme.error.copy(alpha = 0.35f), Offset(0f, dangerY), Offset(size.width, dangerY), unit * 0.03f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.15f, unit * 0.12f)))
                    drawLine(scheme.outlineVariant, Offset(0f, BricksEngine.LAUNCH_Y * unit + BricksEngine.RADIUS * unit), Offset(size.width, BricksEngine.LAUNCH_Y * unit + BricksEngine.RADIUS * unit), unit * 0.02f)

                    for (i in 0 until BricksEngine.COLS * BricksEngine.ROWS) {
                        val gx = i % BricksEngine.COLS
                        val gy = i / BricksEngine.COLS
                        if (engine.pickup[i]) {
                            val c = Offset((gx + 0.5f) * unit, (gy + 0.5f) * unit)
                            val pulse = 1f + 0.08f * sin(t / 160f)
                            drawCircle(scheme.tertiary.copy(alpha = 0.25f), unit * BricksEngine.PICKUP_RADIUS * 1.5f * pulse, c)
                            drawCircle(scheme.tertiary, unit * BricksEngine.PICKUP_RADIUS * pulse, c, style = Stroke(unit * 0.05f))
                            drawLine(scheme.tertiary, c - Offset(unit * 0.09f, 0f), c + Offset(unit * 0.09f, 0f), unit * 0.05f, StrokeCap.Round)
                            drawLine(scheme.tertiary, c - Offset(0f, unit * 0.09f), c + Offset(0f, unit * 0.09f), unit * 0.05f, StrokeCap.Round)
                        }
                        val v = engine.hp[i]
                        if (v <= 0) continue
                        val k = engine.colorIndex(i)
                        val base = if (k < 0.5f) lerp(low, mid, k * 2f) else lerp(mid, high, (k - 0.5f) * 2f)
                        val hit = ((t - engine.hitAt[i]) / 120f).coerceIn(0f, 1f)
                        val punch = if (engine.hitAt[i] > 0L && hit < 1f) (1f - hit) else 0f
                        val inset = unit * (BricksEngine.GAP + 0.02f * punch)
                        val o = Offset(gx * unit + inset, gy * unit + inset)
                        val s = Size(unit - inset * 2, unit - inset * 2)
                        drawRoundRect(
                            Brush.verticalGradient(listOf(lerp(base, Color.White, 0.18f + 0.4f * punch), lerp(base, Color.Black, 0.18f)), o.y, o.y + s.height),
                            o, s, CornerRadius(unit * 0.2f),
                        )
                        val layout = measurer.measure("$v", numberStyle.copy(color = Color.White))
                        drawText(layout, topLeft = Offset(o.x + (s.width - layout.size.width) / 2f, o.y + (s.height - layout.size.height) / 2f))
                    }

                    // Bursts of broken bricks and collected rings.
                    engine.bursts.removeAll { t - it.at > 500 }
                    engine.bursts.forEach { b ->
                        val p = ((t - b.at) / 500f).coerceIn(0f, 1f)
                        val c = Offset(b.x * unit, b.y * unit)
                        val color = if (b.hpColor < 0f) scheme.tertiary else lerp(low, high, b.hpColor)
                        for (k in 0 until 8) {
                            val a = k * PI.toFloat() / 4f + b.x
                            val r = unit * (0.2f + 0.6f * p)
                            drawRoundRect(color.copy(alpha = 1f - p), c + Offset(cos(a) * r, sin(a) * r) - Offset(unit * 0.06f, unit * 0.06f), Size(unit * 0.12f * (1f - p * 0.5f), unit * 0.12f * (1f - p * 0.5f)), CornerRadius(unit * 0.03f))
                        }
                        drawCircle(color.copy(alpha = 0.3f * (1f - p)), unit * (0.3f + 0.4f * p), c, style = Stroke(unit * 0.04f))
                    }

                    // Aim guide: dots to the first bounce and a little beyond.
                    val a = aim
                    if (engine.phase == BricksPhase.AIM && a != null) {
                        BricksEngine.clampAim(a.x - engine.launchX, a.y - BricksEngine.LAUNCH_Y)?.let { (dx, dy) ->
                            var x = engine.launchX
                            var y = BricksEngine.LAUNCH_Y
                            var vx = dx
                            var vy = dy
                            var bounced = 0
                            var dist = 0f
                            var nextDot = 0.3f
                            while (dist < 14f && bounced < 2) {
                                x += vx * 0.03f; y += vy * 0.03f; dist += 0.03f
                                if (x < BricksEngine.RADIUS || x > BricksEngine.COLS - BricksEngine.RADIUS) { vx = -vx; bounced++ }
                                if (y < BricksEngine.RADIUS) { vy = -vy; bounced++ }
                                val gx = x.toInt(); val gy = y.toInt()
                                if (gx in 0 until BricksEngine.COLS && gy in 0 until BricksEngine.ROWS && engine.hp[gy * BricksEngine.COLS + gx] > 0) break
                                if (dist >= nextDot) {
                                    val fade = (1f - dist / 14f) * if (bounced > 0) 0.5f else 1f
                                    drawCircle(scheme.onSurface.copy(alpha = 0.55f * fade), unit * 0.05f, Offset(x * unit, y * unit))
                                    nextDot += 0.32f
                                }
                            }
                        }
                    }

                    // Balls, with a short trail.
                    val ballColor = scheme.onSurface
                    engine.balls.forEach { b ->
                        if (!b.flying) return@forEach
                        val sp = hypot(b.vx, b.vy).coerceAtLeast(0.01f)
                        for (k in 1..3) {
                            val back = k * 0.06f
                            drawCircle(scheme.primary.copy(alpha = 0.18f / k), unit * BricksEngine.RADIUS * (1f - k * 0.15f), Offset((b.x - b.vx / sp * back) * unit, (b.y - b.vy / sp * back) * unit))
                        }
                        drawCircle(ballColor, unit * BricksEngine.RADIUS, Offset(b.x * unit, b.y * unit))
                    }
                    // The launcher: the ball waiting, with how many are ready.
                    if (engine.phase != BricksPhase.OVER) {
                        val waiting = engine.phase == BricksPhase.AIM || engine.balls.any { !it.flying && !it.done }
                        val lx = if (engine.phase == BricksPhase.SHOOTING) engine.launchX else engine.launchX
                        if (waiting) drawCircle(scheme.primary, unit * BricksEngine.RADIUS * 1.25f, Offset(lx * unit, BricksEngine.LAUNCH_Y * unit))
                        engine.nextLaunchX?.let { drawCircle(ballColor.copy(alpha = 0.6f), unit * BricksEngine.RADIUS, Offset(it * unit, BricksEngine.LAUNCH_Y * unit)) }
                        if (engine.phase == BricksPhase.AIM) {
                            val label = measurer.measure("×${engine.ballCount}", TextStyle(fontSize = with(density) { (unit * 0.26f).toSp() }, fontWeight = FontWeight.Black, color = scheme.primary))
                            val lxPx = (lx * unit + unit * 0.3f).coerceAtMost(size.width - label.size.width - 4f)
                            drawText(label, topLeft = Offset(lxPx, BricksEngine.LAUNCH_Y * unit - label.size.height / 2f))
                        }
                    }
                }
                BoardScrim(visible = engine.phase == BricksPhase.OVER) {
                    val reached = engine.turn - 1
                    GameEndCard(
                        title = str(if (reached >= best && reached > 0) "arcade_new_record" else "bricks_over"),
                        body = str("bricks_over_body", reached) + "  ·  " + str("game_best", max(best, reached)),
                        icon = Icons.Rounded.SportsBaseball,
                        onRestart = restart,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            str("bricks_hint"),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

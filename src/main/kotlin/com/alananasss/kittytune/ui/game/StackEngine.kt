package com.alananasss.kittytune.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.pow
import kotlin.random.Random

/** The seven falling pieces, in the order of their colours. */
internal enum class Tetromino(val box: Int, val cells: List<Pair<Int, Int>>) {
    I(4, listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1)),
    O(2, listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)),
    T(3, listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1)),
    S(3, listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1)),
    Z(3, listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1)),
    J(3, listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1)),
    L(3, listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1));

    val color: Int get() = ordinal + 1

    /** The cells in rotation state [rot] (0 spawn, 1 right, 2 reverse, 3 left), within the piece's box. */
    fun cellsAt(rot: Int): List<Pair<Int, Int>> {
        if (this == O) return cells
        var c = cells
        repeat(((rot % 4) + 4) % 4) { c = c.map { (x, y) -> (box - 1 - y) to x } }
        return c
    }
}

internal data class ActivePiece(val type: Tetromino, val rot: Int, val x: Int, val y: Int) {
    fun cells(): List<Pair<Int, Int>> = type.cellsAt(rot).map { (cx, cy) -> (x + cx) to (y + cy) }
}

/** What a lock did, for the score pop-ups and the flash over cleared rows. */
internal data class StackClear(val id: Long, val rows: List<Int>, val points: Int, val combo: Int, val backToBack: Boolean, val perfect: Boolean, val tSpin: Boolean, val at: Long = System.currentTimeMillis())

/**
 * A falling-blocks game played the modern way: 10×20 well (two hidden rows above), the seven pieces dealt from
 * shuffled bags of seven, the standard rotation system with its wall kicks, hold, ghost, a queue of five, lock delay
 * with a limited number of resets, gravity that quickens every ten lines, combos, back-to-back and T-spins.
 */
internal class StackEngine(private val random: Random = Random.Default) {
    val board = IntArray(W * H)
    var rev by mutableIntStateOf(0)
        private set
    var current by mutableStateOf<ActivePiece?>(null)
        private set
    var hold by mutableStateOf<Tetromino?>(null)
        private set
    var holdUsed by mutableStateOf(false)
        private set
    var queue by mutableStateOf(listOf<Tetromino>())
        private set
    var score by mutableIntStateOf(0)
        private set
    var lines by mutableIntStateOf(0)
        private set
    var level by mutableIntStateOf(1)
        private set
    var over by mutableStateOf(false)
        private set
    var lastClear by mutableStateOf<StackClear?>(null)
        private set
    /** Rows and time of the last hard drop, for its streak. */
    var lastDrop by mutableStateOf<Triple<ActivePiece, Int, Long>?>(null)
        private set
    var locks by mutableIntStateOf(0)
        private set

    private val bag = ArrayDeque<Tetromino>()
    private var gravityTimer = 0f
    private var lockTimer = 0f
    private var lockResets = 0
    private var combo = -1
    private var backToBack = false
    private var lastMoveWasRotation = false
    private var nextId = 1L

    init {
        reset()
    }

    fun reset() {
        board.fill(0)
        bag.clear()
        hold = null
        holdUsed = false
        score = 0
        lines = 0
        level = 1
        over = false
        combo = -1
        backToBack = false
        lastClear = null
        lastDrop = null
        queue = List(QUEUE) { draw() }
        spawn(next())
        rev++
    }

    // ─── Input ──────────────────────────────────────────────────────────────────────────

    fun move(dx: Int): Boolean {
        val p = current ?: return false
        if (over) return false
        val moved = p.copy(x = p.x + dx)
        if (!fits(moved)) return false
        current = moved
        lastMoveWasRotation = false
        touchedGround()
        rev++
        return true
    }

    /** dir +1 clockwise, -1 counter-clockwise; tries the wall kicks in order. */
    fun rotate(dir: Int): Boolean {
        val p = current ?: return false
        if (over || p.type == Tetromino.O) return false
        val to = ((p.rot + dir) % 4 + 4) % 4
        val kicks = (if (p.type == Tetromino.I) KICKS_I else KICKS_JLSTZ)[p.rot * 4 + to] ?: return false
        for ((kx, ky) in kicks) {
            val candidate = p.copy(rot = to, x = p.x + kx, y = p.y + ky)
            if (fits(candidate)) {
                current = candidate
                lastMoveWasRotation = true
                touchedGround()
                rev++
                return true
            }
        }
        return false
    }

    /** One row down, scoring a point; false when it rests on something. */
    fun softDrop(): Boolean {
        if (stepDown()) {
            score += 1
            gravityTimer = 0f
            return true
        }
        return false
    }

    fun hardDrop(now: Long = System.currentTimeMillis()) {
        val p = current ?: return
        if (over) return
        val ghost = ghost() ?: return
        val rows = ghost.y - p.y
        score += rows * 2
        if (rows > 0) lastMoveWasRotation = false
        current = ghost
        lastDrop = Triple(p, rows, now)
        lock()
    }

    fun holdPiece() {
        val p = current ?: return
        if (over || holdUsed) return
        val incoming = hold ?: next()
        hold = p.type
        spawn(incoming)
        holdUsed = true
        rev++
    }

    /** Where the piece would land. */
    fun ghost(): ActivePiece? {
        var p = current ?: return null
        while (fits(p.copy(y = p.y + 1))) p = p.copy(y = p.y + 1)
        return p
    }

    // ─── Time ───────────────────────────────────────────────────────────────────────────

    /** Gravity and lock delay; [softDropping] makes the piece fall 20 times faster. */
    fun tick(dt: Float, softDropping: Boolean) {
        if (over || current == null) return
        val interval = if (softDropping) (gravityInterval(level) / 20f).coerceAtMost(0.05f) else gravityInterval(level)
        gravityTimer += dt
        while (gravityTimer >= interval) {
            gravityTimer -= interval
            if (stepDown()) {
                if (softDropping) score += 1
            } else {
                gravityTimer = 0f
                break
            }
        }
        if (!canFall()) {
            lockTimer += dt
            if (lockTimer >= LOCK_DELAY) lock()
        } else {
            lockTimer = 0f
        }
    }

    // ─── Internals ──────────────────────────────────────────────────────────────────────

    private fun stepDown(): Boolean {
        val p = current ?: return false
        val down = p.copy(y = p.y + 1)
        if (!fits(down)) return false
        current = down
        lastMoveWasRotation = false
        lockTimer = 0f
        rev++
        return true
    }

    private fun canFall(): Boolean = current?.let { fits(it.copy(y = it.y + 1)) } ?: false

    /** A move or turn on the ground buys more time, up to [MAX_RESETS] times. */
    private fun touchedGround() {
        if (!canFall() && lockResets < MAX_RESETS) {
            lockTimer = 0f
            lockResets++
        }
    }

    private fun lock() {
        val p = current ?: return
        val tSpin = p.type == Tetromino.T && lastMoveWasRotation && cornersTaken(p) >= 3
        p.cells().forEach { (x, y) -> board[y * W + x] = p.type.color }
        current = null
        locks++
        // Locked entirely in the hidden rows: the stack topped out.
        if (p.cells().all { (_, y) -> y < HIDDEN }) {
            over = true
            rev++
            return
        }
        val full = (0 until H).filter { y -> (0 until W).all { x -> board[y * W + x] != 0 } }
        if (full.isNotEmpty()) {
            val keep = (0 until H).filter { it !in full }
            val copy = IntArray(W * H)
            var dst = H - 1
            for (y in keep.reversed()) {
                for (x in 0 until W) copy[dst * W + x] = board[y * W + x]
                dst--
            }
            copy.copyInto(board)
        }
        val n = full.size
        var points = 0
        if (n > 0 || tSpin) {
            val base = if (tSpin) intArrayOf(400, 800, 1200, 1600)[n.coerceAtMost(3)] else intArrayOf(0, 100, 300, 500, 800)[n]
            val difficult = n == 4 || (tSpin && n > 0)
            val b2b = difficult && backToBack
            points = base * level
            if (b2b) points = points * 3 / 2
            if (n > 0) {
                combo++
                if (combo > 0) points += 50 * combo * level
                backToBack = difficult
            }
            val perfect = n > 0 && board.all { it == 0 }
            if (perfect) points += 2000 * level
            lastClear = StackClear(nextId++, full, points, combo.coerceAtLeast(0), b2b, perfect, tSpin)
        } else {
            combo = -1
        }
        score += points
        lines += n
        level = 1 + lines / 10
        holdUsed = false
        spawn(next())
        rev++
    }

    /** T-spin test: three of the four corners around the T's centre are walls or blocks. */
    private fun cornersTaken(p: ActivePiece): Int {
        val cx = p.x + 1
        val cy = p.y + 1
        return listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1).count { (dx, dy) -> occupied(cx + dx, cy + dy) }
    }

    private fun occupied(x: Int, y: Int): Boolean = x !in 0 until W || y >= H || (y >= 0 && board[y * W + x] != 0)

    private fun spawn(type: Tetromino) {
        val piece = ActivePiece(type, 0, if (type == Tetromino.O) 4 else 3, HIDDEN - 1)
        gravityTimer = 0f
        lockTimer = 0f
        lockResets = 0
        lastMoveWasRotation = false
        if (!fits(piece)) {
            current = null
            over = true
            return
        }
        current = piece
        // Show it straight away in the visible well when there is room.
        if (fits(piece.copy(y = piece.y + 1))) current = piece.copy(y = piece.y + 1)
    }

    private fun next(): Tetromino {
        val head = queue.first()
        queue = queue.drop(1) + draw()
        return head
    }

    private fun draw(): Tetromino {
        if (bag.isEmpty()) bag.addAll(Tetromino.entries.shuffled(random))
        return bag.removeFirst()
    }

    fun fits(p: ActivePiece): Boolean = p.cells().all { (x, y) -> x in 0 until W && y in 0 until H && board[y * W + x] == 0 }

    internal fun setCurrentForTest(piece: ActivePiece) {
        current = piece
        rev++
    }

    companion object {
        const val W = 10
        const val H = 22
        const val HIDDEN = 2
        const val QUEUE = 5
        const val LOCK_DELAY = 0.5f
        const val MAX_RESETS = 15

        /** Seconds per row at [level], the usual curve: about 1 s at level 1, a blur by level 15. */
        fun gravityInterval(level: Int): Float = (0.8 - (level - 1) * 0.007).pow(level - 1).toFloat().coerceAtLeast(0.008f)

        // Wall kicks of the standard rotation system, keyed by from*4+to, with y pointing down.
        private fun k(vararg p: Int) = p.toList().chunked(2).map { (x, y) -> x to -y }

        val KICKS_JLSTZ: Map<Int, List<Pair<Int, Int>>> = mapOf(
            0 * 4 + 1 to k(0, 0, -1, 0, -1, 1, 0, -2, -1, -2),
            1 * 4 + 0 to k(0, 0, 1, 0, 1, -1, 0, 2, 1, 2),
            1 * 4 + 2 to k(0, 0, 1, 0, 1, -1, 0, 2, 1, 2),
            2 * 4 + 1 to k(0, 0, -1, 0, -1, 1, 0, -2, -1, -2),
            2 * 4 + 3 to k(0, 0, 1, 0, 1, 1, 0, -2, 1, -2),
            3 * 4 + 2 to k(0, 0, -1, 0, -1, -1, 0, 2, -1, 2),
            3 * 4 + 0 to k(0, 0, -1, 0, -1, -1, 0, 2, -1, 2),
            0 * 4 + 3 to k(0, 0, 1, 0, 1, 1, 0, -2, 1, -2),
        )
        val KICKS_I: Map<Int, List<Pair<Int, Int>>> = mapOf(
            0 * 4 + 1 to k(0, 0, -2, 0, 1, 0, -2, -1, 1, 2),
            1 * 4 + 0 to k(0, 0, 2, 0, -1, 0, 2, 1, -1, -2),
            1 * 4 + 2 to k(0, 0, -1, 0, 2, 0, -1, 2, 2, -1),
            2 * 4 + 1 to k(0, 0, 1, 0, -2, 0, 1, -2, -2, 1),
            2 * 4 + 3 to k(0, 0, 2, 0, -1, 0, 2, 1, -1, -2),
            3 * 4 + 2 to k(0, 0, -2, 0, 1, 0, -2, -1, 1, 2),
            3 * 4 + 0 to k(0, 0, 1, 0, -2, 0, 1, -2, -2, 1),
            0 * 4 + 3 to k(0, 0, -1, 0, 2, 0, -1, 2, 2, -1),
        )
    }
}

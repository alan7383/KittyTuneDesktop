package com.alananasss.kittytune.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** A piece of the block puzzle: its cells (column, row) from its top-left corner, and its colour (1-based). */
internal data class BlockPiece(val cells: List<Pair<Int, Int>>, val color: Int, val id: Long = 0L) {
    val width: Int = cells.maxOf { it.first } + 1
    val height: Int = cells.maxOf { it.second } + 1
    val size: Int get() = cells.size
}

/** What one placement did, for the score, the effects and the tests. */
internal data class BlocksMove(
    val id: Long,
    val placed: List<Int>,
    val clearedRows: List<Int>,
    val clearedCols: List<Int>,
    /** Board index and colour of every cell the clear removed. */
    val clearedCells: List<Pair<Int, Int>>,
    val points: Int,
    val combo: Int,
    val allClear: Boolean,
) {
    val lines: Int get() = clearedRows.size + clearedCols.size
}

/**
 * The block puzzle: an 8×8 board, three pieces offered at a time, full rows and columns cleared.
 *
 * Piece offers follow the rhythm of the big games of the genre: a pool of trays is drawn, the unplayable ones are
 * thrown away, and the one kept is, with a probability that falls as the score climbs and rises again after dry
 * rounds, the most "helpful" (it fills holes and opens multi-line clears); now and then it is the hardest one that
 * still has a way through instead. The combo survives up to three placements without a clear.
 */
internal class BlocksEngine(private val random: Random = Random.Default, openingPattern: Boolean = true) {
    val board = IntArray(SIZE * SIZE)
    var rev by mutableIntStateOf(0)
        private set
    var tray by mutableStateOf(listOf<BlockPiece?>())
        private set
    var trayRev by mutableIntStateOf(0)
        private set
    var score by mutableIntStateOf(0)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var over by mutableStateOf(false)
        private set
    var lastMove by mutableStateOf<BlocksMove?>(null)
        private set

    private var movesSinceClear = 0
    private var roundsSinceClear = 0
    private var nextId = 1L

    init {
        if (openingPattern && random.nextFloat() < 0.6f) paintOpening()
        refill()
    }

    fun reset(openingPattern: Boolean = true) {
        board.fill(0)
        score = 0
        combo = 0
        movesSinceClear = 0
        roundsSinceClear = 0
        over = false
        lastMove = null
        if (openingPattern && random.nextFloat() < 0.6f) paintOpening()
        rev++
        refill()
    }

    fun canPlace(piece: BlockPiece, x: Int, y: Int): Boolean = fits(board, piece, x, y)

    fun fitsAnywhere(piece: BlockPiece): Boolean = fitsAnywhere(board, piece)

    /** The rows and columns a placement would complete, for the highlight while dragging. */
    fun wouldClear(piece: BlockPiece, x: Int, y: Int): Pair<List<Int>, List<Int>> {
        if (!canPlace(piece, x, y)) return emptyList<Int>() to emptyList()
        val copy = board.copyOf()
        piece.cells.forEach { (cx, cy) -> copy[(y + cy) * SIZE + x + cx] = piece.color }
        return fullRows(copy) to fullCols(copy)
    }

    /** Places tray slot [slot] with its top-left at ([x], [y]); null if it does not fit. */
    fun place(slot: Int, x: Int, y: Int): BlocksMove? {
        if (over) return null
        val piece = tray.getOrNull(slot) ?: return null
        if (!canPlace(piece, x, y)) return null
        val placed = piece.cells.map { (cx, cy) -> (y + cy) * SIZE + x + cx }
        placed.forEach { board[it] = piece.color }

        val rows = fullRows(board)
        val cols = fullCols(board)
        val clearedIdx = LinkedHashSet<Int>()
        rows.forEach { r -> for (c in 0 until SIZE) clearedIdx += r * SIZE + c }
        cols.forEach { c -> for (r in 0 until SIZE) clearedIdx += r * SIZE + c }
        val clearedCells = clearedIdx.map { it to board[it] }
        clearedIdx.forEach { board[it] = 0 }

        val lines = rows.size + cols.size
        var points = piece.size
        if (lines > 0) {
            combo++
            movesSinceClear = 0
            roundsSinceClear = 0
            points += clearPoints(lines, combo)
        } else {
            movesSinceClear++
            if (movesSinceClear >= COMBO_GRACE) combo = 0
        }
        val allClear = lines > 0 && board.all { it == 0 }
        if (allClear) points += ALL_CLEAR_BONUS
        score += points

        tray = tray.toMutableList().also { it[slot] = null }
        val move = BlocksMove(nextId++, placed, rows, cols, clearedCells, points, if (lines > 0) combo else 0, allClear)
        lastMove = move
        rev++
        if (tray.all { it == null }) {
            if (lines == 0) roundsSinceClear++
            refill()
        }
        if (tray.filterNotNull().none { fitsAnywhere(it) }) over = true
        return move
    }

    // ─── Offers ─────────────────────────────────────────────────────────────────────────

    private fun refill() {
        tray = pickTray()
        trayRev++
    }

    private fun pickTray(): List<BlockPiece> {
        val candidates = List(CANDIDATES) { randomTray() }
        val graded = candidates.map { it to grade(board, it) }
        val playable = graded.filter { it.second >= 0 }
        if (playable.isEmpty()) {
            // Nothing gets through: at least offer something that fits once, the way out is then up to the player.
            return candidates.firstOrNull { tray -> tray.any { fitsAnywhere(board, it) } } ?: candidates.first()
        }
        val roll = random.nextInt(100)
        return when {
            roll < helpChance() -> playable.maxBy { it.second }.first
            roll > 100 - HARD_CHANCE && score > 600 -> playable.minBy { it.second }.first
            else -> playable[random.nextInt(playable.size)].first
        }
    }

    /** % chance that the tray is the most helpful one: falls with the score, climbs back after dry rounds. */
    internal fun helpChance(): Int {
        val band = HELP_TABLE.indexOfLast { score >= it.first }.coerceAtLeast(0)
        val steps = HELP_TABLE[band].second
        return steps[roundsSinceClear.coerceAtMost(steps.lastIndex)]
    }

    private fun randomTray(): List<BlockPiece> = List(3) { randomPiece() }

    internal fun randomPiece(): BlockPiece {
        // Early on, the big awkward pieces are rarer: a fresh board should feel generous.
        val easy = score < 400
        val total = SHAPES.sumOf { if (easy && it.cells.size >= 5) it.weight / 2 else it.weight }
        var r = random.nextInt(total)
        for (shape in SHAPES) {
            val w = if (easy && shape.cells.size >= 5) shape.weight / 2 else shape.weight
            if (r < w) return BlockPiece(shape.cells, 1 + random.nextInt(COLORS), nextId++)
            r -= w
        }
        return BlockPiece(SHAPES.first().cells, 1, nextId++)
    }

    /**
     * How good a tray is on [grid]: -1 when no order of the three pieces can all be placed, else the most lines a
     * greedy play of it clears, ×100, plus how snugly its pieces sit (holes filled).
     */
    internal fun grade(grid: IntArray, pieces: List<BlockPiece>): Int {
        var best = -1
        for (order in PERMUTATIONS) {
            val sim = grid.copyOf()
            var total = 0
            var ok = true
            for (i in order) {
                val p = pieces[i]
                var bestHere = -1
                var bx = -1
                var by = -1
                for (y in 0..SIZE - p.height) for (x in 0..SIZE - p.width) {
                    if (!fits(sim, p, x, y)) continue
                    val value = placementValue(sim, p, x, y)
                    if (value > bestHere) { bestHere = value; bx = x; by = y }
                }
                if (bestHere < 0) { ok = false; break }
                total += bestHere
                p.cells.forEach { (cx, cy) -> sim[(by + cy) * SIZE + bx + cx] = p.color }
                val rows = fullRows(sim)
                val cols = fullCols(sim)
                rows.forEach { r -> for (c in 0 until SIZE) sim[r * SIZE + c] = 0 }
                cols.forEach { c -> for (r in 0 until SIZE) sim[r * SIZE + c] = 0 }
            }
            if (ok && total > best) best = total
        }
        return best
    }

    private fun placementValue(grid: IntArray, p: BlockPiece, x: Int, y: Int): Int {
        val copy = grid.copyOf()
        p.cells.forEach { (cx, cy) -> copy[(y + cy) * SIZE + x + cx] = p.color }
        val lines = fullRows(copy).size + fullCols(copy).size
        // Snug: neighbours that are walls or blocks, so holes get filled rather than made.
        var snug = 0
        for ((cx, cy) in p.cells) {
            val gx = x + cx
            val gy = y + cy
            for ((dx, dy) in NEIGHBOURS) {
                val nx = gx + dx
                val ny = gy + dy
                if (nx !in 0 until SIZE || ny !in 0 until SIZE || copy[ny * SIZE + nx] != 0) snug++
            }
        }
        return lines * 100 + snug
    }

    /** A symmetric opening picture, never with a full line, so the first moves already have something to clear. */
    private fun paintOpening() {
        val colorA = 1 + random.nextInt(COLORS)
        val colorB = 1 + random.nextInt(COLORS)
        val target = 10 + random.nextInt(10)
        var placed = 0
        var guard = 0
        while (placed < target && guard++ < 200) {
            val x = random.nextInt(SIZE / 2)
            val y = 2 + random.nextInt(SIZE - 2)
            val i = y * SIZE + x
            val mirror = y * SIZE + (SIZE - 1 - x)
            if (board[i] != 0) continue
            val color = if (y % 2 == 0) colorA else colorB
            board[i] = color
            board[mirror] = color
            if (fullRows(board).isNotEmpty() || fullCols(board).isNotEmpty()) {
                board[i] = 0
                board[mirror] = 0
            } else {
                placed += 2
            }
        }
    }

    internal companion object {
        const val SIZE = 8
        const val COLORS = 7
        const val COMBO_GRACE = 3
        const val ALL_CLEAR_BONUS = 300
        const val CANDIDATES = 24
        const val HARD_CHANCE = 8

        /** Score band → help chance by dry rounds (0, 1, 2, …). */
        val HELP_TABLE: List<Pair<Int, IntArray>> = listOf(
            0 to intArrayOf(60, 70, 80, 90, 100),
            1_500 to intArrayOf(45, 55, 70, 80, 90, 100),
            3_000 to intArrayOf(35, 45, 55, 65, 80, 100),
            6_000 to intArrayOf(25, 35, 45, 55, 70, 90),
            10_000 to intArrayOf(15, 25, 35, 45, 60, 80),
        )

        /** 10 a cleared line, more for several at once, all of it multiplied by the combo. */
        fun clearPoints(lines: Int, combo: Int): Int {
            val base = when (lines) {
                1 -> 10
                2 -> 30
                3 -> 60
                4 -> 100
                else -> 100 + (lines - 4) * 50
            }
            return base * combo.coerceAtLeast(1)
        }

        val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        val PERMUTATIONS = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))

        internal class Shape(val cells: List<Pair<Int, Int>>, val weight: Int)

        private fun parse(pattern: String): List<Pair<Int, Int>> =
            pattern.split('/').flatMapIndexed { y, row -> row.mapIndexedNotNull { x, ch -> if (ch == 'X') x to y else null } }

        private fun rotate(cells: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val h = cells.maxOf { it.second }
            return normalise(cells.map { (x, y) -> (h - y) to x })
        }

        private fun mirror(cells: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val w = cells.maxOf { it.first }
            return normalise(cells.map { (x, y) -> (w - x) to y })
        }

        private fun normalise(cells: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
            val mx = cells.minOf { it.first }
            val my = cells.minOf { it.second }
            return cells.map { (x, y) -> (x - mx) to (y - my) }.sortedWith(compareBy({ it.second }, { it.first }))
        }

        /** Every orientation of a base shape (mirrors too when asked), the base weight shared out between them. */
        private fun family(pattern: String, weight: Int, mirrored: Boolean = false): List<Shape> {
            val seen = LinkedHashSet<List<Pair<Int, Int>>>()
            var cur = normalise(parse(pattern))
            repeat(4) {
                seen += cur
                if (mirrored) seen += mirror(cur)
                cur = rotate(cur)
            }
            val each = (weight * 12 / seen.size).coerceAtLeast(1)
            return seen.map { Shape(it, each) }
        }

        val SHAPES: List<Shape> = buildList {
            addAll(family("X", 2))
            addAll(family("XX", 3))
            addAll(family("XXX", 4))
            addAll(family("XXXX", 3))
            addAll(family("XXXXX", 2))
            addAll(family("XX/XX", 4))
            addAll(family("XXX/XXX/XXX", 2))
            addAll(family("XX/XX/XX", 2))
            addAll(family("XX/X.", 4))
            addAll(family("XXX/X../X..", 2))
            addAll(family("X./X./XX", 3, mirrored = true))
            addAll(family("XXX/.X.", 3))
            addAll(family("XX./.XX", 2, mirrored = true))
        }

        fun fits(grid: IntArray, piece: BlockPiece, x: Int, y: Int): Boolean {
            if (x < 0 || y < 0 || x + piece.width > SIZE || y + piece.height > SIZE) return false
            return piece.cells.all { (cx, cy) -> grid[(y + cy) * SIZE + x + cx] == 0 }
        }

        fun fitsAnywhere(grid: IntArray, piece: BlockPiece): Boolean {
            for (y in 0..SIZE - piece.height) for (x in 0..SIZE - piece.width) if (fits(grid, piece, x, y)) return true
            return false
        }

        fun fullRows(grid: IntArray): List<Int> = (0 until SIZE).filter { r -> (0 until SIZE).all { c -> grid[r * SIZE + c] != 0 } }
        fun fullCols(grid: IntArray): List<Int> = (0 until SIZE).filter { c -> (0 until SIZE).all { r -> grid[r * SIZE + c] != 0 } }
    }

    /** Test hook: a given tray, so a test can play known pieces. */
    internal fun setTrayForTest(pieces: List<BlockPiece?>) {
        tray = pieces
        trayRev++
        over = false
    }
}

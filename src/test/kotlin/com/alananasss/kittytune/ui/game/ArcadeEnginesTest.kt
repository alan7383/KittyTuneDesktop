package com.alananasss.kittytune.ui.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class ArcadeEnginesTest {
    private fun single(color: Int = 1) = BlockPiece(listOf(0 to 0), color)

    @Test
    fun `blocks - completing a row clears it and scores the line with the combo`() {
        val e = BlocksEngine(Random(1), openingPattern = false)
        for (x in 0 until 7) e.board[x] = 2
        e.setTrayForTest(listOf(single(), single(), single()))

        val move = e.place(0, 7, 0)!!
        assertEquals(listOf(0), move.clearedRows)
        assertEquals(1, move.combo)
        assertEquals(1 + BlocksEngine.clearPoints(1, 1) + BlocksEngine.ALL_CLEAR_BONUS, move.points)
        assertTrue((0 until 8).all { e.board[it] == 0 })
        assertTrue(move.allClear)
        assertEquals(move.points, e.score)
    }

    @Test
    fun `blocks - a row and a column at once, and the combo survives three dry moves only`() {
        val e = BlocksEngine(Random(2), openingPattern = false)
        for (x in 1 until 8) e.board[x] = 3          // row 0 minus (0,0)
        for (y in 1 until 8) e.board[y * 8] = 3      // column 0 minus (0,0)
        e.setTrayForTest(listOf(single(), single(), single()))
        val move = e.place(0, 0, 0)!!
        assertEquals(2, move.lines)
        assertEquals(1, e.combo)

        e.setTrayForTest(listOf(single(), single(), single()))
        e.place(0, 4, 4); e.place(1, 4, 5)
        assertEquals(1, e.combo)
        e.place(2, 4, 6)
        assertEquals(0, e.combo)
    }

    @Test
    fun `blocks - occupied cells refuse a piece and a full board ends the game`() {
        val e = BlocksEngine(Random(3), openingPattern = false)
        e.board[9] = 1
        e.setTrayForTest(listOf(single(), null, null))
        assertNull(e.place(0, 1, 1))
        // Checkerboard: no row or column ever fills, and a 2-cell piece fits nowhere.
        for (i in 0 until 64) e.board[i] = if ((i % 8 + i / 8) % 2 == 0) 1 else 0
        val domino = BlockPiece(listOf(0 to 0, 1 to 0), 2)
        e.setTrayForTest(listOf(single(), domino, null))
        e.place(0, 1, 0)
        assertTrue(e.over)
        assertEquals(-1, e.grade(e.board, listOf(domino, domino, domino)))
    }

    @Test
    fun `blocks - every tray dealt on an open board can be played and help falls with the score`() {
        val e = BlocksEngine(Random(4), openingPattern = true)
        repeat(30) {
            assertTrue(e.tray.filterNotNull().any { e.fitsAnywhere(it) })
            e.reset()
        }
        assertTrue(BlocksEngine.SHAPES.size >= 30)
        assertTrue(BlocksEngine.HELP_TABLE.zipWithNext().all { (a, b) -> a.second[0] > b.second[0] })
    }

    @Test
    fun `mines - the first click is safe, opens an area and numbers chord`() {
        val e = MinesEngine(MinesLevel.EASY, Random(7))
        val opened = e.reveal(40)
        assertEquals(MinesState.PLAYING, e.state)
        assertTrue(opened.size >= 9)
        assertFalse(e.mines[40])
        assertTrue(e.neighbours(40).none { e.mines[it] })
        assertEquals(10, e.mines.count { it })

        val number = (0 until e.count).first { e.revealed[it] && e.adjacent[it] > 0 }
        assertTrue(e.chord(number).isEmpty()) // no flags yet: nothing to chord
        e.neighbours(number).filter { e.mines[it] }.forEach { e.toggleFlag(it) }
        e.chord(number)
        assertTrue(e.neighbours(number).all { e.mines[it] || e.revealed[it] })
        assertEquals(10 - e.flags, e.minesLeft)
    }

    @Test
    fun `mines - opening every safe cell wins, a mine loses`() {
        val win = MinesEngine(MinesLevel.EASY, Random(8))
        win.reveal(0)
        for (i in 0 until win.count) if (!win.mines[i]) win.reveal(i)
        assertEquals(MinesState.WON, win.state)
        assertEquals(0, win.minesLeft)

        val lose = MinesEngine(MinesLevel.EASY, Random(8))
        lose.reveal(0)
        lose.reveal((0 until lose.count).first { lose.mines[it] })
        assertEquals(MinesState.LOST, lose.state)
    }

    @Test
    fun `bricks - a volley breaks the brick in its way and the wall steps down`() {
        val e = BricksEngine(Random(5))
        e.hp.fill(0); e.pickup.fill(false)
        val target = 5 * BricksEngine.COLS + 3
        e.hp[target] = 1
        assertTrue(e.shoot(e.launchX, 0f))
        var t = 0L
        var guard = 0
        while (e.phase == BricksPhase.SHOOTING && guard++ < 2_000) {
            t += 16
            e.update(1f / 60f, fast = false, now = t)
        }
        assertEquals(BricksPhase.AIM, e.phase)
        assertEquals(2, e.turn)
        assertEquals(0, e.hp[target + BricksEngine.COLS]) // shifted down one row, and broken
        assertFalse(e.shoot(e.launchX, BricksEngine.LAUNCH_Y + 1f)) // aiming down is refused
    }

    @Test
    fun `bricks - the aim never goes flat`() {
        val (_, dy) = BricksEngine.clampAim(10f, -0.06f)!!
        assertTrue(dy < -0.13f)
        assertNull(BricksEngine.clampAim(1f, 1f))
    }

    @Test
    fun `merge - equal tiles merge once per move`() {
        val e = MergeEngine(Random(9))
        e.setTilesForTest(listOf(Triple(2, 0, 0), Triple(2, 1, 0), Triple(4, 2, 0), Triple(4, 3, 0)))
        assertTrue(e.move(-1, 0))
        val row = e.tiles.filter { it.y == 0 && !it.fresh }.sortedBy { it.x }.map { it.value }
        assertEquals(listOf(4, 8), row)
        assertEquals(12, e.score)
        assertEquals(1, e.tiles.count { it.fresh })
        e.undo()
        assertEquals(0, e.score)
        assertEquals(4, e.tiles.size)
    }

    @Test
    fun `merge - a move that changes nothing is refused`() {
        val e = MergeEngine(Random(10))
        e.setTilesForTest(listOf(Triple(2, 0, 0)))
        assertFalse(e.move(-1, 0))
        assertFalse(e.move(0, -1))
        assertTrue(e.move(1, 0))
    }

    @Test
    fun `playtime - days are kept apart and summed per game`() {
        val store = mutableMapOf<String, Long>()
        val zone = ZoneId.of("UTC")
        val ledger = PlaytimeLedger({ store }, { k, v -> store[k] = v }, { zone })
        val d1 = LocalDate.of(2026, 11, 20).atStartOfDay(zone).toInstant().toEpochMilli()
        val d2 = LocalDate.of(2026, 11, 24).atStartOfDay(zone).toInstant().toEpochMilli()
        ledger.add(GameId.BLOCKS, 60_000, d1 + 1000)
        ledger.add(GameId.BLOCKS, 30_000, d1 + 5000)
        ledger.add(GameId.MINES, 120_000, d2 + 1000)

        assertEquals(mapOf(GameId.BLOCKS to 90_000L, GameId.MINES to 120_000L), ledger.totals())
        assertEquals(mapOf(GameId.MINES to 120_000L), ledger.totals(d2, d2 + 86_400_000L))
        assertTrue(PlaytimeLedger.parse("unrelated") == null)
    }

    // ─── Stack ───────────────────────────────────────────────────────────────────────────

    private fun StackEngine.fillRow(y: Int, except: Set<Int>) {
        for (x in 0 until StackEngine.W) if (x !in except) board[y * StackEngine.W + x] = 1
    }

    @Test
    fun `stack - rotations follow the standard shapes and the first pieces come from one bag`() {
        assertEquals(setOf(2 to 1, 1 to 0, 1 to 1, 1 to 2), Tetromino.T.cellsAt(1).toSet())
        assertEquals(setOf(2 to 0, 2 to 1, 2 to 2, 2 to 3), Tetromino.I.cellsAt(1).toSet())
        assertEquals(Tetromino.O.cells, Tetromino.O.cellsAt(3))
        val e = StackEngine(Random(11))
        val dealt = listOf(e.current!!.type) + e.queue
        assertEquals(6, dealt.toSet().size)
    }

    @Test
    fun `stack - a hard drop that fills a row clears it and scores`() {
        val e = StackEngine(Random(12))
        e.fillRow(StackEngine.H - 1, setOf(3, 4, 5, 6))
        e.setCurrentForTest(ActivePiece(Tetromino.I, 0, 3, 2))
        e.hardDrop()
        assertEquals(1, e.lines)
        assertEquals(listOf(StackEngine.H - 1), e.lastClear!!.rows)
        assertTrue((0 until StackEngine.W).all { e.board[(StackEngine.H - 1) * StackEngine.W + it] == 0 })
        assertTrue(e.score >= 100)
        assertNotNull(e.current)
    }

    @Test
    fun `stack - four lines at once, then back-to-back pays half more`() {
        val e = StackEngine(Random(13))
        fun four(): Int {
            for (y in StackEngine.H - 4 until StackEngine.H) e.fillRow(y, setOf(9))
            e.setCurrentForTest(ActivePiece(Tetromino.I, 1, 7, 2)) // vertical, cells in column 9
            val before = e.score
            e.hardDrop()
            return e.score - before
        }
        val first = four()
        assertEquals(4, e.lastClear!!.rows.size)
        assertFalse(e.lastClear!!.backToBack)
        val second = four()
        assertTrue(e.lastClear!!.backToBack)
        assertTrue(second > first)
        assertEquals(8, e.lines)
    }

    @Test
    fun `stack - hold swaps once per piece and the wall kicks a turn off the wall`() {
        val e = StackEngine(Random(14))
        val first = e.current!!.type
        e.holdPiece()
        assertEquals(first, e.hold)
        val second = e.current!!.type
        e.holdPiece()
        assertEquals(second, e.current!!.type) // refused until the piece locks

        e.setCurrentForTest(ActivePiece(Tetromino.I, 1, -2, 5)) // vertical against the left wall
        assertTrue(e.fits(e.current!!))
        assertTrue(e.rotate(1))
        assertTrue(e.current!!.cells().all { it.first >= 0 })
    }

    @Test
    fun `stack - gravity and lock delay put the piece down, a full well ends the game`() {
        val e = StackEngine(Random(15))
        val start = e.locks
        repeat(60 * 30) { e.tick(1f / 60f, softDropping = false) }
        assertTrue(e.locks > start)

        val f = StackEngine(Random(16))
        for (y in StackEngine.HIDDEN until StackEngine.H) f.fillRow(y, setOf(y % StackEngine.W))
        repeat(20) { if (!f.over) f.hardDrop() }
        assertTrue(f.over)
    }

    @Test
    fun `snake - the classic game serves treats for ever and never needs a song`() {
        val e = SnakeEngine(15, 21, emptyList(), endless = true)
        e.ensureRunning()
        repeat(5) {
            val food = e.foods.single()
            assertNull(food.track)
            val head = e.snake.first()
            e.foods = listOf(food.copy(cell = head.copy(x = (head.x + 1) % 15)))
            e.step()
        }
        assertEquals(5, e.score)
        assertEquals(SnakePhase.RUNNING, e.phase)
        assertNull(e.lastEaten)
    }
}

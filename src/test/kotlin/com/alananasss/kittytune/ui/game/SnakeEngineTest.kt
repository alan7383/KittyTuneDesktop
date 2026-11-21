package com.alananasss.kittytune.ui.game

import com.alananasss.kittytune.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeEngineTest {
    private val songs = (1L..3L).map { Track(it, "Track $it", null, 120_000L, null) }

    @Test
    fun `covers appear in playlist order and each bite speeds up the game`() {
        val engine = SnakeEngine(15, 21, songs.drop(1))
        engine.ensureRunning()
        for (song in songs.drop(1)) {
            val food = engine.foods.single()
            assertEquals(song, food.track)
            assertTrue(food.cell !in engine.snake)
            val head = engine.snake.first()
            engine.foods = listOf(food.copy(cell = head.copy(x = head.x + 1)))
            val previousDelay = engine.speedMs

            engine.step()

            assertEquals(song, engine.lastEaten)
            assertTrue(engine.speedMs < previousDelay)
        }
        assertEquals(SnakePhase.WIN, engine.phase)
        assertEquals(2, engine.score)

        engine.reset()

        assertEquals(SnakePhase.IDLE, engine.phase)
        assertEquals(songs[1], engine.foods.single().track)
        assertEquals(460f, engine.speedMs, 0f)
        assertEquals(0, engine.score)
    }

    @Test
    fun `pausing stops movement and the snake cannot turn straight back`() {
        val engine = SnakeEngine(15, 21, songs)
        val head = engine.snake.first()
        engine.ensureRunning()
        engine.pause()
        engine.step()
        assertEquals(head, engine.snake.first())

        engine.ensureRunning()
        engine.turn(SnakeDirection.LEFT)
        engine.step()

        assertEquals(head.copy(x = head.x + 1), engine.snake.first())
        assertEquals(SnakePhase.RUNNING, engine.phase)
    }
}

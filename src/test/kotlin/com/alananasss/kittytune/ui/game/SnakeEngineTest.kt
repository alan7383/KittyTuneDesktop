package com.alananasss.kittytune.ui.game

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import com.alananasss.kittytune.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(InternalComposeUiApi::class)
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

    @Test
    fun `steering works for ZQSD on AZERTY, WASD on QWERTY, arrow keys and numpad`() {
        // AZERTY ZQSD
        assertEquals(SnakeDirection.UP, steeringFor(Key.Z))
        assertEquals(SnakeDirection.LEFT, steeringFor(Key.Q))
        assertEquals(SnakeDirection.DOWN, steeringFor(Key.S))
        assertEquals(SnakeDirection.RIGHT, steeringFor(Key.D))

        // Char-based fallback for AZERTY
        assertEquals(SnakeDirection.UP, steeringFor(Key.Unknown, 'z'))
        assertEquals(SnakeDirection.LEFT, steeringFor(Key.Unknown, 'q'))
        assertEquals(SnakeDirection.DOWN, steeringFor(Key.Unknown, 's'))
        assertEquals(SnakeDirection.RIGHT, steeringFor(Key.Unknown, 'd'))

        // QWERTY WASD
        assertEquals(SnakeDirection.UP, steeringFor(Key.W))
        assertEquals(SnakeDirection.LEFT, steeringFor(Key.A))

        // Arrow keys
        assertEquals(SnakeDirection.UP, steeringFor(Key.DirectionUp))
        assertEquals(SnakeDirection.DOWN, steeringFor(Key.DirectionDown))
        assertEquals(SnakeDirection.LEFT, steeringFor(Key.DirectionLeft))
        assertEquals(SnakeDirection.RIGHT, steeringFor(Key.DirectionRight))

        // Numpad
        assertEquals(SnakeDirection.UP, steeringFor(Key.NumPad8))
        assertEquals(SnakeDirection.DOWN, steeringFor(Key.NumPad2))
        assertEquals(SnakeDirection.DOWN, steeringFor(Key.NumPad5))
        assertEquals(SnakeDirection.LEFT, steeringFor(Key.NumPad4))
        assertEquals(SnakeDirection.RIGHT, steeringFor(Key.NumPad6))
    }

    @Test
    fun `SnakeGameLauncher routes key events to active handler`() {
        var receivedKey: Key? = null
        SnakeGameLauncher.keyHandler = { event ->
            receivedKey = event.key
            true
        }

        val event = KeyEvent(Key.Z, KeyEventType.KeyDown)
        val handled = SnakeGameLauncher.handleKeyEvent(event)

        assertTrue(handled)
        assertEquals(Key.Z, receivedKey)

        SnakeGameLauncher.dismiss()
        assertFalse(SnakeGameLauncher.handleKeyEvent(event))
    }
}

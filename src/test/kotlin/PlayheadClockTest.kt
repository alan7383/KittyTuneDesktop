import com.alananasss.kittytune.ui.player.slider.PlayheadClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayheadClockTest {
    private val frameNanos = 16_000_000L

    @Test
    fun `moves every frame between reports and never steps back`() {
        val clock = PlayheadClock()
        var now = 0L
        clock.step(0L, "a", direct = false, running = true, now = now)
        var reported = 0L
        var last = 0L
        repeat(300) { frame ->
            now += frameNanos
            if (frame % 15 == 14) reported += 240L // a report every 240 ms, in engine-sized steps
            val shown = clock.step(reported, "a", direct = false, running = true, now = now)
            assertTrue(shown >= last, "frame $frame went back from $last to $shown")
            last = shown
        }
        assertTrue(kotlin.math.abs(last - now / 1_000_000) < 300, "drifted to $last at ${now / 1_000_000} ms")
    }

    @Test
    fun `a seek slides instead of jumping`() {
        val clock = PlayheadClock()
        var now = 0L
        clock.step(10_000L, "a", direct = false, running = false, now = now)
        now += frameNanos
        val first = clock.step(60_000L, "a", direct = false, running = false, now = now)
        assertTrue(first in 10_000L until 60_000L, "jumped straight to $first")
        repeat(30) { now += frameNanos; clock.step(60_000L, "a", direct = false, running = false, now = now) }
        assertEquals(60_000L, clock.step(60_000L, "a", direct = false, running = false, now = now + frameNanos))
    }

    @Test
    fun `a new track starts at its own position`() {
        val clock = PlayheadClock()
        clock.step(170_000L, "a", direct = false, running = true, now = 0L)
        assertEquals(0L, clock.step(0L, "b", direct = false, running = true, now = frameNanos))
    }
}

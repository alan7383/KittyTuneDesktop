import androidx.compose.runtime.MonotonicFrameClock
import com.alananasss.kittytune.ui.main.WheelSeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The wheel over the seek bar moves the bar at once and seeks the player once (tester's list, round 2, item 1). */
class WheelSeekTest {

    private object FrameClock : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(8)
            return onFrame(System.nanoTime())
        }
    }

    private fun scope() = CoroutineScope(SupervisorJob() + Dispatchers.Default + FrameClock)

    @Test
    fun `a spin of notches seeks once, where the spin ended`() = runBlocking {
        val scope = scope()
        val seeks = CopyOnWriteArrayList<Long>()
        var reported = 60_000L
        val wheel = WheelSeek(
            scope = scope,
            drawnMs = { reported },
            reportedMs = { reported },
            durationMs = { 240_000L },
            stepSeconds = { 5f },
            commit = { seeks += it; reported = it },
        )

        // Up is forward: each notch of -1 is five seconds on.
        repeat(6) { wheel.onNotch(-1f); delay(20) }
        assertTrue(wheel.isActive)
        assertNotNull(wheel.shownMs)
        assertTrue(seeks.isEmpty(), "nothing is asked of the player while the wheel is still turning")

        delay(700)
        assertEquals(listOf(90_000L), seeks)
        assertFalse(wheel.isActive, "handed back once the player has arrived")
        scope.cancel()
    }

    @Test
    fun `back and forth counts from where the last notch aimed, not from the stale report`() = runBlocking {
        val scope = scope()
        val seeks = CopyOnWriteArrayList<Long>()
        val wheel = WheelSeek(
            scope = scope,
            drawnMs = { 100_000L },
            reportedMs = { 100_000L },
            durationMs = { 240_000L },
            stepSeconds = { 5f },
            commit = { seeks += it },
        )
        wheel.onNotch(-1f)
        wheel.onNotch(-1f)
        wheel.onNotch(1f)
        wheel.onNotch(-1f)
        delay(400)
        assertEquals(listOf(110_000L), seeks)
        scope.cancel()
    }

    @Test
    fun `the bar stays on the target until the player gets there, then lets go`() = runBlocking {
        val scope = scope()
        var reported = 10_000L
        val wheel = WheelSeek(
            scope = scope,
            drawnMs = { reported },
            reportedMs = { reported },
            durationMs = { 240_000L },
            stepSeconds = { 30f },
            commit = { },
        )
        wheel.onNotch(-1f)
        delay(600)
        assertTrue(wheel.isActive, "the player has not moved yet, the bar holds the target")
        assertTrue(wheel.shownMs!! in 39_000L..40_000L)

        reported = 40_000L
        delay(200)
        assertNull(wheel.shownMs)
        scope.cancel()
    }

    @Test
    fun `it never leaves the track, and a missing track does nothing`() = runBlocking {
        val scope = scope()
        val seeks = CopyOnWriteArrayList<Long>()
        var duration = 0L
        val wheel = WheelSeek(
            scope = scope,
            drawnMs = { 2_000L },
            reportedMs = { 2_000L },
            durationMs = { duration },
            stepSeconds = { 5f },
            commit = { seeks += it },
        )
        wheel.onNotch(-1f)
        delay(300)
        assertTrue(seeks.isEmpty() && !wheel.isActive)

        duration = 10_000L
        repeat(5) { wheel.onNotch(1f) }
        delay(400)
        assertEquals(listOf(0L), seeks)
        scope.cancel()
    }
}

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import com.alananasss.kittytune.core.LocalWindowSeen
import com.alananasss.kittytune.ui.player.slider.MixTransition
import com.alananasss.kittytune.ui.player.slider.PlayheadClock
import com.alananasss.kittytune.ui.player.slider.rememberSmoothPlayhead
import com.alananasss.kittytune.ui.player.slider.rememberSliderWavePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayheadClockTest {
    private val frameNanos = 16_000_000L

    @Test
    fun `slider wave resumes from its last visible phase`() {
        var seen by mutableStateOf(true)
        var phase = 0f
        val scene = ImageComposeScene(width = 1, height = 1) {
            CompositionLocalProvider(LocalWindowSeen provides seen) {
                phase = rememberSliderWavePhase(true, speedPx = 50f, wavelengthPx = 80f).floatValue
            }
        }
        try {
            repeat(5) { scene.render(it * frameNanos).close() }
            assertTrue(phase > 0f)

            seen = false
            scene.render(5 * frameNanos).close()
            val before = phase
            scene.render(2_000_000_000L).close()
            assertEquals(before, phase)

            seen = true
            val restored = 3_000_000_000L
            scene.render(restored).close()
            assertEquals(before, phase)
            scene.render(restored + frameNanos).close()
            assertEquals(before, phase)
            scene.render(restored + 2 * frameNanos).close()
            scene.render(restored + 3 * frameNanos).close()
            assertTrue(phase > before)
            assertTrue(phase - before <= 1.601f)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `restoring during a mix skips the old track glide`() {
        val fade = mutableFloatStateOf(0f)
        val mixing = mutableStateOf(false)
        val mix = MixTransition(fade, mutableFloatStateOf(0f), mutableFloatStateOf(1f), mixing)
        assertEquals(0.9f, mix.shownFraction(0.9f, "old", isSeen = true))
        mix.shownFraction(0.95f, "old", isSeen = false)

        mixing.value = true
        fade.floatValue = 0.3f
        assertEquals(0.1f, mix.shownFraction(0.1f, "new", isSeen = true))
        assertEquals(0.11f, mix.shownFraction(0.11f, "new", isSeen = true))
    }

    @Test
    fun `restoring the window immediately shows the latest position`() {
        var seen by mutableStateOf(true)
        var reported by mutableLongStateOf(10_000L)
        var displayed = 0L
        val scene = ImageComposeScene(width = 1, height = 1) {
            CompositionLocalProvider(LocalWindowSeen provides seen) {
                val shown = rememberSmoothPlayhead(reported, true, false, "track", 0L)
                SideEffect { displayed = shown }
            }
        }
        try {
            scene.render(0L).close()
            assertEquals(10_000L, displayed)

            seen = false
            scene.render(frameNanos).close()
            reported = 60_000L
            scene.render(2 * frameNanos).close()
            assertEquals(60_000L, displayed)

            seen = true
            reported = 61_000L
            scene.render(3 * frameNanos).close()
            assertEquals(61_000L, displayed)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a click shows its committed position in the first frame`() {
        var reported by mutableLongStateOf(10_000L)
        var revision by mutableLongStateOf(0L)
        var displayed = 0L
        val scene = ImageComposeScene(width = 1, height = 1) {
            val shown = rememberSmoothPlayhead(
                reportedMs = reported,
                isRunning = false,
                followsInput = false,
                trackKey = "track",
                seekRevision = revision,
            )
            SideEffect { displayed = shown }
        }
        try {
            scene.render(0L).close()
            assertEquals(10_000L, displayed)

            reported = 80_000L
            revision++
            scene.render(frameNanos).close()
            assertEquals(80_000L, displayed)

            reported = 50_000L
            revision++
            reported = 25_000L
            revision++
            scene.render(2 * frameNanos).close()
            assertEquals(25_000L, displayed)
            scene.render(3 * frameNanos).close()
            assertEquals(25_000L, displayed)
        } finally {
            scene.close()
        }
    }

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

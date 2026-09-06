import com.alananasss.kittytune.ui.player.FLUID_SKSL
import com.alananasss.kittytune.ui.player.FluidCopy
import com.alananasss.kittytune.ui.player.fluidCopies
import org.jetbrains.skia.RuntimeEffect
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The full player's Apple Music background: the stack of sleeves, and the shader that reads it.
 *
 * Two things can go wrong here in a way that no other test would catch, and both of them produce a screen
 * rather than an exception.
 *
 * The first is a typo in the SkSL. It compiles at runtime, inside a `try` that falls back to drawing
 * nothing, so a misplaced semicolon is not a crash — it is a background that silently reverts to the
 * drifting lights it was supposed to replace, on every machine, forever. [shader_compiles] is the only
 * place that difference is visible.
 *
 * The second is a gap. The four copies of the artwork are opaque squares, and the largest one is sized
 * `√2 ×` the canvas's longer side precisely so that it covers the canvas at every angle it can be turned
 * to. Get that constant wrong and there is a wedge of nothing in a corner for part of every rotation —
 * which no amount of blur closes, because blur spreads what is there and there is nothing there.
 */
class FluidArtworkBackgroundTest {

    @Test
    fun shader_compiles() {
        assertNotNull(
            RuntimeEffect.makeForShader(FLUID_SKSL),
            "the background shader has to compile — the fallback for a broken one is an empty screen",
        )
    }

    @Test
    fun largest_copy_covers_the_canvas_at_every_angle() {
        // Aspect ratios from a nearly square window to a very wide one, since the copy is sized off the
        // longer side and it is the shorter one that runs out of cover.
        for ((w, h) in listOf(1000f to 900f, 1920f to 1080f, 2560f to 1080f, 900f to 1600f)) {
            // A full turn of the fastest thing in here, sampled finely enough to catch a corner slipping out.
            for (step in 0 until 400) {
                val copies = fluidCopies(w, h, step * 0.25f, seed = 0x5EED)
                for (corner in listOf(0f to 0f, w to 0f, 0f to h, w to h)) {
                    val uv = localOf(corner.first, corner.second, copies[0])
                    assertTrue(
                        uv.first in 0f..1f && uv.second in 0f..1f,
                        "corner $corner escaped the base copy on a ${w}x$h canvas at step $step",
                    )
                }
            }
        }
    }

    @Test
    fun copies_are_the_sizes_the_web_player_uses() {
        val copies = fluidCopies(1920f, 1080f, seconds = 3f, seed = 1)
        val longest = 1920f
        assertEquals(4, copies.size)
        assertEquals(longest * 1.4142f, copies[0].side, 0.5f)
        assertEquals(longest * 0.8f, copies[1].side, 0.5f)
        assertEquals(longest * 0.5f, copies[2].side, 0.5f)
        assertEquals(longest * 0.25f, copies[3].side, 0.5f)
    }

    @Test
    fun only_the_two_small_copies_travel() {
        val a = fluidCopies(1600f, 900f, seconds = 0f, seed = 7)
        val b = fluidCopies(1600f, 900f, seconds = 9f, seed = 7)
        for (i in 0..1) {
            assertEquals(a[i].centreX, b[i].centreX, 0.001f, "copy $i should turn in place")
            assertEquals(a[i].centreY, b[i].centreY, 0.001f, "copy $i should turn in place")
        }
        for (i in 2..3) {
            assertTrue(
                distance(a[i], b[i]) > 20f,
                "copy $i is supposed to be on a circular track and moved ${distance(a[i], b[i])}px in 9s",
            )
        }
    }

    @Test
    fun neighbouring_copies_turn_against_each_other() {
        // Not "every rate is different" — the source gives the largest and the third copy the same rate, and
        // they are a √2 square and a half-size one on a circular track, so they never read as one shape. What
        // does matter is the sign: two copies stacked on each other turning the same way shear against
        // nothing, and shear between neighbours is the whole appearance of a fluid.
        val start = fluidCopies(1600f, 900f, seconds = 0f, seed = 42)
        val later = fluidCopies(1600f, 900f, seconds = 10f, seed = 42)
        val rates = start.indices.map { (later[it].angle - start[it].angle) / 10f }
        rates.forEachIndexed { i, rate -> assertTrue(rate != 0f, "copy $i does not turn at all") }
        for (i in 0 until rates.size - 1) {
            assertTrue(
                rates[i] * rates[i + 1] < 0f,
                "copies $i and ${i + 1} turn the same way (${rates[i]}, ${rates[i + 1]} rad/s)",
            )
        }
    }

    @Test
    fun the_arrangement_depends_on_the_seed() {
        val one = fluidCopies(1600f, 900f, seconds = 0f, seed = 1)
        val two = fluidCopies(1600f, 900f, seconds = 0f, seed = 2)
        assertTrue(
            one.indices.any { kotlin.math.abs(one[it].angle - two[it].angle) > 0.01f },
            "two sessions should not open on four squares in the same alignment",
        )
    }

    /** The shader's own `localOf`, in Kotlin: where a canvas point falls inside one copy, in 0..1. */
    private fun localOf(x: Float, y: Float, copy: FluidCopy): Pair<Float, Float> {
        val dx = x - copy.centreX
        val dy = y - copy.centreY
        val c = cos(-copy.angle)
        val s = sin(-copy.angle)
        return (dx * c - dy * s) / copy.side + 0.5f to (dx * s + dy * c) / copy.side + 0.5f
    }

    private fun distance(a: FluidCopy, b: FluidCopy): Float =
        kotlin.math.hypot(a.centreX - b.centreX, a.centreY - b.centreY)
}

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Which queue rows count as already played (issue #33).
 *
 * Reported as the queue counting tracks nobody had listened to: jump forward to the sixth track and
 * the five skipped ones were drawn as played, jump back and the ones really heard were drawn as still
 * to come. The rule had been reading position as history, and the two only agree if you never jump.
 *
 * The rule itself is three comparisons, which is exactly the kind of thing that gets quietly
 * reintroduced, so it is pinned here rather than only in the composable.
 */
class QueuePlayedMarkingTest {

    /** The rule the queue draws with: heard, and far enough back that it is not the previous track. */
    private fun compacted(queue: List<Long>, currentIndex: Int, played: Set<Long>): List<Long> =
        queue.filterIndexed { index, id -> index < currentIndex - 1 && id in played }

    private val queue = listOf(10L, 11L, 12L, 13L, 14L, 15L, 16L)

    @Test
    fun `playing straight through compacts everything but the previous track`() {
        // Heard 10 to 15, now on 16.
        val played = setOf(10L, 11L, 12L, 13L, 14L, 15L, 16L)
        // 15 is the previous track and keeps its full row, so it stops at 14.
        assertEquals(listOf(10L, 11L, 12L, 13L, 14L), compacted(queue, currentIndex = 6, played))
    }

    @Test
    fun `jumping forward does not claim the tracks that were skipped`() {
        // Started on 10, clicked 16. Nothing between them was ever played.
        val played = setOf(10L, 16L)
        assertEquals(listOf(10L), compacted(queue, currentIndex = 6, played))
    }

    @Test
    fun `jumping back leaves what was actually heard alone`() {
        // Heard 10 to 16, then clicked back to 12. Only 10 is both played and far enough back.
        val played = queue.toSet()
        assertEquals(listOf(10L), compacted(queue, currentIndex = 2, played))
    }

    @Test
    fun `nothing is compacted at the start of a queue`() {
        assertEquals(emptyList(), compacted(queue, currentIndex = 0, played = setOf(10L)))
        assertEquals(emptyList(), compacted(queue, currentIndex = 1, played = setOf(10L)))
    }

    @Test
    fun `a fresh queue with nothing played compacts nothing`() {
        assertEquals(emptyList(), compacted(queue, currentIndex = 4, played = emptySet()))
    }
}

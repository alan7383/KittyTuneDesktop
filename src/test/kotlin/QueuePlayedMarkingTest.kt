import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.main.QueueListItem
import com.alananasss.kittytune.ui.main.panelQueueItems
import com.alananasss.kittytune.ui.player.AnchorCurrentQueueItem
import com.alananasss.kittytune.ui.player.queuePastIndices
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    private fun track(id: Long) = Track(id, "Track $id", null, 1000L, null)

    private fun compacted(queue: List<Long>, currentIndex: Int, played: Set<Long>): List<Long> =
        queuePastIndices(queue.map(::track), currentIndex, played, preserveUpcoming = true).map { queue[it] }

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

    @Test
    fun `collapse threshold leaves small histories expanded by default`() {
        val pastTracks = listOf(10L, 11L, 12L)
        val hasTooMany = pastTracks.size > 3
        assertEquals(false, hasTooMany)
    }

    @Test
    fun `collapse threshold triggers when history grows too long`() {
        val pastTracks = listOf(10L, 11L, 12L, 13L)
        val hasTooMany = pastTracks.size > 3
        assertEquals(true, hasTooMany)
    }

    @Test
    fun `collapsed queue hides past tracks but preserves previous and current tracks`() {
        val items = panelQueueItems(queue.map(::track), 6, (0..4).toSet(), false, false)
        assertEquals(QueueListItem.PastHeader(5, false), items.first())
        val rows = items.filterIsInstance<QueueListItem.TrackItem>()
        assertEquals(listOf(5, 6), rows.map { it.queueIndex })
        assertTrue(rows.none { it.isCompact })
    }

    @Test
    fun `expanded queue shows all tracks completely`() {
        for (keepPlayedTracksVisible in listOf(false, true)) {
            val items = panelQueueItems(queue.map(::track), 5, setOf(0, 2, 3), true, keepPlayedTracksVisible)
            val rows = items.filterIsInstance<QueueListItem.TrackItem>()
            assertEquals(queue.indices.toList(), rows.map { it.queueIndex })
            assertTrue(rows.none { it.isCompact })
        }
    }

    @Test
    fun `opt in keeps every row visible and compacts only played rows`() {
        val past = setOf(0, 2, 3)
        val items = panelQueueItems(queue.map(::track), 5, past, false, true)
        val rows = items.filterIsInstance<QueueListItem.TrackItem>()
        assertEquals(QueueListItem.PastHeader(3, false), items.first())
        assertEquals(QueueListItem.UpNextHeader(1), items[7])
        assertEquals(queue.indices.toList(), rows.map { it.queueIndex })
        assertEquals(past, rows.filter { it.isCompact }.map { it.queueIndex }.toSet())
    }

    @Test
    fun `expanding played rows leaves the viewport in place but changing track anchors it`() = SwingUtilities.invokeAndWait {
        val tracks = (0L..35L).map(::track)
        val expanded = mutableStateOf(false)
        val playing = mutableStateOf(20)
        lateinit var listState: LazyListState
        val scene = ImageComposeScene(width = 320, height = 100, coroutineContext = Dispatchers.Main.immediate) {
            listState = rememberLazyListState()
            val current = playing.value
            val rows = panelQueueItems(tracks, current, (1..18).toSet(), expanded.value, false)
            val target = rows.indexOfFirst { it is QueueListItem.TrackItem && it.queueIndex == current - 1 }
            AnchorCurrentQueueItem(listState, current, tracks[current].id, currentKey = current, targetIndex = target)
            LazyColumn(state = listState) {
                items(rows, key = {
                    when (it) {
                        is QueueListItem.PastHeader -> "played"
                        is QueueListItem.UpNextHeader -> "upcoming"
                        is QueueListItem.TrackItem -> it.queueIndex
                    }
                }) { Box(Modifier.height(40.dp)) }
            }
        }
        try {
            var frame = 0L
            fun settle() = repeat(30) { scene.render(frame++ * 16_000_000L).close() }
            settle()
            listState.requestScrollToItem(0)
            settle()
            assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.key == "played" })
            assertFalse(listState.layoutInfo.visibleItemsInfo.any { it.key == 20 })

            expanded.value = true
            settle()
            assertEquals(0, listState.firstVisibleItemIndex)
            assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.key == "played" })
            assertFalse(listState.layoutInfo.visibleItemsInfo.any { it.key == 20 })

            playing.value = 34
            settle()
            assertTrue(listState.layoutInfo.visibleItemsInfo.any { it.key == 34 })
        } finally {
            scene.close()
        }
    }
}

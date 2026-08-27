import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.main.MenuTiles
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The arrangement of the options-menu tiles (issue #33).
 *
 * Worth pinning down because the tricky part is not the drag: it is that a menu only ever shows a
 * subset of the tiles it could show — a local file has no comments, a station has no details — so an
 * arrangement has to be stored over the whole catalogue and applied to whatever happens to be
 * present, without disturbing tiles the reader never touched.
 */
class MenuTilesTest {

    private fun arrange(present: List<String>, order: List<String> = emptyList(), hidden: Set<String> = emptySet()) =
        MenuTiles.arrange(present, order, hidden) { it }

    @Test
    fun `no arrangement leaves the built-in order alone`() {
        val present = listOf("a", "b", "c")
        assertEquals(present, arrange(present))
    }

    @Test
    fun `hidden tiles are dropped`() {
        assertEquals(listOf("a", "c"), arrange(listOf("a", "b", "c"), hidden = setOf("b")))
    }

    @Test
    fun `a moved tile comes first`() {
        assertEquals(listOf("c", "a", "b"), arrange(listOf("a", "b", "c"), order = listOf("c")))
    }

    @Test
    fun `tiles nobody moved keep their built-in sequence`() {
        // Only "d" was arranged; a, b, c must not be reshuffled among themselves.
        assertEquals(
            listOf("d", "a", "b", "c"),
            arrange(listOf("a", "b", "c", "d"), order = listOf("d")),
        )
    }

    @Test
    fun `an order naming tiles this menu does not have is harmless`() {
        assertEquals(listOf("b", "a"), arrange(listOf("a", "b"), order = listOf("zzz", "b")))
    }

    @Test
    fun `a tile added in a later version turns up rather than disappearing`() {
        // A stored order written before "new" existed.
        val stored = listOf("b", "a")
        assertTrue("new" in arrange(listOf("a", "b", "new"), order = stored))
        assertEquals(listOf("b", "a", "new"), arrange(listOf("a", "b", "new"), order = stored))
    }

    @Test
    fun `moving splices into the whole catalogue, not into what was on screen`() {
        val menu = PlayerPreferences.MENU_TRACK
        val catalogue = MenuTiles.TRACK.map { it.id }
        val order = MenuTiles.moved(menu, stored = emptyList(), fromId = "trim", toId = "like")

        assertEquals("trim", order.first())
        // Everything else is still there, still in its built-in relative order.
        assertEquals(catalogue.filterNot { it == "trim" }, order.drop(1))
    }

    @Test
    fun `moving a tile that is not there changes nothing`() {
        val stored = listOf("share")
        assertEquals(stored, MenuTiles.moved(PlayerPreferences.MENU_TRACK, stored, "nope", "like"))
        assertEquals(stored, MenuTiles.moved(PlayerPreferences.MENU_TRACK, stored, "like", "like"))
    }

    @Test
    fun `every catalogue id is unique`() {
        listOf(MenuTiles.TRACK, MenuTiles.PLAYLIST).forEach { tiles ->
            val ids = tiles.map { it.id }
            assertEquals(ids.size, ids.distinct().size, "duplicate ids in $ids")
        }
    }
}

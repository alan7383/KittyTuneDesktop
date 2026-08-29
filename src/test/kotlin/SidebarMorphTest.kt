import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.main.SidebarMorph
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The number every part of the sidebar's collapse is keyed to (issue #33).
 *
 * "I also noticed that the 'Favorites' folder and so on are positioned lower when collapsed than when
 * expanded. […] Right now, there's no animation — the text doesn't shrink, it's just removed, and the
 * icons are moved to the centre."
 *
 * The answer to all of it is one progress value read from the panel's real width, so this covers the
 * arithmetic and the geometry that the layout then trusts.
 */
class SidebarMorphTest {

    @Test
    fun `progress is zero at full width and one at the rail`() {
        assertEquals(0f, SidebarMorph.progressFor(expanded = 300.dp, actual = 300.dp))
        assertEquals(1f, SidebarMorph.progressFor(expanded = 300.dp, actual = SidebarMorph.RAIL_WIDTH))
    }

    @Test
    fun `progress is linear in the width between them`() {
        // 300 down to 80 is 220 dp of travel; 190 dp is half of it.
        assertEquals(0.5f, SidebarMorph.progressFor(expanded = 300.dp, actual = 190.dp))
    }

    /** A spring can overshoot either end, and neither end may report more than it means. */
    @Test
    fun `progress is clamped past both ends`() {
        assertEquals(0f, SidebarMorph.progressFor(expanded = 300.dp, actual = 320.dp))
        assertEquals(1f, SidebarMorph.progressFor(expanded = 300.dp, actual = 40.dp))
    }

    /**
     * Only reachable if the stored width is ever allowed down to the rail's own. It is not — the drag
     * clamps to `SIDEBAR_MIN_WIDTH` — but a division by zero here would take the whole panel with it.
     */
    @Test
    fun `a degenerate travel does not divide by zero`() {
        assertEquals(1f, SidebarMorph.progressFor(expanded = SidebarMorph.RAIL_WIDTH, actual = SidebarMorph.RAIL_WIDTH))
        assertEquals(0f, SidebarMorph.progressFor(expanded = SidebarMorph.RAIL_WIDTH, actual = 300.dp))
    }

    /**
     * The whole of "make sure that all icons are in the same place when collapsed and expanded": an
     * expanded row insets its icon, a rail centres it, and the two agree only for this one inset.
     */
    @Test
    fun `the icon inset puts an icon on the centre of the rail`() {
        val centreOfIcon = SidebarMorph.ICON_INSET + SidebarMorph.ICON_SIZE / 2
        assertEquals(SidebarMorph.RAIL_WIDTH / 2, centreOfIcon)
    }

    /**
     * The order these two happen in is the point. Anything still visible when the layouts change hands
     * is something the eye can catch being cut, which is what a cross-dissolve looked like.
     */
    @Test
    fun `everything has finished fading before the layouts swap`() {
        assertTrue(SidebarMorph.FADE_DONE_AT < SidebarMorph.RAIL_SWAP_AT)
    }
}

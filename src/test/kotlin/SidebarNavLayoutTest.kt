import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.SidebarNavEntry
import com.alananasss.kittytune.data.local.parseSidebarNavLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class SidebarNavLayoutTest {

    @Test
    fun withNothingStoredTheOriginalRowsFollowTheOldHiddenSetAndExtrasStartOff() {
        val layout = parseSidebarNavLayout(null, legacyHidden = setOf(PlayerPreferences.SIDEBAR_NAV_EXPLORE))

        assertEquals(PlayerPreferences.SIDEBAR_NAV_ITEMS + PlayerPreferences.SIDEBAR_NAV_EXTRAS, layout.map { it.key })
        assertEquals(listOf(true, false, true, true, false, false, false), layout.map { it.isVisible })
    }

    @Test
    fun storedOrderAndVisibilityWinAndMissingRowsAreAppended() {
        val layout = parseSidebarNavLayout("stats,!feed,sync", legacyHidden = emptySet())

        assertEquals(SidebarNavEntry("stats", true), layout[0])
        assertEquals(SidebarNavEntry("feed", false), layout[1])
        assertEquals(SidebarNavEntry("sync", true), layout[2])
        assertEquals(7, layout.size)
    }

    @Test
    fun unknownAndDuplicateKeysAreDropped() {
        val layout = parseSidebarNavLayout("bogus,feed,!feed", legacyHidden = emptySet())

        assertEquals(1, layout.count { it.key == "feed" })
        assertEquals(true, layout.first { it.key == "feed" }.isVisible)
        assertEquals(false, layout.any { it.key == "bogus" })
    }
}

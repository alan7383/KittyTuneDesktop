import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.SidebarNavEntry
import com.alananasss.kittytune.data.local.parseSidebarNavLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarNavLayoutTest {

    @Test
    fun withNothingStoredTheDefaultRowsFollowTheOldHiddenSetAndExtrasStartOff() {
        val layout = parseSidebarNavLayout(null, legacyHidden = setOf(PlayerPreferences.SIDEBAR_NAV_EXPLORE))

        assertEquals(PlayerPreferences.SIDEBAR_NAV_ITEMS + PlayerPreferences.SIDEBAR_NAV_EXTRAS, layout.map { it.key })
        assertEquals(
            listOf(true, true, false, true, true) + List(PlayerPreferences.SIDEBAR_NAV_EXTRAS.size) { false },
            layout.map { it.isVisible },
        )
    }

    @Test
    fun storedOrderAndVisibilityWinAndHomeJoinsAnOlderLayoutFirst() {
        val layout = parseSidebarNavLayout("stats,!feed,sync", legacyHidden = emptySet())

        assertEquals(SidebarNavEntry("home", true), layout[0])
        assertEquals(SidebarNavEntry("stats", true), layout[1])
        assertEquals(SidebarNavEntry("feed", false), layout[2])
        assertEquals(SidebarNavEntry("sync", true), layout[3])
        assertEquals(PlayerPreferences.SIDEBAR_NAV_ITEMS.size + PlayerPreferences.SIDEBAR_NAV_EXTRAS.size, layout.size)
    }

    @Test
    fun unknownAndDuplicateKeysAreDropped() {
        val layout = parseSidebarNavLayout("bogus,feed,!feed", legacyHidden = emptySet())

        assertEquals(1, layout.count { it.key == "feed" })
        assertEquals(true, layout.first { it.key == "feed" }.isVisible)
        assertEquals(false, layout.any { it.key == "bogus" })
    }

    @Test
    fun aLayoutWithEverythingOffStillShowsHome() {
        val all = PlayerPreferences.SIDEBAR_NAV_ITEMS + PlayerPreferences.SIDEBAR_NAV_EXTRAS
        val layout = parseSidebarNavLayout(all.joinToString(",") { "!$it" }, legacyHidden = emptySet())

        assertTrue(layout.first { it.key == "home" }.isVisible)
        assertEquals(1, layout.count { it.isVisible })
    }
}

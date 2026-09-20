import com.alananasss.kittytune.data.lyrics.providers.DefaultLyricsProviderOrder
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.data.lyrics.providers.deserializeLyricsProviderOrder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualLyricsProviderListTest {

    @Test
    fun `all 15 providers are included in the manual search list without duplicates`() {
        val userOrder = DefaultLyricsProviderOrder
        val all = (userOrder + PreferredLyricsProvider.entries).distinct()

        assertEquals(15, all.size, "All 15 providers should be present in the manual provider selector")
        assertEquals(15, all.distinct().size, "No duplicate providers should exist in the list")
        assertTrue(all.contains(PreferredLyricsProvider.MUSIXMATCH))
        assertTrue(all.contains(PreferredLyricsProvider.BETTER_LYRICS))
        assertTrue(all.contains(PreferredLyricsProvider.LRCLIB))
        assertTrue(all.contains(PreferredLyricsProvider.GENIUS))
    }

    @Test
    fun `user preferred order places top choices first`() {
        val customOrder = listOf(
            PreferredLyricsProvider.LRCLIB,
            PreferredLyricsProvider.GENIUS
        )
        val all = (customOrder + PreferredLyricsProvider.entries).distinct()

        assertEquals(PreferredLyricsProvider.LRCLIB, all[0])
        assertEquals(PreferredLyricsProvider.GENIUS, all[1])
        assertEquals(15, all.size)
    }

    @Test
    fun `partitioning by enabled preserves ordering while moving enabled forward`() {
        val customOrder = listOf(
            PreferredLyricsProvider.GENIUS,
            PreferredLyricsProvider.LRCLIB,
            PreferredLyricsProvider.MUSIXMATCH
        )
        val all = (customOrder + PreferredLyricsProvider.entries).distinct()
        val enabledSet = setOf(PreferredLyricsProvider.LRCLIB, PreferredLyricsProvider.MUSIXMATCH)

        val (enabled, disabled) = all.partition { it in enabledSet }
        val finalOrder = enabled + disabled

        assertEquals(PreferredLyricsProvider.LRCLIB, finalOrder[0])
        assertEquals(PreferredLyricsProvider.MUSIXMATCH, finalOrder[1])
        assertEquals(PreferredLyricsProvider.GENIUS, finalOrder[2])
    }
}

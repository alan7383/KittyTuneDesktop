import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeScreenCardsSettingsTest {

    @Test
    fun `preferences define home cards keys and methods`() {
        assertEquals("show_home_listening_stats", PlayerPreferences.KEY_SHOW_HOME_LISTENING_STATS)
        assertEquals("show_home_your_mix", PlayerPreferences.KEY_SHOW_HOME_YOUR_MIX)
        assertEquals("show_home_my_wave", PlayerPreferences.KEY_SHOW_HOME_MY_WAVE)
        assertEquals("show_home_charts", PlayerPreferences.KEY_SHOW_HOME_CHARTS)

        val prefs = PlayerPreferences()
        // Save previous states
        val prevStats = prefs.getShowHomeListeningStats()
        val prevMix = prefs.getShowHomeYourMix()
        val prevWave = prefs.getShowHomeMyWave()
        val prevCharts = prefs.getShowHomeCharts()

        try {
            prefs.setShowHomeListeningStats(true)
            assertTrue(prefs.getShowHomeListeningStats())

            prefs.setShowHomeListeningStats(false)
            assertFalse(prefs.getShowHomeListeningStats())

            prefs.setShowHomeYourMix(true)
            assertTrue(prefs.getShowHomeYourMix())

            prefs.setShowHomeYourMix(false)
            assertFalse(prefs.getShowHomeYourMix())

            prefs.setShowHomeMyWave(false)
            assertFalse(prefs.getShowHomeMyWave())

            prefs.setShowHomeMyWave(true)
            assertTrue(prefs.getShowHomeMyWave())

            prefs.setShowHomeCharts(false)
            assertFalse(prefs.getShowHomeCharts())

            prefs.setShowHomeCharts(true)
            assertTrue(prefs.getShowHomeCharts())
        } finally {
            prefs.setShowHomeListeningStats(prevStats)
            prefs.setShowHomeYourMix(prevMix)
            prefs.setShowHomeMyWave(prevWave)
            prefs.setShowHomeCharts(prevCharts)
        }
    }

    @Test
    fun `home content conditionally renders the cards`() {
        val homeContentSource = File("src/main/kotlin/com/alananasss/kittytune/ui/main/HomeFeed.kt").readText()

        assertTrue(
            homeContentSource.contains("prefs.showHomeYourMixFlow()"),
            "HomeContent must observe showHomeYourMixFlow",
        )
        assertTrue(
            homeContentSource.contains("prefs.showHomeListeningStatsFlow()"),
            "HomeContent must observe showHomeListeningStatsFlow",
        )
        assertTrue(
            homeContentSource.contains("prefs.showHomeMyWaveFlow()"),
            "HomeContent must observe showHomeMyWaveFlow",
        )
        assertTrue(
            homeContentSource.contains("prefs.showHomeChartsFlow()"),
            "HomeContent must observe showHomeChartsFlow",
        )
        assertTrue(
            homeContentSource.contains("if (showHomeYourMix)"),
            "HomeContent must conditionally render StartMixingCard",
        )
        assertTrue(
            homeContentSource.contains("if (showHomeListeningStats)"),
            "HomeContent must conditionally render ListeningStatsCard",
        )
        assertTrue(
            homeContentSource.contains("if (showHomeMyWave)"),
            "HomeContent must conditionally render MyWaveCard",
        )
        assertTrue(
            homeContentSource.contains("if (showHomeCharts)"),
            "HomeContent must conditionally render HomeChartSection",
        )
        assertTrue(
            homeContentSource.contains("LaunchedEffect(showHomeCharts)"),
            "HomeContent must not load the chart preview while the section is hidden",
        )
    }

    @Test
    fun `appearance settings includes home screen cards group`() {
        val appearanceSource = File("src/main/kotlin/com/alananasss/kittytune/ui/profile/AppearanceSettingsScreen.kt").readText()

        assertTrue(
            appearanceSource.contains("HomeScreenCardsSettingsGroup()"),
            "ThemesSettingsPage must call HomeScreenCardsSettingsGroup()",
        )
        assertTrue(
            appearanceSource.contains("pref_home_cards_group_title"),
            "HomeScreenCardsSettingsGroup must use pref_home_cards_group_title",
        )
        assertTrue(
            appearanceSource.contains("pref_home_listening_stats_desc"),
            "HomeScreenCardsSettingsGroup must use pref_home_listening_stats_desc",
        )
        assertTrue(
            appearanceSource.contains("pref_home_your_mix_desc"),
            "HomeScreenCardsSettingsGroup must use pref_home_your_mix_desc",
        )
        assertTrue(
            appearanceSource.contains("pref_home_my_wave_desc"),
            "HomeScreenCardsSettingsGroup must use pref_home_my_wave_desc",
        )
        assertTrue(
            appearanceSource.contains("pref_home_charts_desc"),
            "HomeScreenCardsSettingsGroup must use pref_home_charts_desc",
        )
    }

    @Test
    fun `settings search catalog includes home screen cards items`() {
        val settingsSource = File("src/main/kotlin/com/alananasss/kittytune/ui/profile/SettingsScreen.kt").readText()

        assertTrue(
            settingsSource.contains("highlightKey = \"pref_home_listening_stats\""),
            "getSearchableSettings must include pref_home_listening_stats",
        )
        assertTrue(
            settingsSource.contains("highlightKey = \"pref_home_your_mix\""),
            "getSearchableSettings must include pref_home_your_mix",
        )
        assertTrue(
            settingsSource.contains("highlightKey = \"pref_home_my_wave\""),
            "getSearchableSettings must include pref_home_my_wave",
        )
        assertTrue(
            settingsSource.contains("highlightKey = \"pref_home_charts\""),
            "getSearchableSettings must include pref_home_charts",
        )
    }

    @Test
    fun `all language string resources contain home cards translations`() {
        val languages = listOf("en", "fr", "de", "hu", "ru", "vi", "tr")
        for (lang in languages) {
            val file = File("src/main/resources/i18n/strings-$lang.xml")
            assertTrue(file.exists(), "strings-$lang.xml must exist")
            val content = file.readText()
            assertTrue(
                content.contains("pref_home_cards_group_title"),
                "strings-$lang.xml must contain pref_home_cards_group_title",
            )
            assertTrue(
                content.contains("pref_home_listening_stats_desc"),
                "strings-$lang.xml must contain pref_home_listening_stats_desc",
            )
            assertTrue(
                content.contains("pref_home_your_mix_desc"),
                "strings-$lang.xml must contain pref_home_your_mix_desc",
            )
            assertTrue(
                content.contains("pref_home_my_wave_desc"),
                "strings-$lang.xml must contain pref_home_my_wave_desc",
            )
            assertTrue(
                content.contains("pref_home_charts_desc"),
                "strings-$lang.xml must contain pref_home_charts_desc",
            )
        }
    }
}

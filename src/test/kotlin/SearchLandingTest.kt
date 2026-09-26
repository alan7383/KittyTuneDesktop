package com.alananasss.kittytune

import com.alananasss.kittytune.data.RecentSearchRepository
import com.alananasss.kittytune.ui.home.ChartKind
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Point 19 of issue #56: the search landing is rebuilt around the chart, the searches already run
 * and the artists already liked, and the mood and genre chip walls are gone.
 */
class SearchLandingTest {

    private val languages = listOf("en", "fr", "de", "hu", "ru", "vi")

    private fun table(lang: String): Map<String, String> {
        val file = File("src/main/resources/i18n/strings-$lang.xml")
        assertTrue(file.exists(), "strings-$lang.xml should exist")
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            .getElementsByTagName("string")
        val out = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            out[el.getAttribute("name")] = el.textContent
        }
        return out
    }

    private fun source(path: String): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/$path")
        assertTrue(file.exists(), "$path should exist")
        return file.readText()
    }

    // ── Moods and genres are gone from the landing ──

    @Test
    fun testMoodAndGenreChipWallsAreGone() {
        val home = source("ui/main/HomeContent.kt")
        assertTrue(
            !home.contains("BrowseCategories"),
            "The mood/genre chip walls must be gone from the search screen",
        )
        val vm = source("ui/home/HomeViewModel.kt")
        assertTrue(
            !vm.contains("moodCategories") && !vm.contains("genreCategories"),
            "The static mood and genre lists must not be held for the landing any more",
        )
    }

    @Test
    fun testLandingShowsTheThreeNewShelves() {
        val landing = source("ui/home/SearchLanding.kt")
        assertTrue(landing.contains("RecentSearchesSection"), "Recent searches must be on the landing")
        assertTrue(landing.contains("ChartPreviewSection"), "A chart preview must be on the landing")
        assertTrue(landing.contains("home_from_your_artists"), "Liked artists' new songs must be on the landing")
        assertTrue(landing.contains("onOpenCharts"), "The landing must link to the full chart")
        assertTrue(landing.contains("onOpenNewReleases"), "The landing must link to new releases")
    }

    // ── Recent searches ──

    @Test
    fun testRecentSearchesAreRecordedWhenASearchRuns() {
        val vm = source("ui/home/HomeViewModel.kt")
        assertTrue(
            vm.contains("recordSearch(query)"),
            "A search must be recorded when it runs, not on every keystroke",
        )
        // Inside performSearch, i.e. after the debounce — the point is that a half-typed prefix
        // never becomes history.
        val body = vm.substringAfter("private suspend fun performSearch")
        assertTrue(
            body.contains("recordSearch(query)"),
            "Recording must sit in performSearch, so the debounce has already elapsed",
        )
    }

    @Test
    fun testRecentSearchesAreCappedAndDeduplicated() {
        assertEquals(20, RecentSearchRepository.MAX_ENTRIES)

        val dao = source("data/local/RecentSearchDao.kt")
        assertTrue(
            dao.contains("INSERT OR REPLACE INTO recent_search"),
            "The term is the primary key, so the same search must reorder one row, not stack two",
        )
        assertTrue(
            dao.contains("ORDER BY timestamp DESC LIMIT ?"),
            "The cap must keep the newest rows",
        )
        assertTrue(
            dao.contains("DELETE FROM recent_search WHERE query NOT IN"),
            "The cap must actually drop the rows past it",
        )
        assertTrue(
            dao.contains("db.execTogether"),
            "The insert and the prune are one transaction: a crash between them would leave 21 rows",
        )
    }

    @Test
    fun testRecentSearchesIgnorePrefixesTooShortToMeanAnything() {
        val repo = source("data/RecentSearchRepository.kt")
        assertTrue(
            repo.contains("MIN_LENGTH = 3"),
            "A one or two letter prefix must not be remembered",
        )
        assertTrue(
            repo.contains("if (term.length < MIN_LENGTH) return"),
            "record() must drop short terms before touching the database",
        )
    }

    @Test
    fun testRecentSearchesCollapseToThreeWithASeeMore() {
        val landing = source("ui/home/SearchLanding.kt")
        assertTrue(
            landing.contains("COLLAPSED_RECENT_SEARCHES = 3"),
            "The list must open collapsed, the way a long list of anything should",
        )
        assertTrue(landing.contains("search_see_more") && landing.contains("search_see_less"))
        assertTrue(landing.contains("search_clear_all"), "There must be a way to clear all of them")
        assertTrue(landing.contains("onForget"), "Each row must be deletable on its own")
    }

    // ── The song chart ──

    @Test
    fun testChartIsRankedAndOrderedByPosition() {
        val vm = source("ui/home/ChartsViewModel.kt")
        assertTrue(vm.contains("data class ChartEntry(val rank: Int"), "A chart row carries its rank")
        assertTrue(
            vm.contains("mapIndexed { index, entry -> entry.copy(rank = index + 1) }"),
            "The rank is the position in the server's order, never the score",
        )
        assertTrue(
            vm.contains("kind == chartKind && genre == chartGenre"),
            "A switch made mid-flight must not be overwritten by the older request's answer",
        )
        assertTrue(
            vm.contains("const val CHART_LENGTH = 50"),
            "A chart long enough to feel like one",
        )
    }

    @Test
    fun testChartOffersTopAndTrendingLikeSoundCloud() {
        val vm = source("ui/home/ChartsViewModel.kt")
        assertTrue(
            vm.contains("TOP(\"top\")") && vm.contains("TRENDING(\"trending\")"),
            "top and trending are the only two kinds, as in SoundCloud's own charts",
        )
        assertTrue(
            vm.contains("soundcloud:genres:all-music"),
            "all-music is SoundCloud's catch-all genre and belongs first",
        )
    }

    @Test
    fun testChartRowsArePlayableAsAQueue() {
        val screen = source("ui/home/ChartsScreen.kt")
        assertTrue(
            screen.contains("startIndex = index"),
            "Pressing a chart song must play the chart from there, not just that one song",
        )
        val landing = source("ui/home/SearchLanding.kt")
        assertTrue(
            landing.contains("startIndex = index"),
            "The landing's chart preview plays as a queue too",
        )
    }

    @Test
    fun testChartPodiumIsEmphasised() {
        val chart = source("ui/home/SongChart.kt")
        assertTrue(
            chart.contains("onPodium = rank <= 3"),
            "The top three are the ones a listener looks for and must read differently",
        )
        assertTrue(chart.contains("headlineSmall"), "The podium takes a larger step than the tail")
    }

    // ── New from your artists ──

    @Test
    fun testLikedArtistUpdatesSkipWhatIsAlreadyLiked() {
        val vm = source("ui/home/HomeViewModel.kt")
        assertTrue(vm.contains("fetchLikedArtistUpdates"), "The shelf has to be built from the liked list")
        assertTrue(
            vm.contains("it.id !in likedIds"),
            "A song already liked is not news, and would fill the shelf with what is already known",
        )
        assertTrue(
            vm.contains("ARTIST_UPDATE_SOURCES = 6"),
            "One request per artist, on the home screen's critical path: a handful, not all of them",
        )
    }

    // ── Localisation ──

    @Test
    fun testEveryNewStringIsTranslatedInEveryLanguage() {
        val keys = listOf(
            "search_recent_searches", "search_clear_all", "search_see_more", "search_see_less",
            "search_remove_recent", "chart_kind_top", "chart_kind_trending", "chart_see_full",
            "chart_genre_all", "chart_genre_pop", "chart_genre_hiphop", "chart_genre_electronic",
            "chart_genre_rock", "chart_genre_rnb", "chart_genre_country", "chart_genre_latin",
            "home_from_your_artists", "home_from_your_artists_sub",
        )
        for (lang in languages) {
            val strings = table(lang)
            for (key in keys) {
                val value = strings[key]
                assertTrue(!value.isNullOrBlank(), "Key '$key' must exist in strings-$lang.xml")
                assertTrue(value != key, "Key '$key' is untranslated in $lang")
            }
        }
    }

    @Test
    fun testFrenchReadsNaturally() {
        val fr = table("fr")
        assertEquals("Recherches récentes", fr["search_recent_searches"])
        assertEquals("Nouveautés de vos artistes", fr["home_from_your_artists"])
        assertEquals("Nouveautés du moment", fr["chart_kind_trending"])
    }

    @Test
    fun testChartKindsAreTheOnlyTwoEnumEntries() {
        assertEquals(2, ChartKind.entries.size)
        assertEquals("top", ChartKind.TOP.apiValue)
        assertEquals("trending", ChartKind.TRENDING.apiValue)
    }
}

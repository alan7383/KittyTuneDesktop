import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * The Top 50 chart, which arrives in two halves.
 *
 * `/resolve` is asked for one of SoundCloud's curated chart playlists and it embeds the first few
 * tracks whole and hands the rest back as a bare id — no title, no artist, no artwork, and a null
 * play count that the model reads as zero. Built straight off that, the screen showed five real
 * songs and then forty-five rows of "unknown title, unknown artist, 0 plays".
 *
 * The nameless ones are fetched by id and put back where the playlist had them. These are the two
 * things that has to keep being true: they are fetched at all, and the playlist's order survives.
 */
class ChartHydrationTest {

    private fun repository(): String = source("ui/home/ChartRepository.kt")

    private fun api(): String = source("data/network/SoundCloudApi.kt")

    private fun source(path: String): String {
        val file = File("src/main/kotlin/com/alananasss/kittytune/$path")
        assertTrue(file.exists(), "$path should exist")
        return file.readText()
    }

    @Test
    fun testTheTopChartFillsInTheNamelessTracks() {
        val repo = repository()
        assertTrue(
            repo.contains("fillInMissingTracks"),
            "The Top 50 comes back partly as bare ids and has to be completed",
        )
        assertTrue(
            repo.contains("it.title.isNullOrBlank()"),
            "A track with no title is one of the stubs",
        )
        assertTrue(
            repo.contains("api.getTracksByIds("),
            "…and it is asked for by id, which is the only way to get the rest",
        )
    }

    @Test
    fun testTheOrderIsThePlaylistsNotTheReplys() {
        val repo = repository()
        // /tracks?ids= answers in whatever order it likes. A chart's whole meaning is its order, so
        // the ids have to be put back where the playlist had them rather than appended.
        assertTrue(
            repo.contains(".associateBy { it.id }"),
            "The fetched tracks are keyed by id so they can be put back in the playlist's order",
        )
        assertTrue(
            repo.contains("listed.mapNotNull { entry ->") &&
                repo.contains("fetched[entry.id]"),
            "The walk is over the playlist's entries, not over the fetched ones",
        )
    }

    @Test
    fun testAStillNamelessTrackIsDroppedRatherThanShown() {
        val repo = repository()
        assertTrue(
            repo.contains("track?.takeIf { !it.title.isNullOrBlank() }"),
            "A track that is still nameless has been deleted or blocked; it is dropped instead of " +
                "becoming another row reading unknown",
        )
    }

    @Test
    fun testTheBatchesAreBounded() {
        val repo = repository()
        assertTrue(
            repo.contains("chunked(ID_BATCH)"),
            "Forty-five ids is one request, a full chart is not — the ids go in batches",
        )
        val batch = Regex("ID_BATCH = (\\d+)").find(repo)?.groupValues?.get(1)?.toIntOrNull()
        assertTrue(batch != null && batch in 1..100, "A sane batch size, was $batch")
    }

    @Test
    fun testAFailedFetchDoesNotLoseTheWholeChart() {
        val repo = repository()
        assertTrue(
            repo.contains("runCatching { api.getTracksByIds"),
            "One failed batch must not empty the chart: what resolved is still usable",
        )
    }

    @Test
    fun testTrendingIsNotPutThroughTheSameRepair() {
        // /charts?kind=trending answers with all fifty complete, and the repair costs an extra
        // request per chart. It should not be applied to something that does not need it.
        val repo = repository()
        val trending = repo.substringAfter("ChartKind.TRENDING ->").substringBefore("ChartKind.TOP ->")
        assertTrue(
            !trending.contains("getTracksByIds"),
            "The trending feed is whole already; hydrating it would be a wasted request",
        )
    }

    @Test
    fun testTheCommentSaysWhy() {
        // The truncation is not documented by SoundCloud and looks like a bug in this code if you
        // only read the diff, so the reason has to be written down.
        val repo = repository()
        assertTrue(
            repo.contains("embeds the first") || repo.contains("bare id"),
            "Why the nameless tracks are fetched again belongs next to the code that does it",
        )
    }
}

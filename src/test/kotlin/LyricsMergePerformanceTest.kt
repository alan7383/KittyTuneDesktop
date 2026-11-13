import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.ui.player.ManualLyricsSearch
import com.alananasss.kittytune.ui.player.UnifiedLyricResult
import java.text.Normalizer
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class LyricsMergePerformanceTest {
    private fun result(index: Int, title: String = "Night Drive $index") = UnifiedLyricResult(
        id = index.toString(), name = title, artistName = "Atlas & Écho", albumName = null,
        durationSec = 180.0 + index % 7, hasLineSync = index % 3 != 0,
        hasWordSync = index % 3 == 2, provider = "provider-${index % 6}",
    )

    @Test
    fun `each candidate is scored a bounded number of times`() {
        var reads = 0
        val alternatives = object : AbstractList<String>() {
            override val size = 1
            override fun get(index: Int): String { reads++; return "Night Drive" }
        }
        val input = (0 until 80).map(::result).shuffled(Random(72))
        val target = LyricsMatcher.Target("Night Drive", "Atlas & Écho", 180_000, alternatives)
        ManualLyricsSearch.merge(emptyList(), input, target)
        assertTrue(reads <= input.size * 3, "Scoring revisited target titles $reads times for ${input.size} results")
    }

    @Test
    fun `ordering and duplicate winners match the original implementation`() {
        val titles = listOf("Night Drive", "NIGHT DRIVE (Live)", "night drive [HQ]", "Été à Paris",
            "Stay With Me", "東京の夜", "Песня", "Night Drive feat. Guest", "", "🎵")
        val random = Random(193)
        repeat(40) {
            val input = (0 until 120).map { index ->
                result(index, titles[random.nextInt(titles.size)]).copy(
                    artistName = if (index % 4 == 0) "Another artist" else "Atlas & Écho",
                    durationSec = listOf(0.0, 180.0, 184.0, 179.0)[random.nextInt(4)],
                )
            }.shuffled(random)
            val target = LyricsMatcher.Target("Night Drive", "Atlas & Écho", 180_000,
                listOf("Night Drive (Official Audio)", "Drive at Night"), listOf("Atlas", "Écho"))
            assertEquals(originalMerge(input.take(60), input.drop(60), target),
                ManualLyricsSearch.merge(input.take(60), input.drop(60), target))
        }
    }

    @Test
    fun `normalization remains identical for punctuation accents scripts and credits`() {
        val inputs = listOf("NIGHT DRIVE (Live) [HQ] {demo}", "Été à Paris feat. Ana", "I İ ı i",
            "東京の夜", "Песня — ft. Гость", "a\tb\nc", "🎵", "", "[broken (bracket", "a prod. guest")
        for (input in inputs) assertEquals(originalNormalize(input), LyricsMatcher.normalize(input))
    }

    private fun originalNormalize(raw: String): String {
        var text = raw.lowercase()
        text = text.replace(Regex("\\[.*?\\]|\\(.*?\\)|\\{.*?\\}"), " ")
        text = text.replace(Regex("(?i)\\b(feat|ft|featuring|prod|w)\\.?\\s.*$"), " ")
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return text.replace(Regex("[^\\p{L}\\p{Nd}]+"), " ").trim().replace(Regex("\\s+"), " ")
    }

    private fun originalMerge(current: List<UnifiedLyricResult>, incoming: List<UnifiedLyricResult>,
                              target: LyricsMatcher.Target): List<UnifiedLyricResult> {
        val targetSec = target.durationMs / 1000.0
        fun tier(result: UnifiedLyricResult) = ManualLyricsSearch.syncTier(result)
        fun rank(result: UnifiedLyricResult) = LyricsMatcher.rank(tier(result),
            LyricsMatcher.score(result.name, result.artistName, result.durationSec, target),
            LyricsMatcher.titleSimilarity(result.name, target))
        return (current + incoming).groupBy {
            listOf(it.provider, LyricsMatcher.normalize(it.name), LyricsMatcher.normalize(it.artistName),
                tier(it).toString()).joinToString("|")
        }.values.map { copies -> copies.minBy {
            if (it.durationSec <= 0.0 || targetSec <= 0.0) Double.MAX_VALUE / 2 else kotlin.math.abs(it.durationSec - targetSec)
        } }.sortedWith(compareByDescending<UnifiedLyricResult> {
            LyricsMatcher.titleSimilarity(it.name, target) >= LyricsMatcher.CONFIDENT_MATCH
        }.thenByDescending { tier(it) }.thenByDescending { rank(it) })
    }
}

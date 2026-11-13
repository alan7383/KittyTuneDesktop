import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.ui.player.ManualLyricsSearch
import com.alananasss.kittytune.ui.player.UnifiedLyricResult
import java.awt.EventQueue
import java.lang.management.ManagementFactory
import kotlin.random.Random

/** Run in fresh JVMs; timing observations belong in the report, not in CI assertions. */
object LyricsMergeBenchmark {
    @Volatile private var sink = 0L

    @JvmStatic fun main(args: Array<String>) {
        val label = args.firstOrNull() ?: "unknown"
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val target = LyricsMatcher.Target("Night Drive", "Atlas & Écho", 180_000,
            listOf("Night Drive (Official Audio)", "Drive at Night"), listOf("Atlas", "Écho"))
        for (count in listOf(24, 96, 240)) {
            val batches = fixtures(count).chunked(count / 6)
            fun burst(): LongArray {
                var current = emptyList<UnifiedLyricResult>()
                var totalWall = 0L
                var totalCpu = 0L
                var totalBytes = 0L
                var longest = 0L
                var overBudget = 0L
                EventQueue.invokeAndWait {
                    for (batch in batches) {
                        val thread = Thread.currentThread().threadId()
                        val bytes = bean.getThreadAllocatedBytes(thread)
                        val cpu = bean.currentThreadCpuTime
                        val start = System.nanoTime()
                        current = ManualLyricsSearch.merge(current, batch, target)
                        val wall = System.nanoTime() - start
                        totalWall += wall
                        totalCpu += bean.currentThreadCpuTime - cpu
                        totalBytes += bean.getThreadAllocatedBytes(thread) - bytes
                        longest = maxOf(longest, wall)
                        if (wall > 16_666_667) overBudget++
                    }
                    sink = current.fold(1L) { hash, result -> hash * 31 + result.id.hashCode() }
                }
                return longArrayOf(totalWall, totalCpu, totalBytes, longest, overBudget, sink)
            }
            repeat(12) { burst() }
            repeat(25) { iteration ->
                val values = burst()
                println("$label,$count,$iteration,${values.joinToString(",")}")
            }
        }
    }

    private fun fixtures(count: Int): List<UnifiedLyricResult> {
        val titles = listOf("Night Drive", "Night Drive (Live)", "NIGHT DRIVE [HQ]",
            "Stay With Me", "Été à Paris", "Tokyo Night", "Drive at Night", "Night Shift")
        val random = Random(913 + count)
        return (0 until count).map { index ->
            UnifiedLyricResult(id = index.toString(), name = titles[index % titles.size] +
                if (index % 4 == 0) "" else " ${index % 17}",
                artistName = if (index % 5 == 0) "Another Artist" else "Atlas & Écho",
                albumName = "Test Album", durationSec = if (index % 11 == 0) 0.0 else 177.0 + index % 9,
                hasLineSync = index % 3 != 0, hasWordSync = index % 3 == 2,
                provider = "provider-${index % 6}", rawContent = "[00:01.00] Deterministic fixture\n".repeat(24))
        }.shuffled(random)
    }
}

package com.alananasss.kittytune.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Screen-by-screen recomposition counter for the perf hunt (issue #66).
 *
 * Dormant unless the app starts with `KITTYTUNE_RECOMP_TRACE=1`: one volatile read per
 * traced composition, nothing allocated, nothing logged. When on, a daemon thread prints
 * per-screen recomposition deltas every 15 s, so an idle app that still recomposes a
 * screen several times a second points straight at the churning state.
 */
object RecompositionTracer {
    val enabled: Boolean = System.getenv("KITTYTUNE_RECOMP_TRACE") == "1"

    private val counts = ConcurrentHashMap<String, AtomicLong>()
    private val lastPrinted = ConcurrentHashMap<String, Long>()

    init {
        if (enabled) {
            Thread({
                while (true) {
                    try {
                        Thread.sleep(15_000)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                    report()
                }
            }, "recomp-tracer").apply { isDaemon = true }.start()
        }
    }

    fun hit(tag: String) {
        if (!enabled) return
        counts.computeIfAbsent(tag) { AtomicLong() }.incrementAndGet()
    }

    private fun report() {
        val lines = counts.map { (tag, total) ->
            val now = total.get()
            val prev = lastPrinted.put(tag, now) ?: 0L
            Triple(tag, now - prev, now)
        }.sortedByDescending { it.second }
        println("[recomp-trace] recompositions in the last 15 s (total):")
        lines.forEach { (tag, delta, total) ->
            println("[recomp-trace]   $tag: +$delta (${"%.1f".format(delta / 15.0)}/s, total $total)")
        }
    }
}

/**
 * Count recompositions of the enclosing screen. One line at the top of a screen root;
 * see [RecompositionTracer] for how to read the numbers.
 */
@Composable
fun TraceRecompositions(tag: String) {
    if (RecompositionTracer.enabled) {
        SideEffect { RecompositionTracer.hit(tag) }
    }
}

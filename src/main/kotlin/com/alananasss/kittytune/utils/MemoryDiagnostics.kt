package com.alananasss.kittytune.utils

import com.alananasss.kittytune.core.AppDirs
import java.io.File
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.management.ObjectName
import kotlin.concurrent.thread

/**
 * Writes down where the memory actually goes, for the reports of 1 GB and occasionally 4 GB in
 * issue #33.
 *
 * ## Why this exists rather than a question
 *
 * The Java heap is capped at 2 GB by the launcher options, so an app reaching 4 GB has at least half
 * of it somewhere else: native allocations by the audio decoder, GPU surfaces, thread stacks, direct
 * buffers. Those are unrelated bugs with unrelated fixes, and no figure a task manager shows can tell
 * them apart. The JVM can, through native memory tracking, but only if it was asked at startup, and
 * only through a diagnostic command that normally needs a JDK and a terminal.
 *
 * So the diagnostic build asks for tracking on the command line and this reads the answer from
 * inside the process, on a timer, into a file somebody can attach to a comment. Nothing to install,
 * nothing to type.
 *
 * Off unless the launcher sets `kittytune.memlog`, so a normal build neither pays for it nor writes
 * anything. Native memory tracking itself costs a few percent, which is why it is not simply always
 * on.
 */
object MemoryDiagnostics {

    private const val ENABLE_PROPERTY = "kittytune.memlog"

    /**
     * How often the cheap numbers are read, and how often the full account is written.
     *
     * Two rates because of what was reported: "I just listened to a song, and then it suddenly froze
     * 1 sec and used 3000-4000 MB". A minute between samples would have every chance of falling
     * either side of an event that lasts a second, so the heap and the process size are read every
     * few seconds, peaks are carried forward, and a jump writes the full account immediately rather
     * than waiting for the next minute.
     */
    private const val POLL_MS = 5_000L
    private const val FULL_REPORT_MS = 60_000L

    /** A jump this big since the last full account is treated as the event we are hunting. */
    private const val SPIKE_MB = 256L

    /** A collection longer than this is the freeze somebody feels, so each one is written down. */
    private const val SLOW_GC_MS = 200L

    /** Kept next to the settings rather than in a cache, so clearing the cache cannot delete it. */
    val logFile: File get() = File(AppDirs.dataDir, "memory.log")

    val isEnabled: Boolean get() = System.getProperty(ENABLE_PROPERTY) != null

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun start() {
        if (!isEnabled) return

        // A plain daemon thread. This has to keep reporting while the app is busy or wedged, which is
        // exactly when a coroutine on a shared dispatcher might not get a turn.
        thread(isDaemon = true, name = "memory-diagnostics") {
            append(header())
            var lastFullMs = 0L
            var baselineMb = 0L
            while (true) {
                runCatching {
                    val nowMb = maxOf(heapCommittedMb(), processMb())
                    trackPeaks()
                    noteSlowCollections()

                    val jumped = baselineMb > 0 && nowMb - baselineMb >= SPIKE_MB
                    val due = System.currentTimeMillis() - lastFullMs >= FULL_REPORT_MS
                    if (jumped || due) {
                        append(report(if (jumped) "jumped ${nowMb - baselineMb} MB" else "periodic"))
                        lastFullMs = System.currentTimeMillis()
                        baselineMb = nowMb
                    }
                }
                Thread.sleep(POLL_MS)
            }
        }

        Runtime.getRuntime().addShutdownHook(
            Thread { runCatching { append(report("shutdown")) } }
        )
    }

    /** Also callable by hand, so a report can be pinned to the moment something was observed. */
    fun snapshot(label: String) {
        if (!isEnabled) return
        runCatching { append(report(label)) }
    }

    private fun header(): String = buildString {
        appendLine("=".repeat(78))
        appendLine("KittyTune memory diagnostics")
        appendLine("started      ${stamp.format(Date())}")
        appendLine("version      ${com.alananasss.kittytune.BuildConfig.VERSION_NAME}")
        appendLine("os           ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
        appendLine("jvm          ${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}")
        appendLine("cpus         ${Runtime.getRuntime().availableProcessors()}")
        appendLine("max heap     ${mb(Runtime.getRuntime().maxMemory())} MB")
        appendLine("vm options   ${vmOptions()}")
        appendLine("=".repeat(78))
    }

    private fun report(reason: String): String = buildString {
        val rt = Runtime.getRuntime()
        val uptimeMin = ManagementFactory.getRuntimeMXBean().uptime / 60_000
        appendLine()
        appendLine("---- ${stamp.format(Date())}  ($reason, up ${uptimeMin} min) ----")

        // The Java half. If this stays small while the total does not, the problem is below.
        appendLine("heap used ${mb(rt.totalMemory() - rt.freeMemory())} MB / committed ${mb(rt.totalMemory())} MB / max ${mb(rt.maxMemory())} MB")
        // What was seen between reports, which is where a one-second event lives.
        appendLine("peak since last report: heap used ${peakHeapUsedMb} MB, heap committed ${peakHeapCommittedMb} MB, process ${peakProcessMb} MB")
        resetPeaks()
        val nonHeap = ManagementFactory.getMemoryMXBean().nonHeapMemoryUsage
        appendLine("non-heap used ${mb(nonHeap.used)} MB / committed ${mb(nonHeap.committed)} MB")
        appendLine("threads ${ManagementFactory.getThreadMXBean().threadCount}")
        appendLine("gc " + ManagementFactory.getGarbageCollectorMXBeans().joinToString(", ") {
            "${it.name}: ${it.collectionCount} collections, ${it.collectionTime} ms"
        })

        // What the operating system charges the process, which is the number in a task manager.
        processMemory()?.let { appendLine(it) }

        // The payload: the JVM's own account of every native allocation, by subsystem.
        appendLine()
        appendLine(diagnosticCommand("vmNativeMemory", "summary") ?: "native memory tracking is off")
    }

    /**
     * Runs one of the commands `jcmd` would run, in-process.
     *
     * The diagnostic command MBean is the same entry point `jcmd` uses, so this needs no JDK
     * alongside the app and no terminal. It comes from `jdk.management`, which the packaged runtime
     * therefore has to include.
     */
    private fun diagnosticCommand(operation: String, vararg args: String): String? = runCatching {
        val server = ManagementFactory.getPlatformMBeanServer()
        val name = ObjectName("com.sun.management:type=DiagnosticCommand")
        server.invoke(
            name,
            operation,
            arrayOf<Any>(args),
            arrayOf("[Ljava.lang.String;"),
        ) as? String
    }.getOrNull()

    /** Resident and virtual size where the platform will say, which is Linux for free and Windows via the JVM. */
    private fun processMemory(): String? {
        val status = File("/proc/self/status")
        if (status.exists()) {
            val wanted = setOf("VmRSS", "VmHWM", "VmSize")
            val lines = runCatching {
                status.readLines().filter { line -> wanted.any { line.startsWith("$it:") } }
            }.getOrNull()
            if (!lines.isNullOrEmpty()) return "process " + lines.joinToString(", ") { it.replace(Regex("\\s+"), " ").trim() }
        }
        // Windows and macOS: no /proc, so this is what the platform bean will admit to.
        return runCatching {
            val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
            "process committed virtual ${mb(os.committedVirtualMemorySize)} MB, " +
                "system free ${mb(os.freeMemorySize)} MB of ${mb(os.totalMemorySize)} MB"
        }.getOrNull()
    }

    private fun vmOptions(): String = runCatching {
        ManagementFactory.getRuntimeMXBean().inputArguments.joinToString(" ")
    }.getOrDefault("unknown")


    // ---- the cheap numbers, read far more often than they are written ------------------------

    private var peakHeapUsedMb = 0L
    private var peakHeapCommittedMb = 0L
    private var peakProcessMb = 0L

    private fun heapCommittedMb(): Long = mb(Runtime.getRuntime().totalMemory())

    private fun heapUsedMb(): Long {
        val rt = Runtime.getRuntime()
        return mb(rt.totalMemory() - rt.freeMemory())
    }

    /** Resident size on Linux, committed virtual elsewhere. Both are cheap enough to read often. */
    private fun processMb(): Long = runCatching {
        val status = File("/proc/self/status")
        if (status.exists()) {
            val rss = status.readLines().firstOrNull { it.startsWith("VmRSS:") }
            if (rss != null) return@runCatching rss.filter { it.isDigit() }.toLong() / 1024
        }
        val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        mb(os.committedVirtualMemorySize)
    }.getOrDefault(0L)

    private fun trackPeaks() {
        peakHeapUsedMb = maxOf(peakHeapUsedMb, heapUsedMb())
        peakHeapCommittedMb = maxOf(peakHeapCommittedMb, heapCommittedMb())
        peakProcessMb = maxOf(peakProcessMb, processMb())
    }

    private fun resetPeaks() {
        peakHeapUsedMb = 0
        peakHeapCommittedMb = 0
        peakProcessMb = 0
    }

    /**
     * Writes down every collection long enough to be heard as a gap.
     *
     * "It suddenly froze 1 sec" is a pause, and a pause has a length, a cause and a heap size either
     * side of it. The platform keeps the last one per collector, so this checks by id and writes each
     * one once. Nothing here needs a log file or a flag.
     */
    private val lastSeenGcId = HashMap<String, Long>()

    private fun noteSlowCollections() {
        for (bean in ManagementFactory.getGarbageCollectorMXBeans()) {
            val sun = bean as? com.sun.management.GarbageCollectorMXBean ?: continue
            val info = runCatching { sun.lastGcInfo }.getOrNull() ?: continue
            if (lastSeenGcId[bean.name] == info.id) continue
            lastSeenGcId[bean.name] = info.id
            if (info.duration < SLOW_GC_MS) continue
            val before = runCatching { info.memoryUsageBeforeGc.values.sumOf { it.used } }.getOrDefault(0L)
            val after = runCatching { info.memoryUsageAfterGc.values.sumOf { it.used } }.getOrDefault(0L)
            append(
                "\n!! ${stamp.format(Date())}  ${bean.name} paused ${info.duration} ms" +
                    " (heap ${mb(before)} MB before, ${mb(after)} MB after)\n"
            )
        }
    }

    private fun mb(bytes: Long): Long = bytes / (1024 * 1024)

    private fun append(text: String) {
        runCatching {
            // Truncated rather than rotated: one long session is what needs reading, and an
            // unbounded file on someone else's machine is not ours to grow.
            if (logFile.exists() && logFile.length() > MAX_LOG_BYTES) {
                logFile.writeText("(earlier entries dropped, the log had reached ${mb(MAX_LOG_BYTES)} MB)\n")
            }
            logFile.appendText(text)
        }
    }

    private const val MAX_LOG_BYTES = 8L * 1024 * 1024
}

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

    /**
     * Names of the collectors whose every collection is written down, however short.
     *
     * G1 does its old-generation work concurrently; a *full* collection is the fallback for when that
     * could not keep up, and it stops the world. So the count matters even when the duration does not.
     * Two logs went by with this hidden behind [SLOW_GC_MS]: the pauses were 25 to 66 ms at the heap
     * sizes those sessions reached, so nothing was written, and the fact that the app was doing two full
     * collections a minute from startup — which is the thing that was wrong — only showed up as a
     * cumulative counter nobody had a reason to read closely (issue #33).
     */
    private val ALWAYS_LOGGED_COLLECTORS = setOf("G1 Old Generation", "PS MarkSweep", "MarkSweepCompact")

    /** Kept next to the settings rather than in a cache, so clearing the cache cannot delete it. */
    val logFile: File get() = File(AppDirs.dataDir, "memory.log")

    val isEnabled: Boolean get() = System.getProperty(ENABLE_PROPERTY) != null

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * The recording behind [retentionStacks], held for the life of the process.
     *
     * One event and nothing else, so this is a sampling queue rather than a profiler: JFR's own overhead
     * here is a few objects per collection, and it replaces the heap dump that would otherwise be the
     * only way to answer the question.
     */
    private var recording: jdk.jfr.Recording? = null

    private fun startOldObjectSampling() {
        recording = runCatching {
            jdk.jfr.Recording().apply {
                name = "kittytune-retention"
                // `cutoff` of zero means every sample the collector kept, rather than only objects past
                // some age: the retention being hunted is acquired in one thirty-second window, and an
                // age filter is exactly the wrong instrument for that.
                enable("jdk.OldObjectSample").withStackTrace().with("cutoff", "0 ns")
                // Never write a chunk on its own. The only thing that ever reads this is a dump taken at
                // the moment a jump is noticed, so an unbounded file on somebody else's disk would be
                // pure cost.
                setToDisk(false)
                maxSize = RECORDING_MAX_BYTES
                start()
            }
        }.getOrNull()
    }

    /** Enough for the sampling queue and its stacks, small enough to be invisible. */
    private const val RECORDING_MAX_BYTES = 16L * 1024 * 1024

    fun start() {
        if (!isEnabled) return
        startOldObjectSampling()

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
        appendLine(nativeLibraryMemory())

        // The payload: the JVM's own account of every native allocation, by subsystem.
        appendLine()
        appendLine(diagnosticCommand("vmNativeMemory", "summary") ?: "native memory tracking is off")

        // And, on a jump, what is actually holding the heap, and the code that put it there.
        if (reason.startsWith("jumped")) {
            appendLine()
            appendLine(classHistogram() ?: "class histogram unavailable")
            appendLine()
            appendLine(retentionStacks() ?: "old object sampling unavailable")
        }
    }

    /**
     * The code that allocated the objects still alive — the question a histogram cannot answer.
     *
     * Two logs now have said the same thing about the same heap: 2.7 million live strings inside 27,000
     * object arrays, and every KittyTune class in the process adding up to about 700 KB. Our own data is
     * innocent and the strings sit in containers the JDK supplies and our code fills, so naming the
     * container names nothing. What is needed is the stack that allocated them.
     *
     * That is exactly what JFR's old-object sample is: the collector keeps a bounded queue of sampled
     * objects that have survived, each with the stack trace of its allocation, and dumping the recording
     * asks for the ones still reachable at that moment. It is the standard answer to "what is leaking",
     * it costs a sampling queue rather than a heap dump, and `jdk.jfr` is already in the packaged
     * runtime.
     *
     * Summarised here rather than left as a file to send, because a stack trace read by the person who
     * can fix it on the same day beats a 3 MB attachment. The raw recording is kept beside the log all
     * the same, in case the summary points somewhere ambiguous and the frames underneath it matter.
     *
     * Verified against a deliberate leak of 1800 retained string arrays: 228 samples, and the method that
     * built them named on the second line. The first line was `StringConcatHelper.newArray`, which is why
     * [originOf] pulls our own frames to the front — the frame that did the allocating is a library one
     * every time, and it is never the frame to change.
     */
    private fun retentionStacks(): String? {
        val active = recording ?: return null
        return runCatching {
            val dump = File(AppDirs.dataDir, "retention.jfr")
            active.dump(dump.toPath())

            // Keyed on the allocated type plus the frames of ours that led to it. Two allocations of the
            // same type from the same place in our code are one finding, however deep the library
            // machinery between them goes.
            val byOrigin = HashMap<String, Int>()
            var sampled = 0
            jdk.jfr.consumer.RecordingFile(dump.toPath()).use { file ->
                while (file.hasMoreEvents()) {
                    val event = runCatching { file.readEvent() }.getOrNull() ?: break
                    if (event.eventType.name != "jdk.OldObjectSample") continue
                    sampled++
                    byOrigin.merge(originOf(event), 1, Int::plus)
                }
            }

            if (sampled == 0) {
                "Surviving objects by allocation site: nothing sampled yet (the queue fills as the heap grows)"
            } else buildString {
                appendLine("Surviving objects by allocation site ($sampled sampled), most first:")
                byOrigin.entries
                    .sortedByDescending { it.value }
                    .take(RETENTION_LINES)
                    .forEach { (origin, count) -> appendLine("  ${count.toString().padStart(5)}  $origin") }
                appendLine("  (full recording: ${dump.name}, beside this log)")
            }
        }.getOrNull()
    }

    /** One line naming what was allocated and the frames of ours that asked for it. */
    private fun originOf(event: jdk.jfr.consumer.RecordedEvent): String {
        val type = runCatching { event.getClass("object.type")?.name }.getOrNull() ?: "?"
        val frames = runCatching { event.stackTrace?.frames.orEmpty() }.getOrElse { emptyList() }
        val described = frames.mapNotNull { frame ->
            runCatching { "${frame.method.type.name}.${frame.method.name}" }.getOrNull()
        }
        // Ours first, because the library frame that did the allocating is never the one to change.
        // Falling back to the top of the stack when none of it is ours, since "not our code at all" is
        // itself the answer in that case.
        val ours = described.filter { it.startsWith(APP_PACKAGE) }
        val shown = (if (ours.isNotEmpty()) ours else described).take(RETENTION_FRAMES)
        return if (shown.isEmpty()) type else "$type  <-  " + shown.joinToString("  <-  ")
    }

    /** Enough distinct sites to see a pattern, not enough to bury the first one. */
    private const val RETENTION_LINES = 15

    /** Three frames is normally the call, its caller and the reason. */
    private const val RETENTION_FRAMES = 3

    /**
     * The classes holding the heap, largest first — the question the second log left open.
     *
     * That log settled what this is not. Over 81 minutes javacpp held 0 MB across 42 allocations, so
     * the audio decoder is not involved; then at minute 33 the *Java* heap jumped by a gigabyte and
     * stayed there, with thirteen full collections over the next hour each pausing 200 to 411 ms and
     * each leaving 1.2 GB behind. Objects that survive thirteen full collections are reachable. So
     * something is retaining them, and a summary by subsystem cannot say what: only a count by class
     * can (issue #33).
     *
     * Trimmed to the head of the list, because the tail is every class in the JVM and the answer is
     * always in the first few lines. Emitted only on a jump: it forces a full collection of its own,
     * which is not something to do to somebody every minute.
     */
    private fun classHistogram(): String? {
        val raw = diagnosticCommand("gcClassHistogram") ?: return null
        val lines = raw.lines()
        return buildString {
            appendLine("Heap by class, largest first:")
            lines.take(HISTOGRAM_LINES).forEach { appendLine(it) }
            val remaining = lines.size - HISTOGRAM_LINES
            if (remaining > 0) appendLine("... and $remaining more classes")

            // And the same list with everything but this app's own types removed.
            //
            // The first histogram we got back was 2.8 million strings in 2.5 million byte arrays inside
            // 36,000 object arrays, and exactly one line of the top forty-five belonged to KittyTune. That
            // is a true answer to "what is on the heap" and no answer at all to "who put it there": the
            // strings are held by containers the JDK supplies and this app fills. Whichever of our own
            // classes is unexpectedly numerous names the subsystem, and it is never in the top forty-five
            // because our objects are large and few where theirs are small and many (issue #33).
            val ours = lines.filter { it.contains(APP_PACKAGE) }
            appendLine()
            appendLine("Of those, KittyTune's own classes:")
            if (ours.isEmpty()) appendLine(" (none in the histogram)")
            else ours.take(APP_HISTOGRAM_LINES).forEach { appendLine(it) }
        }
    }

    /** Enough to name the culprit and its container, not enough to bury them. */
    private const val HISTOGRAM_LINES = 45

    /** The app's own types are the short list, so it can afford to be longer. */
    private const val APP_HISTOGRAM_LINES = 25

    private const val APP_PACKAGE = "com.alananasss.kittytune"

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


    /**
     * The half of the process that native memory tracking cannot see.
     *
     * The first log from a real session settled the easy question and raised the real one: the heap
     * never passed 217 MB, everything the JVM accounts for actually *shrank* by 28 MB, and the process
     * still grew by 692 MB. At the high point, 733 MB of it was memory the JVM had no record of.
     *
     * That is expected rather than mysterious. Tracking covers allocations the JVM makes; it says
     * nothing about what a library allocates for itself, and this app links two that allocate
     * heavily: Skia for drawing and FFmpeg for decoding. So the numbers below come from javacpp, which
     * is the layer every FFmpeg buffer in this app is allocated through and keeps its own account.
     *
     * `physicalBytes` is the whole process footprint as the operating system sees it, which is finally
     * the same number a task manager shows. `totalBytes` is what javacpp itself holds: if that climbs
     * with track changes and never comes back down, the decoder is not being released, and the search
     * is over.
     */
    private fun nativeLibraryMemory(): String = runCatching {
        val physical = org.bytedeco.javacpp.Pointer.physicalBytes()
        val tracked = org.bytedeco.javacpp.Pointer.totalBytes()
        val count = org.bytedeco.javacpp.Pointer.totalCount()
        "native libraries: process physical ${mb(physical)} MB, javacpp holds ${mb(tracked)} MB " +
            "across $count allocations"
    }.getOrElse { "native libraries: unavailable (${it::class.simpleName})" }

    // ---- the cheap numbers, read far more often than they are written ------------------------

    private var peakHeapUsedMb = 0L
    private var peakHeapCommittedMb = 0L
    private var peakProcessMb = 0L

    private fun heapCommittedMb(): Long = mb(Runtime.getRuntime().totalMemory())

    private fun heapUsedMb(): Long {
        val rt = Runtime.getRuntime()
        return mb(rt.totalMemory() - rt.freeMemory())
    }

    /**
     * The process footprint, by whichever route gives the truest figure.
     *
     * javacpp asks the operating system the same question a task manager does, on every platform, so
     * it goes first. The first log came out reporting committed virtual memory instead, which on
     * Windows is a larger and less comparable number than the one anybody reads off the screen.
     */
    private fun processMb(): Long = runCatching {
        val physical = org.bytedeco.javacpp.Pointer.physicalBytes()
        if (physical > 0) return@runCatching mb(physical)
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
            if (info.duration < SLOW_GC_MS && bean.name !in ALWAYS_LOGGED_COLLECTORS) continue
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

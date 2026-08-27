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
    private val INTERVAL_MS = 60_000L

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
            while (true) {
                runCatching { append(report("periodic")) }
                Thread.sleep(INTERVAL_MS)
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

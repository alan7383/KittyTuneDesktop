package com.alananasss.kittytune.utils

import com.alananasss.kittytune.core.AppDirs
import java.io.File
import java.lang.management.ManagementFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The start-up timeline, for the one question numbers like "it takes two seconds" cannot answer: which
 * two seconds.
 *
 * Off unless asked for — `-Dkittytune.startuplog=true` or `KITTYTUNE_STARTUP_LOG=1` — because a normal
 * launch should not open files or measure anything. When it is on, every mark writes one line with the
 * time since the process was created and since the JVM started, so the launcher's own share (loading the
 * JVM, the classpath, Skiko) is separate from the application's (config, database, the first frame).
 *
 * Written to `startup.log` beside the settings as well as to stderr: the packaged launcher has no console,
 * and a log that only exists when started from a terminal is a log nobody has.
 */
object StartupTimeline {

    private const val PROPERTY = "kittytune.startuplog"
    private const val ENV = "KITTYTUNE_STARTUP_LOG"

    val isEnabled: Boolean by lazy {
        System.getProperty(PROPERTY) != null || System.getenv(ENV) != null
    }

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** When the operating system created the process: everything the JVM did before `main` is below. */
    private val processStartMs: Long by lazy {
        runCatching { ProcessHandle.current().info().startInstant().orElse(null)?.toEpochMilli() }
            .getOrNull() ?: ManagementFactory.getRuntimeMXBean().startTime
    }

    /** When the JVM started, which on Windows is a moment after the process existed. */
    private val jvmStartMs: Long by lazy {
        runCatching { ManagementFactory.getRuntimeMXBean().startTime }.getOrDefault(processStartMs)
    }

    private val firstMarkMs = System.currentTimeMillis()
    private var marks = 0
    private val seen = HashSet<String>()

    val logFile: File get() = File(AppDirs.dataDir, "startup.log")

    /** Records one step the first time it happens, for marks inside composables that recompose. */
    fun markOnce(label: String) {
        if (!isEnabled) return
        if (!seen.add(label)) return
        mark(label)
    }

    /** Records one step. Cheap enough to leave in the code paths it marks. */
    fun mark(label: String) {
        if (!isEnabled) return
        runCatching {
            val now = System.currentTimeMillis()
            val line = buildString {
                if (marks++ == 0) {
                    appendLine()
                    appendLine("==== ${stamp.format(Date(now))}  ${com.alananasss.kittytune.BuildConfig.VERSION_NAME} ====")
                    appendLine(
                        "   jvm started    +${jvmStartMs - processStartMs} ms after the process," +
                            " ${firstMarkMs - jvmStartMs} ms before this class was first used"
                    )
                }
                appendLine(
                    "   +${pad(now - processStartMs, 5)} ms process" +
                        "  +${pad(now - jvmStartMs, 5)} ms jvm" +
                        "  +${pad(now - firstMarkMs, 5)} ms since first mark   $label"
                )
            }
            logFile.appendText(line)
            System.err.print(line)
            System.err.flush()
        }
    }

    private fun pad(value: Long, width: Int) = value.toString().padStart(width)

    /**
     * Follows the gap between "the first frame was drawn" and "the window is on screen": marks when the
     * window becomes visible, and records what the AWT thread was doing while it was not.
     *
     * A thread of its own, because the whole question is whether the AWT thread is busy. If the samples
     * are all `EventQueue.getNextEvent`, the work is on Skiko's render thread and this is the first paint;
     * if they are something of ours, that is a function to move off the start-up path.
     */
    fun traceUntilWindowVisible(window: java.awt.Window, timeoutMs: Long = 8_000) {
        if (!isEnabled) return
        Thread({
            runCatching {
                val deadline = System.currentTimeMillis() + timeoutMs
                val samples = LinkedHashMap<String, Long>()
                while (!window.isShowing && System.currentTimeMillis() < deadline) {
                    val stack = awtThreadStack()
                    if (stack != null) samples.putIfAbsent(stack, System.currentTimeMillis())
                    Thread.sleep(50)
                }
                if (window.isShowing) {
                    markOnce("window is on screen (showing)")
                } else {
                    markOnce("window never showed within ${timeoutMs} ms")
                }
                if (samples.isNotEmpty()) {
                    val text = buildString {
                        appendLine("   --- AWT thread while the window was not on screen ---")
                        samples.forEach { (stack, at) ->
                            appendLine("   +${pad(at - processStartMs, 5)} ms  ${stack.replace("\n", "\n              ")}")
                        }
                    }
                    logFile.appendText(text)
                    System.err.print(text)
                }
            }
        }, "startup-window-watch").apply {
            isDaemon = true
            start()
        }
    }

    /** The top of the thread that dispatches AWT events, which is the one that paints and lays out. */
    private fun awtThreadStack(): String? {
        val thread = Thread.getAllStackTraces().keys.firstOrNull { it.name.startsWith("AWT-EventQueue") }
            ?: return null
        val frames = thread.stackTrace.take(STACK_FRAMES).joinToString("\n") { "at $it" }
        return frames.ifBlank { null }
    }

    private const val STACK_FRAMES = 7
}

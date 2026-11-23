package com.alananasss.kittytune.data.sync

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What the connection code saw, kept for the user to copy (device sync, Connect and Listen Together).
 *
 * The connection code used to log only in debug builds, so a report of "both devices show offline" came
 * with nothing to look at. This keeps the last few hundred events in memory, always, and the sync
 * settings can copy them for a bug report. Never frames, secrets, tokens or room codes: only what was
 * attempted, over which route, and how it ended.
 */
object NetLog {
    private const val MAX_LINES = 400
    private val lines = ArrayDeque<String>()
    private val time = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    fun i(area: String, message: String) = add("I", area, message, null)

    fun w(area: String, message: String, error: Throwable? = null) = add("W", area, message, error)

    @Synchronized
    private fun add(level: String, area: String, message: String, error: Throwable?) {
        val cause = error?.let { " (${describe(it)})" }.orEmpty()
        val line = "${time.format(Instant.now())} $level/$area: $message$cause"
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        com.alananasss.kittytune.utils.Logger.i("KittyNet", line)
    }

    /** The log as text, with what is needed to read it (version and platform first). */
    @Synchronized
    fun dump(): String = buildString {
        appendLine("KittyTune ${com.alananasss.kittytune.BuildConfig.VERSION_NAME} (desktop, ${System.getProperty("os.name")} ${System.getProperty("os.version")})")
        appendLine("Sync listener: ${if (SyncService.isRunning) "on, port ${SyncService.port}" else "off"}; paired devices: ${SyncPeers.all().size}")
        appendLine("Local addresses: ${runCatching { SyncService.rankedCandidateAddresses().joinToString() }.getOrDefault("?")}")
        appendLine()
        lines.forEach { appendLine(it) }
    }

    /** Exception class and message, without stack traces: enough to tell a timeout from a refusal. */
    fun describe(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        val name = root.javaClass.simpleName
        return root.message?.takeIf { it.isNotBlank() }?.let { "$name: ${it.take(160)}" } ?: name
    }
}

package com.alananasss.kittytune.data

import java.io.Closeable
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Owns the helper and its stdin on one background worker, including startup and final cleanup. */
internal class SmtcBridgeWorker(
    private val startBridge: () -> Process?,
    private val onBridgeLine: (String) -> Unit,
) : Closeable {
    private val submissions = Any()
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "WindowsSmtc-Worker").apply { isDaemon = true }
    }
    @Volatile private var closed = false
    @Volatile private var process: Process? = null
    // Only the worker reads/writes these fields.
    private var writer: PrintWriter? = null
    private var latestUpdate: String? = null
    private var lastSentUpdate: String? = null

    init { submit { start() } }

    fun update(message: String) = submit {
        latestUpdate = message
        writeUpdate(message)
    }

    fun restart() = submit {
        stop()
        // close() can mark the service closed while stop() is waiting for the old process.
        if (!closed) {
            start()
            latestUpdate?.let { writeUpdate(it) }
        }
    }

    private fun submit(action: () -> Unit) {
        synchronized(submissions) {
            if (closed) return
            worker.execute {
                if (!closed) runCatching(action).onFailure {
                    println("Windows SMTC unavailable: ${it.message}")
                }
            }
        }
    }

    private fun start() {
        if (closed) return
        val next = startBridge() ?: return
        process = next
        // If shutdown happened during process creation, retain ownership for cleanup but never publish it.
        if (closed) {
            stop()
            return
        }
        try {
            writer = PrintWriter(next.outputStream.bufferedWriter(StandardCharsets.UTF_8), true)
            thread(isDaemon = true, name = "WindowsSmtc-Reader") {
                runCatching {
                    next.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                        lines.forEach { line ->
                            if (!closed && process === next) onBridgeLine(line.trim())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            stop()
            throw e
        }
    }

    private fun writeUpdate(message: String) {
        if (closed || message == lastSentUpdate) return
        val out = writer ?: return
        out.println(message)
        // PrintWriter swallows IOExceptions; a failed write must not be cached as delivered.
        if (!out.checkError()) lastSentUpdate = message
    }

    private fun stop() {
        val old = process
        val out = writer
        process = null
        writer = null
        lastSentUpdate = null
        runCatching { out?.println("QUIT") }
        runCatching { out?.close() }
        runCatching { old?.outputStream?.close() }
        if (old != null) {
            runCatching {
                if (!old.waitFor(1500, TimeUnit.MILLISECONDS)) {
                    old.destroyForcibly()
                    old.waitFor(1000, TimeUnit.MILLISECONDS)
                }
            }.onFailure { runCatching { old.destroyForcibly() } }
        }
    }

    /** Nonblocking for the caller; no subsequent update or restart can be accepted. */
    override fun close() {
        synchronized(submissions) {
            if (closed) return
            closed = true
            worker.execute { stop() }
            worker.shutdown()
        }
    }

    internal fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = worker.awaitTermination(timeout, unit)
}

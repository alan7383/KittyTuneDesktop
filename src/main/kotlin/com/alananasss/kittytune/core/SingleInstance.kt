package com.alananasss.kittytune.core

import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileLock
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * One KittyTune per user.
 *
 * Closing the window only hides it to the tray, so the next click on the shortcut used to start a
 * whole second app: another JVM and renderer (hundreds of megabytes), a second player that could
 * play over the first, and two processes writing the same database. Now the second launch finds the
 * first through a lock file, asks it to show its window, and exits before loading anything.
 *
 * The request travels over a loopback socket whose port is written next to the lock. Anything local
 * could connect to it, so it understands two messages and all they can do is raise the window and
 * queue existing files for playback: `SHOW`, or `OPEN` followed by one path per line.
 */
object SingleInstance {
    private const val SHOW_COMMAND = "SHOW"
    private const val OPEN_COMMAND = "OPEN"
    private const val CONNECT_TIMEOUT_MS = 1_500
    /** Plenty for a multi-file "Open with", and a hard cap on what a local client can make us buffer. */
    private const val MAX_MESSAGE_BYTES = 64 * 1024

    private val lockFile = File(AppDirs.dataDir, "instance.lock")
    private val portFile = File(AppDirs.dataDir, "instance.port")

    // Held for the life of the process; releasing either would let another instance in.
    private var lockHandle: RandomAccessFile? = null
    private var lock: FileLock? = null

    /**
     * Returns true when this process is the one that should run, in which case [filesToOpen] are
     * queued on [OpenFileRequests]. When another instance already owns the lock, it is asked to show
     * itself and open [filesToOpen], and false is returned.
     */
    fun acquire(filesToOpen: List<String> = emptyList()): Boolean {
        val handle = runCatching { RandomAccessFile(lockFile, "rw") }.getOrNull() ?: return true
        val acquired = runCatching { handle.channel.tryLock() }.getOrNull()
        if (acquired == null) {
            handle.close()
            // If the running instance cannot be reached, starting anyway beats refusing to start.
            if (signalRunningInstance(filesToOpen)) return false
            OpenFileRequests.submit(filesToOpen)
            return true
        }
        lockHandle = handle
        lock = acquired
        startListener()
        OpenFileRequests.submit(filesToOpen)
        return true
    }

    private fun signalRunningInstance(filesToOpen: List<String>): Boolean {
        val lines = if (filesToOpen.isEmpty()) {
            listOf(SHOW_COMMAND)
        } else {
            listOf(OPEN_COMMAND) + filesToOpen.map { File(it).absolutePath }
        }
        val message = lines.joinToString(separator = "\n", postfix = "\n")
        val port = runCatching { portFile.readText().trim().toInt() }.getOrNull() ?: return false
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS)
                socket.getOutputStream().write(message.toByteArray(StandardCharsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    private fun startListener() {
        val server = runCatching { ServerSocket(0, 4, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        runCatching { portFile.writeText(server.localPort.toString()) }
        thread(isDaemon = true, name = "SingleInstance-Listener") {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { client ->
                        client.soTimeout = CONNECT_TIMEOUT_MS
                        val lines = client.getInputStream().readNBytes(MAX_MESSAGE_BYTES)
                            .toString(StandardCharsets.UTF_8).lines().filter { it.isNotBlank() }
                        when (lines.firstOrNull()) {
                            SHOW_COMMAND -> java.awt.EventQueue.invokeLater { MainWindowRaiser.raise() }
                            OPEN_COMMAND -> {
                                OpenFileRequests.submit(lines.drop(1))
                                java.awt.EventQueue.invokeLater { MainWindowRaiser.raise() }
                            }
                        }
                    }
                }
            }
        }
    }
}

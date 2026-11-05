package com.alananasss.kittytune.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmtcBridgeWorkerTest {
    @Test
    fun updatesAndRestartUseOneWorkerAndReplayLatestWithoutDuplicates() {
        val first = FakeBridge()
        val second = FakeBridge()
        val starts = AtomicInteger()
        val worker = SmtcBridgeWorker({ if (starts.getAndIncrement() == 0) first else second }, {})
        try {
            SwingUtilities.invokeAndWait {
                worker.update("UPDATE\tfirst")
                worker.update("UPDATE\tfirst")
                worker.update("UPDATE\tlatest")
                worker.restart()
                worker.update("UPDATE\tlatest")
                worker.update("UPDATE\tafter restart")
            }
            assertEquals("UPDATE\tfirst", first.nextLine())
            assertEquals("UPDATE\tlatest", first.nextLine())
            assertEquals("QUIT", first.nextLine())
            assertEquals("UPDATE\tlatest", second.nextLine())
            assertEquals("UPDATE\tafter restart", second.nextLine())
        } finally {
            worker.close()
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertEquals("QUIT", second.nextLine())
        assertTrue(first.lines.isEmpty())
        assertTrue(second.lines.isEmpty())
        assertEquals(2, starts.get())
        assertEquals(setOf("WindowsSmtc-Worker"), (first.threads + second.threads).toSet())
        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
    }

    @Test
    fun closingDuringRestartPreventsAnotherHelperAndDiscardsQueuedWork() {
        val first = FakeBridge(blockStop = true)
        val starts = AtomicInteger()
        val worker = SmtcBridgeWorker({ starts.incrementAndGet(); first }, {})
        try {
            worker.update("UPDATE\tinitial")
            assertEquals("UPDATE\tinitial", first.nextLine())
            worker.restart()
            assertTrue(first.waitEntered.await(5, TimeUnit.SECONDS))
            worker.update("UPDATE\tqueued")
            worker.restart()
            SwingUtilities.invokeAndWait { worker.close() }
            worker.restart()
            worker.update("UPDATE\tafter close")
        } finally {
            worker.close()
            first.releaseStop.countDown()
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertEquals(1, starts.get(), "Shutdown must prevent the restart from launching another process")
        assertEquals("QUIT", first.nextLine())
        assertTrue(first.lines.isEmpty(), "No queued media writes may run after close")
        assertFalse(first.isAlive)
        worker.close() // idempotent, even after executor termination
    }

    @Test
    fun processWhoseCreationFinishesAfterCloseIsStillCleanedUp() {
        val bridge = FakeBridge()
        val launchEntered = CountDownLatch(1)
        val releaseLaunch = CountDownLatch(1)
        val worker = SmtcBridgeWorker({
            launchEntered.countDown()
            check(releaseLaunch.await(5, TimeUnit.SECONDS))
            bridge
        }, {})
        try {
            assertTrue(launchEntered.await(5, TimeUnit.SECONDS))
            SwingUtilities.invokeAndWait { worker.close() }
        } finally {
            worker.close()
            releaseLaunch.countDown()
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertTrue(bridge.pipeClosed.await(1, TimeUnit.SECONDS))
        assertFalse(bridge.isAlive, "An in-flight spawn must not leave a helper alive after cleanup")
        assertTrue(bridge.lines.isEmpty())
    }

    @Test
    fun blockedPipeNeverBlocksUiUpdateOrShutdown() {
        val bridge = FakeBridge(blockWrite = true)
        val worker = SmtcBridgeWorker({ bridge }, {})
        val uiReturned = CountDownLatch(1)
        try {
            SwingUtilities.invokeLater {
                worker.update("UPDATE\tblocked")
                uiReturned.countDown()
            }
            assertTrue(bridge.writeEntered.await(5, TimeUnit.SECONDS))
            assertTrue(uiReturned.await(1, TimeUnit.SECONDS), "UI must return while the helper pipe is blocked")
            SwingUtilities.invokeLater { worker.close(); uiReturned.countDown() }
            // A separate marker proves shutdown also returned without waiting on the pipe.
            val closeReturned = CountDownLatch(1)
            SwingUtilities.invokeLater { closeReturned.countDown() }
            assertTrue(closeReturned.await(1, TimeUnit.SECONDS))
        } finally {
            worker.close()
            bridge.releaseWrite.countDown()
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS))
        }
        assertEquals("UPDATE\tblocked", bridge.nextLine())
        assertEquals("QUIT", bridge.nextLine())
        assertFalse(bridge.isAlive)
        assertEquals(setOf("WindowsSmtc-Worker"), bridge.threads.toSet())
    }

    private class FakeBridge(blockStop: Boolean = false, blockWrite: Boolean = false) : Process() {
        val lines = LinkedBlockingQueue<String>()
        val threads = ConcurrentLinkedQueue<String>()
        val waitEntered = CountDownLatch(1)
        val releaseStop = CountDownLatch(if (blockStop) 1 else 0)
        val writeEntered = CountDownLatch(1)
        val releaseWrite = CountDownLatch(if (blockWrite) 1 else 0)
        val pipeClosed = CountDownLatch(1)
        @Volatile private var alive = true
        private val pipe = object : OutputStream() {
            private val pending = ByteArrayOutputStream()
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                threads += Thread.currentThread().name
                if (String(bytes, offset, length, Charsets.UTF_8).startsWith("UPDATE")) {
                    writeEntered.countDown()
                    check(releaseWrite.await(5, TimeUnit.SECONDS))
                }
                for (i in offset until offset + length) write(bytes[i].toInt() and 255)
            }
            override fun write(value: Int) {
                if (value == 10) {
                    lines += pending.toString(Charsets.UTF_8.name()).trimEnd('\r')
                    pending.reset()
                } else pending.write(value)
            }
            override fun close() { pipeClosed.countDown() }
        }
        fun nextLine(): String = requireNotNull(lines.poll(5, TimeUnit.SECONDS)) { "Expected helper input" }
        override fun getOutputStream() = pipe
        override fun getInputStream() = ByteArrayInputStream(byteArrayOf())
        override fun getErrorStream() = ByteArrayInputStream(byteArrayOf())
        override fun waitFor(): Int { waitFor(5, TimeUnit.SECONDS); return 0 }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            waitEntered.countDown()
            check(releaseStop.await(5, TimeUnit.SECONDS))
            alive = false
            return true
        }
        override fun exitValue(): Int = if (alive) throw IllegalThreadStateException() else 0
        override fun isAlive() = alive
        override fun destroy() { alive = false }
        override fun destroyForcibly(): Process { alive = false; return this }
    }
}

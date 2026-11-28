package com.alananasss.kittytune.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QueueHistoryWritesTest {
    @Test fun pendingSaveCannotReappearAfterClearAll() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Default)
        val gate = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val rows = mutableListOf<String>()
        val events = mutableListOf<String>()
        val queue = QueueHistoryWrites(testScope) { throw it }
        queue.submit {
            gate.await() // A long encode or suspended Room insert.
            rows += "old queue"
            events += "save callback"
        }
        queue.submit { rows.clear(); events += "clear complete" }
        queue.submit {
            rows += "new queue"
            events += "new callback"
            done.complete(Unit)
        }
        Thread.sleep(50)
        assertTrue(events.isEmpty())
        gate.complete(Unit)
        done.await()
        assertEquals(listOf("save callback", "clear complete", "new callback"), events)
        assertEquals(listOf("new queue"), rows)
        testScope.cancel()
    }

    @Test fun failedWriteDoesNotDiscardLaterDeleteOrSave() = runBlocking {
        val testScope = CoroutineScope(Dispatchers.Default)
        val failures = mutableListOf<Exception>()
        val rows = mutableListOf("existing")
        val done = CompletableDeferred<Unit>()
        val queue = QueueHistoryWrites(testScope) { failures += it }
        queue.submit { throw IllegalStateException("failed insert") }
        queue.submit { rows.remove("existing") }
        queue.submit {
            rows += "replacement"
            done.complete(Unit)
        }
        done.await()
        assertEquals(1, failures.size)
        assertEquals(listOf("replacement"), rows)
        testScope.cancel()
    }
}

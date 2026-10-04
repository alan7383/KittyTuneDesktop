package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.cover.SharedDecodeHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test

/**
 * One decode per URL no matter how many viewers: sharing starts a single producer,
 * the last release stops it, and a fresh acquire after death starts over.
 */
class SharedDecodeHubTest {

    private fun hub(starts: AtomicInteger, scope: CoroutineScope) =
        SharedDecodeHub<Int>(scope) { _, emit ->
            starts.incrementAndGet()
            try {
                awaitCancellation()
            } finally {
                emit(-1)
            }
        }

    @Test
    fun `collectors share one producer until all release`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val starts = AtomicInteger(0)
        val hub = hub(starts, scope)

        val first = hub.flowFor("u")
        val second = hub.flowFor("u")
        assertSame(first, second)
        assertEquals(1, starts.get())
        assertEquals(2, hub.refCountForTest("u"))

        hub.release("u")
        assertEquals(1, starts.get())
        assertEquals(1, hub.refCountForTest("u"))

        hub.release("u")
        assertEquals(0, hub.refCountForTest("u"))

        // After full release the next acquire starts a new producer.
        hub.flowFor("u")
        assertEquals(2, starts.get())
        hub.release("u")
    }

    @Test
    fun `different urls decode independently`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val starts = AtomicInteger(0)
        val hub = hub(starts, scope)

        hub.flowFor("a")
        hub.flowFor("b")
        assertEquals(2, starts.get())
        hub.release("a")
        hub.release("b")
        assertEquals(0, hub.refCountForTest("a"))
        assertEquals(0, hub.refCountForTest("b"))
    }

    @Test
    fun `release without acquire is a no-op`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val hub = hub(AtomicInteger(0), scope)
        hub.release("missing")
    }

    @Test
    fun `late collector replays the latest frame`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var emit: ((Int?) -> Unit)? = null
        val hub = SharedDecodeHub<Int>(scope) { _, e -> emit = e; awaitCancellation() }
        val flow = hub.flowFor("u")
        // Producer runs eagerly on Unconfined and parks holding the emit hook.
        withTimeout(2000) {
            while (emit == null) kotlinx.coroutines.yield()
        }
        emit!!(42)
        val seen = mutableListOf<Int?>()
        val job = launch(Dispatchers.Unconfined) { flow.collect { seen.add(it) } }
        withTimeout(2000) {
            while (42 !in seen) kotlinx.coroutines.yield()
        }
        job.cancel()
        hub.release("u")
    }
}

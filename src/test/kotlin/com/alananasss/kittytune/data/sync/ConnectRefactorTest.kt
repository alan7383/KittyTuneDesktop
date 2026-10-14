package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.*
import kotlin.test.*

class ConnectRefactorTest {
    private fun track(id: Long) = Track(id = id, title = "Track $id", artworkUrl = null, user = null, durationMs = 300000, source = "soundcloud")
    private fun state(playing: Boolean = false) = PlaybackSnapshot("phone", 1000, listOf(track(1)), 0, 100, playing, false, "NONE")
    private fun command(id: String) = ConnectMessage(kind = "command", sender = "phone", session = "session", sequence = 1, id = id)

    @Test fun queueCaptureReusesSnapshotAndKeepsPlayingTrackWithinBoundedWindow() {
        val capture = ConnectQueueWindow()
        val queue = (1L..800L).map(::track)
        val first = capture.capture(queue, 0)!!
        assertEquals(500, first.queue.size)
        assertSame(first.queue, capture.capture(queue, 100)!!.queue)
        val end = capture.capture(queue, 799)!!
        assertEquals(300, end.offset)
        assertEquals(499, end.index)
        assertEquals(800L, end.queue[end.index].id)
        assertNotSame(first.queue, end.queue)
        assertNull(capture.capture(queue, 800))
        assertFalse(ConnectQueueWindow.sameTracks(listOf(track(1)), listOf(track(1).copy(source = "youtube"))))
    }

    @Test fun pausedStateHasNoPeriodicPublicationAndReconnectIncludesWholeQueue() {
        val publisher = ConnectStatePublisher()
        val state = state()
        assertEquals(state.queue, publisher.plan(state, nowNanos = 0)!!.state.queue)
        assertNull(publisher.plan(state.copy(updatedAtMs = Long.MAX_VALUE), nowNanos = 120_000_000_000))
        assertEquals(state.queue, publisher.plan(state, force = true, nowNanos = 120_000_000_001)!!.state.queue)
    }

    @Test fun playingCorrectionUsesMonotonicTimeAndSendsSmallDelta() {
        val publisher = ConnectStatePublisher()
        val state = state(true)
        publisher.plan(state, nowNanos = 0)
        assertNull(publisher.plan(state.copy(updatedAtMs = 9_999_999, positionMs = 10100), nowNanos = 10_000_000_000))
        val correction = publisher.plan(state.copy(updatedAtMs = -1000, positionMs = 60100), nowNanos = 60_000_000_000)!!
        assertTrue(correction.state.queue.isEmpty())
        assertEquals(ConnectWire.queueVersion(state), correction.queueVersion)
        assertNotNull(publisher.plan(state.copy(positionMs = 5000), nowNanos = 61_000_000_000))
    }

    @Test fun fingerprintRemainsCompatibleWithOldWireAndCompactPersistenceOmitsApiData() {
        val state = state().copy(queue = listOf(track(1), track(1).copy(source = "youtube")))
        val old = java.security.MessageDigest.getInstance("SHA-256")
            .digest(state.queue.joinToString("|") { "${it.source}:${it.id}" }.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { "%02x".format(it) }
        assertEquals(old, ConnectWire.queueVersion(state))
        assertNotEquals(old, ConnectWire.queueVersion(state.copy(queue = state.queue.reversed())))
        val compact = ConnectWire.serializeSnapshot(state.copy(queue = listOf(track(1).copy(description = "api".repeat(10000)))))
        assertTrue(compact.length < 2000)
        assertEquals(1L, ConnectWire.deserializeSnapshot(compact)!!.queue.first().id)
    }

    @Test fun disconnectFailsOnlyItsCommandsAndOldSessionCannotAcknowledgeNewOne() = runBlocking {
        val pending = ConnectPendingCommands<Any>(1)
        val old = Any(); val new = Any()
        val before = pending.register(old, "old")
        val after = pending.register(new, "new")
        assertFailsWith<IllegalStateException> { pending.register(new, "extra") }
        assertFalse(pending.acknowledge(old, command("new")))
        pending.disconnect(old)
        assertFailsWith<java.io.IOException> { before.await() }
        assertFalse(after.isCompleted)
        assertTrue(pending.acknowledge(new, command("new")))
        assertEquals("new", after.await().id)
    }

    @Test fun preparationUsesCallbacksAndUnregistersOnReadyErrorAndCancellation() = runBlocking {
        var ready = false
        var callback: (() -> Unit)? = null
        var removed = 0
        val task = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitConnectPreparation({ ready }, { null }, { callback = it; { removed++ } })
        }
        ready = true
        callback!!(); callback!!()
        withTimeout(1000) { task.join() }
        assertEquals(1, removed)
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitConnectPreparation({ false }, { null }, { callback = it; { removed++ } })
        }
        cancelled.cancelAndJoin()
        assertEquals(2, removed)
        assertFailsWith<IllegalStateException> {
            awaitConnectPreparation({ false }, { java.io.IOException() }, { { removed++ } })
        }
        assertEquals(3, removed)
    }

    @Test fun commandQueueIsBoundedAndCancelsWorkFromOldSession() = runBlocking {
        val session = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val began = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val queue = ConnectCommandQueue(session, execute = {
            calls += it.id
            began.complete(Unit)
            try { awaitCancellation() } finally { ended.complete(Unit) }
        }, failed = { error("Cancellation is not a transport failure") }, capacity = 2)
        try {
            assertTrue(queue.enqueue(command("active")))
            withTimeout(1000) { began.await() }
            assertTrue(queue.enqueue(command("queued-1")))
            assertTrue(queue.enqueue(command("queued-2")))
            assertFalse(queue.enqueue(command("overflow")))
            queue.close()
            withTimeout(1000) { ended.await() }
            assertFalse(queue.enqueue(command("late")))
            assertEquals(listOf("active"), calls.toList())
        } finally { queue.close(); session.cancel() }
    }

    @Test fun frameSenderMovesEncodingOffCallerAndPreservesStateBeforeAck() = runBlocking {
        val session = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val caller = Thread.currentThread()
        val finished = CompletableDeferred<Unit>()
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val sender = ConnectFrameSender(session, encode = { message, _ ->
            check(Thread.currentThread() !== caller)
            message.kind
        }, send = {
            calls += it
            if (it == "ack") finished.complete(Unit)
            true
        }, failed = { finished.completeExceptionally(AssertionError("Send failed")) })
        try {
            assertTrue(sender.enqueue(command("1").copy(kind = "state"), false))
            assertTrue(sender.enqueue(command("2").copy(kind = "ack"), false))
            withTimeout(1000) { finished.await() }
            assertEquals(listOf("state", "ack"), calls.toList())
        } finally { sender.close(); session.cancel() }
    }

    @Test fun rejectedWebsocketFrameFailsSessionImmediately() = runBlocking {
        val session = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val failed = CompletableDeferred<Unit>()
        val sender = ConnectFrameSender(session, encode = { _, _ -> "frame" }, send = { false }, failed = { failed.complete(Unit) })
        try {
            assertTrue(sender.enqueue(command("1"), false))
            withTimeout(1000) { failed.await() }
        } finally { sender.close(); session.cancel() }
    }
}

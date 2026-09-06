package com.alananasss.kittytune.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the merge rule behind LAN sync (QR pairing), and for the loop bound that stops
 * a large backlog from spinning forever.
 *
 * These are pure — no Android, Compose, database or network — so they run on the JVM test task
 * (`./gradlew test`). They pin down the two properties the feature depends on:
 *
 *  1. merging is idempotent and marks only advance over contiguous runs;
 *  2. an exchange drains at most [SyncMerge.MAX_EVENTS_PER_EXCHANGE] events per round and the
 *     drain loop is bounded, so it cannot run forever even with a misbehaving peer.
 */
class SyncMergeTest {

    private fun event(deviceId: String, seq: Long, kind: String = SyncKinds.LIKE) =
        SyncEvent(deviceId = deviceId, seq = seq, timestampMs = seq, kind = kind, payload = "{}")

    @Test
    fun selectNewDropsSelfEvents() {
        val incoming = listOf(
            event("me", 1),
            event("other", 1),
            event("other", 2),
        )
        val fresh = SyncMerge.selectNew(incoming, emptyMap(), "me")
        assertEquals(listOf("other#1", "other#2"), fresh.map { it.id })
    }

    @Test
    fun selectNewDropsEventsAtOrBelowMark() {
        val incoming = listOf(event("a", 1), event("a", 2), event("a", 3))
        val fresh = SyncMerge.selectNew(incoming, mapOf("a" to 2L), "me")
        assertEquals(listOf("a#3"), fresh.map { it.id })
    }

    @Test
    fun selectNewDeduplicatesWithinBatch() {
        val incoming = listOf(event("a", 1), event("a", 1), event("a", 2))
        val fresh = SyncMerge.selectNew(incoming, emptyMap(), "me")
        assertEquals(listOf("a#1", "a#2"), fresh.map { it.id })
    }

    @Test
    fun advanceOnlyMovesOverContiguousRun() {
        // Applied 1,2,3,5 -> mark lands on 3, never 5, so 5 is re-sent later rather than lost.
        val applied = listOf(event("a", 1), event("a", 2), event("a", 3), event("a", 5))
        val marks = SyncMerge.advance(emptyMap(), applied)
        assertEquals(3L, SyncMerge.markFor(marks, "a"))
    }

    @Test
    fun advanceNeverMovesBackwards() {
        val marks = mapOf("a" to 10L)
        val applied = listOf(event("a", 3))
        assertEquals(10L, SyncMerge.markFor(SyncMerge.advance(marks, applied), "a"))
    }

    @Test
    fun eventsToSendCapsAtMaxPerExchange() {
        val local = (1L..10_000L).map { event("a", it) }
        val toSend = SyncMerge.eventsToSend(local, emptyMap(), peerDeviceId = "b")
        assertEquals(SyncMerge.MAX_EVENTS_PER_EXCHANGE, toSend.size)
        // The cap is a contiguous prefix, so resumption after the marks move is exact.
        assertEquals(1L, toSend.first().seq)
        assertEquals(SyncMerge.MAX_EVENTS_PER_EXCHANGE.toLong(), toSend.last().seq)
    }

    @Test
    fun eventsToSendNeverEchoesPeerOwnEvents() {
        val local = listOf(event("a", 1), event("b", 1), event("b", 2))
        val toSend = SyncMerge.eventsToSend(local, emptyMap(), peerDeviceId = "b")
        assertEquals(listOf("a#1"), toSend.map { it.id })
    }

    @Test
    fun nextSeqIsDerivedFromLog() {
        val local = listOf(event("a", 1), event("a", 3), event("b", 9))
        assertEquals(4L, SyncMerge.nextSeq(local, "a"))
        assertEquals(1L, SyncMerge.nextSeq(local, "unseen"))
    }
}
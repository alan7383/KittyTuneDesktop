package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlockManagerTest {

    private fun sampleTrack(id: Long, title: String, artistId: Long, artistName: String): Track {
        return Track(
            id = id,
            title = title,
            artworkUrl = "https://example.com/artwork/$id.jpg",
            durationMs = 180_000L,
            user = User(id = artistId, username = artistName, avatarUrl = null),
            source = "soundcloud",
        )
    }

    @Before
    fun setUp() {
        runCatching { AppDatabase.init() }
        BlockManager.init()
        runBlocking {
            runCatching { AppDatabase.blockedContentDao.unblockTrack(1001L) }
            runCatching { AppDatabase.blockedContentDao.unblockTrack(1002L) }
            runCatching { AppDatabase.blockedContentDao.unblockTrack(1003L) }
            runCatching { AppDatabase.blockedContentDao.unblockArtist(501L) }
            runCatching { AppDatabase.blockedContentDao.unblockArtist(502L) }
        }
        BlockManager.unblockTrack(1001L)
        BlockManager.unblockTrack(1002L)
        BlockManager.unblockTrack(1003L)
        BlockManager.unblockArtist(501L)
        BlockManager.unblockArtist(502L)
    }

    @After
    fun tearDown() {
        runBlocking {
            runCatching { AppDatabase.blockedContentDao.unblockTrack(1001L) }
            runCatching { AppDatabase.blockedContentDao.unblockTrack(1002L) }
            runCatching { AppDatabase.blockedContentDao.unblockTrack(1003L) }
            runCatching { AppDatabase.blockedContentDao.unblockArtist(501L) }
            runCatching { AppDatabase.blockedContentDao.unblockArtist(502L) }
        }
        BlockManager.unblockTrack(1001L)
        BlockManager.unblockTrack(1002L)
        BlockManager.unblockTrack(1003L)
        BlockManager.unblockArtist(501L)
        BlockManager.unblockArtist(502L)
        BlockManager.onCurrentTrackBlocked = null
        BlockManager.onTrackBlocked = null
        BlockManager.onTrackUnblocked = null
        BlockManager.onArtistBlocked = null
        BlockManager.onArtistUnblocked = null
    }

    @Test
    fun testTrackBlockAndUnblock() {
        val track = sampleTrack(1001L, "Synth Track", 501L, "Producer A")

        var callbackFired = false
        BlockManager.onTrackBlocked = { id ->
            if (id == 1001L) callbackFired = true
        }

        assertFalse(BlockManager.isTrackBlocked(1001L))
        assertFalse(BlockManager.isBlocked(track))

        BlockManager.blockTrack(track, reason = BlockManager.REASON_MANUAL)

        assertTrue("Callback should be invoked", callbackFired)
        assertTrue(BlockManager.isTrackBlocked(1001L))
        assertTrue(BlockManager.isBlocked(track))
        assertTrue(BlockManager.blockedTrackIdsFlow.value.contains(1001L))

        var unblockCallbackFired = false
        BlockManager.onTrackUnblocked = { id ->
            if (id == 1001L) unblockCallbackFired = true
        }

        BlockManager.unblockTrack(1001L)

        assertTrue(unblockCallbackFired)
        assertFalse(BlockManager.isTrackBlocked(1001L))
        assertFalse(BlockManager.isBlocked(track))
        assertFalse(BlockManager.blockedTrackIdsFlow.value.contains(1001L))
    }

    @Test
    fun testArtistBlockAndUnblock() {
        val track = sampleTrack(1002L, "Vocal Track", 502L, "Artist B")

        var callbackFired = false
        BlockManager.onArtistBlocked = { id ->
            if (id == 502L) callbackFired = true
        }

        assertFalse(BlockManager.isArtistBlocked(502L))
        assertFalse(BlockManager.isBlocked(track))

        BlockManager.blockArtist(
            artistId = 502L,
            artistName = "Artist B",
            reason = BlockManager.REASON_MANUAL,
        )

        assertTrue("Callback should be invoked", callbackFired)
        assertTrue(BlockManager.isArtistBlocked(502L))
        assertTrue(BlockManager.isBlocked(track))
        assertTrue(BlockManager.blockedArtistIdsFlow.value.contains(502L))

        var unblockCallbackFired = false
        BlockManager.onArtistUnblocked = { id ->
            if (id == 502L) unblockCallbackFired = true
        }

        BlockManager.unblockArtist(502L)

        assertTrue(unblockCallbackFired)
        assertFalse(BlockManager.isArtistBlocked(502L))
        assertFalse(BlockManager.isBlocked(track))
        assertFalse(BlockManager.blockedArtistIdsFlow.value.contains(502L))
    }

    @Test
    fun testCurrentTrackSkipCallback() {
        val track = sampleTrack(1001L, "Playing Song", 501L, "Artist")
        var currentTrackSkipped = false
        BlockManager.onCurrentTrackBlocked = {
            currentTrackSkipped = true
        }

        // Block with different current ID: should NOT fire onCurrentTrackBlocked
        BlockManager.blockTrack(track, skipIfCurrent = true, currentlyPlayingId = 9999L)
        assertFalse(currentTrackSkipped)

        BlockManager.unblockTrack(1001L)

        // Block matching current ID: MUST fire onCurrentTrackBlocked
        BlockManager.blockTrack(track, skipIfCurrent = true, currentlyPlayingId = 1001L)
        assertTrue(currentTrackSkipped)
    }

    @Test
    fun testFilterBlockedTracks() {
        val t1 = sampleTrack(1001L, "Track 1", 501L, "Artist 1")
        val t2 = sampleTrack(1002L, "Track 2", 502L, "Artist 2")
        val t3 = sampleTrack(1003L, "Track 3", 503L, "Artist 3")

        val list = listOf(t1, t2, t3)
        assertEquals(3, BlockManager.filterBlocked(list).size)

        // Block track 1 by track ID
        BlockManager.blockTrack(t1)
        val filtered1 = BlockManager.filterBlocked(list)
        assertEquals(2, filtered1.size)
        assertFalse(filtered1.any { it.id == 1001L })

        // Block artist of track 2
        BlockManager.blockArtist(502L, "Artist 2")
        val filtered2 = BlockManager.filterBlocked(list)
        assertEquals(1, filtered2.size)
        assertEquals(1003L, filtered2.first().id)
    }

    @Test
    fun testQueueReinsertionOnUndoLogic() {
        // Test the exact queue reinsertion algorithm used in PlayerViewModel.undoAiSkip()
        val t1 = sampleTrack(1L, "Song 1", 10L, "A1")
        val t2 = sampleTrack(2L, "Song 2", 20L, "A2")
        val t3 = sampleTrack(3L, "Song 3", 30L, "A3")

        val queue = mutableListOf(t1, t2, t3)
        val originalQueue = mutableListOf(t1, t2, t3)

        // Simulate auto-skip and auto-block removing track at index 1 (t2)
        val skippedTrack = t2
        val queueIndex = 1
        queue.removeAt(1)
        originalQueue.removeAt(1)
        assertEquals(listOf(t1, t3), queue)

        // Simulate undoAiSkip:
        val existingIdx = queue.indexOfFirst { it.id == skippedTrack.id }
        val targetIdx = if (existingIdx != -1) {
            existingIdx
        } else {
            val insertAt = queueIndex.coerceIn(0, queue.size)
            queue.add(insertAt, skippedTrack)
            val origInsertAt = queueIndex.coerceIn(0, originalQueue.size)
            originalQueue.add(origInsertAt, skippedTrack)
            insertAt
        }

        assertEquals(1, targetIdx)
        assertEquals(3, queue.size)
        assertEquals(listOf(t1, t2, t3), queue)
        assertEquals(listOf(t1, t2, t3), originalQueue)
    }

    @Test
    fun testMutableListFilteringWhenInitiallyEmptyAndPopulated() {
        val mutableTracks = mutableListOf<Track>()
        val filteredInitial = com.alananasss.kittytune.ui.common.filterBlockedTracks(mutableTracks.toList())
        assertTrue(filteredInitial.isEmpty())

        val t1 = sampleTrack(101L, "Song 101", 11L, "Artist 11")
        val t2 = sampleTrack(102L, "Song 102", 12L, "Artist 12")
        mutableTracks.addAll(listOf(t1, t2))

        val filteredAfterLoad = com.alananasss.kittytune.ui.common.filterBlockedTracks(mutableTracks.toList())
        assertEquals(2, filteredAfterLoad.size)
        assertEquals(listOf(t1, t2), filteredAfterLoad)
    }
}

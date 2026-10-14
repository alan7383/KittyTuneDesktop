package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The likes table is now the library's persistence (RAM, issue #33): these cover the guarantees
 * the old single-JSON-string-in-prefs save used to give — round-trip, newest-first order, and a
 * replace that leaves no rows of a previous save behind.
 */
class LikeDaoTest {

    private fun track(id: Long, likedAt: Long?): Track = Track(
        id = id,
        title = "Track $id",
        artworkUrl = "https://example.com/artwork/$id.jpg",
        durationMs = 180_000L,
        user = User(id = 1L, username = "Artist", avatarUrl = null),
        source = "soundcloud",
        likedAt = likedAt,
    )

    @Before
    fun setUp() {
        runCatching { AppDatabase.init() }
    }

    @Test
    fun testReplaceAllRoundTripAndOrdering() = runBlocking {
        val dao = AppDatabase.likeDao

        dao.replaceAll(listOf(track(1, 300L), track(2, 500L), track(3, 100L)))

        val loaded = dao.loadAll()
        assertEquals(listOf(2L, 1L, 3L), loaded.map { it.id })
        assertEquals(500L, loaded[0].likedAt)
        assertEquals(300L, loaded[1].likedAt)
        assertEquals(100L, loaded[2].likedAt)
    }

    @Test
    fun testReplaceAllReplacesRatherThanAppends() = runBlocking {
        val dao = AppDatabase.likeDao

        dao.replaceAll(listOf(track(1, 10L), track(2, 20L), track(3, 30L)))
        dao.replaceAll(listOf(track(9, 50L)))

        assertEquals(listOf(9L), dao.loadAll().map { it.id })
    }

    @Test
    fun testEmptyLibraryRoundTrip() = runBlocking {
        val dao = AppDatabase.likeDao

        dao.replaceAll(emptyList())

        assertTrue(dao.loadAll().isEmpty())
    }

    @Test
    fun testNullLikedAtSurvivesRoundTrip() = runBlocking {
        val dao = AppDatabase.likeDao

        dao.replaceAll(listOf(track(7, null)))

        val loaded = dao.loadAll()
        assertEquals(1, loaded.size)
        assertNull(loaded[0].likedAt)
    }

    @Test
    fun testClearEmptiesTable() = runBlocking {
        val dao = AppDatabase.likeDao

        dao.replaceAll(listOf(track(1, 1L), track(2, 2L)))
        dao.clear()

        assertTrue(dao.loadAll().isEmpty())
    }
}

package com.alananasss.kittytune

import com.alananasss.kittytune.data.cache.isCompleteCacheFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Guards the cache predicate behind AudioCache.lookup: an in-progress `.part` (or any
 * empty file) must never be served as a playable track. Serving the empty `.part` made
 * the decoder fail its open, and the player error path retried that same file every
 * couple of seconds without end.
 */
class AudioCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `in-progress part files are not servable`() {
        assertFalse(isCompleteCacheFile(tmp.newFile("1471550845.part")))
    }

    @Test
    fun `empty finals are not servable`() {
        assertFalse(isCompleteCacheFile(tmp.newFile("1471550845.mp3")))
    }

    @Test
    fun `finished downloads are servable`() {
        val done = tmp.newFile("1471550845.mp3")
        done.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(isCompleteCacheFile(done))
    }

    @Test
    fun `directories are not servable`() {
        assertFalse(isCompleteCacheFile(tmp.newFolder("1471550845.mp3")))
    }
}

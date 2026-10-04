package com.alananasss.kittytune

import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.ui.player.PaletteCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The palette memo behind dominantColorCached: repeat views of one artwork must hit,
 * and the map must stay bounded no matter how far anyone scrolls.
 */
class PaletteCacheTest {

    @Test
    fun `miss then hit`() {
        val cache = PaletteCache()
        assertNull(cache.get("k"))
        cache.put("k", Color.Red)
        assertEquals(Color.Red, cache.get("k"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `overwrite keeps one entry`() {
        val cache = PaletteCache()
        cache.put("k", Color.Red)
        cache.put("k", Color.Blue)
        assertEquals(Color.Blue, cache.get("k"))
        assertEquals(1, cache.size())
    }

    @Test
    fun `eldest goes first once over capacity`() {
        val cache = PaletteCache(maxSize = 3)
        cache.put("a", Color.Red)
        cache.put("b", Color.Green)
        cache.put("c", Color.Blue)
        cache.put("d", Color.Yellow)
        assertNull(cache.get("a"))
        assertEquals(Color.Green, cache.get("b"))
        assertEquals(3, cache.size())
    }

    @Test
    fun `recently read entries survive eviction`() {
        val cache = PaletteCache(maxSize = 2)
        cache.put("a", Color.Red)
        cache.put("b", Color.Green)
        cache.get("a")
        cache.put("c", Color.Blue)
        assertEquals(Color.Red, cache.get("a"))
        assertNull(cache.get("b"))
    }
}

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.core.BoundedSet
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fixed-capacity caches behind the memory report in issue #33.
 *
 * What matters is not that they hold things — any map does — but that they let go, and that they let
 * go of the right ones. A cache that evicts what is on screen is worse than one that never evicts.
 */
class BoundedCacheTest {

    @Test
    fun `holds what fits`() {
        val cache = BoundedCache<Int, String>(3)
        cache[1] = "a"
        cache[2] = "b"
        assertEquals("a", cache[1])
        assertEquals(2, cache.size)
    }

    @Test
    fun `never grows past its capacity`() {
        val cache = BoundedCache<Int, Int>(10)
        for (i in 1..10_000) cache[i] = i
        assertEquals(10, cache.size)
    }

    @Test
    fun `evicts the least recently used, not the oldest`() {
        val cache = BoundedCache<String, Int>(3)
        cache["a"] = 1
        cache["b"] = 2
        cache["c"] = 3
        // Touching "a" makes "b" the stale one even though "a" went in first.
        assertEquals(1, cache["a"])
        cache["d"] = 4
        assertNull(cache["b"])
        assertEquals(1, cache["a"])
        assertEquals(3, cache["c"])
        assertEquals(4, cache["d"])
    }

    @Test
    fun `overwriting a key is not a new entry`() {
        val cache = BoundedCache<Int, String>(2)
        cache[1] = "a"
        cache[1] = "b"
        assertEquals(1, cache.size)
        assertEquals("b", cache[1])
    }

    @Test
    fun `a snapshot is not disturbed by later changes`() {
        val cache = BoundedCache<Int, Int>(5)
        cache[1] = 1
        val snapshot = cache.snapshot()
        cache[2] = 2
        cache.clear()
        assertEquals(mapOf(1 to 1), snapshot)
    }

    @Test
    fun `retainWhere drops exactly what the predicate rejects`() {
        val cache = BoundedCache<Int, Int>(10)
        for (i in 1..6) cache[i] = i
        cache.retainWhere { it % 2 == 0 }
        assertEquals(3, cache.size)
        assertEquals(setOf(2, 4, 6), cache.snapshot().keys)
    }

    @Test
    fun `a capacity of zero is rejected rather than silently caching nothing`() {
        // A cache that holds nothing looks like a cache and is a bug at every call site.
        val failed = runCatching { BoundedCache<Int, Int>(0) }.isFailure
        assertTrue(failed)
    }

    @Test
    fun `remove and clear empty it`() {
        val cache = BoundedCache<Int, Int>(4)
        cache[1] = 1
        cache[2] = 2
        assertEquals(1, cache.remove(1))
        assertNull(cache.remove(1))
        cache.clear()
        assertEquals(0, cache.size)
    }

    @Test
    fun `the set reports whether a value is new, like a real set`() {
        val guard = BoundedSet<Long>(4)
        assertTrue(guard.add(7L))
        assertFalse(guard.add(7L))
        assertTrue(7L in guard)
    }

    @Test
    fun `the set forgets its stalest keys and can be asked again`() {
        val guard = BoundedSet<Int>(2)
        guard.add(1)
        guard.add(2)
        guard.add(3)
        assertEquals(2, guard.size)
        // Forgetting means the question can be asked twice, which costs one request — the trade the
        // bound exists to make.
        assertTrue(guard.add(1))
    }

    @Test
    fun `concurrent writers cannot corrupt it or push it over capacity`() {
        val cache = BoundedCache<Int, Int>(50)
        val threads = (0 until 8).map { worker ->
            Thread {
                for (i in 0 until 2_000) {
                    val key = worker * 2_000 + i
                    cache[key] = key
                    cache[key]
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(50, cache.size)
    }
}

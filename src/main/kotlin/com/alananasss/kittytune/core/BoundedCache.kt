package com.alananasss.kittytune.core

/**
 * Small fixed-capacity caches, for the ones that are keyed by track (issue #33).
 *
 * A map keyed by track id and never emptied is a leak with a slow fuse: it costs nothing over an
 * evening and holds an entry for every track scrolled past over a week-long session. Reported as
 * memory climbing from 500 MB to 1000 MB and occasionally much further.
 *
 * Least-recently-used rather than oldest-first, because these all answer "what did we learn about
 * the track on screen": the entries worth keeping are the ones still being asked about.
 */
class BoundedCache<K, V>(private val maxEntries: Int) {

    init {
        require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
    }

    // accessOrder = true makes a get() count as use, which is what "recently used" has to mean here.
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxEntries
    }

    val size: Int get() = synchronized(map) { map.size }

    operator fun get(key: K): V? = synchronized(map) { map[key] }

    operator fun set(key: K, value: V) {
        synchronized(map) { map[key] = value }
    }

    fun remove(key: K): V? = synchronized(map) { map.remove(key) }

    fun containsKey(key: K): Boolean = synchronized(map) { map.containsKey(key) }

    fun clear() {
        synchronized(map) { map.clear() }
    }

    /** A snapshot, safe to hold and iterate while the cache keeps changing. */
    fun snapshot(): Map<K, V> = synchronized(map) { LinkedHashMap(map) }

    /** Drops every entry whose value [predicate] rejects. Used to evict what has expired. */
    fun retainWhere(predicate: (V) -> Boolean) {
        synchronized(map) {
            val iterator = map.entries.iterator()
            while (iterator.hasNext()) {
                if (!predicate(iterator.next().value)) iterator.remove()
            }
        }
    }
}

/**
 * The set counterpart, for the "have we already asked about this?" guards.
 *
 * Forgetting the oldest key means the question may be asked twice after thousands of others, which
 * costs one request. Remembering every key forever costs memory for the whole session, so the
 * trade goes this way round.
 */
class BoundedSet<T>(maxEntries: Int) {

    private val backing = BoundedCache<T, Unit>(maxEntries)

    val size: Int get() = backing.size

    /** @return true if [value] was not already present, matching [MutableSet.add]. */
    fun add(value: T): Boolean {
        val isNew = !backing.containsKey(value)
        backing[value] = Unit
        return isNew
    }

    operator fun contains(value: T): Boolean = backing.containsKey(value)

    fun remove(value: T) {
        backing.remove(value)
    }

    fun clear() = backing.clear()
}

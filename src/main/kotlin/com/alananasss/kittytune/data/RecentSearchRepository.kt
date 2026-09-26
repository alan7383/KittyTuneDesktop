package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow

/**
 * The queries this listener has searched for, newest first.
 *
 * The search box searches as you type, so "what did I look for" is the one thing the results screen
 * cannot tell you afterwards: the field is emptied when you close it and the query is gone with it.
 * This keeps the last [MAX_ENTRIES] of them.
 *
 * One row per distinct term, the term itself as the key: searching for the same thing twice moves it
 * back to the top instead of leaving two rows that read identically, which is what a plain
 * autoincrement id would have done. The list is global rather than per account — a listener who
 * signs in on someone else's machine is more likely to want yesterday's searches than none of them.
 */
object RecentSearchRepository {

    /** How many queries are kept. Older ones are dropped as new ones arrive. */
    const val MAX_ENTRIES = 20

    private val dao get() = AppDatabase.recentSearchDao

    /**
     * Terms this short are almost always a prefix someone is still typing, and they crowd out the
     * searches worth keeping.
     */
    private const val MIN_LENGTH = 3

    fun recent(): Flow<List<String>> = dao.getRecent()

    /**
     * Records a search, unless it is blank or too short to mean anything.
     *
     * Called when the results are fetched rather than on every keystroke, so a half-typed word that
     * never became a search is not remembered.
     */
    suspend fun record(query: String) {
        val term = query.trim()
        if (term.length < MIN_LENGTH) return
        dao.insert(term, System.currentTimeMillis(), MAX_ENTRIES)
    }

    suspend fun forget(query: String) = dao.delete(query)

    suspend fun clear() = dao.clear()
}

package com.alananasss.kittytune.data.local

import kotlinx.coroutines.flow.Flow

/**
 * The search history, kept short on purpose.
 *
 * The cap is enforced by the insert rather than by the read, so the table never grows past it: a
 * history that is only trimmed when a screen opens is a history that has been storing every keystroke
 * that reached the network in the meantime.
 */
class RecentSearchDao(private val db: AppDatabase) {

    suspend fun insert(query: String, timestamp: Long, keep: Int) = db.execTogether(
        "INSERT OR REPLACE INTO recent_search(query,timestamp) VALUES(?,?)" to arrayOf<Any?>(query, timestamp),
        "DELETE FROM recent_search WHERE query NOT IN " +
            "(SELECT query FROM recent_search ORDER BY timestamp DESC LIMIT ?)" to arrayOf<Any?>(keep),
    )

    fun getRecent(): Flow<List<String>> = db.observe {
        db.query("SELECT query FROM recent_search ORDER BY timestamp DESC", mapper = { it.getString("query") })
    }

    suspend fun delete(query: String) = db.exec("DELETE FROM recent_search WHERE query = ?", query)

    suspend fun clear() = db.exec("DELETE FROM recent_search")
}

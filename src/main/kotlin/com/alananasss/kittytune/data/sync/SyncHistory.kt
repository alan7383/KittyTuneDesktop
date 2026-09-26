package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.core.Prefs
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One exchange with a paired device, as the sync page lists it. */
data class SyncRecord(
    val atMs: Long,
    val deviceName: String,
    val isSuccess: Boolean,
    val receivedListens: Int = 0,
    val receivedLikes: Int = 0,
    val sentListens: Int = 0,
    val sentLikes: Int = 0,
    /** Why it failed, when it did. */
    val error: String? = null,
) {
    val movedAnything: Boolean get() = receivedListens + receivedLikes + sentListens + sentLikes > 0
}

/**
 * The last exchanges, newest first: what the sync page shows as "last sync" and "history".
 *
 * Exchanges that moved nothing are only kept when they are the newest — a heartbeat every few minutes would
 * otherwise push every interesting entry out of the list within an hour.
 */
object SyncHistory {
    private const val KEY = "sync_history"
    private const val LIMIT = 40
    private val gson = Gson()

    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<SyncRecord>> = _records

    @Synchronized
    fun add(record: SyncRecord) {
        val kept = _records.value.let { list -> if (list.firstOrNull()?.movedAnything == false) list.drop(1) else list }
        val next = (listOf(record) + kept).take(LIMIT)
        _records.value = next
        Prefs.putString(KEY, gson.toJson(next))
    }

    fun clear() {
        _records.value = emptyList()
        Prefs.putString(KEY, null)
    }

    private fun load(): List<SyncRecord> = runCatching {
        val raw = Prefs.getString(KEY, null) ?: return emptyList()
        gson.fromJson<List<SyncRecord>>(raw, object : TypeToken<List<SyncRecord>>() {}.type).orEmpty()
    }.getOrDefault(emptyList())
}

package com.alananasss.kittytune.data.sync

import kotlinx.coroutines.CompletableDeferred

/** Commands belong to one authenticated session; replacement sessions cannot acknowledge them. */
internal class ConnectPendingCommands<Owner : Any>(private val perSessionLimit: Int = 32) {
    private data class Entry<Owner>(val owner: Owner, val completion: CompletableDeferred<ConnectMessage>)
    private val entries = HashMap<String, Entry<Owner>>()

    @Synchronized fun register(owner: Owner, id: String): CompletableDeferred<ConnectMessage> {
        check(id !in entries) { "Duplicate command" }
        check(entries.values.count { it.owner === owner } < perSessionLimit) { "Device is busy; wait for the previous command" }
        return CompletableDeferred<ConnectMessage>().also { entries[id] = Entry(owner, it) }
    }

    fun acknowledge(owner: Owner, message: ConnectMessage): Boolean {
        val completion = synchronized(this) {
            val entry = entries[message.id]?.takeIf { it.owner === owner } ?: return false
            entries.remove(message.id)
            entry.completion
        }
        return completion.complete(message)
    }

    fun disconnect(owner: Owner) {
        val cancelled = synchronized(this) {
            val owned = entries.filterValues { it.owner === owner }
            owned.keys.forEach(entries::remove)
            owned.values.map { it.completion }
        }
        cancelled.forEach { it.completeExceptionally(java.io.IOException("Device disconnected")) }
    }

    @Synchronized fun discard(id: String, completion: CompletableDeferred<ConnectMessage>) {
        if (entries[id]?.completion === completion) entries.remove(id)
    }
}

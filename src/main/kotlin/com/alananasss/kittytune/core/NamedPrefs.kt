package com.alananasss.kittytune.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * A simple JSON-file-backed key/value store, the desktop replacement for a single
 * named Android SharedPreferences (other than the main "player_state", which is [Prefs]).
 *
 * Used for the standalone Android pref namespaces: achievements_prefs, soundtune_likes_v3,
 * update_cache, etc. — keeps the same file/key names for backup compatibility.
 *
 * ## Two instances of the same name are the same store
 *
 * Every write rewrites the whole file from the instance's own map, so two instances for one name used to
 * silently delete each other's keys: whichever saved last wrote a file missing everything the other had
 * added since it loaded. Three separate objects in the sync layer each held a `NamedPrefs("sync_state")`,
 * which meant remembering a paired device could erase the pairing secret, or the sync marks — and losing
 * marks makes a peer resend history that has already been counted (issue #33).
 *
 * So the map is shared per file rather than per instance, loaded once. `NamedPrefs("x")` is now a handle
 * onto one store, not a copy of it, and callers need not coordinate.
 */
class NamedPrefs(name: String) {

    private val file = File(AppDirs.dataDir, "$name.json")
    private val values = shared(file)

    private fun save() {
        // Locked on the shared map, so two threads writing different keys of the same file cannot
        // interleave a read of the map with a write of the file.
        synchronized(values) {
            try {
                val obj = buildJsonObject { values.forEach { (k, v) -> put(k, v) } }
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(JsonElement.serializer(), obj))
            } catch (_: Exception) {
            }
        }
    }

    fun getInt(key: String, def: Int): Int = (values[key] as? JsonPrimitive)?.intOrNull ?: def
    fun getLong(key: String, def: Long): Long = (values[key] as? JsonPrimitive)?.longOrNull ?: def
    fun getBoolean(key: String, def: Boolean): Boolean = (values[key] as? JsonPrimitive)?.booleanOrNull ?: def
    fun getString(key: String, def: String?): String? = (values[key] as? JsonPrimitive)?.contentOrNull ?: def

    fun putInt(key: String, value: Int) { values[key] = JsonPrimitive(value); save() }
    fun putLong(key: String, value: Long) { values[key] = JsonPrimitive(value); save() }
    fun putBoolean(key: String, value: Boolean) { values[key] = JsonPrimitive(value); save() }
    fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = JsonPrimitive(value)
        save()
    }

    fun remove(key: String) { values.remove(key); save() }
    fun contains(key: String): Boolean = values.containsKey(key)
    fun clear() { values.clear(); save() }

    /** Raw snapshot (for BackupManager). */
    fun all(): Map<String, JsonElement> = values.toMap()

    fun restore(entries: Map<String, JsonElement>) {
        values.clear()
        values.putAll(entries)
        save()
    }

    private companion object {
        private val json = Json { prettyPrint = true }

        /** One map per file, for the lifetime of the process. */
        private val stores = ConcurrentHashMap<String, ConcurrentHashMap<String, JsonElement>>()

        fun shared(file: File): ConcurrentHashMap<String, JsonElement> =
            stores.getOrPut(file.absolutePath) { load(file) }

        private fun load(file: File): ConcurrentHashMap<String, JsonElement> {
            val values = ConcurrentHashMap<String, JsonElement>()
            try {
                if (file.exists()) {
                    json.parseToJsonElement(file.readText()).jsonObject
                        .forEach { (k, v) -> values[k] = v }
                }
            } catch (_: Exception) {
            }
            return values
        }
    }
}

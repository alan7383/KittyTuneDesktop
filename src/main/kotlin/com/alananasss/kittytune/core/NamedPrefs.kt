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
 */
class NamedPrefs(name: String) {

    private val file = File(AppDirs.dataDir, "$name.json")
    private val json = Json { prettyPrint = true }
    private val values = ConcurrentHashMap<String, JsonElement>()

    init {
        load()
    }

    private fun load() {
        try {
            if (file.exists()) {
                json.parseToJsonElement(file.readText()).jsonObject.forEach { (k, v) -> values[k] = v }
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun save() {
        try {
            val obj = buildJsonObject { values.forEach { (k, v) -> put(k, v) } }
            file.writeText(json.encodeToString(JsonElement.serializer(), obj))
        } catch (_: Exception) {
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
}

package com.alananasss.kittytune.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Desktop replacement for Android SharedPreferences, keeping the SAME KEY NAMES
 * as the Android app so BackupManager import/export stays compatible.
 *
 * Backed by a single JSON file (%APPDATA%\KittyTune\prefs.json), with a reactive
 * change stream replacing OnSharedPreferenceChangeListener.
 */
object Prefs {

    private val file = File(AppDirs.dataDir, "prefs.json")
    private val backupFile = File(AppDirs.dataDir, "prefs.json.bak")
    // Compact on purpose: the file is rewritten after every change, and pretty-printing it only made each write bigger.
    private val json = Json { prettyPrint = false }
    private val writer = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "prefs-writer").apply { isDaemon = true }
    }

    private val state: MutableStateFlow<Map<String, JsonElement>> = MutableStateFlow(load())

    /** Reactive view of the whole pref map — collect + map to observe a single key. */
    val flow: StateFlow<Map<String, JsonElement>> get() = state

    /**
     * Loads the prefs map from `prefs.json`. If the primary file is missing or corrupt (partial
     * write that didn't finish atomically, disk error, encoding issue), falls back to
     * `prefs.json.bak` — the previous successful write — before giving up and returning an empty
     * map. Previously a corrupt `prefs.json` silently lost every preference (theme, volume,
     * proxy, download location — the lot) with no backup and no log (audit issue).
     */
    private fun load(): Map<String, JsonElement> {
        return try {
            if (file.exists()) json.parseToJsonElement(file.readText()).jsonObject else emptyMap()
        } catch (primary: Exception) {
            // The primary file is corrupt. Try the backup before falling back to empty — losing
            // all prefs on a single bad write was the silent data-loss bug the audit flagged.
            try {
                if (backupFile.exists()) {
                    println("Prefs: primary prefs.json failed to parse (${primary.message}), falling back to prefs.json.bak")
                    json.parseToJsonElement(backupFile.readText()).jsonObject
                } else {
                    println("Prefs: primary prefs.json failed to parse (${primary.message}) and no backup exists; starting fresh")
                    emptyMap()
                }
            } catch (backup: Exception) {
                println("Prefs: both prefs.json and prefs.json.bak failed to parse (${backup.message}); starting fresh")
                emptyMap()
            }
        }
    }

    @Volatile
    private var savePending = false

    fun flush(force: Boolean = false) {
        if (!savePending && !force) return
        savePending = false
        try {
            val obj = buildJsonObject { state.value.forEach { (k, v) -> put(k, v) } }
            val tmp = File(file.parentFile, "prefs.json.tmp")
            tmp.writeText(json.encodeToString(JsonElement.serializer(), obj))
            // Back up the previous good file before replacing it. If the replace succeeds, the
            // backup stays for next time; if it fails, the backup is the recovery path for the
            // next load(). The previous code had no backup, so a single bad write lost all prefs.
            if (file.exists()) {
                runCatching {
                    @Suppress("DEPRECATION")
                    file.copyTo(backupFile, overwrite = true)
                }
            }
            // The delete-then-rename this replaced had a window with no file at all, and plain
            // renameTo fails on Windows while a reader holds prefs.json open.
            AtomicFiles.replace(tmp, file)
        } catch (_: Exception) {
        }
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            flush()
        })
    }

    private fun scheduleSave() {
        if (savePending) return
        savePending = true
        writer.schedule({
            flush()
        }, 150, TimeUnit.MILLISECONDS)
    }

    private fun put(key: String, value: JsonElement?) {
        state.value = if (value == null) state.value - key else state.value + (key to value)
        scheduleSave()
    }

    // --- SharedPreferences-shaped API ------------------------------------------------------

    fun getBoolean(key: String, def: Boolean): Boolean =
        (state.value[key] as? JsonPrimitive)?.booleanOrNull ?: def

    fun getInt(key: String, def: Int): Int =
        (state.value[key] as? JsonPrimitive)?.intOrNull ?: def

    fun getLong(key: String, def: Long): Long =
        (state.value[key] as? JsonPrimitive)?.longOrNull ?: def

    fun getFloat(key: String, def: Float): Float =
        (state.value[key] as? JsonPrimitive)?.floatOrNull
            ?: (state.value[key] as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: def

    fun getString(key: String, def: String?): String? =
        (state.value[key] as? JsonPrimitive)?.contentOrNull ?: def

    private val SET_SEP = 31.toChar().toString()  // unit separator (U+001F)
    fun getStringSet(key: String, def: Set<String>): Set<String> = getString(key, null)?.split(SET_SEP)?.filter { it.isNotEmpty() }?.toSet() ?: def

    fun putBoolean(key: String, value: Boolean) = put(key, JsonPrimitive(value))
    fun putInt(key: String, value: Int) = put(key, JsonPrimitive(value))
    fun putLong(key: String, value: Long) = put(key, JsonPrimitive(value))
    fun putFloat(key: String, value: Float) = put(key, JsonPrimitive(value))
    fun putString(key: String, value: String?) =
        put(key, value?.let { JsonPrimitive(it) })

    fun putStringSet(key: String, value: Set<String>) =
        putString(key, value.joinToString(SET_SEP))

    fun remove(key: String) = put(key, null)

    fun contains(key: String): Boolean = state.value.containsKey(key)

    /** All keys/values as raw JSON — used by BackupManager. */
    fun snapshot(): Map<String, JsonElement> = state.value

    fun restore(entries: Map<String, JsonElement>) {
        state.value = entries
        scheduleSave()
    }

    // --- Typed observation helpers ---------------------------------------------------------

    fun booleanFlow(key: String, def: Boolean): Flow<Boolean> =
        state.map { (it[key] as? JsonPrimitive)?.booleanOrNull ?: def }

    fun intFlow(key: String, def: Int): Flow<Int> =
        state.map { (it[key] as? JsonPrimitive)?.intOrNull ?: def }

    fun stringFlow(key: String, def: String?): Flow<String?> =
        state.map { (it[key] as? JsonPrimitive)?.contentOrNull ?: def }

    fun floatFlow(key: String, def: Float): Flow<Float> =
        state.map { m -> (m[key] as? JsonPrimitive)?.let { it.floatOrNull ?: it.doubleOrNull?.toFloat() } ?: def }
}

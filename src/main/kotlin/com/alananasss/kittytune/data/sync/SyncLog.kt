package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.NamedPrefs
import com.google.gson.Gson
import java.io.File
import java.util.UUID

/**
 * The device's own append-only log, on disk (issue #33).
 *
 * One JSON object per line. Append-only is not a style choice here: it is what makes the file safe
 * to read while it is being written, cheap to add to, and impossible to corrupt retroactively — a
 * half-written last line is dropped on load and nothing before it is affected.
 *
 * A line that will not parse is skipped rather than fatal. A log that refuses to load would take the
 * whole listening history with it, and one bad line costs one event.
 */
object SyncLog {

    private val gson = Gson()
    private val file: File by lazy { File(AppDirs.dataDir, "sync_log.jsonl") }
    private val prefs by lazy { NamedPrefs("sync_state") }

    /** In memory as well as on disk: the whole log is read for every merge, and it is small. */
    private val events = mutableListOf<SyncEvent>()
    private var loaded = false

    /**
     * This install's identity, generated once.
     *
     * Not derived from the hostname or the MAC address: both change, and an identity that changes
     * makes every past event look like it came from a device we have never met.
     */
    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            .also { prefs.putString(KEY_DEVICE_ID, it) }
    }

    /** A name for the pairing screen, so two devices are told apart by something readable. */
    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
            ?: defaultDeviceName()
        set(value) = prefs.putString(KEY_DEVICE_NAME, value.trim().take(64))

    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_MARKS = "marks"

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!file.isFile) return
        file.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val event = runCatching { gson.fromJson(line, SyncEvent::class.java) }.getOrNull()
            if (event != null && event.isWellFormed) events.add(event)
        }
    }

    /** Everything the log holds, ours and every peer's. A copy: callers iterate while we append. */
    @Synchronized
    fun all(): List<SyncEvent> {
        ensureLoaded()
        return events.toList()
    }

    @Synchronized
    fun size(): Int {
        ensureLoaded()
        return events.size
    }

    /**
     * Records something that happened here.
     *
     * @return the event as written, with the sequence number it was given.
     */
    @Synchronized
    fun append(kind: String, payload: Any, timestampMs: Long = System.currentTimeMillis()): SyncEvent {
        ensureLoaded()
        val event = SyncEvent(
            deviceId = deviceId,
            seq = SyncMerge.nextSeq(events, deviceId),
            timestampMs = timestampMs,
            kind = kind,
            payload = gson.toJson(payload),
        )
        write(listOf(event))
        return event
    }

    /**
     * Records events that came from a peer. Already-known ones are skipped, so calling this twice
     * with the same batch is not the same as playing it twice.
     *
     * @return exactly the events that were new, for whoever has to apply them.
     */
    @Synchronized
    fun merge(incoming: List<SyncEvent>): List<SyncEvent> {
        ensureLoaded()
        val fresh = SyncMerge.selectNew(incoming, marks(), deviceId)
        if (fresh.isEmpty()) return emptyList()
        write(fresh)
        setMarks(SyncMerge.advance(marks(), fresh))
        return fresh
    }

    private fun write(batch: List<SyncEvent>) {
        events.addAll(batch)
        runCatching {
            file.parentFile?.mkdirs()
            // One open, one append, one line each: a crash mid-batch leaves whole lines behind it.
            file.appendText(batch.joinToString("") { gson.toJson(it) + "\n" })
        }
    }

    /** How far we have got with each device we have heard from. */
    @Synchronized
    fun marks(): Map<String, Long> {
        val raw = prefs.getString(KEY_MARKS, null) ?: return emptyMap()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(raw, Map::class.java) as Map<String, Double>)
                .mapValues { it.value.toLong() }
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    private fun setMarks(marks: Map<String, Long>) {
        prefs.putString(KEY_MARKS, gson.toJson(marks))
    }

    /**
     * What a peer last told us it holds, so the next exchange sends only what is new.
     *
     * Empty for a device we have never talked to, which means "send everything" — correct, because
     * the peer drops what it already has. It only costs bandwidth on the very first exchange.
     */
    @Synchronized
    fun peerMarks(peerDeviceId: String): Map<String, Long> {
        val raw = prefs.getString(keyPeerMarks(peerDeviceId), null) ?: return emptyMap()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(raw, Map::class.java) as Map<String, Double>)
                .mapValues { it.value.toLong() }
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun setPeerMarks(peerDeviceId: String, marks: Map<String, Long>) {
        if (peerDeviceId.isBlank()) return
        prefs.putString(keyPeerMarks(peerDeviceId), gson.toJson(marks))
    }

    private fun keyPeerMarks(peerDeviceId: String) = "$PEER_MARKS_PREFIX$peerDeviceId"

    private const val PEER_MARKS_PREFIX = "peer_marks_"

    /**
     * Wipes the log and everything remembered about how far each device has got. Used by "clear my
     * statistics".
     *
     * Both kinds of mark go, ours and the peers'. Clearing only ours left every peer believing we still
     * held its history, so it sent nothing on the next exchange and the cleared statistics stayed
     * cleared — while the peer's own copy was untouched and would reappear piecemeal. Zeroing both makes
     * the next exchange a fresh first one (issue #33).
     *
     * This device's own id and name survive on purpose. An id that changed here would make every event
     * recorded afterwards look like it came from a stranger, and the peers would then count the same
     * listens twice under two identities.
     */
    @Synchronized
    fun clear() {
        events.clear()
        loaded = true
        prefs.remove(KEY_MARKS)
        prefs.all().keys
            .filter { it.startsWith(PEER_MARKS_PREFIX) }
            .forEach { prefs.remove(it) }
        runCatching { file.delete() }
    }

    private fun defaultDeviceName(): String {
        val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
        return host?.takeIf { it.isNotBlank() } ?: "Desktop"
    }
}

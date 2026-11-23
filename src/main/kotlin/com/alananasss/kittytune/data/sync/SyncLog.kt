package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.core.AppDirs
import com.alananasss.kittytune.core.NamedPrefs
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

/**
 * The device's own append-only log, on disk (issue #33).
 *
 * Each line can be recovered independently. Keep older lines so a newly paired device can still
 * receive them and a missing statistics table can be rebuilt from the journal.
 */
object SyncLog {

    private val gson = Gson()
    private val file: File by lazy { File(AppDirs.dataDir, "sync_log.jsonl") }
    private val prefs by lazy { NamedPrefs("sync_state") }

    private val maxSeqByDevice = HashMap<String, Long>()
    private val eventIds = HashSet<String>()
    private var receivedMarks = emptyMap<String, Long>()
    private var loaded = false

    /**
     * The parsed events, cached in RAM so [all] doesn't re-parse the file on every call.
     *
     * `all()` used to re-read and re-parse the entire JSONL file on every call — called up to 1,000
     * times per sync cycle (once per drain round), plus on every SyncLikes/SyncApply/SyncService path.
     * After months of use the file is megabytes; each sync round re-parsed it. The cache is built
     * once in [ensureLoaded] and extended in [write]. [all] returns a copy (audit Phase 5 IO gain).
     */
    private val eventCache = ArrayList<SyncEvent>()

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
        maxSeqByDevice.clear()
        eventIds.clear()
        eventCache.clear()
        if (file.exists()) {
            file.forEachLine { line -> parse(line)?.let(::index) }
        }
        // An older build could acknowledge a write that failed. Ask the peer again for any
        // acknowledged tail that is missing from the copy on disk.
        receivedMarks = readMarks(KEY_MARKS)
            .mapValues { (origin, mark) -> minOf(mark, maxSeqByDevice[origin] ?: 0L) }
            .filterValues { it > 0L }
        loaded = true
    }

    private fun index(event: SyncEvent) {
        if (!eventIds.add(event.id)) return
        val known = maxSeqByDevice[event.deviceId]
        if (known == null || event.seq > known) maxSeqByDevice[event.deviceId] = event.seq
        eventCache.add(event)
    }

    private fun parse(line: String): SyncEvent? {
        if (line.isBlank()) return null
        val event = runCatching { gson.fromJson(line, SyncEvent::class.java) }.getOrNull() ?: return null
        return event.takeIf { it.isWellFormed }
    }

    /**
     * Everything the log holds, ours and every peer's. Returns the in-memory cache (a copy) instead
     * of re-parsing the file on every call — the cache is built once in [ensureLoaded] and extended
     * in [write] (audit Phase 5 IO gain).
     */
    @Synchronized
    fun all(): List<SyncEvent> {
        ensureLoaded()
        return ArrayList(eventCache)
    }

    @Synchronized
    fun size(): Int {
        ensureLoaded()
        return eventCache.size
    }

    /**
     * Records something that happened here.
     *
     * The sequence number comes from the RAM index, which is built from the file itself — so a log
     * restored from a backup still cannot hand out a number that is already in use, exactly as when
     * the whole log was resident.
     *
     * @return the event as written, with the sequence number it was given.
     */
    @Synchronized
    fun append(kind: String, payload: Any, timestampMs: Long = System.currentTimeMillis()): SyncEvent {
        ensureLoaded()
        val event = SyncEvent(
            deviceId = deviceId,
            seq = (maxSeqByDevice[deviceId] ?: 0L) + 1,
            timestampMs = timestampMs,
            kind = kind,
            payload = gson.toJson(payload),
        )
        write(listOf(event))
        return event
    }

    data class BatchItem(val kind: String, val payload: Any, val timestampMs: Long = System.currentTimeMillis())

    @Synchronized
    fun appendBatch(items: List<BatchItem>): List<SyncEvent> {
        if (items.isEmpty()) return emptyList()
        ensureLoaded()
        var currentSeq = (maxSeqByDevice[deviceId] ?: 0L) + 1
        val created = ArrayList<SyncEvent>(items.size)
        for (item in items) {
            created.add(
                SyncEvent(
                    deviceId = deviceId,
                    seq = currentSeq++,
                    timestampMs = item.timestampMs,
                    kind = item.kind,
                    payload = gson.toJson(item.payload),
                )
            )
        }
        write(created)
        return created
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
        val candidates = SyncMerge.selectNew(incoming, receivedMarks, deviceId)
        val fresh = candidates.filterNot { it.id in eventIds }
        if (fresh.isNotEmpty()) write(fresh)
        val next = SyncMerge.advance(receivedMarks, candidates)
        if (next != receivedMarks) {
            prefs.putString(KEY_MARKS, gson.toJson(next))
            receivedMarks = next
        }
        return fresh
    }

    private fun write(batch: List<SyncEvent>) {
        val bytes = batch.joinToString("\n", postfix = "\n") { gson.toJson(it) }.toByteArray()
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { output ->
            val originalSize = output.length()
            try {
                if (originalSize > 0) {
                    output.seek(originalSize - 1)
                    // Keep a line interrupted by a crash separate from the new batch.
                    if (output.read() != '\n'.code) output.write('\n'.code)
                }
                output.write(bytes)
                output.fd.sync()
            } catch (failure: IOException) {
                // A failed append must leave the same sequence numbers available for the retry.
                try {
                    output.setLength(originalSize)
                    output.fd.sync()
                } catch (rollback: IOException) {
                    loaded = false
                    failure.addSuppressed(rollback)
                }
                throw failure
            }
        }
        batch.forEach(::index)
    }

    /** How far we have got with each device we have heard from. */
    @Synchronized
    fun marks(): Map<String, Long> {
        ensureLoaded()
        return receivedMarks
    }

    /**
     * What a peer last told us it holds, so the next exchange sends only what is new.
     *
     * Empty for a device we have never talked to, which means "send everything" — correct, because
     * the peer drops what it already has. It only costs bandwidth on the very first exchange.
     */
    @Synchronized
    fun peerMarks(peerDeviceId: String): Map<String, Long> = readMarks(keyPeerMarks(peerDeviceId))

    private fun readMarks(key: String): Map<String, Long> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
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
        java.nio.file.Files.deleteIfExists(file.toPath())
        maxSeqByDevice.clear()
        eventIds.clear()
        eventCache.clear()
        receivedMarks = emptyMap()
        loaded = true
        prefs.remove(KEY_MARKS)
        prefs.all().keys
            .filter { it.startsWith(PEER_MARKS_PREFIX) }
            .forEach { prefs.remove(it) }
    }

    private fun defaultDeviceName(): String {
        val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()
        return host?.takeIf { it.isNotBlank() } ?: "Desktop"
    }
}

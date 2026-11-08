package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object TrackLoudnessRepository {

    private val cache = BoundedCache<Long, TrackLoudnessInfo>(1000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeScans = ConcurrentHashMap<Long, Job>()

    /**
     * Cache-fronted lookup. On a miss, reads from the DB through the single-thread `dbDispatcher`
     * (via [AppDatabase.withDb]) so the read can't collide with an open transaction on the same
     * connection. Previously this used the raw JDBC connection off the dispatcher, which could
     * race with a concurrent `execBatch` transaction and either throw or see uncommitted state
     * (audit issue).
     */
    suspend fun getLoudness(trackId: Long): TrackLoudnessInfo? {
        cache[trackId]?.let { return it }
        val fromDb = loadFromDatabase(trackId)
        if (fromDb != null) {
            cache[trackId] = fromDb
            return fromDb
        }
        return null
    }

    fun saveLoudness(trackId: Long, lufs: Float, truePeakDb: Float) {
        if (lufs <= -60f || lufs >= 0f) return
        val info = TrackLoudnessInfo(lufs, truePeakDb)
        cache[trackId] = info
        scope.launch {
            saveToDatabase(trackId, lufs, truePeakDb)
        }
    }

    fun scanTrackAsync(
        track: Track,
        url: String,
        headers: Map<String, String> = emptyMap(),
        onComplete: ((TrackLoudnessInfo) -> Unit)? = null
    ) {
        if (com.alananasss.kittytune.audio.WindowsPlayReadyRuntime.tokenFor(url) != null) return
        // getLoudness is now suspend, but we want scanTrackAsync to stay non-suspend (it's called
        // from non-suspend contexts). Check the cache synchronously; the DB miss is handled by the
        // launched scan below. The previous `if (getLoudness(track.id) != null) return` would have
        // also paid the DB read on a cache miss — the new version skips that and lets the scan
        // discover an existing entry (it calls saveLoudness which is a no-op for duplicates via
        // the ON CONFLICT clause).
        if (cache[track.id] != null) return
        if (activeScans.containsKey(track.id)) return

        val job = scope.launch {
            try {
                // Three seeked windows rather than the whole stream: known in a second or two, so
                // the gain is right early in the track instead of a minute in.
                val scanned = AudioScannerManager.scanStreamWindows(url, headers, track.effectiveFullDurationMs)
                if (scanned != null) {
                    saveLoudness(track.id, scanned.integratedLufs, scanned.truePeakDb)
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(scanned)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                activeScans.remove(track.id)
            }
        }
        activeScans[track.id] = job
    }

    // ── Upcoming tracks ─────────────────────────────────────────────────────────────────────────

    private const val LOOKAHEAD = 3
    private const val MAX_ATTEMPTS = 2

    private val prefetchLock = Any()
    private var prefetchPlan: List<Track> = emptyList()
    private var prefetchWorker: Job? = null
    private var prefetchInFlight: Long? = null
    /** Tracks that could not be measured this session, so a dead stream is not retried forever. */
    private val prefetchAttempts = HashMap<Long, Int>()

    /**
     * Measures the tracks coming up in the queue while the current one plays, so each starts at its
     * own level instead of learning it on air.
     *
     * A small queue worker: the next [LOOKAHEAD] of [upcoming], one at a time and in play order,
     * skipping any already in the cache or the database. A queue edit only replaces what is still to
     * do; the track being measured is finished if it is still coming up and dropped if it is not.
     * Each is a three-window estimate; a track played to its end stores its full measurement over it.
     */
    fun prefetch(upcoming: List<Track>) {
        synchronized(prefetchLock) {
            prefetchPlan = upcoming.distinctBy { it.id }.take(LOOKAHEAD)
            val inFlight = prefetchInFlight
            if (inFlight != null && prefetchPlan.none { it.id == inFlight }) {
                prefetchWorker?.cancel()
                prefetchWorker = null
                prefetchInFlight = null
            }
            if (prefetchWorker?.isActive == true) return
            prefetchWorker = scope.launch { drainPrefetch() }
        }
    }

    private suspend fun drainPrefetch() {
        val self = kotlin.coroutines.coroutineContext[Job]
        while (self?.isActive == true) {
            val candidates = synchronized(prefetchLock) {
                prefetchPlan.filter { (prefetchAttempts[it.id] ?: 0) < MAX_ATTEMPTS && !activeScans.containsKey(it.id) }
            }
            // The database check is a suspend read, so it runs outside the lock.
            val track = candidates.firstOrNull { getLoudness(it.id) == null } ?: break
            val stillPlanned = synchronized(prefetchLock) {
                prefetchPlan.any { it.id == track.id }.also { if (it) prefetchInFlight = track.id }
            }
            if (!stillPlanned) continue
            val ok = runCatching { measureUpcoming(track) }.getOrDefault(false)
            synchronized(prefetchLock) {
                prefetchInFlight = null
                if (!ok && self?.isActive == true) prefetchAttempts[track.id] = (prefetchAttempts[track.id] ?: 0) + 1
            }
        }
        synchronized(prefetchLock) { if (prefetchWorker == self) prefetchWorker = null }
    }

    private suspend fun measureUpcoming(track: Track): Boolean {
        val url = StreamResolver.resolveStream(track) ?: return false
        val headers = mutableMapOf("User-Agent" to "SoundCloud/2025.12.10-release (Android 10; Android)")
        if (track.source != "youtube" && track.source != "youtube_music") {
            headers["Origin"] = "https://soundcloud.com"
            headers["Referer"] = "https://soundcloud.com/"
        }
        val scanned = AudioScannerManager.scanStreamWindows(url, headers, track.effectiveFullDurationMs) ?: return false
        // The track may have started, and been measured on air, while this ran.
        if (cache[track.id] != null) return true
        saveLoudness(track.id, scanned.integratedLufs, scanned.truePeakDb)
        println("[TrackLoudness] Estimated ${track.title}: ${"%.1f".format(scanned.integratedLufs)} LUFS")
        return true
    }

    /**
     * Routed through the database's single-thread dispatcher via [AppDatabase.withDb]. The
     * previous version used the raw JDBC connection directly, which could land inside an open
     * `execBatch` transaction on the same connection and either throw `SQLException` or see
     * uncommitted state (audit issue).
     */
    private suspend fun loadFromDatabase(trackId: Long): TrackLoudnessInfo? {
        return try {
            AppDatabase.withDb {
                val conn = AppDatabase.raw()
                conn.prepareStatement("SELECT integratedLufs, truePeakDb FROM track_loudness WHERE trackId = ?").use { st ->
                    st.setLong(1, trackId)
                    st.executeQuery().use { rs ->
                        if (rs.next()) {
                            TrackLoudnessInfo(
                                integratedLufs = rs.getFloat("integratedLufs"),
                                truePeakDb = rs.getFloat("truePeakDb")
                            )
                        } else null
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun saveToDatabase(trackId: Long, lufs: Float, truePeakDb: Float) {
        try {
            // Routed through the database's single-thread dispatcher: on the raw
            // connection this write could land inside an open execBatch transaction
            // and commit it mid-flight, or be rolled back with it (issue #33).
            AppDatabase.withDb {
                val conn = AppDatabase.raw()
                conn.prepareStatement(
                    "INSERT INTO track_loudness (trackId, integratedLufs, truePeakDb, updatedAt) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(trackId) DO UPDATE SET integratedLufs = excluded.integratedLufs, truePeakDb = excluded.truePeakDb, updatedAt = excluded.updatedAt"
                ).use { st ->
                    st.setLong(1, trackId)
                    st.setFloat(2, lufs)
                    st.setFloat(3, truePeakDb)
                    st.setLong(4, System.currentTimeMillis())
                    st.executeUpdate()
                }
            }
        } catch (_: Throwable) {}
    }
}

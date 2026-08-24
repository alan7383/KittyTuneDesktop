package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Fills in the release date of Spotify catalog tracks one row at a time.
 *
 * Spotify's bulk payloads (playlist, search, artist top tracks, radio) carry no date at all —
 * only the single-track query and the album node expose one. Resolving a whole playlist up
 * front would mean one request per track, so rows ask for their own date as they compose,
 * which in a lazy list means only the rows actually on screen. Answers are cached on disk,
 * so scrolling back over a row — or reopening the playlist tomorrow — costs nothing.
 */
object SpotifyReleaseDateResolver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { NamedPrefs("spotify_release_dates") }

    /** Bounded LRU of date states, keyed by the app-side track id. */
    private val states: MutableMap<Long, MutableStateFlow<String?>> = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<Long, MutableStateFlow<String?>>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, MutableStateFlow<String?>>): Boolean =
                size > 1000
        }
    )

    /** Tracks Spotify had no date for; retried on the next launch only. */
    private val exhausted = ConcurrentHashMap.newKeySet<Long>()
    private val inFlight = ConcurrentHashMap<Long, Job>()
    private val semaphore = Semaphore(4)

    fun stateFor(trackId: Long): StateFlow<String?> = flowFor(trackId)

    private fun flowFor(trackId: Long): MutableStateFlow<String?> =
        synchronized(states) {
            states.getOrPut(trackId) { MutableStateFlow(prefs.getString(key(trackId), null)) }
        }

    private fun key(trackId: Long) = "date_$trackId"

    fun requestResolve(track: Track) {
        if (track.source != "spotify") return
        if (!track.releaseDate.isNullOrBlank()) return
        val spotifyId = spotifyIdOf(track) ?: return

        val id = track.id
        val state = flowFor(id)
        if (state.value != null) return
        if (id in exhausted || inFlight.containsKey(id)) return

        val job = scope.launch {
            val date = runCatching {
                semaphore.withPermit { SpotifyRepository.getTrackReleaseDate(spotifyId) }
            }.getOrNull()
            if (date.isNullOrBlank()) {
                exhausted.add(id)
            } else {
                state.value = date
                runCatching { prefs.putString(key(id), date) }
            }
        }
        val prior = inFlight.putIfAbsent(id, job)
        if (prior != null) job.cancel() else job.invokeOnCompletion { inFlight.remove(id) }
    }

    /** The base62 id catalog tracks carry as their permalink, or one dug out of the share URL. */
    private fun spotifyIdOf(track: Track): String? {
        track.permalink
            ?.takeIf { it.isNotBlank() && !it.contains('/') && !it.contains(':') && !it.contains('.') }
            ?.let { return it }
        track.permalinkUrl
            ?.takeIf { it.contains("/track/") || it.startsWith("spotify") }
            ?.let { return SpotifyRepository.extractId(it).ifBlank { null } }
        return null
    }
}

package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.usablePlaylistCover
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
 * Resolves the cover of a playlist that doesn't ship one, by finding the first track
 * that has real artwork — the same thing you see once you open the playlist.
 *
 * Library listings return playlists without their tracks, so a coverless playlist had
 * nothing to fall back on and rendered the shared picsum placeholder. This fills that
 * gap lazily: rows ask for a cover, get one when it lands, and the answer is cached on
 * disk so the next launch doesn't re-fetch.
 */
object PlaylistCoverResolver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api: SoundCloudApi by lazy { RetrofitClient.create() }
    private val prefs by lazy { NamedPrefs("playlist_covers") }

    /** Bounded LRU of cover states, keyed by playlist id. */
    private val states: MutableMap<Long, MutableStateFlow<String?>> = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<Long, MutableStateFlow<String?>>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, MutableStateFlow<String?>>): Boolean =
                size > 500
        }
    )

    /** Playlists we already looked at and found nothing for; retried on next launch only. */
    private val exhausted = ConcurrentHashMap.newKeySet<Long>()
    private val inFlight = ConcurrentHashMap<Long, Job>()
    private val semaphore = Semaphore(3)

    @Volatile
    private var cooldownUntil = 0L
    @Volatile
    private var cooldownStepMs = 60_000L

    fun stateFor(playlistId: Long): StateFlow<String?> = flowFor(playlistId)

    private fun flowFor(playlistId: Long): MutableStateFlow<String?> =
        synchronized(states) {
            states.getOrPut(playlistId) { MutableStateFlow(prefs.getString(key(playlistId), null)) }
        }

    private fun key(playlistId: Long) = "cover_$playlistId"

    fun requestResolve(playlist: Playlist) {
        val id = playlist.id
        if (id == 0L) return
        if (playlist.usableArtwork != null) return
        // Stations, system playlists and YouTube radio shortcuts carry synthetic ids that
        // /playlists/{id} knows nothing about, and their cover is legitimately an avatar.
        if (playlist.isArtistStation || playlist.isTrackStation) return
        if (playlist.urn?.startsWith("soundcloud:system-playlists:") == true) return
        if (playlist.permalinkUrl?.startsWith("yt_radio:") == true) return
        val state = flowFor(id)
        if (state.value != null) return
        if (id in exhausted || inFlight.containsKey(id)) return

        val job = scope.launch { resolve(playlist, state) }
        val prior = inFlight.putIfAbsent(id, job)
        if (prior != null) job.cancel() else job.invokeOnCompletion { inFlight.remove(id) }
    }

    private suspend fun resolve(playlist: Playlist, state: MutableStateFlow<String?>) {
        val id = playlist.id

        // Downloaded tracks answer for free, and work offline.
        localCover(id)?.let {
            publish(id, state, it)
            return
        }

        // Local-only playlists have no remote counterpart to ask about.
        if (id < 0) {
            exhausted.add(id)
            return
        }

        if (System.currentTimeMillis() < cooldownUntil) return
        val remote = runCatching {
            semaphore.withPermit { api.getPlaylist(id) }
        }.getOrElse { e ->
            if ((e as? retrofit2.HttpException)?.code() == 429) {
                cooldownUntil = System.currentTimeMillis() + cooldownStepMs
                cooldownStepMs = (cooldownStepMs * 2).coerceAtMost(600_000L)
            }
            return
        }
        cooldownStepMs = 60_000L

        remote.usableArtwork?.let {
            publish(id, state, it)
            return
        }

        // /playlists/{id} hydrates only the first handful of tracks; the rest come back as
        // bare ids. If none of the hydrated ones had artwork, ask for the next few directly.
        val stubIds = remote.tracks.orEmpty()
            .filter { it.artworkUrl.isNullOrBlank() && it.user == null }
            .map { it.id }
            .filter { it > 0 }
            .take(5)
        if (stubIds.isNotEmpty() && System.currentTimeMillis() >= cooldownUntil) {
            val hydrated = runCatching {
                semaphore.withPermit { api.getTracksByIds(stubIds.joinToString(",")) }
            }.getOrNull()
            hydrated?.firstOrNull { usablePlaylistCover(it.fullResArtwork) }
                ?.let {
                    publish(id, state, it.fullResArtwork)
                    return
                }
        }

        exhausted.add(id)
    }

    private suspend fun localCover(playlistId: Long): String? {
        val tracks = runCatching { AppDatabase.downloadDao.getTracksForPlaylistSync(playlistId) }.getOrNull()
            ?: return null
        return tracks.firstNotNullOfOrNull { local ->
            when {
                local.localArtworkPath.isNotEmpty() -> local.localArtworkPath
                usablePlaylistCover(local.artworkUrl) -> local.artworkUrl
                else -> null
            }
        }
    }

    private fun publish(playlistId: Long, state: MutableStateFlow<String?>, cover: String) {
        state.value = cover
        runCatching { prefs.putString(key(playlistId), cover) }
    }
}

package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.TrackAlbumCacheRow
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Playlist
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
 * Resolves the album a track belongs to, Spotify-style.
 *
 * SoundCloud doesn't expose an album field on tracks directly; instead we query
 * `tracks/{id}/playlists` and pick the best set flagged `is_album` / `set_type`
 * (album > ep > compilation > single), preferring the track's own uploader.
 * `publisher_metadata.album_title` is used as an instant (title-only) fast path.
 *
 * Optimized for large like lists (~4000 tracks): resolution is lazy (triggered per
 * visible row), deduplicated in-flight, concurrency-limited, and persisted in SQLite
 * including negative results (30-day TTL) so unresolvable tracks aren't re-queried.
 * Work runs in this object's own scope — a row scrolling out of view never cancels
 * a request already started; the result is cached either way (complete-and-cache).
 */
object AlbumResolver {

    data class AlbumInfo(val playlistId: Long?, val title: String)

    sealed interface AlbumUiState {
        data object Unknown : AlbumUiState
        data class Resolved(val info: AlbumInfo) : AlbumUiState
        data object None : AlbumUiState
    }

    private const val NEGATIVE_TTL_MS = 30L * 24 * 60 * 60 * 1000

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api: SoundCloudApi by lazy { RetrofitClient.create() }
    private val dao get() = AppDatabase.albumCacheDao

    private val states = ConcurrentHashMap<Long, MutableStateFlow<AlbumUiState>>()
    private val inFlight = ConcurrentHashMap<Long, Job>()
    private val semaphore = Semaphore(4)

    @Volatile private var cooldownUntil = 0L
    @Volatile private var cooldownStepMs = 60_000L

    fun init() {
        scope.launch {
            runCatching { dao.purgeExpiredNegatives(System.currentTimeMillis() - NEGATIVE_TTL_MS) }
        }
    }

    /** Observable state for a track's album. Never triggers any work by itself. */
    fun stateFor(track: Track): StateFlow<AlbumUiState> =
        states.getOrPut(track.id) { MutableStateFlow(AlbumUiState.Unknown) }

    private fun Track.isResolvable() = id > 0 && source != "youtube"

    /** Idempotent trigger, called when a row becomes visible. */
    fun requestResolve(track: Track) {
        if (!track.isResolvable()) return
        val state = states.getOrPut(track.id) { MutableStateFlow(AlbumUiState.Unknown) }
        if (state.value !is AlbumUiState.Unknown) return
        if (inFlight.containsKey(track.id)) return

        val job = scope.launch { resolve(track, state) }
        // Race-free dedup: only one launched job stays registered; losers cancel themselves.
        val prior = inFlight.putIfAbsent(track.id, job)
        if (prior != null) {
            job.cancel()
        } else {
            job.invokeOnCompletion { inFlight.remove(track.id) }
        }
    }

    /** Warm the in-memory states from SQLite in one query burst (chunks of 500). */
    fun prefetchFromDb(tracks: List<Track>) {
        val ids = tracks.filter { it.isResolvable() }
            .map { it.id }
            .filter { states[it]?.value !is AlbumUiState.Resolved }
        if (ids.isEmpty()) return
        scope.launch {
            ids.chunked(500).forEach { chunk ->
                val rows = runCatching { dao.getBatch(chunk) }.getOrNull() ?: return@launch
                rows.forEach { row ->
                    val state = states.getOrPut(row.trackId) { MutableStateFlow(AlbumUiState.Unknown) }
                    if (state.value is AlbumUiState.Unknown) state.value = row.toUiState()
                }
            }
        }
    }

    private fun TrackAlbumCacheRow.toUiState(): AlbumUiState = when {
        albumTitle != null -> AlbumUiState.Resolved(AlbumInfo(albumPlaylistId, albumTitle))
        else -> AlbumUiState.None
    }

    private suspend fun resolve(track: Track, state: MutableStateFlow<AlbumUiState>) {
        // 1. Persistent cache (cheap, before taking a network permit).
        val cached = runCatching { dao.get(track.id) }.getOrNull()
        if (cached != null) {
            if (cached.albumTitle != null || cached.resolvedAt > System.currentTimeMillis() - NEGATIVE_TTL_MS) {
                state.value = cached.toUiState()
                return
            }
            // stale negative → fall through and re-check
        }

        // 2. Instant path: publisher metadata shows a title right away (not clickable yet).
        val publisherTitle = track.publisherMetadata?.albumTitle?.takeIf { it.isNotBlank() }
            ?: track.publisherMetadata?.releaseTitle?.takeIf { it.isNotBlank() }
        if (publisherTitle != null) {
            state.value = AlbumUiState.Resolved(AlbumInfo(null, publisherTitle))
        }

        // 3. Network: find the actual album set (skips silently during 429 cooldown).
        if (System.currentTimeMillis() < cooldownUntil) return
        val result = runCatching {
            semaphore.withPermit { api.getTrackAlbums(track.id, limit = 10) }
        }
        val response = result.getOrElse { e ->
            if ((e as? retrofit2.HttpException)?.code() == 429) {
                cooldownUntil = System.currentTimeMillis() + cooldownStepMs
                cooldownStepMs = (cooldownStepMs * 2).coerceAtMost(600_000L)
            }
            // Transient failure: no upsert (must not become a 30-day negative); state
            // keeps the publisher title if any, else stays Unknown for a later retry.
            return
        }
        cooldownStepMs = 60_000L

        val winner = pickAlbum(track, response.collection)
        val now = System.currentTimeMillis()
        if (winner != null) {
            val title = winner.title ?: publisherTitle ?: return
            runCatching { dao.upsert(TrackAlbumCacheRow(track.id, winner.id, title, now)) }
            state.value = AlbumUiState.Resolved(AlbumInfo(winner.id, title))
        } else {
            runCatching { dao.upsert(TrackAlbumCacheRow(track.id, null, publisherTitle, now)) }
            if (publisherTitle == null) state.value = AlbumUiState.None
        }
    }

    private val SET_TYPE_SCORES = mapOf("album" to 40, "ep" to 30, "compilation" to 20, "single" to 10)

    private fun pickAlbum(track: Track, candidates: List<Playlist>): Playlist? =
        candidates
            .asSequence()
            .filter { it.title != null && it.sharing != "private" && it.secretToken == null }
            .filter { it.isAlbum || it.setType in SET_TYPE_SCORES }
            .maxWithOrNull(
                compareBy<Playlist> { score(track, it) }
                    // older release wins ties (original album beats later compilations)
                    .thenByDescending { it.releaseDate ?: it.createdAt ?: "" }
                    .thenBy { it.likesCount ?: 0 }
            )

    private fun score(track: Track, p: Playlist): Int {
        var s = 0
        if (p.isAlbum) s += 100
        s += SET_TYPE_SCORES[p.setType] ?: 0
        if (p.user?.id != null && p.user.id == track.user?.id) s += 50
        if ((p.trackCount ?: 0) > 1) s += 5
        return s
    }
}

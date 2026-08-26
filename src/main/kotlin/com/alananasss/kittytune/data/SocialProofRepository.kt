package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.network.RelatedLikersGraphQl
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Social proof for track rows: which of the people you follow have liked a given track (issue #33).
 *
 * Ported from the Android app, and batched for the same reason. A scrolling list asks about every
 * row it draws, so the ids are collected for 60 ms and sent as one request for up to fifty tracks
 * instead of one request per row. Ids are remembered once asked — including when the answer was
 * "nobody" — so scrolling back over a row costs nothing.
 */
object SocialProofRepository {

    private const val BATCH_DEBOUNCE_MS = 60L
    private const val MAX_BATCH_SIZE = 50

    /**
     * How many tracks' likers to keep (issue #33).
     *
     * One entry is a handful of users, so this is small in bytes — but it was keyed by track and
     * never emptied, which over a long session with a lot of scrolling is one entry per track ever
     * seen. Well past what any screen shows at once, and the ones evicted are the ones nobody has
     * looked at in the longest time.
     */
    private const val MAX_CACHED_TRACKS = 600

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * The likers themselves, bounded. The flow still publishes a plain map so nothing downstream has
     * to know about the eviction; it is republished from the cache after every change.
     */
    private val likers = com.alananasss.kittytune.core.BoundedCache<Long, List<User>>(MAX_CACHED_TRACKS)

    private val _socialLikersMap = MutableStateFlow<Map<Long, List<User>>>(emptyMap())
    val socialLikersMap: StateFlow<Map<Long, List<User>>> = _socialLikersMap.asStateFlow()

    /**
     * Asked about already, answered or not: the guard against re-requesting on every scroll. Bounded
     * to the same size as the answers, so forgetting a key and forgetting its answer coincide.
     */
    private val requestedTrackIds = com.alananasss.kittytune.core.BoundedSet<Long>(MAX_CACHED_TRACKS)
    private val pendingBatchIds = ConcurrentHashMap.newKeySet<Long>()
    private var batchJob: Job? = null

    /** Own urn, so the listener is not shown as social proof of their own taste. */
    private var myUrn: String? = null

    private val api: SoundCloudApi by lazy { RetrofitClient.create() }

    fun clear() {
        myUrn = null
        likers.clear()
        _socialLikersMap.value = emptyMap()
        requestedTrackIds.clear()
        pendingBatchIds.clear()
        batchJob?.cancel()
    }

    fun getLikersForTrack(trackId: Long): List<User>? = likers[trackId]

    /** Records likers resolved elsewhere (the player sheet), so a row does not ask again. */
    fun putLikersForTrack(trackId: Long, users: List<User>) {
        if (trackId <= 0) return
        requestedTrackIds.add(trackId)
        likers[trackId] = users
        publish()
    }

    /** Mirrors the cache into the flow the rows collect. */
    private fun publish() {
        _socialLikersMap.value = likers.snapshot()
    }

    fun requestSocialProof(trackId: Long) {
        if (trackId <= 0 || trackId in requestedTrackIds) return
        requestSocialProof(listOf(trackId))
    }

    fun requestSocialProof(trackIds: List<Long>) {
        val newIds = trackIds.filter { it > 0 && it !in requestedTrackIds }
        if (newIds.isEmpty()) return
        newIds.forEach {
            requestedTrackIds.add(it)
            pendingBatchIds.add(it)
        }
        scheduleBatchFetch()
    }

    /**
     * Restarts the debounce on every new id, so a fast scroll coalesces into one request rather
     * than firing one per row as it appears.
     */
    private fun scheduleBatchFetch() {
        synchronized(this) {
            batchJob?.cancel()
            batchJob = scope.launch {
                delay(BATCH_DEBOUNCE_MS)
                processBatch()
            }
        }
    }

    private suspend fun processBatch() {
        val idsToFetch = pendingBatchIds.toList().take(MAX_BATCH_SIZE)
        if (idsToFetch.isEmpty()) return
        idsToFetch.forEach { pendingBatchIds.remove(it) }

        try {
            if (myUrn == null) {
                val myId = runCatching { api.getMe().id }.getOrDefault(0L)
                myUrn = if (myId > 0) "soundcloud:users:$myId" else ""
            }

            val response = api.getRelatedLikersGraphQL(
                RelatedLikersGraphQl.request(idsToFetch.map { "soundcloud:tracks:$it" })
            )
            val tracks = response.data?.allTracks
            if (tracks == null) {
                // A failed batch has to be forgotten, or those rows would stay silent for the rest
                // of the session even once the network came back.
                idsToFetch.forEach { requestedTrackIds.remove(it) }
                return
            }

            val resolved = mutableMapOf<Long, List<User>>()
            for (node in tracks) {
                val trackId = node.urn?.substringAfterLast(':')?.toLongOrNull() ?: continue
                resolved[trackId] = node.relatedLikers?.users.orEmpty()
                    .filter { !it.urn.isNullOrEmpty() && it.urn != myUrn }
                    .mapNotNull { apiUser ->
                        val userId = apiUser.urn?.substringAfterLast(':')?.toLongOrNull() ?: 0L
                        if (userId <= 0 || apiUser.username.isNullOrEmpty()) return@mapNotNull null
                        User(
                            id = userId,
                            username = apiUser.username,
                            avatarUrl = apiUser.avatarUrl,
                            verified = apiUser.verified ?: false,
                            urn = apiUser.urn,
                        )
                    }
            }
            // Tracks the response said nothing about have no likers among your follows; recording
            // that keeps them out of the next batch.
            idsToFetch.forEach { resolved.putIfAbsent(it, emptyList()) }
            resolved.forEach { (id, users) -> likers[id] = users }
            publish()
        } catch (e: Exception) {
            idsToFetch.forEach { requestedTrackIds.remove(it) }
        }

        if (pendingBatchIds.isNotEmpty()) scheduleBatchFetch()
    }
}

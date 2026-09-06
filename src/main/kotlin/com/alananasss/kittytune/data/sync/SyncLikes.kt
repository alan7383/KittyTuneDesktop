package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.Logger
import com.google.gson.Gson

/**
 * Favourites, kept the same on every paired device (issue #33).
 *
 * ## The report this exists for
 *
 * "I now have 142 tracks in my favorites on my phone, and 90 on my computer." Sync was moving listening
 * history and nothing else, so the two libraries had been drifting apart for as long as they had existed —
 * and the drift is not recoverable by counting, because the fifty-two missing tracks are specific ones: a
 * local file the phone imported, a Spotify track SoundCloud never held, a like made on a train.
 *
 * ## The rule
 *
 * Every like and every unlike is one event in the shared log, and a track's state is whichever event about
 * it is newest by [LikePayload.atMs]. That is a last-writer-wins register per track id, and it is chosen
 * because it is the only rule that needs no coordination: two devices that cannot see each other can both
 * be edited, in any order, and still agree afterwards. Ties — the same millisecond on two devices — fall
 * back to the device id, so both sides break them the same way rather than converging to different states.
 *
 * ## Why the state is derived rather than applied
 *
 * [reconcile] recomputes the answer from the whole log instead of applying each arriving event to the
 * library. That costs a pass over a few hundred events and buys the one property that matters: the outcome
 * cannot drift. An event applied twice, a batch that arrives out of order, a merge cancelled halfway, a log
 * restored from a backup — all of them end at the same library, because the library is a projection of the
 * log rather than a running total of it. This is the same lesson [SyncApply] learnt the hard way with
 * statistics, where advancing the marks before the rows landed lost sixty-six listens for good.
 *
 * ## What is deliberately left alone
 *
 * A track with no like event at all is not touched. The SoundCloud account is a second, independent channel
 * into the same library, and a library full of server-fetched likes that this had never heard of must not be
 * read as "everything the log does not mention is unliked" — that would empty it. [seedMissing] closes the
 * gap from the other end by giving every like already in the library an event of its own, so the log grows
 * to cover the whole library instead of the library shrinking to the log.
 */
object SyncLikes {

    private val gson = Gson()
    private val prefs by lazy { NamedPrefs("sync_state") }

    /** What the log says about one track, once every event about it has been taken into account. */
    private data class Resolved(val liked: Boolean, val atMs: Long, val track: Track?)

    /**
     * Records that the user liked or unliked [trackId] here, now.
     *
     * Cheap enough to call from the like button: one appended line. Called for every like whether or not
     * anything is paired — a log that only started recording at the moment of pairing would have nothing
     * to hand over, and pairing is meant to be the moment the two libraries meet, not the moment they
     * start being watched.
     */
    fun record(trackId: Long, liked: Boolean, track: Track?) {
        runCatching {
            SyncLog.append(
                kind = SyncKinds.LIKE,
                payload = LikePayload(
                    trackId = trackId,
                    liked = liked,
                    atMs = System.currentTimeMillis(),
                    // Carried on a like so the other device can add it outright, and dropped on an unlike,
                    // which needs nothing but the id.
                    track = track?.takeIf { liked },
                ),
            )
        }.onFailure { Logger.e("SyncLikes", "could not record like for $trackId: ${it.message}") }
        SyncScheduler.requestSync("like changed")
    }

    /** The same, for a playlist. [permalinkUrl] and [urn] are what the other device needs to unlike it. */
    fun recordPlaylist(playlistId: Long, liked: Boolean, permalinkUrl: String?, urn: String?) {
        runCatching {
            SyncLog.append(
                kind = SyncKinds.PLAYLIST_LIKE,
                payload = PlaylistLikePayload(
                    playlistId = playlistId,
                    liked = liked,
                    atMs = System.currentTimeMillis(),
                    permalinkUrl = permalinkUrl,
                    urn = urn,
                ),
            )
        }.onFailure { Logger.e("SyncLikes", "could not record playlist like: ${it.message}") }
        SyncScheduler.requestSync("playlist like changed")
    }

    /**
     * Gives every like already in the library an event of its own, once per track.
     *
     * This is what actually closes a gap that predates sync. The log can only carry what it was told about,
     * so on the day this shipped it held nothing at all about the 142 likes on the phone or the 90 on the
     * desktop — and a merge of two empty sets changes neither library. Seeding turns the existing library
     * into events, and then the ordinary merge does the rest: neither side holds an unlike for any of them,
     * so both converge on the union, which is the answer the user was asking for.
     *
     * The event is stamped with the track's own [Track.likedAt] rather than with now. That matters as soon
     * as the first unlike arrives: a like seeded today with today's date would out-rank a genuine unlike
     * made last week and resurrect it, on every device, for ever. Stamped with when the like really
     * happened, the unlike is newer and wins.
     *
     * Runs after the library is loaded and after each server sync, and does nothing at all on the second
     * and later passes for a track it has already covered — so it is safe on every launch.
     *
     * @return how many events it added.
     */
    fun seedMissing(): Int {
        val known = likeEventsByTrack().keys
        val library = runCatching { LikeRepository.likedTracks.value }.getOrDefault(emptyList())
        val missing = library.filter { it.id !in known }
        if (missing.isEmpty()) return 0

        // A first launch on a large library is the one case where this is not a couple of lines, and a
        // like recorded here is indistinguishable from one made by hand — so the cap is on the batch, not
        // on the feature: the rest are seeded on the next pass rather than in one write of many megabytes.
        val batch = missing.take(MAX_SEED_PER_PASS)
        val now = System.currentTimeMillis()
        val items = batch.map { track ->
            val atMs = track.likedAt?.takeIf { it in 1..now } ?: now
            SyncLog.BatchItem(
                kind = SyncKinds.LIKE,
                payload = LikePayload(
                    trackId = track.id,
                    liked = true,
                    atMs = atMs,
                    track = track,
                ),
                timestampMs = atMs,
            )
        }
        val added = runCatching { SyncLog.appendBatch(items).size }.getOrDefault(0)
        if (added > 0) {
            Logger.e("SyncLikes", "seeded $added existing likes into the sync log (${missing.size - added} left)")
            SyncScheduler.requestSync("likes seeded")
        }
        return added
    }

    /** The same for playlist likes, which are held as bare ids and so need nothing but the id. */
    fun seedMissingPlaylists(): Int {
        val known = playlistEventsById().keys
        val library = runCatching { LikeRepository.likedPlaylists.value }.getOrDefault(emptySet())
        val missing = library.filter { it !in known }
        if (missing.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val items = missing.take(MAX_SEED_PER_PASS).map { id ->
            SyncLog.BatchItem(
                kind = SyncKinds.PLAYLIST_LIKE,
                payload = PlaylistLikePayload(playlistId = id, liked = true, atMs = now),
                timestampMs = now,
            )
        }
        val added = runCatching { SyncLog.appendBatch(items).size }.getOrDefault(0)
        if (added > 0) SyncScheduler.requestSync("playlist likes seeded")
        return added
    }

    /**
     * Makes the library agree with the log, and returns how many tracks it had to change.
     *
     * Only tracks the log has something to say about are considered; see the class note on why everything
     * else is left exactly as it is. Adding and removing go through [LikeRepository]'s remote entry points,
     * which deliberately do not append a new event — otherwise applying what the phone sent would produce
     * an event of our own describing the same like, the phone would apply that, and the two would generate
     * traffic about a track neither of them had touched for the rest of the pairing's life.
     */
    suspend fun reconcile(): Int {
        var changed = 0
        val present = runCatching { LikeRepository.likedTrackIds() }.getOrDefault(emptySet())

        val toAdd = ArrayList<Pair<Track, Long>>()
        val toRemove = HashSet<Long>()

        for ((trackId, resolved) in resolveTracks()) {
            val isPresent = trackId in present
            when {
                resolved.liked && !isPresent -> {
                    val track = resolved.track ?: continue
                    toAdd.add(track to resolved.atMs)
                }

                !resolved.liked && isPresent -> {
                    toRemove.add(trackId)
                }
            }
        }

        if (toAdd.isNotEmpty()) {
            LikeRepository.applyRemoteLikesBatch(toAdd)
            changed += toAdd.size
        }
        if (toRemove.isNotEmpty()) {
            LikeRepository.applyRemoteUnlikesBatch(toRemove)
            changed += toRemove.size
        }

        val likedPlaylists = runCatching { LikeRepository.likedPlaylists.value }.getOrDefault(emptySet())
        for ((playlistId, resolved) in resolvePlaylists()) {
            val isPresent = playlistId in likedPlaylists
            if (resolved.liked != isPresent) {
                LikeRepository.applyRemotePlaylistLike(
                    playlistId = playlistId,
                    liked = resolved.liked,
                    permalinkUrl = resolved.permalinkUrl,
                    urn = resolved.urn,
                )
                changed++
            }
        }

        if (changed > 0) Logger.e("SyncLikes", "reconciled $changed favourites against the sync log")
        return changed
    }

    /** Whether the log holds anything about likes at all, for a screen that wants to say so. */
    fun likeEventCount(): Int = runCatching {
        SyncLog.all().count { it.kind == SyncKinds.LIKE || it.kind == SyncKinds.PLAYLIST_LIKE }
    }.getOrDefault(0)

    /**
     * The winning fact about every track the log mentions.
     *
     * Newest [LikePayload.atMs] wins; a tie is broken on the device id so that every device breaks it the
     * same way. A like's track is remembered even when the winner is an unlike carrying none, so a later
     * device that only ever heard the unlike still has something to show if the like wins again.
     */
    private fun resolveTracks(): Map<Long, Resolved> {
        val winners = HashMap<Long, Pair<SyncEvent, LikePayload>>()
        val events = likeEvents()
        for ((event, payload) in events) {
            val current = winners[payload.trackId]
            if (current == null || beats(payload.atMs, event.deviceId, current.second.atMs, current.first.deviceId)) {
                winners[payload.trackId] = event to payload
            }
        }
        // The best description of the track we have seen anywhere, not only on the winning event.
        val bestKnownTrack = HashMap<Long, Track>()
        for ((_, payload) in events) {
            payload.track?.let { bestKnownTrack.putIfAbsent(payload.trackId, it) }
        }
        return winners.mapValues { (trackId, won) ->
            Resolved(
                liked = won.second.liked,
                atMs = won.second.atMs,
                track = won.second.track ?: bestKnownTrack[trackId],
            )
        }
    }

    private data class ResolvedPlaylist(
        val liked: Boolean,
        val atMs: Long,
        val permalinkUrl: String?,
        val urn: String?,
    )

    private fun resolvePlaylists(): Map<Long, ResolvedPlaylist> {
        val winners = HashMap<Long, Pair<SyncEvent, PlaylistLikePayload>>()
        for ((event, payload) in playlistEvents()) {
            val current = winners[payload.playlistId]
            if (current == null || beats(payload.atMs, event.deviceId, current.second.atMs, current.first.deviceId)) {
                winners[payload.playlistId] = event to payload
            }
        }
        return winners.mapValues { (_, won) ->
            ResolvedPlaylist(
                liked = won.second.liked,
                atMs = won.second.atMs,
                permalinkUrl = won.second.permalinkUrl,
                urn = won.second.urn,
            )
        }
    }

    /** Later wins; the same instant on two devices is settled by the id, identically on both. */
    private fun beats(atMs: Long, deviceId: String, otherAtMs: Long, otherDeviceId: String): Boolean =
        atMs > otherAtMs || (atMs == otherAtMs && deviceId > otherDeviceId)

    private fun likeEvents(): List<Pair<SyncEvent, LikePayload>> =
        runCatching { SyncLog.all() }.getOrDefault(emptyList())
            .asSequence()
            .filter { it.kind == SyncKinds.LIKE }
            .mapNotNull { event ->
                val payload = runCatching { gson.fromJson(event.payload, LikePayload::class.java) }
                    .getOrNull() ?: return@mapNotNull null
                if (payload.trackId == 0L) null else event to payload
            }
            .toList()

    private fun playlistEvents(): List<Pair<SyncEvent, PlaylistLikePayload>> =
        runCatching { SyncLog.all() }.getOrDefault(emptyList())
            .asSequence()
            .filter { it.kind == SyncKinds.PLAYLIST_LIKE }
            .mapNotNull { event ->
                val payload = runCatching { gson.fromJson(event.payload, PlaylistLikePayload::class.java) }
                    .getOrNull() ?: return@mapNotNull null
                if (payload.playlistId == 0L) null else event to payload
            }
            .toList()

    private fun likeEventsByTrack(): Map<Long, List<LikePayload>> =
        likeEvents().groupBy({ it.second.trackId }, { it.second })

    private fun playlistEventsById(): Map<Long, List<PlaylistLikePayload>> =
        playlistEvents().groupBy({ it.second.playlistId }, { it.second })

    /**
     * How many likes one seeding pass turns into events.
     *
     * A library of several thousand would otherwise be one write of that many JSON lines on the launch
     * path. Split across passes it is invisible, and the marks make the remainder no different from any
     * other backlog.
     */
    private const val MAX_SEED_PER_PASS = 400

    private const val KEY_SEEDED = "likes_seeded_at"

    /** When seeding last ran to completion, so a screen can say whether the first pass has happened. */
    var lastSeededAtMs: Long
        get() = prefs.getLong(KEY_SEEDED, 0L)
        set(value) = prefs.putLong(KEY_SEEDED, value)
}

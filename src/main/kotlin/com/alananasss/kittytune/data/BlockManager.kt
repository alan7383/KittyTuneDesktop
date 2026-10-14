package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.BlockedContent
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop port of the Android BlockManager singleton (issue #41).
 *
 * Manages blocked tracks and artists with an in-memory cache for O(1) hot-path checks
 * during playback and list rendering. The SQLite database is the source of truth;
 * the cache is rebuilt on init and kept in sync via coroutines.
 *
 * Block reasons:
 * - [REASON_MANUAL]       — user explicitly blocked via the track menu
 * - [REASON_AI_GENERATED] — ArtifactNet detected AI-generated audio (auto-skip enabled)
 */
object BlockManager {

    const val REASON_MANUAL = "MANUAL"
    const val REASON_AI_GENERATED = "AI_GENERATED"

    private val scope = CoroutineScope(Dispatchers.IO)

    // ─── In-memory caches (thread-safe) ──────────────────────────────────────

    /** Blocked track IDs — checked on every list render and before playback. */
    private val blockedTrackIds = ConcurrentHashMap.newKeySet<Long>()

    /** Blocked artist IDs — checked when filtering lists. */
    private val blockedArtistIds = ConcurrentHashMap.newKeySet<Long>()

    /** Observable StateFlows for reactive Compose UI and ViewModels. */
    private val _blockedTrackIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val blockedTrackIdsFlow: StateFlow<Set<Long>> = _blockedTrackIdsFlow.asStateFlow()

    private val _blockedArtistIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val blockedArtistIdsFlow: StateFlow<Set<Long>> = _blockedArtistIdsFlow.asStateFlow()

    /** Callback invoked when the current playing track is blocked. */
    var onCurrentTrackBlocked: (() -> Unit)? = null

    /** Callbacks invoked when content is blocked or unblocked. */
    var onTrackBlocked: ((Long) -> Unit)? = null
    var onTrackUnblocked: ((Long) -> Unit)? = null
    var onArtistBlocked: ((Long) -> Unit)? = null
    var onArtistUnblocked: ((Long) -> Unit)? = null

    // ─── Init ─────────────────────────────────────────────────────────────────

    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        scope.launch {
            // Warm the in-memory caches from DB
            val trackIds = AppDatabase.blockedContentDao.getAllBlockedTrackIds()
            val artistIds = AppDatabase.blockedContentDao.getAllBlockedArtistIds()
            blockedTrackIds.addAll(trackIds)
            blockedArtistIds.addAll(artistIds)
            _blockedTrackIdsFlow.value = blockedTrackIds.toSet()
            _blockedArtistIdsFlow.value = blockedArtistIds.toSet()
        }
    }

    // ─── Hot-path checks (O(1), no DB) ────────────────────────────────────────

    /** Returns true if this track or its artist is blocked. */
    fun isBlocked(track: Track): Boolean {
        if (blockedTrackIds.contains(track.id)) return true
        val artistId = track.user?.id ?: return false
        return blockedArtistIds.contains(artistId)
    }

    fun isTrackBlocked(trackId: Long): Boolean = blockedTrackIds.contains(trackId)

    fun isArtistBlocked(artistId: Long): Boolean = blockedArtistIds.contains(artistId)

    // ─── Block actions ────────────────────────────────────────────────────────

    /**
     * Block a single track.
     * @param skipIfCurrent  If true and this is the currently playing track,
     *                       [onCurrentTrackBlocked] is invoked so the caller can skip.
     */
    fun blockTrack(
        track: Track,
        reason: String = REASON_MANUAL,
        skipIfCurrent: Boolean = true,
        currentlyPlayingId: Long? = null,
    ) {
        val trackId = track.id
        if (blockedTrackIds.add(trackId)) {
            _blockedTrackIdsFlow.value = blockedTrackIds.toSet()
            scope.launch {
                runCatching {
                    AppDatabase.blockedContentDao.insertBlock(
                        BlockedContent(
                            trackId = trackId,
                            trackTitle = track.title,
                            trackArtworkUrl = track.artworkUrl,
                            source = track.source ?: "soundcloud",
                            reason = reason,
                        ),
                    )
                }
            }
            onTrackBlocked?.invoke(trackId)
        }
        if (skipIfCurrent && currentlyPlayingId == trackId) {
            onCurrentTrackBlocked?.invoke()
        }
    }

    /**
     * Block an artist by ID.
     * @param skipIfCurrent  If true and the currently playing track belongs to this artist,
     *                       [onCurrentTrackBlocked] is invoked.
     */
    fun blockArtist(
        artistId: Long,
        artistName: String,
        avatarUrl: String? = null,
        source: String = "soundcloud",
        reason: String = REASON_MANUAL,
        currentlyPlayingTrack: Track? = null,
    ) {
        if (blockedArtistIds.add(artistId)) {
            _blockedArtistIdsFlow.value = blockedArtistIds.toSet()
            scope.launch {
                runCatching {
                    AppDatabase.blockedContentDao.insertBlock(
                        BlockedContent(
                            artistId = artistId,
                            artistName = artistName,
                            artistAvatarUrl = avatarUrl,
                            source = source,
                            reason = reason,
                        ),
                    )
                }
            }
            onArtistBlocked?.invoke(artistId)
        }
        if (currentlyPlayingTrack?.user?.id == artistId) {
            onCurrentTrackBlocked?.invoke()
        }
    }

    // ─── Unblock ─────────────────────────────────────────────────────────────

    fun unblockTrack(trackId: Long) {
        val wasPresent = blockedTrackIds.remove(trackId)
        _blockedTrackIdsFlow.value = blockedTrackIds.toSet()
        scope.launch { runCatching { AppDatabase.blockedContentDao.unblockTrack(trackId) } }
        if (wasPresent) {
            onTrackUnblocked?.invoke(trackId)
        }
    }

    fun unblockArtist(artistId: Long) {
        val wasPresent = blockedArtistIds.remove(artistId)
        _blockedArtistIdsFlow.value = blockedArtistIds.toSet()
        scope.launch { runCatching { AppDatabase.blockedContentDao.unblockArtist(artistId) } }
        if (wasPresent) {
            onArtistUnblocked?.invoke(artistId)
        }
    }

    // ─── Observable streams (for Settings screen) ─────────────────────────────

    fun observeBlockedTracks(): Flow<List<BlockedContent>> =
        AppDatabase.blockedContentDao.observeBlockedTracks()

    fun observeBlockedArtists(): Flow<List<BlockedContent>> =
        AppDatabase.blockedContentDao.observeBlockedArtists()

    fun observeAll(): Flow<List<BlockedContent>> =
        AppDatabase.blockedContentDao.observeAll()

    // ─── Filter utility ───────────────────────────────────────────────────────

    /** Filter a list of tracks, removing any that are blocked. */
    fun filterBlocked(tracks: List<Track>): List<Track> =
        tracks.filter { !isBlocked(it) }
}

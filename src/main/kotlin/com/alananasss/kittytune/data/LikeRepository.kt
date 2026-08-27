package com.alananasss.kittytune.data

import com.alananasss.kittytune.core.NamedPrefs
import com.alananasss.kittytune.data.network.PlaylistLikeItem
import com.alananasss.kittytune.data.network.PlaylistLikeRequest
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.data.network.TrackLikeItem
import com.alananasss.kittytune.data.network.TrackLikeRequest
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.reflect.Type

/**
 * Desktop port of the Android LikeRepository.
 * SharedPreferences("soundtune_likes_v3") -> NamedPrefs; StringSet stored as a
 * separator-joined string. Same keys, same optimistic + blacklist sync semantics.
 */
object LikeRepository {
    private const val KEY_LIKED_TRACKS = "liked_tracks_full"
    private const val KEY_LIKED_PLAYLISTS = "liked_playlists_ids"
    private const val KEY_LOCALLY_UNLIKED_IDS = "locally_unliked_ids"
    private val SET_SEP = 31.toChar().toString()

    private val prefs = NamedPrefs("soundtune_likes_v3")
    private val api: SoundCloudApi by lazy { RetrofitClient.create() }
    private val gson = Gson()
    private var cachedUserId: Long? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _likedTracks = MutableStateFlow<List<Track>>(emptyList())
    val likedTracks = _likedTracks.asStateFlow()

    private val _likedPlaylists = MutableStateFlow<Set<Long>>(emptySet())
    val likedPlaylists = _likedPlaylists.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val tokenManager = TokenManager
    private val playerPrefs = com.alananasss.kittytune.data.local.PlayerPreferences()

    private fun getStringSet(key: String): Set<String> =
        prefs.getString(key, null)?.split(SET_SEP)?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun putStringSet(key: String, value: Set<String>) =
        prefs.putString(key, value.joinToString(SET_SEP))

    /**
     * How long an id unliked here keeps overriding what the server says (issue #33).
     *
     * The blacklist covers one narrow window: between unliking on this machine and the server
     * agreeing. It used to have no end, so an entry written once suppressed that track for good —
     * and a track unliked here and then liked again from the phone was filtered out of every sync
     * from then on. Dozens of likes can disappear that way over months with nothing looking broken,
     * which is the report. Entries carry the time they were written now and stop counting after
     * this; a confirmed unlike drops its entry immediately, since from then on the server does not
     * report the track as liked at all and there is nothing left to suppress.
     */
    private const val UNLIKE_BLACKLIST_TTL_MS = 7L * 24 * 60 * 60 * 1000

    private data class UnlikeMark(val trackId: Long, val atMs: Long)

    private fun readUnlikeMarks(): List<UnlikeMark> =
        getStringSet(KEY_LOCALLY_UNLIKED_IDS).mapNotNull { raw ->
            val id = raw.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
            // Entries written before there was an expiry carry no timestamp. Dropping them is the
            // repair rather than a side effect: they are exactly the ones that may be hiding a
            // track the server still calls liked, and an unlike done while online is already
            // reflected server-side, so nothing comes back that should not.
            val at = raw.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
            UnlikeMark(id, at)
        }

    private fun writeUnlikeMarks(marks: Collection<UnlikeMark>) =
        putStringSet(KEY_LOCALLY_UNLIKED_IDS, marks.map { "${it.trackId}:${it.atMs}" }.toSet())

    private fun addToBlacklist(trackId: Long) {
        val kept = readUnlikeMarks().filterNot { it.trackId == trackId }
        writeUnlikeMarks(kept + UnlikeMark(trackId, System.currentTimeMillis()))
    }

    private fun removeFromBlacklist(trackId: Long) {
        writeUnlikeMarks(readUnlikeMarks().filterNot { it.trackId == trackId })
    }

    private fun getBlacklist(): Set<Long> {
        val cutoff = System.currentTimeMillis() - UNLIKE_BLACKLIST_TTL_MS
        return readUnlikeMarks().filter { it.atMs >= cutoff }.map { it.trackId }.toSet()
    }

    /**
     * Drops the entries that no longer suppress anything — expired ones and the untimestamped
     * legacy ones — so the stored set cannot grow without bound and cannot keep hiding a like.
     */
    private fun pruneBlacklist() {
        val stored = getStringSet(KEY_LOCALLY_UNLIKED_IDS)
        if (stored.isEmpty()) return
        val cutoff = System.currentTimeMillis() - UNLIKE_BLACKLIST_TTL_MS
        val kept = readUnlikeMarks().filter { it.atMs >= cutoff }
        if (kept.size != stored.size) {
            com.alananasss.kittytune.utils.Logger.e(
                "LikeRepository",
                "Pruned ${stored.size - kept.size} stale locally-unliked ids (${kept.size} left)."
            )
            writeUnlikeMarks(kept)
        }
    }

    fun init() {
        pruneBlacklist()
        loadFromPrefs()

        // Closes the coalescing window. Prefs has a hook of its own, but the order between shutdown
        // hooks is undefined, so this forces the flush rather than hoping ours ran first.
        Runtime.getRuntime().addShutdownHook(
            Thread {
                if (pendingSave?.isActive == true) {
                    pendingSave?.cancel()
                    saveToPrefs()
                    com.alananasss.kittytune.core.Prefs.flush(force = true)
                }
            }
        )
    }

    private suspend fun getUserId(): Long? {
        if (cachedUserId != null) return cachedUserId
        return try {
            val me = api.getMe()
            cachedUserId = me.id
            me.id
        } catch (e: Exception) {
            null
        }
    }

    fun addLike(track: Track) {
        removeFromBlacklist(track.id)

        _likedTracks.update { current ->
            val safeSource = (track.source as? String) ?: "soundcloud"
            if (current.any { it.id == track.id }) {
                current
            } else {
                val newTrack = track.copy(
                    isLiked = true,
                    source = safeSource,
                    likedAt = System.currentTimeMillis()
                )
                (listOf(newTrack) + current).sortedByDescending { it.likedAt ?: 0L }
            }
        }

        scheduleSave()

        scope.launch {
            if (track.source == "spotify" || track.user?.urn?.startsWith("spotify") == true || (track.permalinkUrl != null && track.permalinkUrl!!.contains("spotify"))) return@launch
            if (!playerPrefs.getSyncLikesEnabled()) return@launch
            if (tokenManager.isGuestMode()) return@launch
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrEmpty()) {
                try {
                    val payload = TrackLikeRequest(
                        likes = listOf(TrackLikeItem("soundcloud:tracks:${track.id}"))
                    )
                    val response = api.likeTrack(payload)
                    if (response.code() == 401) {
                        SessionManager.requestSessionRefresh(force = true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun removeLike(trackId: Long) {
        val targetTrack = _likedTracks.value.find { it.id == trackId }
        val isSpotify = targetTrack?.source == "spotify" || targetTrack?.user?.urn?.startsWith("spotify") == true
                || (targetTrack?.permalinkUrl != null && targetTrack.permalinkUrl!!.contains("spotify")) || trackId > 1000000000000000L

        addToBlacklist(trackId)

        _likedTracks.update { it.filterNot { t -> t.id == trackId } }

        // Was a synchronous whole-library serialisation on whichever thread unliked — the UI one,
        // when it came from a row in a list.
        scheduleSave()

        if (isSpotify) return

        scope.launch {
            if (!playerPrefs.getSyncLikesEnabled()) return@launch
            if (tokenManager.isGuestMode()) return@launch
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrEmpty()) {
                try {
                    val payload = TrackLikeRequest(
                        likes = listOf(TrackLikeItem("soundcloud:tracks:$trackId"))
                    )
                    val response = api.unlikeTrack(payload)
                    if (response.code() == 401) {
                        SessionManager.requestSessionRefresh(force = true)
                    }
                    // Once the server has taken the unlike, it stops reporting the track as liked
                    // and the local override has nothing left to do. Kept on failure so an unlike
                    // made offline still holds until the next attempt gets through.
                    if (response.isSuccessful) removeFromBlacklist(trackId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Ids of [likedTracks], rebuilt only when that list is replaced.
     *
     * The likes are held as whole tracks, so answering "is this one liked?" from them is a linear
     * scan. That was fine while only the player bar asked; now every visible row does, and every
     * like replaces the list and invalidates all of them at once — O(rows × likes) per like
     * (issue #33). Memoising on the list's identity makes each row's question a set lookup, and
     * keeps it synchronous, so a row is never briefly wrong the way a derived flow would be.
     */
    private var cachedIdsSource: List<Track>? = null
    private var cachedIds: Set<Long> = emptySet()

    @Synchronized
    fun likedTrackIds(): Set<Long> {
        val current = _likedTracks.value
        if (cachedIdsSource !== current) {
            cachedIdsSource = current
            cachedIds = current.mapTo(HashSet(current.size)) { it.id }
        }
        return cachedIds
    }

    fun isTrackLiked(trackId: Long): Boolean = trackId in likedTrackIds()

    fun isPlaylistLiked(playlistId: Long): Boolean = _likedPlaylists.value.contains(playlistId)

    fun setLikedPlaylists(ids: Set<Long>) {
        val currentLiked = _likedPlaylists.value
        val preservedLocalIds = try {
            val dao = com.alananasss.kittytune.data.local.AppDatabase.downloadDao
            val allLocal = kotlinx.coroutines.runBlocking { dao.getAllPlaylists().first() }
            allLocal.filter { local ->
                currentLiked.contains(local.id) && (local.permalinkUrl?.contains("spotify") == true || local.id < 0 || local.isDownloaded)
            }.map { it.id }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

        _likedPlaylists.value = ids + preservedLocalIds
        scheduleSave()
    }

    fun togglePlaylistLike(playlistId: Long, isLiked: Boolean, permalink: String? = null, urn: String? = null) {
        val current = _likedPlaylists.value.toMutableSet()
        if (isLiked) {
            current.add(playlistId)
            DownloadManager.clearDeletedPlaylistId(playlistId)
        } else {
            current.remove(playlistId)
            DownloadManager.addDeletedPlaylistId(playlistId)
        }
        _likedPlaylists.value = current
        DownloadManager.notifyLibraryUpdated()
        scheduleSave()

        scope.launch {
            if (!isLiked && playlistId > 0) {
                try {
                    val dao = com.alananasss.kittytune.data.local.AppDatabase.downloadDao
                    val p = dao.getPlaylist(playlistId)
                    if (p != null && !p.isDownloaded && !p.isUserCreated) {
                        dao.deletePlaylist(playlistId)
                        dao.deletePlaylistRefs(playlistId)
                        dao.cleanUnreferencedEmptyTracks()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val safePermalink = permalink ?: ""
            val isSpotify = safePermalink.contains("spotify.com") || safePermalink.contains("spotify:")
                    || urn?.startsWith("spotify:") == true || (urn != null && urn.contains("spotify"))
                    || playlistId == 0L
            if (isSpotify) {
                return@launch
            }

            if (tokenManager.isGuestMode()) return@launch
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrEmpty()) {
                try {
                    val targetUrn = urn ?: when {
                        safePermalink.contains("artist-stations") -> "soundcloud:system-playlists:artist-stations:$playlistId"
                        safePermalink.contains("track-stations") -> "soundcloud:system-playlists:track-stations:$playlistId"
                        else -> "soundcloud:playlists:$playlistId"
                    }
                    val payload = PlaylistLikeRequest(likes = listOf(PlaylistLikeItem(targetUrn)))

                    val response = if (isLiked) api.likePlaylist(payload) else api.unlikePlaylist(payload)

                    if (response.code() == 401) {
                        SessionManager.requestSessionRefresh(force = true)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            DownloadManager.notifyLibraryUpdated()
        }
    }

    /**
     * @param serverListComplete whether [serverTracks] is the whole of what the server holds.
     *   A complete list is authoritative and anything absent from it is genuinely no longer liked.
     *   A truncated one is not: a page that failed to load must not be read as "those likes are
     *   gone", which would drop them here and, on the next save, from disk too (issue #33).
     */
    fun replaceAllLikes(
        serverTracks: List<Track>,
        currentUserId: Long? = null,
        serverListComplete: Boolean = true,
    ) {
        if (currentUserId != null) {
            val lastUserId = prefs.getLong("last_synced_user_id", -1L)
            if (lastUserId != -1L && lastUserId != currentUserId) {
                prefs.remove(KEY_LOCALLY_UNLIKED_IDS)
            }
            prefs.putLong("last_synced_user_id", currentUserId)
        }

        val maxAllowedTime = System.currentTimeMillis() + 86_400_000L

        _likedTracks.update { currentLocalList ->
            val blacklist = getBlacklist()

            val serverList = serverTracks
                .filter { !blacklist.contains(it.id) }
                .map { t ->
                    val validLikedAt = t.likedAt?.takeIf { it in 1..maxAllowedTime }
                    t.copy(isLiked = true, likedAt = validLikedAt)
                }

            val serverIds = serverList.map { it.id }.toSet()

            val keptLocal = currentLocalList.filter {
                // What SoundCloud never knew about in the first place — local files, Spotify —
                // always survives a replace. Its own tracks only survive one the server could not
                // finish, where their absence means "not seen yet" rather than "not liked".
                val foreignToServer = it.source != "soundcloud" || it.id <= 0L
                (foreignToServer || !serverListComplete) &&
                    !blacklist.contains(it.id) &&
                    !serverIds.contains(it.id)
            }.map { t ->
                val validLikedAt = t.likedAt?.takeIf { it in 1..maxAllowedTime }
                t.copy(likedAt = validLikedAt)
            }

            val combined = keptLocal + serverList

            combined.sortedByDescending { it.likedAt ?: 0L }
        }

        scheduleSave()
        _isSyncing.value = false
    }

    fun clear() {
        cachedUserId = null
        _likedTracks.value = emptyList()
        _likedPlaylists.value = emptySet()
        _isSyncing.value = false
        prefs.remove(KEY_LIKED_TRACKS)
        prefs.remove(KEY_LIKED_PLAYLISTS)
        prefs.remove(KEY_LOCALLY_UNLIKED_IDS)
        prefs.remove("last_synced_user_id")
    }

    fun setSyncing(isSync: Boolean) {
        _isSyncing.value = isSync
    }

    /**
     * Writes the liked list to disk, at most once per burst (issue #33).
     *
     * ## Why this is coalesced
     *
     * [saveToPrefs] serialises the entire list. For a library of several thousand tracks that is
     * megabytes of JSON built from nothing, handed to [com.alananasss.kittytune.core.Prefs] as one
     * string, which then re-encodes the whole preference object around it and writes the file. Every
     * like paid for all of that, and [removeLike] paid for it on the caller's thread — so unliking
     * from a list did a multi-megabyte serialisation on the UI thread.
     *
     * Liking half a dozen tracks in a row therefore allocated the same library over and over in a few
     * seconds. That is the shape of churn that turns into a collection pause, and a pause in the wrong
     * place is the audio drop-out that was reported alongside the memory figures. Coalescing collapses
     * a burst into one write of the final state, which is the only state anyone wanted written.
     *
     * The window is short and the shutdown hook closes it, so nothing is lost by waiting; a like is
     * also already on its way to the server, which is where the next launch reads it from anyway.
     */
    private var pendingSave: Job? = null

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = scope.launch {
            delay(SAVE_COALESCE_MS)
            saveToPrefs()
        }
    }

    private fun saveToPrefs() {
        val json = gson.toJson(_likedTracks.value)
        prefs.putString(KEY_LIKED_TRACKS, json)
        putStringSet(KEY_LIKED_PLAYLISTS, _likedPlaylists.value.map { it.toString() }.toSet())
    }

    /** Long enough to swallow a run of likes, short enough that nobody could close the app inside it. */
    private const val SAVE_COALESCE_MS = 1_000L

    private fun loadFromPrefs() {
        val json = prefs.getString(KEY_LIKED_TRACKS, null)
        if (json != null) {
            try {
                val type: Type = object : TypeToken<List<Track>>() {}.type
                val loadedList: List<Track> = gson.fromJson(json, type) ?: emptyList()

                val now = System.currentTimeMillis()
                val maxAllowedTime = now + 86_400_000L
                val migratedList = loadedList.mapIndexed { index, track ->
                    val validLikedAt = track.likedAt?.takeIf { it in 1..maxAllowedTime }
                    if (validLikedAt == null) {
                        track.copy(likedAt = now - (index * 1000))
                    } else {
                        track.copy(likedAt = validLikedAt)
                    }
                }

                val blacklist = getBlacklist()
                _likedTracks.value = migratedList.filter { !blacklist.contains(it.id) }
            } catch (e: Exception) {
                _likedTracks.value = emptyList()
            }
        }

        val savedPlaylistIds = getStringSet(KEY_LIKED_PLAYLISTS).mapNotNull { it.toLongOrNull() }.toSet()
        _likedPlaylists.value = savedPlaylistIds
    }
}


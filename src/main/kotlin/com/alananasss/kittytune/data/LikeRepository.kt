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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private fun addToBlacklist(trackId: Long) {
        val current = getBlacklist().toMutableSet()
        current.add(trackId)
        putStringSet(KEY_LOCALLY_UNLIKED_IDS, current.map { it.toString() }.toSet())
    }

    private fun removeFromBlacklist(trackId: Long) {
        val current = getBlacklist().toMutableSet()
        current.remove(trackId)
        putStringSet(KEY_LOCALLY_UNLIKED_IDS, current.map { it.toString() }.toSet())
    }

    private fun getBlacklist(): Set<Long> =
        getStringSet(KEY_LOCALLY_UNLIKED_IDS).mapNotNull { it.toLongOrNull() }.toSet()

    fun init() {
        loadFromPrefs()
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

        scope.launch {
            saveToPrefs()

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
        addToBlacklist(trackId)

        _likedTracks.update { it.filterNot { t -> t.id == trackId } }

        scope.launch {
            saveToPrefs()
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
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun isTrackLiked(trackId: Long): Boolean = _likedTracks.value.any { it.id == trackId }

    fun isPlaylistLiked(playlistId: Long): Boolean = _likedPlaylists.value.contains(playlistId)

    fun setLikedPlaylists(ids: Set<Long>) {
        _likedPlaylists.value = ids
        scope.launch { saveToPrefs() }
    }

    fun togglePlaylistLike(playlistId: Long, isLiked: Boolean, permalink: String? = null, urn: String? = null) {
        val current = _likedPlaylists.value.toMutableSet()
        if (isLiked) current.add(playlistId) else current.remove(playlistId)
        _likedPlaylists.value = current
        DownloadManager.notifyLibraryUpdated()
        scope.launch {
            saveToPrefs()

            if (tokenManager.isGuestMode()) return@launch
            val token = tokenManager.getAccessToken()
            if (!token.isNullOrEmpty()) {
                try {
                    val safePermalink = permalink ?: ""
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

    fun replaceAllLikes(serverTracks: List<Track>) {
        _likedTracks.update { currentLocalList ->
            val blacklist = getBlacklist()

            val serverList = serverTracks
                .filter { !blacklist.contains(it.id) }
                .map { it.copy(isLiked = true) }

            val combined = currentLocalList + serverList

            val mergedAndDeduplicated = combined
                .groupBy { it.id }
                .map { (_, tracks) -> tracks.maxByOrNull { it.likedAt ?: 0L }!! }

            mergedAndDeduplicated.sortedByDescending { it.likedAt ?: System.currentTimeMillis() }
        }

        scope.launch { saveToPrefs() }
        _isSyncing.value = false
    }

    fun setSyncing(isSync: Boolean) {
        _isSyncing.value = isSync
    }

    private fun saveToPrefs() {
        val json = gson.toJson(_likedTracks.value)
        prefs.putString(KEY_LIKED_TRACKS, json)
        putStringSet(KEY_LIKED_PLAYLISTS, _likedPlaylists.value.map { it.toString() }.toSet())
    }

    private fun loadFromPrefs() {
        val json = prefs.getString(KEY_LIKED_TRACKS, null)
        if (json != null) {
            try {
                val type: Type = object : TypeToken<List<Track>>() {}.type
                val loadedList: List<Track> = gson.fromJson(json, type) ?: emptyList()

                val now = System.currentTimeMillis()
                val migratedList = loadedList.mapIndexed { index, track ->
                    if (track.likedAt == null || track.likedAt == 0L) {
                        track.copy(likedAt = now - (index * 1000))
                    } else {
                        track
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


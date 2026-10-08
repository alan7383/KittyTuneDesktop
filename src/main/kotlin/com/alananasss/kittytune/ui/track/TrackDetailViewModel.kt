package com.alananasss.kittytune.ui.track

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.core.AndroidViewModel
import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The page of a track: who liked and reposted it, which playlists it is in, and what is like it.
 *
 * Likers and reposters open by followers, the most followed first, and playlists by likes (round 2 of the tester's
 * list, item 11): a list of everyone who liked a track is read for the names in it that matter. The service hands
 * them out by when, a page at a time, so a few pages are fetched after the first to give "most followed" something
 * to choose from, and the rest as the list is scrolled. Everything loaded is kept in [TrackDetailsCache] and comes
 * back when the page is opened again.
 */
class TrackDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create()

    var track by mutableStateOf<Track?>(null)
    var isLoading by mutableStateOf(true)

    // What the lists show, in the order they are sorted to.
    val likers = mutableStateListOf<User>()
    val reposters = mutableStateListOf<User>()
    val inPlaylists = mutableStateListOf<Playlist>()
    val relatedTracks = mutableStateListOf<Track>()

    // The same, as the service gave them, for going back to that order.
    private val likersArrived = mutableListOf<User>()
    private val repostersArrived = mutableListOf<User>()
    private val playlistsArrived = mutableListOf<Playlist>()

    // pagination cursors (next_href)
    private var likersNextUrl: String? = null
    private var repostersNextUrl: String? = null
    private var playlistsNextUrl: String? = null
    private var relatedNextUrl: String? = null

    // individual loading states for infinite scroll
    var isLikersLoadingMore by mutableStateOf(false)
    var isRepostersLoadingMore by mutableStateOf(false)
    var isPlaylistsLoadingMore by mutableStateOf(false)
    var isRelatedLoadingMore by mutableStateOf(false)

    var isUsersSortedByFollowers by mutableStateOf(true)
        private set
    var isPlaylistsSortedByLikes by mutableStateOf(true)
        private set

    private var loadedTrackId: Long = 0L
    private var prefetchJob: Job? = null
    private var playlistsFetchJob: Job? = null

    fun loadTrackDetails(trackId: Long) {
        if (trackId == 0L) {
            isLoading = false
            return
        }
        if (this.track?.id == trackId) {
            isLoading = false
            return
        }
        loadedTrackId = trackId
        TrackDetailsCache.get(trackId)?.let { restore(it); return }

        viewModelScope.launch {
            isLoading = true
            // clean slate
            likers.clear(); likersArrived.clear(); likersNextUrl = null
            reposters.clear(); repostersArrived.clear(); repostersNextUrl = null
            inPlaylists.clear(); playlistsArrived.clear(); playlistsNextUrl = null
            relatedTracks.clear(); relatedNextUrl = null

            try {
                coroutineScope {
                    val trackDef = async { api.getTracksByIds(trackId.toString()).firstOrNull() }
                    val likersResponseDef = async { try { api.getTrackLikers(trackId) } catch (e: Exception) { null } }
                    val repostersResponseDef = async { try { api.getTrackReposters(trackId) } catch (e: Exception) { null } }
                    val playlistsResponseDef = async { try { api.getTrackInPlaylists(trackId, limit = PLAYLISTS_PAGE) } catch (e: Exception) { null } }
                    val relatedResponseDef = async { try { api.getRelatedTracks(trackId) } catch (e: Exception) { null } }

                    track = trackDef.await()

                    likersResponseDef.await()?.let {
                        likersArrived.addAll(it.collection)
                        likersNextUrl = it.next_href
                    }
                    repostersResponseDef.await()?.let {
                        repostersArrived.addAll(it.collection)
                        repostersNextUrl = it.next_href
                    }
                    playlistsResponseDef.await()?.let {
                        playlistsArrived.addAll(it.collection)
                        playlistsNextUrl = it.next_href
                    }
                    relatedResponseDef.await()?.let {
                        relatedTracks.addAll(it.collection)
                        relatedNextUrl = it.next_href
                    }
                }
                showUsers()
                showPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
                save()
            }
            prefetchUsers()
            fetchRestOfPlaylists()
        }
    }

    // --- the order of the lists ---

    private fun showUsers() {
        likers.clear(); likers.addAll(TrackDetailOrder.users(likersArrived, isUsersSortedByFollowers))
        reposters.clear(); reposters.addAll(TrackDetailOrder.users(repostersArrived, isUsersSortedByFollowers))
    }

    private fun showPlaylists() {
        inPlaylists.clear(); inPlaylists.addAll(TrackDetailOrder.playlists(playlistsArrived.distinctBy { it.id }, isPlaylistsSortedByLikes))
    }

    fun toggleUsersSort() {
        isUsersSortedByFollowers = !isUsersSortedByFollowers
        showUsers()
        save()
    }

    fun toggleSortPlaylists() {
        isPlaylistsSortedByLikes = !isPlaylistsSortedByLikes
        showPlaylists()
        // By likes it is worth having them all before choosing the top.
        if (isPlaylistsSortedByLikes) fetchRestOfPlaylists()
        save()
    }

    /**
     * A few more pages of those who liked and reposted, once, so "the most followed first" is chosen from more than one
     * page of who happened to come last. The list is replaced once when they are in rather than as each arrives, so it
     * does not shuffle under the pointer.
     */
    private fun prefetchUsers() {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch {
            val id = loadedTrackId
            try {
                repeat(PREFETCH_PAGES) {
                    val nextLikers = likersNextUrl
                    if (nextLikers != null) {
                        val page = api.getLikersNextPage(nextLikers)
                        likersArrived.addAll(page.collection); likersNextUrl = page.next_href
                    }
                    val nextReposters = repostersNextUrl
                    if (nextReposters != null) {
                        val page = api.getRepostersNextPage(nextReposters)
                        repostersArrived.addAll(page.collection); repostersNextUrl = page.next_href
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (id == loadedTrackId) {
                showUsers()
                save()
            }
        }
    }

    /** The rest of the playlists the track is in, from where the first page stopped, to sort them all by likes. */
    private fun fetchRestOfPlaylists() {
        if (!isPlaylistsSortedByLikes || playlistsNextUrl == null) return
        playlistsFetchJob?.cancel()
        playlistsFetchJob = viewModelScope.launch {
            val id = loadedTrackId
            isPlaylistsLoadingMore = true
            try {
                var next = playlistsNextUrl
                while (next != null) {
                    val res = api.getInPlaylistsNextPage(next)
                    playlistsArrived.addAll(res.collection)
                    next = res.next_href
                    playlistsNextUrl = next
                }
                if (id == loadedTrackId) showPlaylists()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isPlaylistsLoadingMore = false
                save()
            }
        }
    }

    // --- load more functions ---

    fun loadMoreLikers() {
        if (isLikersLoadingMore || likersNextUrl == null) return
        viewModelScope.launch {
            isLikersLoadingMore = true
            try {
                val res = api.getLikersNextPage(likersNextUrl!!)
                likersArrived.addAll(res.collection)
                likersNextUrl = res.next_href
                showUsers()
            } catch (e: Exception) { e.printStackTrace() }
            finally { isLikersLoadingMore = false; save() }
        }
    }

    fun loadMoreReposters() {
        if (isRepostersLoadingMore || repostersNextUrl == null) return
        viewModelScope.launch {
            isRepostersLoadingMore = true
            try {
                val res = api.getRepostersNextPage(repostersNextUrl!!)
                repostersArrived.addAll(res.collection)
                repostersNextUrl = res.next_href
                showUsers()
            } catch (e: Exception) { e.printStackTrace() }
            finally { isRepostersLoadingMore = false; save() }
        }
    }

    fun loadMorePlaylists() {
        if (isPlaylistsLoadingMore || playlistsNextUrl == null) return
        viewModelScope.launch {
            isPlaylistsLoadingMore = true
            try {
                val res = api.getInPlaylistsNextPage(playlistsNextUrl!!)
                playlistsArrived.addAll(res.collection)
                playlistsNextUrl = res.next_href
                showPlaylists()
            } catch (e: Exception) { e.printStackTrace() }
            finally { isPlaylistsLoadingMore = false; save() }
        }
    }

    fun loadMoreRelated() {
        if (isRelatedLoadingMore || relatedNextUrl == null) return
        viewModelScope.launch {
            isRelatedLoadingMore = true
            try {
                val res = api.getRelatedTracksNextPage(relatedNextUrl!!)
                relatedTracks.addAll(res.collection)
                relatedNextUrl = res.next_href
            } catch (e: Exception) { e.printStackTrace() }
            finally { isRelatedLoadingMore = false; save() }
        }
    }

    // --- what is kept ---

    private fun save() {
        val current = track ?: return
        TrackDetailsCache.put(
            current.id,
            TrackDetailsCache.Snapshot(
                track = current,
                likers = likersArrived.toList(),
                reposters = repostersArrived.toList(),
                playlists = playlistsArrived.toList(),
                related = relatedTracks.toList(),
                likersNext = likersNextUrl,
                repostersNext = repostersNextUrl,
                playlistsNext = playlistsNextUrl,
                relatedNext = relatedNextUrl,
                usersByFollowers = isUsersSortedByFollowers,
                playlistsByLikes = isPlaylistsSortedByLikes,
                takenAtMs = System.currentTimeMillis(),
            )
        )
    }

    private fun restore(snapshot: TrackDetailsCache.Snapshot) {
        track = snapshot.track
        likersArrived.clear(); likersArrived.addAll(snapshot.likers)
        repostersArrived.clear(); repostersArrived.addAll(snapshot.reposters)
        playlistsArrived.clear(); playlistsArrived.addAll(snapshot.playlists)
        relatedTracks.clear(); relatedTracks.addAll(snapshot.related)
        likersNextUrl = snapshot.likersNext
        repostersNextUrl = snapshot.repostersNext
        playlistsNextUrl = snapshot.playlistsNext
        relatedNextUrl = snapshot.relatedNext
        isUsersSortedByFollowers = snapshot.usersByFollowers
        isPlaylistsSortedByLikes = snapshot.playlistsByLikes
        showUsers()
        showPlaylists()
        isLoading = false
    }

    private companion object {
        const val PLAYLISTS_PAGE = 50

        /** Pages of likers and reposters fetched after the first, for the sort by followers to choose from. */
        const val PREFETCH_PAGES = 3
    }
}

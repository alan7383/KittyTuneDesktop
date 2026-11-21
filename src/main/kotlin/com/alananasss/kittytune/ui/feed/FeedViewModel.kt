package com.alananasss.kittytune.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.core.AndroidViewModel
import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.mix.MixRanking
import com.alananasss.kittytune.data.network.FollowingFeedGraphQl
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.wave.DiscoverFeed
import com.alananasss.kittytune.data.wave.WaveFeedback
import com.alananasss.kittytune.data.wave.soundCloudFeedCursor
import com.alananasss.kittytune.domain.StreamItem
import com.alananasss.kittytune.domain.StreamResponse
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create()
    private val prefs = PlayerPreferences()
    private var source = DiscoverFeed()
    private var discoverRequest: Job? = null
    private var followingRequest: Job? = null
    private var nextPage by mutableStateOf<String?>(null)
    private var followingLoaded = false
    private val followingCursors = mutableSetOf<String>()
    private data class PostKey(val type: String, val id: String, val author: Long?, val at: String?)
    private val seenPosts = mutableSetOf<PostKey>()
    private val seenDiscover = mutableSetOf<Long>()
    private val followRequests = mutableSetOf<Long>()

    val discover = mutableStateListOf<Track>()
    val feedItems = mutableStateListOf<StreamItem>()
    val followState = mutableStateMapOf<Long, Boolean>()

    var officialOnly by mutableStateOf(prefs.getFeedOfficialOnlyEnabled())
        private set
    var discoverLoading by mutableStateOf(false)
        private set
    var discoverFailed by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf(false)
        private set
    val hasMore get() = nextPage != null
    val isLoggedIn get() = !TokenManager.isGuestMode() && TokenManager.hasAccessToken()

    init {
        viewModelScope.launch {
            SessionManager.isClientIdValid.collect { ready ->
                if (ready && discover.isEmpty()) loadDiscover()
            }
        }
    }

    fun applyOfficialOnly(enabled: Boolean) {
        if (enabled == officialOnly) return
        officialOnly = enabled
        resetDiscover()
        if (followingLoaded || isLoading || isLoadingMore) refresh()
    }

    fun resetDiscover() {
        discoverRequest?.cancel()
        source = DiscoverFeed()
        discoverLoading = false
        discover.clear()
        seenDiscover.clear()
        loadDiscover()
    }

    fun loadDiscover() {
        if (discoverLoading || source.exhausted) return
        val current = source
        discoverLoading = true
        discoverFailed = false
        discoverRequest = viewModelScope.launch {
            try {
                repeat(4) {
                    val batch = current.nextBatch(seenDiscover.toSet())
                    ensureActive()
                    val visible = batch.filter { seenDiscover.add(it.id) && shouldShow(it) }
                    discover.addAll(visible)
                    if (visible.isNotEmpty() || current.exhausted) return@launch
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                discoverFailed = true
            } finally {
                if (current === source) discoverLoading = false
            }
        }
    }

    private fun shouldShow(track: Track): Boolean =
        !BlockManager.isBlocked(track) && WaveFeedback.isWelcome(track) &&
            MixRanking.isPlayable(track, prefs.getYouTubeFallbackEnabled()) &&
            (!officialOnly || MixRanking.isOfficialArtist(track))

    fun dislike(track: Track) {
        WaveFeedback.onDisliked(track)
        WaveFeedback.dislikeArtist(track.displayArtist)
    }

    fun ensureFollowing() {
        if (!followingLoaded && !isLoading) refresh()
    }

    fun refresh() = fetchFollowing(reset = true)
    fun loadMore() = fetchFollowing(reset = false)

    private fun fetchFollowing(reset: Boolean) {
        if (!isLoggedIn || !reset && (isLoading || isLoadingMore || nextPage == null)) return
        followingRequest?.cancel()
        if (reset) {
            feedItems.clear()
            seenPosts.clear()
            followingCursors.clear()
            nextPage = null
        }
        isLoading = reset
        isLoadingMore = !reset
        error = false
        followingRequest = viewModelScope.launch {
            var cancelled = false
            try {
                val cursor = nextPage
                val response = when {
                    cursor == null -> firstFollowingPage()
                    cursor.startsWith("https://") -> api.getStreamNextPage(cursor)
                    else -> followingGraphQl(cursor)
                }
                ensureActive()
                feedItems.addAll(response.collection.filter { item ->
                    val id = item.track?.id?.toString() ?: item.playlist?.id?.toString() ?: return@filter false
                    seenPosts.add(PostKey(item.type, id, item.user?.id, item.createdAt))
                })
                nextPage = response.next_href?.takeUnless(String::isBlank)?.let {
                    if (it.contains("://")) soundCloudFeedCursor(it) else it
                }?.takeIf(followingCursors::add)
                followingLoaded = true
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } catch (_: Exception) {
                error = true
            } finally {
                if (!cancelled) {
                    isLoading = false
                    isLoadingMore = false
                }
            }
        }
    }

    private suspend fun firstFollowingPage(): StreamResponse {
        // The GraphQL activity fragment carries verification status. The REST representation
        // also supplies publisher metadata, needed when filtering by ISRC or Pro status.
        return try {
            if (officialOnly) api.getMyStream(limit = 20) else followingGraphQl(null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (officialOnly) followingGraphQl(null) else api.getMyStream(limit = 20)
        }
    }

    private suspend fun followingGraphQl(page: String?) =
        api.getFollowingFeedGraphQL(FollowingFeedGraphQl.request(page)).let { response ->
            response.errorMessage()?.let { kotlin.error(it) }
            response.toStreamResponse()
        }

    fun checkFollow(id: Long) {
        if (!isLoggedIn || id <= 0 || id in followState || !followRequests.add(id)) return
        viewModelScope.launch {
            try {
                val response = api.checkFollowState(id)
                if (response.isSuccessful || response.code() == 404) followState[id] = response.isSuccessful
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // An unavailable lookup must not be presented as an unfollowed account.
            } finally {
                followRequests.remove(id)
            }
        }
    }

    fun toggleFollow(id: Long) {
        val previous = followState[id] ?: return
        if (!followRequests.add(id)) return
        followState[id] = !previous
        viewModelScope.launch {
            try {
                val result = if (previous) api.unfollowUser(id) else api.followUser(id)
                if (!result.isSuccessful) followState[id] = previous
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                followState[id] = previous
            } finally {
                followRequests.remove(id)
            }
        }
    }
}

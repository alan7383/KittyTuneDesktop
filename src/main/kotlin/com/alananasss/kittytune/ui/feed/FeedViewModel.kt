package com.alananasss.kittytune.ui.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.core.Application
import com.alananasss.kittytune.core.AndroidViewModel
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.network.FollowingFeedGraphQl
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.StreamItem
import kotlinx.coroutines.launch

class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create()

    val feedItems = mutableStateListOf<StreamItem>()

    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private var nextPage: String? = null
    private var initialized = false

    init {
        viewModelScope.launch {
            com.alananasss.kittytune.data.SessionManager.isClientIdValid.collect { isReady ->
                if (isReady && !initialized) {
                    initialized = true
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (!TokenManager.hasAccessToken()) return@launch
            isLoading = true
            error = null
            feedItems.clear()
            nextPage = null
            try {
                val response = api.getFollowingFeedGraphQL(FollowingFeedGraphQl.request(page = null))
                response.errorMessage()?.let { throw IllegalStateException(it) }
                val streamResponse = response.toStreamResponse()
                feedItems.addAll(streamResponse.collection)
                nextPage = streamResponse.next_href
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        val page = nextPage ?: return
        if (isLoadingMore) return
        viewModelScope.launch {
            isLoadingMore = true
            try {
                val response = api.getFollowingFeedGraphQL(FollowingFeedGraphQl.request(page = page))
                val streamResponse = response.toStreamResponse()
                feedItems.addAll(streamResponse.collection)
                nextPage = streamResponse.next_href
            } catch (e: Exception) {
                // silent
            } finally {
                isLoadingMore = false
            }
        }
    }

    val hasMore get() = nextPage != null
}

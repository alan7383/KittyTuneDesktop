package com.alananasss.kittytune.ui.library

import com.alananasss.kittytune.core.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alananasss.kittytune.core.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YoutubeRadioViewModel(application: Application) : AndroidViewModel(application) {
    val tracks = mutableStateListOf<Track>()
    var isLoading by mutableStateOf(true)
    var isLoadingMore by mutableStateOf(false)

    var playlistTitle by mutableStateOf("")
    var playlistCover by mutableStateOf<String?>(null)
    var playlistUser by mutableStateOf<User?>(null)

    private var videoId: String? = null

    fun loadInitial(youtubeUrl: String) {
        if (tracks.isNotEmpty()) return
        viewModelScope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val cleanId = youtubeUrl.substringAfter("v=").substringBefore("&")
                    videoId = cleanId

                    com.alananasss.kittytune.data.StreamResolver.init()
                    val youtubeService = org.schabi.newpipe.extractor.ServiceList.YouTube
                    val streamInfo = org.schabi.newpipe.extractor.stream.StreamInfo.getInfo(
                        youtubeService,
                        "https://youtube.com/watch?v=$cleanId"
                    )

                    val items = streamInfo.relatedItems.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()

                    val newTracks = items.map { item ->
                        Track(
                            id = kotlin.math.abs(item.url.hashCode().toLong()),
                            title = item.name,
                            user = User(0L, item.uploaderName ?: "YouTube", null),
                            artworkUrl = item.thumbnails.firstOrNull()?.url,
                            durationMs = item.duration * 1000L,
                            permalinkUrl = item.url,
                            source = "youtube"
                        )
                    }

                    withContext(Dispatchers.Main) {
                        val firstArtist = newTracks.firstOrNull()?.user?.username
                        playlistTitle = if (firstArtist != null) "Mix • $firstArtist" else "YouTube Mix"
                        playlistCover = newTracks.firstOrNull()?.artworkUrl
                        playlistUser = User(0, "YouTube", null)
                        tracks.clear()
                        tracks.addAll(newTracks)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore) return
        viewModelScope.launch {
            isLoadingMore = true
            withContext(Dispatchers.IO) {
                try {
                    val query = tracks.randomOrNull()?.title ?: "music"
                    com.alananasss.kittytune.data.StreamResolver.init()
                    val youtubeService = org.schabi.newpipe.extractor.ServiceList.YouTube
                    val searchInfo = org.schabi.newpipe.extractor.search.SearchInfo.getInfo(
                        youtubeService,
                        youtubeService.searchQHFactory.fromQuery(query, listOf("videos"), "")
                    )

                    val newTracks = searchInfo.relatedItems.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>().map { item ->
                        Track(
                            id = kotlin.math.abs(item.url.hashCode().toLong()),
                            title = item.name,
                            user = User(0L, item.uploaderName ?: "YouTube", null),
                            artworkUrl = item.thumbnails.firstOrNull()?.url,
                            durationMs = item.duration * 1000L,
                            permalinkUrl = item.url,
                            source = "youtube"
                        )
                    }

                    withContext(Dispatchers.Main) {
                        val existingIds = tracks.map { it.id }.toSet()
                        tracks.addAll(newTracks.filter { !existingIds.contains(it.id) })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    withContext(Dispatchers.Main) { isLoadingMore = false }
                }
            }
        }
    }
}

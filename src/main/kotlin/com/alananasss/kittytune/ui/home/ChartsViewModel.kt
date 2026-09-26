    package com.alananasss.kittytune.ui.home
    
    import com.alananasss.kittytune.core.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import com.alananasss.kittytune.core.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.ChartsData
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.google.gson.Gson
    import kotlinx.coroutines.async
    import kotlinx.coroutines.awaitAll
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.launch
    
    data class ArtistRanking(
        val user: User,
        val score: Long, // Keep the score for sorting, but we will display the followers
        val rank: Int
    )

    /**
     * A chart is a list of songs in order, so the two things worth choosing between are which list and
     * which genre — not which country. This mirrors how SoundCloud addresses its own charts
     * (`charts-<kind>:<genre>` system playlists, with `top` and `trending` as the only two kinds).
     */
    enum class ChartKind(val apiValue: String) {
        TOP("top"),
        TRENDING("trending"),
    }

    /** Genres the chart endpoint is asked for. `all-music` is SoundCloud's own catch-all. */
    data class ChartGenre(val id: String, val apiValue: String)

    /** One song at its place in the chart. [rank] is the position, never the score. */
    data class ChartEntry(val rank: Int, val track: Track, val score: Double)

    class ChartsViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create()
        private val gson = Gson()

        var selectedCountryIndex by mutableStateOf(0)
        val chartPlaylists = mutableStateListOf<Playlist>()
        val topArtists = mutableStateListOf<ArtistRanking>()

        var isLoading by mutableStateOf(false)

        // ── The song chart ──
        var chartKind by mutableStateOf(ChartKind.TOP)
        var chartGenre by mutableStateOf(chartGenres.first())
        val chartEntries = mutableStateListOf<ChartEntry>()
        var isChartLoading by mutableStateOf(false)

        init {
            loadCountryCharts(0)
            loadChart(ChartKind.TOP, chartGenres.first())
        }

        /**
         * Loads the ranked song list.
         *
         * The endpoint returns the songs in chart order with a score beside each one, so the rank is
         * simply the position: re-sorting by score would be second-guessing a list the server has
         * already ordered, and on `trending` the score is a velocity, not a size.
         */
        fun loadChart(kind: ChartKind, genre: ChartGenre) {
            chartKind = kind
            chartGenre = genre

            viewModelScope.launch {
                isChartLoading = true
                try {
                    val response = api.getCharts(
                        kind = kind.apiValue,
                        genre = genre.apiValue,
                        limit = CHART_LENGTH,
                    )
                    val entries = response.collection.mapNotNull { item ->
                        item.track?.let { ChartEntry(rank = 0, track = it, score = item.score ?: 0.0) }
                    }.mapIndexed { index, entry -> entry.copy(rank = index + 1) }

                    // A switch made mid-flight must not leave the newer request's answer overwritten
                    // by the older one's.
                    if (kind == chartKind && genre == chartGenre) {
                        chartEntries.clear()
                        chartEntries.addAll(entries)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (kind == chartKind && genre == chartGenre) isChartLoading = false
                }
            }
        }
    
        fun loadCountryCharts(index: Int) {
            selectedCountryIndex = index
            val countryData = ChartsData.charts[index]
    
            viewModelScope.launch {
                isLoading = true
                chartPlaylists.clear()
                topArtists.clear()
    
                try {
                    // Parallel playlist retrieval
                    val playlists = coroutineScope {
                        countryData.playlistUrls.map { url ->
                            async {
                                try {
                                    val resolvedJson = api.resolveUrl(url)
                                    val kind = resolvedJson.get("kind")?.asString
                                    if (kind == "playlist") {
                                        gson.fromJson(resolvedJson, Playlist::class.java)
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
    
                    chartPlaylists.addAll(playlists)
                    calculateTopArtists(playlists)
    
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    
        private fun calculateTopArtists(playlists: List<Playlist>) {
            val userMap = mutableMapOf<Long, User>()
            val playCounts = mutableMapOf<Long, Long>()
    
            playlists.forEach { playlist ->
                playlist.tracks?.forEach { track ->
                    val user = track.user
                    if (user != null && user.id > 0) {
                        // Keep the user object as complete as possible
                        if (!userMap.containsKey(user.id) || (user.followersCount > (userMap[user.id]?.followersCount ?: 0))) {
                            userMap[user.id] = user
                        }
                        // Sort by popularity in the charts (plays) anyway
                        val current = playCounts.getOrDefault(user.id, 0L)
                        playCounts[user.id] = current + track.playbackCount.toLong()
                    }
                }
            }
    
            val sorted = playCounts.entries
                .sortedByDescending { it.value }
                .take(40)
                .mapIndexed { index, entry ->
                    val user = userMap[entry.key]!!
                    ArtistRanking(
                        user = user,
                        score = entry.value,
                        rank = index + 1
                    )
                }
    
            topArtists.addAll(sorted)
        }
    
        fun fetchArtistTopTracks(userId: Long, onResult: (List<Track>) -> Unit) {
            viewModelScope.launch {
                try {
                    val tracks = api.getUserTopTracks(userId, limit = 20).collection
                    onResult(tracks)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(emptyList())
                }
            }
        }

        companion object {
            /** A chart long enough to feel like one. SoundCloud's own are 50 and 500. */
            const val CHART_LENGTH = 50

            val chartGenres = listOf(
                ChartGenre("all", "soundcloud:genres:all-music"),
                ChartGenre("pop", "soundcloud:genres:pop"),
                ChartGenre("hiphop", "soundcloud:genres:hiphoprap"),
                ChartGenre("electronic", "soundcloud:genres:electronic"),
                ChartGenre("rock", "soundcloud:genres:rock"),
                ChartGenre("rnb", "soundcloud:genres:rnb"),
                ChartGenre("country", "soundcloud:genres:country"),
                ChartGenre("latin", "soundcloud:genres:latin"),
            )
        }
    }



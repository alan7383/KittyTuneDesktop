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
     * which genre.
     *
     * The two lists are not the same request, which is worth stating because it was not obvious:
     * `GET /charts` answers only `kind=trending`. `kind=top` comes back as an empty object, with or
     * without a genre, and so does every genre other than `all-music` — the endpoint narrowed to one
     * live feed. The Top 50 is a different thing entirely: it is a playlist SoundCloud curates by
     * hand and publishes at `soundcloud.com/music-charts-<country>/sets/<slug>`, fifty tracks in
     * order, which `/resolve` hands back whole. So the genre row belongs to [ChartKind.TOP] and the
     * trending feed has no genre to pick.
     */
    enum class ChartKind {
        /**
         * What is played most where the listener is: Deezer's chart for the country its servers see the
         * request come from. SoundCloud publishes charts for a handful of countries only, so its own were a
         * US list for everyone else (issue #66).
         */
        COUNTRY,
        TOP,
        TRENDING,
    }

    /** The label key for a chart kind. */
    fun ChartKind.labelKey(): String = when (this) {
        ChartKind.COUNTRY -> "chart_kind_country"
        ChartKind.TOP -> "chart_kind_top"
        ChartKind.TRENDING -> "chart_kind_trending"
    }

    /**
     * A chart playlist, by the slug SoundCloud publishes it under.
     *
     * [slug] is the path segment after `/sets/`; the country is the `music-charts-<cc>` account, which
     * is why the same genre has a different chart in a different market.
     */
    data class ChartGenre(val id: String, val slug: String)

    /**
     * A country whose chart can be chosen. One for each language the app is translated into, plus the UK; the
     * name is what Deezer calls the country in its "Top <country>" playlists, the storefront where Apple Music
     * keeps its chart for it.
     *
     * @param hasDeezerChart false where Deezer stopped keeping one: it left Russia, and its "Top Russia" has held a
     *   single track since, so that chart comes from Apple Music straight away (issue #66).
     */
    enum class ChartCountry(
        val code: String,
        val deezerName: String,
        val flag: String,
        val language: String,
        val appleStorefront: String,
        val hasDeezerChart: Boolean = true,
    ) {
        RU("RU", "Russia", "\uD83C\uDDF7\uD83C\uDDFA", "ru", "ru", hasDeezerChart = false),
        US("US", "United States", "\uD83C\uDDFA\uD83C\uDDF8", "en", "us"),
        UK("UK", "United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7", "en", "gb"),
        DE("DE", "Germany", "\uD83C\uDDE9\uD83C\uDDEA", "de", "de"),
        FR("FR", "France", "\uD83C\uDDEB\uD83C\uDDF7", "fr", "fr"),
        HU("HU", "Hungary", "\uD83C\uDDED\uD83C\uDDFA", "hu", "hu"),
        VN("VN", "Vietnam", "\uD83C\uDDFB\uD83C\uDDF3", "vi", "vn"),
        // Countries where Spotify keeps its own Top 50, so the chart opens at once. No Deezer chart is assumed for them:
        // Deezer answers an unknown country with its world chart, which would pass for a local one.
        FI("FI", "Finland", "", "fi", "fi", hasDeezerChart = false),
        SE("SE", "Sweden", "", "sv", "se", hasDeezerChart = false),
        NO("NO", "Norway", "", "no", "no", hasDeezerChart = false),
        PL("PL", "Poland", "", "pl", "pl", hasDeezerChart = false),
        IT("IT", "Italy", "", "it", "it", hasDeezerChart = false),
        ES("ES", "Spain", "", "es", "es", hasDeezerChart = false),
        NL("NL", "Netherlands", "", "nl", "nl", hasDeezerChart = false),
        CA("CA", "Canada", "", "en", "ca", hasDeezerChart = false),
        AU("AU", "Australia", "", "en", "au", hasDeezerChart = false),
        JP("JP", "Japan", "", "ja", "jp", hasDeezerChart = false),
        BR("BR", "Brazil", "", "pt", "br", hasDeezerChart = false),
        MX("MX", "Mexico", "", "es", "mx", hasDeezerChart = false),
        IE("IE", "Ireland", "", "en", "ie", hasDeezerChart = false),
        AT("AT", "Austria", "", "de", "at", hasDeezerChart = false),
        CZ("CZ", "Czech Republic", "", "cs", "cz", hasDeezerChart = false);

        companion object {
            /** The country whose language the app is in. */
            fun forLanguage(language: String): ChartCountry = entries.firstOrNull { it.language == language } ?: US

            /**
             * The country the computer is set to: the region of the formats (Windows "Region"), not the language of the
             * app. A Russian interface in Finland is a listener in Finland, and the chart for "your country" is Finland's.
             */
            fun forDevice(language: String): ChartCountry {
                val region = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT).country.uppercase()
                val code = if (region == "GB") "UK" else region
                return entries.firstOrNull { it.code == code } ?: forLanguage(language)
            }
        }
    }

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
        var chartKind by mutableStateOf(ChartKind.COUNTRY)
        var chartGenre by mutableStateOf(chartGenres.first())
        var chartCountry by mutableStateOf(ChartCountry.forDevice(com.alananasss.kittytune.core.Strings.resolvedLanguage))
        val chartEntries = mutableStateListOf<ChartEntry>()
        var isChartLoading by mutableStateOf(false)

        init {
            loadCountryCharts(0)
            loadChart(ChartKind.COUNTRY, chartGenres.first())
        }

        /**
         * Loads the ranked song list.
         *
         * The rank is the row's position and never the score: on the trending feed the score is a
         * velocity, not a size, and re-sorting it would be second-guessing an order the server has
         * already decided.
         */
        fun loadChart(kind: ChartKind, genre: ChartGenre) {
            chartKind = kind
            chartGenre = genre

            viewModelScope.launch {
                isChartLoading = true
                val entries = fetchChart(api, kind, genre, CHART_LENGTH, currentCountryCode(), chartCountry)
                // A switch made mid-flight must not leave the newer request's answer overwritten by
                // the older one's.
                if (kind == chartKind && genre == chartGenre) {
                    chartEntries.clear()
                    chartEntries.addAll(entries)
                    isChartLoading = false
                }
            }
        }

        fun selectChartCountry(country: ChartCountry) {
            chartCountry = country
            loadChart(chartKind, chartGenre)
        }

        fun currentCountryCode(): String =
            ChartsData.charts.getOrNull(selectedCountryIndex)?.countryCode ?: "US"

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
                ChartGenre("all", "all-music-genres"),
                ChartGenre("pop", "pop"),
                ChartGenre("hiphop", "hip-hop"),
                ChartGenre("electronic", "electronic"),
                ChartGenre("rock", "rock"),
                ChartGenre("rnb", "r-b"),
                ChartGenre("country", "country"),
                ChartGenre("latin", "latin"),
                ChartGenre("folk", "folk"),
            )

            /** The curated chart playlist URL, for a country code as [ChartsData] spells it. */
            fun chartPlaylistUrl(countryCode: String, genre: ChartGenre): String =
                "https://soundcloud.com/music-charts-${countryCode.lowercase()}/sets/${genre.slug}"
        }
    }



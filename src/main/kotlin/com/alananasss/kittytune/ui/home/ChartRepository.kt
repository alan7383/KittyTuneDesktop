package com.alananasss.kittytune.ui.home

import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Fetches a ranked song list.
 *
 * Each kind is a different request, and every answer is ordered by the server, so the rank
 * is always the position and never the score:
 *
 * - [ChartKind.COUNTRY] is Deezer's chart for the chosen country, its tracks played through the configured audio
 *   sources like any Deezer track; where Deezer keeps none, Apple Music's chart for it, each song matched to the
 *   same song on SoundCloud. See [countryChartTracks].
 * - [ChartKind.TRENDING] is `GET /charts?kind=trending`, the one live chart the endpoint serves. It
 *   ignores `genre` — every other genre than `all-music` comes back as an empty object — so the
 *   caller is not offered one here.
 * - [ChartKind.TOP] is the hand-curated 50 SoundCloud publishes per country and genre under
 *   `soundcloud.com/music-charts-<cc>/sets/<slug>`.
 *
 * @return the chart, or an empty list if it could not be read. A chart that failed is not a chart
 *   with fifty gaps in it, so nothing is invented to fill the row.
 */
internal suspend fun fetchChart(
    api: SoundCloudApi,
    kind: ChartKind,
    genre: ChartGenre,
    limit: Int,
    countryCode: String,
    country: ChartCountry? = null,
): List<ChartEntry> = try {
    val tracks: List<Track> = when (kind) {
        ChartKind.COUNTRY -> countryChartTracks(country, limit).ifEmpty {
            // Neither chart reachable: the trending feed, rather than an empty list.
            runCatching {
                api.getCharts(kind = "trending", genre = "soundcloud:genres:all-music", limit = limit)
                    .collection.mapNotNull { it.track }
            }.getOrDefault(emptyList())
        }

        ChartKind.TRENDING -> api.getCharts(
            kind = "trending",
            genre = "soundcloud:genres:all-music",
            limit = limit,
        ).collection.mapNotNull { it.track }

        ChartKind.TOP -> {
            val url = ChartsViewModel.chartPlaylistUrl(countryCode, genre)
            val resolved = api.resolveUrl(url)
            // The chart playlists are the only place a `kind` other than trending comes from, and
            // they are ordinary playlists: a resolve that answers with something else is a slug that
            // has moved, not a chart.
            if (resolved.get("kind")?.asString == "playlist") {
                val listed = Gson().fromJson(resolved, Playlist::class.java)?.tracks.orEmpty()
                fillInMissingTracks(api, listed.take(limit))
            } else {
                emptyList()
            }
        }
    }
    tracks
        .filter { it.id > 0L }
        .distinctBy { it.id }
        .mapIndexed { index, track -> ChartEntry(rank = index + 1, track = track, score = 0.0) }
} catch (e: Exception) {
    e.printStackTrace()
    emptyList()
}

/**
 * The chart of [country]: Deezer's where it keeps a real one, else Apple Music's.
 *
 * Apple's songs carry no stream, so each is matched to the same song on SoundCloud, several at a time and kept in
 * chart order; a song with no confident match is left out rather than replaced by a guess.
 */
private suspend fun countryChartTracks(country: ChartCountry?, limit: Int): List<Track> {
    val key = "${country?.deezerName ?: "all"}/$limit"
    // A chart changes over days, not minutes: the second visit to a country answers at once.
    countryCache[key]?.takeIf { System.currentTimeMillis() - it.first < COUNTRY_CACHE_MS }?.let { return it.second }
    val tracks = loadCountryChart(country, limit)
    if (tracks.isNotEmpty()) countryCache[key] = System.currentTimeMillis() to tracks
    return tracks
}

private val countryCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<Track>>>()

/** How long a country's chart is kept before it is read again. */
private const val COUNTRY_CACHE_MS = 6 * 60 * 60 * 1000L

private suspend fun loadCountryChart(country: ChartCountry?, limit: Int): List<Track> {
    if (country == null || country.hasDeezerChart) {
        val deezer = com.alananasss.kittytune.data.deezer.DeezerSearchRepository.countryChart(limit, country?.deezerName)
        if (deezer.size >= minOf(limit, MIN_REAL_CHART) || country == null) return deezer
    }
    val songs = com.alananasss.kittytune.data.applemusic.AppleMusicClient.topSongs(country.appleStorefront, CHART_FETCH)
    val matching = Semaphore(MATCH_PARALLELISM)
    val matched = coroutineScope {
        songs.map { song ->
            async { matching.withPermit { com.alananasss.kittytune.data.catalog.CatalogFallback.resolve(song) } }
        }.awaitAll()
    }
    return matched.filterNotNull().take(limit)
}

/** Fewer songs than this from Deezer is a chart it no longer keeps up. */
private const val MIN_REAL_CHART = 10

/** Apple's chart is read whole, so a few unmatched songs do not leave a short preview. */
private const val CHART_FETCH = 50

/** Songs matched to SoundCloud at once. */
private const val MATCH_PARALLELISM = 16

/**
 * Fills in the tracks a resolved playlist only names.
 *
 * `/resolve` embeds the first handful of a playlist's tracks in full and hands back the rest as a
 * bare id — no title, no artist, no artwork, and a null play count that the model turns into a zero.
 * A chart built straight off that is five real songs followed by forty-five rows reading "unknown
 * title, unknown artist, 0 plays", which is what the screen showed.
 *
 * So the nameless ones are asked for by id, in batches, and put back where the playlist had them:
 * `/tracks?ids=` answers in whatever order it likes, and a chart's whole meaning is its order.
 *
 * A track that is still nameless afterwards has been deleted, blocked or made private, and is
 * dropped rather than shown as a gap — a chart with a hole in it is honest; one with a row of
 * "unknown" is a bug wearing a chart's clothes.
 */
private suspend fun fillInMissingTracks(
    api: SoundCloudApi,
    listed: List<Track>,
): List<Track> {
    val missing = listed
        .filter { it.title.isNullOrBlank() }
        .mapNotNull { it.id.takeIf { id -> id > 0L } }
    if (missing.isEmpty()) return listed.filter { !it.title.isNullOrBlank() }

    val fetched = missing.chunked(ID_BATCH).map { batch ->
        runCatching { api.getTracksByIds(batch.joinToString(",")) }.getOrDefault(emptyList())
    }.flatten().associateBy { it.id }

    return listed.mapNotNull { entry ->
        val track = if (entry.title.isNullOrBlank()) fetched[entry.id] else entry
        track?.takeIf { !it.title.isNullOrBlank() }
    }
}

/** How many ids to ask for at once. The same batch size the new-releases screen uses. */
private const val ID_BATCH = 50

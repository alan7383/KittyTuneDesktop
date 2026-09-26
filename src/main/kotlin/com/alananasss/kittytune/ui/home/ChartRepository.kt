package com.alananasss.kittytune.ui.home

import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson

/**
 * Fetches a ranked song list.
 *
 * The two kinds are two different requests, and both answers are ordered by the server, so the rank
 * is always the position and never the score:
 *
 * - [ChartKind.TRENDING] is `GET /charts?kind=trending`, the one live chart the endpoint serves. It
 *   ignores `genre` — every other genre than `all-music` comes back as an empty object — so the
 *   caller is not offered one here.
 * - [ChartKind.TOP] is the hand-curated 50 SoundCloud publishes per country and genre under
 *   `soundcloud.com/music-charts-<cc>/sets/<slug>`, which `/resolve` returns with all fifty tracks
 *   already in order.
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
): List<ChartEntry> = try {
    val tracks: List<Track> = when (kind) {
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
                Gson().fromJson(resolved, Playlist::class.java)
                    ?.tracks
                    ?.take(limit)
                    .orEmpty()
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

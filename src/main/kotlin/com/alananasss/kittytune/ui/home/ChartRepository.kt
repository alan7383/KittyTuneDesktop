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

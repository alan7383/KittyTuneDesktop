package com.alananasss.kittytune.data.mix

import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Builds the mix: reads the listener, asks SoundCloud for candidates, hands the arithmetic to [MixRanking].
 *
 * ## What "a real algo" means here
 *
 * Not one request. A recommendation is a fan-out and a filter, and the shape below is the standard one: profile
 * the listener from their own history, turn that profile into *seeds*, expand each seed through an endpoint that
 * knows about similarity, then score and diversify what comes back (issue #33).
 *
 * The expansion uses three SoundCloud endpoints, each answering a different question, and the mix is better for
 * having all three than it would be with more of any one:
 *
 *  - **`/tracks/{id}/related`** — "what goes with this track". Their own collaborative filtering, and the
 *    strongest signal available, because it is built from what other listeners actually played next.
 *  - **`system-playlists:artist-stations:{id}`** — "an hour of this artist and their neighbours". This is what
 *    answers "in the Yeat style" directly: the station for an artist is not that artist's catalogue, it is the
 *    neighbourhood around them.
 *  - **`/search/tracks` filtered by tag** — "what is good in this genre right now". Weaker per candidate, but it
 *    is the only one that reaches artists the listener has no path to at all, and a mix with no strangers in it
 *    is not a mix.
 *
 * Seeds are drawn from the top of the profile but *sampled* rather than taken in order, so two presses on the
 * same evening give two different mixes.
 *
 * ## Why every step degrades instead of failing
 *
 * A mix is a nicety. If the history is empty, if a request times out, if the token has expired — the answer is a
 * shorter mix or a mix built from fewer seeds, never an error dialog. The only hard failure is having nothing at
 * all to work from, and that is reported as its own state so the button can say "listen to something first"
 * rather than spinning.
 */
object MixEngine {

    private val api by lazy { RetrofitClient.create() }

    /** How the mix was asked for. */
    sealed interface Recipe {
        /** From everything the listener plays. The plain "start mixing" press. */
        data object MyTaste : Recipe

        /** "In the Yeat style" — the neighbourhood around one artist, not that artist's catalogue. */
        data class LikeArtist(val artistId: Long?, val artistName: String) : Recipe

        /** One genre or tag, as SoundCloud spells it. */
        data class InGenre(val genre: String) : Recipe
    }

    sealed interface Result {
        data class Mixed(val tracks: List<Track>, val describedBy: String) : Result

        /** Nothing to profile and no seed given: the honest answer is "play something first". */
        data object NotEnoughHistory : Result

        /** Seeds were found and every expansion came back empty. Usually the network. */
        data object NothingFound : Result
    }

    /** How far back the profile looks. Longer than the decay's half-life, so old phases fade rather than cut. */
    private const val HISTORY_WINDOW_DAYS = 120L

    /** How many tracks a mix holds. Long enough to be an evening, short enough to be all listenable. */
    const val MIX_SIZE = 20

    suspend fun mix(recipe: Recipe = Recipe.MyTaste, size: Int = MIX_SIZE): Result =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val taste = profile(now)

            val seeds = seedsFor(recipe, taste)
            if (seeds.isEmpty()) {
                return@withContext if (taste.isEmpty) Result.NotEnoughHistory else Result.NothingFound
            }

            val candidates = expand(seeds)
            if (candidates.isEmpty()) return@withContext Result.NothingFound

            val tracks = MixRanking.order(candidates, taste, size, seed = now)
            if (tracks.isEmpty()) Result.NothingFound
            else Result.Mixed(tracks, describe(recipe, seeds))
        }

    /**
     * Turns a recipe and a profile into things worth expanding.
     *
     * ## Why the seeds are sampled and not taken in order
     *
     * Taking the top five artists gives the same five every time, so the same mix every time, and a mix you
     * cannot re-roll is a playlist with extra steps. Sampling from a wider band — the top [SEED_POOL] weighted
     * by their own weights — keeps the mix recognisably yours while making two presses two different answers.
     *
     * The split between artists, genres and tracks is deliberate rather than even. Artist stations are the
     * strongest signal, so they get the most seeds; a genre seed is the only one that reaches somebody you have
     * no path to at all, so there is always at least one when the profile can supply it.
     */
    private suspend fun seedsFor(recipe: Recipe, taste: MixProfile.Taste): List<Seed> = when (recipe) {
        is Recipe.LikeArtist -> {
            // "In the Yeat style." One seed, full affinity, and the station endpoint does the rest — an artist
            // station is the neighbourhood around them rather than their own back catalogue, which is exactly
            // what "in the style of" means and is why this is not just a search for their name.
            val id = recipe.artistId ?: resolveArtistId(recipe.artistName)
            if (id != null) listOf(Seed.OfArtist(id, recipe.artistName, 1f)) else emptyList()
        }

        is Recipe.InGenre -> listOf(Seed.OfGenre(recipe.genre, 1f))

        Recipe.MyTaste -> {
            if (taste.isEmpty) emptyList() else buildList {
                val random = java.util.Random(System.currentTimeMillis())

                // Artists, with their ids looked up from the same stats table the profile came from.
                val ids = artistIds()
                val artistPool = taste.rankedArtists.take(SEED_POOL).mapNotNull { name ->
                    ids[name]?.let { Triple(name, it, taste.artists[name] ?: 0f) }
                }
                sample(artistPool, ARTIST_SEEDS, random).forEach { (name, id, weight) ->
                    add(Seed.OfArtist(id, name, affinityOf(weight, taste.artists)))
                }

                // Tracks they have played most, for "what goes with this".
                val trackPool = runCatching {
                    ListeningStatsRepository.getTopTracks(sinceForSeeds(), limit = SEED_POOL)
                }.getOrDefault(emptyList())
                sample(trackPool, TRACK_SEEDS, random).forEach {
                    add(Seed.OfTrack(it.trackId, 0.8f))
                }

                // And a genre, which is the only seed that can reach a stranger.
                val genrePool = taste.rankedGenres.take(SEED_POOL).map { it to (taste.genres[it] ?: 0f) }
                sample(genrePool, GENRE_SEEDS, random).forEach { (genre, weight) ->
                    add(Seed.OfGenre(genre, affinityOf(weight, taste.genres) * 0.7f))
                }
            }
        }
    }

    /** Where a weight sits relative to the strongest one, so affinity is comparable across seed kinds. */
    private fun affinityOf(weight: Float, all: Map<String, Float>): Float {
        val top = all.values.maxOrNull() ?: return 0.5f
        return if (top <= 0f) 0.5f else (weight / top).coerceIn(0.15f, 1f)
    }

    /** Weighted-ish sampling without replacement: shuffled, then the first [count]. */
    private fun <T> sample(pool: List<T>, count: Int, random: java.util.Random): List<T> =
        if (pool.size <= count) pool else pool.shuffled(random).take(count)

    private const val SEED_POOL = 12
    private const val ARTIST_SEEDS = 3
    private const val TRACK_SEEDS = 2
    private const val GENRE_SEEDS = 1

    private fun sinceForSeeds(): Long =
        System.currentTimeMillis() - HISTORY_WINDOW_DAYS * 24 * 60 * 60 * 1000L

    /** Artist name to id, from the stats table, because the profile only carries names. */
    private suspend fun artistIds(): Map<String, Long> = runCatching {
        ListeningStatsRepository.getTopArtists(sinceForSeeds(), limit = 60)
            .mapNotNull { row -> row.artistId?.let { row.artistName.trim().lowercase() to it } }
            .toMap()
    }.getOrDefault(emptyMap())

    /** For a hand-picked artist that is not in the history: find them by name. */
    private suspend fun resolveArtistId(name: String): Long? = runCatching {
        api.searchUsers(name, limit = 1).collection.firstOrNull()?.id
    }.getOrNull()

    /**
     * Every seed expanded in parallel, with failures dropped.
     *
     * In parallel because a mix is five to six requests and doing them in series is five to six round trips of
     * waiting; dropped because one dead seed out of six should cost a few candidates, not the mix.
     */
    private suspend fun expand(seeds: List<Seed>): List<MixRanking.Candidate> = coroutineScope {
        seeds
            .map { seed -> async { runCatching { candidatesFor(seed) }.getOrDefault(emptyList()) } }
            .flatMap { it.await() }
    }

    private suspend fun candidatesFor(seed: Seed): List<MixRanking.Candidate> = when (seed) {
        is Seed.OfTrack ->
            api.getRelatedTracks(seed.trackId, limit = PER_SEED).collection
                .map { MixRanking.Candidate(it, seed.affinity) }

        is Seed.OfArtist ->
            api.getArtistStation(seed.artistId).tracks.orEmpty()
                .map { MixRanking.Candidate(it, seed.affinity) }

        is Seed.OfGenre ->
            // Sorted by recency rather than by all-time popularity: "what is good in this genre" should mean
            // this month, not the same five anthems everybody has already heard.
            api.searchTracksStrict(tag = seed.genre, sort = "recent", limit = PER_SEED).collection
                .map { MixRanking.Candidate(it, seed.affinity) }
    }

    /** How many candidates one seed contributes. Six seeds at this rate is plenty to fill twenty slots. */
    private const val PER_SEED = 20

    /** What the mix says it is, on the card. */
    private fun describe(recipe: Recipe, seeds: List<Seed>): String = when (recipe) {
        is Recipe.LikeArtist -> recipe.artistName
        is Recipe.InGenre -> recipe.genre
        Recipe.MyTaste -> seeds.filterIsInstance<Seed.OfArtist>()
            .take(2)
            .joinToString(", ") { it.name }
    }

    /**
     * The listener, from the plays the stats table already holds.
     *
     * Read rather than tracked: every play has been recorded with a timestamp and a listened duration since the
     * statistics feature shipped, which means the profile is available on the first press instead of needing its
     * own collection period.
     */
    private suspend fun profile(now: Long): MixProfile.Taste {
        val since = now - HISTORY_WINDOW_DAYS * 24 * 60 * 60 * 1000L
        val events = runCatching { ListeningStatsRepository.getEvents(since) }.getOrDefault(emptyList())
        val plays = events.map { event ->
            MixProfile.Play(
                trackId = event.trackId,
                artist = event.artistName,
                // The stats row carries no genre — it was built to answer "how long did you listen", not "to
                // what kind of thing". So the genre half of the profile comes from the likes below, and the
                // artist half, which is the stronger signal anyway, comes from here.
                genre = null,
                atMs = event.timestamp,
                listenedMs = event.listenDurationMs,
                durationMs = event.trackDurationMs,
            )
        }
        val likes = runCatching { LikeRepository.likedTracks.value }.getOrDefault(emptyList())
        return MixProfile.build(plays, likes, now)
    }
}

/**
 * One thing to expand into candidates, and how much the profile believes in it.
 *
 * Three kinds because SoundCloud has three different endpoints that know about similarity, and they know
 * different things: a track knows what is played next to it, an artist knows their own neighbourhood, and a
 * genre knows what is good in it right now. Using only one of the three is how a mix ends up either entirely
 * familiar or entirely random.
 */
private sealed interface Seed {
    val affinity: Float

    data class OfTrack(val trackId: Long, override val affinity: Float) : Seed
    data class OfArtist(val artistId: Long, val name: String, override val affinity: Float) : Seed
    data class OfGenre(val genre: String, override val affinity: Float) : Seed
}

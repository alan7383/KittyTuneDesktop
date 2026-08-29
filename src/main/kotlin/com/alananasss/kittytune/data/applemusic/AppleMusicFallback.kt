package com.alananasss.kittytune.data.applemusic

import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns an Apple Music result into something that will actually play (issue #33).
 *
 * ## Why a result you cannot play is not shown as one
 *
 * "Many artists don't upload their music to soundcloud, youtube, spotify, I think it's worth adding Apple
 * Music and Yandex Music."
 *
 * What he wants from those two is coverage: the song exists, so find it. What he does not want — and what
 * a metadata-only source gives you by default — is a row that looks like every other row and does nothing
 * when clicked. So an Apple result carries no stream and never reaches the queue directly. Pressing it
 * asks this: *the catalogue says this song is called X by Y and runs for Z; is it anywhere we can play?*
 *
 * The matching is [LyricsMatcher]'s, unchanged, because it is the same problem it already solves — a title
 * padded with `(Official Video)` and an artist field that is really an uploader's account name — and it has
 * been tuned against a real user's library for several rounds. Reusing it means one place gets better
 * rather than two places drifting.
 */
object AppleMusicFallback {

    private val api by lazy { RetrofitClient.create() }

    /**
     * The best playable stand-in for [song], or null when nothing found is close enough to be it.
     *
     * Null rather than a loose guess on purpose: playing a different song than the one that was pressed is
     * worse than saying nothing happened. [LyricsMatcher.CONFIDENT_MATCH] is the same bar the lyrics use
     * for believing a sheet belongs to a track, and it means the same thing here.
     */
    suspend fun resolve(song: AppleSong): Track? = withContext(Dispatchers.IO) {
        val target = LyricsMatcher.Target(
            title = song.title,
            artist = song.artist,
            durationMs = song.durationMs,
        )

        // Title and artist together first, because that is what identifies a song when the upload is a
        // proper one; the title alone second, because for a re-upload the artist field is an account name
        // and including it only pushes the right result down.
        val queries = listOf("${song.artist} ${song.title}", song.title)

        var best: Pair<Track, Float>? = null
        for (query in queries) {
            val results = runCatching { api.searchTracks(query, limit = 20).collection }
                .getOrDefault(emptyList())

            for (candidate in results) {
                val score = LyricsMatcher.score(
                    candidateTitle = candidate.title,
                    candidateArtist = candidate.user?.username,
                    candidateDurationSec = (candidate.durationMs ?: 0L) / 1000.0,
                    target = target,
                )
                if (score > (best?.second ?: 0f)) best = candidate to score
            }
            // A confident match on the first query makes the looser one a wasted request.
            if ((best?.second ?: 0f) >= LyricsMatcher.CONFIDENT_MATCH) break
        }

        best?.takeIf { it.second >= LyricsMatcher.CONFIDENT_MATCH }?.first
    }
}

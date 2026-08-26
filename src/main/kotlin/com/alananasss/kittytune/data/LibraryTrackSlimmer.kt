package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User

/**
 * Shrinks liked tracks before they are kept for the whole session (issue #33).
 *
 * The likes library is hydrated by walking every page of `track_likes` and holding the result in
 * memory for as long as the app runs. For a library of tens of thousands of tracks that is the
 * largest single thing the app retains, and it was retaining the full API payload — descriptions,
 * tag lists, waveform URLs and a complete set of signed stream URLs per track — none of which a
 * library list draws.
 *
 * Two savings, both behaviour-preserving:
 *
 * 1. **Fields nothing reads here are dropped.** Whoever needs them fetches the track again: the
 *    info panel already re-requests it by id, and [StreamResolver] already re-requests a track that
 *    arrives without transcodings. The stream URLs in particular were dead weight — they are signed
 *    for minutes and these are loaded at launch, so they had almost always expired before anyone
 *    could play from them.
 * 2. **Artists are shared rather than repeated.** Gson builds a separate [User] for every track it
 *    parses, so a library with a hundred tracks by one artist held a hundred copies of that artist,
 *    each with their own description and avatar strings. One instance per id says exactly the same
 *    thing.
 *
 * Not a general-purpose transform: it is only correct for tracks that are being kept as a *list*.
 * Never use it on the track being played or shown in detail.
 */
class LibraryTrackSlimmer {

    /** One [User] per id, so every track by the same artist points at the same object. */
    private val users = HashMap<Long, User>()

    /** Also interned: a handful of distinct values shared across a whole library. */
    private val genres = HashMap<String, String>()

    fun slim(track: Track): Track = track.copy(
        user = intern(track.user),
        genre = track.genre?.let { genres.getOrPut(it) { it } },
        // Re-requested by whoever needs them.
        description = null,
        tagList = null,
        caption = null,
        waveformUrl = null,
        // Signed for minutes, loaded at launch: expired long before anyone plays from this list.
        media = null,
        // Spotify-only, and these come from SoundCloud.
        artists = null,
    )

    fun slimAll(tracks: List<Track>): List<Track> = tracks.map { slim(it) }

    /**
     * @return the shared instance for this artist. The first one seen wins, and later duplicates are
     *   discarded — they are the same artist from the same endpoint, so any of them will do.
     */
    private fun intern(user: User?): User? {
        val id = user?.id ?: return user
        return users.getOrPut(id) { user }
    }

    /** How many distinct artists were seen. Only for logging what a hydration actually cost. */
    val internedArtistCount: Int get() = users.size
}

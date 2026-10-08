package com.alananasss.kittytune.data.artist

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.data.spotify.SpotifyArtist
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * An artist's own profile on a streaming service, found by name: how many people listen to them each month, and
 * the pictures and facts SoundCloud often lacks (issue #66). SoundCloud counts followers of an account, which says
 * little about an artist whose music is mostly uploaded by others; Spotify's monthly listeners is the number
 * listeners know from Spotify and Yandex Music.
 *
 * Only an exact match of the name counts, verified accounts first, so a homonym's numbers are never shown. Answers,
 * misses included, are remembered for the session.
 */
object ArtistReach {

    data class Profile(
        val spotifyId: String,
        val name: String,
        val monthlyListeners: Long?,
        val followers: Long?,
        val avatarUrl: String?,
        val headerImageUrl: String?,
        val biography: String?,
        val isVerified: Boolean,
        val details: SpotifyArtist,
    )

    private val cache = BoundedCache<String, Lookup>(256)
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Profile?>>()
    private val requests = Semaphore(MAX_PARALLEL)
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private data class Lookup(val profile: Profile?)

    /** The profile for [artistName], or null when no streaming artist has exactly that name. */
    suspend fun profileOf(artistName: String?): Profile? {
        val name = artistName?.trim().orEmpty()
        if (name.isEmpty()) return null
        val key = keyOf(name)
        cache[key]?.let { return it.profile }
        // One lookup per name however many rows ask for it at once, and a few names at a time: a list of search
        // results asks for twenty at once, and in turn the last waited for all the others.
        val lookup = inFlight.computeIfAbsent(key) {
            scope.async {
                try {
                    val found = requests.withPermit { runCatching { lookUp(name) }.getOrNull() }
                    cache[key] = Lookup(found)
                    found
                } finally {
                    inFlight.remove(key)
                }
            }
        }
        return lookup.await()
    }

    private suspend fun lookUp(name: String): Profile? {
        val key = keyOf(name)
        val candidates = SpotifyRepository.search(name, limit = 8).artists.filter { keyOf(it.name) == key }
        val best = candidates.sortedByDescending { it.verified }.firstOrNull() ?: return null
        val details = SpotifyRepository.getArtist(best.id) ?: return null
        return Profile(
            spotifyId = best.id,
            name = details.name,
            monthlyListeners = details.monthlyListeners?.takeIf { it > 0 },
            followers = details.followers?.takeIf { it > 0 },
            avatarUrl = details.avatarUrl ?: best.avatarUrl,
            headerImageUrl = details.headerImageUrl,
            biography = details.biography,
            isVerified = details.verified || best.verified,
            details = details,
        )
    }

    /**
     * A banner for [artistName]: their own header on the streaming service, or for a duo named for both ("A & B")
     * that of the first member who has one, since a duo has no profile under the joint name.
     */
    suspend fun bannerOf(artistName: String?): String? {
        val name = artistName?.trim().orEmpty()
        if (name.isEmpty()) return null
        profileOf(name)?.headerImageUrl?.let { return it }
        val members = com.alananasss.kittytune.data.lyrics.GeniusVoices.splitNames(name.replace('/', '&')).filter { it.isNotBlank() }
        if (members.size < 2) return null
        return members.firstNotNullOfOrNull { profileOf(it)?.headerImageUrl }
    }

    private fun keyOf(name: String): String = LyricsMatcher.normalize(name).replace(" ", "")

    private const val MAX_PARALLEL = 4
}

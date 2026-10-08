package com.alananasss.kittytune.data.artist

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.data.spotify.SpotifyArtist
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val lock = Mutex()

    private data class Lookup(val profile: Profile?)

    /** The profile for [artistName], or null when no streaming artist has exactly that name. */
    suspend fun profileOf(artistName: String?): Profile? {
        val name = artistName?.trim().orEmpty()
        if (name.isEmpty()) return null
        val key = keyOf(name)
        cache[key]?.let { return it.profile }
        // One lookup per name at a time: a list of tracks by one artist asks for the same name many times at once.
        return lock.withLock {
            cache[key]?.let { return@withLock it.profile }
            val found = runCatching { lookUp(name) }.getOrNull()
            cache[key] = Lookup(found)
            found
        }
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

    private fun keyOf(name: String): String = LyricsMatcher.normalize(name).replace(" ", "")
}

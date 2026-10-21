package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.data.spotify.SpotifyRepository
import java.net.URLDecoder
import kotlin.math.abs

/**
 * Identifier rules for lists that do not live on SoundCloud.
 *
 * The desktop app already stores Spotify/Deezer/Tidal/Qobuz lists by their
 * provider route, and radios are snapshots saved under the route they were
 * started from. Keeping those rules in one place avoids the library ending up
 * with a different numeric id than the detail screen that saved it.
 */
object ExternalPlaylists {

    fun isRadioPermalink(permalinkUrl: String?): Boolean {
        val value = permalinkUrl?.trim().orEmpty()
        return value.startsWith("yt_radio:", ignoreCase = true) ||
            value.startsWith("spotify_radio:", ignoreCase = true) ||
            value.startsWith("station_spotify:", ignoreCase = true)
    }

    fun isExternalPermalink(permalinkUrl: String?): Boolean {
        val value = permalinkUrl?.trim()?.lowercase().orEmpty()
        if (value.isEmpty()) return false
        if (isRadioPermalink(value)) return true
        return value.startsWith("spotify:") ||
            value.contains("spotify.com") ||
            value.contains("youtube.com") ||
            value.contains("youtu.be") ||
            value.contains("deezer.com") ||
            value.contains("tidal.com") ||
            value.contains("qobuz.com")
    }

    /**
     * The numeric library id for a radio route.
     *
     * YouTube seeds are URLs (possibly percent-encoded once by the player), so
     * decode them before hashing. Spotify seeds use the same stable-id formula
     * as [com.alananasss.kittytune.data.spotify.SpotifyTrack.toTrack] and
     * [com.alananasss.kittytune.data.spotify.SpotifyPlaylist.toPlaylist], which
     * makes a radio started from home match the row saved from its detail page.
     */
    fun radioStableId(permalinkUrl: String?): Long? {
        if (!isRadioPermalink(permalinkUrl)) return null
        val route = permalinkUrl!!.trim()

        if (route.startsWith("yt_radio:", ignoreCase = true)) {
            val rawUrl = route.substringAfter(':')
            val url = runCatching { URLDecoder.decode(rawUrl, "UTF-8") }.getOrDefault(rawUrl)
            return url.takeIf { it.isNotBlank() }?.let { abs(it.hashCode().toLong()) }
        }

        val seed = SpotifyRepository.extractId(route)
        return seed.takeIf { it.isNotBlank() }?.let { spotifyStableId(it) }
    }

    /** Numeric id to use when saving a list, based on what identifies it remotely. */
    fun libraryIdFor(permalinkUrl: String?, fallbackId: Long): Long =
        radioStableId(permalinkUrl) ?: fallbackId

    /** Same formula as SpotifyModels, shared so both sides cannot drift apart. */
    fun spotifyStableId(id: String): Long =
        abs(id.hashCode().toLong() shl 16 or (id.reversed().hashCode().toLong() and 0xFFFFL))
}

package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.alananasss.kittytune.data.PlaylistCoverResolver
import com.alananasss.kittytune.domain.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The cover to draw for [playlist], or null when there is none to draw yet.
 *
 * Returns the playlist's own artwork when it has one, otherwise asks
 * [PlaylistCoverResolver] for the first track's cover and returns it as soon as it
 * lands. Never returns a placeholder — callers decide what an empty cover looks like,
 * which is how a coverless playlist stops borrowing a random stock photo.
 */
@Composable
fun rememberPlaylistCover(playlist: Playlist?): String? {
    val id = playlist?.id
    val own = playlist?.let { it.usableArtwork ?: stationArtwork(it) }

    val flow: StateFlow<String?> = remember(id, own) {
        if (playlist != null && own == null && id != null) PlaylistCoverResolver.stateFor(id)
        else MutableStateFlow(null)
    }
    val resolved by flow.collectAsState()

    LaunchedEffect(id, own) {
        if (playlist != null && own == null) PlaylistCoverResolver.requestResolve(playlist)
    }

    return own ?: resolved
}

/**
 * Stations are the one case where a person's picture is the right cover — an artist
 * station is the artist — so they keep the artwork the usable-cover rule rejects
 * everywhere else.
 */
private fun stationArtwork(playlist: Playlist): String? {
    if (!playlist.isArtistStation && !playlist.isTrackStation) return null
    return (playlist.artworkUrl ?: playlist.calculatedArtworkUrl)
        ?.takeIf { it.isNotBlank() }
        ?.replace("large", "t500x500")
}

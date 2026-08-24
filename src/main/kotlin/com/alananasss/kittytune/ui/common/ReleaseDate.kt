package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.alananasss.kittytune.data.SpotifyReleaseDateResolver
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The release date to show for [track], resolving it if Spotify left it out.
 *
 * Returns the track's own date when it has one. Otherwise, for Spotify catalog tracks, asks
 * [SpotifyReleaseDateResolver] and returns the answer when it lands. Calling this from a row
 * is what keeps the work proportional to the screen: a lazy list only composes visible rows,
 * so only those rows ever ask.
 */
@Composable
fun rememberReleaseDate(track: Track): String? {
    val own = track.releaseDate?.takeIf { it.isNotBlank() }
    val resolvable = own == null && track.source == "spotify"

    val flow: StateFlow<String?> = remember(track.id, own) {
        if (resolvable) SpotifyReleaseDateResolver.stateFor(track.id) else MutableStateFlow(null)
    }
    val resolved by flow.collectAsState()

    LaunchedEffect(track.id, own) {
        if (resolvable) SpotifyReleaseDateResolver.requestResolve(track)
    }

    return own ?: resolved
}

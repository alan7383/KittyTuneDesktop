package com.alananasss.kittytune.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User

/**
 * Reactive content-filtering helpers (issue #41, ported from Android).
 *
 * Collects the block-list flows so any list using these recomposes when a
 * block lands, and returns the input with blocked tracks/artists removed.
 * [BlockManager.isBlocked] covers both track blocks and artist blocks.
 */
@Composable
fun rememberFilteredTracks(tracks: List<Track>): List<Track> {
    val blockedTracks by BlockManager.blockedTrackIdsFlow.collectAsState()
    val blockedArtists by BlockManager.blockedArtistIdsFlow.collectAsState()
    val snapshot = tracks.toList()
    return remember(snapshot, blockedTracks, blockedArtists) {
        if (blockedTracks.isEmpty() && blockedArtists.isEmpty()) {
            snapshot
        } else {
            BlockManager.filterBlocked(snapshot)
        }
    }
}

/** Non-composable variant for ViewModels / repositories (insertion-time filtering). */
fun filterBlockedTracks(tracks: List<Track>): List<Track> =
    BlockManager.filterBlocked(tracks)

/**
 * Same as [rememberFilteredTracks] for artist rows/circles: artists the user
 * blocked are hidden (Android hides ArtistCircle identically).
 */
@Composable
fun rememberFilteredArtists(artists: List<User>): List<User> {
    val blockedArtists by BlockManager.blockedArtistIdsFlow.collectAsState()
    val blockedTracks by BlockManager.blockedTrackIdsFlow.collectAsState()
    val snapshot = artists.toList()
    return remember(snapshot, blockedArtists, blockedTracks) {
        if (blockedArtists.isEmpty()) {
            snapshot
        } else {
            snapshot.filter { !BlockManager.isArtistBlocked(it.id) }
        }
    }
}

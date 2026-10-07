package com.alananasss.kittytune.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Strings
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.cover.AnimatedArtwork
import java.text.NumberFormat

/** Spotify's own green, for the catalog badge and the streams count. */
internal val SpotifyGreen = Color(0xFF1DB954)

/**
 * The top of the info tab: the cover, the title and the artist, then the track's numbers as a row of
 * tonal pills.
 *
 * In a short panel the cover shrinks to a thumbnail beside the title, so the half below it (comments or
 * lyrics) still gets most of the height.
 */
@Composable
internal fun TrackInfoHeader(
    vm: PlayerViewModel,
    track: Track,
    isSpotifyTrack: Boolean,
    isCompact: Boolean,
    isUltraCompact: Boolean,
    onCommentsClick: () -> Unit = {},
) {
    val isCurrentTrack = track.id == vm.uiCurrentTrack?.id ||
        (track.title == vm.uiCurrentTrack?.title && track.user?.username == vm.uiCurrentTrack?.user?.username)
    val animatedCoverUrl = if (isCurrentTrack) vm.currentAnimatedCoverUrl else null

    Column(verticalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp)) {
        if (isCompact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val coverShape = RoundedCornerShape(10.dp)
                AnimatedArtwork(
                    artworkUrl = track.fullResArtwork,
                    animatedCoverUrl = animatedCoverUrl,
                    isPlaying = vm.uiIsPlaying,
                    contentDescription = null,
                    modifier = Modifier
                        .size(if (isUltraCompact) 64.dp else 96.dp)
                        .clip(coverShape)
                        .viewableCover(track.fullResArtwork)
                )
                TitleAndArtist(vm, track, isSpotifyTrack, isLarge = !isUltraCompact, modifier = Modifier.weight(1f))
            }
        } else {
            val coverShape = RoundedCornerShape(16.dp)
            AnimatedArtwork(
                artworkUrl = track.fullResArtwork,
                animatedCoverUrl = animatedCoverUrl,
                isPlaying = vm.uiIsPlaying,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(10.dp, coverShape, clip = false)
                    .clip(coverShape)
                    .viewableCover(track.fullResArtwork)
            )
            TitleAndArtist(vm, track, isSpotifyTrack, isLarge = true)
        }

        if (isSpotifyTrack) SpotifyStats(track) else SoundCloudStats(vm, track, onCommentsClick)
    }
}

@Composable
private fun TitleAndArtist(
    vm: PlayerViewModel,
    track: Track,
    isSpotifyTrack: Boolean,
    isLarge: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = track.title ?: "",
            style = if (isLarge) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // navigateToTrackArtist routes both sources, and opens the picker when several artists are credited.
            ArtistLinkText(
                track = track,
                onArtistClick = { vm.navigateToTrackArtist(it) },
                text = track.user?.username ?: "",
                style = if (isLarge) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                hoverColor = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (track.user?.verified == true) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.Verified, null,
                    tint = if (isSpotifyTrack) SpotifyGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Plays, likes, reposts and comments. Likes and reposts open
 * the matching list on that page; comments switches to the comments
 * half and scrolls it into view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundCloudStats(vm: PlayerViewModel, track: Track, onCommentsClick: () -> Unit) {
    if (track.id <= 0) return
    val hiddenStats = rememberHiddenPanelTrackStats()
    val showPlays = PlayerPreferences.PANEL_STAT_PLAYS !in hiddenStats
    val showLikes = PlayerPreferences.PANEL_STAT_LIKES !in hiddenStats
    val showReposts = PlayerPreferences.PANEL_STAT_REPOSTS !in hiddenStats
    val showComments = PlayerPreferences.PANEL_STAT_COMMENTS !in hiddenStats
    val showInfo = PlayerPreferences.PANEL_STAT_INFO !in hiddenStats

    if (!showPlays && !showLikes && !showReposts && !showComments && !showInfo) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showPlays) {
            StatPill(Icons.Rounded.PlayArrow, compactCount(track.playbackCount.toLong()))
        }
        if (showLikes) {
            StatPill(Icons.Rounded.Favorite, compactCount(track.likesCount.toLong())) {
                vm.navigateToTrackDetails(track.id, 0)
            }
        }
        if (showReposts) {
            StatPill(Icons.Rounded.Repeat, compactCount(track.repostsCount.toLong())) {
                vm.navigateToTrackDetails(track.id, 1)
            }
        }
        if (showComments) {
            StatPill(Icons.Rounded.Comment, compactCount(track.commentCount.toLong())) {
                onCommentsClick()
            }
        }
        if (showInfo) {
            FilledTonalIconButton(
                onClick = { vm.navigateToTrackDetails(track.id, 0) },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = str("detail_track_title"),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpotifyStats(track: Track) {
    val hiddenStats = rememberHiddenPanelTrackStats()
    val showPlays = PlayerPreferences.PANEL_STAT_PLAYS !in hiddenStats
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatPill(label = str("music_provider_spotify"), tint = SpotifyGreen)
        val streams = track.playCount ?: track.playbackCount.takeIf { it > 0 }?.toLong()
        if (showPlays && streams != null && streams > 0) {
            StatPill(Icons.Rounded.PlayArrow, compactCount(streams) + " " + str("spotify_streams_formatted"))
        }
        if (track.publisherMetadata?.explicit == true) StatPill(label = "E")
    }
}

@Composable
private fun rememberHiddenPanelTrackStats(): Set<String> {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) {
        PlayerPreferences().getHiddenPanelTrackStats()
    }
}

/** One number (or label) in a pill; clickable only when [onClick] is given. */
@Composable
private fun StatPill(
    icon: ImageVector? = null,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clip(CircleShape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) Icon(icon, null, modifier = Modifier.size(15.dp), tint = tint)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (icon == null) tint else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

private fun compactCount(count: Long): String =
    NumberFormat.getCompactNumberInstance(Strings.locale(), NumberFormat.Style.SHORT).format(count)

package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.SocialProofRepository
import com.alananasss.kittytune.domain.Track
import coil3.compose.AsyncImage
import com.alananasss.kittytune.domain.User

/**
 * The people you follow who liked this track, as overlapping thumbnails.
 *
 * Same treatment as the Android app so a track row reads identically on both: at most two faces,
 * 16 dp, overlapping by 5 dp with a ring in the surface colour to separate them, and a coloured
 * initial when someone has no avatar.
 */
@Composable
fun MiniSocialProofAvatars(
    likers: List<User>,
    modifier: Modifier = Modifier,
) {
    if (likers.isEmpty()) return
    val displayLikers = remember(likers) { likers.take(2) }
    val borderColor = MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-5).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayLikers.forEachIndexed { index, user ->
            val avatarUrl = user.avatarUrl?.replace("large", "t500x500")

            Box(
                modifier = Modifier
                    // First liker on top, so the stack reads left to right.
                    .zIndex((displayLikers.size - index).toFloat())
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(FALLBACK_AVATAR_COLOR)
                    .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = user.username,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = user.username?.firstOrNull()?.uppercase() ?: "?",
                        style = TextStyle(
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                }
            }
        }
    }
}

/** Deliberately not a theme colour: it stands in for a photo, not for a piece of the palette. */
private val FALLBACK_AVATAR_COLOR = Color(0xFF00897B)

/**
 * The markers that tell you your own relationship to a track, as they appear on the second line of
 * a row: a small heart when it is already in your likes, then the faces of the people you follow
 * who liked it.
 *
 * One composable for every row in the app rather than a copy per screen — the Android app ended up
 * with three copies of this block and they had already drifted apart (issue #33). Neither marker
 * costs a request of its own: the likes are held in memory and the likers are batched fifty at a
 * time by [SocialProofRepository].
 *
 * @param showLikeIndicator false for the liked-tracks list, where every row is liked by definition
 *   and a heart on each one says nothing.
 */
@Composable
fun TrackRowSocialMarkers(track: Track, showLikeIndicator: Boolean = true) {
    val likedTracks by LikeRepository.likedTracks.collectAsState()
    val isTrackLiked = remember(track.id, likedTracks) { LikeRepository.isTrackLiked(track.id) }

    val socialLikersMap by SocialProofRepository.socialLikersMap.collectAsState()
    val socialLikers = socialLikersMap[track.id]

    LaunchedEffect(track.id, track.source) {
        // A YouTube id is not a SoundCloud track, so there is nobody to ask about.
        if (track.id > 0 && track.source != "youtube") {
            SocialProofRepository.requestSocialProof(track.id)
        }
    }

    if (showLikeIndicator && isTrackLiked) {
        SubtitleSeparator()
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = str("track_already_liked"),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
    }
    if (!socialLikers.isNullOrEmpty()) {
        SubtitleSeparator()
        MiniSocialProofAvatars(likers = socialLikers)
    }
}

/** The "·" that divides the markers on a row's second line, with its spacing. */
@Composable
private fun SubtitleSeparator() {
    Spacer(Modifier.width(4.dp))
    Text(
        text = "·",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(4.dp))
}

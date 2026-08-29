package com.alananasss.kittytune.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.player.lyrics.LyricsScreen
import com.alananasss.kittytune.utils.makeTimeString

/**
 * The player, at the size of the whole centre panel (issue #33).
 *
 * ## Why this wraps the lyrics screen instead of replacing it
 *
 * "I also think that it is necessary to change the action of the button at the bottom left, with the
 * cover and the track name — now it opens only the right panel, I think you can do this when you click
 * on it, the player opens in full. […] the cover appears on the left, text appears on the right, you can
 * press and turn off the text, all buttons such as rewind the next track, last track, volume, shuffle,
 * etc. it's under the cover."
 *
 * The right-hand two thirds of that description is the lyrics screen, which already exists, already
 * follows the song, already handles word-by-word, manual search, the offset controls and every setting
 * anybody has asked for. Building a second one alongside it is how the sidebar ended up with two
 * layouts that disagreed for three rounds of this issue. So there is one lyrics view, and this puts a
 * cover and the transport beside it.
 *
 * ## What the animation is doing
 *
 * Turning the text off is the interesting case. The cover does not jump to the middle: the lyrics half
 * collapses horizontally and the cover grows into the space as it goes, both on the same spring the
 * panels use, so the two halves trade width rather than one being deleted and the other re-laid-out.
 * The cover is sized from the space it actually has ([BoxWithConstraints]) rather than from a flag, for
 * the same reason the sidebar reads its progress from its real width: a size animated to its own
 * schedule finishes at a different moment from the layout it belongs to, and the gap between the two is
 * what reads as cheap.
 */
@Composable
fun FullPlayerScreen(viewModel: PlayerViewModel, onClose: () -> Unit) {
    val track = viewModel.currentTrack
    var showText by remember { mutableStateOf(true) }

    // Nothing to put a cover beside, so this is the lyrics screen and only that.
    if (track == null) {
        LyricsScreen(viewModel = viewModel, onClose = onClose)
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        // Weighted rather than fixed: at 1f against the lyrics' 1.35f the cover half is the narrower of
        // the two while the words are up, which is the balance in the screenshots he sent — and when the
        // words go, this is the only child left and takes everything.
        Box(Modifier.weight(1f).fillMaxHeight()) {
            CoverAndTransport(
                viewModel = viewModel,
                showText = showText,
                onToggleText = { showText = !showText },
                onClose = onClose,
            )
        }

        AnimatedVisibility(
            visible = showText,
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                expandHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    expandFrom = Alignment.Start,
                ),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                shrinkHorizontally(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Start,
                ),
            modifier = Modifier.weight(LYRICS_SHARE),
        ) {
            // Its own close button is the one thing it must not keep here: there is a single close for the
            // whole screen, in the corner above the cover, and two would be two ways out of one place.
            LyricsScreen(viewModel = viewModel, onClose = onClose, showCloseButton = false)
        }
    }
}

/** How much more room the words get than the cover, while they are up. */
private const val LYRICS_SHARE = 1.35f

/**
 * The cover, the credit, and every control that used to live only in the bar at the bottom.
 *
 * Sized against the room it has rather than against a target: the square takes the smaller of the width
 * it is given and two thirds of the height, so it grows when the words leave and shrinks when they come
 * back without anything here knowing that either happened.
 */
@Composable
private fun CoverAndTransport(
    viewModel: PlayerViewModel,
    showText: Boolean,
    onToggleText: () -> Unit,
    onClose: () -> Unit,
) {
    val track = viewModel.currentTrack ?: return

    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp)) {
        val coverSide = min(maxWidth, maxHeight * COVER_SHARE_OF_HEIGHT)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(coverSide)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
            )

            Spacer(Modifier.height(20.dp))

            Column(Modifier.width(coverSide)) {
                Text(
                    text = track.title ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                ArtistLinkText(
                    track = track,
                    onArtistClick = { viewModel.navigateToTrackArtist(it) },
                    text = track.user?.username ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    hoverColor = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(16.dp))
            SeekRow(viewModel, Modifier.width(coverSide))
            Spacer(Modifier.height(8.dp))
            TransportRow(viewModel, showText, onToggleText, Modifier.width(coverSide))
        }

        // The single way out of the whole screen, in the corner rather than in a bar of its own.
        IconButton(
            onClick = onClose,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Icon(Icons.Rounded.Close, str("btn_close"), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** At most two thirds of the height, so the credit and the controls under it always have their room. */
private const val COVER_SHARE_OF_HEIGHT = 0.62f

/**
 * The progress bar and the two times, scrubbable.
 *
 * Its own scrub state, rather than writing the position straight to the player on every pixel: dragging
 * a slider that is also being driven by the playhead four times a second fights itself, and the thumb
 * jumps back under the finger between reports.
 */
@Composable
private fun SeekRow(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }
    val duration = viewModel.duration.coerceAtLeast(1L)
    val position = if (scrubbing) scrubPosition.toLong() else viewModel.currentPosition

    Column(modifier) {
        Slider(
            value = position.coerceIn(0L, duration).toFloat(),
            valueRange = 0f..duration.toFloat(),
            onValueChange = {
                scrubbing = true
                scrubPosition = it
            },
            onValueChangeFinished = {
                viewModel.seekTo(scrubPosition.toLong())
                scrubbing = false
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = makeTimeString(position),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = makeTimeString(viewModel.duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shuffle, previous, play, next, repeat — and the three that only make sense here: the heart, the volume
 * and the switch that takes the words away.
 *
 * Deliberately the same actions and the same icons as the bar at the bottom, because it is the same
 * player. What is different is only that they are large and under the cover, which is what was asked for.
 */
@Composable
private fun TransportRow(
    viewModel: PlayerViewModel,
    showText: Boolean,
    onToggleText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = Icons.Filled.Shuffle,
                label = str("player_shuffle"),
                active = viewModel.shuffleEnabled,
                onClick = { viewModel.toggleShuffle() },
            )
            TransportButton(
                icon = Icons.Filled.SkipPrevious,
                label = str("player_previous"),
                size = 32.dp,
                onClick = { viewModel.smartPrevious() },
            )
            // The one filled button in the row, because it is the one anybody aims at without looking.
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { viewModel.togglePlayPause() }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        if (viewModel.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = str("player_play_pause"),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            TransportButton(
                icon = Icons.Filled.SkipNext,
                label = str("player_next"),
                size = 32.dp,
                onClick = { viewModel.playNext() },
            )
            TransportButton(
                icon = if (viewModel.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne
                else Icons.Filled.Repeat,
                label = str("player_repeat"),
                active = viewModel.repeatMode != RepeatMode.NONE,
                onClick = { viewModel.toggleRepeatMode() },
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = if (viewModel.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = str("player_like"),
                active = viewModel.isLiked,
                onClick = { viewModel.toggleLike() },
            )
            TransportButton(
                icon = Icons.Rounded.Lyrics,
                label = str("player_lyrics"),
                active = showText,
                onClick = onToggleText,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Slider(
                value = viewModel.volume,
                valueRange = 0f..1f,
                onValueChange = { viewModel.updateVolume(it) },
                onValueChangeFinished = { viewModel.persistVolume() },
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
    }
}

/** One control, tinted when it is on, and always carrying its own name for the hover. */
@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: () -> Unit,
) {
    val tint by animateFloatAsState(if (active) 1f else 0f, label = "transportActive")
    Tip(label) {
        IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(size).graphicsLayer { alpha = 1f },
                tint = androidx.compose.ui.graphics.lerp(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.primary,
                    tint,
                ),
            )
        }
    }
}

package com.alananasss.kittytune.ui.main

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.alananasss.kittytune.ui.modifiers.squish
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import coil3.compose.AsyncImage
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.RepeatMode
import com.alananasss.kittytune.utils.makeTimeString
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.input.pointer.PointerButton

/**
 * Bottom full-width playback bar: track info left, transport + progress center,
 * lyrics/effects/queue/volume right — mirrors the reference player bar.
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerBar(
    playerViewModel: PlayerViewModel,
    onToggleNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenTrackInfo: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm = playerViewModel
    val track = vm.currentTrack

    Surface(
        modifier = modifier.height(88.dp),
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // --- left: artwork + title/artist + like -----------------------------------
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (track != null) {
                    // Artwork + title/artist open the now-playing panel on the track info tab (left click), or options popup (right click)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .onClick(
                                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                                onClick = { vm.showTrackOptions(track, fromPlayer = true) }
                            )
                            .clickable { onOpenTrackInfo() }
                            .padding(4.dp),
                    ) {
                        AsyncImage(
                            model = track.fullResArtwork,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.widthIn(max = 220.dp)) {
                            Text(
                                text = track.title ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.user?.username ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (track.user?.verified == true) {
                                    Spacer(Modifier.width(3.dp))
                                    Icon(
                                        Icons.Rounded.Verified,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { vm.toggleLike() }) {
                        Icon(
                            if (vm.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (vm.isLiked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // --- center: transport + progress ------------------------------------------
            Column(
                modifier = Modifier.widthIn(min = 340.dp, max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = { vm.toggleShuffle() }
                    ) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (vm.shuffleEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    val backInteractionSource = remember { MutableInteractionSource() }
                    val nextInteractionSource = remember { MutableInteractionSource() }
                    val playPauseInteractionSource = remember { MutableInteractionSource() }

                    val isPlayPausePressed by playPauseInteractionSource.collectIsPressedAsState()
                    val isBackPressed by backInteractionSource.collectIsPressedAsState()
                    val isNextPressed by nextInteractionSource.collectIsPressedAsState()
                    val isPlayPauseHovered by playPauseInteractionSource.collectIsHoveredAsState()
                    val isBackHovered by backInteractionSource.collectIsHoveredAsState()
                    val isNextHovered by nextInteractionSource.collectIsHoveredAsState()

                    val playPauseWeight by animateFloatAsState(
                        targetValue = when {
                            isPlayPausePressed -> 2.0f
                            isBackPressed || isNextPressed -> 1.1f
                            isPlayPauseHovered -> 1.5f
                            else -> 1.3f
                        },
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
                        label = "playPauseWeight"
                    )
                    val backButtonWeight by animateFloatAsState(
                        targetValue = when {
                            isBackPressed -> 0.7f
                            isPlayPausePressed -> 0.3f
                            isBackHovered && !isPlayPauseHovered -> 0.55f
                            else -> 0.45f
                        },
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
                        label = "backButtonWeight"
                    )
                    val nextButtonWeight by animateFloatAsState(
                        targetValue = when {
                            isNextPressed -> 0.7f
                            isPlayPausePressed -> 0.3f
                            isNextHovered && !isPlayPauseHovered -> 0.55f
                            else -> 0.45f
                        },
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
                        label = "nextButtonWeight"
                    )

                    val sideHoverColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    val sideIdleColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    val playHoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)

                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .weight(backButtonWeight)
                            .clip(RoundedCornerShape(50))
                            .background(if (isBackHovered) sideHoverColor else sideIdleColor)
                            .hoverable(backInteractionSource)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                            .clickable(
                                interactionSource = backInteractionSource,
                                indication = ripple()
                            ) { vm.smartPrevious() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SkipPrevious, null, modifier = Modifier.size(22.dp))
                    }

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .weight(playPauseWeight)
                            .clip(RoundedCornerShape(50))
                            .background(if (isPlayPauseHovered) playHoverColor else MaterialTheme.colorScheme.primary)
                            .hoverable(playPauseInteractionSource)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                            .clickable(
                                interactionSource = playPauseInteractionSource,
                                indication = ripple()
                            ) { vm.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .weight(nextButtonWeight)
                            .clip(RoundedCornerShape(50))
                            .background(if (isNextHovered) sideHoverColor else sideIdleColor)
                            .hoverable(nextInteractionSource)
                            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                            .clickable(
                                interactionSource = nextInteractionSource,
                                indication = ripple()
                            ) { vm.playNext() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.SkipNext, null, modifier = Modifier.size(22.dp))
                    }

                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { vm.toggleRepeatMode() }) {
                        Icon(
                            if (vm.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = null,
                            tint = if (vm.repeatMode != RepeatMode.NONE) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Progress row
                var scrubbing by remember { mutableStateOf(false) }
                var scrubPosition by remember { mutableFloatStateOf(0f) }
                val position = if (scrubbing || vm.isScrubbing) scrubPosition.toLong() else vm.currentPosition
                val duration = vm.duration.coerceAtLeast(1L)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = makeTimeString(position),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = position.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = {
                            scrubbing = true
                            scrubPosition = it
                            vm.updateScrubPosition(it.toLong())
                        },
                        onValueChangeFinished = {
                            vm.seekTo(scrubPosition.toLong())
                            scrubbing = false
                        },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(
                        text = makeTimeString(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // --- right: lyrics / effects / queue / volume ------------------------------
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onOpenLyrics,
                ) {
                    Icon(
                        imageVector = if (vm.hasLyrics) Icons.Rounded.Mic else Icons.Outlined.Mic,
                        contentDescription = "Lyrics",
                        tint = if (vm.hasLyrics) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onToggleNowPlaying,

                ) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = onOpenQueue,

                ) {
                    Icon(
                        Icons.Outlined.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                val volume = vm.volume
                Icon(
                    when {
                        volume <= 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
                        volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = "Mute",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).clickable { vm.toggleMute() },
                )
                Slider(
                    value = volume,
                    onValueChange = { vm.updateVolume(it) },
                    modifier = Modifier.width(110.dp).padding(start = 4.dp),
                )
            }
        }
    }
}

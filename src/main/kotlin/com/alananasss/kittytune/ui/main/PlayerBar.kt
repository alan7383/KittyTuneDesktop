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
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import coil3.compose.AsyncImage
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.RepeatMode
import com.alananasss.kittytune.utils.makeTimeString
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.SliderState
import androidx.compose.material3.VerticalSlider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.delay

/**
 * What the row spends before the track: the speaker icon and the gap after it.
 *
 * Counted once. It used to include the slider's own inner padding as well, which the slider then also
 * applied inside its fixed width — so the track was short by that padding twice over (issue #33).
 */
private val VOLUME_ICON_SIZE = 20.dp

/** Between the speaker and the track. Enough to separate them, not enough to eat the track. */
private val VOLUME_ICON_GAP = 10.dp

private val VOLUME_ROW_OVERHEAD = VOLUME_ICON_SIZE + VOLUME_ICON_GAP

/**
 * Shortest track worth aiming at. With less room than this the bar switches to the vertical hover
 * control rather than showing a sliver.
 */
private val MIN_VOLUME_SLIDER_WIDTH = 120.dp

/** And the widest it grows to on a roomy window. */
private val MAX_VOLUME_SLIDER_WIDTH = 200.dp

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
    /**
     * The cover and the credit at the bottom left. It opened the side panel on its Info tab; it opens the
     * whole player now — "I think you can do this when you click on it, the player opens in full"
     * (issue #33). The Info tab is still one press away on the panel's own tab row.
     */
    onOpenFullPlayer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm = playerViewModel
    val track = vm.currentTrack
    val visibleButtons = rememberPlayerBarButtons()
    val showLyricsButton = rememberShowLyricsButton()

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
                            // Yields width before the like button does. A Row measures its
                            // unweighted children first, so without this the artwork and title
                            // take what they want and the heart — last in the row — is the part
                            // that gets clipped away as the UI scale goes up (issue #33).
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(8.dp))
                            .onClick(
                                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                                onClick = { vm.showTrackOptions(track, fromPlayer = true) }
                            )
                            .clickable { onOpenFullPlayer() }
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
                        Column(Modifier.weight(1f, fill = false).widthIn(max = 220.dp)) {
                            Text(
                                text = track.title ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ArtistLinkText(
                                    track = track,
                                    onArtistClick = { vm.navigateToTrackArtist(it) },
                                    text = track.user?.username ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f, fill = false)
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
                    if (PlayerPreferences.PLAYER_BAR_BUTTON_LIKE in visibleButtons) {
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
                    // Active pill background makes on/off state obvious at a glance.
                    ExpressiveToggleButton(
                        selected = vm.shuffleEnabled,
                        icon = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        onClick = { vm.toggleShuffle() },
                    )

                    Spacer(Modifier.width(6.dp))

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

                    Spacer(Modifier.width(6.dp))

                    ExpressiveToggleButton(
                        selected = vm.repeatMode != RepeatMode.NONE,
                        icon = if (vm.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne
                        else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        onClick = { vm.toggleRepeatMode() },
                    )
                }

                // Progress row
                val seekWheelSeconds = rememberSeekWheelSeconds()
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
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .seekWheel(
                                positionMs = { if (scrubbing || vm.isScrubbing) scrubPosition.toLong() else vm.currentPosition },
                                durationMs = { vm.duration },
                                stepSeconds = { seekWheelSeconds },
                                onSeek = { target ->
                                    // Straight to the player rather than through the scrub state: a
                                    // wheel notch is a decision, not a drag in progress.
                                    scrubbing = false
                                    vm.seekTo(target)
                                },
                            ),
                    )
                    Text(
                        text = makeTimeString(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val verticalVolumeSlider = rememberVerticalVolumeSlider()

            // --- right: lyrics / effects / queue / volume ------------------------------
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (showLyricsButton) {
                    IconButton(
                        shapes = IconButtonDefaults.shapes(),
                        onClick = onOpenLyrics,
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource("icons/lyrics.svg"),
                            contentDescription = "Lyrics",
                            tint = if (vm.hasLyrics) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (PlayerPreferences.PLAYER_BAR_BUTTON_PANEL in visibleButtons) {
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
                }
                // The panel this opens also has a queue tab, so hiding this button costs the queue
                // a click rather than access to it (issue #33).
                if (PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE in visibleButtons) {
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
                }

                val volume = vm.volume
                // BoxWithConstraints is the last child of the row, so `maxWidth` here is the width
                // left over for the volume control.
                //
                // The slider's width is then derived from it rather than left to `weight`. Asking
                // for a weighted, wrap-content width inside a row that is itself wrap-content let
                // the row report more than it had been given: the excess was clamped away and the
                // track came out cut off with space still visible beside it, which is why the first
                // attempt at this did not fix the report (issue #33). Computed this way the row is
                // exactly icon + padding + slider wide, so there is nothing left to clip.
                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    val roomForSlider = maxWidth - VOLUME_ROW_OVERHEAD
                    val tooNarrowForSlider = roomForSlider < MIN_VOLUME_SLIDER_WIDTH
                    if (verticalVolumeSlider || tooNarrowForSlider) {
                        VolumeHoverControl(
                            volume = volume,
                            onVolumeChange = { vm.updateVolume(it) },
                            onVolumeChangeFinished = { vm.persistVolume() },
                            onVolumeScrolled = { vm.updateVolume(it); vm.persistVolumeSoon() },
                            onToggleMute = { vm.toggleMute() },
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when {
                                    volume <= 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
                                    volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                                    else -> Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = "Mute",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(VOLUME_ICON_SIZE)
                                    .volumeWheel({ vm.volume }) { vm.updateVolume(it); vm.persistVolumeSoon() }
                                    .clickable { vm.toggleMute() },
                            )
                            Spacer(Modifier.width(VOLUME_ICON_GAP))
                            Slider(
                                value = volume,
                                onValueChange = { vm.updateVolume(it) },
                                onValueChangeFinished = { vm.persistVolume() },
                                modifier = Modifier
                                    // Exactly the room there is, between what is worth showing and
                                    // what is worth using. Still grows on a wide window, which was
                                    // the "make it wider" ask in #27, but never past its box.
                                    //
                                    // No inner padding here. It used to carry `padding(horizontal = 12.dp)`
                                    // *inside* a fixed width, so the track was the width minus 24 dp — while
                                    // [VOLUME_ROW_OVERHEAD] had already subtracted that same 24 dp from the
                                    // room available. The padding was charged twice and the track lost 24 dp
                                    // it had been budgeted, which is the truncated slider in the report. The
                                    // gap to the icon is a Spacer now, so it is paid for exactly once
                                    // (issue #33).
                                    .width(roomForSlider.coerceAtMost(MAX_VOLUME_SLIDER_WIDTH))
                                    .volumeWheel({ vm.volume }) { vm.updateVolume(it); vm.persistVolumeSoon() },
                            )
                        }
                    }
                }
            }
        }
    }
}


/**
 * Shuffle / repeat, in the same language as the transport pills next to them: 42 dp tall,
 * springy, and morphing shape rather than a static circle glued to the end of the row.
 *
 * The shape carries the state as much as the colour does — round when off, noticeably squarer
 * when on — which is the Material 3 Expressive selected-toggle treatment and reads even in a
 * monochrome palette, where a container tint alone was too subtle to tell apart.
 */
@Composable
private fun ExpressiveToggleButton(
    selected: Boolean,
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val corner by animateDpAsState(
        targetValue = when {
            pressed -> 12.dp
            selected -> 14.dp
            else -> 21.dp
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "toggleCorner",
    )
    val width by animateDpAsState(
        targetValue = when {
            pressed -> 40.dp
            hovered -> 48.dp
            else -> 42.dp
        },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "toggleWidth",
    )

    val container = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        hovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(width = width, height = 42.dp)
            .clip(RoundedCornerShape(corner))
            .background(container)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Scroll wheel over any volume control raises or lowers it, in 5% steps. Wheel deltas are
 * positive downwards, so the sign is inverted to match the direction the user pushed.
 *
 * [currentVolume] and [onVolumeChange] are read through [rememberUpdatedState] because the
 * `pointerInput` block is keyed on Unit and therefore never restarts: capturing them directly
 * froze the level at whatever it was when the control first composed, so scrolling only ever
 * moved one step either side of that stale value.
 */
@Composable
private fun Modifier.volumeWheel(
    currentVolume: () -> Float,
    onVolumeChange: (Float) -> Unit,
): Modifier {
    val volume by androidx.compose.runtime.rememberUpdatedState(currentVolume)
    val onChange by androidx.compose.runtime.rememberUpdatedState(onVolumeChange)
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta == 0f) continue
                onChange((volume() - delta * 0.05f).coerceIn(0f, 1f))
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/**
 * The wheel over the progress bar, moving the playhead (issue #33).
 *
 * "If you hover over the slider showing how long the track is, you can use the mouse wheel to rewind
 * and fast-forward the track."
 *
 * Consumed, so the wheel does not also scroll whatever the player bar happens to be sitting on. Up
 * goes forward, matching the volume control right next to it, where up is louder. The step is a
 * setting because five seconds is right for checking a lyric and useless for finding your way around
 * a two-hour set.
 */
@Composable
private fun Modifier.seekWheel(
    positionMs: () -> Long,
    durationMs: () -> Long,
    stepSeconds: () -> Float,
    onSeek: (Long) -> Unit,
): Modifier {
    val position by androidx.compose.runtime.rememberUpdatedState(positionMs)
    val duration by androidx.compose.runtime.rememberUpdatedState(durationMs)
    val step by androidx.compose.runtime.rememberUpdatedState(stepSeconds)
    val seek by androidx.compose.runtime.rememberUpdatedState(onSeek)
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val notches = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (notches == 0f) continue
                val total = duration()
                if (total <= 0L) continue
                val moved = position() - (notches * step() * 1000f).toLong()
                seek(moved.coerceIn(0L, total))
                event.changes.forEach { it.consume() }
            }
        }
    }
}

/**
 * Reactive read of which optional player-bar buttons the user keeps; recomposes on pref changes.
 */
@Composable
private fun rememberPlayerBarButtons(): Set<String> {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) { PlayerPreferences().getPlayerBarButtons() }
}

/** Reactive read of how far a wheel notch over the progress bar moves the playhead. */
@Composable
private fun rememberSeekWheelSeconds(): Float {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) { PlayerPreferences().getSeekWheelSeconds() }
}

/** Reactive read of the lyrics button's own switch, which lives in the lyrics settings. */
@Composable
private fun rememberShowLyricsButton(): Boolean {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) { PlayerPreferences().getShowLyricsButtonEnabled() }
}

/**
 * Reactive read of the "vertical volume slider" setting; recomposes when the pref changes.
 */
@Composable
private fun rememberVerticalVolumeSlider(): Boolean {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) {
        com.alananasss.kittytune.data.local.PlayerPreferences().getVerticalVolumeSlider()
    }
}

/**
 * Speaker button that reveals a vertical volume slider on hover, floating above the bar.
 *
 * Hover rather than click, because a click on the speaker already means mute and a control that
 * needed two different clicks to do two things was the confusing part. The panel stays up while
 * the pointer is over either the button or the panel itself, with a short grace period so the
 * gap between them does not dismiss it mid-reach.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalComposeUiApi::class,
)
@Composable
private fun VolumeHoverControl(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onVolumeScrolled: (Float) -> Unit,
    onToggleMute: () -> Unit,
) {
    var overButton by remember { mutableStateOf(false) }
    var overPanel by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(overButton, overPanel) {
        if (overButton || overPanel) {
            expanded = true
        } else {
            delay(250)
            expanded = false
        }
    }

    val levelIcon = when {
        volume <= 0.01f -> Icons.AutoMirrored.Filled.VolumeOff
        volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }

    Box {
        val buttonShape by animateDpAsState(
            targetValue = if (expanded) 14.dp else 20.dp,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
            label = "volumeButtonShape",
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(buttonShape))
                .background(
                    if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    else Color.Transparent
                )
                .onPointerEvent(PointerEventType.Enter) { overButton = true }
                .onPointerEvent(PointerEventType.Exit) { overButton = false }
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                .volumeWheel({ volume }, onVolumeScrolled)
                .clickable(indication = ripple(), interactionSource = remember { MutableInteractionSource() }) {
                    onToggleMute()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                levelIcon,
                contentDescription = "Volume",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }

        if (expanded) {
            Popup(
                popupPositionProvider = AboveAnchorCentered,
                properties = PopupProperties(focusable = false),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .onPointerEvent(PointerEventType.Enter) { overPanel = true }
                        .onPointerEvent(PointerEventType.Exit) { overPanel = false }
                        .volumeWheel({ volume }, onVolumeScrolled),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        val state = remember {
                            SliderState(volume, 0, { onVolumeChangeFinished() }, 0f..1f)
                        }
                        // Follow changes that did not come from this slider — the mute button,
                        // the wheel, a keyboard shortcut — instead of only seeding once.
                        LaunchedEffect(volume) {
                            if (kotlin.math.abs(state.value - volume) > 0.001f) state.value = volume
                        }
                        LaunchedEffect(state) {
                            snapshotFlow { state.value }.collect { onVolumeChange(it) }
                        }
                        VerticalSlider(
                            state = state,
                            // A volume slider fills from the bottom. The default direction puts
                            // the origin at the top, which is what made it read upside down.
                            reverseDirection = true,
                            modifier = Modifier.height(150.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Icon(
                            levelIcon,
                            contentDescription = "Mute",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                                .clickable { onToggleMute() },
                        )
                    }
                }
            }
        }
    }
}

/** Places a popup directly above its anchor, horizontally centred and clamped to the window. */
private object AboveAnchorCentered : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        return IntOffset(
            x.coerceIn(0, maxX),
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0),
        )
    }
}

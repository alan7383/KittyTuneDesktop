package com.alananasss.kittytune.ui.main

import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.player.cover.AnimatedArtwork
import com.alananasss.kittytune.ui.player.slider.PlayerSlider

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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.IconButtonShapes
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
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.outlined.HeartBroken
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Tune

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.material3.ripple
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.RepeatMode
import com.alananasss.kittytune.utils.makeTimeString
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.GraphicEq
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.core.stringResource

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.onClick
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.alananasss.kittytune.data.local.PlayerPreferences

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
    isNowPlayingOpen: Boolean = false,
    isQueueOpen: Boolean = false,
    modifier: Modifier = Modifier,
    /** Floating only: told where the pill itself is, which the content uses to keep clear of it. */
    onBarPlaced: ((androidx.compose.ui.layout.LayoutCoordinates) -> Unit)? = null,
) {
    val vm = playerViewModel
    val track = vm.currentTrack
    val visibleButtons = rememberPlayerBarButtons()
    val showLyricsButton = rememberShowLyricsButton()
    val barStyle = rememberPlayerBarStyle()
    val isFloating = barStyle == com.alananasss.kittytune.data.local.PlayerBarStyle.FLOATING
    val floatLook = rememberFloatingBarLook()
    // In the pill every hover and press is a circle, like the pill itself; the stock shape is a squircle.
    val iconShapes = if (isFloating) IconButtonShapes(androidx.compose.foundation.shape.CircleShape, androidx.compose.foundation.shape.CircleShape) else IconButtonDefaults.shapes()

    Box(modifier, contentAlignment = Alignment.Center) {
    Surface(
        modifier = when (barStyle) {
            // A pill that floats clear of the window's edges, like a dock: centred, capped in width so it
            // reads as an object rather than a strip, lifted by a soft shadow and a hairline edge.
            com.alananasss.kittytune.data.local.PlayerBarStyle.FLOATING -> Modifier
                .fillMaxWidth(floatLook.widthPercent / 100f)
                .padding(horizontal = 24.dp)
                .height(76.dp)
                .then(onBarPlaced?.let { report -> Modifier.onGloballyPositioned { report(it) } } ?: Modifier)
            else -> Modifier.fillMaxWidth().height(88.dp)
        },
        shape = when (barStyle) {
            com.alananasss.kittytune.data.local.PlayerBarStyle.DEFAULT -> PanelShape
            com.alananasss.kittytune.data.local.PlayerBarStyle.ROUNDED -> RoundedCornerShape(28.dp)
            com.alananasss.kittytune.data.local.PlayerBarStyle.FLOATING -> RoundedCornerShape(floatLook.cornerDp.dp)
        },
        // Floating, it lies over the content: a little see-through, so what scrolls beneath shows, and lifted
        // by a deep soft shadow and a light edge so it reads as an object above the page.
        color = when {
            !isFloating -> MaterialTheme.colorScheme.surfaceContainerLow
            floatLook.isTranslucent -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (isFloating) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
        } else null,
        shadowElevation = if (isFloating) 18.dp else 0.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val barWidth = maxWidth
            val isCompact = barWidth < 900.dp
            val isVeryCompact = barWidth < 740.dp
            val centerMax = when {
                barWidth >= 1100.dp -> 560.dp
                barWidth >= 850.dp -> 440.dp
                barWidth >= 700.dp -> 360.dp
                else -> 280.dp
            }
            val centerMin = when {
                barWidth >= 850.dp -> 300.dp
                barWidth >= 700.dp -> 240.dp
                else -> 180.dp
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = if (isFloating) 18.dp else 12.dp),
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
                                .clip(RoundedCornerShape(if (isFloating) 28.dp else 8.dp))
                                .onClick(
                                    matcher = PointerMatcher.mouse(PointerButton.Secondary),
                                    onClick = { vm.showTrackOptions(track, fromPlayer = true) }
                                )
                                .clickable { onOpenFullPlayer() }
                                .padding(4.dp),
                        ) {
                            AnimatedArtwork(
                                artworkUrl = track.fullResArtwork,
                                animatedCoverUrl = vm.currentAnimatedCoverUrl,
                                isPlaying = vm.isPlaying,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(if (isVeryCompact) 48.dp else if (isFloating) 52.dp else 56.dp)
                                    .clip(RoundedCornerShape(if (isFloating) 26.dp else 8.dp)),
                            )
                            Spacer(Modifier.width(if (isVeryCompact) 8.dp else 12.dp))
                            Column(Modifier.weight(1f, fill = false).widthIn(max = if (isCompact) 160.dp else 220.dp)) {
                                Text(
                                    text = track.title.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                val isAutomixing by AutomixManager.isAutomixing.collectAsState()
                                val mixBeatsLeft by AutomixManager.mixBeatsLeft.collectAsState()
                                val isCrossfading = MusicManager.isCrossfadingOut
                                // Derived, so the bar recomposes when this flips (once a track), not
                                // on every position tick.
                                val isNearingEnd by remember(vm) {
                                    androidx.compose.runtime.derivedStateOf {
                                        val remainingMs = if (vm.duration > 0) vm.duration - vm.currentPosition else Long.MAX_VALUE
                                        remainingMs in 0L..20_000L
                                    }
                                }
                                val isTransitionActive = (isAutomixing || isCrossfading || (mixBeatsLeft != null && mixBeatsLeft!! > 0)) && isNearingEnd

                                val nextTrack = if (vm.repeatMode == RepeatMode.ONE) track else vm.queue.getOrNull(vm.currentQueueIndex + 1)
                                val isDifferentTrack = vm.repeatMode == RepeatMode.ONE || (nextTrack != null && nextTrack.id != track.id)
                                val nextTitle = nextTrack?.title?.trim()?.takeIf { it.isNotEmpty() && isDifferentTrack }

                                AnimatedContent(
                                    targetState = isTransitionActive && nextTitle != null,
                                    transitionSpec = {
                                        (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 })
                                            .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 })
                                    },
                                    label = "TrackSubtitleTransition"
                                ) { showTransition ->
                                    if (showTransition && nextTitle != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.GraphicEq,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.automix_mix_into, nextTitle),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.primary
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            ArtistLinkText(
                                                track = track,
                                                onArtistClick = { vm.navigateToTrackArtist(it) },
                                                text = track.displayArtist.ifBlank { track.user?.username.orEmpty() },
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
                            }
                        }
                        if (PlayerPreferences.PLAYER_BAR_BUTTON_LIKE in visibleButtons) {
                            Spacer(Modifier.width(8.dp))
                            IconButton(shapes = iconShapes, onClick = { vm.toggleLike() }) {
                                Icon(
                                    if (vm.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = str("player_like"),
                                    tint = if (vm.isLiked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            if (vm.isYourMixActive) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(
                                    shapes = iconShapes,
                                    onClick = { vm.dislikeCurrentTrackInMix() }
                                ) {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Outlined.ThumbDown,
                                        contentDescription = str("mix_dislike"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // --- center: transport + progress ------------------------------------------
                Column(
                    modifier = Modifier.widthIn(min = centerMin, max = centerMax),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    // The floating pill is its own object with air around it, so its buttons get air between them
                    // too: at the docked bar's 6 dp they read as one strip on the pill (round 5).
                    val transportGap = if (isFloating) 12.dp else 6.dp
                    if (PlayerPreferences.PLAYER_BAR_BUTTON_SHUFFLE in visibleButtons) {
                        ExpressiveToggleButton(
                            selected = vm.shuffleEnabled,
                            icon = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            onClick = { vm.toggleShuffle() },
                        )
                        Spacer(Modifier.width(transportGap))
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

                    Spacer(Modifier.width(transportGap))

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

                    Spacer(Modifier.width(transportGap))

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

                    if (PlayerPreferences.PLAYER_BAR_BUTTON_REPEAT in visibleButtons) {
                        Spacer(Modifier.width(transportGap))
                        ExpressiveToggleButton(
                            selected = vm.repeatMode != RepeatMode.NONE,
                            icon = if (vm.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne
                            else Icons.Filled.Repeat,
                            contentDescription = "Repeat",
                            onClick = { vm.toggleRepeatMode() },
                        )
                    }
                }

                PlaybackProgressRow(vm)
            }

            val verticalVolumeSlider = rememberVerticalVolumeSlider()

            // --- right: lyrics / effects / queue / volume ------------------------------
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (showLyricsButton && PlayerPreferences.PLAYER_BAR_BUTTON_LYRICS in visibleButtons) {
                    IconButton(
                        shapes = iconShapes,
                        onClick = onOpenLyrics,
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource("icons/lyrics.svg"),
                            contentDescription = "Lyrics",
                            // Lit while the lyrics are open, like any toggle, not whenever a track has them.
                            tint = if (vm.showLyricsSheet) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (!isVeryCompact && PlayerPreferences.PLAYER_BAR_BUTTON_MINIPLAYER in visibleButtons) {
                    Tip(str("mini_player_title")) {
                        IconButton(
                            shapes = iconShapes,
                            onClick = { vm.toggleMiniPlayer() },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = str("mini_player_title"),
                                tint = if (vm.isMiniPlayerVisible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (PlayerPreferences.PLAYER_BAR_BUTTON_PANEL in visibleButtons) {
                    IconButton(
                        shapes = iconShapes,
                        onClick = onToggleNowPlaying,
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = if (isNowPlayingOpen) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                // The panel this opens also has a queue tab, so hiding this button costs the queue
                // a click rather than access to it (issue #33).
                if (!isCompact && PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE in visibleButtons) {
                    IconButton(
                        shapes = iconShapes,
                        onClick = onOpenQueue,
                    ) {
                        Icon(
                            Icons.Outlined.QueueMusic,
                            contentDescription = null,
                            tint = if (isQueueOpen) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                VolumeControl(
                    volume = vm.volume,
                    preferVertical = verticalVolumeSlider,
                    isPlaying = vm.isPlaying,
                    onVolumeChange = { vm.updateVolume(it) },
                    onVolumeChangeFinished = { vm.persistVolume() },
                    onVolumeScrolled = { vm.updateVolume(it); vm.persistVolumeSoon() },
                    onToggleMute = { vm.toggleMute() },
                )
            }
        }
    }
    }
}
}


/**
 * Elapsed time, seek bar and duration.
 *
 * Its own composable so the position, which changes several times a second, only recomposes this
 * row. Read inline, it used to recompose the whole player bar on every tick.
 */
@Composable
private fun PlaybackProgressRow(vm: PlayerViewModel) {
    val seekWheelSeconds = rememberSeekWheelSeconds()
    val sliderStyle = rememberPlayerSliderStyle()
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
        PlayerSlider(
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
            sliderStyle = sliderStyle,
            isPlaying = vm.isPlaying,
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
        // Click to switch between the track's length and the time left, which counts down with a minus, as in
        // the full player. Remembered.
        var showRemaining by remember { mutableStateOf(com.alananasss.kittytune.core.Prefs.getBoolean(KEY_BAR_REMAINING, false)) }
        Text(
            text = if (showRemaining) "-" + makeTimeString((duration - position).coerceAtLeast(0L)) else makeTimeString(duration),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    showRemaining = !showRemaining
                    com.alananasss.kittytune.core.Prefs.putBoolean(KEY_BAR_REMAINING, showRemaining)
                }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

private const val KEY_BAR_REMAINING = "player_bar_show_remaining"

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
private fun rememberPlayerBarStyle(): com.alananasss.kittytune.data.local.PlayerBarStyle {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) { PlayerPreferences().getPlayerBarStyle() }
}

@Composable
private fun rememberFloatingBarLook(): com.alananasss.kittytune.data.local.FloatingBarLook {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) { PlayerPreferences().getFloatingBarLook() }
}

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
 * Reactive read of the "slider style" setting; recomposes when the pref changes.
 */
@Composable
private fun rememberPlayerSliderStyle(): com.alananasss.kittytune.data.local.PlayerSliderStyle {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) {
        com.alananasss.kittytune.data.local.PlayerPreferences().getPlayerSliderStyle()
    }
}


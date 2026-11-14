package com.alananasss.kittytune.ui.main

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.alananasss.kittytune.core.LocalWindowSeen
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.slider.PlayerSlider
import com.alananasss.kittytune.ui.player.slider.rememberMixTransition
import com.alananasss.kittytune.ui.player.slider.rememberSmoothPlayhead
import kotlin.math.roundToLong

/** Shared seek behavior for both player layouts. Times are relative to the clip when one is active. */
@Composable
internal fun PlaybackSeekBar(
    viewModel: PlayerViewModel,
    colors: SliderColors = SliderDefaults.colors(),
    content: @Composable (Long, Long, @Composable (Modifier) -> Unit) -> Unit,
) {
    val prefsSnapshot by Prefs.flow.collectAsState()
    val preferences = remember { PlayerPreferences() }
    val sliderStyle = remember(prefsSnapshot) { preferences.getPlayerSliderStyle() }
    val seekWheelSeconds = remember(prefsSnapshot) { preferences.getSeekWheelSeconds() }
    val trackKey = viewModel.uiCurrentTrack?.id
    val clip = viewModel.uiClipWindow
    val isRemote = viewModel.isRemotePlayback
    val duration = (clip?.lengthMs ?: viewModel.uiDuration).coerceAtLeast(0L)
    fun isCurrentTrack() = trackKey == viewModel.uiCurrentTrack?.id &&
        clip == viewModel.uiClipWindow && isRemote == viewModel.isRemotePlayback
    fun shownOf(songMs: Long) = clip?.toShown(songMs) ?: songMs
    fun songOf(shownMs: Long) = clip?.toSong(shownMs) ?: shownMs
    fun fractionOf(shownMs: Long) = shownMs.coerceIn(0L, duration).toFloat() / duration.coerceAtLeast(1L)

    var drawnMs by remember { mutableLongStateOf(0L) }
    var playheadMs by remember { mutableLongStateOf(0L) }
    val wheel = rememberWheelSeek(
        drawnMs = { drawnMs },
        reportedMs = { playheadMs },
        durationMs = { duration },
        stepSeconds = { seekWheelSeconds },
        commit = { if (isCurrentTrack()) viewModel.seekTo(songOf(it)) },
    )

    val owner = remember(viewModel, trackKey, clip, duration, sliderStyle, isRemote) { Any() }
    DisposableEffect(owner) {
        onDispose {
            viewModel.cancelScrub(owner)
            wheel.cancel()
        }
    }

    val preview = viewModel.uiScrubPosition?.let(::shownOf)
    val playhead = rememberSmoothPlayhead(
        reportedMs = shownOf(viewModel.uiPlaybackPosition),
        isRunning = viewModel.uiIsPlaying && !viewModel.uiIsLoading,
        followsInput = wheel.isActive,
        trackKey = trackKey,
        seekRevision = viewModel.seekBarRevision,
    )
    playheadMs = playhead
    val position = (wheel.shownMs ?: preview ?: playhead).coerceIn(0L, duration)
    drawnMs = position
    val mix = rememberMixTransition()
    val fraction = fractionOf(position)
    val shownFraction = if (preview != null || wheel.isActive) {
        fraction.also { mix.noteShown(it, trackKey) }
    } else {
        mix.shownFraction(fraction, trackKey, isSeen = LocalWindowSeen.current)
    }

    content(position, duration) { modifier ->
        key(owner) {
            PlayerSlider(
                value = shownFraction,
                onValueChange = {
                    if (isCurrentTrack()) {
                        wheel.cancel()
                        viewModel.updateScrubPosition(owner, songOf((it.toDouble() * duration).roundToLong()))
                    }
                },
                onValueChangeFinished = {
                    if (isCurrentTrack()) {
                        viewModel.finishScrub(owner)?.let { mix.noteShown(fractionOf(shownOf(it))) }
                    } else {
                        viewModel.cancelScrub(owner)
                    }
                },
                onValueChangeCancelled = { viewModel.cancelScrub(owner) },
                sliderStyle = sliderStyle,
                isPlaying = viewModel.uiIsPlaying,
                enabled = duration > 0L,
                colors = colors,
                mix = mix,
                modifier = modifier.seekWheel(wheel),
            )
        }
    }
}

@Composable
internal fun rememberShowRemainingTime(): Boolean {
    val prefsSnapshot by Prefs.flow.collectAsState()
    return remember(prefsSnapshot) { PlayerPreferences().getShowRemainingTime() }
}

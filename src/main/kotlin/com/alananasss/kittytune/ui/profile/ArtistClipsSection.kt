package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.BackHandler
import com.alananasss.kittytune.data.artist.ArtistClips
import com.alananasss.kittytune.data.artist.Clip
import com.alananasss.kittytune.media.ClipPlayback
import com.alananasss.kittytune.ui.home.formatCompactNumber
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.pressScale

/** The artist's music videos, watched right here (tester's list, 5.5). Nothing is shown when there are none. */
@Composable
internal fun ArtistClipsSection(artistName: String, playerViewModel: PlayerViewModel) {
    val clips by produceState(emptyList<Clip>(), artistName) { value = ArtistClips.clipsFor(artistName) }
    var watching by remember { mutableStateOf<Clip?>(null) }
    if (clips.isEmpty()) return

    Column {
        ArtistSectionTitle(str("artist_clips_title"), onOpen = null)
        // Arrows at the ends, and a held left button drags it: the row is longer than the page.
        com.alananasss.kittytune.ui.common.ScrollableLazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(clips, key = { it.url }) { clip -> ClipCard(clip) { watching = clip } }
        }
    }
    watching?.let { clip -> ClipPlayerDialog(clip, playerViewModel, onDismiss = { watching = null }) }
}

@Composable
internal fun ClipCard(clip: Clip, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.width(CLIP_CARD_WIDTH)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .pressScale(interaction)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
        ) {
            AsyncImage(model = clip.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.Center).size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text(
                formatClock(clip.durationSec * 1000),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(clip.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (clip.viewCount > 0) {
            Text(
                str("artist_clip_views", formatCompactNumber(clip.viewCount)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A clip playing over the page. The music pauses while it plays and carries on afterwards if it was playing. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ClipPlayerDialog(clip: Clip, playerViewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val playback = remember(clip) { ClipPlayback(scope) }
    var hasNoStream by remember(clip) { mutableStateOf(false) }

    DisposableEffect(clip) {
        // Watching something else leaves a shared playlist, the way playing your own music does; otherwise the
        // pause below would pause it for everyone.
        com.alananasss.kittytune.data.together.Together.stopListening()
        val wasPlaying = playerViewModel.isPlaying
        playerViewModel.pause()
        onDispose {
            playback.stop()
            if (wasPlaying) playerViewModel.play()
        }
    }
    LaunchedEffect(clip) {
        val url = ArtistClips.streamUrl(clip)
        if (url == null) hasNoStream = true else playback.play(url)
    }

    var fullscreen by remember { mutableStateOf(false) }
    // The dialog's own window is stretched over the screen and given its old size back afterwards; Escape and the
    // back button leave full screen first and close the clip only from the window.
    val windowBefore = remember { arrayOfNulls<java.awt.Rectangle>(1) }
    LaunchedEffect(fullscreen) {
        val window = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow ?: return@LaunchedEffect
        if (fullscreen) {
            windowBefore[0] = window.bounds
            window.bounds = window.graphicsConfiguration.bounds
        } else {
            windowBefore[0]?.let { window.bounds = it }
            windowBefore[0] = null
        }
    }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BackHandler(onBack = { if (fullscreen) fullscreen = false else onDismiss() })
    Dialog(onDismissRequest = { if (fullscreen) fullscreen = false else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = if (fullscreen) RectangleShape else RoundedCornerShape(28.dp),
            color = if (fullscreen) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = (if (fullscreen) Modifier.fillMaxSize() else Modifier.widthIn(max = 960.dp).fillMaxWidth(0.86f))
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        if (fullscreen) fullscreen = false else onDismiss()
                        true
                    } else false
                },
        ) {
            Column(Modifier.padding(if (fullscreen) 0.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (fullscreen) 0.dp else 12.dp)) {
                if (!fullscreen) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(clip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(clip.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Close, contentDescription = str("btn_close"))
                    }
                }
                Box(
                    (if (fullscreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)))
                        .background(Color.Black)
                        .pointerInput(playback.isLoading) {
                            detectTapGestures(
                                onDoubleTap = { fullscreen = !fullscreen },
                                onTap = { if (!playback.isLoading) playback.togglePause() },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val frame = playback.frame
                    if (frame != null) {
                        androidx.compose.foundation.Image(frame, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    } else if (!hasNoStream && !playback.hasFailed) {
                        AsyncImage(model = clip.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    when {
                        hasNoStream || playback.hasFailed -> Text(str("artist_clip_failed"), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        playback.isLoading -> ContainedLoadingIndicator(Modifier.size(56.dp))
                    }
                }
                Box(if (fullscreen) Modifier.padding(16.dp) else Modifier) {
                    ClipControls(playback, fullscreen, onToggleFullscreen = { fullscreen = !fullscreen })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClipControls(playback: ClipPlayback, fullscreen: Boolean, onToggleFullscreen: () -> Unit) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val duration = playback.durationMs.coerceAtLeast(1L)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledIconButton(onClick = { playback.togglePause() }, enabled = !playback.isLoading, shapes = IconButtonDefaults.shapes()) {
            Icon(
                when {
                    playback.hasEnded -> Icons.Rounded.Replay
                    playback.isPaused -> Icons.Rounded.PlayArrow
                    else -> Icons.Rounded.Pause
                },
                contentDescription = null,
            )
        }
        Text(formatClock(playback.positionMs), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = dragged ?: (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.let { playback.seekTo((it * duration).toLong()) }
                dragged = null
            },
            enabled = playback.durationMs > 0,
            // The wheel over the bar moves the clip, five seconds a notch.
            modifier = Modifier.weight(1f).wheelSteps { notches ->
                if (playback.durationMs > 0) playback.seekTo((playback.positionMs - (notches * CLIP_WHEEL_SEEK_MS).toLong()).coerceIn(0L, playback.durationMs))
            },
        )
        Text(formatClock(playback.durationMs), style = MaterialTheme.typography.labelMedium)
        Icon(
            if (playback.volume <= 0.001f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Slider(
            value = playback.volume,
            onValueChange = { playback.volume = it },
            // And over the volume, five per cent a notch.
            modifier = Modifier.width(110.dp).wheelSteps { notches -> playback.volume = (playback.volume - notches * 0.05f).coerceIn(0f, 1f) },
        )
        IconButton(onClick = onToggleFullscreen, shapes = IconButtonDefaults.shapes()) {
            Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, contentDescription = null)
        }
    }
}

private const val CLIP_WHEEL_SEEK_MS = 5_000f

/** Calls [onNotches] with the wheel's delta over this element, positive down, and keeps the page from scrolling under it. */
private fun Modifier.wheelSteps(onNotches: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
            if (delta == 0f) continue
            onNotches(delta)
            event.changes.forEach { it.consume() }
        }
    }
}

private fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val minutes = totalSec / 60 % 60
    val seconds = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private val CLIP_CARD_WIDTH = 260.dp

/**
 * Clips on the home page: the videos of the artists the listener plays most (round 3 of the tester's list, 24). The
 * artists come from the liked songs, the most liked first, and each one's clips are looked up once and kept.
 */
@Composable
internal fun HomeClipsShelf(playerViewModel: PlayerViewModel) {
    val likes by com.alananasss.kittytune.data.LikeRepository.likedTracks.collectAsState()
    val artists = remember(likes.size) {
        likes.mapNotNull { it.displayArtist.takeIf { name -> name.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(HOME_CLIP_ARTISTS).map { it.key }
    }
    val clips by produceState(emptyList<Clip>(), artists) {
        value = withContext(Dispatchers.IO) {
            coroutineScope { artists.map { async { ArtistClips.clipsFor(it).take(HOME_CLIPS_EACH) } }.awaitAll().flatten() }
        }.distinctBy { it.url }.take(HOME_CLIPS_MAX)
    }
    var watching by remember { mutableStateOf<Clip?>(null) }
    if (clips.isEmpty()) return

    Column {
        ArtistSectionTitle(str("home_clips_title"), onOpen = null)
        com.alananasss.kittytune.ui.common.ScrollableLazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            fadeColor = MaterialTheme.colorScheme.background,
        ) {
            items(clips, key = { it.url }) { clip -> ClipCard(clip) { watching = clip } }
        }
    }
    watching?.let { clip -> ClipPlayerDialog(clip, playerViewModel, onDismiss = { watching = null }) }
}

private const val HOME_CLIP_ARTISTS = 4
private const val HOME_CLIPS_EACH = 3
private const val HOME_CLIPS_MAX = 12

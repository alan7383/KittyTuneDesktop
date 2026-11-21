@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.alananasss.kittytune.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.LocalWindowSeen
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.cover.FeedVisual
import com.alananasss.kittytune.data.cover.FeedVisualResolver
import com.alananasss.kittytune.data.cover.FeedVisualSource
import com.alananasss.kittytune.data.PlaybackTrackKey
import com.alananasss.kittytune.data.playbackKey
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.game.TrackHighlights
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.cover.CanvasVideo
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal data class PreviewPost(
    val track: Track,
    val actor: User?,
    val caption: String? = null,
    val isRepost: Boolean = false,
)

private const val PREVIEW_MS = 30_000L
private val visualResolver = FeedVisualResolver()

@Composable
internal fun FeedPreviewPager(
    posts: List<PreviewPost>,
    player: PlayerViewModel,
    feed: FeedViewModel,
    videoSamples: Boolean,
    requestMore: () -> Unit,
) {
    val pager = rememberPagerState { posts.size }
    val scope = rememberCoroutineScope()
    val together by Together.active.collectAsState()
    val windowSeen = LocalWindowSeen.current
    val remoteDevice by ConnectManager.selectedDevice.collectAsState()
    val localPreview = together == null && remoteDevice == null && !player.isRemotePlayback && !player.isPlayerExpanded
    val canPreview = localPreview && windowSeen
    val animated by player.playerPrefs.getAnimatedCoversFlow().collectAsState(initial = player.playerPrefs.getAnimatedCoversEnabled())
    val post = posts.getOrNull(pager.settledPage)
    val track = post?.track
    val identity = track?.playbackKey()
    var preview by remember { mutableStateOf<Pair<PlaybackTrackKey, Long>?>(null) }
    var fullTrack by remember { mutableStateOf<PlaybackTrackKey?>(null) }
    var retryPreview by remember(identity) { mutableIntStateOf(0) }
    val current = player.uiCurrentTrack?.playbackKey()
    val hasPreview = identity != null && current == identity && preview?.first == identity && fullTrack != identity
    val playing = hasPreview && player.uiIsPlaying
    val previewStart = preview?.second ?: 0

    LaunchedEffect(pager.settledPage, posts.size) {
        if (pager.settledPage >= posts.size - 3) requestMore()
    }
    LaunchedEffect(identity, canPreview, retryPreview) {
        val candidate = track ?: return@LaunchedEffect
        val key = candidate.playbackKey()
        if (!canPreview || fullTrack == key || player.uiCurrentTrack?.playbackKey() == key) return@LaunchedEffect
        val start = TrackHighlights.startMs(candidate)
        ensureActive()
        preview = key to start
        player.playPlaylist(listOf(candidate), respectShuffle = false, startAtPositionMs = start)
    }
    LaunchedEffect(hasPreview, localPreview, previewStart) {
        if (!hasPreview || !localPreview) return@LaunchedEffect
        snapshotFlow { player.currentPosition to player.uiIsPlaying }.collect { (position, playingNow) ->
            val limit = player.duration.takeIf { it > previewStart }
                ?.let { minOf(previewStart + PREVIEW_MS, it - 300L) } ?: (previewStart + PREVIEW_MS)
            if (playingNow && limit > previewStart && position >= limit) player.seekTo(previewStart)
        }
    }

    fun advance(delta: Int) {
        val target = (pager.settledPage + delta).coerceIn(0, posts.lastIndex.coerceAtLeast(0))
        if (!pager.isScrollInProgress && target != pager.settledPage) {
            scope.launch { pager.animateScrollToPage(target) }
        } else if (delta > 0 && target == pager.settledPage) {
            requestMore()
        }
    }

    Row(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VerticalPager(
            state = pager,
            modifier = Modifier.widthIn(max = 580.dp).fillMaxWidth(0.85f).fillMaxHeight()
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val change = event.changes.firstOrNull() ?: return@onPointerEvent
                    val dy = change.scrollDelta.y
                    if (dy != 0f && !change.isConsumed) {
                        change.consume()
                        advance(if (dy > 0f) 1 else -1)
                    }
                },
        ) { index ->
            val page = posts.getOrNull(index) ?: return@VerticalPager
            val active = index == pager.settledPage
            PreviewCard(
                page, player, feed,
                animate = active && canPreview && playing,
                animated = animated,
                videoSamples = videoSamples,
                playing = active && player.uiIsPlaying && current == page.track.playbackKey(),
                progress = if (active && hasPreview) ((player.currentPosition - previewStart).toFloat() / PREVIEW_MS).coerceIn(0f, 1f) else 0f,
                onPlay = {
                    if (player.uiCurrentTrack?.playbackKey() == page.track.playbackKey()) {
                        player.togglePlayPause()
                    } else if (active && canPreview) {
                        retryPreview++
                    } else if (active) {
                        player.playPlaylist(listOf(page.track), respectShuffle = false)
                    }
                },
                onOpen = {
                    if (player.uiCurrentTrack?.playbackKey() != page.track.playbackKey()) {
                        player.playPlaylist(listOf(page.track), respectShuffle = false)
                    }
                    fullTrack = page.track.playbackKey()
                    player.isPlayerExpanded = true
                },
                onDislike = {
                    feed.dislike(page.track)
                    if (active) {
                        if (pager.settledPage == posts.lastIndex && playing) player.pause()
                        advance(1)
                    }
                },
            )
        }
        Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { advance(-1) }, enabled = pager.settledPage > 0) {
                Icon(Icons.Rounded.KeyboardArrowUp, str("feed_previous_preview"))
            }
            IconButton(onClick = { advance(1) }) {
                Icon(Icons.Rounded.KeyboardArrowDown, str("feed_next_preview"))
            }
        }
    }
}

@Composable
private fun PreviewCard(
    post: PreviewPost,
    player: PlayerViewModel,
    feed: FeedViewModel,
    animate: Boolean,
    animated: Boolean,
    videoSamples: Boolean,
    playing: Boolean,
    progress: Float,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onDislike: () -> Unit,
) {
    val track = post.track
    val author = track.user ?: post.actor
    val authorId = author?.id
    val followed = authorId?.let { feed.followState[it] }
    LaunchedEffect(authorId, animate, playing) {
        if (authorId != null && (animate || playing)) feed.checkFollow(authorId)
    }
    Box(
        Modifier.fillMaxSize().padding(bottom = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        AsyncImage(track.fullResArtwork, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (animate && (animated || videoSamples)) {
            key(track.playbackKey(), animated, videoSamples) {
                PreviewVisual(track, animated, videoSamples)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)))))
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (post.isRepost && post.actor?.username != null) {
                    Text(str("feed_reposted_by", post.actor.username), color = Color.White)
                }
                post.caption?.takeIf(String::isNotBlank)?.let {
                    Text(it, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    track.title.orEmpty(),
                    modifier = Modifier.clickable(onClick = onOpen),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(track.displayArtist, Modifier.clickable { player.navigateToTrackArtist(track) }, color = Color.White)
                    if (authorId != null && followed != null) {
                        TextButton(onClick = { feed.toggleFollow(authorId) }) {
                            Text(str(if (followed) "feed_unfollow" else "feed_follow"), color = Color.White)
                        }
                    }
                }
                if (progress > 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row {
                    IconButton(onClick = { player.toggleTrackLike(track) }) {
                        Icon(Icons.Rounded.FavoriteBorder, str("feed_like"), tint = Color.White)
                    }
                    IconButton(onClick = onDislike) {
                        Icon(Icons.Rounded.ThumbDown, str("feed_dislike"), tint = Color.White)
                    }
                    IconButton(onClick = { player.openComments(track) }) {
                        Icon(Icons.Rounded.Comment, str("feed_comments"), tint = Color.White)
                    }
                    IconButton(onClick = { player.prepareBulkAdd(listOf(track)) }) {
                        Icon(Icons.Rounded.PlaylistAdd, str("feed_add_to_playlist"), tint = Color.White)
                    }
                    IconButton(onClick = { player.openTrackDetails(track) }) {
                        Icon(Icons.Rounded.Info, str("track_info_sheet_title"), tint = Color.White)
                    }
                }
            }
            FilledIconButton(onClick = onPlay) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, str(if (playing) "btn_pause" else "btn_play"))
            }
        }
    }
}

@Composable
private fun PreviewVisual(track: Track, animated: Boolean, samples: Boolean) {
    val order = remember(animated, samples) {
        buildList {
            if (animated) add(FeedVisualSource.ANIMATED_COVER)
            if (samples) add(FeedVisualSource.YOUTUBE)
        }
    }
    var visual by remember { mutableStateOf<FeedVisual?>(null) }
    var excluded by remember { mutableStateOf(emptySet<FeedVisualSource>()) }
    LaunchedEffect(excluded) {
        visual = visualResolver.resolve(track, order, excluded)
    }
    visual?.let { art ->
        key(art) {
            CanvasVideo(
                canvasUrl = art.url,
                modifier = Modifier.fillMaxSize(),
                clipStartMs = art.startMs,
                clipEndMs = art.endMs,
                onError = {
                    visual = null
                    excluded = excluded + art.source
                },
            )
        }
    }
}

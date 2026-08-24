@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.onClick
import androidx.compose.foundation.PointerMatcher
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.ui.draw.shadow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.alananasss.kittytune.domain.Track
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * Right panel — the "Now Playing" column from the reference: big artwork,
 * title/artist and context, with tabs for queue and synced lyrics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingPanel(
    playerViewModel: PlayerViewModel,
    tab: NowPlayingTab,
    onTabChange: (NowPlayingTab) -> Unit,
    onClose: () -> Unit,
    onOpenFullLyrics: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm = playerViewModel
    val track = vm.currentTrack ?: return

    Surface(
        modifier = modifier,
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxSize()) {

            // Header: context name + close
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = vm.currentContext?.displayText ?: track.title ?: "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            val tabs = listOf(NowPlayingTab.TRACK, NowPlayingTab.QUEUE, NowPlayingTab.LYRICS, NowPlayingTab.EFFECTS)
            val tabLabels = listOf(
                str("detail_track_title"),
                str("player_queue"),
                str("player_lyrics"),
                str("player_effects"),
            )
            SecondaryTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0)) {
                tabs.forEachIndexed { i, t ->
                    Tab(
                        selected = tab == t,
                        onClick = { onTabChange(t) },
                        text = { Text(tabLabels[i], maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            when (tab) {
                NowPlayingTab.QUEUE -> QueueList(vm)
                NowPlayingTab.LYRICS -> LyricsPreview(vm, onOpenFullLyrics)
                NowPlayingTab.EFFECTS -> com.alananasss.kittytune.ui.player.EffectsPanel(vm)
                else -> TrackInfoTab(vm)
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueList(vm: PlayerViewModel) {
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            vm.moveQueueItem(from.index, to.index)
        }
    )

    val currentTrack = vm.currentTrack
    androidx.compose.runtime.LaunchedEffect(currentTrack) {
        if (currentTrack != null && vm.queueState.isNotEmpty()) {
            val index = vm.queueState.indexOfFirst { it.id == currentTrack.id }
            if (index >= 0) {
                listState.animateScrollToItem(maxOf(0, index - 2))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
    ) {
        itemsIndexed(items = vm.queueState, key = { index, track -> getQueueTrackStableKey(index, track.id, vm.queueState) }) { index, track ->
            val itemKey = getQueueTrackStableKey(index, track.id, vm.queueState)
            ReorderableItem(
                state = reorderableState,
                key = itemKey
            ) { isDragging ->
                val isCurrent = index == vm.currentQueueIndex
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                val backgroundColor = if (isDragging)
                    MaterialTheme.colorScheme.surfaceContainerHigh
                else
                    MaterialTheme.colorScheme.surfaceContainerLow

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .onClick(
                            matcher = PointerMatcher.mouse(PointerButton.Secondary),
                            onClick = { vm.showTrackOptions(track) }
                        )
                        .clickable { vm.skipToQueueItem(index) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
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
                                Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "Reorder",
                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(24.dp)
                            .draggableHandle()
                            .pointerHoverIcon(PointerIcon.Hand)
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsPreview(vm: PlayerViewModel, onOpenFullLyrics: () -> Unit) {
    val lines = vm.lyricsLines
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = str("player_lyrics"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            androidx.compose.material3.FilledTonalButton(
                onClick = onOpenFullLyrics,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(str("btn_fullscreen"), style = MaterialTheme.typography.labelSmall)
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (lines.isEmpty()) {
                item {
                    Text(
                        text = vm.rawPlainLyrics ?: str("lyrics_no_data"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(lines) { _, line ->
                    val active = vm.currentPosition + vm.lyricsOffset >= line.startTime
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (active) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.seekTo(line.startTime) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

private fun getQueueTrackStableKey(index: Int, trackId: Long, trackList: List<Track>): String {
    var occurrence = 0
    val max = index.coerceAtMost(trackList.size)
    for (i in 0 until max) {
        if (trackList[i].id == trackId) occurrence++
    }
    return "${trackId}_dup$occurrence"
}


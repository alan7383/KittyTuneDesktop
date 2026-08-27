@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.alananasss.kittytune.domain.Track
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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



/**
 * The queue, as the panel shows it (issue #33).
 *
 * Three things were reported about this one list. The interface lurched when you started a track
 * from it; the tracks already played took up as much room as the ones still to come; and there was
 * no way to drop one.
 *
 * Nothing is hidden and nothing is trimmed. What was already played stays in the list, because
 * [PlayerViewModel.smartPrevious] walks this very list backwards and [PlayerViewModel.toggleShuffle]
 * rebuilds it from the untouched original — deleting the past would cost the Previous button and the
 * way back out of shuffle, for a complaint that is about attention rather than storage. It simply
 * stops claiming the same importance: compact rows, dimmed, under a heading, with the track just
 * played left at full size because that is the one worth recognising.
 *
 * Every index here is an index into [PlayerViewModel.queueState] and stays one — this list draws no
 * item of its own that the queue does not have — which is what lets the click, the cross and the
 * drag all pass `index` straight through.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueList(vm: PlayerViewModel) {
    val listState = rememberLazyListState()
    val queue = vm.queueState
    val currentIndex = vm.currentQueueIndex
    val keys = remember(queue) { com.alananasss.kittytune.ui.player.queueItemKeys(queue) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        // Resolved through the keys rather than the raw lazy-list indices. The two agree today, and
        // they would stop agreeing the moment this list gained a header of its own — at which point
        // a drag would quietly move the wrong track.
        onMove = { from, to ->
            val fromIndex = keys.indexOf(from.key)
            val toIndex = keys.indexOf(to.key)
            if (fromIndex >= 0 && toIndex >= 0) vm.moveQueueItem(fromIndex, toIndex)
        }
    )

    com.alananasss.kittytune.ui.player.AnchorCurrentQueueItem(
        listState = listState,
        currentIndex = currentIndex,
        currentTrackId = vm.currentTrack?.id,
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
    ) {
        itemsIndexed(items = queue, key = { index, _ -> keys[index] }) { index, track ->
            ReorderableItem(
                state = reorderableState,
                key = keys[index]
            ) { isDragging ->
                val isCurrent = index == currentIndex
                // The track just played keeps its full row. Only what is further back gets compacted,
                // which is exactly the line drawn in the report: the previous one, the current one,
                // and everything ahead.
                val isPast = currentIndex > 0 && index < currentIndex - 1

                Column {
                    // Named where the treatment changes, so the compact rows read as a section rather
                    // than as rows that failed to load properly.
                    if (index == 0 && currentIndex >= 2) QueueSectionRule(str("queue_played"))
                    if (currentIndex >= 0 && index == currentIndex + 1) QueueSectionRule(str("queue_up_next"))

                    QueueRow(
                        vm = vm,
                        track = track,
                        index = index,
                        isCurrent = isCurrent,
                        isPast = isPast,
                        isDragging = isDragging,
                    )
                }
            }
        }
    }
}

/** The heading that marks where one part of the queue stops and the next begins. */
@Composable
private fun QueueSectionRule(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * One row of the queue.
 *
 * An extension on the reorderable scope rather than a plain composable, because `draggableHandle()`
 * only exists inside it and the row is where the handle lives.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReorderableCollectionItemScope.QueueRow(
    vm: PlayerViewModel,
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPast: Boolean,
    isDragging: Boolean,
) {
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
            .padding(horizontal = 8.dp, vertical = if (isPast) 4.dp else 8.dp)
            // Only where there is something to fade: the modifier forces its own layer, which is not
            // worth paying for on every upcoming row.
            .then(if (isPast) Modifier.alpha(PAST_ROW_ALPHA) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .size(if (isPast) 26.dp else 40.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: "",
                style = if (isPast) MaterialTheme.typography.bodySmall
                else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The artist line is what makes a row two lines tall, and it is there to help *choose* a
            // track. Something already played is only there to be recognised, so it goes.
            if (!isPast) {
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
        }
        // No cross on the track being played. `removeTrackFromQueue` would take it out from under the
        // player and clamp `currentQueueIndex` onto whichever track slid into its place, leaving the
        // audio and the queue disagreeing about what is playing.
        if (!isCurrent) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { vm.removeTrackFromQueue(index) }
                    .pointerHoverIcon(PointerIcon.Hand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = str("queue_remove"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
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

/** How far the already-played rows recede. Legible on purpose — they are still part of the queue. */
private const val PAST_ROW_ALPHA = 0.55f

@Composable
private fun LyricsPreview(vm: PlayerViewModel, onOpenFullLyrics: () -> Unit) {
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

        PanelLyrics(vm, Modifier.fillMaxSize())
    }
}



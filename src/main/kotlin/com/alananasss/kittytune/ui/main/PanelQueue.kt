package com.alananasss.kittytune.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.onClick
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.Tip
import com.alananasss.kittytune.ui.player.AnchorCurrentQueueItem
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.queueItemKeys
import com.alananasss.kittytune.utils.makeTimeString
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn

/**
 * The queue, as the panel shows it (issue #33).
 *
 * Nothing is hidden and nothing is trimmed. What was already played stays in the list, because
 * [PlayerViewModel.smartPrevious] walks this very list backwards and [PlayerViewModel.toggleShuffle]
 * rebuilds it from the untouched original — deleting the past would cost the Previous button and the
 * way back out of shuffle. Instead the rows before the previous one are compacted: small artwork, no
 * artist line, dimmed, under an expandable "Already played" heading, with the track just played left at
 * full size because that is the one worth recognising. Past [PAST_COLLAPSE_THRESHOLD] of them they start
 * collapsed.
 *
 * The rows are quiet until pointed at: the track playing sits in a tinted card with moving bars on its
 * cover, the others are bare and show their length. Hovering a row lights it and swaps the length for the
 * remove and drag controls, so a long queue is not a column of crosses and handles.
 */
private sealed interface QueueListItem {
    data class PastHeader(val count: Int, val isExpanded: Boolean) : QueueListItem
    data class TrackItem(val queueIndex: Int, val track: Track) : QueueListItem
}

/** Beyond this many heard tracks, they collapse under an arrow so the queue stays tidy (issue #33). */
private const val PAST_COLLAPSE_THRESHOLD = 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun QueueList(vm: PlayerViewModel) {
    val listState = rememberLazyListState()
    val queue = vm.queueState
    val currentIndex = vm.currentQueueIndex
    val keys = remember(queue) { queueItemKeys(queue) }

    // Compacted for having been heard, not for sitting at a lower index. Jumping ahead to the sixth
    // track used to draw the five skipped ones as "already played", and jumping back drew the ones
    // really heard as still to come (issue #33).
    val played = vm.playedTrackIds
    val pastIndices = remember(queue, currentIndex, played) {
        queue.indices.filter { it < currentIndex - 1 && queue[it].id in played }
    }
    val pastCount = pastIndices.size
    val firstPast = pastIndices.firstOrNull()

    var pastExpandedByUser by remember { mutableStateOf<Boolean?>(null) }
    val isPastExpanded = pastExpandedByUser ?: (pastCount <= PAST_COLLAPSE_THRESHOLD)

    val listItems = remember(queue, currentIndex, played, isPastExpanded) {
        buildList {
            for (index in queue.indices) {
                if (index == firstPast) add(QueueListItem.PastHeader(count = pastCount, isExpanded = isPastExpanded))
                val isPast = index in pastIndices
                if (!isPast || isPastExpanded) add(QueueListItem.TrackItem(queueIndex = index, track = queue[index]))
            }
        }
    }
    val upNextCount = (queue.size - currentIndex - 1).coerceAtLeast(0)

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        // Resolved through the keys rather than the raw lazy-list indices so reordering stays robust
        // even when a header is present or past rows are collapsed.
        onMove = { from, to ->
            val fromIndex = keys.indexOf(from.key)
            val toIndex = keys.indexOf(to.key)
            if (fromIndex >= 0 && toIndex >= 0) vm.moveQueueItem(fromIndex, toIndex)
        }
    )

    val currentKey = keys.getOrNull(currentIndex)
    val targetTrackIndex = (currentIndex - 1).coerceAtLeast(0)
    val targetListIndex = listItems.indexOfFirst {
        it is QueueListItem.TrackItem && it.queueIndex == targetTrackIndex
    }.coerceAtLeast(0)

    AnchorCurrentQueueItem(
        listState = listState,
        currentIndex = currentIndex,
        currentTrackId = vm.currentTrack?.id,
        currentKey = currentKey,
        targetIndex = targetListIndex,
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            items = listItems,
            key = { item ->
                when (item) {
                    is QueueListItem.PastHeader -> "queue_header_past"
                    is QueueListItem.TrackItem -> keys[item.queueIndex]
                }
            }
        ) { item ->
            when (item) {
                is QueueListItem.PastHeader -> QueueSectionRule(
                    label = str("queue_played"),
                    count = item.count,
                    isCollapsible = true,
                    isExpanded = item.isExpanded,
                    onToggle = { pastExpandedByUser = !item.isExpanded },
                )
                is QueueListItem.TrackItem -> {
                    val index = item.queueIndex
                    ReorderableItem(state = reorderableState, key = keys[index]) { isDragging ->
                        Column {
                            // Named where the treatment changes, so the upcoming tracks read as a section.
                            if (currentIndex >= 0 && index == currentIndex + 1) {
                                QueueSectionRule(str("queue_up_next"), count = upNextCount)
                            }
                            QueueRow(
                                vm = vm,
                                track = item.track,
                                index = index,
                                isCurrent = index == currentIndex,
                                isPast = index in pastIndices,
                                isDragging = isDragging,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The heading that marks where one part of the queue stops and the next begins. */
@Composable
private fun QueueSectionRule(
    label: String,
    count: Int? = null,
    isCollapsible: Boolean = false,
    isExpanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val labelColor = if (isCollapsible && isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    val heading: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isCollapsible && onToggle != null) {
                        Modifier
                            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                            .pointerHoverIcon(PointerIcon.Hand)
                    } else Modifier
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = labelColor,
            )
            if (count != null && count > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (isCollapsible) {
                val arrowRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "past_arrow_rot")
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(18.dp).rotate(arrowRotation),
                )
            }
        }
    }

    if (isCollapsible) {
        Tip(if (isExpanded) str("queue_collapse_played") else str("queue_expand_played"), content = heading)
    } else {
        heading()
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
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val showsControls = isHovered || isDragging

    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "queue_row_elevation")
    val background by animateColorAsState(
        targetValue = when {
            isDragging -> scheme.surfaceContainerHighest
            isCurrent -> scheme.secondaryContainer
            isHovered -> scheme.surfaceContainerHigh
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "queue_row_background"
    )
    val rowShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, rowShape)
            .clip(rowShape)
            .background(background)
            .hoverable(interactionSource)
            .onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary),
                onClick = { vm.showTrackOptions(track) }
            )
            .clickable(interactionSource = interactionSource, indication = null) { vm.skipToQueueItem(index) }
            .padding(horizontal = 8.dp, vertical = if (isPast) 4.dp else 6.dp)
            // Only where there is something to fade: the modifier forces its own layer, which is not
            // worth paying for on every upcoming row.
            .then(if (isPast && !isHovered) Modifier.alpha(PAST_ROW_ALPHA) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QueueCover(track, size = if (isPast) 28.dp else 44.dp, isCurrent = isCurrent, isPlaying = vm.isPlaying)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: "",
                style = if (isPast) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) scheme.onSecondaryContainer else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The artist line is there to help *choose* a track. Something already played is only there
            // to be recognised, so it goes.
            if (!isPast) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtistLinkText(
                        track = track,
                        onArtistClick = { vm.navigateToTrackArtist(it) },
                        text = track.displayArtist.ifBlank { track.user?.username ?: "" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.user?.verified == true) {
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Rounded.Verified, null, tint = scheme.primary, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // The length and the controls share one slot; the handle stays composed while hidden, since the
        // drag has to be able to start from it the moment the row is hovered.
        Box(contentAlignment = Alignment.CenterEnd) {
            val duration = track.durationMs?.takeIf { it > 0 }
            if (duration != null && !isPast) {
                Text(
                    text = makeTimeString(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.alpha(if (showsControls) 0f else 1f),
                )
            }
            Row(
                modifier = Modifier.alpha(if (showsControls) 1f else 0f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // No cross on the track being played. `removeTrackFromQueue` would take it out from under
                // the player and leave the audio and the queue disagreeing about what is playing.
                if (!isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(enabled = showsControls) { vm.removeTrackFromQueue(index) }
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = str("queue_remove"),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = null,
                    tint = if (isDragging) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(2.dp)
                        .draggableHandle()
                        .pointerHoverIcon(PointerIcon.Hand)
                )
            }
        }
    }
}

/** The row's artwork; on the track being played it carries moving bars, still while paused. */
@Composable
private fun QueueCover(track: Track, size: Dp, isCurrent: Boolean, isPlaying: Boolean) {
    val shape = RoundedCornerShape(if (size < 32.dp) 6.dp else 8.dp)
    Box(Modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        if (isCurrent) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            PlayingBars(isPlaying = isPlaying, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** Three bars bouncing out of step, the usual "this one is playing" mark. */
@Composable
private fun PlayingBars(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playing_bars")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        BAR_PERIODS_MS.forEachIndexed { i, period ->
            val bounce by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(period), RepeatMode.Reverse),
                label = "playing_bar_$i"
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(if (isPlaying) bounce else PAUSED_BAR_HEIGHTS[i])
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White)
            )
        }
    }
}

private val BAR_PERIODS_MS = listOf(420, 560, 360)
private val PAUSED_BAR_HEIGHTS = listOf(0.4f, 0.7f, 0.5f)

/** How far the already-played rows recede. Legible on purpose — they are still part of the queue. */
private const val PAST_ROW_ALPHA = 0.55f

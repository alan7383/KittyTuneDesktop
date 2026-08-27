@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.main

import androidx.compose.material3.ButtonDefaults

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.GraphicEq
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.alananasss.kittytune.ui.common.ArtistLinkText
import com.alananasss.kittytune.ui.common.Tip
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

            val hiddenTabs = rememberHiddenPanelTabs()
            // Falls back to the full row rather than drawing a panel with no way out of itself.
            val tabs = remember(hiddenTabs) {
                NowPlayingTab.entries.filter { it.prefKey !in hiddenTabs }.ifEmpty { NowPlayingTab.entries }
            }
            // The tab we were on can be hidden from the menu below while we are looking at it.
            LaunchedEffect(tabs) { if (tab !in tabs) onTabChange(tabs.first()) }

            // Header: context name + tab visibility + close
            var tabMenuOpen by remember { mutableStateOf(false) }
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
                Box {
                    Tip(str("panel_tabs_title")) {
                        IconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = { tabMenuOpen = true },
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = str("panel_tabs_title"),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                    PanelTabsMenu(
                        expanded = tabMenuOpen,
                        hiddenTabs = hiddenTabs,
                        onDismiss = { tabMenuOpen = false },
                    )
                }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            PanelTabRow(tabs = tabs, selected = tab, onTabChange = onTabChange)

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

    // Compacted for having been heard, not for sitting at a lower index. Jumping ahead to the sixth
    // track used to draw the five skipped ones as "already played", and jumping back drew the ones
    // really heard as still to come (issue #33). The track just played keeps its full row either way,
    // since that is the one worth recognising.
    val played = vm.playedTrackIds
    val firstPast = remember(queue, currentIndex, played) {
        queue.indices.firstOrNull { it < currentIndex - 1 && queue[it].id in played }
    }

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
                val isPast = index < currentIndex - 1 && track.id in played

                Column {
                    // Named where the treatment changes, so the compact rows read as a section rather
                    // than as rows that failed to load properly.
                    if (firstPast != null && index == firstPast) QueueSectionRule(str("queue_played"))
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


/**
 * The tab row, which gives up its labels before it gives up its legibility (issue #33).
 *
 * Four text-only tabs in a side panel came out as four truncated words, so the row named nothing and
 * a new user could not tell the queue from the effects. Each tab has an icon now, and the labels are
 * dropped whole — with a tooltip taking over — the moment they no longer fit.
 *
 * "No longer fit" is measured rather than guessed at a breakpoint: the labels are laid out with the
 * row's own text style and summed. A guessed width would be wrong in every language but the one it
 * was tuned in, and "Commentaires" against "Comments" is exactly the case that breaks it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelTabRow(
    tabs: List<NowPlayingTab>,
    selected: NowPlayingTab,
    onTabChange: (NowPlayingTab) -> Unit,
) {
    val labels = tabs.map { panelTabLabel(it) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.titleSmall
    val density = LocalDensity.current

    BoxWithConstraints {
        val available = with(density) { maxWidth.roundToPx() }
        val needed = remember(labels, labelStyle, available) {
            val text = labels.sumOf { measurer.measure(it, labelStyle).size.width }
            val chrome = with(density) { (TAB_ICON_SIZE + TAB_ICON_GAP + TAB_SIDE_PADDING * 2).roundToPx() }
            text + chrome * tabs.size
        }
        val compact = needed > available

        SecondaryTabRow(selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0)) {
            tabs.forEachIndexed { i, t ->
                val tab = @Composable {
                    Tab(
                        selected = selected == t,
                        onClick = { onTabChange(t) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PanelTabIcon(t)
                                if (!compact) {
                                    Spacer(Modifier.width(TAB_ICON_GAP))
                                    Text(labels[i], maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        },
                    )
                }
                // Only where the icon is on its own. A tooltip repeating a label you can already
                // read is noise.
                if (compact) Tip(labels[i]) { tab() } else tab()
            }
        }
    }
}

@Composable
private fun PanelTabIcon(tab: NowPlayingTab) {
    val modifier = Modifier.size(TAB_ICON_SIZE)
    when (tab) {
        NowPlayingTab.TRACK -> Icon(Icons.Outlined.Info, null, modifier)
        NowPlayingTab.QUEUE -> Icon(Icons.AutoMirrored.Outlined.QueueMusic, null, modifier)
        // The same drawing the player bar's lyrics button uses, so the two are recognisably one
        // feature rather than two icons for it.
        NowPlayingTab.LYRICS -> Icon(
            painter = androidx.compose.ui.res.painterResource("icons/lyrics.svg"),
            contentDescription = null,
            modifier = modifier,
        )
        NowPlayingTab.EFFECTS -> Icon(Icons.Rounded.GraphicEq, null, modifier)
    }
}

@Composable
private fun panelTabLabel(tab: NowPlayingTab): String = when (tab) {
    NowPlayingTab.TRACK -> str("detail_track_title")
    NowPlayingTab.QUEUE -> str("player_queue")
    NowPlayingTab.LYRICS -> str("player_lyrics")
    NowPlayingTab.EFFECTS -> str("player_effects")
}

/**
 * The tab-visibility menu, on the panel itself.
 *
 * Also mirrored in Appearance > Customize buttons, next to the player bar's own row, because that is
 * where someone looking for a setting looks. Here because that is where the tabs are, and the effect
 * is visible the moment it is toggled.
 */
@Composable
private fun PanelTabsMenu(
    expanded: Boolean,
    hiddenTabs: Set<String>,
    onDismiss: () -> Unit,
) {
    val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences() }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Text(
            text = str("panel_tabs_desc"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).width(220.dp),
        )
        NowPlayingTab.entries.forEach { t ->
            val shown = t.prefKey !in hiddenTabs
            // The last one standing cannot be hidden: a panel with no tabs has no way back.
            val isLastShown = shown && hiddenTabs.size == NowPlayingTab.entries.size - 1
            DropdownMenuItem(
                enabled = !isLastShown,
                onClick = {
                    prefs.setHiddenPanelTabs(
                        if (shown) hiddenTabs + t.prefKey else hiddenTabs - t.prefKey
                    )
                },
                leadingIcon = { PanelTabIcon(t) },
                trailingIcon = {
                    Checkbox(checked = shown, onCheckedChange = null, enabled = !isLastShown)
                },
                text = { Text(panelTabLabel(t)) },
            )
        }
    }
}

/** Reactive read of which panel tabs are hidden; recomposes on pref changes. */
@Composable
private fun rememberHiddenPanelTabs(): Set<String> {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) {
        com.alananasss.kittytune.data.local.PlayerPreferences().getHiddenPanelTabs()
    }
}

private val TAB_ICON_SIZE = 16.dp
private val TAB_ICON_GAP = 6.dp

/** What a tab spends on padding either side of its content, per Material's own tab metrics. */
private val TAB_SIDE_PADDING = 16.dp

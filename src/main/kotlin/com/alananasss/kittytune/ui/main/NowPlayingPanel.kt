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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.alananasss.kittytune.core.str
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
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
 * The panel's lyrics tab: a header, and the words underneath (issue #33).
 *
 * ## Why the gear is here too
 *
 * "You can also add the scale + focus mode for lyrics."
 *
 * Both have existed for two releases — [com.alananasss.kittytune.data.local.LyricsDisplayStyle] — and both were
 * unreachable from the one place a desktop listener actually reads along. The full screen has a gear and the full
 * player has a gear; the panel, which is the view that is open all the time, had a fullscreen button and nothing
 * else, so the only route to "scale" or "focus" was the settings page. Asking for a feature that shipped is what
 * a feature with no control in sight looks like from outside.
 *
 * It opens the same dialog as the other two, with `isFullScreen = false`, so it edits the panel's own copy of
 * those settings rather than the full screen's — they are deliberately separate: a 16 sp line in a side panel and
 * a 42 sp headline do not want the same treatment.
 */
@Composable
private fun LyricsPreview(vm: PlayerViewModel, onOpenFullLyrics: () -> Unit) {
    var showQuickSettings by remember { mutableStateOf(false) }

    if (showQuickSettings) {
        com.alananasss.kittytune.ui.player.lyrics.QuickLyricsSettingsDialog(
            viewModel = vm,
            isFullScreen = false,
            isSidebar = true,
            onDismiss = { showQuickSettings = false },
        )
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Same glyph and same label as the gear on the full screen, because it opens the same dialog.
                Tip(str("pref_lyrics_title")) {
                    IconButton(
                        onClick = { showQuickSettings = true },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = str("pref_lyrics_title"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
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

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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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

            // Header: context name + close. Which tabs show is chosen in Appearance > Customize buttons; the gear
            // that also opened that menu here sat right next to the close button (issue #66).
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
                Tip(str("pref_lyrics_title"), instant = true) {
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
 * The tab row: one button per tab, drawn like the sidebar's destinations. The open tab has a filled icon on a
 * tonal pill with its name beside it; the others are outlined icons, named by a tooltip when the pointer rests
 * on them (issue #66). Material's tab row had them all in one style with an underline, and its tooltips
 * did not show.
 */
@Composable
private fun PanelTabRow(
    tabs: List<NowPlayingTab>,
    selected: NowPlayingTab,
    onTabChange: (NowPlayingTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            val label = panelTabLabel(tab)
            Box(
                modifier = Modifier.weight(if (isSelected) TAB_SELECTED_WEIGHT else 1f),
                contentAlignment = Alignment.Center,
            ) {
                Tip(label, enabled = !isSelected) {
                    PanelTabButton(tab, label, isSelected, onClick = { onTabChange(tab) })
                }
            }
        }
    }
}

@Composable
private fun PanelTabButton(tab: NowPlayingTab, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val container by animateColorAsState(
        when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else -> Color.Transparent
        },
        tween(TAB_ANIM_MS),
        label = "panelTabContainer",
    )
    val content by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(TAB_ANIM_MS),
        label = "panelTabContent",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TAB_HEIGHT)
            .clip(CircleShape)
            .background(container)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = label }
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crossfade(isSelected, animationSpec = tween(TAB_ANIM_MS), label = "panelTabIcon") { filled ->
            Icon(panelTabIcon(tab, filled), contentDescription = null, tint = content, modifier = Modifier.size(TAB_ICON_SIZE))
        }
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(tween(TAB_ANIM_MS)) + expandHorizontally(tween(TAB_ANIM_MS)),
            exit = fadeOut(tween(TAB_ANIM_MS / 2)) + shrinkHorizontally(tween(TAB_ANIM_MS)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(TAB_ICON_GAP))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The filled drawing for the open tab, the outlined one for the rest. */
private fun panelTabIcon(tab: NowPlayingTab, filled: Boolean): ImageVector = when (tab) {
    NowPlayingTab.TRACK -> if (filled) Icons.Filled.Album else Icons.Outlined.Album
    NowPlayingTab.QUEUE -> if (filled) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
    NowPlayingTab.LYRICS -> if (filled) Icons.Filled.Lyrics else Icons.Outlined.Lyrics
    NowPlayingTab.EFFECTS -> if (filled) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
}

@Composable
private fun panelTabLabel(tab: NowPlayingTab): String = when (tab) {
    NowPlayingTab.TRACK -> str("detail_track_title")
    NowPlayingTab.QUEUE -> str("player_queue")
    NowPlayingTab.LYRICS -> str("player_lyrics")
    NowPlayingTab.EFFECTS -> str("player_effects")
}

/** Reactive read of which panel tabs are hidden; recomposes on pref changes. */
@Composable
private fun rememberHiddenPanelTabs(): Set<String> {
    val prefsSnapshot by com.alananasss.kittytune.core.Prefs.flow.collectAsState()
    return remember(prefsSnapshot) {
        com.alananasss.kittytune.data.local.PlayerPreferences().getHiddenPanelTabs()
    }
}

private val TAB_ICON_SIZE = 20.dp
private val TAB_ICON_GAP = 8.dp
private val TAB_HEIGHT = 40.dp
private const val TAB_ANIM_MS = 220

/** How much more of the row the open tab takes, for its name. */
private const val TAB_SELECTED_WEIGHT = 2.6f

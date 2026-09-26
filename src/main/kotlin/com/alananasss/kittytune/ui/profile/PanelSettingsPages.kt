package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.InfoPanelHalf
import com.alananasss.kittytune.data.local.LibraryTileIcons
import com.alananasss.kittytune.data.local.MiniPlayerStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.SidebarNavEntry
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsSwitch
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.main.SidebarDestinations
import sh.calvin.reorderable.ReorderableColumn

/**
 * Interface → Left panel: how it behaves, which destinations it lists and in what order, and the
 * library's own buttons and tiles.
 */
@Composable
fun LeftPanelSettingsPage() {
    val prefs = remember { PlayerPreferences() }
    var hoverExpand by remember { mutableStateOf(prefs.isSidebarHoverExpandEnabled()) }
    var hiddenLibraryButtons by remember { mutableStateOf(prefs.getHiddenLibraryButtons()) }

    SettingsGroup(
        title = str("settings_group_behaviour"),
        items = listOf { shape ->
            SettingsItem(
                shape = shape,
                title = str("pref_sidebar_hover_expand"),
                subtitle = str("pref_sidebar_hover_expand_sub"),
                hasSwitch = true,
                switchState = hoverExpand,
                onSwitchChange = {
                    hoverExpand = it
                    prefs.setSidebarHoverExpandEnabled(it)
                },
            )
        },
    )

    SidebarLayoutEditor(prefs)

    SettingsGroup(
        title = str("customize_section_library"),
        items = listOf(
            PlayerPreferences.LIBRARY_BUTTON_CREATE to "lib_create",
            PlayerPreferences.LIBRARY_BUTTON_HISTORY to "history_title",
        ).map { (key, labelKey) ->
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str(labelKey),
                    hasSwitch = true,
                    switchState = key !in hiddenLibraryButtons,
                    onSwitchChange = { shown ->
                        hiddenLibraryButtons = if (shown) hiddenLibraryButtons - key else hiddenLibraryButtons + key
                        prefs.setHiddenLibraryButtons(hiddenLibraryButtons)
                    },
                )
            }
        },
    )

    LibraryTilesEditor(prefs)
}

/**
 * The sidebar's rows below Home: dragged by the handle to reorder, switched off to hide. Home is not listed —
 * it is where the app starts and cannot be moved or hidden.
 */
@Composable
private fun SidebarLayoutEditor(prefs: PlayerPreferences) {
    var layout by remember { mutableStateOf(prefs.getSidebarNavLayout()) }
    fun save(next: List<SidebarNavEntry>) {
        layout = next
        prefs.setSidebarNavLayout(next)
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SettingsGroupTitle(str("settings_group_sidebar_items"))
        Text(
            str("sidebar_items_hint"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
        )
        ReorderableColumn(
            list = layout,
            onSettle = { from, to -> save(layout.toMutableList().apply { add(to, removeAt(from)) }) },
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.clip(RoundedCornerShape(24.dp)),
        ) { index, entry, isDragging ->
            key(entry.key) {
                ReorderableItem {
                    val destination = SidebarDestinations.ALL[entry.key]
                    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "dragElevation")
                    Surface(
                        shape = if (isDragging) RoundedCornerShape(16.dp) else getSettingsShape(layout.size, index),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = elevation,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 4.dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                Icon(Icons.Rounded.DragIndicator, contentDescription = str("action_reorder"))
                            }
                            if (destination != null) {
                                Icon(
                                    destination.iconSelected,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(16.dp))
                            }
                            Text(
                                destination?.let { str(it.labelKey) } ?: entry.key,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val isLastOn = entry.isVisible && layout.count { it.isVisible } == 1
                            SettingsSwitch(
                                checked = entry.isVisible,
                                onCheckedChange = if (isLastOn) null else { shown ->
                                    save(layout.map { if (it.key == entry.key) it.copy(isVisible = shown) else it })
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The fixed library tiles: whether each is shown, and an optional picture of one's own for it. */
@Composable
private fun LibraryTilesEditor(prefs: PlayerPreferences) {
    var hidden by remember { mutableStateOf(prefs.getHiddenLibraryTiles()) }
    var icons by remember { mutableStateOf(PlayerPreferences.LIBRARY_TILES.associateWith { prefs.getLibraryTileIcon(it) }) }
    /** Set when a picked file turned out not to be an image; cleared by the next attempt. */
    var rejectedFile by remember { mutableStateOf(false) }
    // The tiles follow the palette while the dynamic theme is on; the previews match the library.
    val themed = prefs.getDynamicTheme()
    val scheme = MaterialTheme.colorScheme

    val tiles = listOf(
        LibraryTileLook(PlayerPreferences.LIBRARY_TILE_LIKES, "lib_liked_tracks", Icons.Rounded.Favorite,
            listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)), scheme.primaryContainer, scheme.onPrimaryContainer),
        LibraryTileLook(PlayerPreferences.LIBRARY_TILE_DOWNLOADS, "lib_downloads", Icons.Rounded.DownloadForOffline,
            listOf(Color(0xFF00C853), Color(0xFF69F0AE)), scheme.secondaryContainer, scheme.onSecondaryContainer),
        LibraryTileLook(PlayerPreferences.LIBRARY_TILE_LOCAL, "lib_local_media", Icons.Rounded.FolderOpen,
            listOf(Color(0xFF0091EA), Color(0xFF40C4FF)), scheme.tertiaryContainer, scheme.onTertiaryContainer),
    )

    SettingsGroup(
        title = str("pref_library_tiles"),
        items = tiles.map { look ->
            { shape ->
                val shown = look.key !in hidden
                Surface(shape = shape, color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibraryTilePreview(
                            iconPath = icons[look.key],
                            icon = look.icon,
                            fill = if (themed) Modifier.background(look.flat) else Modifier.background(Brush.linearGradient(look.gradient)),
                            tint = if (themed) look.onFlat else Color.White,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(str(look.labelKey), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val picked = pickImageFile(str("lib_tile_choose_icon")) ?: return@IconButton
                            val stored = LibraryTileIcons.import(look.key, picked)
                            rejectedFile = stored == null
                            if (stored != null) {
                                prefs.setLibraryTileIcon(look.key, stored)
                                icons = icons + (look.key to stored)
                            }
                        }) {
                            Icon(Icons.Outlined.Image, contentDescription = str("lib_tile_choose_icon"), modifier = Modifier.size(20.dp))
                        }
                        if (icons[look.key] != null) {
                            IconButton(onClick = {
                                LibraryTileIcons.clear(look.key)
                                prefs.setLibraryTileIcon(look.key, null)
                                icons = icons + (look.key to null)
                                rejectedFile = false
                            }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = str("lib_tile_reset_icon"), modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        SettingsSwitch(checked = shown, onCheckedChange = {
                            hidden = if (shown) hidden + look.key else hidden - look.key
                            prefs.setHiddenLibraryTiles(hidden)
                        })
                    }
                }
            }
        },
    )
    if (rejectedFile) {
        Text(
            str("lib_tile_icon_rejected"),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.error,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

private class LibraryTileLook(
    val key: String,
    val labelKey: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val flat: Color,
    val onFlat: Color,
)

/** One tile drawn the way the library draws it: the icon, or the imported image, over its fill. */
@Composable
private fun LibraryTilePreview(iconPath: String?, icon: ImageVector, fill: Modifier, tint: Color) {
    val file = iconPath?.let { path -> remember(path) { java.io.File(path) } }
    Box(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).then(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (file != null && file.isFile) {
            coil3.compose.AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Asks for an image file, or null when the dialog is dismissed. The filter is a hint the platform may ignore,
 * so the file is still validated afterwards — see [LibraryTileIcons.import].
 */
private fun pickImageFile(title: String): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp").any { name.endsWith(it, true) }
    }
    dialog.isVisible = true
    return dialog.files.firstOrNull()
}

/** Interface → Right panel: which tabs the now-playing panel has and which half it opens on. */
@Composable
fun RightPanelSettingsPage() {
    val prefs = remember { PlayerPreferences() }
    var hiddenTabs by remember { mutableStateOf(prefs.getHiddenPanelTabs()) }
    var infoPanelHalf by remember { mutableStateOf(prefs.getInfoPanelHalf()) }
    var showInfoHalfDialog by remember { mutableStateOf(false) }

    val halves = listOf(
        InfoPanelHalf.REMEMBER to str("pref_info_half_remember"),
        InfoPanelHalf.COMMENTS to str("menu_comments"),
        InfoPanelHalf.LYRICS to str("player_lyrics"),
    )
    if (showInfoHalfDialog) {
        ChoiceDialog(
            title = str("pref_info_half"),
            options = halves,
            selected = infoPanelHalf,
            onSelect = {
                infoPanelHalf = it
                prefs.setInfoPanelHalf(it)
            },
            onDismiss = { showInfoHalfDialog = false },
        )
    }

    SettingsGroup(
        title = str("panel_tabs_title"),
        items = listOf(
            Triple(PlayerPreferences.PANEL_TAB_TRACK, "detail_track_title", Icons.Rounded.Info),
            Triple(PlayerPreferences.PANEL_TAB_QUEUE, "player_queue", Icons.Rounded.QueueMusic),
            Triple(PlayerPreferences.PANEL_TAB_LYRICS, "player_lyrics", Icons.Rounded.Lyrics),
            Triple(PlayerPreferences.PANEL_TAB_EFFECTS, "player_effects", Icons.Rounded.Tune),
        ).map { (key, labelKey, icon) ->
            { shape ->
                val shown = key !in hiddenTabs
                // Never the last one: a panel with no tabs has nothing to show and no way back.
                val isLastShown = shown && hiddenTabs.size == 3
                SettingsItem(
                    shape = shape,
                    title = str(labelKey),
                    icon = icon,
                    hasSwitch = true,
                    switchState = shown,
                    onSwitchChange = if (isLastShown) null else { value ->
                        hiddenTabs = if (value) hiddenTabs - key else hiddenTabs + key
                        prefs.setHiddenPanelTabs(hiddenTabs)
                    },
                )
            }
        },
    )

    SettingsGroup(
        items = listOf { shape ->
            SettingsItem(
                shape = shape,
                title = str("pref_info_half"),
                subtitle = halves.first { it.first == infoPanelHalf }.second,
                onClick = { showInfoHalfDialog = true },
            )
        },
    )
}

/** Interface → Mini player: the same grouped cards as every other settings page. */
@Composable
fun MiniPlayerSettingsPage() {
    val prefs = remember { PlayerPreferences() }
    val snapshot by Prefs.flow.collectAsState()
    fun toggle(title: String, subtitle: String, checked: Boolean, set: (Boolean) -> Unit): @Composable (androidx.compose.ui.graphics.Shape) -> Unit = { shape ->
        SettingsItem(shape = shape, title = title, subtitle = subtitle, hasSwitch = true, switchState = checked, onSwitchChange = set)
    }
    val style = remember(snapshot) { prefs.getMiniPlayerStyle() }
    SettingsGroup(
        title = str("mini_player_section_look"),
        items = listOf(
            toggle(str("mini_player_style_elongated"), str("mini_player_style_elongated_desc"), style == MiniPlayerStyle.ELONGATED) {
                prefs.setMiniPlayerStyle(if (it) MiniPlayerStyle.ELONGATED else MiniPlayerStyle.STANDARD)
            },
            toggle(str("mini_player_transparent_bg"), str("mini_player_transparent_bg_desc"), remember(snapshot) { prefs.getMiniPlayerTransparentBg() }) { prefs.setMiniPlayerTransparentBg(it) },
            toggle(str("mini_player_hover_illumination"), str("mini_player_hover_illumination_desc"), remember(snapshot) { prefs.getMiniPlayerHoverIllumination() }) { prefs.setMiniPlayerHoverIllumination(it) },
        ),
    )
    SettingsGroup(
        title = str("mini_player_section_content"),
        items = listOf(
            toggle(str("mini_player_show_cover"), str("mini_player_show_cover_desc"), remember(snapshot) { prefs.getMiniPlayerShowCover() }) { prefs.setMiniPlayerShowCover(it) },
            toggle(str("mini_player_show_playback_controls"), str("mini_player_show_playback_controls_desc"), remember(snapshot) { prefs.getMiniPlayerShowPlaybackControls() }) { prefs.setMiniPlayerShowPlaybackControls(it) },
            toggle(str("mini_player_show_additional_controls"), str("mini_player_show_additional_controls_desc"), remember(snapshot) { prefs.getMiniPlayerShowAdditionalControls() }) { prefs.setMiniPlayerShowAdditionalControls(it) },
            toggle(str("mini_player_show_progress"), str("mini_player_show_progress_desc"), remember(snapshot) { prefs.getMiniPlayerShowProgress() }) { prefs.setMiniPlayerShowProgress(it) },
        ),
    )
    SettingsGroup(
        title = str("mini_player_section_behaviour"),
        items = listOf(
            toggle(str("mini_player_controls_on_hover"), str("mini_player_controls_on_hover_desc"), remember(snapshot) { prefs.getMiniPlayerControlsOnHover() }) { prefs.setMiniPlayerControlsOnHover(it) },
            toggle(str("mini_player_hover_effect"), str("mini_player_hover_effect_desc"), remember(snapshot) { prefs.getMiniPlayerHoverEffect() }) { prefs.setMiniPlayerHoverEffect(it) },
        ),
    )
}

/**
 * The mini player's settings in a bounded pane with a visible scrollbar, for the window the mini player's own
 * menu opens.
 */
@Composable
fun MiniPlayerSettingsList(prefs: PlayerPreferences, modifier: Modifier = Modifier) {
    com.alananasss.kittytune.ui.common.ScrollableColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(end = 12.dp),
    ) {
        MiniPlayerSettingsItems(prefs)
    }
}

/** The mini player's settings, grouped into look, content and behaviour. */
@Composable
private fun MiniPlayerSettingsItems(prefs: PlayerPreferences) {
    val prefsSnapshot by Prefs.flow.collectAsState()
    val style = remember(prefsSnapshot) { prefs.getMiniPlayerStyle() }
    val transparentBg = remember(prefsSnapshot) { prefs.getMiniPlayerTransparentBg() }
    val showCover = remember(prefsSnapshot) { prefs.getMiniPlayerShowCover() }
    val showPlayback = remember(prefsSnapshot) { prefs.getMiniPlayerShowPlaybackControls() }
    val showAdditional = remember(prefsSnapshot) { prefs.getMiniPlayerShowAdditionalControls() }
    val controlsOnHover = remember(prefsSnapshot) { prefs.getMiniPlayerControlsOnHover() }
    val hoverEffect = remember(prefsSnapshot) { prefs.getMiniPlayerHoverEffect() }
    val hoverIllumination = remember(prefsSnapshot) { prefs.getMiniPlayerHoverIllumination() }
    val showProgress = remember(prefsSnapshot) { prefs.getMiniPlayerShowProgress() }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MiniPlayerSectionTitle(str("mini_player_section_look"))
        SwitchRow(
            title = str("mini_player_style_elongated"),
            subtitle = str("mini_player_style_elongated_desc"),
            checked = style == MiniPlayerStyle.ELONGATED,
        ) {
            prefs.setMiniPlayerStyle(if (style == MiniPlayerStyle.ELONGATED) MiniPlayerStyle.STANDARD else MiniPlayerStyle.ELONGATED)
        }
        SwitchRow(str("mini_player_transparent_bg"), transparentBg, str("mini_player_transparent_bg_desc")) {
            prefs.setMiniPlayerTransparentBg(!transparentBg)
        }
        SwitchRow(str("mini_player_hover_illumination"), hoverIllumination, str("mini_player_hover_illumination_desc")) {
            prefs.setMiniPlayerHoverIllumination(!hoverIllumination)
        }

        MiniPlayerSectionTitle(str("mini_player_section_content"))
        SwitchRow(str("mini_player_show_cover"), showCover, str("mini_player_show_cover_desc")) {
            prefs.setMiniPlayerShowCover(!showCover)
        }
        SwitchRow(str("mini_player_show_playback_controls"), showPlayback, str("mini_player_show_playback_controls_desc")) {
            prefs.setMiniPlayerShowPlaybackControls(!showPlayback)
        }
        SwitchRow(str("mini_player_show_additional_controls"), showAdditional, str("mini_player_show_additional_controls_desc")) {
            prefs.setMiniPlayerShowAdditionalControls(!showAdditional)
        }
        SwitchRow(str("mini_player_show_progress"), showProgress, str("mini_player_show_progress_desc")) {
            prefs.setMiniPlayerShowProgress(!showProgress)
        }

        MiniPlayerSectionTitle(str("mini_player_section_behaviour"))
        SwitchRow(str("mini_player_controls_on_hover"), controlsOnHover, str("mini_player_controls_on_hover_desc")) {
            prefs.setMiniPlayerControlsOnHover(!controlsOnHover)
        }
        SwitchRow(str("mini_player_hover_effect"), hoverEffect, str("mini_player_hover_effect_desc")) {
            prefs.setMiniPlayerHoverEffect(!hoverEffect)
        }
    }
}

@Composable
private fun MiniPlayerSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}

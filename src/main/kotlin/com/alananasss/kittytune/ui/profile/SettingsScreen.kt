package com.alananasss.kittytune.ui.profile

import com.alananasss.kittytune.core.trackTextInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.player.PlayerViewModel

import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
/**
 * Settings, divided into categories instead of stacked into one page (issue #33).
 *
 * "I also think you can make settings with tabs, not all together as it is now, but on top there will be category
 * tabs where everything is divided, that is, Appearance, Audio & Playback, Lyrics, and so on."
 *
 * Every category already existed as its own composable — the page merely embedded all nine of them one after
 * another, so it was about ten screens tall and finding the lyrics display style meant scrolling past the whole of
 * appearance and audio. That is not a small annoyance: the same reporter asked for the lyrics "scale and focus"
 * modes in the same breath, and those have shipped for two releases. A setting nobody can find has not shipped.
 *
 * So the sections are the same sections, and only the navigation between them changed. Nothing was renamed and
 * nothing was dropped: the three thin ones that are a single row leading to a screen of their own — import, sync,
 * proxy — keep that row, and [SettingsSection.SOURCES] is the one grouping, because "where music comes from" is
 * one question and it was being asked in three places.
 *
 * Each tab keeps its own scroll position, so leaving Audio half-way down and coming back lands where you were
 * rather than at wherever the last tab happened to sit.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    onBackClick: (() -> Unit)? = null,
    playerViewModel: PlayerViewModel
) {
    val sections = SettingsSection.entries
    var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val index = selected.coerceIn(sections.indices)
    val scrollStates = sections.map { androidx.compose.foundation.rememberScrollState() }

    SettingsScaffold(title = str("settings_title"), onBackClick = onBackClick) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            SettingsTabs(sections = sections, selectedIndex = index, onSelect = { selected = it })

            com.alananasss.kittytune.ui.common.ScrollableColumn(
                modifier = Modifier.fillMaxSize(),
                state = scrollStates[index],
                contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
            ) {
                when (sections[index]) {
                    SettingsSection.APPEARANCE -> AppearanceSettingsScreen(
                        onNavigateToColors = { navController.navigate("color_palette") },
                        onBackClick = null,
                    )

                    SettingsSection.AUDIO -> AudioSettingsScreen(
                        onBackClick = null,
                        playerViewModel = playerViewModel,
                    )

                    SettingsSection.LYRICS -> LyricsSettingsScreen(
                        onBackClick = null,
                        playerViewModel = playerViewModel,
                    )

                    SettingsSection.SOURCES -> SourcesSection(navController)

                    SettingsSection.SYNC -> SyncSection(navController)

                    SettingsSection.DISCORD -> DiscordSettingsScreen(
                        onBackClick = null,
                        onNavigateToLogin = { navController.navigate("discord_login") },
                        playerViewModel = playerViewModel,
                    )

                    SettingsSection.STORAGE -> StorageSettingsScreen()

                    SettingsSection.NETWORK -> NetworkSection(navController)
                }
            }
        }
    }
}

/** The categories, in the order they are worth opening. Each one names itself from the strings it already had. */
private enum class SettingsSection(val titleKey: String, val icon: ImageVector) {
    APPEARANCE("pref_appearance_title", Icons.Rounded.Palette),
    AUDIO("pref_audio_title", Icons.Rounded.GraphicEq),
    LYRICS("pref_lyrics_title", Icons.Rounded.TextSnippet),
    SOURCES("settings_tab_sources", Icons.Rounded.ImportExport),
    SYNC("sync_title", Icons.Rounded.Sync),
    DISCORD("pref_discord_title", Icons.Rounded.Forum),
    STORAGE("pref_storage_title", Icons.Rounded.Storage),
    NETWORK("pref_proxy_title", Icons.Rounded.Dns),
}

/**
 * The category switcher.
 *
 * A real Material 3 [ButtonGroup] rather than a tab row, which buys three things a `SecondaryScrollableTabRow`
 * cannot:
 *
 *  - **Overflow instead of sideways scrolling.** Eight categories do not fit a narrow window, and a tab strip's
 *    answer is to scroll horizontally — which nobody discovers unless they already suspect it is there. The button
 *    group measures its children and moves whatever will not fit into a dropdown behind one trailing button, so
 *    every category stays reachable *and* visibly so at any window width.
 *  - **Labels at their real width.** The app's own [com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup]
 *    divides the row evenly, which for eight items is eight truncated words. Items here are unweighted, so each
 *    button is as wide as its own label and "Audio & Playback" survives in all four translations.
 *  - **The expressive press.** Pressing a category grows it and compresses its neighbours, which is the
 *    interaction the app's other segmented controls already have and a tab row has nothing like.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsTabs(
    sections: List<SettingsSection>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    // Guard against zero/tiny-width constraints that arrive during AnimatedContent exit
    // transitions.  ButtonGroup's internal measure policy subtracts spacing and overflow-
    // indicator width from the incoming maxWidth *before* calling Constraints.copy, so even
    // a clamped-to-1-px value goes negative and violates Constraints invariants.  When the
    // container is that narrow, the content is being cross-faded out and is invisible anyway,
    // so we can safely skip measurement and place nothing.
    ButtonGroup(
        overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
            .layout { measurable, constraints ->
                // fillMaxWidth() pins minWidth == maxWidth.  ButtonGroup's measure policy then
                // subtracts overflow-indicator + inter-item spacing from maxWidth *without*
                // touching minWidth, so minWidth > maxWidth → IllegalArgumentException.
                // Relaxing minWidth to 0 keeps the layout full-width (ButtonGroup still
                // receives the original maxWidth) while letting its internal copy() succeed.
                val placeable = measurable.measure(
                    constraints.copy(minWidth = 0, minHeight = 0)
                )
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            },
    ) {
        sections.forEachIndexed { position, section ->
            toggleableItem(
                checked = position == selectedIndex,
                label = str(section.titleKey),
                // The group hands the overflow menu `!checked`, which for a switcher is meaningless — pressing a
                // category selects it, and pressing the selected one again is not a request to select nothing.
                onCheckedChange = { onSelect(position) },
                icon = {
                    Icon(section.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

/**
 * Where music comes from that is not SoundCloud's stream: an import, a borrowed catalogue, and the disk.
 *
 * The three were three separate headings on the old page, and they are one question. The Yandex token sits here
 * rather than under a "sources" of its own because that is all it is — a credential the user brings so their
 * catalogue can be read. Nothing here unlocks playback: Yandex serves audio only through a signing scheme lifted
 * out of their own client, which is not something to reproduce, so a Yandex hit always plays from wherever else
 * the song exists (issue #33).
 */
@Composable
private fun SourcesSection(navController: NavController) {
    SettingsGroup(
        title = str("music_import_title"),
        items = listOf(
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("music_import_title"),
                    subtitle = str("music_import_settings_subtitle"),
                    onClick = { navController.navigate("music_import") }
                )
            }
        )
    )

    Spacer(Modifier.height(24.dp))

    var showYandexTokenDialog by remember { mutableStateOf(false) }
    val yandexConnected = com.alananasss.kittytune.data.yandex.YandexMusicClient.isConnected
    SettingsGroup(
        title = str("pref_yandex_token"),
        items = listOf(
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_yandex_token"),
                    subtitle = if (yandexConnected) str("pref_yandex_token_sub")
                    else str("yandex_not_connected"),
                    onClick = { showYandexTokenDialog = true },
                )
            }
        )
    )
    if (showYandexTokenDialog) {
        YandexTokenDialog(onDismiss = { showYandexTokenDialog = false })
    }

    Spacer(Modifier.height(24.dp))

    // The one part of this tab with a screenful of its own content, so it keeps the heading it had.
    MainCategoryTitle(str("pref_local_title"), Icons.Filled.SdStorage)
    LocalMediaSettingsScreen(onBackClick = null)
}

/**
 * Pairing with another device.
 *
 * One row, deliberately: the sync screen behind it is a screen, and this is the entry that makes it reachable
 * without a menu. The subtitle says what is actually happening rather than naming the listener switch, so the
 * state is legible without opening it (issue #33).
 */
@Composable
private fun SyncSection(navController: NavController) {
    SettingsGroup(
        title = str("sync_title"),
        items = listOf(
            { shape ->
                val devices = com.alananasss.kittytune.data.sync.SyncPeers.all()
                SettingsItem(
                    shape = shape,
                    title = str("sync_title"),
                    subtitle = if (devices.isEmpty()) {
                        str("sync_state_not_paired_sub")
                    } else {
                        str("sync_state_in_step")
                    },
                    onClick = { navController.navigate("sync_settings") }
                )
            }
        )
    )
}

/** The proxy, whose own screen is long enough to deserve one and short enough to reach in one row. */
@Composable
private fun NetworkSection(navController: NavController) {
    SettingsGroup(
        title = str("pref_proxy_title"),
        items = listOf(
            { shape ->
                val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences() }
                val isProxyEnabled = prefs.getProxyEnabled()
                val proxySubtitle = if (isProxyEnabled) {
                    str("proxy_status_enabled", prefs.getProxyType(), prefs.getProxyHost().ifBlank { "127.0.0.1" }, prefs.getProxyPort())
                } else {
                    str("proxy_status_disabled")
                }
                SettingsItem(
                    shape = shape,
                    title = str("proxy_settings_title"),
                    subtitle = proxySubtitle,
                    onClick = { navController.navigate("proxy_settings") }
                )
            }
        )
    )
}

@Composable
fun MainCategoryTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Where the Yandex Music token is pasted (issue #33).
 *
 * ## Why a paste box and not a sign-in button
 *
 * "Possibilité de se connecter via le web aussi ?" — and the honest answer is not yet. A browser sign-in needs
 * a registered Yandex application's id and secret, and the only ones lying around belong to other projects.
 * Shipping somebody else's OAuth client is the same objection as shipping Apple's developer token out of their
 * APK, so this asks for the token instead, which is what their own API documentation describes and what every
 * third-party client does.
 *
 * The link goes to that documentation. If a Yandex application is ever registered for KittyTune, filling in
 * `YandexMusicClient.OAUTH_CLIENT_ID` turns this dialog into a sign-in button and nothing else has to change.
 *
 * The field is masked while it is at rest and legible while it is being typed, because a token is a password
 * that people paste and then need to check they pasted correctly.
 */
@Composable
private fun YandexTokenDialog(onDismiss: () -> Unit) {
    val client = com.alananasss.kittytune.data.yandex.YandexMusicClient
    var value by remember { mutableStateOf(client.token.orEmpty()) }
    var reveal by remember { mutableStateOf(false) }

    com.alananasss.kittytune.core.BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(str("pref_yandex_token"), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    str("pref_yandex_token_sub"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.trim() },
                    singleLine = true,
                    label = { Text(str("pref_yandex_token")) },
                    visualTransformation =
                        if (reveal) androidx.compose.ui.text.input.VisualTransformation.None
                        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(
                                if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().trackTextInput(),
                )

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        runCatching {
                            java.awt.Desktop.getDesktop()
                                .browse(java.net.URI(com.alananasss.kittytune.data.yandex.YandexMusicClient.TOKEN_HELP_URL))
                        }
                    }
                ) { Text(str("pref_yandex_token_get")) }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = {
                            client.token = null
                            value = ""
                            onDismiss()
                        }
                    ) { Text(str("pref_yandex_token_clear")) }
                    TextButton(
                        onClick = {
                            client.token = value
                            onDismiss()
                        }
                    ) { Text(str("btn_save")) }
                }
            }
        }
    }
}

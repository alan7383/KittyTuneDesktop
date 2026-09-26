package com.alananasss.kittytune.ui.profile

import com.alananasss.kittytune.core.trackTextInput
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.ui.player.PlayerViewModel

import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.pressScale
import androidx.compose.ui.graphics.vector.ImageVector

/** Material's emphasized-decelerate curve: arrives quickly and settles. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val PAGE_MS = 300

/** Below this width the category list shrinks to icons with their labels underneath. */
private val WIDE_LAYOUT = 760.dp

/**
 * Settings as two panes: the categories on the left, the chosen one on the right.
 *
 * Categories that hold more than one screenful — Interface above all — open sub-pages inside the right pane
 * rather than new screens, so the list of categories stays where it is and a back arrow (or Escape, or the
 * mouse's back button) walks back out. Moving deeper slides the page along the horizontal axis; switching
 * category fades through, which is Material's split between "going into" and "going somewhere else".
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    onBackClick: (() -> Unit)? = null,
    playerViewModel: PlayerViewModel
) {
    val location = SettingsNavigation.current

    com.alananasss.kittytune.core.BackHandler(enabled = location.depth > 0) { SettingsNavigation.up() }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isWide = maxWidth >= WIDE_LAYOUT
        Row(Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
            CategoryList(
                selected = location.category,
                isWide = isWide,
                onSelect = { SettingsNavigation.go(SettingsPlace(it)) },
                onCredits = { navController.navigate("credits") },
            )
            Spacer(Modifier.width(8.dp))
            AnimatedContent(
                targetState = location,
                transitionSpec = {
                    if (initialState.category == targetState.category) {
                        val forward = targetState.depth > initialState.depth
                        val slide = { full: Int -> (full * 0.12f).toInt() }
                        (slideInHorizontally(tween(PAGE_MS, easing = EmphasizedDecelerate)) { if (forward) slide(it) else -slide(it) } +
                            fadeIn(tween(PAGE_MS / 2, delayMillis = PAGE_MS / 6))) togetherWith
                            (slideOutHorizontally(tween(PAGE_MS, easing = EmphasizedDecelerate)) { if (forward) -slide(it) else slide(it) } +
                                fadeOut(tween(PAGE_MS / 3)))
                    } else {
                        (fadeIn(tween(210, delayMillis = 90)) + scaleIn(tween(210, delayMillis = 90), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(90))
                    }
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "settingsPage",
            ) { shown ->
                SettingsPane(
                    title = str(shown.subPage?.titleKey ?: shown.category.titleKey),
                    onBack = if (shown.subPage != null) ({ SettingsNavigation.up() }) else onBackClick,
                ) {
                    SettingsPageContent(
                        location = shown,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onOpen = { page -> SettingsNavigation.go(shown.copy(pages = shown.pages + page)) },
                    )
                }
            }
        }
    }
}

/** The categories, in the order they are worth opening. */
internal enum class SettingsCategory(val titleKey: String, val icon: ImageVector) {
    INTERFACE("settings_cat_interface", Icons.Rounded.Palette),
    AUDIO("settings_cat_audio", Icons.Rounded.GraphicEq),
    SOURCES("settings_tab_sources", Icons.Rounded.ImportExport),
    STORAGE("pref_storage_title", Icons.Rounded.Storage),
    SYNC("settings_cat_sync", Icons.Rounded.Devices),
    NETWORK("pref_proxy_title", Icons.Rounded.Dns),
    MISC("settings_cat_misc", Icons.Rounded.Tune),
}

/** Pages opened inside a category. */
internal enum class SettingsSubPage(val titleKey: String, val subtitleKey: String? = null, val icon: ImageVector? = null) {
    THEMES("settings_page_themes", "settings_page_themes_sub", Icons.Rounded.ColorLens),
    PLAYER("settings_page_player", "settings_page_player_sub", Icons.Rounded.PlayCircle),
    LEFT_PANEL("settings_page_left_panel", "settings_page_left_panel_sub", Icons.Rounded.ViewSidebar),
    RIGHT_PANEL("settings_page_right_panel", "settings_page_right_panel_sub", Icons.Rounded.ViewQuilt),
    LYRICS("pref_lyrics_title", "settings_page_lyrics_sub", Icons.Rounded.Lyrics),
    MINI_PLAYER("mini_player_settings_title", "settings_page_mini_player_sub", Icons.Rounded.PictureInPicture),
    CUSTOM_THEME("pref_custom_theme"),
    LYRICS_FULLSCREEN("lyrics_mode_fullscreen"),
    LYRICS_CENTRAL("lyrics_mode_central"),
    LYRICS_SIDEBAR("lyrics_mode_sidebar"),
}

private val interfacePages = listOf(
    SettingsSubPage.THEMES,
    SettingsSubPage.PLAYER,
    SettingsSubPage.LEFT_PANEL,
    SettingsSubPage.RIGHT_PANEL,
    SettingsSubPage.LYRICS,
    SettingsSubPage.MINI_PLAYER,
)

@Composable
private fun SettingsPageContent(
    location: SettingsPlace,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    onOpen: (SettingsSubPage) -> Unit,
) {
    when (location.subPage) {
        null -> when (location.category) {
            SettingsCategory.INTERFACE -> SettingsGroup(
                items = interfacePages.map { page ->
                    { shape ->
                        SettingsItem(
                            shape = shape,
                            title = str(page.titleKey),
                            subtitle = page.subtitleKey?.let { str(it) },
                            icon = page.icon,
                            onClick = { onOpen(page) },
                        )
                    }
                },
            )
            SettingsCategory.AUDIO -> AudioSettingsScreen(onBackClick = null, playerViewModel = playerViewModel)
            SettingsCategory.SOURCES -> {
                SourcesSection(navController)
                LyricsSettingsScreen(playerViewModel = playerViewModel, page = LyricsSettingsPage.SOURCES)
            }
            SettingsCategory.STORAGE -> StorageSettingsScreen()
            SettingsCategory.SYNC -> SyncSettingsContent()
            SettingsCategory.NETWORK -> NetworkSection(navController)
            SettingsCategory.MISC -> MiscSettingsPage(navController, playerViewModel)
        }
        SettingsSubPage.THEMES -> ThemesSettingsPage(onOpenCustomTheme = { onOpen(SettingsSubPage.CUSTOM_THEME) })
        SettingsSubPage.CUSTOM_THEME -> ColorPaletteContent()
        SettingsSubPage.PLAYER -> PlayerDesignSettingsPage()
        SettingsSubPage.LEFT_PANEL -> LeftPanelSettingsPage()
        SettingsSubPage.RIGHT_PANEL -> RightPanelSettingsPage()
        SettingsSubPage.MINI_PLAYER -> MiniPlayerSettingsPage()
        SettingsSubPage.LYRICS -> LyricsSettingsScreen(
            playerViewModel = playerViewModel,
            page = LyricsSettingsPage.OVERVIEW,
            onOpenPage = { page ->
                when (page) {
                    LyricsSettingsPage.FULLSCREEN -> onOpen(SettingsSubPage.LYRICS_FULLSCREEN)
                    LyricsSettingsPage.CENTRAL -> onOpen(SettingsSubPage.LYRICS_CENTRAL)
                    LyricsSettingsPage.SIDEBAR -> onOpen(SettingsSubPage.LYRICS_SIDEBAR)
                    else -> Unit
                }
            },
        )
        SettingsSubPage.LYRICS_FULLSCREEN -> LyricsSettingsScreen(playerViewModel = playerViewModel, page = LyricsSettingsPage.FULLSCREEN)
        SettingsSubPage.LYRICS_CENTRAL -> LyricsSettingsScreen(playerViewModel = playerViewModel, page = LyricsSettingsPage.CENTRAL)
        SettingsSubPage.LYRICS_SIDEBAR -> LyricsSettingsScreen(playerViewModel = playerViewModel, page = LyricsSettingsPage.SIDEBAR)
    }
}

/** The right pane: a title row, with a back arrow on sub-pages, over the page's own scrolling content. */
@Composable
private fun SettingsPane(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (onBack != null) 4.dp else 20.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("btn_back"))
                }
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        com.alananasss.kittytune.ui.common.ScrollableColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberScrollState(),
            contentPadding = PaddingValues(bottom = 80.dp),
            content = content,
        )
    }
}

/**
 * The categories. Wide, a list of labelled rows on Material's navigation-drawer pill; narrow, a rail of icons
 * with their labels underneath, so every category stays one click away at any window width.
 */
@Composable
private fun CategoryList(
    selected: SettingsCategory,
    isWide: Boolean,
    onSelect: (SettingsCategory) -> Unit,
    onCredits: () -> Unit,
) {
    Column(
        Modifier.width(if (isWide) 240.dp else 92.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (isWide) {
            Text(
                str("settings_title"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 18.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        SettingsCategory.entries.forEach { entry ->
            CategoryItem(
                label = str(entry.titleKey),
                icon = entry.icon,
                isSelected = entry == selected,
                isWide = isWide,
                onClick = { onSelect(entry) },
            )
        }
        Spacer(Modifier.weight(1f))
        CategoryItem(
            label = str("about_credits"),
            icon = Icons.Rounded.Groups,
            isSelected = false,
            isWide = isWide,
            onClick = onCredits,
        )
    }
}

@Composable
private fun CategoryItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isWide: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0f),
        label = "categoryPill",
    )
    val content = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(if (isWide) 28.dp else 16.dp),
        color = container,
        contentColor = content,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().pressScale(interaction),
    ) {
        if (isWide) {
            Row(
                Modifier.height(52.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences() }
    val order = prefs.getAudioProviderOrder()
    val orderSummary = order.joinToString(", ") { item ->
        when (item) {
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.QOBUZ -> "Qobuz"
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.TIDAL -> "TIDAL"
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.DEEZER -> "Deezer"
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.YOUTUBE_MUSIC -> "YouTube Music"
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.SOUNDCLOUD -> "SoundCloud"
        }
    }

    val qobuzInstances = prefs.getQobuzCustomInstances().split("\n").filter { it.isNotBlank() }
    val qobuzSubtitle = if (qobuzInstances.isEmpty() || prefs.getQobuzCustomInstances() == com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.DEFAULT_INSTANCE) {
        "${prefs.getQobuzCountry()} • ${str("qobuz_custom_instances_desc_default")}"
    } else {
        "${prefs.getQobuzCountry()} • ${str("qobuz_custom_instances_desc_custom", qobuzInstances.size)}"
    }

    val tidalQuality = prefs.getTidalAudioQuality()
    val tidalSubtitle = when (tidalQuality) {
        com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.AAC_320 -> str("tidal_quality_aac_320")
        com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.FLAC -> str("tidal_quality_flac")
        com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.HI_RES_LOSSLESS -> str("tidal_quality_hires")
    }

    val deezerQuality = prefs.getDeezerAudioQuality()
    val deezerSubtitle = when (deezerQuality) {
        com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.FLAC -> str("deezer_quality_flac")
        com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_320 -> str("deezer_quality_mp3_320")
        com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_128 -> str("deezer_quality_mp3_128")
    }

    // Every service in one list, each saying whether it is connected, the way an accounts page does: the
    // Yandex token used to sit in a group of its own, as if it were a different kind of thing.
    var showYandexTokenDialog by remember { mutableStateOf(false) }
    val yandexConnected = com.alananasss.kittytune.data.yandex.YandexMusicClient.isConnected
    val soundCloudSignedIn = !com.alananasss.kittytune.data.TokenManager.isGuestMode() &&
        !com.alananasss.kittytune.data.TokenManager.getAccessToken().isNullOrBlank()
    if (showYandexTokenDialog) YandexTokenDialog(onDismiss = { showYandexTokenDialog = false })

    SettingsGroup(
        title = str("sources_services_title"),
        items = listOf(
            { shape ->
                ServiceRow(
                    shape = shape,
                    name = "SoundCloud",
                    subtitle = str(if (soundCloudSignedIn) "sources_signed_in" else "sources_guest"),
                    iconRes = com.alananasss.kittytune.R.drawable.ic_logo_soundcloud,
                    isConnected = soundCloudSignedIn,
                    onClick = { navController.navigate("profile") },
                )
            },
            { shape ->
                ServiceRow(shape, "Qobuz", qobuzSubtitle, com.alananasss.kittytune.R.drawable.ic_logo_qobuz, isConnected = true) {
                    navController.navigate("qobuz_settings")
                }
            },
            { shape ->
                ServiceRow(shape, "TIDAL", tidalSubtitle, com.alananasss.kittytune.R.drawable.ic_logo_tidal, isConnected = true) {
                    navController.navigate("tidal_settings")
                }
            },
            { shape ->
                ServiceRow(shape, "Deezer", deezerSubtitle, com.alananasss.kittytune.R.drawable.ic_logo_deezer, isConnected = true) {
                    navController.navigate("deezer_settings")
                }
            },
            { shape ->
                ServiceRow(
                    shape = shape,
                    name = str("sources_yandex"),
                    subtitle = str(if (yandexConnected) "pref_yandex_token_sub" else "yandex_not_connected"),
                    iconRes = null,
                    isConnected = yandexConnected,
                    onClick = { showYandexTokenDialog = true },
                )
            },
        ),
    )

    SettingsGroup(
        title = str("sources_playback_title"),
        items = listOf(
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("provider_order"),
                    subtitle = orderSummary,
                    icon = Icons.Rounded.SwapVert,
                    onClick = { navController.navigate("provider_order") },
                )
            },
            { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("music_import_title"),
                    subtitle = str("music_import_settings_subtitle"),
                    icon = Icons.Rounded.ImportExport,
                    onClick = { navController.navigate("music_import") },
                )
            },
        ),
    )

    Spacer(Modifier.height(24.dp))

    // The one part of this tab with a screenful of its own content, so it keeps the heading it had.
    MainCategoryTitle(str("pref_local_title"), Icons.Filled.SdStorage)
    LocalMediaSettingsScreen(onBackClick = null)
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
    ZapretSection()
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


/** A service in the sources list: its logo, what state it is in, and a "connected" mark. */
@Composable
private fun ServiceRow(
    shape: androidx.compose.ui.graphics.Shape,
    name: String,
    subtitle: String,
    iconRes: String?,
    isConnected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (iconRes != null) {
                        Icon(androidx.compose.ui.res.painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
                    } else {
                        Text(name.take(1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    str(if (isConnected) "sources_connected" else "sources_connect"),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

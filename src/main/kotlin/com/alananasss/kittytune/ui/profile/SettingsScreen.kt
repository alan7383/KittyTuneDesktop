package com.alananasss.kittytune.ui.profile

import com.alananasss.kittytune.core.trackTextInput
import com.alananasss.kittytune.ui.common.escapeDismisses
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
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
    var searchQuery by rememberSaveable { mutableStateOf("") }

    com.alananasss.kittytune.core.BackHandler(enabled = searchQuery.isNotEmpty()) { searchQuery = "" }
    com.alananasss.kittytune.core.BackHandler(enabled = searchQuery.isEmpty() && location.depth > 0) { SettingsNavigation.up() }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isWide = maxWidth >= WIDE_LAYOUT
        Row(Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
            CategoryList(
                selected = location.category,
                isWide = isWide,
                onSelect = {
                    searchQuery = ""
                    SettingsNavigation.go(SettingsPlace(it))
                },
                onCredits = {
                    searchQuery = ""
                    navController.navigate("credits")
                },
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
                    location = shown,
                    title = str(shown.subPage?.titleKey ?: shown.category.titleKey),
                    onBack = if (shown.subPage != null) ({ SettingsNavigation.up() }) else onBackClick,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                ) {
                    if (searchQuery.isNotBlank()) {
                        SettingsSearchResults(
                            query = searchQuery,
                            navController = navController,
                            playerViewModel = playerViewModel,
                            onNavigateToPlace = { place ->
                                searchQuery = ""
                                SettingsNavigation.go(place)
                            },
                        )
                    } else {
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
    // Interface
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

    // Audio
    AUDIO_PLAYBACK("settings_cat_playback", "settings_audio_playback_sub", Icons.Rounded.PlayArrow),
    AUDIO_QUALITY("settings_audio_quality_title", "settings_audio_quality_sub", Icons.Rounded.GraphicEq),
    AUDIO_TRANSITIONS("settings_audio_transitions_title", "settings_audio_transitions_sub", Icons.Rounded.AutoAwesome),
    AUDIO_SLEEP_TIMER("sleep_timer_title", "settings_audio_sleep_sub", Icons.Rounded.Timer),

    // Sources
    SOURCES_SERVICES("sources_services_title", "sources_services_sub", Icons.Rounded.CloudQueue),
    SOURCES_ORDER("sources_playback_title", "sources_order_sub", Icons.Rounded.SwapVert),
    SOURCES_LYRICS("lyrics_sources_title", "lyrics_sources_sub", Icons.Rounded.Lyrics),

    // Storage
    STORAGE_CACHE("storage_group_cache", "storage_cache_sub", Icons.Rounded.OfflineBolt),
    STORAGE_DOWNLOADS("pref_storage_downloads", "storage_downloads_sub", Icons.Rounded.DownloadDone),
    STORAGE_LOCAL_FILES("local_media_title", "storage_local_files_sub", Icons.Rounded.FolderCopy),
    STORAGE_BACKUP("storage_group_backup", "storage_backup_sub", Icons.Rounded.Backup),

    // Sync
    SYNC_DEVICES("sync_paired_devices_title", "sync_paired_devices_sub", Icons.Rounded.Devices),
    SYNC_OPTIONS("sync_options_title", "sync_options_sub", Icons.Rounded.Sync),
    SYNC_ADVANCED("sync_advanced_title", "sync_advanced_sub", Icons.Rounded.SettingsEthernet),

    // Network
    NETWORK_PROXY("pref_proxy_title", "network_proxy_sub", Icons.Rounded.Dns),
    NETWORK_ZAPRET("zapret_title", "network_zapret_sub", Icons.Rounded.Security),

    // Misc
    MISC_GENERAL("settings_cat_general", "misc_general_sub", Icons.Rounded.Tune),
    MISC_DISCORD("pref_discord_title", "misc_discord_sub", Icons.Rounded.Forum),
}

private val interfacePages = listOf(
    SettingsSubPage.THEMES,
    SettingsSubPage.PLAYER,
    SettingsSubPage.LEFT_PANEL,
    SettingsSubPage.RIGHT_PANEL,
    SettingsSubPage.LYRICS,
    SettingsSubPage.MINI_PLAYER,
)

private val audioPages = listOf(
    SettingsSubPage.AUDIO_PLAYBACK,
    SettingsSubPage.AUDIO_QUALITY,
    SettingsSubPage.AUDIO_TRANSITIONS,
    SettingsSubPage.AUDIO_SLEEP_TIMER,
)

private val sourcesPages = listOf(
    SettingsSubPage.SOURCES_SERVICES,
    SettingsSubPage.SOURCES_ORDER,
    SettingsSubPage.SOURCES_LYRICS,
)

private val storagePages = listOf(
    SettingsSubPage.STORAGE_CACHE,
    SettingsSubPage.STORAGE_DOWNLOADS,
    SettingsSubPage.STORAGE_LOCAL_FILES,
    SettingsSubPage.STORAGE_BACKUP,
)

private val syncPages = listOf(
    SettingsSubPage.SYNC_DEVICES,
    SettingsSubPage.SYNC_OPTIONS,
    SettingsSubPage.SYNC_ADVANCED,
)

private val networkPages = listOf(
    SettingsSubPage.NETWORK_PROXY,
    SettingsSubPage.NETWORK_ZAPRET,
)

private val miscPages = listOf(
    SettingsSubPage.MISC_GENERAL,
    SettingsSubPage.MISC_DISCORD,
)

@Composable
private fun CategoryFolderGroup(
    pages: List<SettingsSubPage>,
    onOpen: (SettingsSubPage) -> Unit,
) {
    SettingsGroup(
        items = pages.map { page ->
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
}

@Composable
private fun SettingsPageContent(
    location: SettingsPlace,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    onOpen: (SettingsSubPage) -> Unit,
) {
    when (location.subPage) {
        null -> when (location.category) {
            SettingsCategory.INTERFACE -> CategoryFolderGroup(interfacePages, onOpen)
            SettingsCategory.AUDIO -> CategoryFolderGroup(audioPages, onOpen)
            SettingsCategory.SOURCES -> CategoryFolderGroup(sourcesPages, onOpen)
            SettingsCategory.STORAGE -> CategoryFolderGroup(storagePages, onOpen)
            SettingsCategory.SYNC -> CategoryFolderGroup(syncPages, onOpen)
            SettingsCategory.NETWORK -> CategoryFolderGroup(networkPages, onOpen)
            SettingsCategory.MISC -> CategoryFolderGroup(miscPages, onOpen)
        }
        // Interface subpages
        SettingsSubPage.THEMES -> ThemesSettingsPage(onOpenCustomTheme = { onOpen(SettingsSubPage.CUSTOM_THEME) })
        SettingsSubPage.CUSTOM_THEME -> ColorPaletteContent()
        SettingsSubPage.PLAYER -> PlayerDesignSettingsPage(playerViewModel)
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

        // Audio subpages
        SettingsSubPage.AUDIO_PLAYBACK -> AudioPlaybackPage(playerViewModel)
        SettingsSubPage.AUDIO_QUALITY -> AudioQualityPage(playerViewModel)
        SettingsSubPage.AUDIO_TRANSITIONS -> AudioTransitionsPage(playerViewModel)
        SettingsSubPage.AUDIO_SLEEP_TIMER -> AudioSleepTimerPage()

        // Sources subpages
        SettingsSubPage.SOURCES_SERVICES -> SourcesServicesPage(navController)
        SettingsSubPage.SOURCES_ORDER -> SourcesOrderPage(navController)
        SettingsSubPage.SOURCES_LYRICS -> LyricsSettingsScreen(playerViewModel = playerViewModel, page = LyricsSettingsPage.SOURCES)

        // Storage subpages
        SettingsSubPage.STORAGE_CACHE -> StorageCachePage()
        SettingsSubPage.STORAGE_DOWNLOADS -> StorageDownloadsPage()
        SettingsSubPage.STORAGE_LOCAL_FILES -> LocalMediaSettingsScreen(onBackClick = null)
        SettingsSubPage.STORAGE_BACKUP -> StorageBackupPage()

        // Sync subpages
        SettingsSubPage.SYNC_DEVICES -> SyncDevicesPage()
        SettingsSubPage.SYNC_OPTIONS -> SyncOptionsPage()
        SettingsSubPage.SYNC_ADVANCED -> SyncAdvancedPage()

        // Network subpages
        SettingsSubPage.NETWORK_PROXY -> ProxySettingsScreen(onBackClick = null)
        SettingsSubPage.NETWORK_ZAPRET -> ZapretSection()

        // Misc subpages
        SettingsSubPage.MISC_GENERAL -> MiscGeneralPage()
        SettingsSubPage.MISC_DISCORD -> MiscDiscordPage(navController, playerViewModel)
    }
}

/** The right pane: a title row with search field, with a back arrow on sub-pages, over the page's own scrolling content. */
@Composable
private fun SettingsPane(
    location: SettingsPlace,
    title: String,
    onBack: (() -> Unit)?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(
                    start = if (onBack != null && searchQuery.isEmpty()) 4.dp else 16.dp,
                    end = 16.dp,
                    top = 6.dp,
                    bottom = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null && searchQuery.isEmpty()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = str("btn_back"))
                }
            }
            Text(
                if (searchQuery.isNotBlank()) str("settings_search_results") else title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(str("search_settings_hint"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .width(320.dp)
                    .trackTextInput()
                    .escapeDismisses {
                        onSearchQueryChange("")
                        focusManager.clearFocus()
                    },
            )
        }
        com.alananasss.kittytune.ui.common.ScrollableColumn(
            modifier = Modifier.fillMaxSize(),
            state = androidx.compose.runtime.key(location) { rememberScrollState() },
            contentPadding = PaddingValues(bottom = 80.dp),
            content = content,
        )
    }
}

internal data class SettingsSearchItem(
    val title: String,
    val subtitle: String? = null,
    val category: SettingsCategory,
    val place: SettingsPlace,
    val icon: ImageVector? = null,
    val route: String? = null,
    val keywords: List<String> = emptyList(),
    val hasSwitch: Boolean = false,
    val switchState: Boolean = false,
    val onSwitchChange: ((Boolean) -> Unit)? = null,
    val highlightKey: String? = null,
)

@Composable
private fun getSearchableSettings(playerViewModel: PlayerViewModel): List<SettingsSearchItem> {
    val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences() }
    var animatedArtistProfiles by remember { mutableStateOf(prefs.getAnimatedArtistProfilesEnabled()) }
    var animatedCovers by remember { mutableStateOf(prefs.getAnimatedCoversEnabled()) }
    var animatedCoversFadeUi by remember { mutableStateOf(prefs.getAnimatedCoversFadeUiEnabled()) }
    var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
    var followsCover by remember { mutableStateOf(prefs.getTrackDynamicTheme()) }
    var showHomeListeningStats by remember { mutableStateOf(prefs.getShowHomeListeningStats()) }
    var showHomeYourMix by remember { mutableStateOf(prefs.getShowHomeYourMix()) }
    var sidebarHoverExpand by remember { mutableStateOf(prefs.isSidebarHoverExpandEnabled()) }
    var verticalVolume by remember { mutableStateOf(prefs.getVerticalVolumeSlider()) }
    var showRemainingTime by remember { mutableStateOf(prefs.getShowRemainingTime()) }
    var crossfade by remember { mutableStateOf(prefs.getCrossfadeEnabled()) }
    var automix by remember { mutableStateOf(prefs.getAutomixEnabled()) }
    var autoplay by remember { mutableStateOf(prefs.getAutoplayEnabled()) }
    var continuousPlayback by remember { mutableStateOf(prefs.getContinuousPlaybackEnabled()) }
    var persistentQueue by remember { mutableStateOf(prefs.getPersistentQueueEnabled()) }
    var savePosition by remember { mutableStateOf(prefs.getSavePositionEnabled()) }
    var youtubeFallback by remember { mutableStateOf(prefs.getYouTubeFallbackEnabled()) }
    var localMedia by remember { mutableStateOf(prefs.getLocalMediaEnabled()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var discordRpc by remember { mutableStateOf(prefs.getDiscordRpcEnabled()) }

    return listOf(
        // INTERFACE - Pages
        SettingsSearchItem(str("settings_cat_interface"), null, SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE), Icons.Rounded.Palette, keywords = listOf("interface", "ui", "look", "appearance", "интерфейс", "внешний вид")),
        SettingsSearchItem(str("settings_page_themes"), str("settings_page_themes_sub"), SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES)), Icons.Rounded.ColorLens, keywords = listOf("theme", "color", "dark", "light", "palette", "oled", "amoled", "thème", "couleur", "тема", "цвета")),
        SettingsSearchItem(str("pref_custom_theme"), null, SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES, SettingsSubPage.CUSTOM_THEME)), Icons.Rounded.Palette, keywords = listOf("custom theme", "accent", "colors", "personnalisé", "кастомная тема")),
        SettingsSearchItem(str("settings_page_player"), str("settings_page_player_sub"), SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.PLAYER)), Icons.Rounded.PlayCircle, keywords = listOf("player", "artwork", "fluid", "blur", "lecteur", "плеер", "дизайн")),
        SettingsSearchItem(str("settings_page_left_panel"), str("settings_page_left_panel_sub"), SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.LEFT_PANEL)), Icons.Rounded.ViewSidebar, keywords = listOf("sidebar", "left panel", "navigation", "panneau gauche", "боковая панель")),
        SettingsSearchItem(str("settings_page_right_panel"), str("settings_page_right_panel_sub"), SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.RIGHT_PANEL)), Icons.Rounded.ViewQuilt, keywords = listOf("right panel", "queue", "info", "panneau droit", "правая панель")),
        SettingsSearchItem(str("pref_lyrics_title"), str("settings_page_lyrics_sub"), SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.LYRICS)), Icons.Rounded.Lyrics, keywords = listOf("lyrics", "words", "karaoke", "paroles", "текст песен", "караоке")),
        SettingsSearchItem(str("lyrics_mode_fullscreen"), null, SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.LYRICS, SettingsSubPage.LYRICS_FULLSCREEN)), Icons.Rounded.Fullscreen, keywords = listOf("fullscreen", "lyrics", "plein écran", "полноэкранный")),
        SettingsSearchItem(str("lyrics_mode_central"), null, SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.LYRICS, SettingsSubPage.LYRICS_CENTRAL)), Icons.Rounded.VerticalAlignCenter, keywords = listOf("central", "lyrics", "centre", "центральный")),
        SettingsSearchItem(str("lyrics_mode_sidebar"), null, SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.LYRICS, SettingsSubPage.LYRICS_SIDEBAR)), Icons.Rounded.ViewSidebar, keywords = listOf("sidebar lyrics", "боковой текст")),
        SettingsSearchItem(str("mini_player_settings_title"), str("settings_page_mini_player_sub"), SettingsCategory.INTERFACE, SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.MINI_PLAYER)), Icons.Rounded.PictureInPicture, keywords = listOf("mini player", "pip", "always on top", "мини плеер")),

        // INTERFACE - Direct Options
        SettingsSearchItem(
            title = str("pref_animated_artist_profiles"),
            subtitle = str("pref_animated_artist_profiles_desc"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES)),
            icon = Icons.Rounded.AccountCircle,
            keywords = listOf("artist", "artiste", "artistes", "profile", "profiles", "profil", "profils", "anime", "animé", "animés", "animated", "video", "анимированные профили"),
            hasSwitch = true,
            switchState = animatedArtistProfiles,
            onSwitchChange = {
                animatedArtistProfiles = it
                prefs.setAnimatedArtistProfilesEnabled(it)
            },
            highlightKey = "pref_animated_artist_profiles",
        ),
        SettingsSearchItem(
            title = str("pref_animated_covers"),
            subtitle = str("pref_animated_covers_desc"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES)),
            icon = Icons.Rounded.PlayCircle,
            keywords = listOf("animated cover", "covers animées", "pochette animée", "pochettes animées", "anime", "animé", "animated", "video", "анимированные обложки"),
            hasSwitch = true,
            switchState = animatedCovers,
            onSwitchChange = {
                animatedCovers = it
                prefs.setAnimatedCoversEnabled(it)
            },
            highlightKey = "pref_animated_covers",
        ),
        SettingsSearchItem(
            title = str("pref_animated_covers_fade_ui"),
            subtitle = str("pref_animated_covers_fade_ui_desc"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES)),
            icon = Icons.Rounded.Opacity,
            keywords = listOf("fade", "fondu", "transparence", "ui", "fade ui"),
            hasSwitch = true,
            switchState = animatedCoversFadeUi,
            onSwitchChange = {
                animatedCoversFadeUi = it
                prefs.setAnimatedCoversFadeUiEnabled(it)
            },
            highlightKey = "pref_animated_covers_fade_ui",
        ),
        SettingsSearchItem(
            title = str("pref_theme_pure_black"),
            subtitle = str("pref_theme_pure_black_sub"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES)),
            icon = Icons.Rounded.Contrast,
            keywords = listOf("pure black", "noir pur", "oled", "amoled", "dark", "noir", "чистый черный"),
            hasSwitch = true,
            switchState = pureBlack,
            onSwitchChange = {
                pureBlack = it
                prefs.setPureBlack(it)
            },
            highlightKey = "pref_theme_pure_black",
        ),
        SettingsSearchItem(
            title = str("pref_dynamic_theme_merged"),
            subtitle = str("pref_dynamic_theme_merged_sub"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES)),
            icon = Icons.Rounded.AutoAwesome,
            keywords = listOf("dynamic theme", "thème dynamique", "couleurs dynamiques", "palette", "cover color", "динамическая тема"),
            hasSwitch = true,
            switchState = followsCover,
            onSwitchChange = {
                followsCover = it
                prefs.setTrackDynamicTheme(it)
                prefs.setDynamicTheme(it)
            },
            highlightKey = "pref_dynamic_theme",
        ),
        SettingsSearchItem(
            title = str("listening_stats_title"),
            subtitle = str("pref_home_listening_stats_desc"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES)),
            icon = Icons.Rounded.BarChart,
            keywords = listOf("listening stats", "stats", "statistics", "home", "cards", "statistiques", "écoute", "accueil", "cartes", "статистика", "главный экран"),
            hasSwitch = true,
            switchState = showHomeListeningStats,
            onSwitchChange = {
                showHomeListeningStats = it
                prefs.setShowHomeListeningStats(it)
            },
            highlightKey = "pref_home_listening_stats",
        ),
        SettingsSearchItem(
            title = str("mix_title"),
            subtitle = str("pref_home_your_mix_desc"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.THEMES)),
            icon = Icons.Rounded.AutoAwesome,
            keywords = listOf("mix", "your mix", "personalized", "home", "cards", "ton mix", "personnalisé", "accueil", "cartes", "микс", "персональный микс"),
            hasSwitch = true,
            switchState = showHomeYourMix,
            onSwitchChange = {
                showHomeYourMix = it
                prefs.setShowHomeYourMix(it)
            },
            highlightKey = "pref_home_your_mix",
        ),
        SettingsSearchItem(
            title = str("pref_sidebar_hover_expand"),
            subtitle = str("pref_sidebar_hover_expand_sub"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.LEFT_PANEL)),
            icon = Icons.Rounded.ViewSidebar,
            keywords = listOf("sidebar", "hover", "survol", "déplier", "expand", "раскрывать при наведении"),
            hasSwitch = true,
            switchState = sidebarHoverExpand,
            onSwitchChange = {
                sidebarHoverExpand = it
                prefs.setSidebarHoverExpandEnabled(it)
            },
            highlightKey = "pref_sidebar_hover_expand",
        ),
        SettingsSearchItem(
            title = str("pref_vertical_volume_slider"),
            subtitle = null,
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.PLAYER)),
            icon = Icons.Rounded.VolumeUp,
            keywords = listOf("volume", "vertical", "slider", "curseur", "barre de son", "вертикальный слайдер громкости"),
            hasSwitch = true,
            switchState = verticalVolume,
            onSwitchChange = {
                verticalVolume = it
                prefs.setVerticalVolumeSlider(it)
            },
            highlightKey = "pref_vertical_volume_slider",
        ),
        SettingsSearchItem(
            title = str("pref_show_remaining_time"),
            subtitle = str("pref_show_remaining_time_desc"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.PLAYER)),
            icon = Icons.Rounded.Timer,
            keywords = listOf("remaining", "time", "strip", "duration", "countdown", "restant", "temps restant", "décompte", "оставшееся время", "обратный отсчет"),
            hasSwitch = true,
            switchState = showRemainingTime,
            onSwitchChange = {
                showRemainingTime = it
                prefs.setShowRemainingTime(it)
            },
            highlightKey = "pref_show_remaining_time",
        ),
        SettingsSearchItem(
            title = str("pref_screensaver_title"),
            subtitle = run {
                val sec = playerViewModel.fullPlayerScreensaverTimeoutSeconds
                val formatted = when {
                    sec < 60 -> "${sec}s"
                    sec % 60 == 0 -> "${sec / 60} min"
                    else -> "${sec / 60}m ${sec % 60}s"
                }
                val template = str("pref_screensaver_desc")
                val regex = Regex("""60\s*(s|sec|сек|с|mp|giây)?""", RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(template)) template.replace(regex, formatted) else "$template ($formatted)"
            },
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.PLAYER)),
            icon = Icons.Rounded.DarkMode,
            keywords = listOf("screensaver", "écran de veille", "veille", "sleep", "dim", "fullscreen", "plein écran", "заставка", "экранная заставка"),
            hasSwitch = true,
            switchState = playerViewModel.fullPlayerScreensaverEnabled,
            onSwitchChange = { playerViewModel.updateFullPlayerScreensaverEnabled(it) },
            highlightKey = "pref_screensaver",
        ),
        SettingsSearchItem(
            title = str("pref_full_player_source_title"),
            subtitle = str("pref_full_player_source_desc"),
            category = SettingsCategory.INTERFACE,
            place = SettingsPlace(SettingsCategory.INTERFACE, listOf(SettingsSubPage.PLAYER)),
            icon = Icons.Rounded.GraphicEq,
            keywords = listOf("source", "soundcloud", "youtube", "logo", "badge", "fullscreen", "plein écran", "titre", "point"),
            hasSwitch = true,
            switchState = playerViewModel.fullPlayerSourceIndicatorEnabled,
            onSwitchChange = { playerViewModel.updateFullPlayerSourceIndicatorEnabled(it) },
            highlightKey = "pref_full_player_source",
        ),

        // AUDIO - Folders & Direct Options
        SettingsSearchItem(str("settings_cat_audio"), null, SettingsCategory.AUDIO, SettingsPlace(SettingsCategory.AUDIO), Icons.Rounded.GraphicEq, keywords = listOf("audio", "sound", "son", "звук")),
        SettingsSearchItem(str("settings_cat_playback"), str("settings_audio_playback_sub"), SettingsCategory.AUDIO, SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_PLAYBACK)), Icons.Rounded.PlayArrow, keywords = listOf("playback", "autoplay", "queue", "воспроизведение")),
        SettingsSearchItem(str("settings_audio_quality_title"), str("settings_audio_quality_sub"), SettingsCategory.AUDIO, SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_QUALITY)), Icons.Rounded.GraphicEq, keywords = listOf("quality", "sound", "output", "bitrate", "high", "flac", "качество")),
        SettingsSearchItem(str("settings_audio_transitions_title"), str("settings_audio_transitions_sub"), SettingsCategory.AUDIO, SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_TRANSITIONS)), Icons.Rounded.AutoAwesome, keywords = listOf("transitions", "crossfade", "automix", "dj", "mix")),
        SettingsSearchItem(str("sleep_timer_title"), str("settings_audio_sleep_sub"), SettingsCategory.AUDIO, SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_SLEEP_TIMER)), Icons.Rounded.Timer, keywords = listOf("timer", "sleep", "fade", "таймер сна")),
        SettingsSearchItem(str("pref_quality"), null, SettingsCategory.AUDIO, SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_QUALITY)), Icons.Rounded.Tune, keywords = listOf("quality", "bitrate", "high", "low", "flac", "aac", "качество")),
        SettingsSearchItem(str("pref_audio_device_title"), null, SettingsCategory.AUDIO, SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_QUALITY)), Icons.Rounded.Speaker, keywords = listOf("device", "speaker", "output", "устройство", "вывод")),
        SettingsSearchItem(str("pref_seek_wheel_step"), null, SettingsCategory.AUDIO, SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_PLAYBACK)), Icons.Rounded.FastForward, keywords = listOf("seek", "wheel", "колесико", "перемотка")),

        SettingsSearchItem(
            title = str("pref_norm_title"),
            subtitle = str("pref_norm_sub"),
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_QUALITY)),
            icon = Icons.Rounded.VolumeUp,
            keywords = listOf("normalization", "volume", "loudness", "gain", "normalisation", "громкость", "нормализация"),
            hasSwitch = true,
            switchState = playerViewModel.effectsState.isNormalizationEnabled,
            onSwitchChange = { playerViewModel.toggleNormalization(it) },
            highlightKey = "pref_norm",
        ),
        SettingsSearchItem(
            title = str("pref_crossfade_title"),
            subtitle = null,
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_TRANSITIONS)),
            icon = Icons.Rounded.Shuffle,
            keywords = listOf("crossfade", "fade", "transition", "fondu", "enchaîné", "кроссфейд", "плавный переход"),
            hasSwitch = true,
            switchState = crossfade,
            onSwitchChange = {
                crossfade = it
                prefs.setCrossfadeEnabled(it)
            },
            highlightKey = "pref_crossfade",
        ),
        SettingsSearchItem(
            title = str(com.alananasss.kittytune.R.string.automix),
            subtitle = str(com.alananasss.kittytune.R.string.automix_desc),
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_TRANSITIONS)),
            icon = Icons.Rounded.AutoAwesome,
            keywords = listOf("automix", "dj", "tempo", "harmonic", "transition", "mix", "автомикс"),
            hasSwitch = true,
            switchState = automix,
            onSwitchChange = {
                automix = it
                prefs.setAutomixEnabled(it)
            },
            highlightKey = "pref_automix",
        ),
        SettingsSearchItem(
            title = str("pref_autoplay"),
            subtitle = str("pref_autoplay_sub"),
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_PLAYBACK)),
            icon = Icons.Rounded.PlayArrow,
            keywords = listOf("autoplay", "lecture automatique", "station", "radio", "enchaîner", "автовоспроизведение"),
            hasSwitch = true,
            switchState = autoplay,
            onSwitchChange = {
                autoplay = it
                prefs.setAutoplayEnabled(it)
            },
            highlightKey = "pref_autoplay",
        ),
        SettingsSearchItem(
            title = str("pref_continuous_playback"),
            subtitle = str("pref_continuous_playback_sub"),
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_PLAYBACK)),
            icon = Icons.Rounded.Repeat,
            keywords = listOf("continuous", "lecture continue", "suite", "track", "непрерывное воспроизведение"),
            hasSwitch = true,
            switchState = continuousPlayback,
            onSwitchChange = {
                continuousPlayback = it
                prefs.setContinuousPlaybackEnabled(it)
            },
            highlightKey = "pref_continuous_playback",
        ),
        SettingsSearchItem(
            title = str("pref_persist_queue"),
            subtitle = str("pref_persist_queue_sub"),
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_PLAYBACK)),
            icon = Icons.Rounded.QueueMusic,
            keywords = listOf("queue", "persist", "file d'attente", "garder", "sauvegarder", "сохранять очередь"),
            hasSwitch = true,
            switchState = persistentQueue,
            onSwitchChange = {
                persistentQueue = it
                prefs.setPersistentQueueEnabled(it)
            },
            highlightKey = "pref_persist_queue",
        ),
        SettingsSearchItem(
            title = str("pref_queue_preserve_upcoming"),
            subtitle = str("pref_queue_preserve_upcoming_sub"),
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_PLAYBACK)),
            icon = Icons.Rounded.SkipNext,
            keywords = listOf("queue", "jump", "preserve", "saut", "titres suivants", "file"),
            hasSwitch = true,
            switchState = playerViewModel.isQueuePreserveUpcomingEnabled,
            onSwitchChange = { playerViewModel.toggleQueuePreserveUpcoming(it) },
            highlightKey = "pref_queue_preserve_upcoming",
        ),
        SettingsSearchItem(
            title = str("pref_save_position"),
            subtitle = str("pref_save_position_sub"),
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_PLAYBACK)),
            icon = Icons.Rounded.Save,
            keywords = listOf("position", "save position", "reprendre", "sauvegarder la position", "сохранять позицию"),
            hasSwitch = true,
            switchState = savePosition,
            onSwitchChange = {
                savePosition = it
                prefs.setSavePositionEnabled(it)
            },
            highlightKey = "pref_save_position",
        ),
        SettingsSearchItem(
            title = str("pref_youtube_fallback"),
            subtitle = str("pref_youtube_fallback_sub"),
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_PLAYBACK)),
            icon = Icons.Rounded.SmartDisplay,
            keywords = listOf("youtube", "fallback", "secours", "alternative", "ютуб"),
            hasSwitch = true,
            switchState = youtubeFallback,
            onSwitchChange = {
                youtubeFallback = it
                prefs.setYouTubeFallbackEnabled(it)
            },
            highlightKey = "pref_youtube_fallback",
        ),
        SettingsSearchItem(
            title = str("pref_precise_speed"),
            subtitle = str("pref_precise_speed_sub"),
            category = SettingsCategory.AUDIO,
            place = SettingsPlace(SettingsCategory.AUDIO, listOf(SettingsSubPage.AUDIO_PLAYBACK)),
            icon = Icons.Rounded.Speed,
            keywords = listOf("speed", "vitesse", "précise", "tempo", "rate", "точная скорость"),
            hasSwitch = true,
            switchState = playerViewModel.isPreciseSpeedEnabled,
            onSwitchChange = { playerViewModel.togglePreciseSpeedEnabled(it) },
            highlightKey = "pref_precise_speed",
        ),

        // SOURCES - Folders & Options
        SettingsSearchItem(str("settings_tab_sources"), null, SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES), Icons.Rounded.ImportExport, keywords = listOf("sources", "providers", "streaming", "источники", "сервисы")),
        SettingsSearchItem(str("sources_services_title"), str("sources_services_sub"), SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_SERVICES)), Icons.Rounded.CloudQueue, keywords = listOf("sources", "streaming", "soundcloud", "qobuz", "tidal", "deezer", "yandex")),
        SettingsSearchItem(str("sources_playback_title"), str("sources_order_sub"), SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_ORDER)), Icons.Rounded.SwapVert, keywords = listOf("priority", "provider order", "import", "music import")),
        SettingsSearchItem(str("lyrics_sources_title"), str("lyrics_sources_sub"), SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_LYRICS)), Icons.Rounded.Lyrics, keywords = listOf("lyrics", "lyrics providers", "paroles", "karaoke")),
        SettingsSearchItem("SoundCloud", str("sources_signed_in"), SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_SERVICES)), Icons.Rounded.Cloud, keywords = listOf("soundcloud", "саундклауд")),
        SettingsSearchItem("Qobuz", null, SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_SERVICES)), Icons.Rounded.MusicNote, route = "qobuz_settings", keywords = listOf("qobuz", "flac", "hires", "кобуз")),
        SettingsSearchItem("TIDAL", null, SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_SERVICES)), Icons.Rounded.Waves, route = "tidal_settings", keywords = listOf("tidal", "hifi", "тайдал")),
        SettingsSearchItem("Deezer", null, SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_SERVICES)), Icons.Rounded.GraphicEq, route = "deezer_settings", keywords = listOf("deezer", "дизер")),
        SettingsSearchItem(str("sources_yandex"), str("pref_yandex_token"), SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_SERVICES)), Icons.Rounded.Key, keywords = listOf("yandex", "token", "яндекс", "токен")),
        SettingsSearchItem(str("provider_order"), null, SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_ORDER)), Icons.Rounded.SwapVert, route = "provider_order", keywords = listOf("provider order", "priority", "ordre", "приоритет", "порядок")),
        SettingsSearchItem(str("music_import_title"), str("music_import_settings_subtitle"), SettingsCategory.SOURCES, SettingsPlace(SettingsCategory.SOURCES, listOf(SettingsSubPage.SOURCES_ORDER)), Icons.Rounded.ImportExport, route = "music_import", keywords = listOf("import", "spotify", "playlist import", "импорт")),

        // STORAGE - Folders & Options
        SettingsSearchItem(str("pref_storage_title"), null, SettingsCategory.STORAGE, SettingsPlace(SettingsCategory.STORAGE), Icons.Rounded.Storage, keywords = listOf("storage", "cache", "clear cache", "disk", "stockage", "память", "кэш", "очистить")),
        SettingsSearchItem(str("storage_group_cache"), str("storage_cache_sub"), SettingsCategory.STORAGE, SettingsPlace(SettingsCategory.STORAGE, listOf(SettingsSubPage.STORAGE_CACHE)), Icons.Rounded.OfflineBolt, keywords = listOf("cache", "audio cache", "image cache", "clean cache")),
        SettingsSearchItem(str("pref_storage_downloads"), str("storage_downloads_sub"), SettingsCategory.STORAGE, SettingsPlace(SettingsCategory.STORAGE, listOf(SettingsSubPage.STORAGE_DOWNLOADS)), Icons.Rounded.DownloadDone, keywords = listOf("downloads", "downloaded", "téléchargements")),
        SettingsSearchItem(str("local_media_title"), str("storage_local_files_sub"), SettingsCategory.STORAGE, SettingsPlace(SettingsCategory.STORAGE, listOf(SettingsSubPage.STORAGE_LOCAL_FILES)), Icons.Rounded.FolderCopy, keywords = listOf("local", "storage", "disk", "folder", "directory", "локальные", "папка", "диск")),
        SettingsSearchItem(str("storage_group_backup"), str("storage_backup_sub"), SettingsCategory.STORAGE, SettingsPlace(SettingsCategory.STORAGE, listOf(SettingsSubPage.STORAGE_BACKUP)), Icons.Rounded.Backup, keywords = listOf("backup", "export", "import", "sauvegarde")),
        SettingsSearchItem(
            title = str("pref_local_title"),
            subtitle = null,
            category = SettingsCategory.STORAGE,
            place = SettingsPlace(SettingsCategory.STORAGE, listOf(SettingsSubPage.STORAGE_LOCAL_FILES)),
            icon = Icons.Filled.SdStorage,
            keywords = listOf("local", "storage", "disk", "folder", "directory", "локальные", "папка", "диск"),
            hasSwitch = true,
            switchState = localMedia,
            onSwitchChange = {
                localMedia = it
                prefs.setLocalMediaEnabled(it)
            },
        ),

        // SYNC - Folders
        SettingsSearchItem(str("settings_cat_sync"), null, SettingsCategory.SYNC, SettingsPlace(SettingsCategory.SYNC), Icons.Rounded.Devices, keywords = listOf("sync", "account", "cloud", "export", "import", "синхронизация", "аккаунт", "облако")),
        SettingsSearchItem(str("sync_paired_devices_title"), str("sync_paired_devices_sub"), SettingsCategory.SYNC, SettingsPlace(SettingsCategory.SYNC, listOf(SettingsSubPage.SYNC_DEVICES)), Icons.Rounded.Devices, keywords = listOf("paired devices", "pair", "connect", "appareil", "appareils")),
        SettingsSearchItem(str("sync_options_title"), str("sync_options_sub"), SettingsCategory.SYNC, SettingsPlace(SettingsCategory.SYNC, listOf(SettingsSubPage.SYNC_OPTIONS)), Icons.Rounded.Sync, keywords = listOf("sync data", "listens", "likes", "données")),
        SettingsSearchItem(str("sync_advanced_title"), str("sync_advanced_sub"), SettingsCategory.SYNC, SettingsPlace(SettingsCategory.SYNC, listOf(SettingsSubPage.SYNC_ADVANCED)), Icons.Rounded.SettingsEthernet, keywords = listOf("sync advanced", "port", "listener", "address", "firewall")),

        // NETWORK - Folders
        SettingsSearchItem(str("pref_proxy_title"), null, SettingsCategory.NETWORK, SettingsPlace(SettingsCategory.NETWORK), Icons.Rounded.Dns, keywords = listOf("proxy", "network", "zapret", "сеть")),
        SettingsSearchItem(str("proxy_settings_title"), str("network_proxy_sub"), SettingsCategory.NETWORK, SettingsPlace(SettingsCategory.NETWORK, listOf(SettingsSubPage.NETWORK_PROXY)), Icons.Rounded.Dns, keywords = listOf("proxy", "vpn", "socks", "http", "network", "прокси", "сеть")),
        SettingsSearchItem(str("zapret_title"), str("network_zapret_sub"), SettingsCategory.NETWORK, SettingsPlace(SettingsCategory.NETWORK, listOf(SettingsSubPage.NETWORK_ZAPRET)), Icons.Rounded.Security, keywords = listOf("zapret", "dpi", "block", "circumvention", "запрет", "блокировки")),

        // MISC - Folders & Options
        SettingsSearchItem(str("settings_cat_misc"), null, SettingsCategory.MISC, SettingsPlace(SettingsCategory.MISC), Icons.Rounded.Tune, keywords = listOf("misc", "general", "options", "разное", "общие")),
        SettingsSearchItem(str("settings_cat_general"), str("misc_general_sub"), SettingsCategory.MISC, SettingsPlace(SettingsCategory.MISC, listOf(SettingsSubPage.MISC_GENERAL)), Icons.Rounded.Tune, keywords = listOf("general", "language", "start screen", "update")),
        SettingsSearchItem(str("pref_discord_title"), str("misc_discord_sub"), SettingsCategory.MISC, SettingsPlace(SettingsCategory.MISC, listOf(SettingsSubPage.MISC_DISCORD)), Icons.Rounded.Forum, keywords = listOf("discord", "rpc", "presence", "дискорд", "статус")),
        SettingsSearchItem(str("pref_language"), null, SettingsCategory.MISC, SettingsPlace(SettingsCategory.MISC, listOf(SettingsSubPage.MISC_GENERAL)), Icons.Rounded.Translate, keywords = listOf("language", "lang", "locale", "langue", "язык", "локализация")),
        SettingsSearchItem(str("pref_start_screen"), null, SettingsCategory.MISC, SettingsPlace(SettingsCategory.MISC, listOf(SettingsSubPage.MISC_GENERAL)), Icons.Rounded.Home, keywords = listOf("start screen", "home", "library", "стартовый экран")),
        SettingsSearchItem(
            title = str("pref_auto_update"),
            subtitle = str("pref_auto_update_subtitle"),
            category = SettingsCategory.MISC,
            place = SettingsPlace(SettingsCategory.MISC, listOf(SettingsSubPage.MISC_GENERAL)),
            icon = Icons.Rounded.SystemUpdate,
            keywords = listOf("update", "version", "mise à jour", "обновление", "auto update"),
            hasSwitch = true,
            switchState = autoUpdate,
            onSwitchChange = {
                autoUpdate = it
                prefs.setAutoUpdateEnabled(it)
            },
            highlightKey = "pref_auto_update",
        ),
        SettingsSearchItem(
            title = str("pref_discord_title"),
            subtitle = null,
            category = SettingsCategory.MISC,
            place = SettingsPlace(SettingsCategory.MISC, listOf(SettingsSubPage.MISC_DISCORD)),
            icon = Icons.Rounded.Forum,
            keywords = listOf("discord", "rpc", "presence", "дискорд", "статус"),
            hasSwitch = true,
            switchState = discordRpc,
            onSwitchChange = {
                discordRpc = it
                prefs.setDiscordRpcEnabled(it)
            },
            highlightKey = "pref_discord",
        ),
        SettingsSearchItem(str("about_credits"), null, SettingsCategory.MISC, SettingsPlace(SettingsCategory.MISC), Icons.Rounded.Groups, route = "credits", keywords = listOf("credits", "about", "authors", "team", "crédits", "о программе", "авторы")),
    )
}

@Composable
private fun SettingsSearchResults(
    query: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    onNavigateToPlace: (SettingsPlace) -> Unit,
) {
    val items = getSearchableSettings(playerViewModel)
    val trimmed = query.trim()
    val matches = remember(trimmed, items) {
        items.filter { item ->
            val catName = str(item.category.titleKey)
            item.title.contains(trimmed, ignoreCase = true) ||
                (item.subtitle != null && item.subtitle.contains(trimmed, ignoreCase = true)) ||
                catName.contains(trimmed, ignoreCase = true) ||
                item.keywords.any { it.contains(trimmed, ignoreCase = true) }
        }
    }

    if (matches.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.SearchOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    str("settings_search_no_results"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "\"$trimmed\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    } else {
        val grouped = matches.groupBy { it.category }
        grouped.forEach { (category, groupItems) ->
            SettingsGroup(
                title = str(category.titleKey),
                items = groupItems.map { item ->
                    { shape ->
                        SettingsItem(
                            shape = shape,
                            title = item.title,
                            subtitle = item.subtitle ?: str(item.category.titleKey),
                            icon = item.icon ?: item.category.icon,
                            hasSwitch = item.hasSwitch,
                            switchState = item.switchState,
                            onSwitchChange = item.onSwitchChange,
                            onClick = {
                                if (item.highlightKey != null) {
                                    com.alananasss.kittytune.ui.common.SettingsHighlightManager.setHighlightKey(item.highlightKey)
                                }
                                if (item.route != null) {
                                    onNavigateToPlace(item.place)
                                    navController.navigate(item.route)
                                } else {
                                    onNavigateToPlace(item.place)
                                }
                            },
                        )
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
        }
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
private fun SourcesServicesPage(navController: NavController) {
    val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences() }
    val disabledProviders = prefs.getDisabledAudioProviders()
    val disabledSuffix = str("provider_disabled_suffix")

    val qobuzDisabled = com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.QOBUZ in disabledProviders
    val tidalDisabled = com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.TIDAL in disabledProviders
    val deezerDisabled = com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.DEEZER in disabledProviders

    val qobuzInstances = prefs.getQobuzCustomInstances().split("\n").filter { it.isNotBlank() }
    val baseQobuzSubtitle = if (qobuzInstances.isEmpty() || prefs.getQobuzCustomInstances() == com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.DEFAULT_INSTANCE) {
        "${prefs.getQobuzCountry()} • ${str("qobuz_custom_instances_desc_default")}"
    } else {
        "${prefs.getQobuzCountry()} • ${str("qobuz_custom_instances_desc_custom", qobuzInstances.size)}"
    }
    val qobuzSubtitle = if (qobuzDisabled) "$disabledSuffix • $baseQobuzSubtitle" else baseQobuzSubtitle

    val tidalQuality = prefs.getTidalAudioQuality()
    val baseTidalSubtitle = when (tidalQuality) {
        com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.AAC_320 -> str("tidal_quality_aac_320")
        com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.FLAC -> str("tidal_quality_flac")
        com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality.HI_RES_LOSSLESS -> str("tidal_quality_hires")
    }
    val tidalSubtitle = if (tidalDisabled) "$disabledSuffix • $baseTidalSubtitle" else baseTidalSubtitle

    val deezerQuality = prefs.getDeezerAudioQuality()
    val baseDeezerSubtitle = when (deezerQuality) {
        com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.FLAC -> str("deezer_quality_flac")
        com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_320 -> str("deezer_quality_mp3_320")
        com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality.MP3_128 -> str("deezer_quality_mp3_128")
    }
    val deezerSubtitle = if (deezerDisabled) "$disabledSuffix • $baseDeezerSubtitle" else baseDeezerSubtitle

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
                ServiceRow(shape, "Qobuz", qobuzSubtitle, com.alananasss.kittytune.R.drawable.ic_logo_qobuz, isConnected = !qobuzDisabled) {
                    navController.navigate("qobuz_settings")
                }
            },
            { shape ->
                ServiceRow(shape, "TIDAL", tidalSubtitle, com.alananasss.kittytune.R.drawable.ic_logo_tidal, isConnected = !tidalDisabled) {
                    navController.navigate("tidal_settings")
                }
            },
            { shape ->
                ServiceRow(shape, "Deezer", deezerSubtitle, com.alananasss.kittytune.R.drawable.ic_logo_deezer, isConnected = !deezerDisabled) {
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
}

@Composable
private fun SourcesOrderPage(navController: NavController) {
    val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences() }
    val order = prefs.getAudioProviderOrder()
    val disabledProviders = prefs.getDisabledAudioProviders()
    val disabledSuffix = str("provider_disabled_suffix")
    val orderSummary = order.joinToString(", ") { item ->
        val name = when (item) {
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.QOBUZ -> "Qobuz"
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.TIDAL -> "TIDAL"
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.DEEZER -> "Deezer"
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.YOUTUBE_MUSIC -> "YouTube Music"
            com.alananasss.kittytune.audio.providers.AudioProviderOrderItem.SOUNDCLOUD -> "SoundCloud"
        }
        if (item in disabledProviders) "$name $disabledSuffix" else name
    }

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

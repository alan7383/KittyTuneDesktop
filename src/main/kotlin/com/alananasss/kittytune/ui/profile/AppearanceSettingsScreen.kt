package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.alananasss.kittytune.ui.common.ScrollableLazyColumn as LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alananasss.kittytune.core.AppInstance
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.*
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.Image

@Composable
fun AppearanceSettingsScreen(
    onNavigateToColors: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val prefs = remember { PlayerPreferences() }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    var startDestination by remember { mutableStateOf(prefs.getStartDestination()) }
    var dynamicTheme by remember { mutableStateOf(prefs.getDynamicTheme()) }
    var themedTitleBar by remember { mutableStateOf(prefs.getThemedTitleBar()) }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var verticalVolumeSlider by remember { mutableStateOf(prefs.getVerticalVolumeSlider()) }
    var showIconDialog by remember { mutableStateOf(false) }
    val appIconVariant by prefs.appIconVariantFlow().collectAsState(initial = prefs.getAppIconVariant())
    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var customFontEnabled by remember { mutableStateOf(prefs.getCustomFontEnabled()) }

    var showStartDestDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFontConfigDialog by remember { mutableStateOf(false) }

    val isPureBlackVisible = themeMode == AppThemeMode.DARK || (themeMode == AppThemeMode.SYSTEM && systemDark)

    if (showStartDestDialog) {
        AlertDialog(
            onDismissRequest = { showStartDestDialog = false },
            title = { Text(str("pref_start_screen")) },
            text = {
                Column {
                    StartDestRadioButton(str("nav_home"), StartDestination.HOME, startDestination) {
                        startDestination = it
                        prefs.setStartDestination(it)
                        showStartDestDialog = false
                    }
                    StartDestRadioButton(str("nav_library"), StartDestination.LIBRARY, startDestination) {
                        startDestination = it
                        prefs.setStartDestination(it)
                        showStartDestDialog = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStartDestDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(str("pref_language")) },
            text = {
                Column {
                    LanguageRadioButton(str("theme_system"), AppLanguage.SYSTEM, appLanguage) {
                        prefs.setAppLanguage(it)
                        appLanguage = it
                        com.alananasss.kittytune.core.Strings.appLanguage = it.code
                        showLanguageDialog = false
                    }
                    LanguageRadioButton(str("lang_french"), AppLanguage.FRENCH, appLanguage) {
                        prefs.setAppLanguage(it)
                        appLanguage = it
                        com.alananasss.kittytune.core.Strings.appLanguage = it.code
                        showLanguageDialog = false
                    }
                    LanguageRadioButton(str("lang_english"), AppLanguage.ENGLISH, appLanguage) {
                        prefs.setAppLanguage(it)
                        appLanguage = it
                        com.alananasss.kittytune.core.Strings.appLanguage = it.code
                        showLanguageDialog = false
                    }
                    LanguageRadioButton(str("lang_hungarian"), AppLanguage.HUNGARIAN, appLanguage) {
                        prefs.setAppLanguage(it)
                        appLanguage = it
                        com.alananasss.kittytune.core.Strings.appLanguage = it.code
                        showLanguageDialog = false
                    }
                    LanguageRadioButton(str("lang_russian"), AppLanguage.RUSSIAN, appLanguage) {
                        prefs.setAppLanguage(it)
                        appLanguage = it
                        com.alananasss.kittytune.core.Strings.appLanguage = it.code
                        showLanguageDialog = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showCustomizeDialog) {
        CustomizeButtonsDialog(prefs = prefs, onDismiss = { showCustomizeDialog = false })
    }

    if (showIconDialog) {
        AlertDialog(
            onDismissRequest = { showIconDialog = false },
            title = { Text(str("pref_app_icon")) },
            text = {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    val variants = com.alananasss.kittytune.core.AppIconVariants.AVAILABLE
                    items(variants.size) { index ->
                        val variant = variants[index]
                        val selected = variant.key == appIconVariant
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .clickable {
                                    prefs.setAppIconVariant(variant.key)
                                    com.alananasss.kittytune.core.AppIconInstaller.apply(variant.key)
                                    showIconDialog = false
                                }
                                .padding(8.dp)
                        ) {
                            // Loaded by hand rather than with painterResource, which throws from
                            // inside composition when the bitmap is missing (issue #33).
                            val painter = remember(variant.key) { loadIconVariantPainter(variant.key) }
                            if (painter != null) {
                                androidx.compose.foundation.Image(
                                    painter = painter,
                                    contentDescription = variant.label,
                                    modifier = Modifier.size(56.dp)
                                )
                            } else {
                                Spacer(Modifier.size(56.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = variant.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showIconDialog = false }) { Text(str("btn_cancel")) } }
        )
    }

    if (showFontConfigDialog) {
        var wght by remember { mutableFloatStateOf(prefs.getFontWght().toFloat()) }
        var wdth by remember { mutableFloatStateOf(prefs.getFontWdth()) }
        var slnt by remember { mutableFloatStateOf(prefs.getFontSlnt()) }
        var rond by remember { mutableFloatStateOf(prefs.getFontRond()) }

        fun applyPreset(pWght: Float, pWdth: Float, pSlnt: Float, pRond: Float) {
            wght = pWght; prefs.setFontWght(pWght.toInt())
            wdth = pWdth; prefs.setFontWdth(pWdth)
            slnt = pSlnt; prefs.setFontSlnt(pSlnt)
            rond = pRond; prefs.setFontRond(pRond)
        }

        AlertDialog(
            onDismissRequest = { showFontConfigDialog = false },
            title = { Text(str("dialog_font_settings_title"), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        item { androidx.compose.material3.OutlinedButton(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), onClick = { applyPreset(400f, 100f, 0f, 0f) }) { Text(str("font_preset_default")) } }
                        item { androidx.compose.material3.OutlinedButton(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), onClick = { applyPreset(600f, 100f, 0f, 100f) }) { Text(str("font_preset_rounded")) } }
                        item { androidx.compose.material3.OutlinedButton(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), onClick = { applyPreset(250f, 105f, 0f, 0f) }) { Text(str("font_preset_elegant")) } }
                        item { androidx.compose.material3.OutlinedButton(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), onClick = { applyPreset(900f, 110f, 0f, 50f) }) { Text(str("font_preset_chunky")) } }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column {
                        Text(str("dialog_font_weight", wght.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = wght, onValueChange = { wght = it; prefs.setFontWght(it.toInt()) }, valueRange = 100f..1000f)
                    }
                    Column {
                        Text(str("dialog_font_width", wdth.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = wdth, onValueChange = { wdth = it; prefs.setFontWdth(it) }, valueRange = 25f..151f)
                    }
                    Column {
                        Text(str("dialog_font_slant", slnt.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = slnt, onValueChange = { slnt = it; prefs.setFontSlnt(it) }, valueRange = -10f..0f)
                    }
                    Column {
                        Text(str("dialog_font_roundness", rond.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(value = rond, onValueChange = { rond = it; prefs.setFontRond(it) }, valueRange = 0f..100f)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFontConfigDialog = false }) { Text(str("btn_close")) } },
            dismissButton = { TextButton(onClick = { applyPreset(400f, 100f, 0f, 0f) }) { Text(str("btn_reset")) } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(str("settings_cat_appearance"))
                    ThemeSelector(
                        currentTheme = themeMode,
                        onThemeSelected = {
                            themeMode = it
                            prefs.setThemeMode(it)
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // The themed title bar row only exists on Windows, where the window
                        // manager lets us paint the caption at all (issue #33).
                        val isTitleBarRowVisible = remember {
                            System.getProperty("os.name").lowercase().contains("win")
                        }
                        val titleBarIndex = if (isPureBlackVisible) 6 else 5
                        val customizeIndex = titleBarIndex + (if (isTitleBarRowVisible) 1 else 0)
                        val totalVisibleItems = customizeIndex + 1
                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 0),
                            title = str("pref_language"),
                            subtitle = when (appLanguage) {
                                AppLanguage.SYSTEM -> str("theme_system")
                                AppLanguage.FRENCH -> str("lang_french")
                                AppLanguage.ENGLISH -> str("lang_english")
                                AppLanguage.HUNGARIAN -> str("lang_hungarian")
                                AppLanguage.RUSSIAN -> str("lang_russian")
                            },
                            onClick = { showLanguageDialog = true }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 1),
                            title = str("pref_dynamic_theme"),
                            subtitle = str("pref_dynamic_theme_sub"),
                            hasSwitch = true,
                            switchState = dynamicTheme,
                            onSwitchChange = {
                                dynamicTheme = it
                                prefs.setDynamicTheme(it)
                            }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 2),
                            title = str("pref_vertical_volume_slider"),
                            subtitle = str("pref_vertical_volume_slider_sub"),
                            hasSwitch = true,
                            switchState = verticalVolumeSlider,
                            onSwitchChange = {
                                verticalVolumeSlider = it
                                prefs.setVerticalVolumeSlider(it)
                            }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 3),
                            title = str("pref_app_icon"),
                            subtitle = com.alananasss.kittytune.core.AppIconVariants.byKey(appIconVariant)?.label ?: "Default",
                            onClick = { showIconDialog = true }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 4),
                            title = str("pref_colors"),
                            subtitle = str("pref_colors_subtitle"),
                            icon = Icons.Rounded.Palette,
                            onClick = onNavigateToColors
                        )

                        AnimatedVisibility(
                            visible = isPureBlackVisible,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, 5),
                                title = str("pref_pure_black"),
                                hasSwitch = true,
                                switchState = pureBlack,
                                onSwitchChange = {
                                    pureBlack = it
                                    prefs.setPureBlack(it)
                                }
                            )
                        }

                        if (isTitleBarRowVisible) {
                            SettingsItem(
                                shape = getSettingsShape(totalVisibleItems, titleBarIndex),
                                title = str("pref_themed_title_bar"),
                                subtitle = str("pref_themed_title_bar_sub"),
                                hasSwitch = true,
                                switchState = themedTitleBar,
                                onSwitchChange = {
                                    themedTitleBar = it
                                    prefs.setThemedTitleBar(it)
                                }
                            )
                        }

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, customizeIndex),
                            title = str("pref_customize_buttons"),
                            subtitle = str("pref_customize_buttons_sub"),
                            icon = Icons.Rounded.Tune,
                            onClick = { showCustomizeDialog = true }
                        )
                    }
                }
            }

            Box {
                val uiScale by prefs.uiScaleFlow().collectAsState(initial = prefs.getUiScale())
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(str("pref_zoom_level"))
                    Text(str("pref_zoom_level_sub"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(str("pref_zoom_compact"), style = MaterialTheme.typography.labelLarge, fontWeight = if (uiScale < 0.95f) FontWeight.Bold else FontWeight.Normal)
                                Text(str("pref_zoom_default"), style = MaterialTheme.typography.labelLarge, fontWeight = if (uiScale in 0.95f..1.05f) FontWeight.Bold else FontWeight.Normal)
                                Text(str("pref_zoom_airy"), style = MaterialTheme.typography.labelLarge, fontWeight = if (uiScale > 1.05f) FontWeight.Bold else FontWeight.Normal)
                            }
                            Spacer(Modifier.height(8.dp))
                            Slider(
                                value = uiScale,
                                onValueChange = { prefs.setUiScale(it) },
                                valueRange = 0.7f..1.3f,
                                steps = 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                (7..13).forEach { step ->
                                    Text("${step * 10} %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                OutlinedButton(onClick = { prefs.setUiScale(1.0f) }) {
                                    Text(str("btn_reset"))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            Box {
                SettingsGroup(
                    title = str("settings_cat_general"),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_start_screen"),
                                subtitle = when (startDestination) {
                                    StartDestination.HOME -> str("nav_home")
                                    StartDestination.LIBRARY -> str("nav_library")
                                },
                                onClick = { showStartDestDialog = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_auto_update"),
                                subtitle = str("pref_auto_update_subtitle"),
                                hasSwitch = true,
                                switchState = autoUpdate,
                                onSwitchChange = {
                                    autoUpdate = it
                                    prefs.setAutoUpdateEnabled(it)
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_custom_font"),
                                subtitle = str("pref_custom_font_subtitle"),
                                hasSwitch = true,
                                switchState = customFontEnabled,
                                onSwitchChange = {
                                    customFontEnabled = it
                                    prefs.setCustomFontEnabled(it)
                                },
                                onClick = if (customFontEnabled) {
                                    { showFontConfigDialog = true }
                                } else null
                            )
                        }
                    )
                )
            }
        }
}

@Composable
fun ThemeSelector(
    currentTheme: AppThemeMode,
    onThemeSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeOption(
                icon = Icons.Outlined.BrightnessAuto,
                selectedIcon = Icons.Filled.BrightnessAuto,
                label = str("theme_system"),
                isSelected = currentTheme == AppThemeMode.SYSTEM,
                onClick = { onThemeSelected(AppThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                icon = Icons.Outlined.LightMode,
                selectedIcon = Icons.Filled.LightMode,
                label = str("theme_light"),
                isSelected = currentTheme == AppThemeMode.LIGHT,
                onClick = { onThemeSelected(AppThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                icon = Icons.Outlined.DarkMode,
                selectedIcon = Icons.Filled.DarkMode,
                label = str("theme_dark"),
                isSelected = currentTheme == AppThemeMode.DARK,
                onClick = { onThemeSelected(AppThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
@Composable
private fun ThemeOption(
    icon: ImageVector,
    selectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .padding(vertical = 4.dp)
    ) {
        FilledTonalIconToggleButton(checked = isSelected,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = if (isSelected) selectedIcon else icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StartDestRadioButton(text: String,
    dest: StartDestination,
    selected: StartDestination,
    onSelect: (StartDestination) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(dest) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (dest == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun LanguageRadioButton(text: String,
    lang: AppLanguage,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(lang) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (lang == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

/**
 * Decodes an app-icon variant into a painter, or returns null when the bitmap is not in this
 * build. `painterResource` is the usual way to do this, but it throws for an unknown path and
 * it throws from composition, which took the whole app down when the icons were missing from
 * the packaged jar (issue #33). Here a missing file is just an empty tile.
 */
private fun loadIconVariantPainter(key: String): androidx.compose.ui.graphics.painter.Painter? =
    runCatching {
        val path = com.alananasss.kittytune.core.AppIconVariants.resourcePath(key)
        val loader = Thread.currentThread().contextClassLoader
            ?: com.alananasss.kittytune.core.AppIconVariants::class.java.classLoader
        loader?.getResourceAsStream(path)?.use { stream ->
            androidx.compose.ui.graphics.painter.BitmapPainter(
                androidx.compose.ui.res.loadImageBitmap(stream)
            )
        }
    }.getOrNull()

/**
 * One place to customise every optional button and tile in the app (issue #33).
 *
 * Three separate dialogs would have been three places to go looking. The sections are the three
 * surfaces that carry optional controls: the sidebar's navigation rows, the player bar's right-hand
 * buttons, and the fixed library tiles.
 *
 * The library rows preview each tile the way the library will actually draw it, so the effect of the
 * colour switch and of an imported icon is visible without leaving the dialog.
 */
@Composable
private fun CustomizeButtonsDialog(prefs: PlayerPreferences, onDismiss: () -> Unit) {
    var hiddenNav by remember { mutableStateOf(prefs.getHiddenSidebarNav()) }
    var hiddenLibraryButtons by remember { mutableStateOf(prefs.getHiddenLibraryButtons()) }
    var playerBarButtons by remember { mutableStateOf(prefs.getPlayerBarButtons()) }
    var hidden by remember { mutableStateOf(prefs.getHiddenLibraryTiles()) }
    // Not a setting of its own: the tiles follow the palette exactly while the dynamic theme is
    // on, which is how it was asked for. Read here only so the previews match the library.
    val themed = prefs.getDynamicTheme()
    var icons by remember {
        mutableStateOf(PlayerPreferences.LIBRARY_TILES.associateWith { prefs.getLibraryTileIcon(it) })
    }
    /** Set when a picked file turned out not to be an image; cleared by the next attempt. */
    var rejectedFile by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme
    val labels = mapOf(
        PlayerPreferences.LIBRARY_TILE_LIKES to str("lib_liked_tracks"),
        PlayerPreferences.LIBRARY_TILE_DOWNLOADS to str("lib_downloads"),
        PlayerPreferences.LIBRARY_TILE_LOCAL to str("lib_local_media"),
    )
    val builtInIcons = mapOf(
        PlayerPreferences.LIBRARY_TILE_LIKES to Icons.Rounded.Favorite,
        PlayerPreferences.LIBRARY_TILE_DOWNLOADS to Icons.Rounded.DownloadForOffline,
        PlayerPreferences.LIBRARY_TILE_LOCAL to Icons.Rounded.FolderOpen,
    )
    // Mirrors the library exactly: flat container roles while the dynamic theme is on, the original
    // gradients when it is off. See rememberFixedLibraryTiles.
    val gradients = mapOf(
        PlayerPreferences.LIBRARY_TILE_LIKES to
            if (themed) null else listOf(Color(0xFF7C4DFF), Color(0xFFB388FF)),
        PlayerPreferences.LIBRARY_TILE_DOWNLOADS to
            if (themed) null else listOf(Color(0xFF00C853), Color(0xFF69F0AE)),
        PlayerPreferences.LIBRARY_TILE_LOCAL to
            if (themed) null else listOf(Color(0xFF0091EA), Color(0xFF40C4FF)),
    )
    val flats = mapOf(
        PlayerPreferences.LIBRARY_TILE_LIKES to scheme.primaryContainer.takeIf { themed },
        PlayerPreferences.LIBRARY_TILE_DOWNLOADS to scheme.secondaryContainer.takeIf { themed },
        PlayerPreferences.LIBRARY_TILE_LOCAL to scheme.tertiaryContainer.takeIf { themed },
    )
    val tints = mapOf(
        PlayerPreferences.LIBRARY_TILE_LIKES to
            if (themed) scheme.onPrimaryContainer else Color.White,
        PlayerPreferences.LIBRARY_TILE_DOWNLOADS to
            if (themed) scheme.onSecondaryContainer else Color.White,
        PlayerPreferences.LIBRARY_TILE_LOCAL to
            if (themed) scheme.onTertiaryContainer else Color.White,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("pref_customize_buttons")) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CustomizeSectionTitle(str("customize_section_sidebar"))
                Text(
                    str("customize_section_sidebar_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                listOf(
                    PlayerPreferences.SIDEBAR_NAV_FEED to str("nav_feed"),
                    PlayerPreferences.SIDEBAR_NAV_EXPLORE to str("explorer_title"),
                    PlayerPreferences.SIDEBAR_NAV_RECOGNITION to str("pref_bottom_menu_fab_recognition"),
                    PlayerPreferences.SIDEBAR_NAV_SYNC to str("sync_title"),
                ).forEach { (key, label) ->
                    val shown = key !in hiddenNav
                    CustomizeCheckRow(label = label, checked = shown) {
                        hiddenNav = if (shown) hiddenNav + key else hiddenNav - key
                        prefs.setHiddenSidebarNav(hiddenNav)
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                CustomizeSectionTitle(str("pref_player_bar_buttons"))
                Text(
                    str("pref_player_bar_buttons_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                listOf(
                    PlayerPreferences.PLAYER_BAR_BUTTON_LIKE to str("player_button_like"),
                    PlayerPreferences.PLAYER_BAR_BUTTON_PANEL to str("player_button_panel"),
                    PlayerPreferences.PLAYER_BAR_BUTTON_QUEUE to str("player_button_queue"),
                ).forEach { (key, label) ->
                    val checked = key in playerBarButtons
                    CustomizeCheckRow(label = label, checked = checked) {
                        playerBarButtons =
                            if (checked) playerBarButtons - key else playerBarButtons + key
                        prefs.setPlayerBarButtons(playerBarButtons)
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                CustomizeSectionTitle(str("customize_section_library"))
                listOf(
                    PlayerPreferences.LIBRARY_BUTTON_CREATE to str("lib_create"),
                    PlayerPreferences.LIBRARY_BUTTON_HISTORY to str("history_title"),
                ).forEach { (key, label) ->
                    val shown = key !in hiddenLibraryButtons
                    CustomizeCheckRow(label = label, checked = shown) {
                        hiddenLibraryButtons =
                            if (shown) hiddenLibraryButtons + key else hiddenLibraryButtons - key
                        prefs.setHiddenLibraryButtons(hiddenLibraryButtons)
                    }
                }

                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

                CustomizeSectionTitle(str("pref_library_tiles"))
                Text(
                    str("pref_library_tiles_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                PlayerPreferences.LIBRARY_TILES.forEach { tile ->
                    val shown = tile !in hidden
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = shown,
                            onCheckedChange = {
                                hidden = if (shown) hidden + tile else hidden - tile
                                prefs.setHiddenLibraryTiles(hidden)
                            }
                        )
                        LibraryTilePreview(
                            iconPath = icons[tile],
                            icon = builtInIcons.getValue(tile),
                            gradient = gradients.getValue(tile),
                            flatColor = flats.getValue(tile),
                            tint = tints.getValue(tile),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            labels[tile].orEmpty(),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(onClick = {
                            val picked = pickImageFile(str("lib_tile_choose_icon"))
                            if (picked != null) {
                                val stored = com.alananasss.kittytune.data.local.LibraryTileIcons
                                    .import(tile, picked)
                                rejectedFile = stored == null
                                if (stored != null) {
                                    prefs.setLibraryTileIcon(tile, stored)
                                    icons = icons + (tile to stored)
                                }
                            }
                        }) {
                            Icon(
                                Icons.Outlined.Image,
                                contentDescription = str("lib_tile_choose_icon"),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (icons[tile] != null) {
                            IconButton(onClick = {
                                com.alananasss.kittytune.data.local.LibraryTileIcons.clear(tile)
                                prefs.setLibraryTileIcon(tile, null)
                                icons = icons + (tile to null)
                                rejectedFile = false
                            }) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = str("lib_tile_reset_icon"),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (rejectedFile) {
                    Text(
                        str("lib_tile_icon_rejected"),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_close")) } }
    )
}

@Composable
private fun CustomizeSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/** A checkbox whose whole row is the target, the way the settings list behaves. */
@Composable
private fun CustomizeCheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One tile drawn the way the library draws it: the icon, or the imported image, over the gradient. */
@Composable
private fun LibraryTilePreview(
    iconPath: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: List<Color>?,
    flatColor: Color?,
    tint: Color,
) {
    val file = iconPath?.let { path -> remember(path) { java.io.File(path) } }
    val fill = when {
        flatColor != null -> Modifier.background(flatColor)
        gradient != null -> Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(gradient))
        else -> Modifier
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(fill),
        contentAlignment = Alignment.Center
    ) {
        if (file != null && file.isFile) {
            coil3.compose.AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Asks for an image file, or null when the dialog is dismissed.
 *
 * The filter is a hint the platform is free to ignore, so the file still has to be validated
 * afterwards — see [com.alananasss.kittytune.data.local.LibraryTileIcons.import].
 */
private fun pickImageFile(title: String): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp").any { name.endsWith(it, true) }
    }
    dialog.isVisible = true
    return dialog.files.firstOrNull()
}

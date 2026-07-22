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

@Composable
fun AppearanceSettingsScreen(
    onNavigateToColors: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val prefs = remember { PlayerPreferences() }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    var startDestination by remember { mutableStateOf(prefs.getStartDestination()) }
    var dynamicTheme by remember { mutableStateOf(prefs.getDynamicTheme()) }
    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
    var playerStyle by remember { mutableStateOf(prefs.getPlayerStyle()) }
    var newPlayerDesign by remember { mutableStateOf(prefs.getNewPlayerDesignEnabled()) }
    var appLanguage by remember { mutableStateOf(prefs.getAppLanguage()) }
    var achievementPopupsEnabled by remember { mutableStateOf(prefs.getAchievementPopupsEnabled()) }
    var autoUpdate by remember { mutableStateOf(prefs.getAutoUpdateEnabled()) }
    var customFontEnabled by remember { mutableStateOf(prefs.getCustomFontEnabled()) }

    var showPlayerStyleDialog by remember { mutableStateOf(false) }
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

    if (showPlayerStyleDialog) {
        AlertDialog(
            onDismissRequest = { showPlayerStyleDialog = false },
            title = { Text(str("pref_player_style")) },
            text = {
                Column {
                    PlayerStyleRadioButton(str("style_theme"), PlayerBackgroundStyle.THEME, playerStyle) {
                        playerStyle = it
                        prefs.setPlayerStyle(it)
                        showPlayerStyleDialog = false
                    }
                    PlayerStyleRadioButton(str("style_gradient"), PlayerBackgroundStyle.GRADIENT, playerStyle) {
                        playerStyle = it
                        prefs.setPlayerStyle(it)
                        showPlayerStyleDialog = false
                    }
                    PlayerStyleRadioButton(str("style_blur"), PlayerBackgroundStyle.BLUR, playerStyle) {
                        playerStyle = it
                        prefs.setPlayerStyle(it)
                        showPlayerStyleDialog = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlayerStyleDialog = false }) { Text(str("btn_cancel")) } }
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
                        showLanguageDialog = false
                    }
                    LanguageRadioButton(str("lang_french"), AppLanguage.FRENCH, appLanguage) {
                        prefs.setAppLanguage(it)
                        appLanguage = it
                        showLanguageDialog = false
                    }
                    LanguageRadioButton(str("lang_english"), AppLanguage.ENGLISH, appLanguage) {
                        prefs.setAppLanguage(it)
                        appLanguage = it
                        showLanguageDialog = false
                    }
                    LanguageRadioButton(str("lang_hungarian"), AppLanguage.HUNGARIAN, appLanguage) {
                        prefs.setAppLanguage(it)
                        appLanguage = it
                        showLanguageDialog = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text(str("btn_cancel")) } }
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

    SettingsScaffold(
        title = str("pref_appearance_title"),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            item {
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
                        val totalVisibleItems = if (isPureBlackVisible) 4 else 3
                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 0),
                            title = str("pref_language"),
                            subtitle = when (appLanguage) {
                                AppLanguage.SYSTEM -> str("theme_system")
                                AppLanguage.FRENCH -> str("lang_french")
                                AppLanguage.ENGLISH -> str("lang_english")
                                AppLanguage.HUNGARIAN -> str("lang_hungarian")
                            },
                            onClick = { showLanguageDialog = true }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 1),
                            title = str("pref_dynamic_theme"),
                            hasSwitch = true,
                            switchState = dynamicTheme,
                            onSwitchChange = {
                                dynamicTheme = it
                                prefs.setDynamicTheme(it)
                            }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 2),
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
                                shape = getSettingsShape(totalVisibleItems, 3),
                                title = str("pref_pure_black"),
                                hasSwitch = true,
                                switchState = pureBlack,
                                onSwitchChange = {
                                    pureBlack = it
                                    prefs.setPureBlack(it)
                                }
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(
                    title = str("settings_cat_player"),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_new_player_design"),
                                hasSwitch = true,
                                switchState = newPlayerDesign,
                                onSwitchChange = {
                                    newPlayerDesign = it
                                    prefs.setNewPlayerDesignEnabled(it)
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = str("pref_player_style"),
                                subtitle = when (playerStyle) {
                                    PlayerBackgroundStyle.THEME -> str("style_theme")
                                    PlayerBackgroundStyle.GRADIENT -> str("style_gradient")
                                    PlayerBackgroundStyle.BLUR -> str("style_blur")
                                },
                                onClick = { showPlayerStyleDialog = true }
                            )
                        }
                    )
                )
            }

            item {
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
                                title = str("pref_achievement_popups"),
                                hasSwitch = true,
                                switchState = achievementPopupsEnabled,
                                onSwitchChange = {
                                    achievementPopupsEnabled = it
                                    prefs.setAchievementPopupsEnabled(it)
                                }
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
fun PlayerStyleRadioButton(text: String,
    style: PlayerBackgroundStyle,
    selected: PlayerBackgroundStyle,
    onSelect: (PlayerBackgroundStyle) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(style) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = (style == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
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

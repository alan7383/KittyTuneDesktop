package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.local.*
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.Slider
import com.alananasss.kittytune.ui.common.pressScale
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

private val isWindows = System.getProperty("os.name").lowercase().contains("win")

/**
 * Interface → Themes: whether the colours follow the cover, the light/dark mode, a row of ready-made
 * palettes, the way to build one's own, and the window-level look (title bar, scale, font, icon).
 *
 * "Dynamic theme" and "Dynamic theme from the track" used to be two switches that nobody could tell apart;
 * they are one switch now, and picking a ready-made palette turns it off, since the two cannot both win.
 */
@Composable
fun ThemesSettingsPage(onOpenCustomTheme: () -> Unit) {
    val prefs = remember { PlayerPreferences() }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

    var followsCover by remember { mutableStateOf(prefs.getTrackDynamicTheme()) }
    var keyColor by remember { mutableIntStateOf(prefs.getKeyColor()) }
    var themeMode by remember { mutableStateOf(prefs.getThemeMode()) }
    var pureBlack by remember { mutableStateOf(prefs.getPureBlack()) }
    var themedTitleBar by remember { mutableStateOf(prefs.getThemedTitleBar()) }
    var appFont by remember { mutableStateOf(com.alananasss.kittytune.ui.theme.AppFont.parse(prefs.getAppFont())) }
    val appIconVariant by prefs.appIconVariantFlow().collectAsState(initial = prefs.getAppIconVariant())
    val uiScale by prefs.uiScaleFlow().collectAsState(initial = prefs.getUiScale())

    var showIconDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showFontAxesDialog by remember { mutableStateOf(false) }
    var showScaleDialog by remember { mutableStateOf(false) }
    if (showIconDialog) AppIconDialog(prefs, appIconVariant) { showIconDialog = false }
    if (showFontAxesDialog) FontAxesDialog(prefs) { showFontAxesDialog = false }
    if (showScaleDialog) UiScaleDialog(prefs, uiScale) { showScaleDialog = false }
    if (showFontDialog) {
        FontPickerDialog(
            current = appFont,
            onSelect = {
                appFont = it
                prefs.setAppFont(it.id)
            },
            onTuneFlex = { showFontAxesDialog = true },
            onDismiss = { showFontDialog = false },
        )
    }

    val isDark = themeMode == AppThemeMode.DARK || (themeMode == AppThemeMode.SYSTEM && systemDark)
    val look = when {
        themeMode == AppThemeMode.DARK && pureBlack -> ThemeLook.AMOLED
        themeMode == AppThemeMode.DARK -> ThemeLook.DARK
        themeMode == AppThemeMode.LIGHT -> ThemeLook.LIGHT
        else -> ThemeLook.SYSTEM
    }

    SettingsGroup(
        items = listOf { shape ->
            SettingsItem(
                shape = shape,
                title = str("pref_dynamic_theme_merged"),
                subtitle = str("pref_dynamic_theme_merged_sub"),
                icon = Icons.Rounded.AutoAwesome,
                hasSwitch = true,
                switchState = followsCover,
                onSwitchChange = {
                    followsCover = it
                    prefs.setTrackDynamicTheme(it)
                    prefs.setDynamicTheme(it)
                },
            )
        },
    )

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SettingsGroupTitle(str("theme_presets_title"))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
                // One compact row: the mode, with AMOLED as a theme of its own rather than a switch under Dark.
                com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup(
                    options = ThemeLook.entries,
                    selectedOption = look,
                    onOptionSelected = { chosen ->
                        themeMode = chosen.mode
                        pureBlack = chosen == ThemeLook.AMOLED
                        prefs.setThemeMode(chosen.mode)
                        prefs.setPureBlack(pureBlack)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fillMaxWidth = true,
                    iconProvider = { Icon(it.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    labelProvider = { Text(str(it.labelKey), maxLines = 1, softWrap = false) },
                )
                ThemePresetRow(
                    selectedSeed = if (followsCover) null else keyColor,
                    isDark = isDark,
                    pureBlack = pureBlack && isDark,
                    onSelect = { preset ->
                        keyColor = preset.seed
                        prefs.setKeyColor(preset.seed)
                        prefs.setColorStyle(preset.styleName)
                        followsCover = false
                        prefs.setTrackDynamicTheme(false)
                    },
                    onCustom = onOpenCustomTheme,
                )
            }
        }
    }

    SettingsGroup(
        title = str("settings_group_window"),
        items = buildList {
            if (isWindows) add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_themed_title_bar"),
                    subtitle = str("pref_themed_title_bar_sub"),
                    hasSwitch = true,
                    switchState = themedTitleBar,
                    onSwitchChange = {
                        themedTitleBar = it
                        prefs.setThemedTitleBar(it)
                    },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_zoom_level"),
                    subtitle = str("pref_zoom_level_sub"),
                    trailingText = "${(uiScale * 100).toInt()} %",
                    onClick = { showScaleDialog = true },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_font"),
                    subtitle = fontLabel(appFont),
                    icon = Icons.Rounded.TextFields,
                    onClick = { showFontDialog = true },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_app_icon"),
                    subtitle = com.alananasss.kittytune.core.AppIconVariants.byKey(appIconVariant)?.let { com.alananasss.kittytune.core.AppIconVariants.localizedLabel(it) } ?: str("app_icon_default"),
                    icon = Icons.Rounded.Apps,
                    onClick = { showIconDialog = true },
                )
            }
        },
    )
}

/** The theme choices of the mode row: the three modes plus AMOLED, which is Dark with true black. */
private enum class ThemeLook(val mode: AppThemeMode, val labelKey: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SYSTEM(AppThemeMode.SYSTEM, "theme_system", Icons.Rounded.BrightnessAuto),
    LIGHT(AppThemeMode.LIGHT, "theme_light", Icons.Rounded.LightMode),
    DARK(AppThemeMode.DARK, "theme_dark", Icons.Rounded.DarkMode),
    AMOLED(AppThemeMode.DARK, "theme_amoled", Icons.Rounded.Contrast),
}

@Composable
private fun fontLabel(font: com.alananasss.kittytune.ui.theme.AppFont): String =
    if (font == com.alananasss.kittytune.ui.theme.AppFont.Default) str("font_default")
    else com.alananasss.kittytune.ui.theme.AppFonts.nameOf(font)

/**
 * The typefaces on offer, each shown in itself: the default, the adjustable Google Sans Flex, a selection of
 * installed fonts, the user's own, and a way to add one.
 */
@Composable
private fun FontPickerDialog(
    current: com.alananasss.kittytune.ui.theme.AppFont,
    onSelect: (com.alananasss.kittytune.ui.theme.AppFont) -> Unit,
    onTuneFlex: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fonts = com.alananasss.kittytune.ui.theme.AppFonts
    var userFonts by remember { mutableStateOf(fonts.userFonts()) }
    val systemFonts = remember { fonts.availableSystemFonts() }
    var rejected by remember { mutableStateOf(false) }
    val options = buildList {
        add(com.alananasss.kittytune.ui.theme.AppFont.Default)
        add(com.alananasss.kittytune.ui.theme.AppFont.Flex)
        systemFonts.forEach { add(com.alananasss.kittytune.ui.theme.AppFont.System(it)) }
        userFonts.forEach { add(com.alananasss.kittytune.ui.theme.AppFont.UserFile(it)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("pref_font")) },
        text = {
            com.alananasss.kittytune.ui.common.ScrollableColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                contentPadding = PaddingValues(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                options.forEach { font ->
                    val family = when (font) {
                        com.alananasss.kittytune.ui.theme.AppFont.Flex -> remember { com.alananasss.kittytune.ui.theme.getDynamicTypography(true, 500, 100f, 0f, 0f, 0f, 18f).bodyLarge.fontFamily }
                        else -> remember(font) { fonts.familyFor(font) }
                    }
                    val selected = font == current
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { onSelect(font) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(fontLabel(font), style = MaterialTheme.typography.titleMedium.copy(fontFamily = family))
                            Text("Aa Бб 123 — KittyTune", style = MaterialTheme.typography.bodySmall.copy(fontFamily = family), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (font == com.alananasss.kittytune.ui.theme.AppFont.Flex) {
                            IconButton(onClick = { onSelect(font); onTuneFlex() }) {
                                Icon(Icons.Rounded.Tune, contentDescription = str("dialog_font_settings_title"))
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        val picked = pickFontFile(str("font_add")) ?: return@TextButton
                        val stored = fonts.import(picked)
                        rejected = stored == null
                        if (stored != null) {
                            userFonts = fonts.userFonts()
                            onSelect(com.alananasss.kittytune.ui.theme.AppFont.UserFile(stored))
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(str("font_add"))
                }
                if (rejected) Text(str("font_rejected"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_close")) } },
    )
}

/** The system's own file dialog, filtered to fonts. */
private fun pickFontFile(title: String): java.io.File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.file = "*.ttf;*.otf"
    dialog.setFilenameFilter { _, name -> name.endsWith(".ttf", true) || name.endsWith(".otf", true) }
    dialog.isVisible = true
    return dialog.files.firstOrNull()
}

/**
 * Interface → Player design: the sliders, which buttons the player bar carries, the covers, and the tiles
 * of the track and playlist menus.
 */
@Composable
fun PlayerDesignSettingsPage() {
    val prefs = remember { PlayerPreferences() }
    var animatedCovers by remember { mutableStateOf(prefs.getAnimatedCoversEnabled()) }
    var animatedCoversFadeUi by remember { mutableStateOf(prefs.getAnimatedCoversFadeUiEnabled()) }
    var animatedArtistProfiles by remember { mutableStateOf(prefs.getAnimatedArtistProfilesEnabled()) }
    var showMenuTilesDialog by remember { mutableStateOf(false) }
    if (showMenuTilesDialog) MenuTilesDialog(prefs) { showMenuTilesDialog = false }

    // Shape, sliders, the bar's buttons and the scroll step.
    PlayerDesignContent(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

    SettingsGroup(
        title = str("settings_group_covers"),
        items = buildList {
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_animated_covers"),
                    subtitle = str("pref_animated_covers_desc"),
                    hasSwitch = true,
                    switchState = animatedCovers,
                    onSwitchChange = {
                        animatedCovers = it
                        prefs.setAnimatedCoversEnabled(it)
                    },
                )
            }
            if (animatedCovers) add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_animated_covers_fade_ui"),
                    subtitle = str("pref_animated_covers_fade_ui_desc"),
                    hasSwitch = true,
                    switchState = animatedCoversFadeUi,
                    onSwitchChange = {
                        animatedCoversFadeUi = it
                        prefs.setAnimatedCoversFadeUiEnabled(it)
                    },
                )
            }
            add { shape ->
                SettingsItem(
                    shape = shape,
                    title = str("pref_animated_artist_profiles"),
                    subtitle = str("pref_animated_artist_profiles_desc"),
                    hasSwitch = true,
                    switchState = animatedArtistProfiles,
                    onSwitchChange = {
                        animatedArtistProfiles = it
                        prefs.setAnimatedArtistProfilesEnabled(it)
                    },
                )
            }
        },
    )

    SettingsGroup(
        items = listOf { shape ->
            SettingsItem(
                shape = shape,
                title = str("menu_tiles_title"),
                subtitle = str("menu_tiles_desc"),
                icon = Icons.Rounded.GridView,
                onClick = { showMenuTilesDialog = true },
            )
        },
    )
}


/**
 * A ready-made palette: a key colour and the style it is generated with. Zero is the app's own seed with its
 * default (expressive) style — the look KittyTune ships with. The named ones use the tonal-spot style, which
 * keeps the key colour's hue; the expressive style rotates it, and a "Forest" that comes out red is no preset.
 */
private class ThemePreset(val labelKey: String, val seed: Int, val style: PaletteStyle) {
    /** What the colour-style preference stores for this preset; "System" is the default expressive style. */
    val styleName: String get() = if (seed == 0) "System" else style.name
}

private val themePresets = listOf(
    ThemePreset("theme_preset_ocean", 0xFF1565C0.toInt(), PaletteStyle.TonalSpot),
    ThemePreset("theme_preset_forest", 0xFF2E7D32.toInt(), PaletteStyle.TonalSpot),
    ThemePreset("theme_preset_sunset", 0xFFE64A19.toInt(), PaletteStyle.TonalSpot),
    ThemePreset("theme_preset_rose", 0xFFD81B60.toInt(), PaletteStyle.TonalSpot),
    ThemePreset("theme_preset_lavender", 0xFF7E57C2.toInt(), PaletteStyle.TonalSpot),
    ThemePreset("theme_preset_mint", 0xFF00A884.toInt(), PaletteStyle.TonalSpot),
)

@Composable
private fun ThemePresetRow(
    selectedSeed: Int?,
    isDark: Boolean,
    pureBlack: Boolean,
    onSelect: (ThemePreset) -> Unit,
    onCustom: () -> Unit,
) {
    com.alananasss.kittytune.ui.common.ScrollableLazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        fadeColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        items(themePresets.size) { index ->
            val preset = themePresets[index]
            ThemePresetCard(
                label = str(preset.labelKey),
                seed = preset.seed,
                style = preset.style,
                isSelected = selectedSeed == preset.seed,
                isDark = isDark,
                pureBlack = pureBlack,
                onClick = { onSelect(preset) },
            )
        }
        item { CustomThemeCard(onCustom) }
    }
}

/** The last card of the row: build a theme of one's own. */
@Composable
private fun CustomThemeCard(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.size(width = 96.dp, height = 72.dp).pressScale(interaction),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Text(str("theme_custom_short"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A thumbnail of the palette a key colour produces: a surface with its primary, secondary and tertiary. */
@Composable
private fun ThemePresetCard(
    label: String,
    seed: Int,
    style: PaletteStyle,
    isSelected: Boolean,
    isDark: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
) {
    val scheme = rememberDynamicColorScheme(
        seedColor = if (seed == 0) Color(0xFFFF7A1A) else Color(seed),
        isDark = isDark,
        isAmoled = pureBlack,
        style = style,
    )
    val interaction = remember { MutableInteractionSource() }
    val ring by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        label = "presetRing",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = RoundedCornerShape(18.dp),
            color = scheme.surfaceContainer,
            border = BorderStroke(if (isSelected) 2.dp else 1.dp, ring),
            modifier = Modifier.size(width = 96.dp, height = 72.dp).pressScale(interaction),
        ) {
            Box(Modifier.fillMaxSize().padding(10.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.height * 0.28f
                    drawCircle(scheme.primary, radius, Offset(radius, radius))
                    val barHeight = size.height * 0.16f
                    val barLeft = radius * 2 + 8.dp.toPx()
                    drawRoundRect(
                        scheme.secondaryContainer,
                        topLeft = Offset(barLeft, radius - barHeight),
                        size = Size(size.width - barLeft, barHeight),
                        cornerRadius = CornerRadius(barHeight / 2),
                    )
                    drawRoundRect(
                        scheme.tertiary,
                        topLeft = Offset(barLeft, radius + 2.dp.toPx()),
                        size = Size((size.width - barLeft) * 0.6f, barHeight),
                        cornerRadius = CornerRadius(barHeight / 2),
                    )
                    drawRoundRect(
                        scheme.primaryContainer,
                        topLeft = Offset(0f, size.height - barHeight * 1.4f),
                        size = Size(size.width, barHeight * 1.4f),
                        cornerRadius = CornerRadius(barHeight),
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).size(18.dp),
                    )
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** A radio row whose whole width is the target, used by the settings' single-choice dialogs. */
@Composable
internal fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

/** A single-choice dialog: pick one of [options] and it closes. */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    ChoiceRow(label, value == selected) {
                        onSelect(value)
                        onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_cancel")) } },
    )
}

@Composable
private fun UiScaleDialog(prefs: PlayerPreferences, uiScale: Float, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("pref_zoom_level")) },
        text = {
            Column {
                Text(str("pref_zoom_level_sub"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_close")) } },
        dismissButton = { TextButton(onClick = { prefs.setUiScale(1.0f) }) { Text(str("btn_reset")) } },
    )
}

@Composable
private fun AppIconDialog(prefs: PlayerPreferences, appIconVariant: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable {
                                prefs.setAppIconVariant(variant.key)
                                com.alananasss.kittytune.core.AppIconInstaller.apply(variant.key)
                                onDismiss()
                            }
                            .padding(8.dp)
                    ) {
                        // Loaded by hand rather than with painterResource, which throws from
                        // inside composition when the bitmap is missing (issue #33).
                        val painter = remember(variant.key) { loadIconVariantPainter(variant.key) }
                        if (painter != null) {
                            androidx.compose.foundation.Image(
                                painter = painter,
                                contentDescription = com.alananasss.kittytune.core.AppIconVariants.localizedLabel(variant),
                                modifier = Modifier.size(56.dp)
                            )
                        } else {
                            Spacer(Modifier.size(56.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = com.alananasss.kittytune.core.AppIconVariants.localizedLabel(variant),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_cancel")) } }
    )
}

@Composable
private fun FontAxesDialog(prefs: PlayerPreferences, onDismiss: () -> Unit) {
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
        onDismissRequest = onDismiss,
        title = { Text(str("dialog_font_settings_title"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    item { OutlinedButton(contentPadding = PaddingValues(horizontal = 12.dp), onClick = { applyPreset(400f, 100f, 0f, 0f) }) { Text(str("font_preset_default")) } }
                    item { OutlinedButton(contentPadding = PaddingValues(horizontal = 12.dp), onClick = { applyPreset(600f, 100f, 0f, 100f) }) { Text(str("font_preset_rounded")) } }
                    item { OutlinedButton(contentPadding = PaddingValues(horizontal = 12.dp), onClick = { applyPreset(250f, 105f, 0f, 0f) }) { Text(str("font_preset_elegant")) } }
                    item { OutlinedButton(contentPadding = PaddingValues(horizontal = 12.dp), onClick = { applyPreset(900f, 110f, 0f, 50f) }) { Text(str("font_preset_chunky")) } }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_close")) } },
        dismissButton = { TextButton(onClick = { applyPreset(400f, 100f, 0f, 0f) }) { Text(str("btn_reset")) } }
    )
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
 * Which tiles the track and playlist menus show. Visibility only: the order is set by dragging the tiles
 * themselves, the one place it can be judged (issue #33).
 */
@Composable
private fun MenuTilesDialog(prefs: PlayerPreferences, onDismiss: () -> Unit) {
    var hiddenTrackTiles by remember { mutableStateOf(prefs.getHiddenMenuTiles(PlayerPreferences.MENU_TRACK)) }
    var hiddenPlaylistTiles by remember { mutableStateOf(prefs.getHiddenMenuTiles(PlayerPreferences.MENU_PLAYLIST)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("menu_tiles_title")) },
        text = {
            com.alananasss.kittytune.ui.common.ScrollableColumn(
                modifier = Modifier.heightIn(max = 460.dp),
                contentPadding = PaddingValues(end = 12.dp),
            ) {
                listOf(
                    Triple(str("menu_tiles_track"), PlayerPreferences.MENU_TRACK, com.alananasss.kittytune.ui.main.MenuTiles.TRACK),
                    Triple(str("menu_tiles_playlist"), PlayerPreferences.MENU_PLAYLIST, com.alananasss.kittytune.ui.main.MenuTiles.PLAYLIST),
                ).forEach { (label, menu, catalogue) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    val hiddenHere = if (menu == PlayerPreferences.MENU_TRACK) hiddenTrackTiles else hiddenPlaylistTiles
                    catalogue.forEach { tile ->
                        val shown = tile.id !in hiddenHere
                        SwitchRow(title = str(tile.labelKey), checked = shown) {
                            val next = if (shown) hiddenHere + tile.id else hiddenHere - tile.id
                            prefs.setHiddenMenuTiles(menu, next)
                            if (menu == PlayerPreferences.MENU_TRACK) hiddenTrackTiles = next else hiddenPlaylistTiles = next
                        }
                    }
                    TextButton(onClick = {
                        prefs.resetMenuTiles(menu)
                        if (menu == PlayerPreferences.MENU_TRACK) hiddenTrackTiles = emptySet() else hiddenPlaylistTiles = emptySet()
                    }) { Text(str("menu_tiles_reset")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("btn_close")) } },
    )
}

/** A compact switch row whose whole width is the target, for lists inside dialogs and small windows. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // The row is the target; the switch only shows the state, so one click never toggles twice.
        com.alananasss.kittytune.ui.common.SettingsSwitch(checked = checked, onCheckedChange = null)
    }
}

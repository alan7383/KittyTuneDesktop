package com.alananasss.kittytune.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.data.local.AppThemeMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.scheme.DynamicScheme

object ThemeState {
    var previewKeyColor by mutableStateOf<Int?>(null)
}

internal val KittyTuneDefaultSeedColor = Color(0xFFFF7A1A)
internal val MaterialKolorColorSpecOptions = listOf("SPEC_2025", "SPEC_2021")

internal fun parseMaterialKolorPaletteStyle(colorStyle: String): PaletteStyle =
    PaletteStyle.entries.firstOrNull { it.name.equals(colorStyle.trim(), ignoreCase = true) }
        ?: PaletteStyle.Expressive

internal fun parseMaterialKolorColorSpec(colorSpec: String): ColorSpec.SpecVersion =
    when (colorSpec.trim().uppercase()) {
        "SPEC_2021", "2021", "MATERIAL_2021" -> ColorSpec.SpecVersion.SPEC_2021
        "SPEC_2025", "2025", "MATERIAL_2025", "DEFAULT" -> ColorSpec.SpecVersion.SPEC_2025
        else -> ColorSpec.SpecVersion.SPEC_2025
    }

internal fun normalizedMaterialKolorColorSpecName(colorSpec: String): String =
    when (parseMaterialKolorColorSpec(colorSpec)) {
        ColorSpec.SpecVersion.SPEC_2021 -> "SPEC_2021"
        ColorSpec.SpecVersion.SPEC_2025 -> "SPEC_2025"
    }

@Composable
internal fun rememberSoundTuneColorScheme(
    useDarkTheme: Boolean,
    dynamicColor: Boolean,
    pureBlack: Boolean,
    keyColor: Int,
    colorStyle: String,
    colorSpec: String,
): ColorScheme {
    val style = remember(colorStyle) { parseMaterialKolorPaletteStyle(colorStyle) }
    val specVersion = remember(colorSpec) { parseMaterialKolorColorSpec(colorSpec) }
    
    val effectiveKeyColor = ThemeState.previewKeyColor ?: keyColor
    val seedColor = remember(effectiveKeyColor) {
        if (effectiveKeyColor != 0) Color(effectiveKeyColor) else KittyTuneDefaultSeedColor
    }

    return rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = useDarkTheme,
        isAmoled = pureBlack,
        style = style,
        specVersion = specVersion,
        platform = DynamicScheme.Platform.PHONE,
        modifyColorScheme = { scheme ->
            if (pureBlack && useDarkTheme) scheme.withAmoledSurfaces() else scheme
        }
    )
}

private fun ColorScheme.withAmoledSurfaces(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color(0xFF121212),
        surfaceContainerHighest = Color(0xFF181818)
    )

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoundTuneTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    keyColor: Int = 0,
    colorStyle: String = "System",
    colorSpec: String = "SPEC_2025",
    typography: androidx.compose.material3.Typography = Typography,
    content: @Composable () -> Unit,
) {
    val systemInDark = isSystemInDarkTheme()

    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemInDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = rememberSoundTuneColorScheme(
        useDarkTheme = useDarkTheme,
        dynamicColor = dynamicColor,
        pureBlack = pureBlack,
        keyColor = keyColor,
        colorStyle = colorStyle,
        colorSpec = colorSpec,
    )

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
        motionScheme = MotionScheme.expressive(),
    )
}
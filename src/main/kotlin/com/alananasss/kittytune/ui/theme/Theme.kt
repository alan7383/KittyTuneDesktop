package com.alananasss.kittytune.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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

    /** Dominant color extracted from the current track artwork (null = none). */
    var coverSeedColor by mutableStateOf<Int?>(null)

    /**
     * Several of the artwork's colours, brightest first, for the full player's moving background.
     *
     * Separate from [coverSeedColor] because they answer different questions: the seed is "what colour is this
     * record", which is what a Material palette wants, and this is "what colours are *in* it", which is what
     * a mesh gradient wants. Deriving the second from the first is what produced a flat grey rectangle for a
     * black sleeve (issue #33).
     *
     * Empty until a cover has been read, and empty again for a track with no real artwork.
     */
    var coverMeshColors by mutableStateOf<List<Int>>(emptyList())
}

internal val KittyTuneDefaultSeedColor = Color(0xFFFF7A1A)

/**
 * How long the palette takes to travel to a new cover's colour. Long enough to read as a
 * transition rather than a flash, short enough that the app has finished changing before you have
 * finished looking at the new track.
 */
private const val SEED_TRANSITION_MS = 450
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
    // Dynamic theme, seeded from the current cover, outranks the fixed-accent styles.
    // Both "end4 (Material You)" and "Windows Accent" used to return from this function before
    // the cover seed was ever read, so with either of them picked — and the setup screen offers
    // both — the Dynamic Theme switch silently did nothing at all (issue #33). Read the seed
    // first and let it take over when there is one; the accent styles still apply on their own
    // whenever the switch is off or no artwork colour has been extracted yet.
    val coverSeed = if (dynamicColor) ThemeState.coverSeedColor else null

    if (coverSeed == null && colorStyle.contains("end4", ignoreCase = true)) {
        val end4Colors by com.alananasss.kittytune.data.theme.End4ThemeManager.colorsMap.collectAsState()
        @Suppress("UNUSED_VARIABLE")
        val unused = end4Colors
        val fallbackScheme = rememberDynamicColorScheme(
            seedColor = KittyTuneDefaultSeedColor,
            isDark = useDarkTheme,
            isAmoled = pureBlack,
            style = PaletteStyle.Expressive,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            platform = DynamicScheme.Platform.PHONE,
        )
        return com.alananasss.kittytune.data.theme.End4ThemeManager.buildColorScheme(fallbackScheme, pureBlack && useDarkTheme)
    }

    val wantsWindowsAccent = colorStyle.contains("windows", ignoreCase = true)
    if (coverSeed == null && wantsWindowsAccent) {
        val isWindowsOS = System.getProperty("os.name").lowercase().contains("win")
        if (isWindowsOS) {
            com.alananasss.kittytune.data.theme.WindowsThemeManager.startWatching()
            val winColor by com.alananasss.kittytune.data.theme.WindowsThemeManager.accentColor.collectAsState()
            val effectiveWinColor = ThemeState.previewKeyColor?.let { Color(it) } ?: winColor ?: KittyTuneDefaultSeedColor
            
            val style = PaletteStyle.Fidelity
            val specVersion = parseMaterialKolorColorSpec(colorSpec)
            
            return rememberDynamicColorScheme(
                seedColor = effectiveWinColor,
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
    }
    if (!wantsWindowsAccent) {
        com.alananasss.kittytune.data.theme.WindowsThemeManager.stopWatching()
    }

    val style = remember(colorStyle) { parseMaterialKolorPaletteStyle(colorStyle) }
    val specVersion = remember(colorSpec) { parseMaterialKolorColorSpec(colorSpec) }

    val effectiveKeyColor = ThemeState.previewKeyColor ?: keyColor
    // The whole palette is seeded from the current track's dominant artwork colour, falling
    // back to the user's key colour when there is none.
    val seedColor = remember(effectiveKeyColor, coverSeed, useDarkTheme) {
        when {
            coverSeed != null -> Color(coverSeed)
            effectiveKeyColor != 0 -> Color(effectiveKeyColor)
            else -> KittyTuneDefaultSeedColor
        }
    }

    val target = rememberDynamicColorScheme(
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

    // Snapped rather than eased while the colour picker is being dragged: there the whole point is
    // that the app follows your finger, and a tween would only lag behind it.
    return target.easedInto(instant = ThemeState.previewKeyColor != null)
}

/**
 * Eases the palette towards this scheme instead of repainting the app in a single frame.
 *
 * The interpolation is over the resolved colours, not over the seed. Building a scheme from a seed
 * costs about 5 ms here, a third of a 60 Hz frame, so animating the seed would mean paying that on
 * every frame of the transition. Both ends of the interpolation are schemes materialkolor built, so
 * lerping between them stays coherent while costing a lerp per slot.
 */
@Composable
private fun ColorScheme.easedInto(instant: Boolean): ColorScheme {
    val spec: AnimationSpec<Color> = remember(instant) {
        if (instant) snap() else tween(durationMillis = SEED_TRANSITION_MS, easing = FastOutSlowInEasing)
    }

    @Composable
    fun ease(color: Color, label: String): Color =
        animateColorAsState(targetValue = color, animationSpec = spec, label = label).value

    return copy(
        primary = ease(primary, "primary"),
        onPrimary = ease(onPrimary, "onPrimary"),
        primaryContainer = ease(primaryContainer, "primaryContainer"),
        onPrimaryContainer = ease(onPrimaryContainer, "onPrimaryContainer"),
        inversePrimary = ease(inversePrimary, "inversePrimary"),
        secondary = ease(secondary, "secondary"),
        onSecondary = ease(onSecondary, "onSecondary"),
        secondaryContainer = ease(secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = ease(onSecondaryContainer, "onSecondaryContainer"),
        tertiary = ease(tertiary, "tertiary"),
        onTertiary = ease(onTertiary, "onTertiary"),
        tertiaryContainer = ease(tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = ease(onTertiaryContainer, "onTertiaryContainer"),
        background = ease(background, "background"),
        onBackground = ease(onBackground, "onBackground"),
        surface = ease(surface, "surface"),
        onSurface = ease(onSurface, "onSurface"),
        surfaceVariant = ease(surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = ease(onSurfaceVariant, "onSurfaceVariant"),
        surfaceTint = ease(surfaceTint, "surfaceTint"),
        inverseSurface = ease(inverseSurface, "inverseSurface"),
        inverseOnSurface = ease(inverseOnSurface, "inverseOnSurface"),
        error = ease(error, "error"),
        onError = ease(onError, "onError"),
        errorContainer = ease(errorContainer, "errorContainer"),
        onErrorContainer = ease(onErrorContainer, "onErrorContainer"),
        outline = ease(outline, "outline"),
        outlineVariant = ease(outlineVariant, "outlineVariant"),
        scrim = ease(scrim, "scrim"),
        surfaceBright = ease(surfaceBright, "surfaceBright"),
        surfaceDim = ease(surfaceDim, "surfaceDim"),
        surfaceContainer = ease(surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = ease(surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerHighest = ease(surfaceContainerHighest, "surfaceContainerHighest"),
        surfaceContainerLow = ease(surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainerLowest = ease(surfaceContainerLowest, "surfaceContainerLowest"),
        primaryFixed = ease(primaryFixed, "primaryFixed"),
        primaryFixedDim = ease(primaryFixedDim, "primaryFixedDim"),
        onPrimaryFixed = ease(onPrimaryFixed, "onPrimaryFixed"),
        onPrimaryFixedVariant = ease(onPrimaryFixedVariant, "onPrimaryFixedVariant"),
        secondaryFixed = ease(secondaryFixed, "secondaryFixed"),
        secondaryFixedDim = ease(secondaryFixedDim, "secondaryFixedDim"),
        onSecondaryFixed = ease(onSecondaryFixed, "onSecondaryFixed"),
        onSecondaryFixedVariant = ease(onSecondaryFixedVariant, "onSecondaryFixedVariant"),
        tertiaryFixed = ease(tertiaryFixed, "tertiaryFixed"),
        tertiaryFixedDim = ease(tertiaryFixedDim, "tertiaryFixedDim"),
        onTertiaryFixed = ease(onTertiaryFixed, "onTertiaryFixed"),
        onTertiaryFixedVariant = ease(onTertiaryFixedVariant, "onTertiaryFixedVariant"),
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
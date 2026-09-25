package com.alananasss.kittytune.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.unit.dp
/**
 * Reactive theme wrapper: applies SoundTuneTheme + the dynamic variable-font typography and
 * rebuilds them live when a theme setting changes.
 *
 * Only when a *theme* setting changes. This used to observe the whole preference map, and the player
 * writes its position there every five seconds — so every five seconds the colour scheme was generated
 * again and a new Typography handed down, recomposing every piece of text in the app.
 */
@Composable
fun KittyTuneTheme(content: @Composable () -> Unit) {
    val prefs = remember { PlayerPreferences() }
    val themePrefs by remember {
        Prefs.flow.map { prefs.readThemePrefs() }.distinctUntilChanged()
    }.collectAsState(initial = remember { prefs.readThemePrefs() })

    if (com.alananasss.kittytune.core.Strings.appLanguage != themePrefs.appLanguage) {
        com.alananasss.kittytune.core.Strings.appLanguage = themePrefs.appLanguage
    }

    val keyColor = ThemeState.previewKeyColor ?: themePrefs.keyColor
    val font = themePrefs.font
    val typography = remember(font) {
        if (font == null) {
            Typography
        } else {
            getDynamicTypography(
                useCustomFont = true,
                wght = font.wght,
                wdth = font.wdth,
                slnt = font.slnt,
                rond = font.rond,
                grad = font.grad,
                opsz = font.opsz,
            )
        }
    }

    SoundTuneTheme(
        themeMode = themePrefs.themeMode,
        dynamicColor = themePrefs.dynamicColor,
        trackDynamicColor = themePrefs.trackDynamicColor,
        pureBlack = themePrefs.pureBlack,
        keyColor = keyColor,
        colorStyle = themePrefs.colorStyle,
        colorSpec = themePrefs.colorSpec,
        typography = typography,
    ) {
        val scrollbarStyle = androidx.compose.foundation.defaultScrollbarStyle().copy(
            thickness = 8.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            hoverColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            unhoverColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            hoverDurationMillis = 300
        )

        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.foundation.LocalScrollbarStyle provides scrollbarStyle
        ) {
            content()
        }
    }
}

/** Every preference the theme depends on, compared as a value so unrelated writes are ignored. */
private data class ThemePrefs(
    val appLanguage: String,
    val themeMode: AppThemeMode,
    val dynamicColor: Boolean,
    val trackDynamicColor: Boolean,
    val pureBlack: Boolean,
    val keyColor: Int,
    val colorStyle: String,
    val colorSpec: String,
    /** Null when the custom variable font is off. */
    val font: FontAxes?,
)

private data class FontAxes(
    val wght: Int,
    val wdth: Float,
    val slnt: Float,
    val rond: Float,
    val grad: Float,
    val opsz: Float,
)

private fun PlayerPreferences.readThemePrefs() = ThemePrefs(
    appLanguage = getAppLanguage().code,
    themeMode = getThemeMode(),
    dynamicColor = getDynamicTheme(),
    trackDynamicColor = getTrackDynamicTheme(),
    pureBlack = getPureBlack(),
    keyColor = getKeyColor(),
    colorStyle = getColorStyle(),
    colorSpec = getColorSpec(),
    font = if (getCustomFontEnabled()) {
        FontAxes(getFontWght(), getFontWdth(), getFontSlnt(), getFontRond(), getFontGrad(), getFontOpsz())
    } else {
        null
    },
)

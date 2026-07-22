package com.alananasss.kittytune.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import com.alananasss.kittytune.core.Prefs
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.flow.map
import androidx.compose.ui.unit.dp
/**
 * Reactive theme wrapper: reads the theme-related prefs (rebuilt live whenever any of them
 * change, like the Android SharedPreferences listener) and applies SoundTuneTheme +
 * the dynamic variable-font typography.
 */
@Composable
fun KittyTuneTheme(content: @Composable () -> Unit) {
    val prefs = remember { PlayerPreferences() }

    // Collect the whole pref map once; recompute derived theme values on any change.
    val prefsSnapshot by Prefs.flow.collectAsState()

    // Reading through the snapshot dependency makes this recompose on pref writes.
    prefsSnapshot // touch

    val appLangCode = prefs.getAppLanguage().code
    if (com.alananasss.kittytune.core.Strings.appLanguage != appLangCode) {
        com.alananasss.kittytune.core.Strings.appLanguage = appLangCode
    }

    val themeMode = prefs.getThemeMode()
    val dynamicColor = prefs.getDynamicTheme()
    val pureBlack = prefs.getPureBlack()
    val keyColor = ThemeState.previewKeyColor ?: prefs.getKeyColor()
    val colorStyle = prefs.getColorStyle()
    val colorSpec = prefs.getColorSpec()

    val typography = if (prefs.getCustomFontEnabled()) {
        getDynamicTypography(
            useCustomFont = true,
            wght = prefs.getFontWght(),
            wdth = prefs.getFontWdth(),
            slnt = prefs.getFontSlnt(),
            rond = prefs.getFontRond(),
            grad = prefs.getFontGrad(),
            opsz = prefs.getFontOpsz(),
        )
    } else {
        Typography
    }

    SoundTuneTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        pureBlack = pureBlack,
        keyColor = keyColor,
        colorStyle = colorStyle,
        colorSpec = colorSpec,
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

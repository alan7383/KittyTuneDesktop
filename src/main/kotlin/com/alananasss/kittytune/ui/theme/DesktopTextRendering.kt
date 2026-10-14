@file:OptIn(
    androidx.compose.ui.text.ExperimentalTextApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.alananasss.kittytune.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.FontHinting
import androidx.compose.ui.text.FontRasterizationSettings
import androidx.compose.ui.text.FontSmoothing
import androidx.compose.ui.text.PlatformParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle

/** Rasterize static Windows UI text on physical pixels, including at 125% and 150% DPI. */
internal fun Typography.withDesktopTextRendering(
    isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
): Typography {
    if (!isWindows) return this
    val rendering = PlatformTextStyle(
        spanStyle = null,
        paragraphStyle = PlatformParagraphStyle(
            FontRasterizationSettings(
                smoothing = FontSmoothing.AntiAlias,
                hinting = FontHinting.Normal,
                // Compose uses grayscale surfaces. Fractional glyph origins soften small
                // labels on these surfaces; align glyphs without changing font or size.
                subpixelPositioning = false,
                autoHintingForced = false,
            )
        ),
    )
    return copy(
        displayLarge = displayLarge.copy(platformStyle = rendering),
        displayMedium = displayMedium.copy(platformStyle = rendering),
        displaySmall = displaySmall.copy(platformStyle = rendering),
        headlineLarge = headlineLarge.copy(platformStyle = rendering),
        headlineMedium = headlineMedium.copy(platformStyle = rendering),
        headlineSmall = headlineSmall.copy(platformStyle = rendering),
        titleLarge = titleLarge.copy(platformStyle = rendering),
        titleMedium = titleMedium.copy(platformStyle = rendering),
        titleSmall = titleSmall.copy(platformStyle = rendering),
        bodyLarge = bodyLarge.copy(platformStyle = rendering),
        bodyMedium = bodyMedium.copy(platformStyle = rendering),
        bodySmall = bodySmall.copy(platformStyle = rendering),
        labelLarge = labelLarge.copy(platformStyle = rendering),
        labelMedium = labelMedium.copy(platformStyle = rendering),
        labelSmall = labelSmall.copy(platformStyle = rendering),
        displayLargeEmphasized = displayLargeEmphasized.copy(platformStyle = rendering),
        displayMediumEmphasized = displayMediumEmphasized.copy(platformStyle = rendering),
        displaySmallEmphasized = displaySmallEmphasized.copy(platformStyle = rendering),
        headlineLargeEmphasized = headlineLargeEmphasized.copy(platformStyle = rendering),
        headlineMediumEmphasized = headlineMediumEmphasized.copy(platformStyle = rendering),
        headlineSmallEmphasized = headlineSmallEmphasized.copy(platformStyle = rendering),
        titleLargeEmphasized = titleLargeEmphasized.copy(platformStyle = rendering),
        titleMediumEmphasized = titleMediumEmphasized.copy(platformStyle = rendering),
        titleSmallEmphasized = titleSmallEmphasized.copy(platformStyle = rendering),
        bodyLargeEmphasized = bodyLargeEmphasized.copy(platformStyle = rendering),
        bodyMediumEmphasized = bodyMediumEmphasized.copy(platformStyle = rendering),
        bodySmallEmphasized = bodySmallEmphasized.copy(platformStyle = rendering),
        labelLargeEmphasized = labelLargeEmphasized.copy(platformStyle = rendering),
        labelMediumEmphasized = labelMediumEmphasized.copy(platformStyle = rendering),
        labelSmallEmphasized = labelSmallEmphasized.copy(platformStyle = rendering),
    )
}

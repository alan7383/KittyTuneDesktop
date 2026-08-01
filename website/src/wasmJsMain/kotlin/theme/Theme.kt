package com.alananasss.kittytunewebsite.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import kittytunewindows.website.generated.resources.Res

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalTextApi::class)
@Composable
fun KittyTuneWebTheme(
    seedColor: Color = Color(0xFF1976D2),
    paletteStyle: PaletteStyle = PaletteStyle.Vibrant,
    content: @Composable () -> Unit
) {
    val colorScheme = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = true,
        style = paletteStyle,
        isAmoled = false,
        specVersion = ColorSpec.SpecVersion.SPEC_2025
    )

    val fontBytes by produceState<ByteArray?>(null) {
        value = Res.readBytes("font/google_sans_flex.ttf")
    }

    if (fontBytes != null) {
        val bytes = fontBytes!!
        val weights = listOf(
            FontWeight.W100, FontWeight.W200, FontWeight.W300,
            FontWeight.W400, FontWeight.W500, FontWeight.W600,
            FontWeight.W700, FontWeight.W800, FontWeight.W900
        )

        val customFamily = FontFamily(
            weights.map { fw ->
                Font(
                    identity = "GoogleSansFlex-${fw.weight}",
                    data = bytes,
                    weight = fw,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(fw.weight),
                        FontVariation.width(100f),
                        FontVariation.slant(0f),
                        FontVariation.Setting("ROND", 0f),
                        FontVariation.Setting("GRAD", 0f),
                        FontVariation.Setting("opsz", 14f)
                    )
                )
            }
        )

        val customFamilyRounded = FontFamily(
            weights.map { fw ->
                Font(
                    identity = "GoogleSansFlexRounded-${fw.weight}",
                    data = bytes,
                    weight = fw,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(fw.weight),
                        FontVariation.width(100f),
                        FontVariation.slant(0f),
                        FontVariation.Setting("ROND", 100f),
                        FontVariation.Setting("GRAD", 0f),
                        FontVariation.Setting("opsz", 14f)
                    )
                )
            }
        )

        val defaultTypography = Typography()

        val customTypography = Typography(
            displayLarge = defaultTypography.displayLarge.copy(fontFamily = customFamilyRounded),
            displayMedium = defaultTypography.displayMedium.copy(fontFamily = customFamilyRounded),
            displaySmall = defaultTypography.displaySmall.copy(fontFamily = customFamilyRounded),
            headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = customFamilyRounded),
            headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = customFamilyRounded),
            headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = customFamilyRounded),
            titleLarge = defaultTypography.titleLarge.copy(fontFamily = customFamilyRounded),
            titleMedium = defaultTypography.titleMedium.copy(fontFamily = customFamilyRounded),
            titleSmall = defaultTypography.titleSmall.copy(fontFamily = customFamilyRounded),
            bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = customFamily),
            bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = customFamily),
            bodySmall = defaultTypography.bodySmall.copy(fontFamily = customFamily),
            labelLarge = defaultTypography.labelLarge.copy(fontFamily = customFamily),
            labelMedium = defaultTypography.labelMedium.copy(fontFamily = customFamily),
            labelSmall = defaultTypography.labelSmall.copy(fontFamily = customFamily)
        )

        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            typography = customTypography,
            content = content
        )
    } else {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}

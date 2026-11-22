package com.alananasss.kittytune.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.scheme.DynamicScheme
import org.junit.Test
import kotlin.test.assertTrue

/** Samples dynamic palettes and checks the contrast of the Together host badge. */
class AccentContrastSurveyTest {
    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance() + 0.05
        val l2 = b.luminance() + 0.05
        return maxOf(l1, l2) / minOf(l1, l2)
    }

    @Test fun survey() {
        val seeds = (0 until 24).map { Color.hsv(it * 15f, 0.75f, 0.85f) } +
            listOf(Color(0xFF0080FF.toInt()), Color(0xFF6382FF.toInt()), Color(0xFFFF5500.toInt()), Color(0xFF808080.toInt()))
        val schemes = mutableListOf<Triple<String, Boolean, ColorScheme>>()
        for (style in PaletteStyle.entries) for (dark in listOf(false, true)) for (spec in ColorSpec.SpecVersion.entries) for (seed in seeds) {
            val scheme = dynamicColorScheme(
                seedColor = seed, isDark = dark, isAmoled = false, style = style, specVersion = spec,
                platform = DynamicScheme.Platform.PHONE,
            )
            val badgeContrast = contrast(scheme.onPrimaryContainer, scheme.primaryContainer)
            assertTrue(badgeContrast >= 4.5, "Host badge contrast $badgeContrast for $style/$spec (dark=$dark, seed=$seed)")
            schemes += Triple("$style/$spec", dark, scheme)
        }
        val pairs = mapOf<String, (ColorScheme) -> Pair<Color, Color>>(
            "primary on primaryContainer" to { it.primary to it.primaryContainer },
            "secondary on primaryContainer" to { it.secondary to it.primaryContainer },
            "onPrimaryContainer on primaryContainer" to { it.onPrimaryContainer to it.primaryContainer },
            "primary on secondaryContainer" to { it.primary to it.secondaryContainer },
            "secondary on secondaryContainer" to { it.secondary to it.secondaryContainer },
            "primary on tertiaryContainer" to { it.primary to it.tertiaryContainer },
            "secondary on tertiaryContainer" to { it.secondary to it.tertiaryContainer },
            "primary on primary" to { it.primary to it.primary },
            "secondary on primary" to { it.secondary to it.primary },
            "primary on inversePrimary" to { it.primary to it.inversePrimary },
            "primary on surfaceContainerHighest" to { it.primary to it.surfaceContainerHighest },
            "secondary on surfaceContainerHighest" to { it.secondary to it.surfaceContainerHighest },
            "primary on surfaceBright" to { it.primary to it.surfaceBright },
            "primary on surface" to { it.primary to it.surface },
        )
        fun over(top: Color, alpha: Float, bottom: Color) = Color(
            red = top.red * alpha + bottom.red * (1 - alpha),
            green = top.green * alpha + bottom.green * (1 - alpha),
            blue = top.blue * alpha + bottom.blue * (1 - alpha),
        )
        val blended = mutableMapOf<String, (ColorScheme) -> Pair<Color, Color>>()
        for (a in listOf(0.12f, 0.15f, 0.18f, 0.22f, 0.25f, 0.3f, 0.35f, 0.45f, 0.5f, 0.55f, 0.6f, 0.7f)) {
            blended["primary on primaryContainer@$a"] = { it.primary to over(it.primaryContainer, a, it.surfaceContainer) }
            blended["primary on primary@$a"] = { it.primary to over(it.primary, a, it.surfaceContainer) }
            blended["secondary on secondary@$a"] = { it.secondary to over(it.secondary, a, it.surfaceContainer) }
        }
        for (a in listOf(0.45f, 0.5f, 0.55f)) {
            blended["onPrimaryContainer on primaryContainer@$a"] = {
                it.onPrimaryContainer to over(it.primaryContainer, a, it.surfaceContainer)
            }
        }
        for ((name, pick) in pairs + blended) {
            val values = schemes.map { (label, dark, scheme) -> Triple(label, dark, pick(scheme).let { contrast(it.first, it.second) }) }
            val worst = values.minBy { it.third }
            val below3 = values.count { it.third < 3.0 }
            println("SURVEY %-40s min %.2f median %.2f  <3:1 in %d/%d  worst=%s dark=%s".format(
                name, worst.third, values.map { it.third }.sorted()[values.size / 2], below3, values.size, worst.first, worst.second))
        }
    }
}

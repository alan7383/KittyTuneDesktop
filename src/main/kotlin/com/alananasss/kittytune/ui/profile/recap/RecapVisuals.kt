@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.alananasss.kittytune.ui.profile.recap

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.RecapKind
import com.alananasss.kittytune.data.stats.RecapPeriod
import com.alananasss.kittytune.data.stats.RecapPersona
import com.alananasss.kittytune.data.stats.Season
import java.time.format.TextStyle
import java.util.Locale

/** A recap's colours: a container and what sits on it, plus an accent for figures. */
internal data class RecapPalette(val container: Color, val onContainer: Color, val accent: Color)

/**
 * Each kind of recap has its own colour role, so a row of them reads at a glance: months on the
 * primary container, each season on its own, the year on the full-strength primary as the headline.
 * Roles rather than fixed colours, so it follows the user's wallpaper and the dark theme like the
 * rest of the app.
 */
internal fun RecapPeriod.palette(scheme: ColorScheme): RecapPalette = when {
    kind == RecapKind.YEAR -> RecapPalette(scheme.primary, scheme.onPrimary, scheme.primaryContainer)
    kind == RecapKind.MONTH -> RecapPalette(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
    season == Season.SPRING -> RecapPalette(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary)
    season == Season.SUMMER -> RecapPalette(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary)
    season == Season.AUTUMN -> RecapPalette(scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer)
    // Winter: the inverse surface — crisp light on a near-black row, like snow on a dark morning.
    // surfaceContainerHighest washed out against the background and left the card hard to read.
    else -> RecapPalette(scheme.inverseSurface, scheme.inverseOnSurface, scheme.inversePrimary)
}

/** The expressive shape that stands for each kind: a flower for spring, a sun for summer… */
internal fun RecapPeriod.polygon(): RoundedPolygon = when {
    kind == RecapKind.YEAR -> MaterialShapes.Cookie12Sided
    kind == RecapKind.MONTH -> MaterialShapes.Cookie9Sided
    season == Season.SPRING -> MaterialShapes.Flower
    season == Season.SUMMER -> MaterialShapes.Sunny
    season == Season.AUTUMN -> MaterialShapes.Clover4Leaf
    else -> MaterialShapes.SoftBurst
}

internal fun RecapPeriod.icon(): ImageVector = when {
    kind == RecapKind.YEAR -> Icons.Rounded.Celebration
    kind == RecapKind.MONTH -> Icons.Rounded.CalendarMonth
    season == Season.SPRING -> Icons.Rounded.LocalFlorist
    season == Season.SUMMER -> Icons.Rounded.WbSunny
    season == Season.AUTUMN -> Icons.Rounded.Park
    else -> Icons.Rounded.AcUnit
}

/** "October 2026", "Summer 2026", "Winter 2025–26", "2026". */
@Composable
internal fun RecapPeriod.title(): String {
    val locale = Locale.getDefault()
    return when (kind) {
        RecapKind.MONTH -> start.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) } + " ${start.year}"
        RecapKind.SEASON -> {
            val name = str(season.nameKey())
            if (end.year != start.year) "$name ${start.year}–${end.year % 100}" else "$name ${start.year}"
        }
        RecapKind.YEAR -> start.year.toString()
    }
}

/** "Monthly recap", "Seasonal recap", "Yearly recap". */
@Composable
internal fun RecapPeriod.kindLabel(): String = str(
    when (kind) {
        RecapKind.MONTH -> "recaps_kind_month"
        RecapKind.SEASON -> "recaps_kind_season"
        RecapKind.YEAR -> "recaps_kind_year"
    }
)

internal fun Season?.nameKey(): String = when (this) {
    Season.SPRING -> "recaps_season_spring"
    Season.SUMMER -> "recaps_season_summer"
    Season.AUTUMN -> "recaps_season_autumn"
    else -> "recaps_season_winter"
}

internal fun RecapPersona.titleKey(): String = when (this) {
    RecapPersona.NIGHT_OWL -> "recap_persona_night_owl"
    RecapPersona.EARLY_BIRD -> "recap_persona_early_bird"
    RecapPersona.LOYAL_FAN -> "recap_persona_loyal_fan"
    RecapPersona.REPLAYER -> "recap_persona_replayer"
    RecapPersona.EXPLORER -> "recap_persona_explorer"
    RecapPersona.MARATHONER -> "recap_persona_marathoner"
    RecapPersona.DEVOTED -> "recap_persona_devoted"
    RecapPersona.ECLECTIC -> "recap_persona_eclectic"
}

internal fun RecapPersona.descriptionKey(): String = when (this) {
    RecapPersona.NIGHT_OWL -> "recap_persona_night_owl_desc"
    RecapPersona.EARLY_BIRD -> "recap_persona_early_bird_desc"
    RecapPersona.LOYAL_FAN -> "recap_persona_loyal_fan_desc"
    RecapPersona.REPLAYER -> "recap_persona_replayer_desc"
    RecapPersona.EXPLORER -> "recap_persona_explorer_desc"
    RecapPersona.MARATHONER -> "recap_persona_marathoner_desc"
    RecapPersona.DEVOTED -> "recap_persona_devoted_desc"
    RecapPersona.ECLECTIC -> "recap_persona_eclectic_desc"
}

internal fun RecapPersona.icon(): ImageVector = when (this) {
    RecapPersona.NIGHT_OWL -> Icons.Rounded.Bedtime
    RecapPersona.EARLY_BIRD -> Icons.Rounded.WbTwilight
    RecapPersona.LOYAL_FAN -> Icons.Rounded.Favorite
    RecapPersona.REPLAYER -> Icons.Rounded.Repeat
    RecapPersona.EXPLORER -> Icons.Rounded.Explore
    RecapPersona.MARATHONER -> Icons.Rounded.DirectionsRun
    RecapPersona.DEVOTED -> Icons.Rounded.AllInclusive
    RecapPersona.ECLECTIC -> Icons.Rounded.Shuffle
}

internal fun RecapPersona.polygon(): RoundedPolygon = when (this) {
    RecapPersona.NIGHT_OWL -> MaterialShapes.Ghostish
    RecapPersona.EARLY_BIRD -> MaterialShapes.Sunny
    RecapPersona.LOYAL_FAN -> MaterialShapes.Heart
    RecapPersona.REPLAYER -> MaterialShapes.Cookie7Sided
    RecapPersona.EXPLORER -> MaterialShapes.Gem
    RecapPersona.MARATHONER -> MaterialShapes.Burst
    RecapPersona.DEVOTED -> MaterialShapes.Clover8Leaf
    RecapPersona.ECLECTIC -> MaterialShapes.PixelCircle
}

/** An expressive shape turning slowly, as a decoration behind a card or a slide. */
@Composable
internal fun SpinningShape(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    periodMs: Int = 24_000,
    clockwise: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (clockwise) 360f else -360f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Box(
        modifier
            .graphicsLayer { rotationZ = angle }
            .clip(polygon.toShape())
            .background(color)
    )
}

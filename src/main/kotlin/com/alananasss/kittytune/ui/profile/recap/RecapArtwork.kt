package com.alananasss.kittytune.ui.profile.recap

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Offset
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.alananasss.kittytune.data.stats.RecapKind
import com.alananasss.kittytune.data.stats.RecapPeriod
import com.alananasss.kittytune.data.stats.Season
import java.time.Month
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The large decorative silhouette for a recap's card and story opening. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun RecapPeriod.artworkPolygon(): RoundedPolygon = when (kind) {
    RecapKind.MONTH -> when (start.month) {
        Month.JANUARY -> MaterialShapes.PixelCircle
        Month.FEBRUARY -> MaterialShapes.Heart
        Month.MARCH -> MaterialShapes.ClamShell
        Month.APRIL -> MaterialShapes.Clover4Leaf
        Month.MAY -> MaterialShapes.Puffy
        Month.JUNE -> MaterialShapes.Fan
        Month.JULY -> MaterialShapes.Triangle
        Month.AUGUST -> MaterialShapes.Arch
        Month.SEPTEMBER -> MaterialShapes.Cookie6Sided
        Month.OCTOBER -> MaterialShapes.Ghostish
        Month.NOVEMBER -> MaterialShapes.Slanted
        Month.DECEMBER -> MaterialShapes.PuffyDiamond
    }
    RecapKind.SEASON -> when (season) {
        Season.WINTER, null -> WinterSnowflake
        Season.SPRING -> MaterialShapes.Flower
        Season.SUMMER -> SummerSun
        Season.AUTUMN -> AutumnLeaf
    }
    RecapKind.YEAR -> YearStar
}

private val WinterSnowflake = radialPolygon(
    repeats = 6,
    // Outline of one arm, starting in the gap between two arms and circling its forked tip.
    section = listOf(
        Offset(0.13f, -0.075f),
        Offset(0.43f, -0.075f),
        Offset(0.66f, -0.30f),
        Offset(0.75f, -0.22f),
        Offset(0.60f, -0.075f),
        Offset(0.96f, -0.075f),
        Offset(1f, 0f),
        Offset(0.96f, 0.075f),
        Offset(0.60f, 0.075f),
        Offset(0.75f, 0.22f),
        Offset(0.66f, 0.30f),
        Offset(0.43f, 0.075f),
    ),
    rounding = CornerRounding(0.025f),
)

private val SummerSun = radialPolygon(
    repeats = 10,
    // One ray and the arc of the disc between rays.
    section = listOf(
        Offset(0.647f, -0.210f),
        Offset(0.675f, -0.085f),
        Offset(1f, 0f),
        Offset(0.675f, 0.085f),
    ),
    rounding = CornerRounding(0.04f),
)

private val AutumnLeaf = RoundedPolygon(
    vertices = floatArrayOf(
        0f, -1f,
        0.22f, -0.58f,
        0.43f, -0.67f,
        0.36f, -0.17f,
        0.90f, -0.39f,
        0.82f, -0.08f,
        1f, -0.04f,
        0.72f, 0.19f,
        0.84f, 0.31f,
        0.13f, 0.55f,
        0.10f, 0.91f,
        -0.10f, 0.91f,
        -0.13f, 0.55f,
        -0.84f, 0.31f,
        -0.72f, 0.19f,
        -1f, -0.04f,
        -0.82f, -0.08f,
        -0.90f, -0.39f,
        -0.36f, -0.17f,
        -0.43f, -0.67f,
        -0.22f, -0.58f,
    ),
    rounding = CornerRounding(0.055f),
    centerX = 0f,
    centerY = 0f,
).normalized()

private val YearStar = RoundedPolygon.star(
    numVerticesPerRadius = 5,
    innerRadius = 0.48f,
    rounding = CornerRounding(0.09f),
    innerRounding = CornerRounding(0.04f),
).normalized()

private fun radialPolygon(repeats: Int, section: List<Offset>, rounding: CornerRounding): RoundedPolygon {
    val vertices = (0 until repeats).flatMap { index ->
        val angle = index * 2 * PI / repeats
        val cosine = cos(angle).toFloat()
        val sine = sin(angle).toFloat()
        section.flatMap { (x, y) -> listOf(x * cosine - y * sine, x * sine + y * cosine) }
    }.toFloatArray()
    return RoundedPolygon(vertices, rounding, centerX = 0f, centerY = 0f).normalized()
}

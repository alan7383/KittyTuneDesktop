package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.FullPlayerBgStyle
import com.alananasss.kittytune.data.local.FullPlayerLayout
import com.alananasss.kittytune.ui.theme.animatePaletteColor

/**
 * Choices drawn as cards, two to a row: a sketch of what each does with its name under it.
 *
 * The same card as [LyricsDisplayStylePicker] draws, so every picture-choice in these settings looks and
 * behaves alike: a raised card, the chosen one tinted and outlined in the accent colour.
 */
@Composable
internal fun <T> SketchChoices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    sketch: @Composable (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { option ->
                    PreviewCard(
                        title = label(option),
                        isSelected = option == selected,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    ) { sketch(option) }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * A card with a picture on top and a name under it, drawn as the player-design settings draw theirs: a faint raised
 * surface with a hairline, and for the chosen one a tinted surface, a 2 dp accent border and the name in the accent colour.
 */
@Composable
internal fun PreviewCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    picture: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = animatePaletteColor(
        selected = isSelected,
        selectedColor = scheme.primaryContainer,
        unselectedColor = scheme.surfaceContainerHighest.copy(alpha = 0.5f),
        label = "previewCardContainer",
    )
    val borderColor = animatePaletteColor(
        selected = isSelected,
        selectedColor = scheme.primary,
        unselectedColor = scheme.outlineVariant.copy(alpha = 0.35f),
        label = "previewCardBorder",
    )
    val labelColor = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = modifier,
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(PREVIEW_PICTURE_HEIGHT).clip(RoundedCornerShape(10.dp))) { picture() }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = scheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val PREVIEW_PICTURE_HEIGHT = 76.dp

/** What a background style looks like, in a few shapes. */
@Composable
internal fun BackgroundSketch(style: FullPlayerBgStyle) {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cr = CornerRadius(h * 0.06f, h * 0.06f)
        when (style) {
            FullPlayerBgStyle.APPLE_MUSIC -> {
                drawRect(Color(0xFF14111D))
                drawSoftBlob(Offset(w * 0.22f, h * 0.28f), size.minDimension * 0.72f, scheme.primary, 0.75f)
                drawSoftBlob(Offset(w * 0.78f, h * 0.68f), size.minDimension * 0.70f, scheme.tertiary, 0.70f)
                drawSoftBlob(Offset(w * 0.56f, h * 0.22f), size.minDimension * 0.52f, scheme.secondary, 0.45f)
                drawMiniLyricPills(w, h, Color.White)
            }
            FullPlayerBgStyle.BLUR -> {
                drawRect(Color(0xFF1B1C22))
                drawSoftBlob(center, size.minDimension * 0.85f, scheme.primary, 0.40f)
                val artSize = h * 0.56f
                drawRoundRect(
                    color = scheme.primary.copy(alpha = 0.24f),
                    topLeft = Offset(center.x - artSize * 0.5f, center.y - artSize * 0.5f),
                    size = Size(artSize, artSize),
                    cornerRadius = CornerRadius(h * 0.12f, h * 0.12f),
                )
                drawRect(Color.Black.copy(alpha = 0.22f))
                drawMiniLyricPills(w, h, Color.White)
            }
            FullPlayerBgStyle.GRADIENT -> {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.78f),
                            scheme.primaryContainer.copy(alpha = 0.35f),
                            Color(0xFF0A0A0F),
                        ),
                        start = Offset.Zero,
                        end = Offset(w * 0.2f, h),
                    )
                )
                drawMiniLyricPills(w, h, Color.White)
            }
            FullPlayerBgStyle.PURE_BLACK -> {
                drawRect(Color.Black)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f),
                    topLeft = Offset(0.5f, 0.5f),
                    size = Size(w - 1f, h - 1f),
                    cornerRadius = cr,
                    style = Stroke(width = 1f),
                )
                drawMiniLyricPills(w, h, Color.White)
            }
        }
    }
}

/** Where the words sit against the cover, in a few shapes: a block for the cover, pills for the lines. */
@Composable
internal fun LayoutSketch(layout: FullPlayerLayout) {
    val scheme = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize().background(scheme.surfaceContainerLowest)) {
        val coverTint = scheme.primary.copy(alpha = 0.75f)
        val coverHighlight = scheme.onPrimary.copy(alpha = 0.16f)
        val activeWord = scheme.onSurface.copy(alpha = 0.88f)
        val dimWord = scheme.onSurfaceVariant.copy(alpha = 0.34f)
        val w = size.width
        val h = size.height
        val pillH = h * 0.085f
        val barCorner = CornerRadius(pillH * 0.5f, pillH * 0.5f)
        val coverCorner = CornerRadius(h * 0.10f, h * 0.10f)
        fun drawCoverBlock(left: Float, top: Float, side: Float, withBar: Boolean) {
            drawRoundRect(coverTint, Offset(left, top), Size(side, side), coverCorner)
            drawRoundRect(
                coverHighlight,
                Offset(left + side * 0.18f, top + side * 0.18f),
                Size(side * 0.64f, side * 0.24f),
                CornerRadius(side * 0.12f, side * 0.12f),
            )
            if (withBar) {
                drawRoundRect(
                    dimWord,
                    Offset(left, top + side + h * 0.08f),
                    Size(side, h * 0.055f),
                    barCorner,
                )
            }
        }
        when (layout) {
            FullPlayerLayout.LYRICS_RIGHT -> {
                val side = h * 0.54f
                drawCoverBlock(w * 0.11f, h * 0.15f, side, withBar = true)
                bars(activeWord, dimWord, w * 0.52f, w * 0.37f, h, centered = false)
            }
            FullPlayerLayout.LYRICS_LEFT -> {
                bars(activeWord, dimWord, w * 0.10f, w * 0.37f, h, centered = false)
                val side = h * 0.54f
                drawCoverBlock(w - w * 0.11f - side, h * 0.15f, side, withBar = true)
            }
            FullPlayerLayout.LYRICS_CENTRED -> {
                val thumb = h * 0.18f
                val headerW = thumb + w * 0.22f
                val startX = (w - headerW) * 0.5f
                drawRoundRect(coverTint, Offset(startX, h * 0.13f), Size(thumb, thumb), CornerRadius(h * 0.05f, h * 0.05f))
                drawRoundRect(dimWord, Offset(startX + thumb + w * 0.04f, h * 0.18f), Size(w * 0.18f, h * 0.075f), barCorner)
                bars(activeWord, dimWord, w * 0.26f, w * 0.48f, h, topFrac = 0.41f, stepFrac = 0.17f, centered = true)
            }
            FullPlayerLayout.COVER_AND_LINE -> {
                val side = h * 0.54f
                drawCoverBlock((w - side) * 0.5f, h * 0.12f, side, withBar = false)
                drawRoundRect(activeWord, Offset(w * 0.27f, h * 0.77f), Size(w * 0.46f, pillH), barCorner)
            }
        }
    }
}

private fun DrawScope.drawSoftBlob(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawMiniLyricPills(w: Float, h: Float, ink: Color) {
    val pillH = h * 0.085f
    val cr = CornerRadius(pillH * 0.5f, pillH * 0.5f)
    drawRoundRect(ink.copy(alpha = 0.78f), Offset(w * 0.16f, h * 0.26f), Size(w * 0.56f, pillH), cr)
    drawRoundRect(ink.copy(alpha = 0.34f), Offset(w * 0.16f, h * 0.46f), Size(w * 0.44f, pillH), cr)
    drawRoundRect(ink.copy(alpha = 0.22f), Offset(w * 0.16f, h * 0.66f), Size(w * 0.32f, pillH), cr)
}

/** Three rounded lyric pills, the first active and brighter. */
private fun DrawScope.bars(
    activeColor: Color,
    dimColor: Color,
    left: Float,
    maxWidth: Float,
    h: Float,
    topFrac: Float = 0.22f,
    stepFrac: Float = 0.22f,
    centered: Boolean = false,
) {
    val pillH = h * 0.085f
    val cr = CornerRadius(pillH * 0.5f, pillH * 0.5f)
    val lengths = listOf(1f, 0.78f, 0.56f)
    lengths.forEachIndexed { index, fraction ->
        val pw = maxWidth * fraction
        val x = if (centered) left + (maxWidth - pw) * 0.5f else left
        drawRoundRect(
            color = if (index == 0) activeColor else dimColor,
            topLeft = Offset(x, h * (topFrac + index * stepFrac)),
            size = Size(pw, pillH),
            cornerRadius = cr,
        )
    }
}

package com.alananasss.kittytune.ui.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.alananasss.kittytune.utils.Logger
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import java.awt.image.BufferedImage
import kotlin.math.abs

/**
 * Draws a track's artwork and its link as one square: a QR code whose modules are cut from the cover
 * (desktop port of Android PR #83, drawn with Skia instead of android.graphics).
 *
 * The modules are filled with the cover but forced dark and the ground forced light, so the contrast a scanner
 * needs does not depend on the cover. The three finder patterns are drawn solid. Error correction runs at H
 * (about 30 % recoverable) and the centre disc where the cover shows through stays well inside that budget.
 */
object QrCoverRenderer {

    private const val TAG = "QrCoverRenderer"

    /** Quiet zone in modules. Four is what the spec asks for. */
    private const val QUIET_ZONE = 4

    /** Side of the centre disc, as a fraction of the code. Stays within the H-level budget. */
    private const val CENTRE_FRACTION = 0.26f

    /** How far the module fill is pushed towards black, so a pale cover still reads as dark. */
    private const val MODULE_DARKEN = 0.45f

    private const val MID_LOW = 0.15f
    private const val MID_HIGH = 0.85f
    private const val HALFTONE_THRESHOLD = 0.60f
    private const val SAMPLE_STEPS = 64

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /**
     * [SOLID] keeps the code's own contrast and lets the artwork texture the modules. [HALFTONE] shows the cover
     * whole and carries the code in a grid of dots: the better picture, the harder scan.
     */
    enum class CoverCodeStyle { SOLID, HALFTONE }

    /** Halftone needs a cover with room to push dots both ways: judged by its share of mid-tones. */
    fun chooseStyle(cover: BufferedImage?): CoverCodeStyle {
        val image = cover ?: return CoverCodeStyle.SOLID
        return try {
            val stepX = (image.width / SAMPLE_STEPS).coerceAtLeast(1)
            val stepY = (image.height / SAMPLE_STEPS).coerceAtLeast(1)
            var mid = 0
            var total = 0
            var y = 0
            while (y < image.height) {
                var x = 0
                while (x < image.width) {
                    val p = image.getRGB(x, y)
                    val l = (0.2126f * ((p shr 16) and 0xFF) + 0.7152f * ((p shr 8) and 0xFF) + 0.0722f * (p and 0xFF)) / 255f
                    if (l in MID_LOW..MID_HIGH) mid++
                    total++
                    x += stepX
                }
                y += stepY
            }
            val share = if (total == 0) 0f else mid.toFloat() / total
            if (share >= HALFTONE_THRESHOLD) CoverCodeStyle.HALFTONE else CoverCodeStyle.SOLID
        } catch (e: Exception) {
            Logger.w(TAG, "Could not judge the cover, falling back to the robust style: ${e.message}")
            CoverCodeStyle.SOLID
        }
    }

    /** Renders [content] as a QR code textured with [cover]; null when it cannot be encoded. */
    fun render(
        content: String,
        cover: BufferedImage?,
        sizePx: Int,
        style: CoverCodeStyle = chooseStyle(cover),
    ): ImageBitmap? {
        if (content.isBlank() || sizePx <= 0) return null
        val matrix = try {
            Encoder.encode(content, ErrorCorrectionLevel.H, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")).matrix
        } catch (e: Exception) {
            Logger.w(TAG, "Could not encode '$content' as a QR code: ${e.message}")
            return null
        } ?: return null
        val coverImage = cover?.let { toSkiaImage(it) }
        val modules = matrix.width
        val total = modules + QUIET_ZONE * 2
        return if (style == CoverCodeStyle.HALFTONE && coverImage != null) {
            renderHalftone(matrix, modules, total, coverImage, sizePx)
        } else {
            renderSolid(matrix, modules, total, coverImage, sizePx)
        }
    }

    private fun renderSolid(matrix: ByteMatrix, modules: Int, total: Int, cover: Image?, sizePx: Int): ImageBitmap {
        // Whole-pixel modules: a fractional size leaves seams a scanner reads as noise.
        val moduleSize = (sizePx / total).coerceAtLeast(1)
        val side = moduleSize * total
        val origin = (moduleSize * QUIET_ZONE).toFloat()
        val surface = Surface.makeRasterN32Premul(side, side)
        val canvas = surface.canvas
        canvas.clear(WHITE)

        val modulePaint = Paint().apply {
            isAntiAlias = true
            color = BLACK
            if (cover != null) {
                shader = coverShader(cover, side.toFloat(), 0f, 0f)
                val k = 1f - MODULE_DARKEN
                colorFilter = ColorFilter.makeMatrix(
                    ColorMatrix(k, 0f, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 0f, k, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
                )
            }
        }
        val solid = Paint().apply { isAntiAlias = true; color = BLACK }
        val radius = moduleSize * 0.5f
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (matrix.get(x, y).toInt() != 1 || isFinder(x, y, modules) || isCentre(x, y, modules)) continue
                val left = origin + x * moduleSize
                val top = origin + y * moduleSize
                // Rounded modules read as artwork; a scanner samples the centre, which no corner touches.
                canvas.drawRRect(RRect.makeLTRB(left, top, left + moduleSize, top + moduleSize, radius), modulePaint)
            }
        }
        drawFinders(canvas, modules, moduleSize, origin, solid)
        if (cover != null) drawCentre(canvas, cover, side)
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    /** The cover whole, the code carried in one forced dot per module (the middle ninth of each). */
    private fun renderHalftone(matrix: ByteMatrix, modules: Int, total: Int, cover: Image, sizePx: Int): ImageBitmap {
        val subSize = (sizePx / (total * 3)).coerceAtLeast(1)
        val moduleSize = subSize * 3
        val side = moduleSize * total
        val origin = (moduleSize * QUIET_ZONE).toFloat()
        val codeSide = (moduleSize * modules).toFloat()
        val surface = Surface.makeRasterN32Premul(side, side)
        val canvas = surface.canvas
        canvas.clear(WHITE)
        canvas.drawRect(
            Rect.makeLTRB(origin, origin, origin + codeSide, origin + codeSide),
            Paint().apply { isAntiAlias = true; shader = coverShader(cover, codeSide, origin, origin) },
        )
        val dark = Paint().apply { color = BLACK }
        val light = Paint().apply { color = WHITE }
        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (isFinder(x, y, modules)) continue
                val left = origin + x * moduleSize + subSize
                val top = origin + y * moduleSize + subSize
                // Square: a circle would leave the sampled cell's corners to the artwork.
                canvas.drawRect(Rect.makeLTRB(left, top, left + subSize, top + subSize), if (matrix.get(x, y).toInt() == 1) dark else light)
            }
        }
        drawFinders(canvas, modules, moduleSize, origin, Paint().apply { isAntiAlias = true; color = BLACK })
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    /** The cover scaled to cover a [side] square placed at ([left], [top]), centred. */
    private fun coverShader(cover: Image, side: Float, left: Float, top: Float): Shader {
        val scale = side / minOf(cover.width, cover.height).toFloat()
        val tx = left - (cover.width * scale - side) / 2f
        val ty = top - (cover.height * scale - side) / 2f
        return cover.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, Matrix33(scale, 0f, tx, 0f, scale, ty, 0f, 0f, 1f))
    }

    private fun isFinder(x: Int, y: Int, modules: Int): Boolean {
        val near = 7
        return (x < near && y < near) || (x >= modules - near && y < near) || (x < near && y >= modules - near)
    }

    private fun isCentre(x: Int, y: Int, modules: Int): Boolean {
        val half = (modules * CENTRE_FRACTION) / 2f
        val mid = modules / 2f
        return abs(x + 0.5f - mid) < half && abs(y + 0.5f - mid) < half
    }

    /** The three corner squares, solid: what a scanner looks for first. */
    private fun drawFinders(canvas: org.jetbrains.skia.Canvas, modules: Int, moduleSize: Int, origin: Float, paint: Paint) {
        val hole = Paint().apply { isAntiAlias = true; color = WHITE }
        listOf(0 to 0, modules - 7 to 0, 0 to modules - 7).forEach { (cx, cy) ->
            val left = origin + cx * moduleSize
            val top = origin + cy * moduleSize
            val outer = (moduleSize * 7).toFloat()
            val m = moduleSize.toFloat()
            canvas.drawRRect(RRect.makeLTRB(left, top, left + outer, top + outer, m * 1.6f), paint)
            canvas.drawRRect(RRect.makeLTRB(left + m, top + m, left + outer - m, top + outer - m, m * 1.1f), hole)
            canvas.drawRRect(RRect.makeLTRB(left + 2 * m, top + 2 * m, left + outer - 2 * m, top + outer - 2 * m, m * 0.8f), paint)
        }
    }

    /** The cover, unaltered, in the disc left clear at the centre, with a ring of ground around it. */
    private fun drawCentre(canvas: org.jetbrains.skia.Canvas, cover: Image, side: Int) {
        val disc = side * CENTRE_FRACTION
        val left = (side - disc) / 2f
        val ring = side * 0.012f
        val ringSide = disc + 2 * ring
        canvas.drawRRect(
            RRect.makeLTRB(left - ring, left - ring, left + disc + ring, left + disc + ring, ringSide * 0.22f),
            Paint().apply { isAntiAlias = true; color = WHITE },
        )
        canvas.drawRRect(
            RRect.makeLTRB(left, left, left + disc, left + disc, disc * 0.2f),
            Paint().apply { isAntiAlias = true; shader = coverShader(cover, disc, left, left) },
        )
    }

    private fun toSkiaImage(image: BufferedImage): Image {
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            javax.imageio.ImageIO.write(image, "png", out)
            out.toByteArray()
        }
        return Image.makeFromEncoded(bytes)
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.profile.passport

import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.graphics.shapes.RoundedPolygon
import com.alananasss.kittytune.core.str
import com.alananasss.kittytune.data.stats.MusicPassport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.font.TextAttribute
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.text.Normalizer
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws a [MusicPassport] as one story-sized image (1080 × 1920) matching the Android passport 1-to-1:
 * security-printed paper, a header with expressive shapes and large bold typography, a photo with its
 * ghost copy, the holder's signature, citizenships as national genre flags, inked visa and achievement
 * stamps with authentic wear, and an ICAO 9303 machine-readable zone with real check digits.
 */
object MusicPassportRender {

    const val WIDTH = 1080
    const val HEIGHT = 1920
    private const val W = WIDTH.toFloat()
    private const val H = HEIGHT.toFloat()
    private const val LEFT = 84f
    private const val RIGHT = 996f
    private const val MRZ_TOP = 1784f

    private val PAPER = Color(246, 241, 230)
    private val PAPER_DEEP = Color(236, 227, 209)
    private val INK = Color(30, 27, 36)
    private val INK_SOFT = Color(104, 98, 112)

    private class Palette(val ink: Color, val tint: Color, val stampInks: Array<Color>)

    private val baseSansFont: Font by lazy {
        runCatching {
            MusicPassportRender::class.java.getResourceAsStream("/fonts/google_sans_flex.ttf")?.use {
                Font.createFont(Font.TRUETYPE_FONT, it)
            }
        }.getOrNull()
            ?: runCatching {
                MusicPassportRender::class.java.getResourceAsStream("/fonts/sfprodisplaybold.ttf")?.use {
                    Font.createFont(Font.TRUETYPE_FONT, it)
                }
            }.getOrNull()
            ?: Font("Segoe UI", Font.PLAIN, 12)
    }

    private val baseMonoFont: Font by lazy {
        val candidates = listOf("JetBrains Mono", "Consolas", "Courier New", "Menlo")
        for (cand in candidates) {
            val f = Font(cand, Font.BOLD, 12)
            if (f.family.equals(cand, ignoreCase = true)) return@lazy f
        }
        Font(Font.MONOSPACED, Font.BOLD, 12)
    }

    private val baseScriptFont: Font by lazy {
        val candidates = listOf("Segoe Script", "Lucida Handwriting", "Snell Roundhand", "Brush Script MT")
        for (cand in candidates) {
            val f = Font(cand, Font.PLAIN, 12)
            if (f.family.equals(cand, ignoreCase = true)) return@lazy f
        }
        Font(Font.SERIF, Font.ITALIC, 12)
    }

    suspend fun render(passport: MusicPassport): BufferedImage = withContext(Dispatchers.IO) {
        val portrait = passport.avatarUrl?.let { loadPortrait(it) }
        draw(passport, portrait)
    }

    suspend fun renderBitmap(passport: MusicPassport): Pair<BufferedImage, ImageBitmap> = withContext(Dispatchers.IO) {
        val portrait = passport.avatarUrl?.let { loadPortrait(it) }
        val awt = draw(passport, portrait)
        awt to awt.toComposeImageBitmap()
    }

    internal fun draw(passport: MusicPassport, portrait: BufferedImage? = null): BufferedImage {
        val out = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            configureQuality(g)
            val palette = paletteFor(passport.accentArgb)
            val title = runCatching { str("passport_title") }.getOrDefault("Music Passport")
                .takeIf { it.isNotBlank() && it != "passport_title" } ?: "Music Passport"

            drawPaper(g, palette)
            drawTitle(g, title, palette)
            drawHeaderRow(g, passport, palette)
            drawPhoto(g, passport, portrait, palette)
            drawFields(g, passport, portrait, palette)
            drawSignature(g, passport, palette)
            drawFigures(g, passport, palette)
            drawCitizenships(g, passport, palette)
            drawStamps(g, passport, palette)
            drawMrz(g, passport)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun configureQuality(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    }

    // ── Paper ───────────────────────────────────────────────────────────────────────────────────

    private fun drawPaper(g: Graphics2D, p: Palette) {
        g.paint = LinearGradientPaint(0f, 0f, 0f, H, floatArrayOf(0f, 1f), arrayOf(PAPER, PAPER_DEEP))
        g.fill(Rectangle2D.Float(0f, 0f, W, H))

        // Large soft expressive shapes in the theme's tint, matching Android 1-to-1
        g.color = withAlpha(p.tint, 120)
        g.fill(polygonPath(MaterialShapes.Cookie12Sided, Rectangle2D.Float(690f, -170f, 500f, 500f), 14f))

        g.color = withAlpha(p.tint, 70)
        g.fill(polygonPath(MaterialShapes.Flower, Rectangle2D.Float(-150f, 1460f, 380f, 380f), -8f))

        // Guilloché: the interlaced wave line-work real passports are printed with against forgery
        g.stroke = BasicStroke(1.1f)
        for (family in 0..1) {
            g.color = withAlpha(p.ink, if (family == 0) 20 else 12)
            for (i in 0 until 80) {
                val y0 = -8f + i * 24f
                val path = Path2D.Float()
                var x = 0f
                while (x <= W + 8f) {
                    val y = if (family == 0) {
                        y0 + 8f * sin(x / 46f + i * 0.55f) + 3.5f * sin(x / 15f + i)
                    } else {
                        y0 + 12f + 9f * cos(x / 61f - i * 0.4f)
                    }
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 8f
                }
                g.draw(path)
            }
        }

        // Rosette watermark behind the details
        g.color = withAlpha(p.ink, 20)
        g.stroke = BasicStroke(1.2f)
        for (i in 0 until 26) {
            val path = Path2D.Float()
            val r = 40f + i * 10f
            var a = 0.0
            while (a <= Math.PI * 2 + 0.01) {
                val rr = r + 7f * sin(a * 8 + i * 0.35).toFloat()
                val x = 720f + (rr * cos(a)).toFloat()
                val y = 690f + (rr * sin(a)).toFloat()
                if (a == 0.0) path.moveTo(x, y) else path.lineTo(x, y)
                a += Math.PI / 90
            }
            g.draw(path)
        }
    }

    /** The document's own heading: an expressive emblem, the issuer, and the title in the app's type. */
    private fun drawTitle(g: Graphics2D, title: String, p: Palette) {
        val emblemRect = Rectangle2D.Float(LEFT, 72f, 112f, 112f)
        g.color = p.ink
        g.fill(polygonPath(MaterialShapes.Cookie9Sided, emblemRect, 0f))

        // A vinyl record in the emblem
        val cx = LEFT + 56f
        val cy = 72f + 56f
        g.color = PAPER
        g.stroke = BasicStroke(3f)
        g.draw(Ellipse2D.Float(cx - 30f, cy - 30f, 60f, 60f))
        g.stroke = BasicStroke(1.5f)
        g.color = withAlpha(PAPER, 170)
        g.draw(Ellipse2D.Float(cx - 21f, cy - 21f, 42f, 42f))
        g.color = PAPER
        g.fill(Ellipse2D.Float(cx - 8f, cy - 8f, 16f, 16f))

        // App name & Document type
        g.font = sansFont(24f, bold = true, tracking = 0.22f)
        g.color = p.ink
        g.drawString("KITTYTUNE", LEFT + 140f, 118f)

        g.font = sansFont(19f, bold = true, tracking = 0.12f)
        g.color = INK_SOFT
        g.drawString("OFFICIAL LISTENING DOCUMENT", LEFT + 140f, 154f)

        // Main Title (PASSPORT / MUSIC PASSPORT) in big bold type, matching Android (96pt)
        val headingFont = sansFont(96f, bold = true, tracking = -0.02f)
        g.font = headingFont
        g.color = INK
        val titleText = fit(title, headingFont, RIGHT - LEFT, 52f, g)
        g.drawString(titleText, LEFT, 296f)
    }

    private fun drawHeaderRow(g: Graphics2D, passport: MusicPassport, p: Palette) {
        g.color = withAlpha(p.ink, 80)
        g.stroke = BasicStroke(2f)
        g.draw(Line2D.Float(LEFT, 334f, RIGHT, 334f))

        val labelFont = sansFont(19f, bold = true, tracking = 0.14f)
        val valueFont = sansFont(30f, bold = true)
        val cols = listOf(LEFT to ("TYPE" to "P"), 196f to ("CODE" to "KTN"), 340f to ("AUTHORITY" to "KITTYTUNE"))
        cols.forEach { (x, pair) ->
            g.font = labelFont
            g.color = p.ink
            g.drawString(pair.first, x, 380f)
            g.font = valueFont
            g.color = INK
            g.drawString(pair.second, x, 418f)
        }

        g.font = labelFont
        g.color = p.ink
        val passNoLabel = "PASSPORT NO."
        val labelW = g.fontMetrics.stringWidth(passNoLabel)
        g.drawString(passNoLabel, RIGHT - labelW, 380f)

        g.font = sansFont(30f, bold = true, tracking = 0.04f)
        g.color = INK
        val noW = g.fontMetrics.stringWidth(passport.passportNo)
        g.drawString(passport.passportNo, RIGHT - noW, 418f)

        g.color = withAlpha(p.ink, 80)
        g.stroke = BasicStroke(2f)
        g.draw(Line2D.Float(LEFT, 446f, RIGHT, 446f))
    }

    private fun drawPhoto(g: Graphics2D, passport: MusicPassport, portrait: BufferedImage?, p: Palette) {
        val photoX = LEFT
        val photoY = 476f
        val photoW = 300f
        val photoH = 360f
        val shape = RoundRectangle2D.Float(photoX, photoY, photoW, photoH, 44f, 44f)

        // Drop shadow behind portrait
        g.color = Color(0, 0, 0, 45)
        g.fill(RoundRectangle2D.Float(photoX, photoY + 6f, photoW, photoH, 44f, 44f))

        val savedClip = g.clip
        g.clip(shape)
        drawPortrait(g, photoX, photoY, photoW, photoH, passport, portrait, p, grayscale = false)

        // Holographic sheen across laminate
        g.paint = LinearGradientPaint(
            Point2D.Float(photoX, photoY),
            Point2D.Float(photoX + photoW, photoY + photoH),
            floatArrayOf(0.25f, 0.45f, 0.55f, 0.75f),
            arrayOf(Color(255, 255, 255, 0), Color(255, 255, 255, 70), Color(170, 210, 255, 30), Color(255, 255, 255, 0)),
        )
        g.fill(Rectangle2D.Float(photoX, photoY, photoW, photoH))
        g.clip = savedClip

        // Photo border
        g.color = withAlpha(INK, 55)
        g.stroke = BasicStroke(2f)
        g.draw(shape)
    }

    private fun drawPortrait(
        g: Graphics2D,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        passport: MusicPassport,
        avatar: BufferedImage?,
        p: Palette,
        grayscale: Boolean,
    ) {
        if (avatar != null && avatar.width > 0 && avatar.height > 0) {
            val scale = max(w / avatar.width.toFloat(), h / avatar.height.toFloat())
            val dw = avatar.width * scale
            val dh = avatar.height * scale
            val dx = x + (w - dw) / 2f
            val dy = y + (h - dh) / 2f

            if (grayscale) {
                val gray = BufferedImage(avatar.width, avatar.height, BufferedImage.TYPE_INT_ARGB)
                for (py in 0 until avatar.height) {
                    for (px in 0 until avatar.width) {
                        val argb = avatar.getRGB(px, py)
                        val sa = (argb ushr 24) and 0xFF
                        val r = (argb ushr 16) and 0xFF
                        val gr = (argb ushr 8) and 0xFF
                        val b = argb and 0xFF
                        val lum = (0.299f * r + 0.587f * gr + 0.114f * b).toInt().coerceIn(0, 255)
                        gray.setRGB(px, py, (sa shl 24) or (lum shl 16) or (lum shl 8) or lum)
                    }
                }
                val at = AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble()).apply {
                    scale(scale.toDouble(), scale.toDouble())
                }
                g.drawRenderedImage(gray, at)
            } else {
                val at = AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble()).apply {
                    scale(scale.toDouble(), scale.toDouble())
                }
                g.drawRenderedImage(avatar, at)
            }
        } else {
            g.paint = LinearGradientPaint(
                0f, y, 0f, y + h,
                floatArrayOf(0f, 1f),
                arrayOf(blendColor(p.ink, Color.WHITE, 0.25f), p.ink),
            )
            g.fill(Rectangle2D.Float(x, y, w, h))

            val initial = passport.holderName.trim().take(1).uppercase(Locale.ROOT).ifEmpty { "♪" }
            g.font = sansFont(h * 0.48f, bold = true)
            g.color = Color.WHITE
            val metrics = g.fontMetrics
            val iw = metrics.stringWidth(initial)
            g.drawString(initial, x + (w - iw) / 2f, y + h / 2f + (metrics.ascent - metrics.descent) / 2f - 4f)
        }
    }

    private fun drawFields(g: Graphics2D, passport: MusicPassport, portrait: BufferedImage?, p: Palette) {
        val x = 424f
        val labelFont = sansFont(19f, bold = true, tracking = 0.14f)

        fun field(title: String, value: String, top: Float, size: Float, maxWidth: Float, fx: Float = x) {
            g.font = labelFont
            g.color = p.ink
            g.drawString(title, fx, top)

            val valueFont = sansFont(size, bold = true)
            g.font = valueFont
            g.color = INK
            val text = fit(value, valueFont, maxWidth, size * 0.7f, g)
            g.drawString(text, fx, top + size + 6f)
        }

        field("HOLDER", passport.holderName, 504f, 50f, RIGHT - x)
        field("NATIONALITY", passport.citizenships.firstOrNull()?.genre ?: "—", 616f, 36f, 440f)
        field("CLASS", passport.travellerClass, 714f, 36f, 440f)
        field("DATE OF ISSUE", stampDate(passport.issuedAtMs), 812f, 34f, 228f)
        field("EXPIRES", "NEVER", 812f, 34f, 200f, fx = 672f)

        // Ghost portrait at 884f, 604f (width 96f, height 124f) with alpha 85
        val ghostRect = RoundRectangle2D.Float(884f, 604f, 96f, 124f, 20f, 20f)
        val savedClip = g.clip
        val prevComp = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f)
        g.clip(ghostRect)
        drawPortrait(g, 884f, 604f, 96f, 124f, passport, portrait, p, grayscale = true)
        g.composite = prevComp
        g.clip = savedClip
    }

    private fun drawSignature(g: Graphics2D, passport: MusicPassport, p: Palette) {
        val sigFont = scriptFont(58f)
        g.font = sigFont
        g.color = withAlpha(INK, 225)
        val sigText = fit(passport.holderName, sigFont, 280f, 36f, g)
        g.drawString(sigText, LEFT + 14f, 904f)

        g.color = withAlpha(INK, 90)
        g.stroke = BasicStroke(1.5f)
        g.draw(Line2D.Float(LEFT, 918f, LEFT + 300f, 918f))

        g.font = sansFont(16f, bold = true, tracking = 0.14f)
        g.color = p.ink
        g.drawString("HOLDER'S SIGNATURE", LEFT, 946f)
    }

    private fun drawFigures(g: Graphics2D, passport: MusicPassport, p: Palette) {
        g.color = withAlpha(p.ink, 90)
        g.stroke = BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(10f, 8f), 0f)
        g.draw(Line2D.Float(LEFT, 980f, RIGHT, 980f))

        val nf = NumberFormat.getIntegerInstance()
        val figures = listOf(
            nf.format(passport.totalMinutes) to "MINUTES",
            nf.format(passport.tracks) to "TRACKS",
            nf.format(passport.artists) to "ARTISTS",
            nf.format(passport.longestStreakDays) to "DAY STREAK",
        )
        val cell = (RIGHT - LEFT) / figures.size
        val divider = BasicStroke(1.5f)
        val labelFont = sansFont(19f, bold = true, tracking = 0.14f)

        figures.forEachIndexed { i, (value, name) ->
            val cx = LEFT + cell * i + cell / 2f
            val numFont = sansFont(56f, bold = true)
            g.font = numFont
            g.color = INK
            val fittedValue = fit(value, numFont, cell - 16f, 36f, g)
            val vw = g.fontMetrics.stringWidth(fittedValue)
            g.drawString(fittedValue, cx - vw / 2f, 1056f)

            g.font = labelFont
            g.color = p.ink
            val lw = g.fontMetrics.stringWidth(name)
            g.drawString(name, cx - lw / 2f, 1092f)

            if (i > 0) {
                g.color = withAlpha(p.ink, 50)
                g.stroke = divider
                g.draw(Line2D.Float(LEFT + cell * i, 1012f, LEFT + cell * i, 1098f))
            }
        }
    }

    private fun drawCitizenships(g: Graphics2D, passport: MusicPassport, p: Palette) {
        drawSectionTitle(g, "CITIZENSHIPS", 1156f, p)
        if (passport.citizenships.isEmpty()) {
            g.font = sansFont(28f, bold = false)
            g.color = INK_SOFT
            g.drawString("Citizenship pending — keep listening", LEFT, 1220f)
            return
        }

        val colWidth = (RIGHT - LEFT) / 2f
        passport.citizenships.take(4).forEachIndexed { i, c ->
            val x = LEFT + (i % 2) * colWidth
            val top = 1182f + (i / 2) * 94f

            drawFlag(g, Rectangle2D.Float(x, top + 8f, 88f, 58f), c.genre)

            val nameFont = sansFont(32f, bold = true)
            g.font = nameFont
            g.color = INK
            val genreText = fit(c.genre, nameFont, colWidth - 124f, 24f, g)
            g.drawString(genreText, x + 108f, top + 36f)

            val shareFont = sansFont(17f, bold = true, tracking = 0.14f)
            g.font = shareFont
            g.color = INK_SOFT
            g.drawString("${c.sharePercent}% OF LISTENING", x + 108f, top + 66f)
        }
    }

    private fun drawFlag(g: Graphics2D, rect: Rectangle2D.Float, genre: String) {
        val (c1, c2, c3) = genreStripeColors(genre)
        val colors = arrayOf(Color(c1), Color(c2), Color(c3))
        val hash = genre.lowercase(Locale.ROOT).hashCode() and 0x7fffffff
        val horizontal = (hash / 360) % 2 == 0
        val shape = RoundRectangle2D.Float(rect.x, rect.y, rect.width, rect.height, 14f, 14f)

        val savedClip = g.clip
        g.clip(shape)
        colors.forEachIndexed { i, color ->
            g.color = color
            if (horizontal) {
                val h = rect.height / 3f
                g.fill(Rectangle2D.Float(rect.x, rect.y + h * i, rect.width, h + 1f))
            } else {
                val w = rect.width / 3f
                g.fill(Rectangle2D.Float(rect.x + w * i, rect.y, w + 1f, rect.height))
            }
        }
        g.paint = LinearGradientPaint(
            0f, rect.y, 0f, rect.y + rect.height,
            floatArrayOf(0f, 1f),
            arrayOf(Color(255, 255, 255, 55), Color(0, 0, 0, 25)),
        )
        g.fill(rect)
        g.clip = savedClip

        g.color = withAlpha(INK, 60)
        g.stroke = BasicStroke(1.5f)
        g.draw(shape)
    }

    internal fun genreStripeColors(genre: String): Triple<Int, Int, Int> {
        val hash = genre.lowercase(Locale.ROOT).hashCode() and 0x7fffffff
        val hue = (hash % 360).toFloat()
        return Triple(
            hsl(hue, 0.58f, 0.44f),
            hsl(hue, 0.22f, 0.95f),
            hsl((hue + 38f) % 360f, 0.62f, 0.30f),
        )
    }

    internal fun visaColor(platform: String, fallback: Int): Int = when (platform.uppercase(Locale.ROOT)) {
        "SOUNDCLOUD" -> rgbInt(184, 72, 24)
        "SPOTIFY" -> rgbInt(24, 118, 68)
        "TIDAL" -> rgbInt(28, 84, 156)
        "DEEZER" -> rgbInt(112, 54, 158)
        "QOBUZ" -> rgbInt(36, 78, 148)
        "YOUTUBE" -> rgbInt(176, 42, 50)
        else -> fallback
    }

    // ── Stamps ──────────────────────────────────────────────────────────────────────────────────

    private fun drawStamps(g: Graphics2D, passport: MusicPassport, p: Palette) {
        drawSectionTitle(g, "VISAS & ENTRY STAMPS", 1404f, p)
        val visas = passport.visas.take(3)
        val stamps = passport.stamps.take(4)
        if (visas.isEmpty() && stamps.isEmpty()) {
            g.font = sansFont(28f, bold = false)
            g.color = INK_SOFT
            g.drawString("No stamps yet — your first trip awaits", LEFT, 1468f)
            return
        }

        val total = visas.size + stamps.size
        val width = RIGHT - LEFT
        val positions: List<Pair<Float, Float>> = if (total <= 4) {
            val step = width / total
            List(total) { i -> LEFT + step * (i + 0.5f) to if (i % 2 == 0) 1560f else 1640f }
        } else {
            val visaStep = width / visas.size
            val stampStep = width / stamps.size
            List(visas.size) { i -> LEFT + visaStep * (i + 0.5f) to 1514f + (i % 2) * 14f } +
                List(stamps.size) { i -> LEFT + stampStep * (i + 0.5f) to 1680f - (i % 2) * 10f }
        }
        val rotations = floatArrayOf(-6f, 9f, -4f, 7f, -11f, 5f, -8f)
        var inkIdx = 0

        visas.forEachIndexed { i, visa ->
            val (cx, cy) = positions[i]
            val color = p.stampInks[inkIdx++ % p.stampInks.size]
            drawWorn(g, cx, cy, rotations[i % rotations.size], 262, 124, seed = i * 31 + 5) { sg, x, y ->
                drawVisaStamp(sg, x, y, 262f, 124f, visa, color)
            }
        }
        stamps.forEachIndexed { i, stamp ->
            val index = visas.size + i
            val (cx, cy) = positions[index]
            val color = p.stampInks[inkIdx++ % p.stampInks.size]
            drawWorn(g, cx, cy, rotations[index % rotations.size], 172, 172, seed = i * 17 + 11) { sg, x, y ->
                drawRoundStamp(sg, x, y, 84f, stamp, color)
            }
        }
    }

    private fun drawVisaStamp(g: Graphics2D, cx: Float, cy: Float, w: Float, h: Float, visa: MusicPassport.Visa, color: Color) {
        val outer = RoundRectangle2D.Float(cx - w / 2f + 3f, cy - h / 2f + 3f, w - 6f, h - 6f, 28f, 28f)
        g.color = color
        g.stroke = BasicStroke(5f)
        g.draw(outer)

        val inner = RoundRectangle2D.Float(outer.x + 9f, outer.y + 9f, outer.width - 18f, outer.height - 18f, 16f, 16f)
        g.stroke = BasicStroke(2f)
        g.draw(inner)

        val room = inner.width - 24f
        val nameFont = sansFont(36f, bold = true, tracking = 0.05f)
        g.font = nameFont
        val nameText = fit(visa.platform.uppercase(Locale.ROOT), nameFont, room, 22f, g)
        val nw = g.fontMetrics.stringWidth(nameText)
        g.drawString(nameText, cx - nw / 2f, cy - 8f)

        val smallFont = sansFont(15f, bold = true, tracking = 0.12f)
        g.font = smallFont
        val entryText = fit("ENTRY ${visa.since}", smallFont, room, 11f, g)
        val ew = g.fontMetrics.stringWidth(entryText)
        g.drawString(entryText, cx - ew / 2f, cy + 18f)

        val countFont = sansFont(18f, bold = true, tracking = 0.08f)
        g.font = countFont
        val countText = fit("${compact(visa.discoveries)} TRACKS ADMITTED", countFont, room, 12f, g)
        val cw = g.fontMetrics.stringWidth(countText)
        g.drawString(countText, cx - cw / 2f, cy + 42f)
    }

    private fun drawRoundStamp(g: Graphics2D, cx: Float, cy: Float, r: Float, stamp: MusicPassport.Stamp, color: Color) {
        g.color = color
        g.stroke = BasicStroke(5f)
        g.draw(Ellipse2D.Float(cx - (r - 3f), cy - (r - 3f), (r - 3f) * 2f, (r - 3f) * 2f))

        g.stroke = BasicStroke(2f)
        g.draw(Ellipse2D.Float(cx - (r - 14f), cy - (r - 14f), (r - 14f) * 2f, (r - 14f) * 2f))

        // Top arc text
        val topFont = sansFont(17f, bold = true, tracking = 0.1f)
        val topRadius = r - 31f
        drawArcText(g, stamp.label.uppercase(Locale.ROOT), cx, cy, topRadius, top = true, topFont, color)

        // Bottom arc text
        val bottomFont = sansFont(13f, bold = true, tracking = 0.32f)
        val bottomRadius = r - 25f
        drawArcText(g, "KITTYTUNE", cx, cy, bottomRadius, top = false, bottomFont, color)

        // Date between rules
        val half = r - 40f
        g.stroke = BasicStroke(2f)
        g.draw(Line2D.Float(cx - half, cy - 19f, cx + half, cy - 19f))
        g.draw(Line2D.Float(cx - half, cy + 20f, cx + half, cy + 20f))

        if (stamp.date.isNotBlank()) {
            val dateFont = sansFont(19f, bold = true, tracking = 0.02f)
            g.font = dateFont
            val dw = g.fontMetrics.stringWidth(stamp.date)
            g.drawString(stamp.date, cx - dw / 2f, cy + 7f)
        } else {
            g.font = sansFont(32f, bold = true)
            val sw = g.fontMetrics.stringWidth("★")
            g.drawString("★", cx - sw / 2f, cy + 12f)
        }
    }

    private fun drawArcText(
        g: Graphics2D,
        text: String,
        cx: Float,
        cy: Float,
        radius: Float,
        top: Boolean,
        font: Font,
        color: Color,
    ) {
        if (text.isEmpty() || radius <= 0f) return
        g.font = font
        g.color = color
        val metrics = g.fontMetrics
        val widths = FloatArray(text.length) { i -> metrics.stringWidth(text[i].toString()).toFloat() }
        val totalWidth = widths.sum() + (text.length - 1) * 3f
        val totalAngle = totalWidth / radius

        var cursor = 0f
        for (i in text.indices) {
            val ch = text[i].toString()
            val w = widths[i]
            val mid = cursor + w / 2f
            val offset = mid - totalWidth / 2f
            val angle = offset / radius
            val savedTransform = g.transform
            if (top) {
                val rot = angle
                g.translate(cx.toDouble(), cy.toDouble())
                g.rotate(rot.toDouble())
                g.translate(0.0, -radius.toDouble())
                g.drawString(ch, -w / 2f, 4f)
            } else {
                val rot = -angle
                g.translate(cx.toDouble(), cy.toDouble())
                g.rotate(rot.toDouble())
                g.translate(0.0, radius.toDouble())
                g.drawString(ch, -w / 2f, 4f)
            }
            g.transform = savedTransform
            cursor += w + 3f
        }
    }

    private fun drawWorn(
        g: Graphics2D,
        cx: Float,
        cy: Float,
        rotation: Float,
        w: Int,
        h: Int,
        seed: Int,
        draw: (Graphics2D, Float, Float) -> Unit,
    ) {
        val pad = 14
        val lw = w + pad * 2
        val lh = h + pad * 2
        val layer = BufferedImage(lw, lh, BufferedImage.TYPE_INT_ARGB)
        val lc = layer.createGraphics()
        try {
            configureQuality(lc)
            draw(lc, lw / 2f, lh / 2f)

            // Random speck erosion
            val rnd = Random(seed)
            lc.composite = AlphaComposite.Clear
            repeat(w * h / 70) {
                val rx = rnd.nextFloat() * lw
                val ry = rnd.nextFloat() * lh
                val rad = 0.6f + rnd.nextFloat() * 1.9f
                lc.fill(Ellipse2D.Float(rx - rad, ry - rad, rad * 2f, rad * 2f))
            }
        } finally {
            lc.dispose()
        }

        val saved = g.transform
        g.rotate(Math.toRadians(rotation.toDouble()), cx.toDouble(), cy.toDouble())
        val prevComp = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.90f)
        g.drawImage(layer, (cx - lw / 2f).toInt(), (cy - lh / 2f).toInt(), null)
        g.composite = prevComp
        g.transform = saved
    }

    // ── Machine-readable zone ───────────────────────────────────────────────────────────────────

    private fun drawMrz(g: Graphics2D, passport: MusicPassport) {
        g.color = blendColor(PAPER_DEEP, INK, 0.05f)
        g.fill(Rectangle2D.Float(0f, MRZ_TOP, W, H - MRZ_TOP))

        g.color = withAlpha(INK, 40)
        g.stroke = BasicStroke(1.5f)
        g.draw(Line2D.Float(0f, MRZ_TOP, W, MRZ_TOP))

        val (line1, line2) = mrzLines(passport)
        var size = 34f
        var mrzFont = monoFont(size, tracking = 0.06f)
        g.font = mrzFont
        while (g.fontMetrics.stringWidth(line1) > RIGHT - LEFT && size > 20f) {
            size -= 0.5f
            mrzFont = monoFont(size, tracking = 0.06f)
            g.font = mrzFont
        }

        g.color = INK
        g.drawString(line1, LEFT, MRZ_TOP + 58f)
        g.drawString(line2, LEFT, MRZ_TOP + 108f)
    }

    /** Two 44-character lines laid out as a TD3 passport's, with ICAO 9303 check digits. */
    internal fun mrzLines(passport: MusicPassport): Pair<String, String> {
        val name = mrzText(passport.holderName)
        val line1 = ("P<KTN$name<<${mrzText(passport.travellerClass)}").padEnd(44, '<').take(44)
        val number = mrzText(passport.passportNo).replace("<", "").padEnd(9, '<').take(9)
        val issued = DateTimeFormatter.ofPattern("yyMMdd")
            .format(Instant.ofEpochMilli(passport.issuedAtMs).atZone(ZoneId.systemDefault()))
        val genre = mrzText(passport.citizenships.firstOrNull()?.genre.orEmpty())
        val body = "$number${checkDigit(number)}KTN$issued${checkDigit(issued)}X<<<<<<<$genre"
            .padEnd(43, '<').take(43)
        return line1 to body + checkDigit(body)
    }

    internal fun mrzCheckDigit(field: String): Char = ('0' + checkDigit(field))

    private fun mrzText(raw: String): String =
        Normalizer.normalize(raw.uppercase(Locale.ROOT), Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' }
            .filter { it.code < 128 }
            .map { if (it.isLetterOrDigit()) it else '<' }
            .joinToString("")

    private fun checkDigit(text: String): Int {
        val weights = intArrayOf(7, 3, 1)
        return text.mapIndexed { i, c ->
            val value = when (c) {
                in '0'..'9' -> c - '0'
                in 'A'..'Z' -> c - 'A' + 10
                else -> 0
            }
            value * weights[i % 3]
        }.sum() % 10
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    private fun stampDate(ms: Long): String =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
            .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
            .uppercase(Locale.ENGLISH)

    private fun polygonPath(polygon: RoundedPolygon, target: Rectangle2D.Float, rotation: Float): Path2D.Float {
        val cubics = polygon.cubics
        if (cubics.isEmpty()) return Path2D.Float()
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        cubics.forEach { c ->
            minX = min(minX, min(c.anchor0X, min(c.control0X, min(c.control1X, c.anchor1X))))
            minY = min(minY, min(c.anchor0Y, min(c.control0Y, min(c.control1Y, c.anchor1Y))))
            maxX = max(maxX, max(c.anchor0X, max(c.control0X, max(c.control1X, c.anchor1X))))
            maxY = max(maxY, max(c.anchor0Y, max(c.control0Y, max(c.control1Y, c.anchor1Y))))
        }
        val bw = (maxX - minX).coerceAtLeast(1e-4f)
        val bh = (maxY - minY).coerceAtLeast(1e-4f)
        val scale = min(target.width / bw, target.height / bh)
        val srcCx = (minX + maxX) / 2f
        val srcCy = (minY + maxY) / 2f
        val dstCx = target.x + target.width / 2f
        val dstCy = target.y + target.height / 2f
        val rad = Math.toRadians(rotation.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()

        fun mapX(x: Float, y: Float): Float = dstCx + ((x - srcCx) * scale) * cosA - ((y - srcCy) * scale) * sinA
        fun mapY(x: Float, y: Float): Float = dstCy + ((x - srcCx) * scale) * sinA + ((y - srcCy) * scale) * cosA

        val path = Path2D.Float()
        cubics.forEachIndexed { i, c ->
            if (i == 0) path.moveTo(mapX(c.anchor0X, c.anchor0Y), mapY(c.anchor0X, c.anchor0Y))
            path.curveTo(
                mapX(c.control0X, c.control0Y), mapY(c.control0X, c.control0Y),
                mapX(c.control1X, c.control1Y), mapY(c.control1X, c.control1Y),
                mapX(c.anchor1X, c.anchor1Y), mapY(c.anchor1Y, c.anchor1Y),
            )
        }
        path.closePath()
        return path
    }

    private fun paletteFor(accent: Int): Palette {
        val hsl = FloatArray(3)
        colorToHSL(accent, hsl)
        val hue = hsl[0]
        val sat = hsl[1]
        return Palette(
            ink = Color(hsl(hue, sat.coerceIn(0.45f, 0.7f), 0.30f)),
            tint = Color(hsl(hue, sat.coerceIn(0.35f, 0.6f), 0.84f)),
            stampInks = arrayOf(
                Color(hsl(hue, sat.coerceIn(0.5f, 0.75f), 0.36f)),
                Color(176, 46, 54),
                Color(36, 84, 160),
                Color(30, 118, 84),
                Color(112, 60, 150),
                Color(184, 98, 34),
            ),
        )
    }

    private fun drawSectionTitle(g: Graphics2D, text: String, baseline: Float, p: Palette) {
        val titleFont = sansFont(20f, bold = true, tracking = 0.2f)
        g.font = titleFont
        g.color = p.ink
        g.drawString(text, LEFT, baseline)

        val end = LEFT + g.fontMetrics.stringWidth(text) + 18f
        g.color = withAlpha(p.ink, 70)
        g.stroke = BasicStroke(2f)
        g.draw(Line2D.Float(end, baseline - 7f, RIGHT, baseline - 7f))
    }

    private fun sansFont(size: Float, bold: Boolean = false, tracking: Float = 0f): Font {
        var f = baseSansFont.deriveFont(if (bold) Font.BOLD else Font.PLAIN, size)
        if (tracking != 0f) {
            f = f.deriveFont(mapOf(TextAttribute.TRACKING to tracking))
        }
        return f
    }

    private fun monoFont(size: Float, tracking: Float = 0f): Font {
        var f = baseMonoFont.deriveFont(Font.BOLD, size)
        if (tracking != 0f) {
            f = f.deriveFont(mapOf(TextAttribute.TRACKING to tracking))
        }
        return f
    }

    private fun scriptFont(size: Float): Font = baseScriptFont.deriveFont(Font.PLAIN, size)

    private fun compact(n: Int): String = when {
        n >= 1_000_000 -> "%.1fM".format(Locale.US, n / 1_000_000f)
        n >= 10_000 -> "%.1fK".format(Locale.US, n / 1_000f)
        else -> n.toString()
    }

    private fun fit(text: String, font: Font, maxWidth: Float, minSize: Float, g: Graphics2D): String {
        var curFont = font
        while (g.getFontMetrics(curFont).stringWidth(text) > maxWidth && curFont.size2D > minSize) {
            curFont = curFont.deriveFont(curFont.size2D - 0.5f)
        }
        g.font = curFont
        return ellipsize(text, curFont, maxWidth, g)
    }

    private fun ellipsize(text: String, font: Font, maxWidth: Float, g: Graphics2D): String {
        val fm = g.getFontMetrics(font)
        if (fm.stringWidth(text) <= maxWidth) return text
        var cut = text.length
        while (cut > 1 && fm.stringWidth(text.take(cut) + "…") > maxWidth) cut--
        return text.take(cut) + "…"
    }

    private fun loadPortrait(url: String): BufferedImage? = runCatching {
        when {
            url.startsWith("file://") -> ImageIO.read(File(URI(url)))
            File(url).exists() -> ImageIO.read(File(url))
            else -> {
                val connection = URI(url).toURL().openConnection().apply {
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    connectTimeout = 8_000
                    readTimeout = 8_000
                }
                connection.getInputStream().use { ImageIO.read(it) }
            }
        }
    }.getOrNull()

    private fun withAlpha(c: Color, alpha: Int): Color = Color(c.red, c.green, c.blue, alpha.coerceIn(0, 255))

    private fun blendColor(c1: Color, c2: Color, ratio: Float): Color {
        val inv = (1f - ratio).coerceIn(0f, 1f)
        val rRatio = ratio.coerceIn(0f, 1f)
        return Color(
            (c1.red * inv + c2.red * rRatio).toInt().coerceIn(0, 255),
            (c1.green * inv + c2.green * rRatio).toInt().coerceIn(0, 255),
            (c1.blue * inv + c2.blue * rRatio).toInt().coerceIn(0, 255),
            (c1.alpha * inv + c2.alpha * rRatio).toInt().coerceIn(0, 255),
        )
    }

    private fun rgbInt(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun colorToHSL(color: Int, outHsl: FloatArray) {
        val rf = ((color ushr 16) and 0xFF) / 255f
        val gf = ((color ushr 8) and 0xFF) / 255f
        val bf = (color and 0xFF) / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val d = max - min
        val l = (max + min) / 2f
        val h: Float
        val s: Float
        if (max == min) {
            h = 0f
            s = 0f
        } else {
            s = d / (1f - abs(2f * l - 1f))
            h = when (max) {
                rf -> ((gf - bf) / d) % 6f
                gf -> (bf - rf) / d + 2f
                else -> (rf - gf) / d + 4f
            } * 60f
        }
        outHsl[0] = (h + 360f) % 360f
        outHsl[1] = s.coerceIn(0f, 1f)
        outHsl[2] = l.coerceIn(0f, 1f)
    }

    private fun hsl(h: Float, s: Float, l: Float): Int {
        val sat = s.coerceIn(0f, 1f)
        val light = l.coerceIn(0f, 1f)
        val c = (1f - abs(2f * light - 1f)) * sat
        val m = light - 0.5f * c
        val x = c * (1f - abs(((h / 60f) % 2f) - 1f))
        val hueSegment = ((h % 360f + 360f) % 360f).toInt() / 60
        val (rf, gf, bf) = when (hueSegment) {
            0 -> Triple(c + m, x + m, m)
            1 -> Triple(x + m, c + m, m)
            2 -> Triple(m, c + m, x + m)
            3 -> Triple(m, x + m, c + m)
            4 -> Triple(x + m, m, c + m)
            else -> Triple(c + m, m, x + m)
        }
        return rgbInt(
            (rf * 255f).toInt().coerceIn(0, 255),
            (gf * 255f).toInt().coerceIn(0, 255),
            (bf * 255f).toInt().coerceIn(0, 255),
        )
    }
}
